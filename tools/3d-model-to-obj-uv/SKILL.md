---
name: 3d-model-to-obj-uv
description: 把 3D 模型文件转成 Wavefront OBJ（带 UV/MTL 材质）+ UV 展开图，不需要装 3ds Max / MMD / 付费软件。支持 Autodesk .max（3ds Max 场景，走 Blender 扩展）和 .pmx（MikuMikuDance 模型，走纯 Python 解析）。当用户给出 .max / .pmx 文件，说要“转成 obj”“要 uv 图 / uv 展开图”“max 建模 / mmd 模型转出来”“打开这个模型”时使用。
agent_created: true
---

# 3D 模型 → OBJ + UV 图

产出永远是：`<base>.obj` + `<base>.mtl` + `UV展开图.png` + `UV线框_白底.png`（+ 一张临时预览图）。
**所有产物写进用户指定的目录**；临时脚本放 `%TEMP%`，用完删。

## 环境

- Blender：`D:\blender\blender.exe`（5.2 LTS）
- Python venv：`C:\Users\Sky deity\.workbuddy\binaries\python\envs\default\Scripts\python.exe`（pillow / numpy / olefile）
- ★ Bash 里调 Windows 程序**必须传 Windows 路径**（`C:/…`）；给 `/c/…` 会报 No such file。

## 路由

| 输入 | 走法 |
|---|---|
| `.max` | Blender 扩展 `io_scene_max` + `scripts/export_max.py`（见 A） |
| `.pmx` | 纯 Python 解析 `scripts/pmx2obj.py`（见 B）——**不要**装 Blender 的 mmd_tools，没必要 |
| `.pmd` / `.fbx` / `.blend` | 优先用 Blender 自带导入器；`.pmd` 需另做 |

---

## A. .max（Autodesk 3ds Max）

### A1. 装 / 确认 Blender 扩展

```bash
ls "/c/Users/Sky deity/AppData/Roaming/Blender Foundation/Blender/5.2/extensions/user_default/"
```

没装就下载（**GitHub release 附件在本机沙箱会被 502 墙掉，必须走 codeload**）：

```bash
curl -L -o /tmp/io_scene_max.zip \
  "https://codeload.github.com/nrgsille76/io_scene_max/zip/refs/heads/main"
```

重打包成扩展 zip —— `blender_manifest.toml` / `__init__.py` / `import_max.py` 必须在 **zip 根目录**
（仓库里它们在 `source/` 下），然后：

```bash
"/d/blender/blender.exe" --command extension install-file -r user_default "<zip>"   # STATUS Installed
```

### A2. 导出

```bash
"/d/blender/blender.exe" -b --factory-startup \
  --python "<skill>/scripts/export_max.py" -- "<in.max>" "<out_dir>" "<basename>"
```

★ **无头模式不要调 `import_max.load()`**：它第一句是 `context.window.cursor_set(...)`，
`-b` 下 `context.window is None` 直接 AttributeError。必须绕到底层 `import_max.read()`（脚本已处理）。

★ **UV 的 V 归一化不能省**：`io_scene_max` 给出的 UV 常是 `v ∈ [-1,0)`，Blender 里靠 wrap 看不出问题，
写进 OBJ 后别的加载器会 clamp 成 0、贴图全废。判据：`vmax <= 0` 就整体 `v += 1`。

★ 物体常带一个很大的 scale（3ds Max 场景单位残留，样例是 ~91.6）。`transform_apply` 后尺寸
就是「3ds Max 里的数值」，**不要自作主张缩放**，但要在回复里写明成品尺寸。

---

## B. .pmx（MikuMikuDance）

```bash
"<venv>/Scripts/python.exe" "<skill>/scripts/pmx2obj.py" -- "<in.pmx>" "<out_dir>" "<basename>"
```

格式有公开规范，直接解析比装插件可靠。脚本已处理：材质分组（`usemtl`）、
左手系 → 右手系镜像（Z 取反 **且反转三角绕序**）、`v_obj = 1 - v_pmx`。

### ★★ 四个必踩的坑

1. **头部 9 字节里有 6 个不同的索引长度**：顶点 / 贴图 / 材质 / **骨骼** / 变形 / 刚体。
   权重里的骨骼索引必须用**骨骼索引长度**（常见是 1），误用「顶点索引长度」（常见 2）会整体错位，
   症状是解析到一半冒出 `weight type 41` 之类的非法值。**这是最容易卡住的地方。**
2. **顶点结构长度不固定**：随权重类型变（BDEF1/2/4、SDEF、QDEF），只能顺序解析；
   用固定步长去"跳"会立刻跑偏。定位真实步长的技巧：扫 `stride ∈ [40,130]` × `uvOff ∈ [0,stride)`，
   统计 `(stride, uvOff)` 处两个 float 同时落在 [0,1] 的比例，真值会给出 1.000。
3. **追加 UV**：头部那个字节是"追加 UV 数"，每个占 4 个 float，不跳过就全错。
4. **toon 索引**：先读 1 字节「是否共享内置 toon」，共享时是 1 字节编号，否则才按索引长度读。

### 校验（脚本已内置）
- `sum(每个材质的 nfaces) == 面索引总数`，不等就说明解析错位。
- 贴图路径记录的是原作者的机器路径（如 `E:\!jW\...`），**以同目录同名文件为准**，不要去找那个盘符。

### 交出去之前要说的
- 同目录里 `_Eff` / `_Lightmap` / `Eff_Wind` 之类常常**没有被 PMX 材质引用**（那些给引擎自定义
  shader 用），别删、别乱引，提醒用户一句。
- `sphere`（球面贴图 spa/sph）和 `toon`（卡通贴图）OBJ 表达不了，脚本只在 MTL 里写注释说明。

---

## C. 画 UV 图（两种格式通用）

```bash
"<venv>/Scripts/python.exe" "<skill>/scripts/uv_layout.py" -- "<out_dir>" "<basename>" "<texture.png>" [size=2048]
```

产出 `UV展开图.png`（贴图打底 + 青色岛轮廓，**主产物**）和 `UV线框_白底.png`。

★ 岛缝算法：以「顶点对 (min,max)」为 key 收集各面用到的 UV 对，
**UV 对不一致、或该边只有一个面 ⇒ 判为岛缝**。不要画全部三角形边，会糊成一片。
★ `px = (u*S, (1-v)*S)`（OBJ 的 v=0 在图片底部）。
★ 线要「深色宽线 + 亮色细线」双描边，否则深色贴图上看不见。

## D. 验收（建议做，但别把预览图当长期资产）

用 Blender 导入 OBJ 渲染 1~2 个正交视图：Workbench + `shading.color_type='TEXTURE'`，
**并打开 `show_backface_culling`** —— 模型出现空洞/内外面翻转就说明三角绕序错了（镜像时必须反转绕序）。

★ 正交相机的 `ortho_scale` 作用在**较长的那条屏幕边**上；细长模型请把分辨率设成
竖长条（如 620×1400）并让相机 `up` 对准模型长轴，否则会像"从刀尖看过去"一样只看得到一个点。
★ 合成一张 `模型预览.png` 后**删掉单张中间渲染**；预览属临时产物，不要长期留在项目里。
