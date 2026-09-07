package com.example.skydeityslash.registry;

import com.example.skydeityslash.SkydeitySlash;
import com.example.skydeityslash.entity.EntityFontaineTideSword;
import com.example.skydeityslash.entity.EntityFontaineWave;
import com.example.skydeityslash.entity.EntityFoxEnlightenedField;
import com.example.skydeityslash.entity.EntityGoldenBranchBall;
import com.example.skydeityslash.entity.EntityInkBloom;
import com.example.skydeityslash.entity.EntityInkButterfly;
import com.example.skydeityslash.entity.EntityInkFoxField;
import com.example.skydeityslash.entity.EntityInkSwarm;
import com.example.skydeityslash.entity.EntityRainUmbrella;
import com.example.skydeityslash.entity.EntitySevenThunders21;
import com.example.skydeityslash.entity.EntitySmokeShadow;
import com.example.skydeityslash.entity.EntityTideSword;
import com.example.skydeityslash.entity.EntityTianxingFaz;
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

    /** 潮汐幻影剑（下落更慢） */
    public static final RegistryObject<EntityType<EntityTideSword>> TIDE_SWORD =
            ENTITIES.register("tide_sword", () -> EntityType.Builder
                    .<EntityTideSword>of(EntityTideSword::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .setTrackingRange(4)
                    .setUpdateInterval(20)
                    .build(SkydeitySlash.MODID + ":tide_sword"));

    /** 枫丹剑气（吸血刀波） */
    public static final RegistryObject<EntityType<EntityFontaineWave>> FONTAINE_WAVE =
            ENTITIES.register("fontaine_wave", () -> EntityType.Builder
                    .<EntityFontaineWave>of(EntityFontaineWave::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .setTrackingRange(4)
                    .setUpdateInterval(20)
                    .build(SkydeitySlash.MODID + ":fontaine_wave"));

    /** 枫丹深蓝幻影剑（吸血） */
    public static final RegistryObject<EntityType<EntityFontaineTideSword>> FONTAINE_TIDE_SWORD =
            ENTITIES.register("fontaine_tide_sword", () -> EntityType.Builder
                    .<EntityFontaineTideSword>of(EntityFontaineTideSword::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .setTrackingRange(4)
                    .setUpdateInterval(20)
                    .build(SkydeitySlash.MODID + ":fontaine_tide_sword"));

    /** 狐剑技组合结界（剑体始觉） */
    public static final RegistryObject<EntityType<EntityFoxEnlightenedField>> FOX_FIELD =
            ENTITIES.register("fox_field", () -> EntityType.Builder
                    .<EntityFoxEnlightenedField>of(EntityFoxEnlightenedField::new, MobCategory.MISC)
                    .sized(0.2f, 0.2f)
                    .setTrackingRange(6)
                    .setUpdateInterval(2)
                    .build(SkydeitySlash.MODID + ":fox_field"));

    /** 钤印伞特效主机（纯视觉，无碰撞无 AI） */
    public static final RegistryObject<EntityType<EntityRainUmbrella>> UMBRELLA =
            ENTITIES.register("umbrella", () -> EntityType.Builder
                    .<EntityRainUmbrella>of(EntityRainUmbrella::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .setTrackingRange(8)
                    .setUpdateInterval(1)
                    .build(SkydeitySlash.MODID + ":umbrella"));

    /** 鸣雷神 紫霆雷狱结界（宁雷神 SA 特效） */
    public static final RegistryObject<EntityType<EntitySevenThunders21>> SEVEN_THUNDERS =
            ENTITIES.register("seven_thunders", () -> EntityType.Builder
                    .<EntitySevenThunders21>of(EntitySevenThunders21::new, MobCategory.MISC)
                    .sized(0.2f, 0.2f)
                    .setTrackingRange(10)
                    .setUpdateInterval(1)
                    .build(SkydeitySlash.MODID + ":seven_thunders"));

    /** 雨间蝶舞·水墨蝶（纯视觉粒子实体，无渲染，trackingRange 0） */
    public static final RegistryObject<EntityType<EntityInkButterfly>> INK_BUTTERFLY =
            ENTITIES.register("ink_butterfly", () -> EntityType.Builder
                    .<EntityInkButterfly>of(EntityInkButterfly::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f)
                    .setTrackingRange(0)
                    .setUpdateInterval(1)
                    .build(SkydeitySlash.MODID + ":ink_butterfly"));

    /** 笛烟·墨芙蓉（纯视觉粒子实体，无渲染，trackingRange 0） */
    public static final RegistryObject<EntityType<EntityInkBloom>> INK_BLOOM =
            ENTITIES.register("ink_bloom", () -> EntityType.Builder
                    .<EntityInkBloom>of(EntityInkBloom::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f)
                    .setTrackingRange(0)
                    .setUpdateInterval(1)
                    .build(SkydeitySlash.MODID + ":ink_bloom"));

    /** 一笛清影·水墨烟影（纯视觉粒子实体，无渲染，trackingRange 0） */
    public static final RegistryObject<EntityType<EntitySmokeShadow>> SMOKE_SHADOW =
            ENTITIES.register("smoke_shadow", () -> EntityType.Builder
                    .<EntitySmokeShadow>of(EntitySmokeShadow::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f)
                    .setTrackingRange(0)
                    .setUpdateInterval(1)
                    .build(SkydeitySlash.MODID + ":smoke_shadow"));

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
}
