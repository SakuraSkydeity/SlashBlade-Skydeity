# -*- coding: utf-8 -*-
"""OBJ 网格简化（边长优先的边塌缩，带 UV 缝与硬边保护）。

- 顶点位置先去重（同坐标合并），再按边长从小到大塌缩边；
- 保护：UV 距离过大的边（贴图缝）、二面角过大的边（硬边/转折）、边界边；
- UV 索引与顶点法线索引**原样保留**（模型是平滑着色，重算法线会变刻面）；
- 塌缩后产生的退化面直接删除。
"""
import heapq
import math
import sys
from collections import defaultdict

import numpy as np

UV_TEX = 2048.0        # 贴图边长，用于把 UV 距离换算成像素
MAX_UV_PX = 34.0       # UV 像素距离超过它 → 认为是贴图缝，不塌缩
MAX_DIHEDRAL = 38.0    # 二面角超过它 → 硬边，不塌缩
UV_WEIGHT = 0.020      # UV 距离在代价里的权重（像素 × 权重）


def parse(path):
    V, VT, VN = [], [], []
    groups = {}
    order = []
    cur = None
    for line in open(path, errors='replace'):
        if line.startswith('v '):
            p = line.split(); V.append((float(p[1]), float(p[2]), float(p[3])))
        elif line.startswith('vt '):
            p = line.split(); VT.append((float(p[1]), float(p[2])))
        elif line.startswith('vn '):
            p = line.split(); VN.append((float(p[1]), float(p[2]), float(p[3])))
        elif line.startswith('g ') or line.startswith('o '):
            cur = line.split(None, 1)[1].strip()
            if cur in groups:
                cur += '#2'
            groups[cur] = []
            order.append(cur)
        elif line.startswith('f ') and cur:
            groups[cur].append([tuple(int(x) - 1 for x in t.split('/')) for t in line.split()[1:]])
    return V, VT, VN, groups, order


def dedupe(seq, nd):
    """按值去重，返回 (新表, 旧索引->新索引)。"""
    key = {}
    new = []
    remap = [0] * len(seq)
    for i, p in enumerate(seq):
        k = tuple(round(v, nd) for v in p)
        j = key.get(k)
        if j is None:
            j = len(new)
            key[k] = j
            new.append(p)
        remap[i] = j
    return new, remap


def collapse(P, faces, target_faces, frozen=()):
    """P: list[np.array], faces: list[list[(vi,ti,ni)]]。返回 (P, faces)。"""
    P = [np.array(p, float) for p in P]
    faces = [[list(c) for c in f] for f in faces]
    alive = [True] * len(faces)
    frozen = set(frozen)

    def vof(f):
        return [c[0] for c in f]

    vert_faces = defaultdict(set)
    for fi, f in enumerate(faces):
        for c in f:
            vert_faces[c[0]].add(fi)

    def geo_normal(f):
        p = [P[c[0]] for c in f]
        n = np.cross(p[1] - p[0], p[2] - p[0])
        ln = np.linalg.norm(n)
        return n / ln if ln > 1e-12 else np.zeros(3)

    def edge_faces(a, b):
        out = []
        for fi in vert_faces.get(a, ()):
            if not alive[fi]:
                continue
            vs = vof(faces[fi])
            if b in vs:
                out.append(fi)
        return out

    def uv_of(face, v):
        for c in faces[face]:
            if c[0] == v:
                return c[1]
        return None

    heap = []
    rev = defaultdict(int)

    def cost(a, b):
        efs = edge_faces(a, b)
        if not efs:
            return None
        if a in frozen or b in frozen:
            return None
        n0 = geo_normal(faces[efs[0]])
        for fi in efs[1:]:
            n1 = geo_normal(faces[fi])
            ang = math.degrees(math.acos(max(-1.0, min(1.0, float(n0 @ n1)))))
            if ang > MAX_DIHEDRAL:
                return None
        if len(efs) != 2:
            return None
        fi = efs[0]
        ta, tb = uv_of(fi, a), uv_of(fi, b)
        if ta is None or tb is None:
            return None
        uvpx = math.hypot((UVS[ta][0] - UVS[tb][0]) * UV_TEX, (UVS[ta][1] - UVS[tb][1]) * UV_TEX)
        if uvpx > MAX_UV_PX:
            return None
        return float(np.linalg.norm(P[a] - P[b])) + UV_WEIGHT * uvpx

    def push(a, b):
        c = cost(a, b)
        if c is not None:
            heapq.heappush(heap, (c, rev[a], rev[b], a, b))

    seen = set()
    for fi, f in enumerate(faces):
        vs = vof(f)
        for i in range(3):
            a, b = vs[i], vs[(i + 1) % 3]
            if a == b:
                continue
            k = (min(a, b), max(a, b))
            if k in seen:
                continue
            seen.add(k)
            push(k[0], k[1])

    n_alive = len(faces)
    while heap and n_alive > target_faces:
        c, ra, rb, a, b = heapq.heappop(heap)
        if ra != rev[a] or rb != rev[b]:
            continue
        if not alive_check(a) or not alive_check(b):
            continue
        cur = cost(a, b)
        if cur is None or cur > c * 1.0001 + 1e-9:
            continue
        P[a] = (P[a] + P[b]) / 2.0
        touched = set()
        for fi in set(vert_faces[a]) | set(vert_faces[b]):
            if not alive[fi]:
                continue
            for c2 in faces[fi]:
                if c2[0] == b:
                    c2[0] = a
            vs = vof(faces[fi])
            if len(set(vs)) < 3:
                alive[fi] = False
                n_alive -= 1
                for v in set(vs):
                    vert_faces[v].discard(fi)
            else:
                touched.add(fi)
        vert_faces[b] = set()
        rev[a] += 1
        rev[b] += 1
        for fi in list(vert_faces[a]):
            vs = vof(faces[fi])
            for v in set(vs):
                if v == a:
                    continue
                push(a, v)
                push(v, a)
    return P, [f for fi, f in enumerate(faces) if alive[fi]], sum(1 for x in alive if x)


def src_newline(path):
    """探测源文件的主流行尾（CRLF / LF），输出时保持一致。
    文本模式写入会把 CRLF 翻成 LF，凭空少掉几万字节；这里一律二进制写。"""
    with open(path, 'rb') as f:
        d = f.read(1 << 20)
    crlf = d.count(b'\r\n')
    lf = d.count(b'\n') - crlf
    return '\r\n' if crlf > lf else '\n'


def alive_check(v):
    return True


UVS = []


def main():
    global UVS
    src, dst, ratio = sys.argv[1], sys.argv[2], float(sys.argv[3])
    ratios = {}
    if len(sys.argv) > 4:
        for kv in sys.argv[4].split(','):
            k, v = kv.split('=')
            ratios[k.strip()] = float(v)
    V, VT, VN, groups, order = parse(src)
    print(f"输入: v={len(V)} vt={len(VT)} vn={len(VN)} "
          f"面={sum(len(groups[n]) for n in order)}")

    V, rv = dedupe(V, 4)
    VT, rt = dedupe(VT, 6)
    VN, rn = dedupe(VN, 4)
    print(f"按值去重后: v={len(V)} vt={len(VT)} vn={len(VN)}")

    UVS = VT
    out_faces = {}
    tot = 0
    for n in order:
        fs = [[(rv[c[0]], rt[c[1]], rn[c[2]]) for c in f] for f in groups[n]]
        r = ratios.get(n, ratio)
        tgt = max(4, int(len(fs) * r))
        P, fs2, n_alive = collapse(V, fs, tgt)
        # 只保留该组真正用到的顶点，重新编号
        used = sorted({c[0] for f in fs2 for c in f})
        rmap = {u: i for i, u in enumerate(used)}
        P = [P[u] for u in used]
        fs2 = [[(rmap[c[0]], c[1], c[2]) for c in f] for f in fs2]
        out_faces[n] = (P, fs2)
        tot += len(fs2)
        print(f"  {n:10s} {len(fs):6d} -> {len(fs2):6d} 面  (顶点 {len(P)})")

    # 写 obj：所有组的顶点合并进同一个 v 表（索引偏移）
    allV = []
    lines = ["# simplified"]
    vlines = []
    flines = defaultdict(list)
    off = 0
    for n in order:
        P, fs2 = out_faces[n]
        for p in P:
            vlines.append("v %.6f %.6f %.6f" % (p[0], p[1], p[2]))
        for f in fs2:
            flines[n].append("f " + " ".join(
                "%d/%d/%d" % (c[0] + 1 + off, c[1] + 1, c[2] + 1) for c in f))
        off += len(P)
    out = ["# Created by skydeity mesh simplifier", ""]
    out += vlines
    out.append("# %d vertices" % len(vlines))
    out.append("")
    out += ["vt %.6f %.6f" % p for p in VT]
    out.append("# %d texture vertices" % len(VT))
    out.append("")
    out += ["vn %.5f %.5f %.5f" % p for p in VN]
    out.append("# %d normal vertices" % len(VN))
    out.append("")
    for n in order:
        out.append("g " + n)
        out.append("s 1")
        out += flines[n]
    nl = src_newline(src)
    with open(dst, 'wb') as f:
        f.write((nl.join(out) + nl).encode('utf-8'))
    print(f"输出: 面={tot}  v={len(vlines)} vt={len(VT)} vn={len(VN)} "
          f"行尾={('CRLF' if nl == chr(13) + chr(10) else 'LF')} -> {dst}")


if __name__ == '__main__':
    main()
