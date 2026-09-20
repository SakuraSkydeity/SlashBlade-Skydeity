# obj-simplify —— 拔刀剑 OBJ 的简化 / 瘦身 / 校验

处理 `assets/skydeityslash/model/named/<id>.obj` 这一套模型：**减面**、**瘦身**、**改形后验证**。

```
C:\Users\Sky deity\AppData\Local\Programs\Python\Python311\python.exe   # numpy / Pillow / scipy
```

| 脚本 | 干什么 | 命令 |
|---|---|---|
| **`decimate.py`** | **网格简化**（边长优先的边塌缩，保护 UV 缝/硬边/边界） | `python decimate.py <in.obj> <out.obj> <总比例> ["组名=比例,..."]` |
| `compactD.py` | **文本瘦身**：数值精度 + 顶点去重 + 索引重排 | `python compactD.py <in.obj> <out.obj> [v位=3] [vt位=5] [vn位=3]` |
| **`fixobj.py`** | **解析器仿真校验**：抓"改完 obj 游戏里变默认刀" | `python fixobj.py <obj> [更多 obj...]` |
| `check_poke.py` | **剪影露刃检测**（鞘改形后的最终裁判） | `python check_poke.py <obj> [--tex png] [--far -62] [--out dbg.png]` |
| `render_obj.py` | 通用带贴图软渲染（z-buffer），被上面两个 import | — |
| `sren.py` | 真超采样封装（`Renderer` 的 `ss` 参数是坏的） | — |

---

## 1. `decimate.py` —— 网格简化

```
python decimate.py zankou.obj sim.obj 0.40 "effect=0.22,item_blade=1.0"
```

- **边长优先的边塌缩**：顶点先按值去重，再把最短的边依次塌缩到中点，直到达到该组目标面数。
- **四道保护**（缺一个就会毁模型）：
  - UV 像素距离 > `MAX_UV_PX`（34）的边 = **贴图缝**，不塌；
  - 二面角 > `MAX_DIHEDRAL`（38°）的边 = **硬边/转折**，不塌；
  - 边界边不塌（`len(edge_faces) != 2` 直接跳过）；
  - 塌缩产生的退化面（顶点数 < 3）删除。
- **`vt` / `vn` 索引原样保留** —— 模型是**平滑着色**（同一点各面法线不同），重算法线会让整把刀变刻面。
- 输出**按源文件的行尾风格写**（二进制写；文本模式会把 CRLF 翻成 LF，凭空少几万字节）。
- 分组比例用第 4 个参数按 `组名=比例` 覆盖：`effect`（蓄力发光壳，细节无所谓）可以砍得更狠；
  `item_blade`（物品图标）通常保持 `1.0`。

实测（zankou，面 36464→13957、文件 3.75 MB→1.99 MB）逐像素差 <1%。

### ★ 顺序：先减面，再改形

简化器会把**新做出来的尖角磨钝**。所以鞘收窄/收尖这类"改形"要放在 `decimate` **之后**做。
反过来，如果只是比例缩放，叠加是安全的（乘性），但**永远从基线 `.bak` 重新生成**，不要在已改过的文件上再叠一次。

---

## 2. `compactD.py` —— obj 文本瘦身

```
python compactD.py zankou.obj small.obj 2 4 3
```

比单纯调精度多做两件事：

1. 按「目标精度格式化后的字面值」去重 `v/vt/vn`（重复行删掉，索引指回首次出现）；
2. 相应重排所有 `f` 的 `v/vt/vn` 三元索引，并更新文件头的 `# N vertices` / `# N elements` 注释。

- **一定是定点格式化**（`%.*f`），**绝不写科学计数法**：`str(float)` / `"%s" % round()` 对小数会输出 `1e-05`，
  而 SlashBlade 的正则只认定点小数 → 那一行被**静默丢弃** → 索引整体错位 → 越界 → **游戏里变默认刀**。
- 精度建议：`v` 2~3 位（0.01 模型单位 ≈ 游戏里 3e-5 像素）、`vt` 4~5 位、**`vn` 保守留 3 位**
  （2 位会有约 0.8° 角误差，光滑曲面上可能起带）。
- 行尾与源文件保持一致。

实测（orig.obj）：202,326 → 156,986 B（−22.4%），渲染逐像素平均差 0.037、>16 占 0.0115%。

---

## 3. `fixobj.py` —— 解析器仿真校验（**改完 obj 必跑**）

```
python fixobj.py model/named/*.obj
```

**忠实模拟** SlashBlade 1.9.65 的 `WavefrontObject.loadObjModel`：

```
line = line.replaceAll("\\s+", " ").trim()
按 startsWith("v ")/"vn "/"vt "/"f "/("g "|"o ") 分类，每类先过正则
正则不过 → return null → **该条不入表**（后面所有索引整体错位一格）
f 的索引直接查 ArrayList → 越界抛异常 → BladeModelManager 的 catch(Exception) 吞掉 → 返回默认模型
```

它会报出：**非法行**（会被丢弃）与**面索引越界**。两项都是 0 才算安全。

已知会踩的雷：

| 雷 | 后果 |
|---|---|
| 数值写成 `-2e-05` | 该行被丢弃 → 索引错位 → 越界 → **默认刀** |
| 缺前导 0 的 `.5` | 同上 |
| 面不是三角形 / 四边面 | 解析器只认 3~4 个 token 且**不做三角化**，四边面渲染是错的 |
| 另开一个**同名** `g` | 会覆盖（`render_obj` 里重名加 `#2`，但游戏端行为不同，别这么干） |

注意：`groupObjectPattern` 是 `([go]( [\w\d\.]+) *\n)|([go]( [\w\d\.]+) *$)` ⇒ **`o` 和 `g` 都算分组名**
（Blender 导出的 `o blade` 直接可用）；`mtllib / usemtl / s` 行被忽略。
UV 约定：**`v=0` 是图片最后一行**。

---

## 4. `check_poke.py` —— 剪影露刃检测

```
python check_poke.py model/named/zankou.obj --tex model/named/zankou.png --out poke.png
```

把「X < `--far` 的那段刀身」与「`--outer`（默认 sheath）」用**同一相机**分别光栅化，比较剪影：

```
angle=0    鞘剪影  123456 px  刀身剪影  78901 px  落在鞘外  0 px (0.000%)
=> 无露刃 ✓
```

**为什么不用数值余量**：低模顶点稀疏，按 X 切片统计"鞘内刀身范围"会出"该处没顶点 ⇒ 量成 0"的假数据；
而且余量指标本身有约 1.3 单位的假阳性（正常模型也会报负值）。所以**只认剪影**。

真出露刃时打印屏幕坐标范围，`--out` 会存一张调试图（灰=鞘、绿=被包住的刀身、红=露刃）。
退出码 0 = 干净，1 = 有露刃（可直接用于批处理）。

---

## 5. `render_obj.py` / `sren.py` —— 软渲染

- `render_obj.parse_obj(path)` → `{组名: [三角面]}`，每个面是 `[(pos, uv), ...]`。
- `render_obj.Renderer(tex, W, H, ss)`：**`ss` 参数是坏的**（缓冲区按 ss 放大，但坐标是最终空间的
  → 物体会缩到 1/ss 挤在左上角）。**用 `sren.render(...)`**：以 ss 倍尺寸、`ss=1` 渲染再缩回来，才是真超采样。
- `sren.render(meshes, rot, W, H, tex, ss=3, fit=..., shade=..., bg=...)`；`fit` 传入可让多张图共用同一相机
  （对比图必须共用相机，否则自动取景会把差异抹平）。

---

## 通用铁律

1. **二进制读写 obj**。文本模式写会把 CRLF 翻成 LF，文件凭空小几万字节，看着像"压缩成功"。
2. **改形永远从基线重新生成**（`tools/obj_backup/*.bak` 或从 mod jar 里取原件），不要在已改过的文件上二次叠加。
3. 任何 obj 改动后跑 **`fixobj.py`**；任何鞘改形后跑 **`check_poke.py`**。
4. 覆盖文件前先把原文件拷到临时目录当对照。
5. 换完资源要**重新编译 jar** 才在游戏里生效。
