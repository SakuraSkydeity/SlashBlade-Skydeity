# -*- coding: utf-8 -*-
"""正确的软渲染封装。

render_obj.Renderer 的 `ss` 参数是坏的（缓冲区按 ss 放大，但调用方给的坐标是
最终空间的 → 物体会缩到 1/ss 并挤在左上角）。这里改成：以 1 倍缓冲区渲染到
(W*ss, H*ss)，再用 PIL 缩小，得到真正的超采样。
"""
import os
import sys
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))   # 与本文件同目录
import render_obj as RO

LIGHT = np.array([0.35, 0.55, 0.75]) / np.linalg.norm([0.35, 0.55, 0.75])


def sub(g, names, xlo=None, xhi=None):
    """按三角形重心的 X 过滤分组。"""
    out = {}
    for n in names:
        ts = g[n]
        if xlo is not None:
            ts = [t for t in ts if np.mean([c[0][0] for c in t]) >= xlo]
        if xhi is not None:
            ts = [t for t in ts if np.mean([c[0][0] for c in t]) <= xhi]
        out[n] = ts
    return out


def auto_fit(meshes, rot, W, H, pad=1.0):
    R = RO.rot_matrix(*rot)
    pts = [R @ c[0] for ts in meshes.values() for t in ts for c in t]
    P = np.array(pts)
    if not len(P):
        return 1.0, 0.0, 0.0
    sc = min(W / ((P[:, 0].max() - P[:, 0].min()) * pad), H / ((P[:, 1].max() - P[:, 1].min()) * pad))
    return sc, (P[:, 0].max() + P[:, 0].min()) / 2, (P[:, 1].max() + P[:, 1].min()) / 2


def render(meshes, rot, W, H, tex, ss=3, fit=None, pad=1.05, shade=False, bg=(52, 56, 66), zbuf=True):
    """meshes: {name: [tri,...]}；tri = ((pos,uv),(pos,uv),(pos,uv))"""
    R = RO.rot_matrix(*rot)
    sc, cx, cy = fit if fit is not None else auto_fit(meshes, rot, W, H, pad)
    r = RO.Renderer(tex, W * ss, H * ss, 1)
    items = []
    for ts in meshes.values():
        for t in ts:
            pr = []
            for pos, _ in t:
                q = R @ pos
                pr.append(((W * ss / 2.0 + (q[0] - cx) * sc * ss), (H * ss / 2.0 - (q[1] - cy) * sc * ss), q[2]))
            if shade:
                e1 = R @ (t[1][0] - t[0][0]); e2 = R @ (t[2][0] - t[0][0])
                nn = np.cross(e1, e2); ln = np.linalg.norm(nn)
                s = 0.45 + 0.55 * (abs(float(nn @ LIGHT) / ln) if ln > 1e-9 else 0.7)
            else:
                s = 1.0
            items.append((float(np.mean([p[2] for p in pr])), pr, [t[0][1], t[1][1], t[2][1]], s))
    if zbuf:
        items.sort(key=lambda o: o[0])
    for _, pr, uv, s in items:
        r.tri(pr, uv, True, s)
    return r.image(bg).resize((W, H), Image.LANCZOS)
