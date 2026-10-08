package com.example.skydeityslash.client;

import com.example.skydeityslash.entity.EntityNewMoonRule;
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
 * NewMoonRule 渲染器：在**地面**上画出「新月法则」的徽记 —— 现在**全部由代码绘制，不再用贴图**
 * （图形见 {@link NewMoonRuleGeometry}：居中一个**蓝色新月** + **五颗蓝白十字星**分散在边上）。
 *
 * <p>做法：先绕 X 轴转 −90°，把"竖着的公告板"放平到地面 —— 这样局部 +Z 朝上、局部 +Y 指向世界 −Z（北）；
 * 再绕局部 Z 轴（= 世界竖直轴）转实体 yaw，让法阵正前方对齐生成时的朝向。
 * 缩放（出现时展开）用 {@code pose.scale(s, s, 1)}：在放平之后的局部坐标系里，
 * 局部 X / Y 就是地面上的两个轴，所以缩放正好是"法阵在地面上长大"。
 *
 * <p>不跟相机（这是地面法阵，不是公告板）；出现时从小到大展开、结束时整体淡出。
 * ★ 图形内部**没有任何 alpha 花活**（纯色、无渐变、无光晕），唯一的 alpha 是这条特效的淡入淡出。
 */
public class RenderNewMoonRule extends EntityRenderer<EntityNewMoonRule> {

    /** 展开口径与展开用时（tick）——出现时由 0.72 倍放到 1 倍 */
    private static final float GROW_TICKS = 8.0f;
    /** 展开起始缩放 */
    private static final float GROW_FROM = 0.72f;
    /** 淡入 / 淡出耗时（tick） */
    private static final float FADE_IN = 6.0f;
    private static final float FADE_OUT = 14.0f;

    public RenderNewMoonRule(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(EntityNewMoonRule entity) {
        // 纯几何，没有贴图；这个返回值只是 API 要求，不会被采样
        return new ResourceLocation("skydeityslash", "newmoonrule_geometry");
    }

    @Override
    public void render(EntityNewMoonRule entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        float age = entity.getAge() + partial;
        int life = EntityNewMoonRule.LIFETIME;

        // 整体的淡入 / 淡出（整条特效唯一使用 alpha 的地方）
        float alpha = 1.0f;
        if (age < FADE_IN) alpha = age / FADE_IN;
        if (age > life - FADE_OUT) alpha = Mth.clamp((life - age) / FADE_OUT, 0.0f, 1.0f);
        if (alpha <= 0.01f) return;

        // 出现时展开（缓出）
        float s = 1.0f;
        if (age < GROW_TICKS) {
            float t = age / GROW_TICKS;
            float e = 1.0f - (1.0f - t) * (1.0f - t);      // easeOutQuad
            s = GROW_FROM + (1.0f - GROW_FROM) * e;
        }

        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(-90.0f));                 // 放平到地面（局部 +Z 朝上）
        pose.mulPose(Axis.ZP.rotationDegrees(-entity.getYRot() + entity.getSpin() * age)); // 绕竖直轴取向 / 自转
        pose.scale(s, s, 1.0f);                                        // 展开（局部 X/Y 就是地面两轴）

        VertexConsumer vc = buffer.getBuffer(NewMoonRuleGeometry.FLAT);
        Matrix4f mat = pose.last().pose();
        NewMoonRuleGeometry.draw(mat, vc, alpha);
        pose.popPose();

        super.render(entity, yaw, partial, pose, buffer, light);
    }
}
