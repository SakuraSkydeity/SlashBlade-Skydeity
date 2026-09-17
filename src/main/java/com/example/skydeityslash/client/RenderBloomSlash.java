package com.example.skydeityslash.client;

import com.example.skydeityslash.entity.EntityBloomSlash;
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
 * 「花开见血血染双瞳」的暗红刀痕渲染器 —— **连续线条**（三角带 ribbon），不用粒子。
 * 这就是原本 zankou SA 那套六条凌乱弧线，原样搬到 SE 里。
 *
 * 布局由 {@link #AZIMUTH_DEG} 等几张**固定参数表**驱动：不随机（每次触发完全一样），
 * 但方位用黄金角散开、倾角/半径/高低/扫掠各不相邻，所以看起来是"散"的。
 *
 * 单条刀痕是**叶形**：起点与终点都收成尖，中段最饱满；随生长逐段铺开，
 * 头部（正在划的地方）始终是尖端，尾部渐隐，划完留一道淡痕，最后整体淡出。
 * 颜色＝暗红（尾 0x8B0B1E → 头 0xD01B33）。
 */
public class RenderBloomSlash extends EntityRenderer<EntityBloomSlash> {

    private static final ResourceLocation NONE = new ResourceLocation("skydeityslash", "bloom_slash");

    /** 自定义渲染类型：顶点色、三角面、半透明、不剔除、只写颜色 */
    private static final RenderType SLASH_TYPE = RState.composite(
            "skydeityslash:bloom_slash",
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

    // ---------------------------------------------------------------- 散乱布局表（固定，不随机）

    /** 每条刀痕绕竖直轴的方位（度）—— 用黄金角 137.5° 铺开，避免等分成"整齐一圈" */
    private static final double[] AZIMUTH_DEG = {0.0, 137.5, 275.0, 52.5, 190.0, 327.5};
    /** 每条刀痕相对水平面的倾角（正=上仰、负=下俯），相邻不重复 */
    private static final double[] TILT = {0.62, -0.34, 0.16, -0.55, 0.40, -0.12};
    /** 弧半径（格）—— 拉开档次，让线条之间空隙更大 */
    private static final float[] RADIUS = {1.34f, 1.78f, 0.84f, 1.52f, 1.06f, 0.68f};
    /** 每条弧的上下偏移（格）—— 高低错落更分散 */
    private static final float[] Y_OFF = {0.36f, -0.44f, 0.06f, 0.54f, -0.20f, -0.58f};
    /** 扫掠角度（度）—— 长短不一 */
    private static final float[] SWEEP_DEG = {128f, 172f, 104f, 156f, 116f, 182f};
    /** 旋向：+1 / −1（不成规律地交替） */
    private static final float[] DIRECTION = {1f, -1f, -1f, 1f, 1f, -1f};

    private static final int ARC_COUNT = AZIMUTH_DEG.length;

    // ---------------------------------------------------------------- 观感参数

    /** 每条弧的细分段数（越大越顺滑） */
    private static final int TRAIL_SEGMENTS = 44;
    /** 残痕的细分段数 */
    private static final int GHOST_SEGMENTS = 40;
    /** 亮拖尾长度（占整条弧的比例） */
    private static final float TAIL = 0.62f;
    /** 线宽（格）—— 2026-09-16 按要求加粗：0.082 → 0.13 */
    private static final float WIDTH = 0.13f;
    /** 两端收尖段长度（占整条弧比例）：越小尖越"锐" */
    private static final float TIP = 0.26f;
    /** 尖端最细时的宽度占比 */
    private static final float TIP_WIDTH = 0.08f;
    /** 划完后残留的痕迹透明度 */
    private static final float GHOST_ALPHA = 0.28f;
    /** 一条弧扫完所需 tick */
    private static final float GROW_TICKS = 9.0f;
    /** 相邻两条弧的起步间隔（tick） */
    private static final float STAGGER = 2.1f;
    /** 末尾淡出 tick */
    private static final float FADE_TICKS = 12.0f;

    private static final int DARK = 0x8B0B1E;
    private static final int BRIGHT = 0xD01B33;

    public RenderBloomSlash(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(EntityBloomSlash entity) {
        return NONE;
    }

    @Override
    public void render(EntityBloomSlash entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        float age = entity.tickCount + partial;
        float fade = age > EntityBloomSlash.LIFETIME - FADE_TICKS
                ? Mth.clamp((EntityBloomSlash.LIFETIME - age) / FADE_TICKS, 0f, 1f) : 1f;
        if (fade <= 0.01f) return;
        fade = smooth(fade);

        Vec3 flat = new Vec3(entity.getArcDirection().x, 0.0, entity.getArcDirection().z);
        if (flat.lengthSqr() < 1.0E-6) flat = new Vec3(0, 0, 1);
        flat = flat.normalize();
        Vec3 up = new Vec3(0, 1, 0);

        VertexConsumer vc = buffer.getBuffer(SLASH_TYPE);
        Matrix4f m = pose.last().pose();

        for (int i = 0; i < ARC_COUNT; i++) {
            Arc arc = layout(i, flat, up);
            float grow = Mth.clamp((age - i * STAGGER) / GROW_TICKS, 0f, 1f);
            if (grow <= 0.002f) continue;
            drawTrail(vc, m, arc, smooth(grow), fade);
            if (grow >= 0.999f) drawGhost(vc, m, arc, fade);
        }
    }

    // ------------------------------------------------------------------ 布局

    /** 一条刀痕的几何参数 */
    private record Arc(Vec3 e1, Vec3 e2, Vec3 offset, float radius, float sweep, float dir) {}

    /** 第 i 条弧的参数（查表，不随机） */
    private static Arc layout(int i, Vec3 flat, Vec3 up) {
        double ang = Math.toRadians(AZIMUTH_DEG[i % ARC_COUNT]);
        double cs = Math.cos(ang), sn = Math.sin(ang);
        Vec3 axis = new Vec3(flat.x * cs + flat.z * sn, 0.0, -flat.x * sn + flat.z * cs).normalize();
        Vec3 e1 = axis.add(0.0, TILT[i % ARC_COUNT], 0.0).normalize();
        Vec3 e2 = up.subtract(e1.scale(up.dot(e1)));
        e2 = e2.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : e2.normalize();
        return new Arc(e1, e2,
                new Vec3(0, Y_OFF[i % ARC_COUNT], 0),
                RADIUS[i % ARC_COUNT],
                SWEEP_DEG[i % ARC_COUNT],
                DIRECTION[i % ARC_COUNT]);
    }

    private static double angleAt(Arc arc, float u) {
        return Math.toRadians(-arc.sweep() * 0.5 + arc.sweep() * u) * arc.dir();
    }

    private static Vec3 pointAt(Arc arc, float u) {
        double a = angleAt(arc, u);
        return arc.e1().scale(Math.cos(a) * arc.radius())
                .add(arc.e2().scale(Math.sin(a) * arc.radius()))
                .add(arc.offset());
    }

    /** 弧面内垂直于切线的方向（用来撑开 ribbon 宽度） */
    private static Vec3 radialAt(Arc arc, float u) {
        double a = angleAt(arc, u);
        return arc.e1().scale(Math.cos(a)).add(arc.e2().scale(Math.sin(a)));
    }

    // ------------------------------------------------------------------ 剖面（叶形：两端尖、中段饱满）

    /** 沿弧的线宽占比：u=0 与 u=1 收成尖，中段 =1（平滑过渡） */
    private static float leafWidth(float u) {
        float t = smooth(Mth.clamp(Math.min(u, 1f - u) / TIP, 0f, 1f));
        return TIP_WIDTH + (1f - TIP_WIDTH) * t;
    }

    /** 综合线宽占比：叶形剖面 × 正在延伸的头部（头部始终是尖端） */
    private static float widthAt(float u, float grow) {
        float w = leafWidth(u);
        if (grow < 0.999f) {
            w *= smooth(Mth.clamp((grow - u) / 0.12f, 0f, 1f));
        }
        return w;
    }

    // ------------------------------------------------------------------ 绘制

    /** 亮拖尾：只画 [grow-TAIL, grow] 这一段，头部亮、尾部渐隐 */
    private static void drawTrail(VertexConsumer vc, Matrix4f m, Arc arc, float grow, float fade) {
        float uStart = Math.max(0f, grow - TAIL);
        float span = grow - uStart;
        if (span <= 1.0E-4f) return;
        for (int k = 0; k < TRAIL_SEGMENTS; k++) {
            float ua = uStart + span * (k / (float) TRAIL_SEGMENTS);
            float ub = uStart + span * ((k + 1) / (float) TRAIL_SEGMENTS);
            float aa = alphaAlong(ua, grow);
            float ab = alphaAlong(ub, grow);
            if (aa <= 0.004f && ab <= 0.004f) continue;

            Vec3 pa = pointAt(arc, ua), pb = pointAt(arc, ub);
            Vec3 qa = radialAt(arc, ua).scale(WIDTH * 0.5f * widthAt(ua, grow));
            Vec3 qb = radialAt(arc, ub).scale(WIDTH * 0.5f * widthAt(ub, grow));

            ribbon(vc, m, pa.subtract(qa), pa.add(qa), pb.add(qb), pb.subtract(qb),
                    aa * fade, ab * fade, aa, ab);
        }
    }

    /** 划完后残留的一整条淡痕（同样是叶形剖面，两端收尖） */
    private static void drawGhost(VertexConsumer vc, Matrix4f m, Arc arc, float fade) {
        float a = GHOST_ALPHA * fade;
        for (int k = 0; k < GHOST_SEGMENTS; k++) {
            float ua = k / (float) GHOST_SEGMENTS, ub = (k + 1) / (float) GHOST_SEGMENTS;
            Vec3 pa = pointAt(arc, ua), pb = pointAt(arc, ub);
            Vec3 qa = radialAt(arc, ua).scale(WIDTH * 0.44f * leafWidth(ua));
            Vec3 qb = radialAt(arc, ub).scale(WIDTH * 0.44f * leafWidth(ub));
            ribbon(vc, m, pa.subtract(qa), pa.add(qa), pb.add(qb), pb.subtract(qb),
                    a, a, 0.0f, 0.0f);
        }
    }

    /** 拖尾亮度：头部 1，向尾部平滑衰减到 0 */
    private static float alphaAlong(float u, float grow) {
        return smooth(Mth.clamp(1.0f - (grow - u) / TAIL, 0f, 1f));
    }

    /** 四边面：p0/p1 用同一透明度、p2/p3 用另一透明度 */
    private static void ribbon(VertexConsumer vc, Matrix4f m,
                               Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                               float aA, float aB, float tA, float tB) {
        int cA = lerpColor(DARK, BRIGHT, tA);
        int cB = lerpColor(DARK, BRIGHT, tB);
        vtx(vc, m, p0, cA, aA);
        vtx(vc, m, p3, cA, aA);
        vtx(vc, m, p2, cB, aB);

        vtx(vc, m, p0, cA, aA);
        vtx(vc, m, p2, cB, aB);
        vtx(vc, m, p1, cB, aB);
    }

    private static void vtx(VertexConsumer vc, Matrix4f m, Vec3 p, int rgb, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF,
                        Mth.clamp((int) (a * 255.0f), 0, 255))
                .endVertex();
    }

    private static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t), g = (int) (ag + (bg - ag) * t), bl = (int) (ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** 平滑（smoothstep） */
    private static float smooth(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
