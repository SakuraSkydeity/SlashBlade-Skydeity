# -*- coding: utf-8 -*-
"""RGBA -> 调色板 + 透明索引 PNG（**色位全留给可见像素**）。

和 uvmask3.py 的区别：那版把整图（含 51.7% 透明像素的填充色）一起量化，
一半调色板被浪费；这版只用「可见像素」建调色板，再把所有像素（可见=原色，
透明=邻近色填充，避免 mipmap 吸黑）映射到最近调色板项。

透明仍用**标准写法**：索引 0 = 透明，PLTE 补满 256 项，tRNS 单字节标记 0。
"""
import os
import struct
import sys

import numpy as np
from PIL import Image
from scipy.spatial import cKDTree

SRC = sys.argv[1]
OUT = sys.argv[2]
METH_NAME = (sys.argv[3] if len(sys.argv) > 3 else 'FASTOCTREE').upper()
METHOD = getattr(Image, METH_NAME)
NCOL = int(sys.argv[4]) if len(sys.argv) > 4 else 255
FAR_BLUR = 8


def main():
    im = Image.open(SRC).convert('RGBA')
    W, H = im.size
    a = np.asarray(im)
    vis = a[..., 3] > 0
    rgb = a[..., :3].astype(np.float32)

    # 透明区的 RGB：用邻近可见色填充（否则 mipmap 过滤会把边缘吸暗）
    small = im.resize((max(1, W // FAR_BLUR), max(1, H // FAR_BLUR)), Image.BILINEAR)
    blur = np.asarray(small.resize((W, H), Image.BILINEAR)).astype(np.float32)[..., :3]
    fill = rgb.copy()
    fill[~vis] = blur[~vis]

    # ---- 只用可见像素建调色板 ----
    px = a[..., :3][vis].astype(np.uint8)          # (N,3)
    N = px.shape[0]
    side = int(np.ceil(np.sqrt(N)))
    buf = np.empty((side, side, 3), np.uint8)
    flat = buf.reshape(-1, 3)
    flat[:N] = px
    flat[N:] = px[:side * side - N]                # 填充位用可见色循环填，别用黑占色位
    q = Image.fromarray(buf, 'RGB').quantize(colors=NCOL, method=METHOD, dither=Image.NONE)
    pal = np.asarray(q.getpalette()[:NCOL*3], np.uint8).reshape(NCOL, 3)

    # ---- 全图映射到最近调色板色 ----
    tree = cKDTree(pal.astype(np.int16))
    _, idx = tree.query(fill.reshape(-1, 3).astype(np.int16), workers=-1)
    idx = idx.reshape(H, W).astype(np.uint16)

    data = np.where(vis, idx + 1, 0).astype(np.uint8)     # 0 = 透明
    out = Image.fromarray(data, 'P')
    out.putpalette(bytes([0, 0, 0]) + pal.tobytes())
    out.info['transparency'] = 0
    out.save(OUT, optimize=True, compress_level=9)

    # ---- 校验 ----
    chk = Image.open(OUT)
    back = np.asarray(chk.convert('RGBA'))
    print("方法 %s 色数 %d；模式 %s 尺寸 %s 调色板 %d 项 透明索引 %s" % (METH_NAME, NCOL,
          chk.mode, chk.size, len(chk.getpalette()) // 3, chk.info.get('transparency')))
    print("透明区与预期一致:", np.array_equal(back[..., 3] > 0, vis),
          " 透明像素 %.1f%%" % ((back[..., 3] == 0).mean() * 100))
    dd = np.abs(back[..., :3].astype(np.int16) - a[..., :3].astype(np.int16)).max(2)[vis]
    print("可见区量化误差：平均 %.2f 最大 %d >16 占比 %.3f%%" % (dd.mean(), dd.max(), (dd > 16).mean() * 100))
    d = open(OUT, 'rb').read()
    i, info = 8, {}
    while i < len(d):
        ln = struct.unpack('>I', d[i:i + 4])[0]
        info[d[i + 4:i + 8].decode('latin1')] = info.get(d[i + 4:i + 8].decode('latin1'), 0) + ln + 12
        i += 12 + ln
    print("PNG 块(总长):", info, " 文件 %d B" % len(d))


if __name__ == '__main__':
    main()
