package com.example.skydeityslash.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * sakurafox 默认剑技「墨山·翠嶂流泉」特效几何 —— 山水水墨 + 墨绿。
 * 复用剑体始觉同款 POSITION_COLOR 加法混合管线：亮芯（白热细线）+ 淡外包（羽化晕）、无剔除、无深度写。
 * 视觉语言：三叠由深到浅、由低到高的墨绿远山层峦（近深墨 · 中山水绿 · 远翠玉），峰顶月白亮脊，
 * 底部深墨山影环带，四周青绿水纹溪流扇出，中央月白凝光与上冲光柱为亮芯。
 */
public final class InkFoxGeometry {
    private InkFoxGeometry() {}

    private static final float TAU = (float) Math.PI * 2F, DEG = (float) Math.PI / 180F;
    private static final int RING_SEGMENTS = 88, SWEEP_SEGMENTS = 56;

    // 墨绿山水色板：深墨 → 墨绿 → 山水绿 → 翠玉 → 月白(亮芯)
    private static final float DEEP_R = .05F, DEEP_G = .10F, DEEP_B = .08F;
    private static final float DARK_R = .10F, DARK_G = .26F, DARK_B = .16F;
    private static final float MID_R = .14F, MID_G = .33F, MID_B = .21F;
    private static final float LITE_R = .18F, LITE_G = .38F, LITE_B = .28F;
    private static final float MOON_R = .16F, MOON_G = .40F, MOON_B = .28F;

    public enum Pass { COLOR, GLOW }

    private static void vv(Matrix4f m, VertexConsumer vc, float x, float y, float z, float r, float g, float b, float a) {
        vc.vertex(m, x, y, z)
                .color((int) (Mth.clamp(r, 0, 1) * 255), (int) (Mth.clamp(g, 0, 1) * 255),
                        (int) (Mth.clamp(b, 0, 1) * 255), (int) (Mth.clamp(a, 0, 1) * 255))
                .endVertex();
    }
    private static void quadQ(Matrix4f m, VertexConsumer vc, float x0, float y0, float z0,
                              float x1, float y1, float z1, float x2, float y2, float z2,
                              float x3, float y3, float z3, float r, float g, float b, float a) {
        vv(m, vc, x0, y0, z0, r, g, b, a); vv(m, vc, x1, y1, z1, r, g, b, a); vv(m, vc, x2, y2, z2, r, g, b, a);
        vv(m, vc, x0, y0, z0, r, g, b, a); vv(m, vc, x2, y2, z2, r, g, b, a); vv(m, vc, x3, y3, z3, r, g, b, a);
    }
    private static void quadA(Matrix4f m, VertexConsumer vc, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                              float r, float g, float b, float a0, float a1, float a2, float a3) {
        vv(m, vc, (float) p0.x, (float) p0.y, (float) p0.z, r, g, b, a0);
        vv(m, vc, (float) p1.x, (float) p1.y, (float) p1.z, r, g, b, a1);
        vv(m, vc, (float) p2.x, (float) p2.y, (float) p2.z, r, g, b, a2);
        vv(m, vc, (float) p0.x, (float) p0.y, (float) p0.z, r, g, b, a0);
        vv(m, vc, (float) p2.x, (float) p2.y, (float) p2.z, r, g, b, a2);
        vv(m, vc, (float) p3.x, (float) p3.y, (float) p3.z, r, g, b, a3);
    }

    // ==================================================================
    // 墨山·翠嶂流泉 主绘制（围绕原点，前方为 +Z，实体已按 yaw 旋转）
    // ==================================================================
    public static void draw(Matrix4f m, VertexConsumer vc, float age, Pass pass) {
        if (pass == Pass.COLOR) {
            groundField(m, vc, age);
            inkCage(m, vc, age);
            accents(m, vc, age);
            groundRings(m, vc, age);
            mountainWreath(m, vc, age);
            risingColumns(m, vc, age);
            aerialRings(m, vc, age);
            valleyStreams(m, vc, age);
        } else {
            brightRings(m, vc, age);
        }
    }

    /** 新增·地面阵图：山水绿主断环 + 深墨细分环 + 翠玉旋转径环，中央月白细亮芯（原剑体始觉 groundRings 形态，山水配色） */
    private static void groundField(Matrix4f m, VertexConsumer vc, float age) {
        float open = smoother(stage(age, 4, 6)), fade = 1 - smoother(stage(age, 44, 14)), a = open * fade;
        if (a <= .01F) return;
        float rot = age * .03F;
        brokenRing(m, vc, .05F, 1.2F + open * 4.1F, .014F + open * .030F, .18F + open * .4F,
                MID_R, MID_G, MID_B, a * .25F);
        ring(m, vc, .07F, 1.45F + open * 4.3F, .008F + open * .022F, DARK_R, DARK_G, DARK_B, a * .20F);
        segmentedRing(m, vc, .09F, 0, 3.3F + open * .3F, .020F, rot, 8, LITE_R, LITE_G, LITE_B, a * .22F);
        ring(m, vc, .10F, 1.35F + open * 4.0F, .005F + open * .010F, MOON_R, MOON_G, MOON_B, a * .20F);
    }

    /** 新增·牢笼：8 根竖直光柱 + 顶底环，山水绿外 / 月白细芯（原鸣雷神 cage 形态） */
    private static void inkCage(Matrix4f m, VertexConsumer vc, float age) {
        float open = smoother(stage(age, 5, 5)), fade = 1 - smoother(stage(age, 44, 12)), a = open * fade;
        if (a <= .01F) return;
        float R = 2.4F + open * 1.6F, top = 2.2F + open * 1.6F;
        for (int i = 0; i < 8; i++) {
            float ang = i * TAU / 8 + age * .02F;
            float x = Mth.cos(ang) * R, z = Mth.sin(ang) * R;
            lightColumn(m, vc, x, z, .10F, top, .055F, MID_R, MID_G, MID_B, a * .22F);
            lightColumn(m, vc, x, z, .10F, top, .014F, MOON_R, MOON_G, MOON_B, a * .25F);
        }
        ring(m, vc, .16F, R, .020F, MOON_R, MOON_G, MOON_B, a * .30F);
        ring(m, vc, .32F, R, .016F, MID_R, MID_G, MID_B, a * .20F);
        ring(m, vc, .05F, R + .05F, .014F, DARK_R, DARK_G, DARK_B, a * .18F);
    }

    /** 新增·点缀：环绕场地四周漂浮的短翠玉/月白小光点（原鸣雷神 accents 形态） */
    private static void accents(Matrix4f m, VertexConsumer vc, float age) {
        float appear = smoother(stage(age, 4, 6)), fade = 1 - smoother(stage(age, 44, 14)), a = appear * fade;
        if (a <= .01F) return;
        for (int i = 0; i < 12; i++) {
            float ang = i * TAU / 12 + age * .05F;
            float r = (3.0F + appear * 1.6F) + Mth.sin(age * .4F + i * 2.1F) * .35F;
            float x = Mth.cos(ang) * r, z = Mth.sin(ang) * r;
            float y = .8F + Mth.sin(age * .3F + i * 1.7F) * .6F;
            lightColumn(m, vc, x, z, y, y + .30F, .045F, LITE_R, LITE_G, LITE_B, a * .25F);
            lightColumn(m, vc, x, z, y, y + .30F, .010F, MOON_R, MOON_G, MOON_B, a * .28F);
        }
    }

    /** 底部深墨山影环带：主断环（墨绿）+ 深墨细分环。先展开到当前大小，随后持续缓慢扩大 */
    private static void groundRings(Matrix4f m, VertexConsumer vc, float age) {
        float open = smoother(stage(age, 4, 8)), fade = 1 - smoother(stage(age, 46, 14)), a = open * fade;
        float grow = smoother(stage(age, 12, 28)) * 3.8F;
        if (a <= .01F) return;
        brokenRing(m, vc, .05F, lerp(open, 1.0F, 3.4F) + grow, lerp(open, .009F, .030F), .18F + open * .5F,
                MID_R, MID_G, MID_B, a * .5F);
        ring(m, vc, .07F, 1.4F + open * 3.0F + grow, .010F + open * .024F, DARK_R, DARK_G, DARK_B, a * .40F);
        float rot = age * .022F;
        segmentedRing(m, vc, .09F, 0, 3.45F + open * .2F + grow * .8F, .024F, rot, 8, MID_R, MID_G, MID_B, a * .45F);
        segmentedRing(m, vc, .11F, 0, 4.35F + grow * .9F, .014F, -rot * 1.3F, 12, DEEP_R, DEEP_G, DEEP_B, a * .38F);
    }

    /** 三叠远山层峦：由近及远、由深渐浅、由低渐高的墨绿山环带，峰顶细亮脊 */
    private static void mountainWreath(Matrix4f m, VertexConsumer vc, float age) {
        float open = smoother(stage(age, 4, 10)), fade = 1 - smoother(stage(age, 44, 14)), a = open * fade;
        if (a <= .01F) return;
        float rot = age * .05F;
        int seg = 40;

        // 近山 · 深墨绿（最低）
        mountainShell(m, vc, age, 2.0F + open * .3F, open, a * .42F, DARK_R, DARK_G, DARK_B, .9F, rot * 1.6F, seg);
        crestRing(m, vc, 2.0F + open * .3F, open, a * .18F, MID_R, MID_G, MID_B, .9F, rot * 1.6F, seg);
        // 中山 · 山水绿
        mountainShell(m, vc, age, 3.4F + open * .25F, open, a * .45F, MID_R, MID_G, MID_B, 1.55F, rot, seg);
        crestRing(m, vc, 3.4F + open * .25F, open, a * .16F, LITE_R, LITE_G, LITE_B, 1.55F, rot, seg);
        // 远山 · 翠玉（最高、最细、最淡）
        mountainShell(m, vc, age, 5.0F + open * .2F, open, a * .40F, LITE_R, LITE_G, LITE_B, 2.25F, -rot * .5F, seg);
        crestRing(m, vc, 5.0F + open * .2F, open, a * .20F, MOON_R, MOON_G, MOON_B, 2.25F, -rot * .5F, seg);
        // 高远 · 淡翠远峰（最远最浅，素墨勾）
        mountainShell(m, vc, age, 6.7F + open * .15F, open, a * .30F, MOON_R, MOON_G, MOON_B, 2.9F, rot * .4F, seg);
        crestRing(m, vc, 6.7F + open * .15F, open, a * .13F, MOON_R, MOON_G, MOON_B, 2.9F, rot * .4F, seg);
    }

    /** 一层山环带：锯齿峰顶的环状山壁 */
    private static void mountainShell(Matrix4f m, VertexConsumer vc, float age, float radius, float open, float a,
                                      float r, float g, float b, float amp, float rot, int seg) {
        float ri = Math.max(.25F, radius - .55F), ro = radius + .55F;
        for (int i = 0; i < seg; i++) {
            float t0 = i / (float) seg, t1 = (i + 1) / (float) seg;
            float th0 = rot + t0 * TAU, th1 = rot + t1 * TAU;
            float h0 = peak(th0, amp), h1 = peak(th1, amp);
            Vec3 pIn0 = pol(ri, .0F, th0), pIn1 = pol(ri, .0F, th1);
            Vec3 pTip0 = pol(ri, h0, th0), pTip1 = pol(ri, h1, th1);
            Vec3 pOut0 = pol(ro, h0, th0), pOut1 = pol(ro, h1, th1);
            Vec3 pOutB0 = pol(ro, .05F, th0), pOutB1 = pol(ro, .05F, th1);
            // 内壁（朝中心，较亮）
            quadA(m, vc, pIn0, pTip0, pTip1, pIn1, r * 1F, g * 1F, b * 1F, a * .40F, a * .40F, a * .40F, a * .40F);
            // 峰顶面（山脊）
            quadA(m, vc, pTip0, pOut0, pOut1, pTip1, r * 1.05F, g * 1.05F, b * 1.05F, a * .60F, a * .60F, a * .60F, a * .60F);
            // 外壁（背向中心，较暗）
            quadA(m, vc, pOutB0, pOut0, pOut1, pOutB1, r * .62F, g * .62F, b * .62F, a * .22F, a * .22F, a * .22F, a * .22F);
        }
    }

    /** 峰顶亮脊细线（月白亮芯 + 对应色外包） */
    private static void crestRing(Matrix4f m, VertexConsumer vc, float radius, float open, float a,
                                  float r, float g, float b, float amp, float rot, int seg) {
        float ro = radius + .55F;
        for (int i = 0; i < seg; i++) {
            float t0 = i / (float) seg, t1 = (i + 1) / (float) seg;
            float th0 = rot + t0 * TAU, th1 = rot + t1 * TAU;
            Vec3 q0 = pol(ro, peak(th0, amp), th0), q1 = pol(ro, peak(th1, amp), th1);
            line(m, vc, q0, q1, .026F, r, g, b, a * .9F);
        }
    }

    private static float peak(float th, float amp) {
        float n = .58F * Mth.sin(th * 3 + 1.3F) + .42F * Mth.sin(th * 5 + .7F);
        return amp * (.30F + .70F * (n * .5F + .5F));
    }
    private static Vec3 pol(float radius, float h, float th) {
        return new Vec3(Mth.cos(th) * radius, .55F + h, Mth.sin(th) * radius);
    }

    /** 上冲飞瀑光柱：围绕中心的 4 根细墨绿光柱（内里月白亮芯），出现即拉升到最高 */
    private static void risingColumns(Matrix4f m, VertexConsumer vc, float age) {
        float fade = 1 - smoother(stage(age, 44, 12)), a = fade;
        if (a <= .01F) return;
        for (int i = 0; i < 4; i++) {
            float ang = i * TAU / 4 + age * .05F;
            float x = Mth.cos(ang) * 3.2F, z = Mth.sin(ang) * 3.2F;
            float h = 200.0F;
            lightColumn(m, vc, x, z, .16F, h, .10F, MID_R, MID_G, MID_B, a * .55F);
            lightColumn(m, vc, x, z, .22F, h, .024F, MOON_R, MOON_G, MOON_B, a * .40F);
        }
    }

    /** 空中偏向环带：中心上方不同高度、倾角的墨绿/翠玉光环（疏烟勾云雾）+ 月白亮芯细环 */
    private static void aerialRings(Matrix4f m, VertexConsumer vc, float age) {
        float appear = smoother(stage(age, 4, 6)), fade = 1 - smoother(stage(age, 44, 14)), a = appear * fade;
        if (a <= .01F) return;
        float open = smoother(stage(age, 6, 12));
        aerialRing(m, vc, new Vec3(0, 1.9F, 0), .18F, 2.35F + open * .6F, .022F, MID_R, MID_G, MID_B, a * .25F);
        aerialRing(m, vc, new Vec3(0, 1.6F, 0), -.24F, 2.9F + open * .5F, .020F, MID_R, MID_G, MID_B, a * .23F);
        aerialRing(m, vc, new Vec3(0, 2.4F, 0), .34F, 3.4F + open * .4F, .020F, LITE_R, LITE_G, LITE_B, a * .20F);
        aerialRing(m, vc, new Vec3(0, 1.35F, 0), -.12F, 3.85F + open * .3F, .016F, MOON_R, MOON_G, MOON_B, a * .21F);
        aerialRing(m, vc, new Vec3(0, 3.0F, 0), .05F, 4.6F + open * .8F, .030F, MOON_R, MOON_G, MOON_B, a * .14F);
    }

    private static void aerialRing(Matrix4f m, VertexConsumer vc, Vec3 center, float tilt, float radius, float width,
                                   float r, float g, float b, float a) {
        float inner = Math.max(.02F, radius - width * .5F), mid = radius, outer = radius + width * .5F;
        final float edgeA = .13F, coreA = .9F;
        float cT = Mth.cos(tilt), sT = Mth.sin(tilt);
        for (int i = 0; i < RING_SEGMENTS; i++) {
            float t0 = i / (float) RING_SEGMENTS, t1 = (i + 1) / (float) RING_SEGMENTS, th0 = t0 * TAU, th1 = t1 * TAU;
            Vec3 eOut0 = ringPt(center, cT, sT, th0, outer),
                 eOut1 = ringPt(center, cT, sT, th1, outer),
                 eMid0 = ringPt(center, cT, sT, th0, mid),
                 eMid1 = ringPt(center, cT, sT, th1, mid),
                 eIn0 = ringPt(center, cT, sT, th0, inner),
                 eIn1 = ringPt(center, cT, sT, th1, inner);
            quadA(m, vc, eOut0, eOut1, eMid1, eMid0, r, g, b, a * edgeA, a * edgeA, a * coreA, a * coreA);
            quadA(m, vc, eMid0, eMid1, eIn1, eIn0, r, g, b, a * coreA, a * coreA, a * edgeA, a * edgeA);
        }
    }
    private static Vec3 ringPt(Vec3 center, float cT, float sT, float th, float r) {
        float c = Mth.cos(th) * r, s = Mth.sin(th) * r;
        float y = -s * sT, z = s * cT;
        return center.add(c, y, z);
    }

    /** 山间青绿水纹溪流：从中心向四方扇出的细亮溪流 */
    private static void valleyStreams(Matrix4f m, VertexConsumer vc, float age) {
        float sweep = smoother(stage(age, 10, 7)), fade = 1 - smoother(stage(age, 40, 8)), a = sweep * fade;
        if (a <= .01F) return;
        for (int i = 0; i < 14; i++) {
            float base = i * TAU / 14 + age * .05F;
            float jitter = Mth.sin(i * 2.7F) * .5F;
            float r0 = 1.0F + sweep * 3.5F, r1 = r0 + 1.3F;
            float y = .62F + Mth.sin(age * .3F + i) * .06F;
            hRibbon(m, vc, Mth.cos(base) * r0, y, Mth.sin(base) * r0,
                    Mth.cos(base + jitter) * r1, y + Mth.sin(age * .2F + i) * .20F, Mth.sin(base + jitter) * r1,
                    .06F + sweep * .03F, MID_R, MID_G, MID_B, a * .28F);
            hRibbon(m, vc, Mth.cos(base) * r0, y + .03F, Mth.sin(base) * r0,
                    Mth.cos(base + jitter) * r1, y + .03F + Mth.sin(age * .2F + i) * .20F, Mth.sin(base + jitter) * r1,
                    .02F, MOON_R, MOON_G, MOON_B, a * .22F);
        }
    }

    /** MONO GLOW 通道：中央月白凝光球（青白色） */
    private static void coreGlow(Matrix4f m, VertexConsumer vc, float age) {
        float appear = smoother(stage(age, 4, 4)), fade = 1 - smoother(stage(age, 46, 12)), a = appear * fade;
        if (a <= .01F) return;
        float radius = .55F + .25F * smoother(stage(age, 4, 7)) * (1 - smoother(stage(age, 42, 5)));
        int seg = 26;
        Vec3 o = new Vec3(0, 1.15F, 0), right = new Vec3(1, 0, 0), up = new Vec3(0, 1, 0);
        for (int i = 0; i < seg; i++) {
            float t0 = i * TAU / seg, t1 = (i + 1) * TAU / seg;
            Vec3 r0 = right.scale(Mth.cos(t0)).add(up.scale(Mth.sin(t0))).scale(radius);
            Vec3 r1 = right.scale(Mth.cos(t1)).add(up.scale(Mth.sin(t1))).scale(radius);
            vv(m, vc, (float) o.x, (float) o.y, (float) o.z, 1, 1, 1, a * .15F);
            vv(m, vc, (float) o.x + (float) r0.x, (float) o.y + (float) r0.y, (float) o.z + (float) r0.z,
                    .45F, 1F, .66F, a * .03F);
            vv(m, vc, (float) o.x + (float) r1.x, (float) o.y + (float) r1.y, (float) o.z + (float) r1.z,
                    .45F, 1F, .66F, a * .03F);
        }
    }

    /** MONO GLOW 通道：主断环月白亮芯 */
    private static void brightRings(Matrix4f m, VertexConsumer vc, float age) {
        float open = smoother(stage(age, 4, 8)), fade = 1 - smoother(stage(age, 46, 14)), a = open * fade;
        if (a <= .01F) return;
        ring(m, vc, .08F, 1.1F + open * 5.0F, .004F + open * .008F, MOON_R, MOON_G, MOON_B, a * .14F);
    }

    // ---------------- 图元（加法混合：亮芯 + 淡外包） ----------------
    private static void brokenRing(Matrix4f m, VertexConsumer vc, float y, float radius, float width, float scatter,
                                   float r, float g, float b, float a) {
        ringInternal(m, vc, y, radius, width, scatter, r, g, b, a, true);
    }
    private static void ring(Matrix4f m, VertexConsumer vc, float y, float radius, float width, float r, float g, float b, float a) {
        ringInternal(m, vc, y, radius, width, 0, r, g, b, a, false);
    }
    private static void ringInternal(Matrix4f m, VertexConsumer vc, float y, float radius, float width, float scatter,
                                 float r, float g, float b, float a, boolean broken) {
        float inner = Math.max(.02F, radius - width * .5F), mid = radius, outer = radius + width * .5F;
        int gap = Math.max(2, 8 - (int) Math.floor(scatter * 5));
        final float edgeA = .13F, coreA = .95F;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            float noise = .5F + .5F * Mth.sin(i * 1.73F + scatter * 9);
            if (broken && scatter > .22F && ((i + (int) Math.floor(scatter * 13)) % gap == 0 || noise < scatter * .18F)) continue;
            float t0 = i / (float) RING_SEGMENTS, t1 = (i + 1) / (float) RING_SEGMENTS, a0 = t0 * TAU, a1 = t1 * TAU,
                    local = a * (broken ? (.72F + noise * .28F) * (1 - scatter * .28F) : 1);
            Vec3 eOut0 = new Vec3(Mth.cos(a0) * outer, y, Mth.sin(a0) * outer),
                 eOut1 = new Vec3(Mth.cos(a1) * outer, y, Mth.sin(a1) * outer),
                 eMid0 = new Vec3(Mth.cos(a0) * mid, y, Mth.sin(a0) * mid),
                 eMid1 = new Vec3(Mth.cos(a1) * mid, y, Mth.sin(a1) * mid),
                 eIn0 = new Vec3(Mth.cos(a0) * inner, y, Mth.sin(a0) * inner),
                 eIn1 = new Vec3(Mth.cos(a1) * inner, y, Mth.sin(a1) * inner);
            quadA(m, vc, eOut0, eOut1, eMid1, eMid0, r, g, b, local * edgeA, local * edgeA, local * coreA, local * coreA);
            quadA(m, vc, eMid0, eMid1, eIn1, eIn0, r, g, b, local * coreA, local * coreA, local * edgeA, local * edgeA);
        }
    }
    private static void segmentedRing(Matrix4f m, VertexConsumer vc, float y, float centerZ, float radius, float width,
                                  float rotation, int gap, float r, float g, float b, float a) {
        float inner = Math.max(.02F, radius - width * .5F), mid = radius, outer = radius + width * .5F;
        final float edgeA = .12F, coreA = .90F;
        for (int i = 0; i < 72; i++) {
            if (i % gap == gap - 1) continue;
            float a0 = rotation + (i / 72F) * TAU, a1 = rotation + ((i + 1) / 72F) * TAU;
            Vec3 eOut0 = new Vec3(Mth.cos(a0) * outer, y, centerZ + Mth.sin(a0) * outer),
                 eOut1 = new Vec3(Mth.cos(a1) * outer, y, centerZ + Mth.sin(a1) * outer),
                 eMid0 = new Vec3(Mth.cos(a0) * mid, y, centerZ + Mth.sin(a0) * mid),
                 eMid1 = new Vec3(Mth.cos(a1) * mid, y, centerZ + Mth.sin(a1) * mid),
                 eIn0 = new Vec3(Mth.cos(a0) * inner, y, centerZ + Mth.sin(a0) * inner),
                 eIn1 = new Vec3(Mth.cos(a1) * inner, y, centerZ + Mth.sin(a1) * inner);
            quadA(m, vc, eOut0, eOut1, eMid1, eMid0, r, g, b, a * edgeA, a * edgeA, a * coreA, a * coreA);
            quadA(m, vc, eMid0, eMid1, eIn1, eIn0, r, g, b, a * coreA, a * coreA, a * edgeA, a * edgeA);
        }
    }
    private static void lightColumn(Matrix4f m, VertexConsumer vc, float x, float z, float y0, float y1, float half,
                                    float r, float g, float b, float a) {
        quadQ(m, vc, x - half, y0, z, x - half * .45F, y1, z, x + half * .45F, y1, z, x + half, y0, z, r, g, b, a);
        quadQ(m, vc, x, y0, z - half, x, y1, z - half * .45F, x, y1, z + half * .45F, x, y0, z + half, r, g, b, a);
    }
    private static void hRibbon(Matrix4f m, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                                float width, float r, float g, float b, float a) {
        Vec3 d = new Vec3(x1 - x0, 0, z1 - z0);
        if (d.lengthSqr() <= 1E-5) return;
        d = d.normalize();
        Vec3 s = new Vec3(-d.z * width, 0, d.x * width);
        quadQ(m, vc, x0 + (float) s.x, y0, z0 + (float) s.z, x1 + (float) s.x, y1, z1 + (float) s.z,
                x1 - (float) s.x, y1, z1 - (float) s.z, x0 - (float) s.x, y0, z0 - (float) s.z, r, g, b, a);
    }
    private static void line(Matrix4f m, VertexConsumer vc, Vec3 p0, Vec3 p1, float width, float r, float g, float b, float a) {
        Vec3 d = p1.subtract(p0);
        if (d.lengthSqr() <= 1E-5 || a <= .001F) return;
        d = d.normalize();
        Vec3 s = new Vec3(-d.z, 0, d.x);
        if (s.lengthSqr() <= 1E-5) s = new Vec3(1, 0, 0); else s = s.normalize();
        float dr = r * .48F, dg = g * .48F, db = b * .48F;
        Vec3 h = s.scale(width * .22F);
        quadQ(m, vc, (float) p0.add(h).x, (float) p0.add(h).y, (float) p0.add(h).z,
                (float) p1.add(h).x, (float) p1.add(h).y, (float) p1.add(h).z,
                (float) p1.subtract(h).x, (float) p1.subtract(h).y, (float) p1.subtract(h).z,
                (float) p0.subtract(h).x, (float) p0.subtract(h).y, (float) p0.subtract(h).z, dr, dg, db, a * .14F);
        Vec3 mo = s.scale(width * .11F);
        quadQ(m, vc, (float) p0.add(mo).x, (float) p0.add(mo).y, (float) p0.add(mo).z,
                (float) p1.add(mo).x, (float) p1.add(mo).y, (float) p1.add(mo).z,
                (float) p1.subtract(mo).x, (float) p1.subtract(mo).y, (float) p1.subtract(mo).z,
                (float) p0.subtract(mo).x, (float) p0.subtract(mo).y, (float) p0.subtract(mo).z, r, g, b, a * .35F);
        Vec3 n = s.scale(width * .035F);
        quadQ(m, vc, (float) p0.add(n).x, (float) p0.add(n).y, (float) p0.add(n).z,
                (float) p1.add(n).x, (float) p1.add(n).y, (float) p1.add(n).z,
                (float) p1.subtract(n).x, (float) p1.subtract(n).y, (float) p1.subtract(n).z,
                (float) p0.subtract(n).x, (float) p0.subtract(n).y, (float) p0.subtract(n).z, 1F, .97F, 1F, a * .95F);
    }

    // ---------------- 工具 ----------------
    private static float stage(float age, float start, float duration) { return Mth.clamp((age - start) / duration, 0, 1); }
    private static float smoother(float t) { t = Mth.clamp(t, 0, 1); return t * t * t * (t * (t * 6 - 15) + 10); }
    private static float lerp(float t, float a, float b) { return a + (b - a) * t; }
}