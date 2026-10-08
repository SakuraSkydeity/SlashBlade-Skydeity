package com.example.skydeityslash.client;

import com.example.skydeityslash.entity.EntityChikuiFlower;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 「赤葵」第二种 SA 的**程序化花朵** —— 纯几何绘制（无贴图、无粒子），加法混合。
 *
 * <p>结构：**8 片外层花瓣 + 8 片内层花瓣**（错开半格）＋ 亮芯 ＋ 9 根花蕊。
 * 每片花瓣由两条带子叠成：**宽而暗的主体** + **细而亮的中脉**（和 {@code GlowGeometry.line} 同一思路），
 * 所以花瓣中间有一条亮脉、边缘自然消散。
 *
 * <p>张开动画：外层花瓣从"卷曲"（{@code bend} 大）随 {@code grow} 展开到平铺，内层花瓣随后张开
 * （{@code open} 比 {@code grow} 晚 4 tick 起步），整体还带一点呼吸缩放。
 *
 * <p>★ **加法混合的 alpha 预算**：同一像素上所有层相加不能超过 1，否则叠爆成白（会丢掉全部颜色）。
 * 所以这里的 {@code A_*} 都压得很低（0.22~0.55），并且越靠内层越低。改颜色/加层时务必同步复核。
 * 颜色也一并保持**高饱和的赤红**（饱和度低一档整体就会发白）。
 *
 * <p>几何在**平面内**给出（正交语义）：调用方传入平面基 {@code u}/{@code v}（公告板，法线朝相机），
 * 本类所有点都是 {@code u*x + v*y} 的组合。
 */
public final class ChikuiFlowerGeometry {

    private ChikuiFlowerGeometry() {
    }

    // ================================================================ 尺寸参数
    /** 外层花瓣：条数 / 长度 / 最宽处半宽 / 起画半径（格） */
    private static final int N_OUTER = 8;
    private static final float OUTER_LEN = 2.85f, OUTER_HALF = 0.88f, OUTER_START = 0.30f;
    /** 内层花瓣：与外层错开半格 */
    private static final int N_INNER = 8;
    private static final float INNER_LEN = 1.75f, INNER_HALF = 0.62f, INNER_START = 0.16f;
    /** 花芯最大半径 */
    private static final float CORE_R = 0.60f;
    /** 花蕊：根数 / 起点半径 / 终点半径 */
    private static final int STAMEN_N = 9;
    private static final float STAMEN_R0 = 0.32f, STAMEN_R1 = 1.55f;

    /** 基准段数（近档），由 {@link #setDetail(int)} 按距离降档 */
    private static final int SEG_OUTER_FULL = 12, SEG_INNER_FULL = 10, CORE_SEG_FULL = 44;
    private static int SEG_OUTER = SEG_OUTER_FULL, SEG_INNER = SEG_INNER_FULL,
            CORE_SEG = CORE_SEG_FULL;

    // ================================================================ 颜色（zankou 主题：赤红，已提饱和）
    private static final int OUTER_BASE = 0x5E000B, OUTER_TIP = 0xC10021, OUTER_VEIN = 0xFF0E35;
    private static final int INNER_BASE = 0x8C0018, INNER_TIP = 0xF0002F, INNER_VEIN = 0xFF7D9B;
    private static final int CORE_EDGE = 0xD80E00, CORE_HOT = 0xFFC671;
    private static final int STAMEN = 0xFFA720, STAMEN_TIP = 0xFFE494;

    // ================================================================ alpha 预算（★ 见类注释）
    private static final float A_OUTER_BAND = 0.22f, A_OUTER_VEIN = 0.30f;
    private static final float A_INNER_BAND = 0.26f, A_INNER_VEIN = 0.30f;
    private static final float A_CORE = 0.55f, A_STAMEN = 0.42f;

    /** 花瓣主体 / 中脉的宽度倍率 */
    private static final float VEIN_W = 0.24f;
    /** 花蕊的三层（宽度倍率、alpha 倍率、颜色）—— 外晕 + 主线 + 白热芯 */
    private static final float STAMEN_BASE_W = 0.052f;

    // ================================================================ 时序
    /** 外层花瓣长出所需 tick */
    private static final float GROW_TICKS = 16f;
    /** 内层花瓣张开（比外层晚 4 tick 起步）所需 tick */
    private static final float OPEN_TICKS = 18f, OPEN_DELAY = 4f;
    /**
     * 末尾**消散**所需 tick（寿命本身取 {@link EntityChikuiFlower#LIFETIME}，避免两处魔数漂移）。
     *
     * <p>★ 2026-09-28：这段就是**一次均匀的过渡**——alpha 用 smoothstep 一路塌到 0，
     * 同时颜色**往浅端走一点点**（往 {@link #FADE_TINT} 混，最多 {@link #FADE_TINT_MIX}），
     * 所以是"花慢慢淡掉"，不是"碎成片飞走"（飞散版已按用户要求去掉）。
     * ★★ 分寸：色调只能往**同色相的浅端**挪一点 —— 混多了会发白，反而像没淡干净。
     */
    private static final float FADE_TICKS = 20f;
    /** 消散时颜色趋向的浅色（比花本色亮一档的粉红）与最多混入的比例 */
    private static final int FADE_TINT = 0xFF7C93;
    private static final float FADE_TINT_MIX = .40f;

    /** 按距离降档（近处保持原值） */
    public static void setDetail(int tier) {
        int pct = FxLod.percent(tier);
        SEG_OUTER = Math.max(6, SEG_OUTER_FULL * pct / 100);
        SEG_INNER = Math.max(5, SEG_INNER_FULL * pct / 100);
        CORE_SEG = Math.max(18, CORE_SEG_FULL * pct / 100);
    }

    /**
     * 画整朵花。{@code u}/{@code v} 是花朵所在平面的一组正交基（u = 水平、v = 竖直），
     * 平面法线朝相机 —— 由渲染器按公告板算出来。
     */
    public static void draw(Matrix4f m, VertexConsumer vc, float age, Vec3 u, Vec3 v) {
        float grow = smooth(stage(age, 0f, GROW_TICKS));
        float opened = smooth(stage(age, OPEN_DELAY, OPEN_TICKS));
        // ★ 末尾"消散"：一段均匀的过渡 —— alpha 塌到 0，颜色同时往浅端走一点（越淡越浅）
        float fade = 1f - smooth(stage(age, EntityChikuiFlower.LIFETIME - FADE_TICKS, FADE_TICKS));
        if (fade <= 0.01f) return;
        float tint = (1f - fade) * FADE_TINT_MIX;         // 0 → FADE_TINT_MIX
        float breathe = 1f + 0.035f * Mth.sin(age * 0.16f);

        // ---------- 外层花瓣：grow 过程中由"卷起来"展开成平铺 ----------
        float curlOuter = (1f - grow) * 0.95f;
        for (int i = 0; i < N_OUTER; i++) {
            double ang = i * (Math.PI * 2.0 / N_OUTER) + Math.PI / N_OUTER;
            float jitter = 1f + 0.06f * Mth.sin(i * 2.399f);
            float bend = (curlOuter + 0.10f * Mth.sin(i * 1.7f)) * (i % 2 == 0 ? 1f : -0.72f);
            float alpha = A_OUTER_BAND * (0.88f + 0.12f * Mth.sin(age * 0.28f + i));
            drawPetal(m, vc, u, v, ang, OUTER_LEN * jitter * grow * breathe, OUTER_HALF,
                    OUTER_START * grow, bend,
                    wash(OUTER_BASE, tint), wash(OUTER_TIP, tint), wash(OUTER_VEIN, tint),
                    alpha, A_OUTER_VEIN, SEG_OUTER);
        }

        // ---------- 内层花瓣：错开 22.5°，稍短、更亮 ----------
        float curlInner = (1f - opened) * 0.80f;
        for (int i = 0; i < N_INNER; i++) {
            double ang = i * (Math.PI * 2.0 / N_INNER);
            float jitter = 1f + 0.05f * Mth.sin(i * 1.913f);
            float bend = (curlInner + 0.07f * Mth.sin(i * 2.1f)) * (i % 2 == 0 ? 1f : -0.68f);
            float alpha = A_INNER_BAND * (0.88f + 0.12f * Mth.sin(age * 0.31f + i * 1.7f));
            drawPetal(m, vc, u, v, ang, INNER_LEN * jitter * opened * breathe, INNER_HALF,
                    INNER_START * opened, bend,
                    wash(INNER_BASE, tint), wash(INNER_TIP, tint), wash(INNER_VEIN, tint),
                    alpha, A_INNER_VEIN, SEG_INNER);
        }

        // ---------- 花芯 + 花蕊 ----------
        drawCore(m, vc, u, v, CORE_R * (0.5f + 0.5f * opened) * breathe, fade * A_CORE, tint);
        for (int i = 0; i < STAMEN_N; i++) {
            double ang = i * (Math.PI * 2.0 / STAMEN_N) + 0.25;
            float a = fade * A_STAMEN * (0.85f + 0.15f * Mth.sin(age * 0.35f + i));
            drawStamen(m, vc, u, v, ang, STAMEN_R0, STAMEN_R1 * opened, a, tint);
        }
    }

    // ================================================================ 图元

    /**
     * 一片花瓣 = 两条带子叠起来：
     *  · 主体（宽、暗）：宽度包络 {@link #petalShape}，从根部本色渐变到尖端亮色；
     *  · 中脉（细、亮）：只有主体的 {@link #VEIN_W} 宽，整条用 {@code veinRgb}。
     * 中心线沿 {@code perp} 侧向弯 {@code bend × t²} —— {@code bend} 由大到小就是"展开"。
     */
    private static void drawPetal(Matrix4f m, VertexConsumer vc, Vec3 u, Vec3 v, double ang,
                                  float len, float halfW, float startR, float bend,
                                  int baseRgb, int tipRgb, int veinRgb,
                                  float aBand, float aVein, int seg) {
        Vec3 dir = u.scale(Math.cos(ang)).add(v.scale(Math.sin(ang)));
        Vec3 perp = u.scale(-Math.sin(ang)).add(v.scale(Math.cos(ang)));
        for (int layer = 0; layer < 2; layer++) {
            boolean vein = layer == 1;
            float wMul = vein ? VEIN_W : 1f;
            float aMul = vein ? aVein : aBand;
            int ca = vein ? veinRgb : baseRgb, cb = vein ? veinRgb : tipRgb;

            Vec3 prev = point(dir, perp, startR, len, startR, bend, 0f);
            float prevHalf = 0f, prevA = aMul;
            int prevRgb = lerpRgb(ca, cb, 0f);
            for (int k = 1; k <= seg; k++) {
                float t = k / (float) seg;
                Vec3 cur = point(dir, perp, startR, len, startR, bend, t);
                float half = halfW * wMul * petalShape(t);
                int rgb = lerpRgb(ca, cb, t);
                float a = aMul * (1f - 0.30f * t);
                Vec3 c0 = perp.scale(prevHalf), c1 = perp.scale(half);
                // 两条三角形拼成一段四边形（逐顶点颜色/alpha）
                tri(m, vc, prev.subtract(c0), prevRgb, prevA, prev.add(c0), prevRgb, prevA,
                        cur.add(c1), rgb, a);
                tri(m, vc, prev.subtract(c0), prevRgb, prevA, cur.add(c1), rgb, a,
                        cur.subtract(c1), rgb, a);
                prev = cur;
                prevHalf = half;
                prevRgb = rgb;
                prevA = a;
            }
        }
    }

    /** 花瓣中心线上的一点：从 {@code startR} 由内向外长，并沿 {@code perp} 侧弯 {@code bend·t²} */
    private static Vec3 point(Vec3 dir, Vec3 perp, float startR, float len, float from, float bend, float t) {
        double r = from + (len - from) * t;
        return dir.scale(r).add(perp.scale((double) bend * t * t));
    }

    /** 花瓣宽度包络：根部收细、中后段最饱满、尖端再收成圆头（指数越小越圆润） */
    private static float petalShape(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return (float) Math.pow(Math.sin(Math.PI * Math.pow(t, 0.80)), 0.55);
    }

    /** 平面内极坐标取点（花芯扇形用） */
    private static Vec3 radial(Vec3 u, Vec3 v, float r, double ang) {
        return u.scale(Math.cos(ang) * r).add(v.scale(Math.sin(ang) * r));
    }

    /** 花芯：一个由"暖白中心 → 赤红边缘"的扇形盘，再叠一个更亮的小盘（{@code tint} = 消散时往浅端混的比例） */
    private static void drawCore(Matrix4f m, VertexConsumer vc, Vec3 u, Vec3 v, float radius, float a, float tint) {
        int edge = wash(CORE_EDGE, tint), hot = wash(CORE_HOT, tint);
        for (int i = 0; i < CORE_SEG; i++) {
            double t0 = i * (Math.PI * 2.0 / CORE_SEG), t1 = (i + 1) * (Math.PI * 2.0 / CORE_SEG);
            tri(m, vc, Vec3.ZERO, lerpRgb(hot, edge, 0.35f), a,
                    radial(u, v, radius, t0), edge, a * 0.25f,
                    radial(u, v, radius, t1), edge, a * 0.25f);
        }
        float r2 = radius * 0.42f;
        for (int i = 0; i < CORE_SEG; i++) {
            double t0 = i * (Math.PI * 2.0 / CORE_SEG), t1 = (i + 1) * (Math.PI * 2.0 / CORE_SEG);
            tri(m, vc, Vec3.ZERO, hot, a * 0.85f,
                    radial(u, v, r2, t0), hot, a * 0.20f,
                    radial(u, v, r2, t1), hot, a * 0.20f);
        }
    }

    /** 一根花蕊：三层细线（暗金外晕 + 金主线 + 亮芯）+ 顶端的金色小球（{@code tint} 同上） */
    private static void drawStamen(Matrix4f m, VertexConsumer vc, Vec3 u, Vec3 v, double ang,
                                   float r0, float r1, float a, float tint) {
        if (r1 <= r0 + 0.02f) return;
        Vec3 dir = u.scale(Math.cos(ang)).add(v.scale(Math.sin(ang)));
        Vec3 perp = u.scale(-Math.sin(ang)).add(v.scale(Math.cos(ang)));
        final float[][] LAYERS = {{0.34f, 0.30f}, {0.14f, 0.70f}, {0.045f, 1.00f}};
        int[] COLS = {STAMEN, STAMEN, STAMEN_TIP};
        for (int i = 0; i < LAYERS.length; i++) {
            float w = STAMEN_BASE_W * LAYERS[i][0];
            float am = a * LAYERS[i][1];
            int col = wash(COLS[i], tint);
            quad(m, vc,
                    dir.scale(r0).subtract(perp.scale(w)), col, 0f,
                    dir.scale(r1).subtract(perp.scale(w)), col, am,
                    dir.scale(r1).add(perp.scale(w)), col, am,
                    dir.scale(r0).add(perp.scale(w)), col, 0f);
        }
        Vec3 tip = dir.scale(r1 + 0.045f);
        int tipCol = wash(STAMEN_TIP, tint);
        float s = 0.075f;
        quad(m, vc,
                tip.subtract(u.scale(s)), tipCol, a * 0.80f,
                tip.subtract(v.scale(s)), tipCol, a * 0.80f,
                tip.add(u.scale(s)), tipCol, a * 0.80f,
                tip.add(v.scale(s)), tipCol, a * 0.80f);
    }

    /** 消散时的"往浅走"：把颜色朝 {@link #FADE_TINT} 混 {@code mix} 比例（0 = 原色） */
    private static int wash(int rgb, float mix) {
        return mix <= 0f ? rgb : lerpRgb(rgb, FADE_TINT, mix);
    }

    // ================================================================ 顶点 / 工具
    private static void vv(Matrix4f m, VertexConsumer vc, Vec3 p, int rgb, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF,
                        Mth.clamp((int) (a * 255f), 0, 255))
                .endVertex();
    }

    private static void tri(Matrix4f m, VertexConsumer vc, Vec3 p0, int c0, float a0,
                            Vec3 p1, int c1, float a1, Vec3 p2, int c2, float a2) {
        vv(m, vc, p0, c0, a0);
        vv(m, vc, p1, c1, a1);
        vv(m, vc, p2, c2, a2);
    }

    private static void quad(Matrix4f m, VertexConsumer vc, Vec3 p0, int c0, float a0,
                             Vec3 p1, int c1, float a1, Vec3 p2, int c2, float a2,
                             Vec3 p3, int c3, float a3) {
        tri(m, vc, p0, c0, a0, p1, c1, a1, p2, c2, a2);
        tri(m, vc, p0, c0, a0, p2, c2, a2, p3, c3, a3);
    }

    private static int lerpRgb(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (((int) (ar + (br - ar) * t)) << 16) | (((int) (ag + (bg - ag) * t)) << 8)
                | ((int) (ab + (bb - ab) * t));
    }

    private static float stage(float age, float start, float dur) {
        return Mth.clamp((age - start) / dur, 0f, 1f);
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
