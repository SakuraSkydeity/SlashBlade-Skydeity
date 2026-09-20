package com.example.skydeityslash.client;

import com.example.skydeityslash.entity.EntityInkFoxField;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * 墨渊·蝶舞九天 特效渲染器 —— 复用「剑体始觉/鸣雷神」同款 POSITION_COLOR 加法混合管线。
 * 两通道：COLOR（主体颜色面）+ GLOW（月白亮芯），几何已按实体 yaw 绕 Y 轴旋转对齐前方 +Z。
 */
public class RenderInkFoxField extends EntityRenderer<EntityInkFoxField> {
    private static final ResourceLocation NONE = new ResourceLocation("skydeityslash", "ink_fox_field");

    public RenderInkFoxField(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override
    public ResourceLocation getTextureLocation(EntityInkFoxField e) { return NONE; }

    @Override
    public void render(EntityInkFoxField entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        float age = Mth.clamp(entity.tickCount + partial, 0, EntityInkFoxField.LIFETIME);

        // 山水几何每帧现算，段数按距离降档（近处保持原样，远处减半）。
        InkFoxGeometry.setDetail(FxLod.tier(entity, entityRenderDispatcher.camera));

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-entity.getYaw()));
        Matrix4f m = pose.last().pose();

        VertexConsumer vc = buffer.getBuffer(GlowGeometry.GLOW);
        InkFoxGeometry.draw(m, vc, age, InkFoxGeometry.Pass.COLOR);

        pose.popPose();
        super.render(entity, yaw, partial, pose, buffer, light);
    }
}