package com.example.skydeityslash.client;

import com.example.skydeityslash.registry.ModBlockEntities;
import com.example.skydeityslash.registry.ModEntities;
import mods.flammpfeil.slashblade.client.renderer.entity.SummonedSwordRenderer;
import net.minecraftforge.client.event.EntityRenderersEvent;

/**
 * 客户端事件：注册自定义实体与方块实体的渲染器。
 */
public class ModClientEvents {
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TIDE_SWORD.get(), SummonedSwordRenderer::new);
        event.registerEntityRenderer(ModEntities.FONTAINE_WAVE.get(), SummonedSwordRenderer::new);
        event.registerEntityRenderer(ModEntities.FONTAINE_TIDE_SWORD.get(), FontaineTideSwordRenderer::new);
        event.registerEntityRenderer(ModEntities.FOX_FIELD.get(), RenderFoxEnlightenedField::new);
        event.registerEntityRenderer(ModEntities.UMBRELLA.get(), RenderRainUmbrella::new);
        event.registerEntityRenderer(ModEntities.SEVEN_THUNDERS.get(), RenderSevenThunders21::new);
        event.registerEntityRenderer(ModEntities.INK_FOX_FIELD.get(), RenderInkFoxField::new);
        event.registerEntityRenderer(ModEntities.GOLDEN_BRANCH_BALL.get(), RenderGoldenBranchBall::new);
        event.registerEntityRenderer(ModEntities.FURINA_NPC.get(), RenderFurinaNpc::new);
        event.registerEntityRenderer(ModEntities.HUTAO_NPC.get(), RenderHutaoNpc::new);
        event.registerEntityRenderer(ModEntities.ODETTE_NPC.get(), RenderOdetteNpc::new);
        event.registerEntityRenderer(ModEntities.LINNEA_NPC.get(), RenderLinneaNpc::new);
        event.registerEntityRenderer(ModEntities.COLUMBINA_NPC.get(), RenderColumbinaNpc::new);
        event.registerEntityRenderer(ModEntities.IROI_NPC.get(), RenderIroiNpc::new);
        event.registerEntityRenderer(ModEntities.TIANXING_FAZ.get(), RenderTianxingFaz::new);
        event.registerEntityRenderer(ModEntities.TIANXING_STONE.get(), RenderTianxingStone::new);

        event.registerBlockEntityRenderer(ModBlockEntities.ARCANE_PEDESTAL.get(), ArcanePedestalBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ENCHANTING_APPARATUS.get(), EnchantingApparatusBlockEntityRenderer::new);
    }
}
