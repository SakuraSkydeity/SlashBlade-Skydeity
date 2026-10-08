package com.example.skydeityslash.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 「翾风回雪」圆环几何层 —— 三段轨道弧环 + 四层倾斜悬浮环，
 * 配色为**淡蓝渐变**（带子中段一支浅蓝、两侧淡到近乎透明蓝白），环绕锁定目标/落点。
 * 原生 POSITION_COLOR 管线、无贴图、加法混合（使用 {@link GlowGeometry#GLOW}）。
 *
 * <p>★ 2026-09-27 改（按反馈「几个环都交错一点 / 最后画出来的几个偏位 / 改淡蓝渐变」）：
 * <ul>
 *   <li><b>交错</b>：四层悬浮环不再同处一个水平面（那样从地面看就是几圈"叠在头顶的扁椭圆"、且越外圈在屏幕上越偏上，
 *       看着像偏位）—— 现在每层**各自倾斜**（倾角/倾轴方位都不同，{@link #FLOAT}），于是四层互相穿插；</li>
 *   <li><b>同心</b>：四层共用同一个中心（{@code FLOAT_Y}），螺旋相位也错开，所以是"环绕"而不是"偏位"；</li>
 *   <li><b>渐变</b>：带宽方向由 7 列顶点构成（外缘透明 → 芯部 {@link #CORE} → 外缘透明），
 *       横向是**连续渐变**而不是"外晕 + 亮芯"两条独立带子（那条老写法看着就是"一层套一层"）。</li>
 * </ul>
 * 三段轨道弧环的角度区间也重排成"每段 150°、相邻两段重叠 30°"，让它们在低空相互穿插。
 * （竖弧组在 {@code GlowGeometry.verticalArcsCentered} 一侧，由 {@code RenderXuanfengRing} 摆放。）
 *
 * <p>★★ <b>「白色描边」的成因与修法（2026-09-28，踩了两次）</b>：
 * <ol>
 *   <li>第一次：渐变的**淡端用了近白** `.80/.93/1.00` —— 低 alpha 的近白在加法混合下就是一圈发白的镶边。
 *       修法：淡端也**有色相**（蓝 → 更淡的蓝），亮度只由 alpha 管。</li>
 *   <li>第二次（"水平环上特别明显"）：**带子躺在环自己的平面里**时，接近水平的环从站立视角看是**贴边**的 ——
 *       整条带宽（7 列）压进同一个像素，加法混合把该像素的 R/G/B 一起叠到溢出，夹到 (1,1,1) 就是**一道纯白线**。
 *       修法：**带子一律朝相机**（{@code ribbon(..., normal = null, camera, ...)}）⇒ 永远展得开、按像素摊平；
 *       同时把峰值 alpha 再压低一档（{@link #RING_BACK} 的 maxOpacity、悬浮环的 alpha 系数）。</li>
 * </ol>
 * 判据很简单：**加法混合里"一像素上的总累积量"才是亮度**（顶点色 × alpha 之和），
 * 所以又薄又贴边的几何最容易在某个像素堆到溢出 → 白边。
 */
final class OdetteRingGeometry {
    interface Curve { Vec3 point(float u); }

    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final float TAU = (float) Math.PI * 2;

    // ---------------- 配色：淡蓝渐变 ----------------
    /** 带芯的蓝 —— ★ 2026-09-28 加深（原 `.46/.74/1.00`）：与外缘拉开色差，渐变才看得出来 */
    private static final float[] CORE = {.18F, .48F, 1.00F};
    /**
     * 带子外缘的颜色（比芯淡一档的**蓝**）。
     * ★★ 2026-09-28 修「白色的边框」：外缘原来是 `.80/.93/1.00`（近白）——加法混合下，
     * 那圈低 alpha 的近白就是**一道发白的镶边**，看着像给圆环描了白边。现在两端都还是蓝，
     * 渐变（深蓝 → 淡蓝）保留，但没有白。
     */
    private static final float[] EDGE = {.46F, .74F, 1.00F};
    /**
     * 带宽方向的 **7 列**采样（-1 / +1 = 两侧外缘）。
     * ★ 2026-09-28：列数从 4 加到 7、过渡段拉长（见 {@link #COL_T}）⇒ 横向那道"蓝 → 淡蓝"的**渐变更明显也更平滑**。
     */
    private static final float[] COL_F = {-1F, -.66F, -.34F, 0F, .34F, .66F, 1F};
    /** 每一列里 {@link #CORE} 的权重（0 = 全 EDGE，1 = 全 CORE）⇒ 中间一道窄芯、两侧长过渡 */
    private static final float[] COL_T = {0F, .45F, .85F, 1F, .85F, .45F, 0F};
    /** 每一列的 alpha 倍率（外缘归 0 ⇒ 没有硬边，横向就是一道渐变） */
    private static final float[] COL_A = {0F, .55F, .90F, 1F, .90F, .55F, 0F};
    /** 每一列的**实际颜色**（按 {@link #COL_T} 在 EDGE→CORE 之间插值，类加载时算一次） */
    private static final float[][] COL_RGB = new float[COL_F.length][];
    static {
        for (int i = 0; i < COL_F.length; i++) {
            COL_RGB[i] = new float[]{
                    EDGE[0] + (CORE[0] - EDGE[0]) * COL_T[i],
                    EDGE[1] + (CORE[1] - EDGE[1]) * COL_T[i],
                    EDGE[2] + (CORE[2] - EDGE[2]) * COL_T[i]};
        }
    }

    // RING_BACK 三段轨道弧环：{start冲击开始, impact, release, end, maxOpacity}（smoother 平滑）
    // ★ 2026-09-28 消散加快：收尾窗口从 7 tick 压到 4 tick，并且整体提前
    // ★ 2026-09-28 二次：峰值 alpha 也压低一档（.34/.30/.26 → .24/.21/.18）—— 见类注释「白色描边」的成因
    private static final float[][] RING_BACK = {
            {5.5F, 8.5F, 18.5F, 22.5F, .24F},
            {6.8F, 9.8F, 20F, 24F, .21F},
            {8.2F, 11.2F, 21.5F, 25.5F, .18F},
    };
    /**
     * 各段轨道参数：{start度, end度, rx, rz, sy, ey, hb, bulge, twist}。
     * ★ 三段各 150°、起点 0/120/240 ⇒ 相邻两段重叠 30°，三圈低空弧相互穿插；y 都保持在原点之上（不再有沉到地下的）。
     */
    private static final float[][] RING_BACK_ORBIT = {
            {8F, 158F, 3.30F, 2.14F, .16F, .04F, .09F, .026F, .040F},
            {128F, 278F, 3.12F, 2.02F, .30F, .18F, .07F, .022F, -.035F},
            {248F, 398F, 3.44F, 2.24F, .10F, .26F, .08F, .020F, .030F},
    };
    /** 各段轨道带宽（带子总宽；芯部约占 45%） */
    private static final float[] RING_BACK_W = {.090F, .084F, .078F};

    /**
     * 四层悬浮环：{半径, 倾角°, 倾轴方位°, 带宽, 自旋相位(0..1), 弧头, 弧尾}。
     * ★ 倾角正负交替、倾轴方位 0/92/186/278 ⇒ 四层两两穿插，且都穿过同一个中心 ⇒ 不会看着"偏位"。
     */
    private static final float[][] FLOAT = {
            {1.52F, 20F, 6F, .030F, .00F, .93F, .03F},
            {2.18F, -16F, 92F, .036F, .27F, .89F, .15F},
            {2.86F, 22F, 186F, .042F, .54F, .85F, .29F},
            {3.52F, -20F, 278F, .048F, .78F, .91F, .43F},
    };
    /** 四层悬浮环的共同中心高度（相对实体原点，格）—— 同心才不会看着偏位 */
    private static final float FLOAT_Y = 1.20F;
    /** 悬浮环自旋速度（圈/tick） */
    private static final float FLOAT_SPIN = .05F;

    private OdetteRingGeometry() {}

    static final class Basis { final Vec3 right, forward; Basis(Vec3 r, Vec3 f) { right = r; forward = f; } }

    static Basis basis(Vec3 direction) {
        Vec3 f = new Vec3(direction.x, 0, direction.z);
        if (f.lengthSqr() < 1E-8) f = new Vec3(0, 0, 1); else f = f.normalize();
        return new Basis(UP.cross(f).normalize(), f);
    }

    /** 主入口：以 center 为圆心（相对——实体自身即为落点），q 为朝向基，绘制淡蓝渐变圆环。 */
    static void renderRings(Matrix4f m, VertexConsumer out, float frame, Vec3 center, Basis q, Vec3 camera, long seed) {
        // 三段 RING_BACK 轨道弧环（带子朝相机，横向渐变：中段浅蓝、两侧淡到透明）
        for (int i = 0; i < 3; i++) {
            drawTrack(m, out, frame, center, q, camera, seed + 1151 + i * 41,
                    RING_BACK[i], RING_BACK_ORBIT[i], RING_BACK_W[i]);
        }
        // 四层倾斜悬浮环（同心、交错、缓慢自旋）—— 带子同样**朝相机**，见下方注释
        drawFloatingRings(m, out, frame, center, q, camera, seed + 2600);
    }

    // ---------------- 轨道弧环 ----------------
    private static void drawTrack(Matrix4f m, VertexConsumer out, float f, Vec3 center, Basis q, Vec3 camera,
                                  long seed, float[] cue, float[] orbit, float width) {
        float a = envelope(cue, f);
        if (a <= .001F) return;
        Curve curve = orbit(center, q, orbit);
        float head = reveal(cue, f), tail = exit(cue, f), er = .25F + smooth(f, cue[2], cue[3]) * .50F;
        // 朝相机的带子（normal = null）⇒ 任何角度都是一条看得见的弧
        ribbon(m, out, curve, 56, head, tail, width, null, camera, a, er, seed, f);
    }

    // ---------------- 倾斜悬浮环 ----------------
    private static void drawFloatingRings(Matrix4f m, VertexConsumer out, float f, Vec3 center, Basis q, Vec3 camera,
                                          long seed) {
        float open = smoother(stage(f, 2.5F, 9F));
        float fade = 1 - smoother(stage(f, FADE_START, FADE_DUR));      // ★ 消散加快后的收尾窗口
        float a = open * fade;
        if (a <= .001F) return;
        float pulse = .5F + .5F * Mth.sin(f * .35F);          // 轻微脉动
        Vec3 c = center.add(0, FLOAT_Y, 0);
        for (int i = 0; i < FLOAT.length; i++) {
            float[] r = FLOAT[i];
            float radius = r[0] + pulse * (.08F + i * .03F);
            // 环平面：法线 = UP 绕水平轴（方位 r[2]）倾斜 r[1] 度；面内两轴 e1（水平，方位 r[2]+90°）、e2 = n × e1
            float t = r[1] * Mth.DEG_TO_RAD, az = r[2] * Mth.DEG_TO_RAD;
            float ct = Mth.cos(t), st = Mth.sin(t), ca = Mth.cos(az), sa = Mth.sin(az);
            Vec3 e1 = new Vec3(ca, 0, sa), e2 = new Vec3(ct * sa, st, -ct * ca);
            float base = f * FLOAT_SPIN * TAU + r[4] * TAU;
            final float rr = radius;
            Curve ring = u -> {
                float ang = u * TAU + base, cc = Mth.cos(ang), ss = Mth.sin(ang);
                return c.add(e1.scale(cc * rr)).add(e2.scale(ss * rr));
            };
            float alpha = a * (.22F - i * .025F + pulse * .12F);
            // ★★ 带子**朝相机**（normal = null），不再"躺在环平面里"：
            //   躺在平面里时，接近水平的环从站立视角看是**贴边**的 —— 整条带宽（7 列）会压进同一个像素，
            //   加法混合把那一像素的 R/G/B 一起叠到溢出 ⇒ 夹到 (1,1,1) 就是**一道纯白的描边**
            //   （用户反馈"水平环上特别明显"）。朝相机后带子永远展得开，累积量按像素摊平，白边消失。
            ribbon(m, out, ring, 64, r[5], r[6], r[3], null, camera, alpha, .20F, seed + i * 31, f);
        }
    }

    // ---------------- 时间轴 ----------------
    /** 悬浮环开始淡出 / 淡出时长（tick）—— ★ 2026-09-28 按要求"消散快一点"：原 27→36（9 tick），现 24→28（4 tick） */
    private static final float FADE_START = 24F, FADE_DUR = 4F;
    private static float envelope(float[] c, float f) {
        return c[4] * Math.min(smooth(f, c[0], c[1]), 1 - smooth(f, c[2], c[3]));
    }
    private static float reveal(float[] c, float f) { return smooth(f, c[0], c[1]); }
    private static float exit(float[] c, float f) { return .94F * smooth(f, c[2], c[3]); }
    private static float smooth(float v, float from, float to) {
        if (Math.abs(to - from) < 1E-6F) return v >= to ? 1 : 0;
        float x = Mth.clamp((v - from) / (to - from), 0, 1);
        return x * x * (3 - 2 * x);
    }
    private static float stage(float f, float start, float dur) { return Mth.clamp((f - start) / dur, 0, 1); }
    private static float smoother(float t) { t = Mth.clamp(t, 0, 1); return t * t * t * (t * (t * 6 - 15) + 10); }

    // ---------------- 曲线 ----------------
    private static Curve orbit(final Vec3 c, final Basis q, final float[] p) {
        return u -> { double angle = Math.toRadians(p[0] + (p[1] - p[0]) * u), scale = 1 + Math.sin(Math.PI * u) * p[7];
            return local(c, q, Math.cos(angle) * p[2] * scale, p[4] + (p[5] - p[4]) * u + Math.sin(Math.PI * u) * p[6] + Math.sin(Math.PI * 2 * u) * p[8], Math.sin(angle) * p[3] * scale); };
    }
    private static Vec3 local(Vec3 c, Basis q, double x, double y, double z) {
        return c.add(q.right.scale(x)).add(0, y, 0).add(q.forward.scale(z));
    }

    // ---------------- ribbon 图元 ----------------
    /**
     * 沿曲线铺一条**横向渐变**的带子。
     *
     * @param normal {@code null} ⇒ 带子朝相机（侧向 = 切向 × 视线）；否则带子躺在 normal 所在的平面里（侧向 = normal × 切向）
     */
    private static void ribbon(Matrix4f m, VertexConsumer out, Curve curve, int segments, float head, float tail,
                               float width, Vec3 normal, Vec3 camera, float alpha, float erode, long seed, float frame) {
        float start = Mth.clamp(Math.min(tail, head), 0, 1), end = Mth.clamp(Math.max(tail, head), 0, 1);
        if (alpha <= .001F || end <= start + 1E-4F) return;
        int count = Math.max(2, segments);
        float span = end - start;
        for (int i = 0; i < count; i++) {
            float u0 = i / (float) count, u1 = (i + 1) / (float) count;
            if (u1 < start || u0 > end) continue;
            float a = Math.max(start, u0), b = Math.min(end, u1), middle = (a + b) * .5F;
            float noise = hash01(seed + i * 0x9E3779B97F4A7C15L + (long) (frame * 13) * 0x632BE59BD9B4E019L),
                    edge = erode * (.34F + .66F * ((middle - start) / span));
            if (noise < edge * .72F) continue;
            Vec3 p0 = curve.point(a), p1 = curve.point(b);
            Vec3 side = normal == null ? sideToCamera(p0, p1, camera) : sideInPlane(p0, p1, normal);
            if (side == null) continue;
            // 两端收细（可见弧段的两端），中间最宽
            float v0 = (a - start) / span, v1 = (b - start) / span;
            float w0 = width * taper(v0), w1 = width * taper(v1);
            band(m, out, p0, p1, side, w0, w1, alpha * (1 - edge * .46F));
        }
    }

    /** 可见弧段两端的收细包络（0/1 处细、中间满） */
    private static float taper(float v) {
        float e = (float) Math.pow(Math.max(0, Math.sin(Math.PI * Mth.clamp(v, 0, 1))), .55);
        return .22F + .78F * e;
    }

    /** 横向 7 列渐变的带子：外缘(alpha 0, {@link #EDGE}) → 芯({@link #CORE}, 满 alpha) → 外缘(透明) */
    private static void band(Matrix4f m, VertexConsumer out, Vec3 a, Vec3 b, Vec3 side,
                             float wa, float wb, float alpha) {
        if (alpha <= .003F) return;
        for (int k = 0; k + 1 < COL_F.length; k++) {
            float f0 = COL_F[k] * .5F, f1 = COL_F[k + 1] * .5F;
            float[] c0 = COL_RGB[k], c1 = COL_RGB[k + 1];
            float a0 = alpha * COL_A[k], a1 = alpha * COL_A[k + 1];
            Vec3 p0 = a.add(side.scale(wa * f0)), p1 = b.add(side.scale(wb * f0));
            Vec3 p2 = b.add(side.scale(wb * f1)), p3 = a.add(side.scale(wa * f1));
            vv(m, out, p0, c0, a0); vv(m, out, p1, c0, a0); vv(m, out, p2, c1, a1);
            vv(m, out, p0, c0, a0); vv(m, out, p2, c1, a1); vv(m, out, p3, c1, a1);
        }
    }

    /** 朝相机的侧向（带子永远正对观察者） */
    private static Vec3 sideToCamera(Vec3 a, Vec3 b, Vec3 camera) {
        Vec3 direction = b.subtract(a);
        if (direction.lengthSqr() < 1E-10 || camera == null) return null;
        Vec3 side = direction.cross(camera.subtract(a.add(b).scale(.5F)));
        if (side.lengthSqr() < 1E-10) side = direction.cross(UP);
        if (side.lengthSqr() < 1E-10) side = direction.cross(new Vec3(1, 0, 0));
        return side.lengthSqr() < 1E-10 ? null : side.normalize();
    }

    /** 落在给定平面内的侧向（环带用：侧向 = 平面法线 × 切向 ⇒ 带宽沿半径方向） */
    private static Vec3 sideInPlane(Vec3 a, Vec3 b, Vec3 normal) {
        Vec3 direction = b.subtract(a), side = normal.cross(direction);
        if (direction.lengthSqr() < 1E-10 || side.lengthSqr() < 1E-10) return null;
        return side.normalize();
    }

    private static void vv(Matrix4f m, VertexConsumer out, Vec3 p, float[] c, float alpha) {
        out.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color(Mth.clamp(c[0], 0, 1), Mth.clamp(c[1], 0, 1), Mth.clamp(c[2], 0, 1), Mth.clamp(alpha, 0, 1))
                .endVertex();
    }

    static float hash01(long value) {
        long x = value; x ^= x >>> 33; x *= 0xff51afd7ed558ccdL; x ^= x >>> 33; x *= 0xc4ceb9fe1a85ec53L; x ^= x >>> 33;
        return (x >>> 40) / (float) (1L << 24);
    }
}
