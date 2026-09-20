# tex-compress —— 贴图 PNG 压缩（调色板 + 保留 alpha）

把 RGBA 贴图压成 **256 色调色板 PNG**，同时**保留半透明**（用 tRNS 逐项 alpha）。
典型效果：2048×1024 的 1.5 MB RGBA → **350 KB**，分辨率不降、羽化边不变实。

## 依赖

```
C:\Users\Sky deity\AppData\Local\Programs\Python\Python311\python.exe   # numpy / Pillow / scipy
```

## 用哪个

| 脚本 | 适用 | 命令 |
|---|---|---|
| **`uvpal.py`** | **默认首选**。贴图里有半透明像素（羽化描边）时用这个，效果最好 | `python uvpal.py <in.png> <out.png> [core=235] [edge=15] [Lloyd=3] [档数=5] [量化器=FASTOCTREE]` |
| `uvmask9.py` | 老版本（无 Lloyd 精修），保留作对照 | `python uvmask9.py <in.png> <out.png> [KC=210] [KE=45]` |
| `uvmask5.py` | **alpha 是二值的**（只有全透明/不透明）时用，更简单 | `python uvmask5.py <in.png> <out.png> [量化器=FASTOCTREE] [色数=255]` |

推荐参数：`uvpal.py in.png out.png 228 25 3 8`

## 原理

```
索引 0            → 全透明（alpha 0）
1 .. KC           → core（alpha == 255）像素量化出的色，tRNS = 255
KC+1 .. KC+KE     → edge（0 < alpha < 255）像素，按 alpha 分 8 个**等宽带**、
                    每档各自一套色，tRNS = 该档 alpha 中值（带宽 31 ⇒ alpha 误差 ≤16）
```

三个关键点（都是踩过的坑）：

1. **edge 必须按 alpha 分档**。不分档时整段羽化的 alpha 误差可达 80；
   也**不能用分位数分档** —— alpha 分布通常极偏（多数羽化像素在高位），
   分位数会把 1..121 并成一档，误差飙到 121。**必须用等宽带**。
2. **Lloyd（k-means）精修**值得做：`FASTOCTREE` 只给"八叉树叶子代表色"、不最小化误差；
   对子采样做 3 轮"最近色分配 + 取均值"后，columbina 平均误差 4.08→3.35、>16 由 1.92%→1.00%，耗时 <1s。
3. **量化器选 `FASTOCTREE`，别用 `MEDIANCUT`**（同一张图：>16 1.92% vs 5.69%，且体积更小）。

透明区的 RGB 用邻近可见色填充（防 mipmap/过滤时吸黑出暗边），alpha=0 本身不变。

## 输出结构（必须是这个规格）

```
颜色类型 3（调色板） · 位深 8 · PLTE 满 256 项 · tRNS 256 字节
```

`PLTE` 一定要补满 256 项、`tRNS` 一定要写全 256 字节 —— PIL 默认写法会生成
"PLTE 255 项 + tRNS 255 字节"，也就是**用到的索引 255 根本没定义**，游戏端解码器可能不认。

## 为什么必须保留半透明

刀模走的是 `RenderStateShard.f_110139_` = **`TRANSLUCENT_TRANSPARENCY`**（见
`BladeRenderState.getSlashBladeBlend`），也就是 **alpha 混合**。压成二值 alpha 会让羽化边变实。

## 脚本自带校验

每个脚本跑完会直接打印：调色板项数、文件大小、`RGB core/edge 平均与 max`、
`>16 占比`、`alpha 平均误差/max`、`半透明像素保持比例`、`透明区是否逐像素一致`。
看这行就该决定要不要收/放参数：

| 现象 | 调法 |
|---|---|
| core 的 `>16 占比` 偏高 | 调大 core 色数（把 edge 色数压小） |
| alpha 误差偏大 | 调大档数（8→12，带宽变小） |
| 文件偏大 | 调小 core/edge，或减少 Lloyd 轮数（几乎不影响体积，主要影响误差） |

## 注意

- 本目录只做**编码压缩**，不改分辨率、不改像素语义；要改图请用别的工具。
- **动手前先把原文件拷一份到临时目录当对照**，否则事后给不出 before/after 与误差数字。
- 换完文件游戏要**重新编译 jar** 才生效（资源在 jar 里）。
