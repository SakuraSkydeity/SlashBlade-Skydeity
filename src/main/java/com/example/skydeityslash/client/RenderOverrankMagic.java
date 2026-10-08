package com.example.skydeityslash.client;

import com.example.skydeityslash.entity.EntityOverrankMagic;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * 「超位法术」渲染器 —— **全部由代码绘制**，不加载任何贴图。
 *
 * <p>地面那张大法阵（多重环 / 刻度 / 24 条放射线 / 符文带 / 十二边形 / 六芒十二芒星 / 内心）
 * 和所有发光体（光柱、天光、旋转光幕、升腾符文、冲击环、核心光球）现在都在
 * {@link OverrankGeometry} 里用圆弧与直线现算，逐顶点给颜色和透明度。
 *
 * <p>为什么放弃贴图：
 * <ul>
 *   <li>线稿贴图一旦 alpha 混合上去，颜色就被"压"住了 —— 想调暗一档要重新生成、重压、重打包，
 *       而且很容易糊成一片发白发灰（上一版就是这样）；</li>
 *   <li>贴图是"绷在地面上的画"，线宽固定：离远了细线闪、离近了是位图；代码画的线按世界宽度走，
 *       还能跟着距离平滑降档；</li>
 *   <li>省掉两张贴图（jar 里少 22.8 KB），也就没有滤波/mipmap 这些需要拿捏的参数了。</li>
 * </ul>
 *
 * <p>因此这个渲染器只做三件事：算年龄 → 设细节档位 → 把几何写进加法缓冲。
 */
public class RenderOverrankMagic extends EntityRenderer<EntityOverrankMagic> {

    private static final ResourceLocation NONE =
            new ResourceLocation("skydeityslash", "overrank_magic");

    public RenderOverrankMagic(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(EntityOverrankMagic entity) {
        return NONE;
    }

    @Override
    public void render(EntityOverrankMagic entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        float age = entity.getAge() + partial;
        if (OverrankGeometry.lifeEnvelope(age) <= .01f) {
            super.render(entity, yaw, partial, pose, buffer, light);
            return;
        }

        OverrankGeometry.setDetail(FxLod.tier(entity, entityRenderDispatcher.camera));
        Camera cam = entityRenderDispatcher.camera;

        // ---- 第 1 趟：加法发光（地面法阵线稿 + 光柱 / 天光 / 光幕 / 符文 / 冲击环）----
        pose.pushPose();
        Matrix4f m = pose.last().pose();
        float radius = entity.getRadius();
        OverrankGeometry.draw(m, buffer.getBuffer(GlowGeometry.GLOW), age, radius,
                cam.getYRot(), cam.getXRot());
        pose.popPose();

        // ---- 第 2 趟：实体（空中那颗暗球，半透明混合）----
        // 必须放在加法之后：这样球是"盖在光之上"的实心球，而不是被光重新照亮的一团透明亮斑。
        pose.pushPose();
        Matrix4f m2 = pose.last().pose();
        OverrankGeometry.drawSolid(m2, buffer.getBuffer(OverrankGeometry.SOLID), age, radius);
        pose.popPose();

        super.render(entity, yaw, partial, pose, buffer, light);
    }
}
