# -*- coding: utf-8 -*-
"""compactE：OBJ 极限瘦身 = 精度 + 去重 + 法线聚类 + 删孤儿 + 索引紧凑重排

用法: python compactE.py <in.obj> <out.obj> [v位=1] [vt位=3] [vn位=2] [法线阈值度=1.0]

比 compactD 多两件事（都是为了把 f 行的索引位数压下来）：
  1. **法线聚类**：把夹角在阈值内的 vn 合并成一个（引用加权平均）。模型的 vn 往往有
     1100+ 种，只要压到 <=999，f 行的 vn 索引就从 4 位变 3 位，每行省 3 字符。
     实测 1.0° 阈值可压到 ~908 簇、最大角偏差 0.95°（亮度差约 2.5/255，和 vn 2 位
     本身的量化误差同级）。
  2. **删孤儿 + 紧凑重排**：只保留被 f 引用到的 v/vt/vn，重新编号。

⚠ 定点格式化，禁止 str(float)（会写出 1e-05，SlashBlade 正则不认 -> 条目被丢 -> 索引错位）。
⚠ 二进制写、CRLF。
"""
import sys

import numpy as np

src = sys.argv[1]
dst = sys.argv[2]
NV = int(sys.argv[3]) if len(sys.argv) > 3 else 1
NT = int(sys.argv[4]) if len(sys.argv) > 4 else 3
NN = int(sys.argv[5]) if len(sys.argv) > 5 else 2
THR = float(sys.argv[6]) if len(sys.argv) > 6 else 1.0
DO_CLUS = True


def num(x, nd):
    s = ('%.*f' % (nd, float(x)))
    if '.' in s:
        s = s.rstrip('0')
        if s.endswith('.'):
            s += '0'
    return s


fmt = {'v': NV, 'vt': NT, 'vn': NN}
raw = open(src, 'rb').read().decode('utf-8')
lines = raw.splitlines()

# ---------------- pass 1: 精度 + 去重 ----------------
seen = {'v': {}, 'vt': {}, 'vn': {}}
maps = {'v': [], 'vt': [], 'vn': []}
tables = {'v': [], 'vt': [], 'vn': []}          # 每项是格式化后的字符串
skeleton = []
faces = []
dropped = {'v': 0, 'vt': 0, 'vn': 0}

for line in lines:
    kind = line.split(' ', 1)[0] if line else ''
    if kind in fmt:
        p = line.split()
        vals = [num(t, fmt[kind]) for t in p[1:]]
        key = ' '.join(vals)
        if key in seen[kind]:
            maps[kind].append(seen[kind][key])
            dropped[kind] += 1
            continue
        idx = len(tables[kind])
        seen[kind][key] = idx
        maps[kind].append(idx)
        tables[kind].append(key)
        skeleton.append((kind, idx))
    elif kind == 'f':
        p = line.split()
        tri = []
        for t in p[1:]:
            a, b, c = t.split('/')
            tri.append((maps['v'][int(a) - 1], maps['vt'][int(b) - 1], maps['vn'][int(c) - 1]))
        skeleton.append(('f', len(faces)))
        faces.append(tri)
    else:
        skeleton.append(('raw', line))

n_v0, n_t0, n_n0 = len(tables['v']), len(tables['vt']), len(tables['vn'])

# ---------------- pass 2: vn 聚类 ----------------
ref = np.zeros(n_n0, int)
for tri in faces:
    for a, b, c in tri:
        ref[c] += 1
n_used_vn = int((ref > 0).sum())

old_to_new_vn = None
n_clus = None
if DO_CLUS and n_used_vn > 0:
    VN = np.array([[float(x) for x in s.split()] for s in tables['vn']], float)
    ln = np.linalg.norm(VN, axis=1)
    U = VN / np.maximum(ln[:, None], 1e-12)
    used_idx = np.nonzero(ref > 0)[0]
    w = ref[used_idx].astype(float)
    uu = U[used_idx]
    order = np.argsort(-w)
    thr = np.cos(np.radians(THR))
    cents = []
    lab = -np.ones(len(uu), int)
    for k in order:
        v = uu[k]
        if cents:
            d = np.asarray(cents) @ v
            j = int(np.argmax(d))
            if d[j] >= thr:
                lab[k] = j
                continue
        lab[k] = len(cents)
        cents.append(v.copy())
    C = np.asarray(cents)
    for _ in range(4):                       # 迭代精修
        a = np.argmax(uu @ C.T, axis=1)
        for j in range(len(C)):
            m = a == j
            if not m.any():
                continue
            v = (uu[m] * w[m, None]).sum(0)
            nn = np.linalg.norm(v)
            if nn > 1e-12:
                C[j] = v / nn
        lab = np.argmax(uu @ C.T, axis=1)
    dev = np.degrees(np.arccos(np.clip(np.einsum('ij,ij->i', uu, C[lab]), -1, 1)))
    # 簇代表 -> 格式化 -> 合并重复字面值
    new_seen, new_tab = {}, []
    old_to_new_vn = {}
    for k, ci in enumerate(lab):
        key = ' '.join(num(x, NN) for x in C[ci])
        if key not in new_seen:
            new_seen[key] = len(new_tab)
            new_tab.append(key)
        old_to_new_vn[int(used_idx[k])] = new_seen[key]
    n_clus = len(new_tab)
    print("vn 聚类: %d 项(被引用 %d) -> %d 簇（阈值 %.1f°）；角偏差 平均 %.3f° max %.2f°"
          % (n_n0, n_used_vn, n_clus, THR, dev.mean(), dev.max()))
    tables['vn'] = new_tab
    for tri in faces:
        for i, (a, b, c) in enumerate(tri):
            tri[i] = (a, b, old_to_new_vn[c])

# ---------------- pass 3: 删孤儿 + 紧凑重排 ----------------
use_v, use_t, use_n = set(), set(), set()
for tri in faces:
    for a, b, c in tri:
        use_v.add(a)
        use_t.add(b)
        use_n.add(c)


def remap(tab, used):
    used = sorted(used)
    m = {o: i for i, o in enumerate(used)}
    return [tab[o] for o in used], m


tabV, mapV = remap(tables['v'], use_v)
tabT, mapT = remap(tables['vt'], use_t)
tabN, mapN = remap(tables['vn'], use_n)
for tri in faces:
    for i, (a, b, c) in enumerate(tri):
        tri[i] = (mapV[a], mapT[b], mapN[c])

# ---------------- 写回 ----------------
out = []
fi = 0
for kind, payload in skeleton:
    if kind == 'raw':
        out.append(payload)
    elif kind == 'f':
        tri = faces[payload]
        toks = ['%d/%d/%d' % (a + 1, b + 1, c + 1) for a, b, c in tri]
        out.append('f ' + ' '.join(toks))
        fi += 1
    # v/vt/vn 行统一在最后重排输出（见下）

# 重建：骨架里的 v/vt/vn 位置按新表顺序填
cnt = {'v': 0, 'vt': 0, 'vn': 0}
final = []
for kind, payload in skeleton:
    if kind in ('v', 'vt', 'vn'):
        continue                              # 稍后统一插入
    final.append((kind, payload))

body = []
for kind, payload in final:
    if kind == 'raw':
        s = payload.strip()
        if s and not s.startswith('#'):       # 丢掉空行与旧注释（表头注释统一重写）
            body.append(payload)
    elif kind == 'f':
        tri = faces[payload]
        body.append('f ' + ' '.join('%d/%d/%d' % (a + 1, b + 1, c + 1) for a, b, c in tri))

# 组装：头部注释 + v 表 + vt 表 + vn 表 + 分组/面
res = []
res.append("# compactE: v%d vt%d vn%d（法线聚类 %.1f°）" % (NV, NT, NN, THR))
res += ["v " + s for s in tabV]
res.append("# %d vertices" % len(tabV))
res.append("")
res += ["vt " + s for s in tabT]
res.append("# %d texture vertices" % len(tabT))
res.append("")
res += ["vn " + s for s in tabN]
res.append("# %d normal vertices" % len(tabN))
res.append("")
res += body

open(dst, 'wb').write(("\r\n".join(res) + "\r\n").encode('utf-8'))
sz = len(open(dst, 'rb').read())
print("表:  v %4d(原 %4d)  vt %3d(原 %3d)  vn %4d(原 %4d)   面 %d" % (
    len(tabV), n_v0, len(tabT), n_t0, len(tabN), n_n0, len(faces)))
print("去重删行: v %d vt %d vn %d   输出 %d B（输入 %d B）" % (
    dropped['v'], dropped['vt'], dropped['vn'], sz, len(raw.encode('utf-8'))))
mx = [max(mapV.values()) + 1, max(mapT.values()) + 1, max(mapN.values()) + 1]
print("索引最大值: v %d  vt %d  vn %d   -> 位数 %s" % (
    mx[0], mx[1], mx[2], [len(str(k)) for k in mx]))
