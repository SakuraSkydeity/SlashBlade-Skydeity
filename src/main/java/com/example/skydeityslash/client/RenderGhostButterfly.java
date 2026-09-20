package com.example.skydeityslash.client;

import com.example.skydeityslash.entity.EntityGhostButterfly;
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
 * 幽灵蝶渲染器：把 ghostbutterfly.png 以「公告板」方式贴在一个小四边形上，
 * 始终绕 Y 轴朝向摄像机；轻微上下浮动 + 左右摇晃 + 扇翅式横向缩放，
 * 每只按实体 id 错开相位；带自转的个体额外在屏幕平面内慢慢旋转。整体淡入淡出。
 */
public class RenderGhostButterfly extends EntityRenderer<EntityGhostButterfly> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("skydeityslash", "effects/ghostbutterfly/ghostbutterfly.png");
    /** 只跟贴图有关，建一次即可 —— 每帧现调会新建实例并让顶点缓冲每帧重建。 */
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucent(TEXTURE, false);

    /** 渲染高度（格）—— 很小的一只 */
    private static final float HEIGHT = 0.42f;
    /** 贴图宽高比（宽/高）——必须与 effects/ghostbutterfly/ghostbutterfly.png 一致（64/60） */
    private static final float ASPECT = 64.0f / 60.0f;

    public RenderGhostButterfly(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(EntityGhostButterfly entity) {
        return TEXTURE;
    }

    @Override
    public void render(EntityGhostButterfly entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        float age = entity.getAge() + partial;
        float phase = (entity.getId() % 97) * 0.83f;   // 每只错开，避免动作同步

        // 淡入 / 淡出
        float alpha = 1.0f;
        if (age < 4.0f) alpha = age / 4.0f;
        int life = EntityGhostButterfly.LIFE;
        if (age > life - 10.0f) alpha = Mth.clamp((life - age) / 10.0f, 0.0f, 1.0f);
        if (alpha <= 0.01f) return;
        // 呼吸（幅度收小，避免显得过透）
        alpha *= 0.93f + 0.07f * Mth.sin(age * 0.20f + phase);

        // 只绕 Y 轴朝向摄像机
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        float f = (float) (entity.getX() - cam.x);
        float f1 = (float) (entity.getZ() - cam.z);
        float yawToCam = (float) (Mth.atan2(f, f1) * (180.0F / (float) Math.PI));

        // 悬浮：上下浮动 + 左右摇晃 + 扇翅（横向伸缩）
        float bob = 0.08f * Mth.sin(age * 0.13f + phase);
        float sway = 0.05f * Mth.sin(age * 0.17f + phase * 1.7f);
        float flap = 1.0f + 0.10f * Mth.sin(age * 0.45f + phase);

        float hw = HEIGHT * ASPECT * 0.5f * flap;
        float hh = HEIGHT * 0.5f;

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(yawToCam));
        // 带自转的个体：在屏幕平面内慢慢转一点点
        float spin = entity.getSpin();
        if (spin != 0f) {
            pose.mulPose(Axis.ZP.rotationDegrees(age * spin));
        }
        VertexConsumer vc = buffer.getBuffer(RENDER_TYPE);
        Matrix4f mat = pose.last().pose();
        Matrix3f nor = pose.last().normal();
        float r = 1.0f, g = 0.98f, b = 0.95f;   // 近白，让贴图本身的深橙显色
        vertex(vc, mat, nor,
                -hw + sway, -hh + bob, hw + sway, -hh + bob,
                hw + sway, hh + bob, -hw + sway, hh + bob, r, g, b, alpha);
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
