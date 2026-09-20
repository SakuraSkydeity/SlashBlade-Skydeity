package com.example.skydeityslash.client;

import com.example.skydeityslash.entity.EntityTianxingFaz;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 天星·法阵渲染器：贴图渲染（复刻 ysjxspells FazRenderer）。
 * 地面法阵用 array_bg.png，天空法阵用 array_sky.png（均 64x64）。
 * 底层半透明 + 辉光层放大 1.025 倍叠加，绕 Y 旋转，全亮渲染。
 */
public class RenderTianxingFaz extends EntityRenderer<EntityTianxingFaz> {
    private static final ResourceLocation DADI_TEXTURE =
            new ResourceLocation("skydeityslash", "textures/entity/array_bg.png");
    private static final ResourceLocation SKY_TEXTURE =
            new ResourceLocation("skydeityslash", "textures/entity/array_sky.png");
    private static final float ALPHA_MULTIPLIER = 1.45f;
    private static final float GLOW_ALPHA_MULTIPLIER = 0.72f;
    private static final float GLOW_SCALE = 1.025f;

    /**
     * RenderType 只跟贴图有关，是常量。每帧现调 {@code RenderType.entityTranslucent(...)}
     * 会新建实例，而 {@code MultiBufferSource} 是按实例查表的 —— 等于每帧都新开一个
     * 顶点缓冲再丢掉。提前建好即可。
     */
    private static final RenderType DADI_RENDER_TYPE = RenderType.entityTranslucent(DADI_TEXTURE);
    private static final RenderType DADI_GLOW_RENDER_TYPE = RenderType.entityTranslucentEmissive(DADI_TEXTURE);
    private static final RenderType SKY_RENDER_TYPE = RenderType.entityTranslucent(SKY_TEXTURE);
    private static final RenderType SKY_GLOW_RENDER_TYPE = RenderType.entityTranslucentEmissive(SKY_TEXTURE);

    public RenderTianxingFaz(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(EntityTianxingFaz entity) {
        return entity.getVisualVariant() == EntityTianxingFaz.VARIANT_DADI_2
                ? SKY_TEXTURE : DADI_TEXTURE;
    }

    @Override
    public void render(EntityTianxingFaz entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        if (!entity.isVisibleSigil()) {
            return;
        }
        float radius = entity.getRadius() * entity.getExpansionProgress(partial);
        boolean sky = entity.getVisualVariant() == EntityTianxingFaz.VARIANT_DADI_2;
        VertexConsumer vc = buffer.getBuffer(sky ? SKY_RENDER_TYPE : DADI_RENDER_TYPE);
        VertexConsumer glowVc = buffer.getBuffer(sky ? SKY_GLOW_RENDER_TYPE : DADI_GLOW_RENDER_TYPE);
        float alpha = Mth.clamp(entity.getRenderAlpha(partial) * ALPHA_MULTIPLIER, 0.0f, 1.0f);
        float glowAlpha = Mth.clamp(alpha * GLOW_ALPHA_MULTIPLIER, 0.0f, 1.0f);
        pose.pushPose();
        pose.scale(radius, radius, radius);
        pose.mulPose(Axis.YP.rotationDegrees(entity.getRenderRotation(partial)));
        renderQuad(pose, vc, alpha);
        pose.scale(GLOW_SCALE, GLOW_SCALE, GLOW_SCALE);
        renderQuad(pose, glowVc, glowAlpha);
        pose.popPose();
        super.render(entity, yaw, partial, pose, buffer, light);
    }

    private void renderQuad(PoseStack pose, VertexConsumer vc, float alpha) {
        Matrix4f mat = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        vc.vertex(mat, -1.0f, 0.0f, -1.0f).color(1.0f, 1.0f, 1.0f, alpha)
                .uv(0.0f, 0.0f).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0).normal(normal, 0.0f, 1.0f, 0.0f).endVertex();
        vc.vertex(mat, -1.0f, 0.0f, 1.0f).color(1.0f, 1.0f, 1.0f, alpha)
                .uv(0.0f, 1.0f).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0).normal(normal, 0.0f, 1.0f, 0.0f).endVertex();
        vc.vertex(mat, 1.0f, 0.0f, 1.0f).color(1.0f, 1.0f, 1.0f, alpha)
                .uv(1.0f, 1.0f).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0).normal(normal, 0.0f, 1.0f, 0.0f).endVertex();
        vc.vertex(mat, 1.0f, 0.0f, -1.0f).color(1.0f, 1.0f, 1.0f, alpha)
                .uv(1.0f, 0.0f).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0).normal(normal, 0.0f, 1.0f, 0.0f).endVertex();
    }
}
