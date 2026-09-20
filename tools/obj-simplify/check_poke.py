# -*- coding: utf-8 -*-
"""露刃检测：把「远离刀镡的那段刀身」与「剑鞘」用**同一变换**分别光栅化，比较剪影。

只要刀身剪影有落在鞘剪影之外的部分，就说明鞘没包住刀身（收纳状态下会看到刀身露出来）。
这是鞘改形（变细/收窄/收尖）的**最终裁判** —— 顶点级的"窗口统计"在低模上会出假数据。

用法：
    python check_poke.py <obj> [--tex <png>] [--inner blade] [--outer sheath]
                                 [--far -62] [--views 0,90] [--out <png>] [--ss 2]

  <obj>      要检查的 obj（需要有 --inner / --outer 两个分组）
  --tex      贴图，仅影响调试图的颜色，可省略（默认纯白）
  --far      只检查 X < far 的「刀身段」，避免把刀镡/护手算成露刃（默认 -62）
  --views    两个视角的绕 X 轴角度（角度1,角度2），默认 0,90（侧视/俯视）
  --out      输出调试图：灰=鞘、绿=被包住的刀身、红=露刃

退出码：0 = 没有露刃；1 = 检测到露刃。
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_obj as RO


def flat_tex():
    return np.full((4, 4, 3), 255, np.float32)


def silhouette(mesh_keys, meshes, tex, rot, W, H, ss, fit_pts):
    """把若干网格按给定相机光栅化，返回最终分辨率下的 bool 掩码。"""
    R = RO.rot_matrix(*rot)
    scale, cx, cy = RO.fit_scale([(q[0], q[1]) for q in fit_pts], W, H)
    scale *= 1.15
    r = RO.Renderer(tex, W * ss, H * ss, 1)          # ss=1 缓冲区 + 自己放大坐标 = 真超采样
    for k in mesh_keys:
        for t in meshes[k]:
            pr = []
            for pos, _uv in t:
                q = R @ pos
                pr.append((W * ss / 2.0 + (q[0] - cx) * scale * ss,
                           H * ss / 2.0 - (q[1] - cy) * scale * ss, q[2]))
            r.tri(pr, [t[0][1], t[1][1], t[2][1]], True, 1.0)
    return r.M.reshape(H, ss, W, ss).any(axis=(1, 3))   # ss×ss 取"或"降采样


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('obj')
    ap.add_argument('--tex', default=None)
    ap.add_argument('--inner', default='blade')
    ap.add_argument('--outer', default='sheath')
    ap.add_argument('--far', type=float, default=-62.0)
    ap.add_argument('--views', default='0,90')
    ap.add_argument('--out', default=None)
    ap.add_argument('--ss', type=int, default=2)
    ap.add_argument('-W', type=int, default=1400)
    ap.add_argument('-H', type=int, default=200)
    a = ap.parse_args()

    g = RO.parse_obj(a.obj)
    for nm in (a.inner, a.outer):
        if nm not in g:
            print("!! obj 里没有分组 %r；现有分组：%s" % (nm, list(g)))
            return 2
    tex = RO.load_tex(a.tex)[0] if a.tex else flat_tex()

    inner_far = {'__x__': [t for t in g[a.inner] if max(c[0][0] for c in t) < a.far]}
    outer = {'__o__': g[a.outer]}
    if not inner_far['__x__']:
        print("!! X < %.1f 的 %s 分组里没有面（--far 是不是给错了？）" % (a.far, a.inner))
        return 2

    angles = [float(v) for v in a.views.replace('，', ',').split(',') if v.strip()]
    total, tiles = 0, []
    for ang in angles:
        rot = (ang, 0, 0)
        R = RO.rot_matrix(*rot)
        fit_pts = [R @ c[0] for n in (a.inner, a.outer) for t in g[n] for c in t]
        ms = silhouette(['__o__'], outer, tex, rot, a.W, a.H, a.ss, fit_pts)
        mb = silhouette(['__x__'], inner_far, tex, rot, a.W, a.H, a.ss, fit_pts)
        poke = mb & (~ms)
        pct = poke.sum() / max(mb.sum(), 1) * 100
        total += int(poke.sum())
        print("angle=%-4g 鞘剪影 %7d px  刀身剪影 %7d px  落在鞘外 %6d px (%.3f%%)"
              % (ang, ms.sum(), mb.sum(), poke.sum(), pct))
        if poke.any():
            ys, xs = np.nonzero(poke)
            print("           露刃位置(屏幕) X %d..%d  Y %d..%d" % (xs.min(), xs.max(), ys.min(), ys.max()))
        if a.out:
            im = np.zeros((a.H, a.W, 3), np.uint8)
            im[ms] = (60, 70, 90)
            im[mb & ms] = (60, 130, 90)
            im[poke] = (230, 60, 60)
            tiles.append(Image.fromarray(im))

    if a.out and tiles:
        out = Image.new('RGB', (a.W, a.H * len(tiles)))
        for i, t in enumerate(tiles):
            out.paste(t, (0, i * a.H))
        out.save(a.out)
        print("调试图 -> %s（灰=鞘 绿=被鞘包住的刀身 红=露刃）" % a.out)

    print("=> %s" % ("无露刃 ✓" if total == 0 else "检测到露刃 %d px ✗" % total))
    return 0 if total == 0 else 1


if __name__ == '__main__':
    sys.exit(main())
