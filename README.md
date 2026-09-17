<div align="center">

<img src="src/main/resources/skydeityslash_icon.png" width="128" alt="SkydeitySlash Icon"/>

# ⚔️ 拔刀剑 源流 · SkydeitySlash
**「江晚雪霁冬」**
*基于 SlashBlade Resharped 的 Forge 附属模组，为拔刀剑的世界带来全新的命名刀、锻造材料与全新世界。*

</div>

***
特性一览
全新命名拔刀剑 —— 每柄都拥有独一无二的拔刀技（SA）与多重附魔特效（SE）
全新维度「彼岸」 —— 虚空维度，死亡不掉落无生物生成无随机时间刻
「新月台」多方块合成系统 —— 八座基座中央核心
丢刀化形 —— 掷出的爱刀将化作人形幻影
新添拔刀剑
芙宁娜「枫丹」静水流涌之辉、胡桃「璃月」雪霁梅香、奥黛塔「至冬」白湖冬羽、墨染江湖「离恨烟」
莉奈娅「诺德卡莱」启喻鸟·博闻异旅、哥伦比娜「少女」、伊洛伊「向阳」三十八亿年的海市蜃楼、残虹「赤葵」饲火殷红幻景的暮落残阳
> 后续继续添加原神异环鸣潮拔刀剑
全新维度「彼岸」
```
/skydeityslash enter mandarava    进入彼岸
/skydeityslash leave mandarava    返回原世界
```
***
特效指令
```
/skydeityslash effect 
```
***
## 前置mod
本模组需要 **Minecraft 1.20.1** 与 **Forge 47.4.10+**，并以前置模组 **SlashBlade Resharped 1.9.x** 为运行基础；**JEI / EMI** 为可选组件，用于配方查询。

从源码构建
项目使用 **JDK 17** 与项目自带的 **Gradle Wrapper** 构建，运行：
```powershell
gradlew.bat build -x test
```

正在添加基于该模组制作拔刀剑教程

```
SkydeitySlash_1.20/
├── src/main/java/com/example/skydeityslash/
│   ├── SkydeitySlash.java              # 主入口 + 核心事件(onBladeCreated / 幻影剑改色)
│   ├── ability/                        # 专属剑技 SA 的实现类
│   ├── registry/
│   │   ├── ModSlashArts.java           # 注册「剑技 SA」(slash_art)
│   │   ├── ModComboStates.java         # 注册连段 Combo(SA 触发方式)
│   │   ├── ModSpecialEffects.java      # 注册「特殊效果 SE」
│   │   ├── ModEntities.java            # 注册自定义实体
│   │   └── ModItems.java               # 注册自定义物品(锭等)
│   ├── client/ModClientEvents.java     # 注册实体渲染器(重要,不注册=看不见)
│   └── block/、blockentity/、dimension/ …
└── src/main/resources/
    ├── assets/skydeityslash/
    │   ├── model/named/<刀id>.obj      # 刀 3D 模型
    │   ├── model/named/<刀id>.png      # 刀贴图
    │   ├── effects/<特效名>/…          # 特效用贴图(如 sword/、ghostbutterfly/)
    │   └── textures/…                  # 其他贴图(方块/实体/物品)
    └── data/skydeityslash/
        ├── slashblade/named_blades/<刀id>.json   # 刀定义(核心)
        └── recipes/<刀id>.json                   # 合成/锻造配方
```

构建产物位于 `build/libs/SlashBlade Skydeity-1.0.0.jar`

```powershell
Remove-Item '.minecraft\mods\skydeityslash-1.0.0.jar' -Force -ErrorAction SilentlyContinue
Copy-Item 'build\libs\SlashBlade Skydeity-1.0.0.jar' '.minecraft\mods\' -Force
```
***
## 开源许可
本项目基于 **MIT License** 开源，欢迎学习、修改与二次创作。

<div align="center">

***

**拔刀剑 源流 · BladeSlash Skydeity**\
作者：`sakuraskydeity`

*源流。*

</div>
