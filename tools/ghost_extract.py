#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从用户上传的 60x60 图标中提取「中间幽灵」主体，输出透明背景 PNG 给游戏作 billboard 贴图。
只保留幽灵本身：剔除外圈橙红圆环与深色圆形背景。
输出：assets/skydeityslash/effects/ghost/ghost.png（游戏资源） + tools/ghost_preview.png
"""
from PIL import Image, ImageFilter
import math, colorsys, os

SRC = r"c:\Users\Sky deity\.trae-cn\attachments\6aa0258d054a0a635bc0c0ab\23c33cf4-94ca-4367-96b1-7944c1d686bf_cd89f914-eb97-468b-b1c3-342bfa113f2e_image.png"
RES_DIR = r"D:\.codepy\trea\slash\SkydeitySlash_1.20\src\main\resources\assets\skydeityslash\effects\ghost"
PREVIEW = r"D:\.codepy\trea\slash\SkydeitySlash_1.20\tools\ghost_preview.png"

TARGET = 128          # 游戏贴图边长（幂率无关，Minecraft 贴图需 2 的幂，128 即可）
BG_LUM = 95           # 亮度低于此视为深背景/外环衬底
RADIUS = 20           # 距幽灵中心最大半径（剔除远距的外圈圆环）


def main():
    im = Image.open(SRC).convert("RGB")
    W, H = im.size
    px = im.load()
    cx = (W - 1) / 2.0
    cy = (H - 1) / 2.0

    # 亮度图
    lum = [[0] * W for _ in range(H)]
    for y in range(H):
        for x in range(W):
            r, g, b = px[x, y]
            lum[y][x] = (r + g + b) / 3.0

    # 从中心 flood-fill：只走「非深背景」像素，得到与幽灵连通的区域（外环隔黑，进不来）
    start = int(round(cx)), int(round(cy))
    visit = [[False] * W for _ in range(H)]
    stack = [start]
    visit[start[1]][start[0]] = True
    while stack:
        x, y = stack.pop()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < W and 0 <= ny < H and not visit[ny][nx]:
                if lum[ny][nx] >= BG_LUM:
                    visit[ny][nx] = True
                    stack.append((nx, ny))

    # 距离窗半径剔除：去掉被蔓延进来的外环亮像素
    def dist(x, y):
        return math.hypot(x - cx, y - cy)

    mask = [[0] * W for _ in range(H)]       # 0=透明
    xs, ys = [], []
    for y in range(H):
        for x in range(W):
            if visit[y][x] and dist(x, y) <= RADIUS:
                mask[y][x] = 1
                xs.append(x); ys.append(y)
    if not xs:
        raise SystemExit("empty ghost mask")
    minx, maxx, miny, maxy = min(xs), max(xs), min(ys), max(ys)

    # 构建带 alpha 的图像（边缘 1px 羽化）
    Wd, Hd = (maxx - minx + 1), (maxy - miny + 1)
    out = Image.new("RGBA", (Wd, Hd), (0, 0, 0, 0))
    opx = out.load()
    for y in range(H):
        for x in range(W):
            if mask[y][x]:
                r, g, b = px[x, y]
                a = 255
                # 外轮廓羽化：越靠近 mask 边界透明度越低
                for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0), (-1, -1), (1, 1), (-1, 1), (1, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < W and 0 <= ny < H and not mask[ny][nx]:
                        a = min(a, 150)
                opx[x - minx, y - miny] = (r, g, b, a)

    # 缩放至目标边长（双三次，兼顾 alpha 平滑）
    scale = TARGET / max(Wd, Hd)
    nw, nh = max(1, round(Wd * scale)), max(1, round(Hd * scale))
    out = out.resize((nw, nh), Image.LANCZOS)

    # 居中放入 128x128 正方形画布
    canvas = Image.new("RGBA", (TARGET, TARGET), (0, 0, 0, 0))
    offx = (TARGET - nw) // 2
    offy = (TARGET - nh) // 2
    canvas.paste(out, (offx, offy), out)

    os.makedirs(RES_DIR, exist_ok=True)
    res_path = os.path.join(RES_DIR, "ghost.png")
    canvas.save(res_path)
    canvas.save(PREVIEW)
    print("ghost bbox x[%d..%d] y[%d..%d]" % (minx, maxx, miny, maxy))
    print("ld size %dx%d -> scaled %dx%d aspect=%.4f" % (Wd, Hd, nw, nh, nw / float(nh)))
    print("saved", res_path, os.path.getsize(res_path), "bytes")
    print("aspect (w/h) for renderer = %.6f" % (nw / float(nh)))


if __name__ == "__main__":
    main()