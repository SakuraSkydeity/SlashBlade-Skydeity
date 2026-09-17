package com.example.skydeityslash.registry;

import com.example.skydeityslash.SkydeitySlash;
import com.example.skydeityslash.ability.ChikuiFentian;
import com.example.skydeityslash.ability.ColumbinaArt;
import com.example.skydeityslash.ability.FontaineCarnival;
import com.example.skydeityslash.ability.GoldenBranchArt;
import com.example.skydeityslash.ability.IroiXiangyangArt;
import com.example.skydeityslash.ability.LiRenLei;
import com.example.skydeityslash.ability.LiyueButterfly;
import com.example.skydeityslash.ability.XuanfengSlashArt;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.init.DefaultResources;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/**
 * 自定义连击状态（ComboState）注册表。
 * 连击状态定义了剑技释放时的动作帧、攻击判定、收刀等逻辑。
 */
public class ModComboStates {
    public static final DeferredRegister<ComboState> COMBO_STATES =
            DeferredRegister.create(ComboState.REGISTRY_KEY, SkydeitySlash.MODID);

    /** 「璃月」蝶引来生：红色圆刃环绕 + 两侧竖排幻影剑万箭齐发 */
    public static final RegistryObject<ComboState> LIYUE_BUTTERFLY_COMBO =
            COMBO_STATES.register("liyue_butterfly_combo",
                    ComboState.Builder.newInstance()
                            .startAndEnd(400, 459)
                            .priority(100)
                            .motionLoc(DefaultResources.ExMotionLocation)
                            .next(ComboState.TimeoutNext.buildFromFrame(15, e -> SlashBlade.prefix("none")))
                            .nextOfTimeout(e -> SlashBlade.prefix("none"))
                            .releaseAction((player, chargeTicks) -> SlashArts.ArtsType.Success)
                            .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                                    .put(2, LiyueButterfly::doButterfly)
                                    .build())
                            ::build);

    /** 「枫丹」万众狂欢：20 把浅蓝幻影剑雨 + 蓝色大幻影剑垂直下劈 */
    public static final RegistryObject<ComboState> FONTAINE_CARNIVAL_COMBO =
            COMBO_STATES.register("fontaine_carnival_combo",
                    ComboState.Builder.newInstance()
                            .startAndEnd(400, 459)
                            .priority(100)
                            .motionLoc(DefaultResources.ExMotionLocation)
                            .next(ComboState.TimeoutNext.buildFromFrame(15, e -> SlashBlade.prefix("none")))
                            .nextOfTimeout(e -> SlashBlade.prefix("none"))
                            .releaseAction((player, chargeTicks) -> SlashArts.ArtsType.Success)
                            .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                                    .put(2, FontaineCarnival::doCarnival)
                                    .build())
                            ::build);

    /** 「翾风回雪」：银白巨大刀波 + 斜后方白色刀波劈下 */
    public static final RegistryObject<ComboState> XUANFENG_COMBO =
            COMBO_STATES.register("xuanfeng_combo",
                    ComboState.Builder.newInstance()
                            .startAndEnd(400, 459)
                            .priority(100)
                            .motionLoc(DefaultResources.ExMotionLocation)
                            .next(ComboState.TimeoutNext.buildFromFrame(15, e -> SlashBlade.prefix("none")))
                            .nextOfTimeout(e -> SlashBlade.prefix("none"))
                            .releaseAction((player, chargeTicks) -> SlashArts.ArtsType.Success)
                            .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                                    .put(2, XuanfengSlashArt::doXuanfeng)
                                    .build())
                            ::build);

    /** 「离人泪·曲断魂」：「狐」剑技，朝准星方向放出金白结界（sakurafox 默认 SA） */
    public static final RegistryObject<ComboState> LI_REN_LEI_COMBO =
            COMBO_STATES.register("li_ren_lei_combo",
                    ComboState.Builder.newInstance()
                            .startAndEnd(400, 459)
                            .priority(100)
                            .motionLoc(DefaultResources.ExMotionLocation)
                            .next(ComboState.TimeoutNext.buildFromFrame(15, e -> SlashBlade.prefix("none")))
                            .nextOfTimeout(e -> SlashBlade.prefix("none"))
                            .releaseAction((player, chargeTicks) -> SlashArts.ArtsType.Success)
                            .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                                    .put(2, LiRenLei::doLiRenLei)
                                    .build())
                            ::build);

    /** 「诺德卡莱」霜结的誓金枝：朝准星方向降下金色誓金枝球，落地金黄/橙黄爆炸 */
    public static final RegistryObject<ComboState> GOLDEN_BRANCH_COMBO =
            COMBO_STATES.register("golden_branch_combo",
                    ComboState.Builder.newInstance()
                            .startAndEnd(400, 459)
                            .priority(100)
                            .motionLoc(DefaultResources.ExMotionLocation)
                            .next(ComboState.TimeoutNext.buildFromFrame(15, e -> SlashBlade.prefix("none")))
                            .nextOfTimeout(e -> SlashBlade.prefix("none"))
                            .releaseAction((player, chargeTicks) -> SlashArts.ArtsType.Success)
                            .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                                    .put(2, GoldenBranchArt::doGoldenBranch)
                                    .build())
                            ::build);

    /** 「诺德卡莱」为夜增辉与君遥伴：目标处释放 frostflourish 螺旋冰花 + 玩家身边释放 frost_nova 冰霜新星 */
    public static final RegistryObject<ComboState> COLUMBINA_COMBO =
            COMBO_STATES.register("columbina_combo",
                    ComboState.Builder.newInstance()
                            .startAndEnd(400, 459)
                            .priority(100)
                            .motionLoc(DefaultResources.ExMotionLocation)
                            .next(ComboState.TimeoutNext.buildFromFrame(15, e -> SlashBlade.prefix("none")))
                            .nextOfTimeout(e -> SlashBlade.prefix("none"))
                            .releaseAction((player, chargeTicks) -> SlashArts.ArtsType.Success)
                            .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                                    .put(2, ColumbinaArt::doColumbina)
                                    .build())
                            ::build);

    /** 「向阳」九千五百万年前的分歧：iroi 专属 SA，向前斩出一道天青→紫色渐变剑气 */
    public static final RegistryObject<ComboState> IROI_XIANGYANG_COMBO =
            COMBO_STATES.register("iroi_xiangyang_combo",
                    ComboState.Builder.newInstance()
                            .startAndEnd(400, 459)
                            .priority(100)
                            .motionLoc(DefaultResources.ExMotionLocation)
                            .next(ComboState.TimeoutNext.buildFromFrame(15, e -> SlashBlade.prefix("none")))
                            .nextOfTimeout(e -> SlashBlade.prefix("none"))
                            .releaseAction((player, chargeTicks) -> SlashArts.ArtsType.Success)
                            .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                                    .put(2, IroiXiangyangArt::doIroiXiangyang)
                                    .build())
                            ::build);

    /**
     * 「赤葵」焚天烬灭舞：zankou 专属 SA（`ability/ChikuiFentian`）。
     * 效果：① 朝面朝方向 **25 格 × 宽 3 格**（上下 +3 / −1）方框内每个生物各 **520 真伤**；
     *       ② 一条暗红摆线 + 密集火焰（`EntityChikuiArc`，纯几何线条）；③ 身边 6 把暗红幻影剑依次射出（纯视觉，damage 0）。
     */
    public static final RegistryObject<ComboState> CHIKUI_COMBO =
            COMBO_STATES.register("chikui_combo",
                    ComboState.Builder.newInstance()
                            .startAndEnd(400, 459)
                            .priority(100)
                            .motionLoc(DefaultResources.ExMotionLocation)
                            .next(ComboState.TimeoutNext.buildFromFrame(15, e -> SlashBlade.prefix("none")))
                            .nextOfTimeout(e -> SlashBlade.prefix("none"))
                            .releaseAction((player, chargeTicks) -> SlashArts.ArtsType.Success)
                            .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                                    .put(2, ChikuiFentian::doChikui)
                                    .build())
                            ::build);
}
