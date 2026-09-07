package com.example.skydeityslash.client;

import com.example.skydeityslash.blockentity.EnchantingApparatusBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * 新月台渲染器：把装置内当前的核心物/产物悬浮显示在台面上方。
 */
public class EnchantingApparatusBlockEntityRenderer implements BlockEntityRenderer<EnchantingApparatusBlockEntity> {

    public EnchantingApparatusBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(EnchantingApparatusBlockEntity blockEntity, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (blockEntity.getStack().isEmpty()) return;
        float time = (blockEntity.getLevel() == null ? 0 : blockEntity.getLevel().getGameTime()) + partialTick;
        pose.pushPose();
        pose.translate(0.5, 0.6 + 0.06 * Math.sin(time / 5.0), 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(time * 1.2f));
        pose.scale(0.6f, 0.6f, 0.6f);
        Minecraft.getInstance().getItemRenderer()
                .renderStatic(blockEntity.getStack(), ItemDisplayContext.FIXED,
                        packedLight, packedOverlay, pose, buffer, blockEntity.getLevel(), 0);
        pose.popPose();
    }
}