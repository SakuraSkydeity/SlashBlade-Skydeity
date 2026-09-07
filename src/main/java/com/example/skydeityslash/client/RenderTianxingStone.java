package com.example.skydeityslash.client;

import com.example.skydeityslash.entity.EntityTianxingStone;
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
 * 天星·陨石渲染器：贴图渲染（复刻 ysjxspells ShitouRenderer 外观）。
 * 使用 64x64 无透明区域的 tianxing.png 贴图，代码绘制 4 个方块拼成的十字星
 * （中央 12³ 亮核 + 沿 X/Y/Z 三轴的 4×20×20 星臂），全亮渲染。
 */
public class RenderTianxingStone extends EntityRenderer<EntityTianxingStone> {
    private static final float SCALE = 0.2f;
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("skydeityslash", "textures/entity/tianxing.png");

    public RenderTianxingStone(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(EntityTianxingStone entity) {
        return TEXTURE;
    }

    @Override
    public void render(EntityTianxingStone entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        float age = entity.tickCount + partial;
        float lerpYaw = Mth.lerp(partial, entity.yRotO, entity.getYRot());
        float lerpPitch = Mth.lerp(partial, entity.xRotO, entity.getXRot());
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(lerpYaw - 90.0f));
        pose.mulPose(Axis.XP.rotationDegrees(lerpPitch));
        pose.mulPose(Axis.ZP.rotation(age * 0.06f));
        pose.scale(SCALE, SCALE, SCALE);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
        drawBox(pose, vc, -6, -6, -6, 12, 12, 12);
        drawBox(pose, vc, -2, -10, -10, 4, 20, 20);
        drawBox(pose, vc, -10, -2, -10, 20, 4, 20);
        drawBox(pose, vc, -10, -10, -2, 20, 20, 4);
        pose.popPose();
        super.render(entity, yaw, partial, pose, buffer, light);
    }

    private void drawBox(PoseStack pose, VertexConsumer vc,
                         float x, float y, float z, float sx, float sy, float sz) {
        float x1 = x + sx, y1 = y + sy, z1 = z + sz;
        quad(pose, vc, 0, 0, -1, x1, y, z, x, y, z, x, y1, z, x1, y1, z);
        quad(pose, vc, 0, 0, 1, x, y, z1, x1, y, z1, x1, y1, z1, x, y1, z1);
        quad(pose, vc, 1, 0, 0, x1, y, z1, x1, y, z, x1, y1, z, x1, y1, z1);
        quad(pose, vc, -1, 0, 0, x, y, z, x, y, z1, x, y1, z1, x, y1, z);
        quad(pose, vc, 0, 1, 0, x, y1, z, x1, y1, z, x1, y1, z1, x, y1, z1);
        quad(pose, vc, 0, -1, 0, x, y, z1, x1, y, z1, x1, y, z, x, y, z);
    }

    private void quad(PoseStack pose, VertexConsumer vc, float nx, float ny, float nz,
                      float x0, float y0, float z0, float x1, float y1, float z1,
                      float x2, float y2, float z2, float x3, float y3, float z3) {
        Matrix4f mat = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        vc.vertex(mat, x0, y0, z0).color(1.0f, 1.0f, 1.0f, 1.0f)
                .uv(0.0f, 0.0f).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0).normal(normal, nx, ny, nz).endVertex();
        vc.vertex(mat, x1, y1, z1).color(1.0f, 1.0f, 1.0f, 1.0f)
                .uv(1.0f, 0.0f).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0).normal(normal, nx, ny, nz).endVertex();
        vc.vertex(mat, x2, y2, z2).color(1.0f, 1.0f, 1.0f, 1.0f)
                .uv(1.0f, 1.0f).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0).normal(normal, nx, ny, nz).endVertex();
        vc.vertex(mat, x3, y3, z3).color(1.0f, 1.0f, 1.0f, 1.0f)
                .uv(0.0f, 1.0f).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0).normal(normal, nx, ny, nz).endVertex();
    }
}
