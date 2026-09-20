# -*- coding: utf-8 -*-
"""RGBA -> 调色板 PNG（保留半透明），带 k-means 精修。

    python uvpal.py <in.png> <out.png> [core色数=235] [edge色数=15] [Lloyd轮数=3] [档数=5] [量化器]

在 uvmask9 的基础上加了一步 **Lloyd（k-means）精修**：FASTOCTREE 只保证"每个八叉树叶子的
代表色"，不最小化误差；对 1.28M 像素做 3 轮"最近色分配 + 取均值"能把平均误差降 ~15%，
（columbina：平均 4.08 -> 3.35，>16 的像素 1.92% -> 1.00%），代价 <1s。

结构：
  索引 0          = 全透明（alpha 0）
  1 .. KC         = core（alpha == 255）色，tRNS = 255
  KC+1 .. KC+KE   = edge（0 < alpha < 255）色，按 alpha 分 5 档、每档自己一套色，
                    tRNS = 该档的 alpha 中值
"""
import io
import struct
import sys

import numpy as np
from PIL import Image
from scipy.spatial import cKDTree

SRC = sys.argv[1]
OUT = sys.argv[2]
KC = int(sys.argv[3]) if len(sys.argv) > 3 else 235          # core（不透明）色数
KE = int(sys.argv[4]) if len(sys.argv) > 4 else 15           # edge（半透明）色数
NLLOYD = int(sys.argv[5]) if len(sys.argv) > 5 else 3        # Lloyd 轮数
NB = int(sys.argv[6]) if len(sys.argv) > 6 else 5             # edge alpha 等宽档数
METH = getattr(Image, (sys.argv[7] if len(sys.argv) > 7 else 'FASTOCTREE').upper())
FAR_BLUR = 8
SUB_MAX = 300000                                             # Lloyd 用的子采样上限


def octree(px, k):
    """FASTOCTREE 初版调色板（只用给定像素）。"""
    n = px.shape[0]
    side = int(np.ceil(np.sqrt(n)))
    buf = np.empty((side, side, 3), np.uint8)
    flat = buf.reshape(-1, 3)
    flat[:n] = px
    if side * side > n:
        flat[n:] = px[:side * side - n]
    q = Image.fromarray(buf, 'RGB').quantize(colors=k, method=METH, dither=Image.NONE)
    return np.asarray(q.getpalette()[:k * 3], np.uint8).reshape(k, 3).astype(np.float32)


def build_palette(px, k, rounds=NLLOYD, seed=0):
    """octree 初版 + `rounds` 轮 Lloyd 精修；返回 uint8 调色板 (k,3)。"""
    n = px.shape[0]
    rs = np.random.RandomState(seed)
    sub = px if n <= SUB_MAX else px[rs.choice(n, size=SUB_MAX, replace=False)]
    pal = octree(sub, k)
    if rounds > 0 and k > 1:
        tree = cKDTree(pal)
        for _ in range(rounds):
            _, j = tree.query(sub, workers=-1)          # 分配
            for c in range(k):                          # 更新
                m = j == c
                if m.sum() > 0:
                    pal[c] = sub[m].mean(0)
            pal = np.clip(np.round(pal), 0, 255)
            tree = cKDTree(pal)
    return np.clip(np.round(pal), 0, 255).astype(np.uint8)


def main():
    im = Image.open(SRC).convert('RGBA')
    W, H = im.size
    a = np.asarray(im)
    al = a[..., 3]
    rgb = a[..., :3]
    core = al == 255
    edge = (al > 0) & (al < 255)
    vis = al > 0
    print("尺寸 %dx%d  core %d (%.1f%%)  edge %d (%.2f%%)  透明 %.1f%%"
          % (W, H, core.sum(), core.mean() * 100, edge.sum(), edge.mean() * 100,
             (al == 0).mean() * 100))

    pal_core = build_palette(rgb[core], KC)
    print("  core 调色板 %d 色（octree%s）" % (KC, " + %d 轮 Lloyd" % NLLOYD if NLLOYD else ""))

    # edge：按 alpha 分 NB 个**等宽**档（1..254 均分）。等宽带保证 alpha 误差 <= 半个带宽，
    # 且每档颜色数按该档像素数**按比例**分配（多的多给），最少 2 色。
    bounds = np.unique(np.linspace(1, 255, NB + 1).round().astype(int))
    cand = []
    for bi in range(len(bounds) - 1):
        lo, hi = int(bounds[bi]), int(bounds[bi + 1])
        m = edge & (al >= lo) & (al < hi) if hi < 255 else edge & (al >= lo) & (al <= hi)
        if m.sum() == 0:
            continue
        cand.append((m, lo, hi, float((lo + hi - 1) / 2)))
    # 按像素数比例分配色数（最少 2，且总和 <= KE）
    tot_px = sum(c[0].sum() for c in cand) or 1
    alloc = [max(2, int(round(KE * c[0].sum() / tot_px))) for c in cand]
    while sum(alloc) > KE and max(alloc) > 2:
        alloc[int(np.argmax(alloc))] -= 1
    bands, pal_edge_parts = [], []
    for (m, lo, hi, mid), k in zip(cand, alloc):
        k = min(k, max(2, int(m.sum())))
        bands.append((m, mid, k, lo, hi))
        pal_edge_parts.append(build_palette(rgb[m], k, rounds=min(1, NLLOYD)))
    pal_edge = np.vstack(pal_edge_parts) if pal_edge_parts else np.zeros((0, 3), np.uint8)
    ke = len(pal_edge)
    print("  edge 分 %d 个等宽档（带宽 %d），像素数/色数 %s"
          % (len(bands), (255 - 1) // max(1, len(bands)),
             [(int(b[0].sum()), b[2]) for b in bands]))

    # 透明区 RGB 用邻近可见色填充（防 mipmap/过滤时吸黑）
    small = im.resize((max(1, W // FAR_BLUR), max(1, H // FAR_BLUR)), Image.BILINEAR)
    blur = np.asarray(small.resize((W, H), Image.BILINEAR)).astype(np.uint8)[..., :3]
    fill = rgb.copy()
    fill[~vis] = blur[~vis]

    idx = np.zeros((H, W), np.uint8)                       # 0 = 透明
    if core.any():
        _, j = cKDTree(pal_core.astype(np.int16)).query(fill[core].astype(np.int16), workers=-1)
        idx[core] = (j + 1).astype(np.uint8)
    mean_a = np.zeros(KC + ke, np.float32)
    if edge.any():
        off = 0
        for m, mid, k, lo, hi in bands:
            sub = pal_edge[off:off + k]
            _, j = cKDTree(sub.astype(np.int16)).query(fill[m].astype(np.int16), workers=-1)
            idx[m] = (j + 1 + KC + off).astype(np.uint8)
            mean_a[KC + off:KC + off + k] = mid
            off += k

    palette = np.zeros((256, 3), np.uint8)
    palette[1:1 + KC] = pal_core
    if ke:
        palette[1 + KC:1 + KC + ke] = pal_edge
    trns = bytearray(256)
    trns[0] = 0
    trns[1:1 + KC] = b'\xff' * KC
    if ke:
        trns[1 + KC:1 + KC + ke] = bytes(np.round(mean_a[KC:KC + ke]).clip(0, 255).astype(np.uint8))

    out = Image.fromarray(idx, 'P')
    out.putpalette(palette.tobytes())
    out.info['transparency'] = bytes(trns)
    buf = io.BytesIO()
    out.save(buf, 'PNG', optimize=True, compress_level=9)
    data = buf.getvalue()
    i, info = 8, {}
    while i < len(data):
        ln = struct.unpack('>I', data[i:i + 4])[0]
        t = data[i + 4:i + 8].decode('latin1')
        info[t] = info.get(t, 0) + ln + 12
        i += 12 + ln
    if 'tRNS' not in info:                                  # 兜底：手工插 tRNS
        pos = data.index(b'IDAT') - 4
        ch = b'tRNS' + bytes(trns)
        data = (data[:pos] + struct.pack('>I', len(trns)) + ch
                + struct.pack('>I', __import__('zlib').crc32(ch)) + data[pos:])
        info['tRNS'] = len(trns) + 12
    open(OUT, 'wb').write(data)

    # ---- 校验 ----
    back = np.asarray(Image.open(OUT).convert('RGBA')).astype(int)
    d_rgb = np.abs(back[..., :3] - rgb.astype(int)).max(2)
    d_a = np.abs(back[..., 3] - al.astype(int))
    print("  调色板 %d 项（core %d + edge %d + 透明 1）  文件 %d B (%.3f B/px)  块 %s"
          % (1 + KC + ke, KC, ke, len(data), len(data) / (W * H), info))
    print("  RGB core 平均 %.2f max %d >16 %.3f%% | RGB edge 平均 %.2f max %d"
          % (d_rgb[core].mean(), d_rgb[core].max(), (d_rgb[core] > 16).mean() * 100,
             d_rgb[edge].mean() if edge.any() else 0, d_rgb[edge].max() if edge.any() else 0))
    print("  alpha 平均误差 %.3f max %d ; 半透明保持 %d/%d (%.1f%%)"
          % (d_a[edge].mean() if edge.any() else 0, d_a[edge].max() if edge.any() else 0,
             int(((back[..., 3] > 0) & (back[..., 3] < 255))[edge].sum()), int(edge.sum()),
             ((back[..., 3] > 0) & (back[..., 3] < 255))[edge].mean() * 100 if edge.any() else 100))
    print("  透明区一致: %s" % bool(((back[..., 3] == 0) == (al == 0)).all()))


if __name__ == '__main__':
    main()
