# -*- coding: utf-8 -*-
"""Generic textured OBJ software renderer (numpy z-buffer) for SlashBlade blade models."""
import math
import sys
from collections import OrderedDict

import numpy as np
from PIL import Image, ImageDraw


def parse_obj(path):
    v = []
    vt = []
    groups = OrderedDict()
    cur = None
    for line in open(path, errors='replace'):
        if line.startswith('v '):
            p = line.split()
            v.append((float(p[1]), float(p[2]), float(p[3])))
        elif line.startswith('vt '):
            p = line.split()
            vt.append((float(p[1]), float(p[2])))
        elif line.startswith('g ') or line.startswith('o '):
            name = line.split(None, 1)[1].strip()
            if name in groups:
                name = name + '#2'
            groups[name] = []
            cur = name
        elif line.startswith('f ') and cur:
            toks = line.split()[1:]
            tri = []
            for t in toks:
                s = t.split('/')
                vi = int(s[0]) - 1
                ti = int(s[1]) - 1 if len(s) > 1 and s[1] else -1
                tri.append((np.array(v[vi], np.float64), (vt[ti] if ti >= 0 else (0.0, 0.0))))
            for k in range(1, len(tri) - 1):
                groups[cur].append([tri[0], tri[k], tri[k + 1]])
    return groups


def load_tex(path):
    im = Image.open(path).convert('RGB')
    return np.asarray(im, np.float32), im.size


def rot_matrix(ax, ay, az):
    ax, ay, az = math.radians(ax), math.radians(ay), math.radians(az)
    Rx = np.array([[1, 0, 0], [0, math.cos(ax), -math.sin(ax)], [0, math.sin(ax), math.cos(ax)]])
    Ry = np.array([[math.cos(ay), 0, math.sin(ay)], [0, 1, 0], [-math.sin(ay), 0, math.cos(ay)]])
    Rz = np.array([[math.cos(az), -math.sin(az), 0], [math.sin(az), math.cos(az), 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


class Renderer:
    def __init__(self, tex, W, H, ss=2):
        self.tex = tex
        self.TH, self.TW = tex.shape[:2]
        self.W, self.H, self.ss = W, H, ss
        self.C = np.zeros((H * ss, W * ss, 3), np.float32)
        self.Z = np.full((H * ss, W * ss), -1e9, np.float32)
        self.M = np.zeros((H * ss, W * ss), bool)

    def tri(self, p, uv, flip_v, shade=1.0):
        W, H, ss = self.W * self.ss, self.H * self.ss, self.ss
        px = [q[0] for q in p]
        py = [q[1] for q in p]
        minx = max(0, int(math.floor(min(px))) - 1)
        maxx = min(W - 1, int(math.ceil(max(px))) + 1)
        miny = max(0, int(math.floor(min(py))) - 1)
        maxy = min(H - 1, int(math.ceil(max(py))) + 1)
        if minx > maxx or miny > maxy:
            return
        X, Y = np.meshgrid(np.arange(minx, maxx + 1, dtype=np.float64) + 0.5,
                           np.arange(miny, maxy + 1, dtype=np.float64) + 0.5)
        den = (py[1] - py[2]) * (px[0] - px[2]) + (px[2] - px[1]) * (py[0] - py[2])
        if abs(den) < 1e-12:
            return
        w0 = ((py[1] - py[2]) * (X - px[2]) + (px[2] - px[1]) * (Y - py[2])) / den
        w1 = ((py[2] - py[0]) * (X - px[2]) + (px[0] - px[2]) * (Y - py[2])) / den
        w2 = 1.0 - w0 - w1
        m = (w0 >= -1e-5) & (w1 >= -1e-5) & (w2 >= -1e-5)
        if not m.any():
            return
        zz = w0 * p[0][2] + w1 * p[1][2] + w2 * p[2][2]
        sub_z = self.Z[miny:maxy + 1, minx:maxx + 1]
        keep = m & (zz > sub_z)
        if not keep.any():
            return
        uu = w0 * uv[0][0] + w1 * uv[1][0] + w2 * uv[2][0]
        vv = w0 * uv[0][1] + w1 * uv[1][1] + w2 * uv[2][1]
        tx = np.clip((uu * self.TW).astype(int), 0, self.TW - 1)
        ty = np.clip(((1.0 - vv if flip_v else vv) * self.TH).astype(int), 0, self.TH - 1)
        col = self.tex[ty[keep], tx[keep]] / 255.0 * shade
        tgt = self.C[miny:maxy + 1, minx:maxx + 1]
        tgt[keep] = col
        sub_z[keep] = zz[keep]
        self.M[miny:maxy + 1, minx:maxx + 1][keep] = True

    def image(self, bg=(255, 255, 255)):
        img = self.C.copy()
        mask = ~self.M
        img[mask] = np.array(bg, np.float32) / 255.0
        out = Image.fromarray(np.clip(img * 255, 0, 255).astype(np.uint8))
        return out.resize((self.W, self.H), Image.LANCZOS)


def fit_scale(points, W, H, pad=24):
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    w = max(xs) - min(xs)
    h = max(ys) - min(ys)
    return min((W - pad * 2) / max(w, 1e-6), (H - pad * 2) / max(h, 1e-6)), (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2


def render_groups(groups, names, tex, W, H, rot=(0, 0, 0), flip_v=True, ss=2, shade=True,
                  bg=(255, 255, 255), zoom=1.0, label=None, axes=True):
    R = rot_matrix(*rot)
    pts = []
    for n in names:
        for t in groups[n]:
            for pos, _ in t:
                q = R @ pos
                pts.append((q[0], q[1], q[2]))
    scale, cx, cy = fit_scale(pts, W, H)
    scale *= zoom
    r = Renderer(tex, W, H, ss)
    light = np.array([0.35, 0.55, 0.75])
    light = light / np.linalg.norm(light)
    for n in names:
        for t in groups[n]:
            pr = []
            for pos, _ in t:
                q = R @ pos
                pr.append(((W / 2.0 + (q[0] - cx) * scale), (H / 2.0 - (q[1] - cy) * scale), q[2]))
            uv = [t[0][1], t[1][1], t[2][1]]
            if shade:
                e1 = R @ (t[1][0] - t[0][0])
                e2 = R @ (t[2][0] - t[0][0])
                nn = np.cross(e1, e2)
                ln = np.linalg.norm(nn)
                l = abs(float(nn @ light) / ln) if ln > 1e-9 else 0.7
                s = 0.45 + 0.55 * l
            else:
                s = 1.0
            r.tri(pr, uv, flip_v, s)
    return r.image(bg)


if __name__ == '__main__':
    pass
