# -*- coding: utf-8 -*-
"""RGBA -> 调色板 PNG，**保留半透明**：<in> <out> [core色数=210] [edge色数=45]

和 uvmask5.py 的区别：uvmask5 把 alpha 当二值（>0 即不透明），遇到"有 1~2% 抗锯齿半透明像素"
的贴图会把那些边缘像素变实。这版把像素分成两拨，各自建板，用 **tRNS 每项一字节 alpha** 表达：

  索引 0        : 全透明（alpha 0）
  1 .. Kc       : core 像素（alpha == 255）量化出的色，tRNS = 255
  Kc+1 .. Kc+Ke : edge 像素（0 < alpha < 255）量化出的色，tRNS = 该桶像素 alpha 的均值

透明区的 RGB 用邻近可见色填充（防 mipmap 吸黑）。
"""
import io
import struct
import sys

import numpy as np
from PIL import Image
from scipy.spatial import cKDTree

SRC = sys.argv[1]
OUT = sys.argv[2]
KC = int(sys.argv[3]) if len(sys.argv) > 3 else 210     # core（不透明）色数
KE = int(sys.argv[4]) if len(sys.argv) > 4 else 45      # edge（半透明）色数
METH = getattr(Image, (sys.argv[5] if len(sys.argv) > 5 else 'FASTOCTREE').upper())
FAR_BLUR = 8


def quantize_colors(px, k):
    """px: (N,3) uint8 -> (k,3) uint8 调色板（FASTOCTREE，只用给定像素）"""
    n = px.shape[0]
    side = int(np.ceil(np.sqrt(n)))
    buf = np.empty((side, side, 3), np.uint8)
    flat = buf.reshape(-1, 3)
    flat[:n] = px
    if side * side > n:          # 只有真的有多余位置才填充（n 恰为完全平方数时 pad 宽度为 0）
        flat[n:] = px[:side * side - n]
    q = Image.fromarray(buf, 'RGB').quantize(colors=k, method=METH, dither=Image.NONE)
    p = q.getpalette()
    return np.asarray(p[:k * 3], np.uint8).reshape(k, 3)


def main():
    im = Image.open(SRC).convert('RGBA')
    W, H = im.size
    a = np.asarray(im)
    al = a[..., 3]
    rgb = a[..., :3]
    core = al == 255
    edge = (al > 0) & (al < 255)
    vis = al > 0
    print("core %d (%.1f%%)  edge %d (%.2f%%)  透明 %.1f%%"
          % (core.sum(), core.mean() * 100, edge.sum(), edge.mean() * 100, (al == 0).mean() * 100))

    pal_core = quantize_colors(rgb[core], KC)
    # ★ edge 像素按 alpha 分成 3 档，每档各自量化颜色：
    #   同档内 alpha 接近 -> tRNS 取档均值时误差小（不分档时误差可达 80）。
    BANDS = [((1, 51), 26), ((51, 102), 76), ((102, 153), 127), ((153, 204), 178), ((204, 255), 229)]
    pal_edge_parts, band_a, band_of = [], [], []
    per = max(1, KE // len(BANDS))
    for bi, ((lo, hi), av) in enumerate(BANDS):
        m = edge & (al >= lo) & (al < hi)
        if m.sum() == 0:
            continue
        k = min(per, max(2, int(m.sum())))
        pal_edge_parts.append(quantize_colors(rgb[m], k))
        band_of.append((bi, int(m.sum()), k))
        band_a.append((bi, av, m))
    pal_edge = np.vstack(pal_edge_parts) if pal_edge_parts else np.zeros((0, 3), np.uint8)
    ke = len(pal_edge)

    # 透明区 RGB 填充（邻近可见色），只为防过滤时吸黑
    small = im.resize((max(1, W // FAR_BLUR), max(1, H // FAR_BLUR)), Image.BILINEAR)
    blur = np.asarray(small.resize((W, H), Image.BILINEAR)).astype(np.uint8)[..., :3]
    fill = rgb.copy()
    fill[~vis] = blur[~vis]

    idx = np.zeros((H, W), np.uint8)                      # 0 = 透明
    if core.any():
        _, j = cKDTree(pal_core.astype(np.int16)).query(fill[core].astype(np.int16), workers=-1)
        idx[core] = (j + 1).astype(np.uint8)
    mean_a = np.zeros(KC + ke, np.float32)
    if edge.any():
        off = 0
        for bi, n_px, k in band_of:
            m = edge & (al >= BANDS[bi][0][0]) & (al < BANDS[bi][0][1])
            sub = pal_edge[off:off + k]
            _, j = cKDTree(sub.astype(np.int16)).query(fill[m].astype(np.int16), workers=-1)
            idx[m] = (j + 1 + KC + off).astype(np.uint8)
            mean_a[KC + off:KC + off + k] = BANDS[bi][1]
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
    # 校验 tRNS 是否真的写进去了
    i, info = 8, {}
    while i < len(data):
        ln = struct.unpack('>I', data[i:i + 4])[0]
        info[data[i + 4:i + 8].decode('latin1')] = info.get(data[i + 4:i + 8].decode('latin1'), 0) + ln + 12
        i += 12 + ln
    if 'tRNS' not in info:                                # 兜底：手工插 tRNS
        pos = data.index(b'IDAT') - 4
        ch = b'tRNS' + bytes(trns)
        data = data[:pos] + struct.pack('>I', len(trns)) + ch + struct.pack('>I', __import__('zlib').crc32(ch)) + data[pos:]
        info['tRNS'] = len(trns) + 12
    open(OUT, 'wb').write(data)

    # ---- 校验 ----
    back = np.asarray(Image.open(OUT).convert('RGBA')).astype(int)
    d_rgb = np.abs(back[..., :3] - rgb.astype(int)).max(2)
    d_a = np.abs(back[..., 3] - al.astype(int))
    print("调色板 %d 项（core %d + edge %d + 透明 1）  块: %s  文件 %d B"
          % (1 + KC + ke, KC, ke, info, len(data)))
    print("RGB core 平均 %.2f max %d >16 %.3f%% | RGB edge 平均 %.2f"
          % (d_rgb[core].mean(), d_rgb[core].max(), (d_rgb[core] > 16).mean() * 100, d_rgb[edge].mean()))
    print("alpha 平均误差 %.3f max %d ; 半透明像素保持 %d/%d (%.1f%%)"
          % (d_a[edge].mean(), d_a[edge].max(),
             int(((back[..., 3] > 0) & (back[..., 3] < 255))[edge].sum()), int(edge.sum()),
             ((back[..., 3] > 0) & (back[..., 3] < 255))[edge].mean() * 100))
    print("透明区一致:", bool(((back[..., 3] == 0) == (al == 0)).all()))


if __name__ == '__main__':
    main()
