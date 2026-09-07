package com.example.skydeityslash.client;

import com.example.skydeityslash.entity.EntityFoxEnlightenedField;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * 剑体始觉 特效 —— 忠实移植原版 LimpidityGeometry 四通道：
 * BASE_COLOR（地面阵图/光晕外壳/斩弧/竖弧/剑刃/碎片/辉光）、BASE_GLOW（重复描亮斩弧竖弧剑刃）、
 * UNITY_COLOR（曼陀罗/翼符/合击/余辉）、UNITY_GLOW（翼符/合击/余辉）。
 * 金/淡紫配色、内圈白亮核两层面片，加法混合。时长 64 tick。
 */
public class RenderFoxEnlightenedField extends EntityRenderer<EntityFoxEnlightenedField> {
    private static final ResourceLocation NONE = new ResourceLocation("skydeityslash", "fox_field");

    public RenderFoxEnlightenedField(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override
    public ResourceLocation getTextureLocation(EntityFoxEnlightenedField e) { return NONE; }

    @Override
    public void render(EntityFoxEnlightenedField entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        float age = Mth.clamp(entity.tickCount + partial, 0, EntityFoxEnlightenedField.LIFETIME);

        Matrix4f m = pose.last().pose();
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        float viewYaw = cam.getYRot();
        float viewPitch = cam.getXRot();
        float entityYaw = entity.getYaw();

        // 四通道：颜色层与发光层叠加，逐层忠实还原原版光影
        GlowGeometry.draw(m, buffer.getBuffer(GlowGeometry.GLOW), age, viewYaw, viewPitch, entityYaw, GlowGeometry.Pass.BASE_COLOR);
        GlowGeometry.draw(m, buffer.getBuffer(GlowGeometry.GLOW), age, viewYaw, viewPitch, entityYaw, GlowGeometry.Pass.BASE_GLOW);
        GlowGeometry.draw(m, buffer.getBuffer(GlowGeometry.UNITY), age, viewYaw, viewPitch, entityYaw, GlowGeometry.Pass.UNITY_COLOR);
        GlowGeometry.draw(m, buffer.getBuffer(GlowGeometry.UNITY), age, viewYaw, viewPitch, entityYaw, GlowGeometry.Pass.UNITY_GLOW);

        super.render(entity, yaw, partial, pose, buffer, light);
    }
}