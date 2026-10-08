package com.example.skydeityslash.client;

import com.example.skydeityslash.entity.EntityIroiOrb;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * 「向阳」第二形态收尾**紫色光球群**的渲染器 —— 全部由代码绘制，不加载任何贴图。
 *
 * <p>几何在 {@link IroiOrbGeometry} 里现算（8 个 UV 球 + 上下漂浮），走
 * {@link GlowGeometry#GLOW}（POSITION_COLOR + 加法混合 + 不写深度 + 无剔除）。
 */
public class RenderIroiOrb extends EntityRenderer<EntityIroiOrb> {

    private static final ResourceLocation NONE =
            new ResourceLocation("skydeityslash", "iroi_orb");

    public RenderIroiOrb(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(EntityIroiOrb entity) {
        return NONE;
    }

    @Override
    public void render(EntityIroiOrb entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        float age = entity.tickCount + partial;
        if (age > IroiOrbGeometry.LIFETIME) return;

        pose.pushPose();
        Matrix4f m = pose.last().pose();
        IroiOrbGeometry.draw(m, buffer.getBuffer(GlowGeometry.GLOW), age, entity.getSpread());
        pose.popPose();

        super.render(entity, yaw, partial, pose, buffer, light);
    }
}
