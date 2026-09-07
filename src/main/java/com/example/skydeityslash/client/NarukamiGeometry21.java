package com.example.skydeityslash.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 鸣雷神 几何层 忠实移植（来自 1.20KaBlade NarukamiGeometry）。
 * 原生管线、无贴图：相机感知 ribbon / 圆盘 / 星爆 / 确定性闪电，int 0xRRGGBB 取色 + alpha。
 */
final class NarukamiGeometry21 {
    interface Curve { Vec3 point(float u); }
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final float TAU = (float) Math.PI * 2;
    private NarukamiGeometry21(){}

    private static void vv(Matrix4f m, VertexConsumer out, Vec3 p, int color, float alpha) {
        out.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F,
                        Mth.clamp(alpha, 0, 1))
                .endVertex();
    }
    private static void quad(Matrix4f m, VertexConsumer out, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, float alpha) {
        vv(m, out, a, color, alpha); vv(m, out, b, color, alpha); vv(m, out, c, color, alpha);
        vv(m, out, a, color, alpha); vv(m, out, c, color, alpha); vv(m, out, d, color, alpha);
    }

    static void ribbon(Matrix4f m, VertexConsumer out, Curve curve, int segments, float head, float tail,
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

    static void ribbonFixed(Matrix4f m, VertexConsumer out, Curve curve, int segments, float head, float tail,
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

    static void beam(Matrix4f m, VertexConsumer out, Vec3 start, Vec3 end, float width, Vec3 camera, int color, float alpha) {
        beamQuad(m, out, start, end, width, width, camera, color, alpha);
    }

    static void lightningLayer(Matrix4f m, VertexConsumer out, boolean coreLayer, Vec3 start, Vec3 end, int segments,
                               float jitter, float width, Vec3 camera, int outerColor, int coreColor, float alpha, long seed) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1E-8 || alpha <= .001F) return;
        Vec3 dir = direction.normalize(), reference = Math.abs(dir.y) < .86 ? UP : new Vec3(1, 0, 0),
                sideA = dir.cross(reference).normalize(), sideB = dir.cross(sideA).normalize(), previous = start;
        int count = Math.max(3, segments);
        for (int i = 1; i <= count; i++) {
            float u = i / (float) count, envelope = Mth.sin((float) Math.PI * u);
            double a = (hash01(seed + i * 31L) - .5) * 2 * jitter * envelope,
                    b = (hash01(seed + i * 47L + 19) - .5) * jitter * envelope;
            Vec3 point = start.add(direction.scale(u)).add(sideA.scale(a)).add(sideB.scale(b));
            float flicker = .66F + hash01(seed + i * 73L) * .34F;
            // 芯/晕双层：芯=近乎纯白白热·更细，晕=淡紫·更宽但更低透明度，呈"内亮外暗透"梯度
            float w, a2; int col;
            if (coreLayer) {
                w = width * .42F;                          // 纤细白热亮芯
                col = washWhite(coreColor, .60F);          // 强拉白 → 白热
                a2 = alpha * 1.0F * flicker;
            } else {
                w = width * 1.28F;                         // 宽晕（相对稍窄，配更细芯）
                col = washWhite(outerColor, .42F);         // 淡紫晕
                a2 = alpha * .34F * flicker;               // 更低透明度 → 暗而透
            }
            beam(m, out, previous, point, w, camera, col, a2);
            previous = point;
        }
    }

    /** 把颜色向纯白方向提亮 k∈[0,1]，用于闪电亮芯的 HDR 感。 */
    static int washWhite(int color, float k) {
        if (k <= 0) return color;
        float r = (color >> 16 & 255) / 255F, g = (color >> 8 & 255) / 255F, b = (color & 255) / 255F;
        r += (1 - r) * k; g += (1 - g) * k; b += (1 - b) * k;
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    static void billboard(Matrix4f m, VertexConsumer out, Vec3 center, Vec3 camera, float halfWidth, float halfHeight,
                          float rotation, int color, float alpha) {
        if (alpha <= .001F) return;
        Axes axes = cameraAxes(center, camera, rotation);
        Vec3 x = axes.x.scale(halfWidth), y = axes.y.scale(halfHeight);
        quad(m, out, center.subtract(x).subtract(y), center.add(x).subtract(y),
                center.add(x).add(y), center.subtract(x).add(y), color, alpha);
    }
    static void discBillboard(Matrix4f m, VertexConsumer out, Vec3 center, Vec3 camera, float halfWidth, float halfHeight,
                              float rotation, int color, float alpha) {
        if (alpha <= .001F) return;
        Axes axes = cameraAxes(center, camera, rotation);
        disc(m, out, center, axes.x, axes.y, halfWidth, halfHeight, color, alpha, 24);
    }
    static void discOriented(Matrix4f m, VertexConsumer out, Vec3 center, Vec3 axisX, Vec3 axisY,
                             float halfWidth, float halfHeight, int color, float alpha) {
        if (alpha <= .001F || axisX.lengthSqr() < 1E-8 || axisY.lengthSqr() < 1E-8) return;
        disc(m, out, center, axisX.normalize(), axisY.normalize(), halfWidth, halfHeight, color, alpha, 32);
    }
    private static void disc(Matrix4f m, VertexConsumer out, Vec3 center, Vec3 x, Vec3 y,
                             float hw, float hh, int color, float alpha, int segments) {
        for (int i = 0; i < segments; i++) {
            double a0 = i * TAU / segments, a1 = (i + 1) * TAU / segments;
            Vec3 e0 = center.add(x.scale(Math.cos(a0) * hw)).add(y.scale(Math.sin(a0) * hh)),
                    e1 = center.add(x.scale(Math.cos(a1) * hw)).add(y.scale(Math.sin(a1) * hh));
            vv(m, out, center, color, alpha); vv(m, out, e0, color, alpha); vv(m, out, e1, color, alpha);
        }
    }
    static void starBurst(Matrix4f m, VertexConsumer out, Vec3 center, Vec3 camera, int rays, float minLength, float maxLength,
                          float width, int color, float alpha, long seed, float rotation) {
        Axes axes = cameraAxes(center, camera, 0);
        for (int i = 0; i < rays; i++) {
            double angle = rotation + i * TAU / (double) rays + (hash01(seed + i * 17L) - .5) * .18;
            float length = minLength + (maxLength - minLength) * hash01(seed + i * 29L);
            Vec3 d = axes.x.scale(Math.cos(angle)).add(axes.y.scale(Math.sin(angle)));
            beam(m, out, center.add(d.scale(.08)), center.add(d.scale(length)), width, camera, color,
                    alpha * (.62F + hash01(seed + i * 43L) * .38F));
        }
    }

    static float hash01(long value) {
        long x = value; x ^= x >>> 33; x *= 0xff51afd7ed558ccdL; x ^= x >>> 33; x *= 0xc4ceb9fe1a85ec53L; x ^= x >>> 33;
        return (x >>> 40) / (float) (1L << 24);
    }
    private static Axes cameraAxes(Vec3 center, Vec3 camera, float rotation) {
        Vec3 view = camera.subtract(center);
        if (view.lengthSqr() < 1E-8) view = new Vec3(0, 0, 1); else view = view.normalize();
        Vec3 right = UP.cross(view);
        if (right.lengthSqr() < 1E-8) right = new Vec3(1, 0, 0); else right = right.normalize();
        Vec3 up = view.cross(right).normalize();
        double c = Math.cos(rotation), s = Math.sin(rotation);
        return new Axes(right.scale(c).add(up.scale(s)), up.scale(c).subtract(right.scale(s)));
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
    private static float endTaper(float u) {
        float e = Math.min(Mth.clamp(u / .075F, 0, 1), Mth.clamp((1 - u) / .075F, 0, 1));
        return e * e * (3 - 2 * e);
    }
    private static final class Axes { final Vec3 x, y; Axes(Vec3 x, Vec3 y) { this.x = x; this.y = y; } }
}