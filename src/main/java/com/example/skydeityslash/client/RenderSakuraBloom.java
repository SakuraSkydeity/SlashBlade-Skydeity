package com.example.skydeityslash.client;

import com.example.skydeityslash.entity.EntitySakuraBloom;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 「瞳中深渊渊底之吻」的**几何樱花**渲染器 —— 5 片花瓣绕中心均分 72°，每片是一条
 * **短而略弯的三角带**（根部与尖端都收成尖的花瓣形），再加一个很小的亮芯。
 *
 *  · 花瓣所在平面**始终正对相机**（取 `相机 − 实体` 作平面法线，`u/v` 为平面内基），
 *    所以不管从哪看都是完整一朵五瓣花；
 *  · 颜色沿线从花瓣根部 {@link #PETAL_BASE} 渐变到尖端 {@link #PETAL_TIP}；
 *  · 时序：0~3.5 tick 从中心长开（smoothstep），停留到 9 tick，最后 3 tick 淡出。
 *
 * 渲染类型：POSITION_COLOR + 普通半透明（深红用半透明；加法混合会发闷）。
 */
public class RenderSakuraBloom extends EntityRenderer<EntitySakuraBloom> {

    private static final ResourceLocation NONE = new ResourceLocation("skydeityslash", "sakura_bloom");
    private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);

    private static final RenderType BLOOM_TYPE = RState.composite(
            "skydeityslash:sakura_bloom",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            0x8000, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RState.SHADER_POSITION_COLOR)
                    .setTransparencyState(RState.TRANSPARENCY_TRANSLUCENT)
                    .setCullState(RState.CULL_NONE)
                    .setWriteMaskState(RState.WRITE_COLOR)
                    .setOutputState(RState.OUTPUT_MAIN)
                    .createCompositeState(false));

    // ---------------------------------------------------------------- 花瓣参数
    /** 花瓣数（绕中心均分 360/5 = 72°） */
    private static final int PETALS = 5;
    /** 花瓣长度（格）—— 整朵花直径约 0.9 格 */
    private static final float PETAL_LEN = 0.40f;
    /** 花瓣最宽处的半宽（格） */
    private static final float PETAL_HALF_W = 0.105f;
    /** 每片花瓣的侧向弯曲量（格）—— **每片都不一样**，看起来才自然 */
    private static final float[] PETAL_BEND = {0.055f, -0.030f, 0.042f, -0.048f, 0.018f};
    /** 每片花瓣的长度微调（倍率）—— 也不是等长的 */
    private static final float[] PETAL_LEN_MUL = {1.00f, 0.92f, 1.06f, 0.95f, 1.02f};
    /** 花瓣的细分段数 */
    private static final int SEG = 10;

    /** 花瓣颜色：根部 → 尖端 —— **深红**（zankou 主题：根部稍亮的深红 → 尖端更深的暗红） */
    private static final int PETAL_BASE = 0xA8121F;
    private static final int PETAL_TIP = 0x5C0712;
    /** 中心亮芯颜色与半径（比花瓣亮一点，留个火芯，免得整朵发闷） */
    private static final int CORE_RGB = 0xD93A2A;
    private static final float CORE_RADIUS = 0.055f;
    private static final int CORE_SIDES = 6;

    /** 长出所需 tick */
    private static final float GROW_TICKS = 3.5f;
    /** 开始淡出的 tick */
    private static final float FADE_START = 9.0f;

    public RenderSakuraBloom(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(EntitySakuraBloom entity) {
        return NONE;
    }

    @Override
    public void render(EntitySakuraBloom entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        float age = entity.tickCount + partial;
        float life = EntitySakuraBloom.LIFETIME;
        if (age > life) return;
        float fade = age <= FADE_START ? 1f
                : Mth.clamp((life - age) / (life - FADE_START), 0f, 1f);
        if (fade <= 0.01f) return;
        fade = smooth(fade);
        float grow = smooth(Mth.clamp(age / GROW_TICKS, 0f, 1f));

        // 花面正对相机：法线 = 相机 → 实体 的反方向
        Vec3 camLocal = this.entityRenderDispatcher.camera.getPosition()
                .subtract(entity.getX(), entity.getY(), entity.getZ());
        Vec3 n = camLocal.lengthSqr() < 1.0E-8 ? new Vec3(0, 0, 1) : camLocal.normalize();
        Vec3 u = n.cross(WORLD_UP);
        if (u.lengthSqr() < 1.0E-6) u = n.cross(new Vec3(1, 0, 0));
        u = u.normalize();
        Vec3 v = n.cross(u);

        VertexConsumer vc = buffer.getBuffer(BLOOM_TYPE);
        Matrix4f m = pose.last().pose();

        for (int p = 0; p < PETALS; p++) {
            double ang = p * (Math.PI * 2.0 / PETALS);
            float bend = PETAL_BEND[p % PETAL_BEND.length];
            float len = PETAL_LEN * PETAL_LEN_MUL[p % PETAL_LEN_MUL.length] * grow;
            // 花瓣的两个平面内基：dirP = 花瓣朝向，perpP = 平面内垂直于花瓣
            Vec3 dirP = u.scale(Math.cos(ang)).add(v.scale(Math.sin(ang)));
            Vec3 perpP = u.scale(-Math.sin(ang)).add(v.scale(Math.cos(ang)));
            drawPetal(vc, m, dirP, perpP, n, len, bend, fade);
        }

        // 中心亮芯（一个很小的六边形）
        double step = Math.PI * 2.0 / CORE_SIDES;
        for (int s = 0; s < CORE_SIDES; s++) {
            double a0 = s * step;
            double a1 = (s + 1) * step;
            float alpha = fade * 0.95f;
            color(vc, m, planePt(u, v, CORE_RADIUS, a0), CORE_RGB, alpha);
            color(vc, m, planePt(u, v, CORE_RADIUS, a1), CORE_RGB, alpha);
            color(vc, m, Vec3.ZERO, CORE_RGB, alpha);
        }
    }

    /**
     * 画一片花瓣：中心线从中心向外、并沿 {@code perpP} 侧向弯 {@code bend × t²}，
     * 半宽用 `sin(πt)^0.75` 的花瓣形（两端收尖、中段最饱满），颜色从根部渐变到尖端。
     */
    private static void drawPetal(VertexConsumer vc, Matrix4f m, Vec3 dirP, Vec3 perpP, Vec3 n,
                                  float len, float bend, float fade) {
        Vec3 prev = Vec3.ZERO;
        float prevHalf = 0f;
        for (int k = 1; k <= SEG; k++) {
            float t = k / (float) SEG;
            Vec3 cur = dirP.scale(len * t).add(perpP.scale((double) bend * t * t));
            float half = PETAL_HALF_W * petalShape(t) * fade;
            // 花瓣的厚度方向 = 中心线切线 × 花面法线（落在花面内）
            Vec3 tan = cur.subtract(prev);
            if (tan.lengthSqr() < 1.0E-10) {
                prev = cur;
                prevHalf = half;
                continue;
            }
            Vec3 c = n.cross(tan.normalize());
            c = c.lengthSqr() < 1.0E-8 ? perpP : c.normalize();
            Vec3 c0 = c.scale(prevHalf);
            Vec3 c1 = c.scale(half);
            float t0 = (k - 1) / (float) SEG;
            int rgb0 = lerpColor(PETAL_BASE, PETAL_TIP, t0);
            int rgb1 = lerpColor(PETAL_BASE, PETAL_TIP, t);
            float a0 = fade * (1.0f - 0.25f * t0);
            float a1 = fade * (1.0f - 0.25f * t);

            color(vc, m, prev.subtract(c0), rgb0, a0);
            color(vc, m, prev.add(c0), rgb0, a0);
            color(vc, m, cur.add(c1), rgb1, a1);

            color(vc, m, prev.subtract(c0), rgb0, a0);
            color(vc, m, cur.add(c1), rgb1, a1);
            color(vc, m, cur.subtract(c1), rgb1, a1);
            prev = cur;
            prevHalf = half;
        }
    }

    /** 花瓣的宽度包络：两端收尖、中段最饱满 */
    private static float petalShape(float t) {
        return (float) Math.pow(Math.sin(Math.PI * Mth.clamp(t, 0f, 1f)), 0.75);
    }

    private static Vec3 planePt(Vec3 u, Vec3 v, float r, double ang) {
        return u.scale(Math.cos(ang) * r).add(v.scale(Math.sin(ang) * r));
    }

    private static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t), g = (int) (ag + (bg - ag) * t), bl = (int) (ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static void color(VertexConsumer vc, Matrix4f m, Vec3 p, int rgb, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF,
                        Mth.clamp((int) (a * 255.0f), 0, 255))
                .endVertex();
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
