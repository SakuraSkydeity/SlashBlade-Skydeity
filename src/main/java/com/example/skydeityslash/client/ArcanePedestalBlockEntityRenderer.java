package com.example.skydeityslash.client;

import com.example.skydeityslash.blockentity.ArcanePedestalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * 奥术基座渲染器：把基座上存放的物品悬浮显示，便于玩家看到放上了什么。
 */
public class ArcanePedestalBlockEntityRenderer implements BlockEntityRenderer<ArcanePedestalBlockEntity> {

    public ArcanePedestalBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ArcanePedestalBlockEntity blockEntity, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (blockEntity.getStack().isEmpty()) return;
        float time = (blockEntity.getLevel() == null ? 0 : blockEntity.getLevel().getGameTime()) + partialTick;
        pose.pushPose();
        pose.translate(0.5, 1.5 + 0.10 * Math.sin(time / 6.0), 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(time * 1.5f));
        pose.scale(0.75f, 0.75f, 0.75f);
        Minecraft.getInstance().getItemRenderer()
                .renderStatic(blockEntity.getStack(), ItemDisplayContext.FIXED,
                        packedLight, packedOverlay, pose, buffer, blockEntity.getLevel(), 0);
        pose.popPose();
    }
}