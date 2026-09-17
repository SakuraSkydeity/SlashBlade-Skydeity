package com.example.skydeityslash.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 1.21.1 版特效几何库 —— 剑体始觉·LimpidityGeometry 忠实移植。
 * 逐行对齐参考 1.20KaBlade 的 LimpidityGeometry：
 *   - 金色/淡紫配色(GOLD 0.95,0.86,0.63 / LAV 0.74,0.76,1.00)
 *   - 四通道(BASE_COLOR / BASE_GLOW / UNITY_COLOR / UNITY_GLOW)
 *   - 细节线条/光影：彩色外带 + 内圈亮白核，加法混合、无剔除、无深度写。
 * 原版 SWORD/CONCEPTUAL 材质无贴图，颜色完全由顶点色决定，故可无损用 POSITION_COLOR 重建。
 */
public final class GlowGeometry {
    private GlowGeometry() {}

    public enum Pass { BASE_COLOR, BASE_GLOW, UNITY_COLOR, UNITY_GLOW }

    private static final float TAU = (float) Math.PI * 2F, DEG = (float) Math.PI / 180F, CENTER_Z = 2.15F;
    private static final int RING_SEGMENTS = 92, LOOP_SEGMENTS = 58, SWEEP_SEGMENTS = 54, SHARD_COUNT = 58;
    private static final float GOLD_R = .95F, GOLD_G = .86F, GOLD_B = .63F, LAV_R = .74F, LAV_G = .76F, LAV_B = 1F;

    private static RenderType rtype(String name, RenderStateShard.TransparencyStateShard transp) {
        return RState.composite(
                "skydeityslash:" + name,
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.TRIANGLES,
                0x8000, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RState.SHADER_POSITION_COLOR)
                        .setTransparencyState(transp)
                        .setCullState(RState.CULL_NONE)
                        .setWriteMaskState(RState.WRITE_COLOR)
                        .setOutputState(RState.OUTPUT_MAIN)
                        .createCompositeState(false));
    }
    public static final RenderType GLOW = rtype("glow", RState.TRANSPARENCY_ADDITIVE);
    public static final RenderType UNITY = rtype("unity", RState.TRANSPARENCY_ADDITIVE);

    // ---- 顶点/四边面（拆两个三角形） ----
    private static void vv(Matrix4f m, VertexConsumer vc, Vec3 p, float r, float g, float b, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color((int) (Mth.clamp(r, 0, 1) * 255), (int) (Mth.clamp(g, 0, 1) * 255),
                        (int) (Mth.clamp(b, 0, 1) * 255), (int) (Mth.clamp(a, 0, 1) * 255))
                .endVertex();
    }
    /** 均匀透明四边面 */
    private static void quadQ(Matrix4f m, VertexConsumer vc, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                              float r, float g, float b, float a) {
        vv(m, vc, p0, r, g, b, a); vv(m, vc, p1, r, g, b, a); vv(m, vc, p2, r, g, b, a);
        vv(m, vc, p0, r, g, b, a); vv(m, vc, p2, r, g, b, a); vv(m, vc, p3, r, g, b, a);
    }
    /** 逐顶点透明四边面 */
    private static void quadA(Matrix4f m, VertexConsumer vc, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                              float r, float g, float b, float a0, float a1, float a2, float a3) {
        vv(m, vc, p0, r, g, b, a0); vv(m, vc, p1, r, g, b, a1); vv(m, vc, p2, r, g, b, a2);
        vv(m, vc, p0, r, g, b, a0); vv(m, vc, p2, r, g, b, a2); vv(m, vc, p3, r, g, b, a3);
    }

    // ==================================================================
    // ---------- 剑体始觉 · LimpidityGeometry 四通道 ----------
    // ==================================================================
    public static void draw(Matrix4f m, VertexConsumer vc, float age,
                            float viewYaw, float viewPitch, float entityYaw, Pass pass) {
        if (pass == Pass.BASE_COLOR) {
            groundRings(m, vc, age); haloShell(m, vc, age); slashTimeline(m, vc, age);
            verticalArcs(m, vc, age); fragments(m, vc, age);
        } else if (pass == Pass.BASE_GLOW) {
            slashTimeline(m, vc, age); verticalArcs(m, vc, age);
            glowBall(m, vc, age, viewYaw, viewPitch);
        } else {
            if (pass == Pass.UNITY_COLOR) mandala(m, vc, age);
            wingSigils(m, vc, age); unityFinisher(m, vc, age);
        }
    }

    // ---------------- 单子特效预览 ----------------
    /** 只渲染某一个子特效，含其所在通道的叠加（等价完整效果中该部分），全部入同一加成缓冲。 */
    public static void renderSubEffect(Matrix4f m, VertexConsumer vc, int sub, float age, float viewYaw, float viewPitch) {
        switch (sub) {
            case 0: groundRings(m, vc, age); break;                                    // 地基碎环
            case 1: haloShell(m, vc, age); break;                                      // 高悬环壳
            case 2: slashTimeline(m, vc, age); slashTimeline(m, vc, age); break;       // 新月弧斩(色彩+发光)
            case 3: verticalArcs(m, vc, age); verticalArcs(m, vc, age); break;         // 竖弧(色彩+发光)
            case 4: glowBall(m, vc, age, viewYaw, viewPitch); break;                   // 白色光球
            case 5: fragments(m, vc, age); break;                                      // 碎片
            case 6: mandala(m, vc, age); break;                                        // 旋转碎环法阵
            case 7: wingSigils(m, vc, age); wingSigils(m, vc, age); break;             // 翼铭文(色彩+发光)
            default: unityFinisher(m, vc, age); unityFinisher(m, vc, age); break;      // 合击金光柱/冲击环
        }
    }

    // ---------------- BASE_COLOR ----------------
    private static void groundRings(Matrix4f m, VertexConsumer vc, float age) {
        float open = smoother(stage(age, 5, 10)), fade = 1 - smoother(stage(age, 38, 16)), a = open * fade;
        if (a <= .01F) return;
        float pulse = .5F + .5F * Mth.sin(age * .38F);
        brokenRing(m, vc, .055F, lerp(open, .64F, 4.95F), lerp(open, .008F, .035F), .20F + open * .52F, .76F, .28F, 1, a * .44F);
        ring(m, vc, .075F, 1.35F + open * 5.05F + pulse * .10F, .008F + open * .025F, .94F, .82F, 1, a * .28F);
        for (int i = 0; i < 5; i++) {
            float start = 6 + i * 3.2F, t = stage(age, start, 18);
            if (t >= 1) continue;
            float grow = fastOut(t), local = (1 - smoother(stage(age, start + 8, 10))) * fade;
            brokenRing(m, vc, .10F + i * .012F, .55F + grow * (4.9F + i * .55F), .01F + grow * (.03F + i * .003F),
                    grow, i % 2 == 0 ? .92F : .56F, i % 2 == 0 ? .72F : .24F, 1, local * (.26F - i * .025F));
        }
    }
    private static void haloShell(Matrix4f m, VertexConsumer vc, float age) {
        float appear = smoother(stage(age, 7, 3)), fade = 1 - smoother(stage(age, 36, 10)), a = appear * fade;
        if (a <= .01F) return;
        for (int i = 0; i < 11; i++) {
            float localOpen = smoother(stage(age, 7.4F + i * .55F, 8)),
                    localFade = 1 - smoother(stage(age, 25 + i * .75F, 12)),
                    local = a * localOpen * localFade;
            if (local <= .01F) continue;
            float rx = 2.05F + i * .22F + localOpen * 1.25F, rz = 1.55F + i * .18F + localOpen * .88F,
                    y = .82F + (i % 4) * .20F, lift = .48F + localOpen * (1.05F + (i % 3) * .20F);
            liftedLoop(m, vc, age * (.11F + i * .008F) + i * .713F, (.58F + localOpen * .66F) * TAU,
                    rx, rz, y, lift, .02F + i * .003F,
                    i % 3 == 0 ? .98F : .58F, i % 3 == 0 ? .86F : .28F, 1, local * (.20F + (i % 3) * .035F));
        }
    }
    private static void slashTimeline(Matrix4f m, VertexConsumer vc, float age) {
        crescent(m, vc, age, 6.4F, 5.6F, 4.65F, 116, -58, .18F, 2.12F, .15F, .10F, .94F, .78F, 1, 1);
        crescent(m, vc, age, 9, 6.8F, 5.15F, 158, -26, .42F, 2.62F, .45F, .065F, .68F, .34F, 1, .84F);
        crescent(m, vc, age, 13.2F, 8, 5.90F, 210, 18, .22F, 2.92F, .82F, .055F, .92F, .82F, 1, .74F);
        crescent(m, vc, age, 17.2F, 9, 6.45F, 255, 52, .18F, 3.34F, 1.20F, .05F, .58F, .30F, 1, .72F);
        crescent(m, vc, age, 21.6F, 10, 6.85F, 318, 112, .34F, 3.76F, 1.62F, .04F, .96F, .86F, 1, .55F);
    }
    private static void verticalArcs(Matrix4f m, VertexConsumer vc, float age) {
        vArc(m, vc, age, 8.2F, 4.8F, -.25F, 1.62F, 2.45F, 5.45F, 2.05F, .72F, 206, 344, .09F, .66F, .24F, 1, .88F);
        vArc(m, vc, age, 10.6F, 5.8F, .32F, 1.78F, 2.75F, 4.85F, 2.55F, -.56F, -24, 174, .065F, .82F, .42F, 1, .68F);
        vArc(m, vc, age, 14.4F, 6.5F, -.46F, 1.96F, 3, 5.9F, 2.9F, .48F, 222, 396, .06F, .58F, .20F, 1, .70F);
        vArc(m, vc, age, 17.8F, 6.8F, .50F, 2.06F, 3.20F, 5.15F, 2.70F, -.66F, -40, 152, .052F, .74F, .34F, 1, .62F);
        vArc(m, vc, age, 21.5F, 7.2F, -.18F, 2.22F, 3.45F, 6.35F, 3.10F, .82F, 198, 358, .045F, .92F, .62F, 1, .54F);
        vArc(m, vc, age, 27, 7, .20F, 2.26F, 3.18F, 5.45F, 2.80F, -.44F, 18, 188, .038F, .62F, .28F, 1, .42F);
    }
    private static void glowBall(Matrix4f m, VertexConsumer vc, float age, float viewYaw, float viewPitch) {
        float appear = smoother(stage(age, 7, 4)), fade = 1 - smoother(stage(age, 36, 10)), a = appear * fade;
        if (a <= .01F) return;
        float radius = 1.05F + .45F * smoother(stage(age, 7, 8)) * (1 - smoother(stage(age, 34, 6)));
        float delta = viewYaw * DEG, pitch = viewPitch * DEG;
        float sy = Mth.sin(delta), cyw = Mth.cos(delta), sp = Mth.sin(pitch), cp = Mth.cos(pitch);
        Vec3 right = new Vec3(-cyw, 0, -sy), up = new Vec3(-sy * sp, cp, cyw * sp), o = new Vec3(0, 1.05F, CENTER_Z);
        int seg = 30;
        for (int i = 0; i < seg; i++) {
            float t0 = i * TAU / seg, t1 = (i + 1) * TAU / seg;
            Vec3 r0 = right.scale(Mth.cos(t0)).add(up.scale(Mth.sin(t0))).scale(radius);
            Vec3 r1 = right.scale(Mth.cos(t1)).add(up.scale(Mth.sin(t1))).scale(radius);
            vv(m, vc, o, 1F, 1F, 1F, a * .92F);
            vv(m, vc, o.add(r0), .60F, .58F, .80F, a * .16F);
            vv(m, vc, o.add(r1), .60F, .58F, .80F, a * .16F);
        }
    }
    private static void fragments(Matrix4f m, VertexConsumer vc, float age) {
        float global = 1 - smoother(stage(age, 48, 9));
        if (global <= .01F) return;
        for (int i = 0; i < SHARD_COUNT; i++) {
            float start = 7 + det(i, 2.2F) * 27, raw = stage(age, start, 20 + det(i, 3.1F) * 6);
            if (raw <= 0 || raw >= 1) continue;
            float launch = fastOut(Mth.clamp(raw / .42F, 0, 1)), fade = (1 - smoother(raw)) * global,
                    angle = det(i, 4) * TAU + age * .018F;
            float radius = .72F + det(i, 5) * 4.70F + launch * (.35F + det(i, 6) * 1.45F),
                    x = Mth.cos(angle) * radius, z = .45F + Mth.sin(angle) * radius * .68F + det(i, 7) * 2.4F;
            float y = .35F + det(i, 8) * 2.75F + launch * (.35F + det(i, 9) * 1.35F),
                    size = .02F + det(i, 10) * .055F, alpha = fade * (.26F + det(i, 11) * .44F);
            diamond(m, vc, x, y, z, size, age * .16F + i * .91F,
                    i % 3 == 0 ? .96F : .62F, i % 3 == 0 ? .82F : .34F, 1, alpha);
        }
    }

    // ---------------- UNITY_COLOR ----------------
    private static void mandala(Matrix4f m, VertexConsumer vc, float age) {
        float open = smoother(stage(age, 7, 8)), fade = 1 - smoother(stage(age, 39, 12)), a = open * fade;
        if (a <= .01F) return;
        float rot = age * .022F;
        segmentedRing(m, vc, .09F, CENTER_Z, 3.65F + open * .22F, .028F, rot, 6, GOLD_R, GOLD_G, GOLD_B, a * .50F);
        segmentedRing(m, vc, .115F, CENTER_Z, 4.42F, .018F, -rot * 1.35F, 9, LAV_R, LAV_G, LAV_B, a * .34F);
        for (int i = 0; i < 3; i++) {
            float a0 = rot + i * TAU / 3, a1 = rot + (i + 1) * TAU / 3;
            hRibbon(m, vc, Mth.cos(a0) * 3.28F, .13F, CENTER_Z + Mth.sin(a0) * 3.28F,
                    Mth.cos(a1) * 3.28F, .13F, CENTER_Z + Mth.sin(a1) * 3.28F, .014F, GOLD_R, GOLD_G, GOLD_B, a * .28F, 4);
        }
    }
    private static void wingSigils(Matrix4f m, VertexConsumer vc, float age) {
        float appear = smoother(stage(age, 7, 4)), disappear = 1 - smoother(stage(age, 36, 7)), a = appear * disappear;
        if (a <= .01F) return;
        float gather = smoother(stage(age, 30, 6)), radius = lerp(gather, 4.25F, .42F), spin = age * (.24F + gather * .20F),
                scale = .86F + appear * .26F + gather * .18F;
        for (int i = 0; i < 3; i++) {
            float angle = spin + i * TAU / 3, x = Mth.cos(angle) * radius, z = CENTER_Z + Mth.sin(angle) * radius,
                    y = 1.05F + Mth.sin(angle * 2) * .22F;
            wing(m, vc, x, y, z, angle + (float) Math.PI / 2, scale, a * (.72F + gather * .28F));
            float trail = angle - .34F - gather * .18F;
            hRibbon(m, vc, Mth.cos(trail) * radius, y - .02F, CENTER_Z + Mth.sin(trail) * radius, x, y, z,
                .035F + gather * .015F, LAV_R, LAV_G, LAV_B, a * .52F, 2);
        }
    }
    private static void unityFinisher(Matrix4f m, VertexConsumer vc, float age) {
        float charge = smoother(stage(age, 32.5F, 3.5F)), fade = 1 - smoother(stage(age, 36, 9)), a = charge * fade;
        if (a <= .01F) return;
        float half = 5.15F * charge;
        for (int i = 0; i < 3; i++) {
            float angle = i * TAU / 3 + .12F, fx = Mth.cos(angle), fz = Mth.sin(angle);
            hRibbon(m, vc, -fx * half, 1.12F, CENTER_Z - fz * half, fx * half, 1.12F, CENTER_Z + fz * half,
                .03F + charge * .02F, GOLD_R, GOLD_G, GOLD_B, a * .82F, 0);
            hRibbon(m, vc, -fx * half, 1.16F, CENTER_Z - fz * half, fx * half, 1.16F, CENTER_Z + fz * half,
                .025F, 1, .98F, .90F, a, 2);
        }
        float shock = smoother(stage(age, 35.5F, 6.5F)), shockFade = 1 - smoother(stage(age, 40, 7));
        segmentedRing(m, vc, .16F, CENTER_Z, lerp(shock, .38F, 6.15F), lerp(shock, .15F, .026F), age * .015F, 12,
                GOLD_R, GOLD_G, GOLD_B, shockFade * (1 - shock * .52F));
    }

    // ---------------- 图元 ----------------
    private static void crescent(Matrix4f m, VertexConsumer vc, float age, float start, float duration, float radius,
                                 float startDeg, float endDeg, float y0, float y1, float zOff, float width,
                                 float r, float g, float bl, float alphaScale) {
        radius *= 1.35F;
        float reveal = smoother(stage(age, start, duration)), fade = 1 - smoother(stage(age, start + duration + 9, 9)),
                a = reveal * fade * alphaScale;
        if (a <= .01F) return;
        int visible = Mth.clamp((int) Math.ceil(SWEEP_SEGMENTS * reveal), 2, SWEEP_SEGMENTS);
        for (int i = 0; i < visible; i++) {
            float t0 = i / (float) SWEEP_SEGMENTS, t1 = Math.min((i + 1) / (float) SWEEP_SEGMENTS, reveal);
            if (t1 <= t0) continue;
            Vec3 p0 = sweepPoint(t0, radius, startDeg, endDeg, y0, y1, zOff),
                    p1 = sweepPoint(t1, radius, startDeg, endDeg, y0, y1, zOff);
            float angle0 = (startDeg + (endDeg - startDeg) * t0) * DEG,
                    angle1 = (startDeg + (endDeg - startDeg) * t1) * DEG,
                    w0 = width * sweepWidth(t0), w1 = width * sweepWidth(t1),
                    a0 = a * sweepAlpha(t0, reveal), a1 = a * sweepAlpha(t1, reveal);
            Vec3 s0 = new Vec3(Mth.sin(angle0), 0, Mth.cos(angle0)), s1 = new Vec3(Mth.sin(angle1), 0, Mth.cos(angle1));
            quadA(m, vc, p0.add(s0.scale(w0)), p1.add(s1.scale(w1)), p1.subtract(s1.scale(w1)), p0.subtract(s0.scale(w0)),
                    r, g, bl, a0, a1, a1 * .88F, a0 * .88F);
            quadA(m, vc, p0.add(s0.scale(w0 * .32F)).add(0, .04F, 0), p1.add(s1.scale(w1 * .32F)).add(0, .04F, 0),
                    p1.subtract(s1.scale(w1 * .32F)).add(0, -.04F, 0), p0.subtract(s0.scale(w0 * .32F)).add(0, -.04F, 0),
                    1, .88F, 1, a0 * 1.25F, a1 * 1.25F, a1 * 1.05F, a0 * 1.05F);
        }
    }
    private static Vec3 sweepPoint(float t, float radius, float start, float end, float y0, float y1, float z) {
        float angle = (start + (end - start) * t) * DEG;
        return new Vec3(Mth.sin(angle) * radius, lerp(t, y0, y1) + Mth.sin(t * (float) Math.PI) * .56F, Mth.cos(angle) * radius + z);
    }

    private static void vArc(Matrix4f m, VertexConsumer vc, float age, float start, float duration, float cx, float cy, float cz,
                             float rx, float ry, float bend, float startDeg, float endDeg, float width,
                             float r, float g, float bl, float alphaScale) {
        float reveal = smoother(stage(age, start, duration)), fade = 1 - smoother(stage(age, start + duration + 5, 8)),
                a = reveal * fade * alphaScale;
        if (a <= .01F) return;
        int visible = Mth.clamp((int) Math.ceil(SWEEP_SEGMENTS * reveal), 2, SWEEP_SEGMENTS);
        for (int i = 0; i < visible; i++) {
            float t0 = i / (float) SWEEP_SEGMENTS, t1 = Math.min((i + 1) / (float) SWEEP_SEGMENTS, reveal);
            if (t1 <= t0) continue;
            Arc p0 = arcPoint(t0, cx, cy, cz, rx, ry, bend, startDeg, endDeg),
                    p1 = arcPoint(t1, cx, cy, cz, rx, ry, bend, startDeg, endDeg);
            float w0 = width * vWidth(t0), w1 = width * vWidth(t1), a0 = a * vAlpha(t0, reveal), a1 = a * vAlpha(t1, reveal);
            quadA(m, vc, p0.plus(w0, 0), p1.plus(w1, 0), p1.plus(-w1, 0), p0.plus(-w0, 0),
                    r, g, bl, a0, a1, a1 * .9F, a0 * .9F);
            quadA(m, vc, p0.plus(w0 * .26F, -.018F), p1.plus(w1 * .26F, -.018F), p1.plus(-w1 * .26F, .018F), p0.plus(-w0 * .26F, .018F),
                    1, .84F, 1, a0 * 1.36F, a1 * 1.36F, a1 * 1.14F, a0 * 1.14F);
        }
    }

    private static void blade(Matrix4f m, VertexConsumer vc, float age, float start, float duration,
                              float x0, float y0, float z0, float x1, float y1, float z1, float width,
                              float r, float g, float bl, float alphaScale) {
        float in = smoother(stage(age, start, 1.2F)), out = 1 - smoother(stage(age, start + duration - 2.2F, 2.2F)),
                a = in * out * alphaScale;
        if (a <= .01F) return;
        float jitter = Mth.sin(age * .66F + start) * .05F;
        line(m, vc, new Vec3(x0, y0 + jitter, z0), new Vec3(x1, y1 - jitter, z1), width * (.75F + in * .55F), r, g, bl, a);
    }

    private static void liftedLoop(Matrix4f m, VertexConsumer vc, float phase, float arc, float rx, float rz,
                                   float baseY, float lift, float width, float r, float g, float bl, float a) {
        for (int i = 0; i < LOOP_SEGMENTS; i++) {
            float t0 = i / (float) LOOP_SEGMENTS, t1 = (i + 1) / (float) LOOP_SEGMENTS;
            line(m, vc, loopPoint(phase + (t0 - .5F) * arc, rx, rz, baseY, lift),
                    loopPoint(phase + (t1 - .5F) * arc, rx, rz, baseY, lift), width, r, g, bl, edge(t0) * a);
        }
    }
    private static Vec3 loopPoint(float angle, float rx, float rz, float baseY, float lift) {
        return new Vec3(Mth.cos(angle) * rx, baseY + Mth.sin(angle * .62F + .7F) * lift + Mth.sin(angle * 1.7F) * .16F,
                Mth.sin(angle) * rz + 2);
    }

    private static void ring(Matrix4f m, VertexConsumer vc, float y, float radius, float width, float r, float g, float bl, float a) {
        ringInternal(m, vc, y, radius, width, 0, r, g, bl, a, false);
    }
    private static void brokenRing(Matrix4f m, VertexConsumer vc, float y, float radius, float width, float scatter,
                                   float r, float g, float bl, float a) {
        ringInternal(m, vc, y, radius, width, scatter, r, g, bl, a, true);
    }
    private static void ringInternal(Matrix4f m, VertexConsumer vc, float y, float radius, float width, float scatter,
                                 float r, float g, float bl, float a, boolean broken) {
        float inner = Math.max(.02F, radius - width * .5F), mid = radius, outer = radius + width * .5F;
        int gap = Math.max(2, 8 - (int) Math.floor(scatter * 5));
        // 径向渐变：中心亮、内/外边缘暗且透明 → 圆润、融合
        final float edgeA = .13F, coreA = .95F;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            float noise = .5F + .5F * Mth.sin(i * 1.73F + scatter * 9);
            if (broken && scatter > .22F && ((i + (int) Math.floor(scatter * 13)) % gap == 0 || noise < scatter * .18F)) continue;
            float t0 = i / (float) RING_SEGMENTS, t1 = (i + 1) / (float) RING_SEGMENTS, a0 = t0 * TAU, a1 = t1 * TAU,
                    local = a * (broken ? (.72F + noise * .28F) * (1 - scatter * .28F) : 1);
            float ca = Mth.cos(a0), sa = Mth.sin(a0), cb = Mth.cos(a1), sb = Mth.sin(a1);
            Vec3 eOut0 = new Vec3(ca * outer, y, sa * outer), eOut1 = new Vec3(cb * outer, y, sb * outer),
                 eMid0 = new Vec3(ca * mid, y, sa * mid),   eMid1 = new Vec3(cb * mid, y, sb * mid),
                 eIn0  = new Vec3(ca * inner, y, sa * inner), eIn1 = new Vec3(cb * inner, y, sb * inner);
            quadA(m, vc, eOut0, eOut1, eMid1, eMid0, r, g, bl, local * edgeA, local * edgeA, local * coreA, local * coreA);
            quadA(m, vc, eMid0, eMid1, eIn1, eIn0, r, g, bl, local * coreA, local * coreA, local * edgeA, local * edgeA);
        }
    }
    private static void segmentedRing(Matrix4f m, VertexConsumer vc, float y, float centerZ, float radius, float width,
                                  float rotation, int gap, float r, float g, float bl, float a) {
        float inner = Math.max(.02F, radius - width * .5F), mid = radius, outer = radius + width * .5F;
        final float edgeA = .12F, coreA = .90F;
        for (int i = 0; i < 72; i++) {
            if (i % gap == gap - 1) continue;
            float t0 = i / 72F, t1 = (i + 1) / 72F, a0 = rotation + t0 * TAU, a1 = rotation + t1 * TAU;
            float ca = Mth.cos(a0), sa = Mth.sin(a0), cb = Mth.cos(a1), sb = Mth.sin(a1);
            Vec3 eOut0 = new Vec3(ca * outer, y, centerZ + sa * outer), eOut1 = new Vec3(cb * outer, y, centerZ + sb * outer),
                 eMid0 = new Vec3(ca * mid, y, centerZ + sa * mid),   eMid1 = new Vec3(cb * mid, y, centerZ + sb * mid),
                 eIn0  = new Vec3(ca * inner, y, centerZ + sa * inner), eIn1 = new Vec3(cb * inner, y, centerZ + sb * inner);
            quadA(m, vc, eOut0, eOut1, eMid1, eMid0, r, g, bl, a * edgeA, a * edgeA, a * coreA, a * coreA);
            quadA(m, vc, eMid0, eMid1, eIn1, eIn0, r, g, bl, a * coreA, a * coreA, a * edgeA, a * edgeA);
        }
    }
    private static void hRibbon(Matrix4f m, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                                float width, float r, float g, float bl, float a, float u) {
        Vec3 d = new Vec3(x1 - x0, 0, z1 - z0);
        if (d.lengthSqr() <= 1E-5) return;
        d = d.normalize();
        Vec3 s = new Vec3(-d.z * width, 0, d.x * width), p0 = new Vec3(x0, y0, z0), p1 = new Vec3(x1, y1, z1);
        quadQ(m, vc, p0.add(s), p1.add(s), p1.subtract(s), p0.subtract(s), r, g, bl, a);
    }
    private static void line(Matrix4f m, VertexConsumer vc, Vec3 p0, Vec3 p1, float width, float r, float g, float bl, float a) {
        Vec3 d = p1.subtract(p0);
        if (d.lengthSqr() <= 1E-5 || a <= .001F) return;
        d = d.normalize();
        Vec3 s = new Vec3(-d.z, 0, d.x);
        if (s.lengthSqr() <= 1E-5) s = new Vec3(1, 0, 0); else s = s.normalize();
        // 内亮外暗·细线（加法混合）：外层=宽而暗的羽化晕(颜色压暗+低透明度)使它"能感觉到但不亮"；
        // 中层=较细彩色带；内芯=极细白热亮芯。亮部收窄→整体细，暗部放宽→羽化层次清晰
        float dr = r * .48F, dg = g * .48F, db = bl * .48F;
        Vec3 h = s.scale(width * .22F);                       // 外层暗晕（很细、很淡）
        quadQ(m, vc, p0.add(h), p1.add(h), p1.subtract(h), p0.subtract(h), dr, dg, db, a * .14F);
        Vec3 mo = s.scale(width * .11F);                      // 中带（细）
        quadQ(m, vc, p0.add(mo), p1.add(mo), p1.subtract(mo), p0.subtract(mo), r, g, bl, a * .35F);
        Vec3 n = s.scale(width * .035F);                      // 发丝级白热亮芯
        quadQ(m, vc, p0.add(n), p1.add(n), p1.subtract(n), p0.subtract(n), 1F, .97F, 1F, a * .95F);
    }
    private static void diamond(Matrix4f m, VertexConsumer vc, float x, float y, float z, float size, float rot,
                            float r, float g, float bl, float a) {
    float c = Mth.cos(rot), s = Mth.sin(rot), mx = c * size, mz = s * size, sx = -s * size * .10F, sz = c * size * .10F;
    // 细条纹：极薄的纵向光条，众多条叠加+加法混合 → 融合感
    quadQ(m, vc, new Vec3(x - mx, y - size * .10F, z - mz), new Vec3(x + sx, y + size * 1.15F, z + sz),
            new Vec3(x + mx, y + size * .10F, z + mz), new Vec3(x - sx, y - size * 1.15F, z - sz), r, g, bl, a);
}
    private static void lightColumn(Matrix4f m, VertexConsumer vc, float x, float z, float y0, float y1, float half,
                                    float r, float g, float bl, float a) {
        quadQ(m, vc, new Vec3(x - half, y0, z), new Vec3(x - half * .45F, y1, z),
                new Vec3(x + half * .45F, y1, z), new Vec3(x + half, y0, z), r, g, bl, a);
        quadQ(m, vc, new Vec3(x, y0, z - half), new Vec3(x, y1, z - half * .45F),
                new Vec3(x, y1, z + half * .45F), new Vec3(x, y0, z + half), r, g, bl, a);
    }

    private static final class Arc {
        final float x, y, z, sx, sy;
        Arc(float x, float y, float z, float sx, float sy) { this.x = x; this.y = y; this.z = z; this.sx = sx; this.sy = sy; }
        Vec3 plus(float w, float dz) { return new Vec3(x + sx * w, y + sy * w, z + dz); }
    }

    private static void wing(Matrix4f m, VertexConsumer vc, float x, float y, float z, float angle, float scale, float a) {
        float fx = Mth.cos(angle), fz = Mth.sin(angle), sx = -fz, sz = fx, front = 1.18F * scale, back = .72F * scale, w = .34F * scale;
        quadQ(m, vc, new Vec3(x + fx * front, y, z + fz * front), new Vec3(x + sx * w, y + .12F * scale, z + sz * w),
                new Vec3(x - fx * back, y, z - fz * back), new Vec3(x - sx * w, y - .12F * scale, z - sz * w),
                GOLD_R, GOLD_G, GOLD_B, a);
        hRibbon(m, vc, x - fx * .18F * scale, y - .025F, z - fz * .18F * scale,
                x - fx * .74F * scale + sx * .66F * scale, y - .025F, z - fz * .74F * scale + sz * .66F * scale,
                .04F * scale, LAV_R, LAV_G, LAV_B, a * .82F, 2);
        hRibbon(m, vc, x - fx * .18F * scale, y + .025F, z - fz * .18F * scale,
                x - fx * .74F * scale - sx * .66F * scale, y + .025F, z - fz * .74F * scale - sz * .66F * scale,
                .04F * scale, LAV_R, LAV_G, LAV_B, a * .82F, 2);
    }

    // ---------------- 工具 ----------------
    private static float sweepWidth(float t) { return .05F + (float) Math.pow(Mth.sin(t * (float) Math.PI), .6D) * .30F; }
    private static float sweepAlpha(float t, float reveal) {
        float head = smoother(Mth.clamp(t / Math.max(.001F, reveal), 0, 1)),
                tail = smoother(Mth.clamp(t / .14F, 0, 1)),
                end = 1 - smoother(Mth.clamp((t - .92F) / .08F, 0, 1));
        return (.26F + head * .74F) * tail * end;
    }
    private static Arc arcPoint(float t, float cx, float cy, float cz, float rx, float ry, float bend, float start, float end) {
        float angle = (start + (end - start) * t) * DEG,
                x = cx + Mth.cos(angle) * rx, y = cy + Mth.sin(angle) * ry, z = cz + Mth.sin(t * (float) Math.PI) * bend,
                span = (end - start) * DEG, dx = -Mth.sin(angle) * rx * span, dy = Mth.cos(angle) * ry * span,
                len = Mth.sqrt(dx * dx + dy * dy);
        return len <= 1E-5F ? new Arc(x, y, z, 0, 1) : new Arc(x, y, z, -dy / len, dx / len);
    }
    private static float vWidth(float t) { return .06F + (float) Math.pow(Math.max(0, Mth.sin(t * (float) Math.PI)), .55D) * .34F; }
    private static float vAlpha(float t, float reveal) {
        float head = smoother(Mth.clamp(t / Math.max(.001F, reveal), 0, 1)),
                tail = smoother(Mth.clamp(t / .10F, 0, 1)),
                end = 1 - smoother(Mth.clamp((t - .94F) / .06F, 0, 1));
        return (.32F + head * .68F) * tail * end;
    }
    private static float stage(float age, float start, float duration) { return Mth.clamp((age - start) / duration, 0, 1); }
    private static float smoother(float t) { t = Mth.clamp(t, 0, 1); return t * t * t * (t * (t * 6 - 15) + 10); }
    private static float fastOut(float t) { t = Mth.clamp(t, 0, 1); float i = 1 - t; return 1 - i * i * i * i; }
    private static float edge(float t) {
        return smoother(Mth.clamp(t / .12F, 0, 1)) * (1 - smoother(Mth.clamp((t - .88F) / .12F, 0, 1)));
    }
    private static float det(int i, float salt) {
        float v = Mth.sin(i * 12.9898F + salt * 78.233F) * 43758.547F;
        return v - (float) Math.floor(v);
    }
    private static float lerp(float t, float a, float b) { return a + (b - a) * t; }
}