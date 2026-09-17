package com.example.skydeityslash.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 「翾风回雪」白色圆环几何层 —— 移植自鸣雷神 RING_BACK 三段轨道弧环 + groundField 旋转 ribbon 环，
 * 配色改为纯白（内亮白芯 + 极淡外晕，符合「亮中心、四周极淡」），环绕锁定目标/落点旋转。
 * 原生 POSITION_COLOR 管线、无贴图、加法混合（使用 GlowGeometry.GLOW）。
 */
final class OdetteRingGeometry {
    interface Curve { Vec3 point(float u); }

    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final float TAU = (float) Math.PI * 2;
    private static final int WHITE = 0xFFFFFF;

    // RING_BACK 三段轨道弧环：{start冲击开始, impact, release, end, maxOpacity}（smoother 平滑）
    private static final float[][] RING_BACK = {
            {5.5F, 8.5F, 20F, 27F, .34F},
            {6.8F, 9.8F, 21.5F, 28.5F, .30F},
            {8.2F, 11.2F, 23F, 30F, .26F},
    };
    // 各段轨道参数：{start度, end度, rx, rz, sy, ey, hb, bulge, twist}
    private static final float[][] RING_BACK_ORBIT = {
            {120F, 260F, 3.28F, 2.12F, .20F, .02F, .10F, .025F, .040F},
            {-66F, 76F, 3.12F, 2.02F, .34F, .24F, .08F, .020F, -.035F},
            {100F, 240F, 3.42F, 2.20F, -.08F, .14F, .07F, .018F, .030F},
    };
    // 各段轨道宽度
    private static final float[] RING_BACK_W = {.070F, .065F, .060F};

    private OdetteRingGeometry() {}

    static final class Basis { final Vec3 right, forward; Basis(Vec3 r, Vec3 f) { right = r; forward = f; } }

    static Basis basis(Vec3 direction) {
        Vec3 f = new Vec3(direction.x, 0, direction.z);
        if (f.lengthSqr() < 1E-8) f = new Vec3(0, 0, 1); else f = f.normalize();
        return new Basis(UP.cross(f).normalize(), f);
    }

    /** 主入口：以 center 为圆心（相对——实体自身即为落点），q 为朝向基，绘制白色动态圆环。 */
    static void renderRings(Matrix4f m, VertexConsumer out, float frame, Vec3 center, Basis q, Vec3 camera, long seed) {
        // 三段 RING_BACK 轨道弧环（内亮白芯 + 极淡外晕）
        for (int i = 0; i < 3; i++) {
            drawTrack(m, out, frame, center, q, camera, seed + 1151 + i * 41,
                    RING_BACK[i], RING_BACK_ORBIT[i], RING_BACK_W[i]);
        }
        // 旋转 ribbon 环（4 层同心椭圆，缓慢自旋，环绕于落点上方）
        drawFloatingRings(m, out, frame, center, q, camera, seed + 2600);
    }

    // ---------------- 轨道弧环 ----------------
    private static void drawTrack(Matrix4f m, VertexConsumer out, float f, Vec3 center, Basis q, Vec3 camera,
                                  long seed, float[] cue, float[] orbit, float width) {
        float a = envelope(cue, f);
        if (a <= .001F) return;
        Curve curve = orbit(center, q, orbit);
        float head = reveal(cue, f), tail = exit(cue, f), er = .25F + smooth(f, cue[2], cue[3]) * .50F;
        // 极淡外晕（宽而透）
        ribbon(m, out, curve, 56, head, tail, width, camera, WHITE, a * .16F, er, seed, f);
        // 内亮白芯（细而实）
        ribbon(m, out, curve, 56, head, tail, width * .30F, camera, WHITE, a, er * .5F, seed + 7, f);
    }

    // ---------------- 旋转 ribbon 环 ----------------
    private static void drawFloatingRings(Matrix4f m, VertexConsumer out, float f, Vec3 center, Basis q, Vec3 camera,
                                          long seed) {
        float open = smoother(stage(f, 2.5F, 9F));
        float fade = 1 - smoother(stage(f, 27F, LIFETIME - 27F));
        float a = open * fade;
        if (a <= .001F) return;
        // 轻微脉动
        float pulse = .5F + .5F * Mth.sin(f * .35F);
        Vec3 c = center.add(0, 1.1, 0); // 环绕于落点上方（空中），贴合「在空气中动态会动」
        for (int i = 0; i < 4; i++) {
            final int ii = i;
            final float radius = 1.55F + i * .78F + pulse * (.10F + i * .04F);
            float base = f * .06F + ii * .37F; // 缓慢自旋，形成「动态会动」
            Curve ring = u -> c.add(q.right.scale(Math.cos(u * TAU + base) * radius))
                    .add(q.forward.scale(Math.sin(u * TAU + base) * radius * .76));
            float ringA = a * (.26F - i * .035F + pulse * .18F);
            // 极淡外晕
            ribbonFixed(m, out, ring, 56, .94F, .05F + i * .09F, .010F + i * .0024F, UP, WHITE, ringA * .18F, .24F, seed + i * 31, f);
            // 内亮白芯
            ribbonFixed(m, out, ring, 56, .94F, .05F + i * .09F, .006F + i * .0016F, UP, WHITE, ringA, .24F, seed + i * 31 + 9, f);
        }
    }

    // ---------------- 时间轴 ----------------
    private static final int LIFETIME = 36;
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

    // ---------------- ribbon 图元（相机感知 / 固定平面） ----------------
    private static void ribbon(Matrix4f m, VertexConsumer out, Curve curve, int segments, float head, float tail,
                               float width, Vec3 camera, int color, float alpha, float erode, long seed, float frame) {
        float start = Mth.clamp(tail, 0, 1), end = Mth.clamp(head, 0, 1);
        if (alpha <= .001F || end <= start + 1E-4F) return;
        int count = Math.max(2, segments);
        for (int i = 0; i < count; i++) {
            float u0 = i / (float) count, u1 = (i + 1) / (float) count;
            if (u1 < start || u0 > end) continue;
            float a = Math.max(start, u0), b = Math.min(end, u1), middle = (a + b) * .5F;
            float noise = hash01(seed + i * 0x9E3779B97F4A7C15L + (long) (frame * 13) * 0x632BE59BD9B4E019L),
                    edge = erode * (.34F + .66F * middle);
            if (noise < edge * .72F) continue;
            Vec3 p0 = curve.point(a), p1 = curve.point(b);
            float t0 = (float) Math.pow(Math.max(0, Math.sin(Math.PI * a)), .72),
                    t1 = (float) Math.pow(Math.max(0, Math.sin(Math.PI * b)), .72);
            beamQuad(m, out, p0, p1, width * (.12F + .88F * t0), width * (.12F + .88F * t1), camera, color, alpha * (1 - edge * .46F));
        }
    }
    private static void ribbonFixed(Matrix4f m, VertexConsumer out, Curve curve, int segments, float head, float tail,
                                    float width, Vec3 normal, int color, float alpha, float erode, long seed, float frame) {
        float start = Mth.clamp(tail, 0, 1), end = Mth.clamp(head, 0, 1);
        if (alpha <= .001F || end <= start + 1E-4F) return;
        int count = Math.max(2, segments);
        for (int i = 0; i < count; i++) {
            float u0 = i / (float) count, u1 = (i + 1) / (float) count;
            if (u1 < start || u0 > end) continue;
            float a = Math.max(start, u0), b = Math.min(end, u1), middle = (a + b) * .5F;
            float noise = hash01(seed + i * 0x9E3779B97F4A7C15L + (long) (frame * 13) * 0x632BE59BD9B4E019L),
                    edge = erode * (.34F + .66F * middle);
            if (noise < edge * .72F) continue;
            fixedBeamQuad(m, out, curve.point(a), curve.point(b), width * (.12F + .88F * endTaper(a)),
                    width * (.12F + .88F * endTaper(b)), normal, color, alpha * (1 - edge * .46F));
        }
    }
    private static void beamQuad(Matrix4f m, VertexConsumer out, Vec3 start, Vec3 end, float sw, float ew,
                                 Vec3 camera, int color, float alpha) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1E-10 || alpha <= .001F) return;
        Vec3 side = direction.cross(camera.subtract(start.add(end).scale(.5)));
        if (side.lengthSqr() < 1E-10) side = direction.cross(UP);
        if (side.lengthSqr() < 1E-10) side = direction.cross(new Vec3(1, 0, 0));
        side = side.normalize();
        Vec3 s0 = side.scale(sw * .50F), s1 = side.scale(ew * .50F);
        quad(m, out, start.subtract(s0), start.add(s0), end.add(s1), end.subtract(s1), color, alpha);
    }
    private static void fixedBeamQuad(Matrix4f m, VertexConsumer out, Vec3 start, Vec3 end, float sw, float ew,
                                      Vec3 normal, int color, float alpha) {
        Vec3 direction = end.subtract(start), side = normal.cross(direction);
        if (direction.lengthSqr() < 1E-10 || side.lengthSqr() < 1E-10 || alpha <= .001F) return;
        side = side.normalize();
        Vec3 s0 = side.scale(sw * .50F), s1 = side.scale(ew * .50F);
        quad(m, out, start.subtract(s0), start.add(s0), end.add(s1), end.subtract(s1), color, alpha);
    }
    private static void quad(Matrix4f m, VertexConsumer out, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, float alpha) {
        vv(m, out, a, color, alpha); vv(m, out, b, color, alpha); vv(m, out, c, color, alpha);
        vv(m, out, a, color, alpha); vv(m, out, c, color, alpha); vv(m, out, d, color, alpha);
    }
    private static void vv(Matrix4f m, VertexConsumer out, Vec3 p, int color, float alpha) {
        out.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F,
                        Mth.clamp(alpha, 0, 1))
                .endVertex();
    }
    private static float endTaper(float u) {
        float e = Math.min(Mth.clamp(u / .075F, 0, 1), Mth.clamp((1 - u) / .075F, 0, 1));
        return e * e * (3 - 2 * e);
    }
    static float hash01(long value) {
        long x = value; x ^= x >>> 33; x *= 0xff51afd7ed558ccdL; x ^= x >>> 33; x *= 0xc4ceb9fe1a85ec53L; x ^= x >>> 33;
        return (x >>> 40) / (float) (1L << 24);
    }
}