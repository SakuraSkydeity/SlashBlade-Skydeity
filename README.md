<div align="center">

<img src="src/main/resources/skydeityslash_icon.png" width="128" alt="SkydeitySlash Icon"/>

# 拔刀剑 源流 · SkydeitySlash

**「江晚雪霁冬」**

基于 SlashBlade Resharped 的 Forge 附属模组，为拔刀剑的世界带来全新的命名刀、锻造材料与一个全新维度。

</div>

---

## 特性

**命名刀**

每柄刀拥有独立的拔刀技（SA）、多重附魔特效（SE）、逐字渐变刀名与专属模型贴图。目前已加入：

- 芙宁娜「枫丹」静水流涌之辉、胡桃「璃月」雪霁梅香、奥黛塔「至冬」白湖冬羽、伊洛伊「向阳」三十八亿年的海市蜃楼、墨染江湖「离恨烟」
- 莉奈娅「诺德卡莱」启喻鸟·博闻异旅、哥伦比娜「少女」、残虹「赤葵」饲火殷红幻景的暮落残阳

**新维度「彼岸」**

虚空维度：无生物生成、无随机时间刻、死亡不掉落，供特效演示与场景搭建使用。

**「新月台」多方块合成**

八座基座 + 中央核心的锻造系统，把一把裸刀锻造成上述命名刀。

**丢刀化形**

掷出的爱刀会化作可拾取的人形幻影。幻影打不死，也不会被自己的刀误伤。

**渲染性能优化**

针对拔刀剑的 OBJ 模型渲染做了独立优化：模型在加载时烘焙一次，渲染期直接复用顶点数据，不再每帧重算。剑雨、刀光、剑气这类由刀模绘制的大量特效因此共享同一批数据。附带阴影代理盒与刀光距离降级。不改变画面表现，出现异常会自动退回原版渲染。

## 指令

```
/skydeityslash enter mandarava    进入彼岸
/skydeityslash leave mandarava    返回原世界
/skydeityslash effect             特效预览
```

## 前置与安装

需要 **Minecraft 1.20.1** 与 **Forge 47.4.10+**，并以 **SlashBlade Resharped 1.9.x** 为运行基础。**JEI / EMI** 为可选组件，用于配方查询。

## 从源码构建

使用 **JDK 21** 与项目自带的 Gradle Wrapper：

```powershell
.\gradlew.bat build -x test
```

产物位于 `build/libs/SlashBlade Skydeity-1.0.0.jar`。

> 基于本模组制作拔刀剑的教程正在整理中。

## 开源许可

本项目基于 **MIT License** 开源，欢迎学习、修改与二次创作。

<div align="center">

---

**拔刀剑 源流 · BladeSlash Skydeity**\
作者：`sakuraskydeity`

*源流。*

</div>
