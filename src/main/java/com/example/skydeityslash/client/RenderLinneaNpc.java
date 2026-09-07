package com.example.skydeityslash.client;

import com.example.skydeityslash.entity.LinneaNpcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * 莉奈娅人形 NPC 渲染器：与芙宁娜 NPC 完全相同的坐姿模型 + 下移 0.65 + 隐藏名字，
 * 仅贴图换为 linnea_npc.png。
 */
public class RenderLinneaNpc extends LivingEntityRenderer<LinneaNpcEntity, FurinaNpcModel<LinneaNpcEntity>> {
    private static final ResourceLocation TEX = new ResourceLocation("skydeityslash", "textures/entity/linnea_npc.png");

    public RenderLinneaNpc(EntityRendererProvider.Context ctx) {
        super(ctx, new FurinaNpcModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(LinneaNpcEntity e) { return TEX; }

    @Override
    protected void setupRotations(LinneaNpcEntity entity, PoseStack poseStack,
                                  float ageInTicks, float rotationYaw, float partialTick) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTick);
        poseStack.translate(0.0D, -0.65D, 0.0D);
        // 面向主人：只旋转朝向，不移动
        UUID ownerId = entity.getOwnerUuid();
        if (ownerId != null) {
            Player owner = null;
            for (Player p : entity.level().players()) {
                if (p.getUUID().equals(ownerId)) { owner = p; break; }
            }
            if (owner != null) {
                double dx = owner.getX() - entity.getX();
                double dz = owner.getZ() - entity.getZ();
                float myYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
                poseStack.mulPose(Axis.YP.rotationDegrees(entity.getYRot() - myYaw));
            }
        }
    }

    /** 渲染层兜底：无论实体逻辑如何，都不渲染名字标签 */
    @Override
    protected boolean shouldShowName(LinneaNpcEntity entity) { return false; }
}
