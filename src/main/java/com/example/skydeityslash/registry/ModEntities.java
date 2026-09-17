package com.example.skydeityslash.registry;

import com.example.skydeityslash.SkydeitySlash;
import com.example.skydeityslash.entity.EntityBloomSlash;
import com.example.skydeityslash.entity.EntityChikuiArc;
import com.example.skydeityslash.entity.EntityCutLines;
import com.example.skydeityslash.entity.EntityGhostButterfly;
import com.example.skydeityslash.entity.EntityGoldenBranchBall;
import com.example.skydeityslash.entity.EntityInkFoxField;
import com.example.skydeityslash.entity.EntityInkSwarm;
import com.example.skydeityslash.entity.EntityRainUmbrella;
import com.example.skydeityslash.entity.EntitySakuraBloom;
import com.example.skydeityslash.entity.EntitySwordHologram;
import com.example.skydeityslash.entity.EntityTianxingFaz;
import com.example.skydeityslash.entity.EntityXuanfengRing;
import com.example.skydeityslash.entity.EntityEffectPreview;
import com.example.skydeityslash.entity.EntityTianxingStone;
import com.example.skydeityslash.entity.IroiNpcEntity;
import com.example.skydeityslash.entity.ColumbinaNpcEntity;
import com.example.skydeityslash.entity.FurinaNpcEntity;
import com.example.skydeityslash.entity.HutaoNpcEntity;
import com.example.skydeityslash.entity.LinneaNpcEntity;
import com.example.skydeityslash.entity.OdetteNpcEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/**
 * 自定义实体类型注册表。
 */
public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SkydeitySlash.MODID);

    /** 钤印伞特效主机（纯视觉，无碰撞无 AI） */
    public static final RegistryObject<EntityType<EntityRainUmbrella>> UMBRELLA =
            ENTITIES.register("umbrella", () -> EntityType.Builder
                    .<EntityRainUmbrella>of(EntityRainUmbrella::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .setTrackingRange(8)
                    .setUpdateInterval(1)
                    .build(SkydeitySlash.MODID + ":umbrella"));

    /** 墨渊·蝶舞九天（sakurafox 新 SA 特效实体） */
    public static final RegistryObject<EntityType<EntityInkFoxField>> INK_FOX_FIELD =
            ENTITIES.register("ink_fox_field", () -> EntityType.Builder
                    .<EntityInkFoxField>of(EntityInkFoxField::new, MobCategory.MISC)
                    .sized(0.2f, 0.2f)
                    .setTrackingRange(10)
                    .setUpdateInterval(1)
                    .build(SkydeitySlash.MODID + ":ink_fox_field"));

    /** 雨间蝶舞·墨羽环绕（纯视觉粒子实体，无渲染，trackingRange 0） */
    public static final RegistryObject<EntityType<EntityInkSwarm>> INK_SWARM =
            ENTITIES.register("ink_swarm", () -> EntityType.Builder
                    .<EntityInkSwarm>of(EntityInkSwarm::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f)
                    .setTrackingRange(0)
                    .setUpdateInterval(1)
                    .build(SkydeitySlash.MODID + ":ink_swarm"));

    /** 「诺德卡莱」霜结的誓金枝：金色誓金枝球（下落 + 落地爆炸） */
    public static final RegistryObject<EntityType<EntityGoldenBranchBall>> GOLDEN_BRANCH_BALL =
            ENTITIES.register("golden_branch_ball", () -> EntityType.Builder
                    .<EntityGoldenBranchBall>of(EntityGoldenBranchBall::new, MobCategory.MISC)
                    .sized(0.9f, 0.9f)
                    .setTrackingRange(10)
                    .setUpdateInterval(1)
                    .build(SkydeitySlash.MODID + ":golden_branch_ball"));

    /** 芙宁娜人形 NPC：丢下 furina 刀时生成的人形替身 */
    public static final RegistryObject<EntityType<FurinaNpcEntity>> FURINA_NPC =
            ENTITIES.register("furina_npc", () -> EntityType.Builder
                    .<FurinaNpcEntity>of(FurinaNpcEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .setTrackingRange(10)
                    .setUpdateInterval(3)
                    .build(SkydeitySlash.MODID + ":furina_npc"));

    /** 胡桃人形 NPC：丢下 hutao 刀时生成的人形替身（与芙宁娜同逻辑，贴图不同） */
    public static final RegistryObject<EntityType<HutaoNpcEntity>> HUTAO_NPC =
            ENTITIES.register("hutao_npc", () -> EntityType.Builder
                    .<HutaoNpcEntity>of(HutaoNpcEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .setTrackingRange(10)
                    .setUpdateInterval(3)
                    .build(SkydeitySlash.MODID + ":hutao_npc"));

    /** 奥黛塔人形 NPC：丢下 odette 刀时生成的人形替身（与芙宁娜同逻辑，贴图不同） */
    public static final RegistryObject<EntityType<OdetteNpcEntity>> ODETTE_NPC =
            ENTITIES.register("odette_npc", () -> EntityType.Builder
                    .<OdetteNpcEntity>of(OdetteNpcEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .setTrackingRange(10)
                    .setUpdateInterval(3)
                    .build(SkydeitySlash.MODID + ":odette_npc"));

    /** 莉奈娅人形 NPC：丢下 linnea 刀时生成的人形替身（与芙宁娜同逻辑，贴图不同） */
    public static final RegistryObject<EntityType<LinneaNpcEntity>> LINNEA_NPC =
            ENTITIES.register("linnea_npc", () -> EntityType.Builder
                    .<LinneaNpcEntity>of(LinneaNpcEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .setTrackingRange(10)
                    .setUpdateInterval(3)
                    .build(SkydeitySlash.MODID + ":linnea_npc"));

    /** 哥伦比娅人形 NPC：丢下 columbina 刀时生成的人形替身（与芙宁娜同逻辑，贴图不同） */
    public static final RegistryObject<EntityType<ColumbinaNpcEntity>> COLUMBINA_NPC =
            ENTITIES.register("columbina_npc", () -> EntityType.Builder
                    .<ColumbinaNpcEntity>of(ColumbinaNpcEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .setTrackingRange(10)
                    .setUpdateInterval(3)
                    .build(SkydeitySlash.MODID + ":columbina_npc"));

    /** 伊洛伊人形 NPC：丢下 iroi 刀时生成的人形替身（与芙宁娜同逻辑，贴图不同） */
    public static final RegistryObject<EntityType<IroiNpcEntity>> IROI_NPC =
            ENTITIES.register("iroi_npc", () -> EntityType.Builder
                    .<IroiNpcEntity>of(IroiNpcEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .setTrackingRange(10)
                    .setUpdateInterval(3)
                    .build(SkydeitySlash.MODID + ":iroi_npc"));

    /** 天星·法阵主机（地面 array_bg + 天空 array_sky，纯视觉无碰撞） */
    public static final RegistryObject<EntityType<EntityTianxingFaz>> TIANXING_FAZ =
            ENTITIES.register("tianxing_faz", () -> EntityType.Builder
                    .<EntityTianxingFaz>of(EntityTianxingFaz::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .setTrackingRange(32)
                    .setUpdateInterval(1)
                    .build(SkydeitySlash.MODID + ":tianxing_faz"));

    /** 天星·陨石（天星斜落投射物） */
    public static final RegistryObject<EntityType<EntityTianxingStone>> TIANXING_STONE =
            ENTITIES.register("tianxing_stone", () -> EntityType.Builder
                    .<EntityTianxingStone>of(EntityTianxingStone::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .setTrackingRange(32)
                    .setUpdateInterval(1)
                    .build(SkydeitySlash.MODID + ":tianxing_stone"));

    /** 全息剑特效（纯视觉公告板贴图，自上方落下 7 格） */
    public static final RegistryObject<EntityType<EntitySwordHologram>> SWORD_HOLOGRAM =
            ENTITIES.register("sword_hologram", () -> EntityType.Builder
                    .<EntitySwordHologram>of(EntitySwordHologram::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .setTrackingRange(64)
                    .setUpdateInterval(1)
                    .build(SkydeitySlash.MODID + ":sword_hologram"));

    /** 幽灵蝶特效（很小的纯视觉公告板贴图，悬浮空中；/skydeityslash effect ghostbutterfly） */
    public static final RegistryObject<EntityType<EntityGhostButterfly>> GHOST_BUTTERFLY =
            ENTITIES.register("ghostbutterfly", () -> EntityType.Builder
                    .<EntityGhostButterfly>of(EntityGhostButterfly::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f)
                    .setTrackingRange(64)
                    .setUpdateInterval(1)
                    .build(SkydeitySlash.MODID + ":ghostbutterfly"));

    /** 翾风回雪·白色动态圆环（纯视觉，环绕锁定目标/落点；客户端渲染白色轨道弧环+旋转环） */
    public static final RegistryObject<EntityType<EntityXuanfengRing>> XUANFENG_RING =
            ENTITIES.register("xuanfeng_ring", () -> EntityType.Builder
                    .<EntityXuanfengRing>of(EntityXuanfengRing::new, MobCategory.MISC)
                    .sized(0.2f, 0.2f)
                    .setTrackingRange(10)
                    .setUpdateInterval(1)
                    .build(SkydeitySlash.MODID + ":xuanfeng_ring"));

    /** 子特效预览（纯视觉，逐个触发 naru/enlightenment 的单个子特效便于测试取舍） */
    public static final RegistryObject<EntityType<EntityEffectPreview>> EFFECT_PREVIEW =
            ENTITIES.register("effect_preview", () -> EntityType.Builder
                    .<EntityEffectPreview>of(EntityEffectPreview::new, MobCategory.MISC)
                    .sized(0.2f, 0.2f)
                    .setTrackingRange(64)
                    .setUpdateInterval(1)
                    .build(SkydeitySlash.MODID + ":effect_preview"));

    /** 「赤葵」焚天烬灭舞的暗红摆线（纯视觉，一端固定在玩家身上做钟摆摆动；客户端按固定参数画连续线条） */
    public static final RegistryObject<EntityType<EntityChikuiArc>> CHIKUI_ARC =
            ENTITIES.register("chikui_arc", () -> EntityType.Builder
                    .<EntityChikuiArc>of(EntityChikuiArc::new, MobCategory.MISC)
                    .sized(0.2f, 0.2f)
                    .setTrackingRange(32)
                    .setUpdateInterval(1)
                    .build(SkydeitySlash.MODID + ":chikui_arc"));

    /** 「花开见血血染双瞳」的暗红刀痕（纯视觉，出现在命中目标身边；六条凌乱弧线，连续线条非粒子） */
    public static final RegistryObject<EntityType<EntityBloomSlash>> BLOOM_SLASH =
            ENTITIES.register("bloom_slash", () -> EntityType.Builder
                    .<EntityBloomSlash>of(EntityBloomSlash::new, MobCategory.MISC)
                    .sized(0.2f, 0.2f)
                    .setTrackingRange(32)
                    .setUpdateInterval(1)
                    .build(SkydeitySlash.MODID + ":bloom_slash"));

    /** 「瞳中深渊渊底之吻」的几何樱花（纯视觉，开在命中目标身上；5 片花瓣、连续几何非粒子） */
    public static final RegistryObject<EntityType<EntitySakuraBloom>> SAKURA_BLOOM =
            ENTITIES.register("sakura_bloom", () -> EntityType.Builder
                    .<EntitySakuraBloom>of(EntitySakuraBloom::new, MobCategory.MISC)
                    .sized(0.2f, 0.2f)
                    .setTrackingRange(32)
                    .setUpdateInterval(1)
                    .build(SkydeitySlash.MODID + ":sakura_bloom"));

    /** 「吻痕窥梦梦魇生花」的横切割刀痕（纯视觉，几道横着的细红圆柱同时划过目标） */
    public static final RegistryObject<EntityType<EntityCutLines>> CUT_LINES =
            ENTITIES.register("cut_lines", () -> EntityType.Builder
                    .<EntityCutLines>of(EntityCutLines::new, MobCategory.MISC)
                    .sized(0.2f, 0.2f)
                    .setTrackingRange(32)
                    .setUpdateInterval(1)
                    .build(SkydeitySlash.MODID + ":cut_lines"));
}
