package com.example.skydeityslash.client;

import com.example.skydeityslash.entity.EntitySwordHologram;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 全息剑渲染器：把 sword.png 以「竖直公告板」方式贴在一个四边形上，
 * 始终绕 Y 轴朝向摄像机（剑身保持竖直），全亮渲染（贴图自发光感），前后淡入淡出。
 */
public class RenderSwordHologram extends EntityRenderer<EntitySwordHologram> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("skydeityslash", "effects/sword/sword.png");

    /** 全息剑渲染高度（格） */
    private static final float HEIGHT = 3.0f;
    /** 贴图宽高比（宽/高）——必须与 effects/sword/sword.png 实际尺寸一致，换图要同步改这里 */
    private static final float ASPECT = 85.0f / 192.0f;

    public RenderSwordHologram(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(EntitySwordHologram entity) {
        return TEXTURE;
    }

    @Override
    public void render(EntitySwordHologram entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        float age = entity.tickCount + partial;

        // 淡入 / 淡出
        float alpha = 1.0f;
        if (age < 4.0f) alpha = age / 4.0f;
        int life = entity.getTotalLife();
        if (age > life - 8.0f) alpha = Mth.clamp((life - age) / 8.0f, 0.0f, 1.0f);
        if (alpha <= 0.01f) return;
        // 全息感：轻微呼吸闪烁
        alpha *= 0.88f + 0.12f * Mth.sin(age * 0.35f);

        // 只绕 Y 轴朝向摄像机
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        float f = (float) (entity.getX() - cam.x);
        float f1 = (float) (entity.getZ() - cam.z);
        float yawToCam = (float) (Mth.atan2(f, f1) * (180.0F / (float) Math.PI));

        float w = HEIGHT * ASPECT;
        float hw = w * 0.5f;
        // 落地后轻微浮动
        float bob = entity.isLanded() ? 0.05f * Mth.sin(age * 0.10f) : 0.0f;

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(yawToCam));
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE, false));
        Matrix4f mat = pose.last().pose();
        Matrix3f nor = pose.last().normal();
        float r = 1.0f, g = 1.0f, b = 1.0f;
        // 底边落在实体位置，向上撑起 HEIGHT
        vertex(vc, mat, nor, -hw, bob, hw, bob, hw, HEIGHT + bob, -hw, HEIGHT + bob, r, g, b, alpha);
        pose.popPose();

        super.render(entity, yaw, partial, pose, buffer, light);
    }

    /** 一个竖直四边形（4 顶点 + UV + 全亮），法线朝 +Z */
    private void vertex(VertexConsumer vc, Matrix4f mat, Matrix3f nor,
                        float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
                        float r, float g, float b, float a) {
        vc.vertex(mat, x0, y0, 0.0f).color(r, g, b, a).uv(0.0f, 1.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nor, 0.0f, 0.0f, 1.0f).endVertex();
        vc.vertex(mat, x1, y1, 0.0f).color(r, g, b, a).uv(1.0f, 1.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nor, 0.0f, 0.0f, 1.0f).endVertex();
        vc.vertex(mat, x2, y2, 0.0f).color(r, g, b, a).uv(1.0f, 0.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nor, 0.0f, 0.0f, 1.0f).endVertex();
        vc.vertex(mat, x3, y3, 0.0f).color(r, g, b, a).uv(0.0f, 0.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nor, 0.0f, 0.0f, 1.0f).endVertex();
    }
}
