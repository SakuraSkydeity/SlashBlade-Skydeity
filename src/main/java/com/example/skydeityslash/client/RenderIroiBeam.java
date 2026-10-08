package com.example.skydeityslash.client;

import com.example.skydeityslash.entity.EntityIroiBeam;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * 「向阳」第二形态**天降打击**的渲染器 —— 全部由代码绘制，不加载任何贴图。
 *
 * <p>几何在 {@link IroiBeamGeometry} 里现算（引导环 / 瞄准引线 / 落点聚能 / 锥形柔光带 + 同色亮芯 / 命中闪光），走
 * {@link GlowGeometry#GLOW}（POSITION_COLOR + 加法混合 + 不写深度 + 无剔除）。
 * 本类只做三件事：算年龄 → 把几何写进加法缓冲 → 交给父类。
 */
public class RenderIroiBeam extends EntityRenderer<EntityIroiBeam> {

    private static final ResourceLocation NONE =
            new ResourceLocation("skydeityslash", "iroi_beam");

    public RenderIroiBeam(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(EntityIroiBeam entity) {
        return NONE;
    }

    @Override
    public void render(EntityIroiBeam entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        float age = entity.tickCount + partial;
        if (age > IroiBeamGeometry.LIFETIME) return;

        pose.pushPose();
        Matrix4f m = pose.last().pose();
        // 相机朝向要传进去：外层的柔光带是"正对相机的一片"，靠它算宽度方向
        Camera cam = entityRenderDispatcher.camera;
        IroiBeamGeometry.draw(m, buffer.getBuffer(GlowGeometry.GLOW), age,
                entity.getBeamYaw(), entity.getLengthScale(), cam.getYRot(), cam.getXRot());
        pose.popPose();

        super.render(entity, yaw, partial, pose, buffer, light);
    }
}
