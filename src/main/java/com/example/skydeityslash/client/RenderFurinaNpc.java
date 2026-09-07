package com.example.skydeityslash.client;

import com.example.skydeityslash.entity.FurinaNpcEntity;
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
 * 芙宁娜人形 NPC 渲染器：使用自定义坐姿模型 FurinaNpcModel（复刻参考 mod 坐姿），
 * 渲染时整体下移 0.65 格贴合地面。贴图固定为 furina_npc.png，绝不使用玩家自身皮肤。
 * 渲染层强制 shouldShowName=false，彻底隐藏头顶名字标签。
 */
public class RenderFurinaNpc extends LivingEntityRenderer<FurinaNpcEntity, FurinaNpcModel<FurinaNpcEntity>> {
    private static final ResourceLocation TEX = new ResourceLocation("skydeityslash", "textures/entity/furina_npc.png");

    public RenderFurinaNpc(EntityRendererProvider.Context ctx) {
        super(ctx, new FurinaNpcModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(FurinaNpcEntity e) { return TEX; }

    @Override
    protected void setupRotations(FurinaNpcEntity entity, PoseStack poseStack,
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
    protected boolean shouldShowName(FurinaNpcEntity entity) { return false; }
}
