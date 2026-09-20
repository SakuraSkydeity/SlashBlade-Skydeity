package com.example.skydeityslash.client;

import com.example.skydeityslash.entity.EntityEffectPreview;
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
 * 单子特效预览渲染器 —— 根据 type/ID 分发渲染 naru 阶段或 enlight 子特效。
 * naru(0)：用 NarukamiRenderLayer21.renderStage 按 F0-F40 时间轴；enlight(1)：用 GlowGeometry.renderSubEffect。
 */
public class RenderEffectPreview extends EntityRenderer<EntityEffectPreview> {
    private static final ResourceLocation NONE = new ResourceLocation("skydeityslash", "effect_preview");

    public RenderEffectPreview(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override
    public ResourceLocation getTextureLocation(EntityEffectPreview e) { return NONE; }

    @Override
    public void render(EntityEffectPreview entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        Matrix4f m = pose.last().pose();
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();

        Vec3 entityWorld = entity.getPosition(partial);
        Vec3 camera = cam.getPosition().subtract(entityWorld);
        float age = Mth.clamp(entity.tickCount + partial, 0, entity.getLifetime());

        // 预览是拿来逐帧看形态的，一律用最高细节，不跟着距离降档。
        GlowGeometry.setDetail(FxLod.FULL);

        if (entity.getEffectType() == 0) {
            float frame = age * 40.0F / EntityEffectPreview.LIFETIME_NARU; // 原版 F0-F40 时间轴
            Vec3 zero = new Vec3(0, 0, 0);
            NarukamiRenderLayer21.Basis basis = NarukamiRenderLayer21.basis(entity.getLookDirection());
            VertexConsumer b = buffer.getBuffer(GlowGeometry.GLOW);
            NarukamiRenderLayer21.renderStage(m, b, entity.getEffectId(), frame, zero, zero, basis, camera, entity.getSeed());
        } else {
            float viewYaw = cam.getYRot();
            float viewPitch = cam.getXRot();
            VertexConsumer b = buffer.getBuffer(GlowGeometry.GLOW);
            GlowGeometry.renderSubEffect(m, b, entity.getEffectId(), age, viewYaw, viewPitch);
        }

        super.render(entity, yaw, partial, pose, buffer, light);
    }
}