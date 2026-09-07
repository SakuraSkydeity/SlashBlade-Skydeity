package com.example.skydeityslash.client;

import com.example.skydeityslash.entity.EntityGoldenBranchBall;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 「诺德卡莱」霜结的誓金枝 —— 金色誓金枝球渲染器。
 * 坠落阶段：金色球体（亮核 + 边缘极淡，加法混合）；
 * 落地阶段：球体缩小淡出，化为鸟群粒子（粒子由服务端生成）。
 */
public class RenderGoldenBranchBall extends EntityRenderer<EntityGoldenBranchBall> {
    private static final ResourceLocation NONE = new ResourceLocation("skydeityslash", "golden_branch_ball");
    private static final float TAU = (float) Math.PI * 2F;

    public RenderGoldenBranchBall(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override
    public ResourceLocation getTextureLocation(EntityGoldenBranchBall e) { return NONE; }

    @Override
    public void render(EntityGoldenBranchBall entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        float age = entity.tickCount + partial;
        Matrix4f m = pose.last().pose();
        VertexConsumer glow = buffer.getBuffer(GlowGeometry.GLOW);
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition().subtract(entity.getPosition(partial));

        if (!entity.isImpacted()) {
            drawSphere(m, glow, camPos, age, 1.0F, 1.0F);
            drawRing(m, glow, age);
        } else {
            float impactAge = entity.getImpactAge() + partial;
            float p = Mth.clamp(impactAge / EntityGoldenBranchBall.IMPACT_LIFE, 0, 1);
            drawSphere(m, glow, camPos, age, 1.0F - p * 0.75F, 1.0F - p);
        }
        super.render(entity, yaw, partial, pose, buffer, light);
    }

    // ---- 金色球体：亮核 + 边缘极淡（菲涅尔） ----
    private void drawSphere(Matrix4f m, VertexConsumer vc, Vec3 camPos, float age, float scale, float alphaMul) {
        int stacks = 14, slices = 26;
        float r = 1.35F * (1.0F + 0.06F * Mth.sin(age * 0.35F)) * scale;
        for (int i = 0; i < stacks; i++) {
            float phi0 = (float) i / stacks * (float) Math.PI;
            float phi1 = (float) (i + 1) / stacks * (float) Math.PI;
            for (int j = 0; j < slices; j++) {
                float th0 = (float) j / slices * TAU;
                float th1 = (float) (j + 1) / slices * TAU;
                Vec3 p00 = sph(phi0, th0, r), p10 = sph(phi1, th0, r);
                Vec3 p01 = sph(phi0, th1, r), p11 = sph(phi1, th1, r);
                quadFresnel(m, vc, p00, p10, p11, p01, camPos, r, alphaMul);
            }
        }
    }

    private Vec3 sph(float phi, float theta, float r) {
        float sp = Mth.sin(phi), cp = Mth.cos(phi);
        float st = Mth.sin(theta), ct = Mth.cos(theta);
        return new Vec3(r * sp * ct, r * cp, r * sp * st);
    }

    /** 环绕球体的旋转金环：上下起伏的环带，增强下落阶段特效 */
    private void drawRing(Matrix4f m, VertexConsumer vc, float age) {
        int segs = 32;
        float r = 1.85F * (1.0F + 0.05F * Mth.sin(age * 0.4F));
        float wave = 0.18F * Mth.sin(age * 0.6F);
        for (int i = 0; i < segs; i++) {
            float a0 = (float) i / segs * TAU;
            float a1 = (float) (i + 1) / segs * TAU;
            float y0 = r * 0.12F * Mth.sin(a0 * 2 + age * 0.5F) + wave;
            float y1 = r * 0.12F * Mth.sin(a1 * 2 + age * 0.5F) + wave;
            Vec3 p0 = new Vec3(r * Mth.cos(a0), y0, r * Mth.sin(a0));
            Vec3 p1 = new Vec3(r * Mth.cos(a1), y1, r * Mth.sin(a1));
            Vec3 up = new Vec3(0, 0.14F, 0);
            quadA(m, vc, p0, p1, p1.add(up), p0.add(up), 1.0F, 0.85F, 0.4F, 0.30F, 0.30F, 0.30F, 0.30F);
        }
    }

    private void quadFresnel(Matrix4f m, VertexConsumer vc, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                             Vec3 camPos, float r, float alphaMul) {
        float a0 = alphaAt(p0, camPos, r) * alphaMul, a1 = alphaAt(p1, camPos, r) * alphaMul;
        float a2 = alphaAt(p2, camPos, r) * alphaMul, a3 = alphaAt(p3, camPos, r) * alphaMul;
        quadA(m, vc, p0, p1, p2, p3, 1.0F, 0.82F, 0.32F, a0, a1, a2, a3);
    }

    private float alphaAt(Vec3 p, Vec3 camPos, float r) {
        Vec3 n = p.normalize();
        Vec3 v = camPos.subtract(p).normalize();
        float d = (float) Math.max(0, n.dot(v));
        return 0.12F + 0.85F * d * d;
    }

    private static void vv(Matrix4f m, VertexConsumer vc, Vec3 p, float r, float g, float b, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color((int) (Mth.clamp(r, 0, 1) * 255), (int) (Mth.clamp(g, 0, 1) * 255),
                        (int) (Mth.clamp(b, 0, 1) * 255), (int) (Mth.clamp(a, 0, 1) * 255))
                .endVertex();
    }

    private static void quadA(Matrix4f m, VertexConsumer vc, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                              float r, float g, float b, float a0, float a1, float a2, float a3) {
        vv(m, vc, p0, r, g, b, a0); vv(m, vc, p1, r, g, b, a1); vv(m, vc, p2, r, g, b, a2);
        vv(m, vc, p0, r, g, b, a0); vv(m, vc, p2, r, g, b, a2); vv(m, vc, p3, r, g, b, a3);
    }
}
