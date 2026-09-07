package com.example.skydeityslash.client;

import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.client.renderer.entity.SummonedSwordRenderer;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * 「枫丹」深蓝幻影剑渲染器：将幻影剑放大为原来的 2.5 倍。
 */
public class FontaineTideSwordRenderer extends SummonedSwordRenderer<EntityAbstractSummonedSword> {
    public FontaineTideSwordRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(EntityAbstractSummonedSword entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.scale(2.5f, 2.5f, 2.5f);
        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
