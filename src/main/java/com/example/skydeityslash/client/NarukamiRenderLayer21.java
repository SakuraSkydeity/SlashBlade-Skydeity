package com.example.skydeityslash.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import static com.example.skydeityslash.client.NarukamiGeometry21.hash01;

/**
 * 鸣雷神 六阶段视觉 忠实移植（来自 1.20KaBlade NarukamiRenderLayer）。
 * 「几何+配色」方案：保留原版全部几何/配色/时间轴，去着色器贴图，用 POSITION_COLOR 原生管线输出。
 */
final class NarukamiRenderLayer21 {
    enum Material { COMPOSITE, ENERGY, LIGHTNING, CROSS, PARTICLE }
    static final class Basis { final Vec3 right, forward; Basis(Vec3 r, Vec3 f) { right = r; forward = f; } }

    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final float TAU = (float) Math.PI * 2;
    private static final int BLACK_PURPLE = 0x160A22, DEEP_PURPLE = 0x3A126B, PURPLE = 0x7623C7,
            VIOLET = 0xAF50F2, PINK = 0xE39BFF, PALE = 0xF5DCFF, WHITE = 0xFFFFFF;
    private NarukamiRenderLayer21(){}

    static void render(Matrix4f m, VertexConsumer b, Material p, float frame, Vec3 owner, Vec3 target,
                       Basis q, Vec3 camera, long seed) {
        opening(m, b, p, frame, owner, q, camera, seed);
        crossSequence(m, b, p, frame, owner, target, q, camera, seed);
        cage(m, b, p, frame, owner, q, camera, seed);
        groundField(m, b, p, frame, owner, target, q, camera, seed);
        accents(m, b, p, frame, owner, target, q, camera, seed);
        residue(m, b, p, frame, owner, q, camera, seed);
    }

    static Basis basis(Vec3 direction) {
        Vec3 f = new Vec3(direction.x, 0, direction.z);
        if (f.lengthSqr() < 1E-8) f = new Vec3(0, 0, 1); else f = f.normalize();
        return new Basis(UP.cross(f).normalize(), f);
    }

    private static void opening(Matrix4f m, VertexConsumer b, Material p, float f, Vec3 owner, Basis q, Vec3 camera, long seed) {
        float a = NarukamiTimeline21.plateau(f, 0, .18F, 3.55F, 5);
        if (a <= .001F) return;
        Vec3 c = owner.add(0, 1.18, 0);
        float impact = NarukamiTimeline21.gaussian(f, .65F, .62F), secondary = NarukamiTimeline21.gaussian(f, 2.15F, .86F) * .55F,
                pulse = Math.min(1, impact + secondary), radius = .34F + pulse * 1.62F;
        if (p == Material.COMPOSITE) {
            NarukamiGeometry21.beam(m, b, c.subtract(q.right.scale(5.1)), c.add(q.right.scale(5.1)), .09F, camera, PURPLE, a * .68F);
        }
        if (p == Material.ENERGY) {
            NarukamiGeometry21.discBillboard(m, b, c, camera, radius, radius, -f * .13F, PINK, a * (.42F + pulse * .58F));
            NarukamiGeometry21.starBurst(m, b, c, camera, 18, 1.05F, 4.05F, .05F, VIOLET, a * (.52F + pulse * .48F), seed + 91, f * .035F);
            NarukamiGeometry21.starBurst(m, b, c, camera, 16, .88F, 3.62F, .022F, PALE, a * (.60F + pulse * .40F), seed + 101, f * .035F);
            NarukamiGeometry21.beam(m, b, c.subtract(q.right.scale(4.65)), c.add(q.right.scale(4.65)), .02F, camera, PALE, a);
        }
        if (p == Material.COMPOSITE || p == Material.LIGHTNING) {
            long ds = seed + 170 + (long) (f * 5) * 101;
            for (int i = 0; i < 15; i++) {
                double angle = i * TAU / 15 + (hash01(seed + i * 29) - .5) * .30,
                        h = .90 + hash01(seed + 220 + i * 31) * 3.15;
                Vec3 d = q.right.scale(Math.cos(angle) * h).add(UP.scale(Math.sin(angle) * (.78 + i % 4 * .30)))
                        .add(q.forward.scale((hash01(seed + 260 + i * 37) - .5) * 2));
                float flicker = .30F + .70F * sine2(f * 4.2F + i * 1.31F);
                NarukamiGeometry21.lightningLayer(m, b, p == Material.LIGHTNING, c, c.add(d), 8 + i % 5, .20F + i % 3 * .038F,
                        .022F + i % 2 * .006F, camera, PURPLE, i % 4 == 0 ? PALE : PINK, a * flicker, ds + i * 127);
            }
        }
    }

    private static void crossSequence(Matrix4f m, VertexConsumer b, Material p, float f, Vec3 owner, Vec3 target, Basis q, Vec3 camera, long seed) {
        float travel = crossTravel(f), previous = crossTravel(f - .55F), lag = .22F + Math.max(0, travel - previous) * 1.35F;
        Vec3 center = target.add(q.forward.scale(travel)), echo = center.subtract(q.forward.scale(lag));
        NarukamiGeometry21.Curve a = crossCurve(center, q, false), bb = crossCurve(center, q, true),
                ea = crossCurve(echo, q, false), eb = crossCurve(echo, q, true);
        float aa = NarukamiTimeline21.plateau(f, 4.8F, 5.8F, 13, 16), ab = NarukamiTimeline21.plateau(f, 5.35F, 6.25F, 13.4F, 16.2F),
                ha = NarukamiTimeline21.smooth(f, 4.8F, 6.85F), hb = NarukamiTimeline21.smooth(f, 5.35F, 7.45F),
                ta = .90F * NarukamiTimeline21.smooth(f, 11.8F, 15.8F), tb = .90F * NarukamiTimeline21.smooth(f, 12.2F, 16),
                era = NarukamiTimeline21.smooth(f, 12, 16), erb = NarukamiTimeline21.smooth(f, 12.4F, 16.2F);
        crossEcho(m, b, p, ea, ha, ta, aa, era, camera, seed + 481, f, .46F);
        crossEcho(m, b, p, eb, hb, tb, ab, erb, camera, seed + 581, f + 4, .43F);
        slashLayers(m, b, p, a, ha, ta, aa, era, camera, seed + 501, f, .46F, true);
        slashLayers(m, b, p, bb, hb, tb, ab, erb, camera, seed + 601, f + 4, .43F, true);
        float pulse = Mth.clamp(NarukamiTimeline21.gaussian(f, 6.70F, .52F) * .82F + NarukamiTimeline21.gaussian(f, 7.35F, .58F), 0, 1);
        if (pulse > .002F) {
            if (p == Material.COMPOSITE) NarukamiGeometry21.discBillboard(m, b, center, camera, 1.55F + pulse * 1.1F, 1.55F + pulse * 1.1F, f * .06F, DEEP_PURPLE, pulse * .24F);
            if (p == Material.ENERGY) {
                NarukamiGeometry21.discBillboard(m, b, center, camera, .72F + pulse * .58F, .72F + pulse * .58F, -f * .09F, PINK, pulse * .44F);
                NarukamiGeometry21.starBurst(m, b, center, camera, 10, .48F, 2.15F, .026F, PALE, pulse * .76F, seed + 651, f * .08F);
            }
            if (p == Material.COMPOSITE || p == Material.LIGHTNING) {
                long ds = seed + 670 + (long) (f * 5) * 83;
                for (int i = 0; i < 8; i++) {
                    double angle = i * TAU / 8 + (hash01(seed + 680 + i * 17) - .5) * .35;
                    Vec3 end = center.add(q.right.scale(Math.cos(angle) * (1.3 + i % 3 * .34)))
                            .add(UP.scale(Math.sin(angle) * (.9 + i % 2 * .3)))
                            .add(q.forward.scale((hash01(seed + 700 + i * 23) - .5) * 1.2));
                    NarukamiGeometry21.lightningLayer(m, b, p == Material.LIGHTNING, center, end, 6 + i % 3, .14F, .014F, camera,
                            PURPLE, i % 3 == 0 ? PALE : VIOLET, pulse * (.46F + .34F * sine2(f * 4.1F + i)), ds + i * 97);
                }
            }
        }
    }

    private static void cage(Matrix4f m, VertexConsumer b, Material p, float f, Vec3 owner, Basis q, Vec3 camera, long seed) {
        if (f < 13.5F || f > 29.2F) return;
        Vec3 c = owner.add(0, 1.58, 0).subtract(q.forward.scale(.05));
        track(m, b, p, f, NarukamiTimeline21.ENTRY_FRONT, orbit(c, q, -75, 75, 3.68, 2.42, -.18, .02, .04, .040, .025), camera, seed + 1001, .205F);
        track(m, b, p, f, NarukamiTimeline21.ENTRY_UPPER, orbit(c, q, 100, 240, 3.32, 2.20, .24, .40, .035, .030, .020), camera, seed + 1031, .165F);
        track(m, b, p, f, NarukamiTimeline21.ORBIT_A, orbit(c, q, 116, 246, 3.22, 2.08, .48, .12, .09, .025, .045), camera, seed + 1061, .095F);
        track(m, b, p, f, NarukamiTimeline21.ORBIT_B, orbit(c, q, 280, 400, 3.36, 2.18, .04, .40, .08, .018, -.040), camera, seed + 1091, .085F);
        track(m, b, p, f, NarukamiTimeline21.ORBIT_C, orbit(c, q, 298, 414, 3.06, 2, -.30, .10, .07, .020, .035), camera, seed + 1121, .075F);
        track(m, b, p, f, NarukamiTimeline21.RING_BACK_A, orbit(c, q, 120, 260, 3.28, 2.12, .20, .02, .10, .025, .040), camera, seed + 1151, .070F);
        track(m, b, p, f, NarukamiTimeline21.RING_BACK_B, orbit(c, q, -66, 76, 3.12, 2.02, .34, .24, .08, .020, -.035), camera, seed + 1181, .065F);
        track(m, b, p, f, NarukamiTimeline21.RING_BACK_C, orbit(c, q, 100, 240, 3.42, 2.20, -.08, .14, .07, .018, .030), camera, seed + 1211, .060F);
        NarukamiTimeline21.Cue dom = NarukamiTimeline21.dominant(f);
        pierce(m, b, p, f, NarukamiTimeline21.PIERCE_VERTICAL, axis(c, q, .76, -.30, -.46, .82, .78, -.43, .03, -.03),
                axis(c, q, .80, -1.20, -.50, .92, 1.58, -.42, .07, -.10), axis(c, q, .72, -1.08, -.56, 1.02, 1.46, -.34, .11, -.14),
                camera, seed + 1401, .180F, dom);
        pierce(m, b, p, f, NarukamiTimeline21.PIERCE_RISING, orbit(c, q, -165, -130, 3.05, 2.02, -.72, -.18, .08, .020, .025),
                orbit(c, q, -165, -25, 3.28, 2.20, -1.02, 1.08, .18, .055, .050), orbit(c, q, -155, -16, 3.38, 2.26, -.88, 1.18, .14, .040, .035),
                camera, seed + 1501, .205F, dom);
        pierce(m, b, p, f, NarukamiTimeline21.PIERCE_HORIZONTAL, orbit(c, q, -58, -22, 3.30, 2.18, -.10, -.04, .05, .020, .020),
                orbit(c, q, -58, 66, 3.58, 2.34, -.16, .18, .12, .045, -.040), orbit(c, q, -48, 74, 3.66, 2.40, -.08, .26, .08, .030, -.025),
                camera, seed + 1601, .165F, dom);
        pierce(m, b, p, f, NarukamiTimeline21.PIERCE_FALLING, orbit(c, q, 44, 80, 3.10, 2.08, .74, .22, .06, .020, .020),
                orbit(c, q, 44, 166, 3.36, 2.28, 1.18, -1.02, .16, .052, -.045), orbit(c, q, 54, 176, 3.44, 2.34, 1.06, -.90, .12, .035, -.030),
                camera, seed + 1701, .180F, dom);
        pierce(m, b, p, f, NarukamiTimeline21.PIERCE_OFFSET, orbit(c, q, 146, 182, 2.96, 2, -.52, -.12, .05, .020, .020),
                orbit(c, q, 146, 256, 3.22, 2.18, -.88, .78, .15, .050, .045), orbit(c, q, 156, 264, 3.30, 2.24, -.78, .86, .10, .035, .030),
                camera, seed + 1801, .145F, dom);
        single(m, b, p, f, NarukamiTimeline21.COLLAPSE_MAIN, orbit(c, q, 100, 250, 3.58, 2.34, -.68, -.46, .08, .035, .030), camera, seed + 1901, .255F, dom);
        track(m, b, p, f, NarukamiTimeline21.COLLAPSE_ECHO, orbit(c, q, 110, 230, 3.28, 2.14, -.72, -.52, .055, .020, -.025), camera, seed + 1941, .135F);
        single(m, b, p, f, NarukamiTimeline21.RESIDUAL_DIAGONAL, axis(c, q, -5.35, -1.95, -.10, 5.65, 3.15, .10, 0, -.18), camera, seed + 1981, .075F, dom);
        track(m, b, p, f, NarukamiTimeline21.RESIDUAL_HOOK, orbit(c, q, 150, 260, 3.02, 2, -.72, -.28, .09, .020, .025), camera, seed + 2021, .080F);
        float ea = NarukamiTimeline21.plateau(f, 14, 15.2F, 25.8F, 29);
        if (p == Material.COMPOSITE || p == Material.LIGHTNING) {
            long ds = seed + 2300 + (long) (f * 4) * 131;
            for (int i = 0; i < 14 && ea > .001F; i++) {
                double a = hash01(seed + i * 37) * TAU, bb = a + .55 + hash01(seed + 80 + i * 41) * .80;
                Vec3 s = c.add(q.right.scale(Math.cos(a) * (2.4 + i % 4 * .25))).add(q.forward.scale(Math.sin(a) * (1.55 + i % 3 * .20)))
                        .add(UP.scale(-.85 + hash01(seed + 120 + i * 43) * 2.1)),
                        e = c.add(q.right.scale(Math.cos(bb) * (2.8 + i % 3 * .24))).add(q.forward.scale(Math.sin(bb) * (1.8 + i % 4 * .16)))
                                .add(UP.scale(-.75 + hash01(seed + 160 + i * 47) * 2));
                NarukamiGeometry21.lightningLayer(m, b, p == Material.LIGHTNING, s, e, 7 + i % 4, .16F, .017F + i % 2 * .004F, camera,
                        PURPLE, i % 5 == 0 ? PALE : VIOLET, ea * (.24F + .76F * sine2(f * 3.6F + i * 1.17F)) * .72F, ds + i * 149);
            }
        }
    }

    private static void groundField(Matrix4f m, VertexConsumer b, Material p, float f, Vec3 owner, Vec3 target, Basis q, Vec3 camera, long seed) {
        float a = NarukamiTimeline21.plateau(f, 13.4F, 14.4F, 33.6F, 40);
        if (a > .001F) {
            Vec3 g = owner.add(0, .035, 0);
        float pulse = groundPulse(f);
        for (int i = 0; i < 4; i++) {
            final int ii = i;
            final float radius = 1.55F + i * .78F + pulse * (.12F + i * .045F);
            NarukamiGeometry21.Curve ring = u -> g.add(q.right.scale(Math.cos(u * TAU + ii * .37) * radius))
                    .add(q.forward.scale(Math.sin(u * TAU + ii * .37) * radius * .76));
            if ((i == 3 && p == Material.COMPOSITE) || (i != 3 && p == Material.ENERGY))
                NarukamiGeometry21.ribbonFixed(m, b, ring, 56, .94F, .05F + i * .09F, .010F + i * .0024F, UP,
                        i == 3 ? DEEP_PURPLE : VIOLET, a * (.26F - i * .035F + pulse * .18F), .24F, seed + 2600 + i * 31, f);
            }
            if (p == Material.COMPOSITE || p == Material.LIGHTNING) {
                long ds = seed + 2800 + (long) (f * 3) * 97;
                for (int i = 0; i < 11; i++) {
                    double angle = i * TAU / 11 + (hash01(seed + 2820 + i * 19) - .5) * .36,
                            length = 1.6 + hash01(seed + 2860 + i * 23) * 3.4;
                    Vec3 e = g.add(q.right.scale(Math.cos(angle) * length)).add(q.forward.scale(Math.sin(angle) * length * .72));
                    NarukamiGeometry21.lightningLayer(m, b, p == Material.LIGHTNING, g, e, 8 + i % 4, .14F, .015F + i % 2 * .004F, camera,
                            DEEP_PURPLE, i % 4 == 0 ? PALE : VIOLET, a * (.24F + .76F * sine2(f * 3 + i * 1.37F)) * (.40F + pulse * .42F), ds + i * 113);
                }
            }
        }
        float horizon = NarukamiTimeline21.plateau(f, 34.2F, 35, 37, 40);
        if (horizon > .001F) {
            Vec3 c = target.add(0, -.55, 0).subtract(q.forward.scale(2.9));
            if (p == Material.COMPOSITE) NarukamiGeometry21.beam(m, b, c.subtract(q.right.scale(8.6)), c.add(q.right.scale(8.6)), .13F, camera, DEEP_PURPLE, horizon * .46F);
            if (p == Material.ENERGY) NarukamiGeometry21.beam(m, b, c.subtract(q.right.scale(7.8)), c.add(q.right.scale(7.8)), .025F, camera, PALE, horizon * .72F);
        }
    }

    private static void accents(Matrix4f m, VertexConsumer b, Material p, float f, Vec3 owner, Vec3 target, Basis q, Vec3 camera, long seed) {
        if (p != Material.PARTICLE && p != Material.COMPOSITE) return;
        Vec3 oc = owner.add(0, 1.18, 0);
        float oa = NarukamiTimeline21.plateau(f, 0, .20F, 3.9F, 5.8F);
        for (int i = 0; i < 34 && oa > .001F; i++) {
            float birth = hash01(seed + 4000 + i * 17) * 1.15F, age = f - birth, life = 2.8F + hash01(seed + 4040 + i * 19) * 2.5F,
                    fade = 1 - NarukamiTimeline21.smooth(age, life * .5F, life);
            if (age <= 0 || fade <= .001F) continue;
            boolean dark = i % 9 == 0;
            if ((dark && p != Material.COMPOSITE) || (!dark && p != Material.PARTICLE)) continue;
            double angle = hash01(seed + 4080 + i * 23) * TAU, speed = .22 + hash01(seed + 4120 + i * 29) * .38,
                    vs = (hash01(seed + 4160 + i * 31) - .42) * .42;
            Vec3 pos = oc.add(q.right.scale(Math.cos(angle) * speed * age)).add(q.forward.scale(Math.sin(angle) * speed * .74 * age))
                    .add(0, vs * age - age * age * .006, 0);
            float size = .025F + i % 5 * .005F;
            NarukamiGeometry21.billboard(m, b, pos, camera, size, size * (1.55F + i % 3 * .24F), (float) angle + age * .22F,
                    dark ? DEEP_PURPLE : (i % 4 == 0 ? PALE : VIOLET), oa * NarukamiTimeline21.smooth(age, 0, .18F) * fade * (.68F + .32F * sine2(f * 3.5F + i)));
        }
        float ca = NarukamiTimeline21.plateau(f, 4.65F, 5.25F, 14, 16.7F);
        Vec3 cc = target.add(q.forward.scale(crossTravel(f)));
        NarukamiGeometry21.Curve c0 = crossCurve(cc, q, false), c1 = crossCurve(cc, q, true);
        float h0 = NarukamiTimeline21.smooth(f, 4.8F, 6.85F), h1 = NarukamiTimeline21.smooth(f, 5.35F, 7.45F),
                t0 = .9F * NarukamiTimeline21.smooth(f, 11.8F, 15.8F), t1 = .9F * NarukamiTimeline21.smooth(f, 12.2F, 16);
        for (int i = 0; i < 44 && ca > .001F; i++) {
            if (p != Material.PARTICLE) break;
            boolean rev = (i & 1) != 0;
            float u = hash01(seed + 4300 + i * 23), head = rev ? h1 : h0, tail = rev ? t1 : t0;
            if (u > head || u < tail) continue;
            float birth = 4.75F + hash01(seed + 4340 + i * 29) * 4.2F, age = f - birth, life = 4.5F + hash01(seed + 4380 + i * 31) * 4,
                    fade = 1 - NarukamiTimeline21.smooth(age, life * .45F, life);
            if (age <= 0 || fade <= .001F) continue;
            Vec3 pos = (rev ? c1 : c0).point(u).subtract(q.forward.scale(age * (.055 + i % 4 * .012)))
                    .add(q.right.scale((hash01(seed + 4420 + i * 37) - .5) * .22))
                    .add(0, (hash01(seed + 4460 + i * 41) - .36) * .055 * age, 0);
            float size = .018F + i % 6 * .004F;
            NarukamiGeometry21.billboard(m, b, pos, camera, size, size * (1.45F + i % 3 * .25F), age * (.32F + i % 5 * .08F),
                    i % 6 == 0 ? PALE : (i % 3 == 0 ? PINK : VIOLET), ca * fade * (.52F + .48F * sine2(f * 3 + i * .8F)));
        }
        float cageA = NarukamiTimeline21.plateau(f, 13.9F, 14.7F, 25.8F, 29.2F);
        Vec3 cage = owner.add(0, 1.58, 0).subtract(q.forward.scale(.05));
        for (int i = 0; i < 38 && cageA > .001F; i++) {
            if (p != Material.PARTICLE) break;
            float birth = 14.2F + hash01(seed + 4800 + i * 17) * 10.2F, age = f - birth, life = 3.8F + hash01(seed + 4840 + i * 19) * 4.8F,
                    fade = 1 - NarukamiTimeline21.smooth(age, life * .48F, life);
            if (age <= 0 || fade <= .001F) continue;
            double angle = hash01(seed + 4880 + i * 23) * TAU, radius = 2.35 + hash01(seed + 4920 + i * 29) * 1.35;
            Vec3 radial = q.right.scale(Math.cos(angle)).add(q.forward.scale(Math.sin(angle) * .68)),
                    pos = cage.add(radial.scale(radius + age * (.018 + i % 4 * .006))).add(0, (hash01(seed + 4960 + i * 31) - .5) * 2.25 + age * .018, 0);
            float size = .020F + i % 5 * .004F;
            NarukamiGeometry21.billboard(m, b, pos, camera, size, size * (1.25F + i % 4 * .22F), age * (.25F + i % 6 * .07F),
                    i % 8 == 0 ? PALE : (i % 3 == 0 ? PINK : VIOLET), cageA * fade * (.46F + .54F * sine2(f * 3.2F + i)));
        }
        float collapse = NarukamiTimeline21.plateau(f, 19.8F, 20.8F, 29, 34.2F);
        Vec3 g = owner.add(0, .055, 0);
        for (int i = 0; i < 28 && collapse > .001F; i++) {
            float birth = 20 + hash01(seed + 5200 + i * 17) * 4.8F, age = f - birth, life = 6.5F + hash01(seed + 5240 + i * 19) * 6.2F,
                    fade = 1 - NarukamiTimeline21.smooth(age, life * .5F, life);
            if (age <= 0 || fade <= .001F) continue;
            boolean dark = i % 9 == 0;
            if ((dark && p != Material.COMPOSITE) || (!dark && p != Material.PARTICLE)) continue;
            double angle = hash01(seed + 5280 + i * 23) * TAU, radius = 1.3 + hash01(seed + 5320 + i * 29) * 2.2;
            Vec3 radial = q.right.scale(Math.cos(angle)).add(q.forward.scale(Math.sin(angle) * .74)),
                    pos = g.add(radial.scale(radius + age * .075)).add(0, .08 + age * .045 - age * age * .004, 0);
            float size = .024F + i % 5 * .005F;
            NarukamiGeometry21.billboard(m, b, pos, camera, size, size * (1.35F + i % 3 * .28F), (float) angle + age * .24F,
                    dark ? BLACK_PURPLE : (i % 5 == 0 ? PALE : VIOLET), collapse * fade * (.54F + .46F * sine2(f * 2.8F + i * 1.1F)));
        }
    }

    private static void residue(Matrix4f m, VertexConsumer b, Material p, float f, Vec3 owner, Basis q, Vec3 camera, long seed) {
        if (p != Material.PARTICLE && p != Material.COMPOSITE) return;
        float a = NarukamiTimeline21.plateau(f, 13.6F, 15, 32, 39);
        for (int i = 0; i < 72; i++) {
            float birth = 13.8F + hash01(seed + 3100 + i * 17) * 12, life = 6 + hash01(seed + 3140 + i * 19) * 11,
                    age = f - birth, fade = 1 - NarukamiTimeline21.smooth(age, life * .55F, life);
            if (age <= 0 || fade <= .001F) continue;
            boolean dark = i % 7 == 0;
            if ((dark && p != Material.COMPOSITE) || (!dark && p != Material.PARTICLE)) continue;
            double angle = hash01(seed + 3180 + i * 23) * TAU, radius = .5 + hash01(seed + 3220 + i * 29) * 3.4;
            Vec3 origin = owner.add(q.right.scale(Math.cos(angle) * radius)).add(q.forward.scale(Math.sin(angle) * radius * .70))
                    .add(0, hash01(seed + 3260 + i * 31) * 1.8, 0),
                    pos = origin.add(q.right.scale((hash01(seed + 3300 + i * 37) - .5) * age * .08))
                            .add(q.forward.scale((hash01(seed + 3340 + i * 41) - .5) * age * .07))
                            .add(0, age * (.025 + i % 5 * .007) - age * age * .0025, 0);
            float size = .025F + i % 6 * .004F;
            NarukamiGeometry21.billboard(m, b, pos, camera, size, size * (1.2F + i % 4 * .28F), age * (.22F + i % 5 * .07F),
                    dark ? BLACK_PURPLE : (i % 5 == 0 ? PALE : VIOLET), a * fade * (.58F + .42F * sine2(f * 2.4F + i)));
        }
    }

    private static void pierce(Matrix4f m, VertexConsumer b, Material p, float f, NarukamiTimeline21.Cue cue, NarukamiGeometry21.Curve seedCurve,
                               NarukamiGeometry21.Curve impact, NarukamiGeometry21.Curve handoff, Vec3 camera, long seed, float width, NarukamiTimeline21.Cue dominant) {
        float approach = NarukamiTimeline21.smooth(f, cue.start, cue.impact), depart = NarukamiTimeline21.smooth(f, cue.handoff, cue.release);
        NarukamiGeometry21.Curve curve = u -> seedCurve.point(u)
                .add(impact.point(u).subtract(seedCurve.point(u)).scale(approach))
                .add(handoff.point(u).subtract(seedCurve.point(u)
                        .add(impact.point(u).subtract(seedCurve.point(u)).scale(approach))).scale(depart));
        float pre = NarukamiTimeline21.precursor(cue, f);
        if (pre > .001F && p == Material.CROSS)
            NarukamiGeometry21.ribbon(m, b, seedCurve, 56, NarukamiTimeline21.reveal(cue, f), 0, width * .35F, camera, PURPLE, pre, .20F, seed - 7, f);
        single(m, b, p, f, cue, curve, camera, seed, width, dominant);
        float wake = NarukamiTimeline21.wake(cue, f);
        if (wake > .001F && p == Material.COMPOSITE)
            NarukamiGeometry21.ribbon(m, b, handoff, 56, 1, NarukamiTimeline21.exit(cue, f), width * .58F, camera, DEEP_PURPLE, wake, .42F, seed + 9, f + 2);
    }
    private static void single(Matrix4f m, VertexConsumer b, Material p, float f, NarukamiTimeline21.Cue cue, NarukamiGeometry21.Curve curve,
                               Vec3 camera, long seed, float width, NarukamiTimeline21.Cue dominant) {
        float a = NarukamiTimeline21.envelope(cue, f);
        if (a <= .001F) return;
        float head = NarukamiTimeline21.reveal(cue, f), tail = NarukamiTimeline21.exit(cue, f), er = NarukamiTimeline21.smooth(f, cue.release, cue.end);
        if (p == Material.COMPOSITE) NarukamiGeometry21.ribbon(m, b, curve, 64, head, tail, width * 1.70F, camera, BLACK_PURPLE, a * .62F, er * .40F, seed, f);
        if (p == Material.CROSS) NarukamiGeometry21.ribbon(m, b, curve, 64, head, tail * .95F, width, camera, VIOLET, a * .94F, er * .60F, seed + 1, f + 1.1F);
        if (p == Material.ENERGY) NarukamiGeometry21.ribbon(m, b, curve, 64, head, tail * .88F, width * (dominant == cue ? .24F : .12F), camera,
                dominant == cue ? WHITE : PINK, a * (dominant == cue ? 1 : .55F), er * .76F, seed + 2, f + 2.2F);
    }
    private static void track(Matrix4f m, VertexConsumer b, Material p, float f, NarukamiTimeline21.Cue cue, NarukamiGeometry21.Curve curve,
                              Vec3 camera, long seed, float width) {
        float a = NarukamiTimeline21.envelope(cue, f);
        if (a <= .001F) return;
        float head = NarukamiTimeline21.reveal(cue, f), tail = NarukamiTimeline21.exit(cue, f), er = .25F + NarukamiTimeline21.smooth(f, cue.release, cue.end) * .50F;
        if (p == Material.COMPOSITE) NarukamiGeometry21.ribbon(m, b, curve, 56, head, tail, width * 1.50F, camera, BLACK_PURPLE, a * .48F, er * .52F, seed, f);
        if (p == Material.CROSS) NarukamiGeometry21.ribbon(m, b, curve, 56, head, tail * .93F, width, camera, PURPLE, a * .80F, er * .72F, seed + 1, f + 1.7F);
    }
    private static void slashLayers(Matrix4f m, VertexConsumer b, Material p, NarukamiGeometry21.Curve curve, float head, float tail,
                                    float alpha, float erode, Vec3 camera, long seed, float f, float width, boolean white) {
        if (p == Material.COMPOSITE) NarukamiGeometry21.ribbon(m, b, curve, 72, head, tail, width * 1.75F, camera, BLACK_PURPLE, alpha * .66F, erode * .38F, seed, f);
        if (p == Material.CROSS) NarukamiGeometry21.ribbon(m, b, curve, 72, head, tail * .95F, width, camera, VIOLET, alpha * .96F, erode * .60F, seed + 1, f + 1.4F);
        if (p == Material.ENERGY) NarukamiGeometry21.ribbon(m, b, curve, 72, head, tail * .86F, width * .22F, camera, white ? WHITE : PALE, alpha, erode * .78F, seed + 2, f + 2.8F);
    }
    private static void crossEcho(Matrix4f m, VertexConsumer b, Material p, NarukamiGeometry21.Curve curve, float head, float tail,
                                  float alpha, float erode, Vec3 camera, long seed, float f, float width) {
        float ma = alpha * (.18F + .12F * head);
        if (p == Material.COMPOSITE) NarukamiGeometry21.ribbon(m, b, curve, 64, head, tail, width * 1.42F, camera, BLACK_PURPLE, ma, .28F + erode * .48F, seed, f);
        if (p == Material.CROSS) NarukamiGeometry21.ribbon(m, b, curve, 64, head, tail * .92F, width * .68F, camera, PURPLE, ma * 1.25F, .42F + erode * .46F, seed + 1, f + 1.3F);
    }

    private static float crossTravel(float f) {
        float t = Mth.clamp((f - 4.35F) / (16.20F - 4.35F), 0, 1), inv = 1 - t, launch = 1 - inv * inv * inv;
        return -.30F + launch * 2.35F + t * 1.35F;
    }
    private static NarukamiGeometry21.Curve crossCurve(final Vec3 c, final Basis q, final boolean reverse) {
        return u -> local(c, q, (u - .5) * 9.8, (u - .5) * 5.6 * (reverse ? -1 : 1), Math.sin(Math.PI * u) * (reverse ? .46 : .38));
    }
    private static NarukamiGeometry21.Curve axis(final Vec3 c, final Basis q, final double x0, final double y0, final double z0,
                                                 final double x1, final double y1, final double z1, final double xb, final double zb) {
        return u -> { double bow = Math.sin(Math.PI * u); return local(c, q, x0 + (x1 - x0) * u + bow * xb, y0 + (y1 - y0) * u, z0 + (z1 - z0) * u + bow * zb); };
    }
    private static NarukamiGeometry21.Curve orbit(final Vec3 c, final Basis q, final double start, final double end, final double rx, final double rz,
                                                  final double sy, final double ey, final double hb, final double bulge, final double twist) {
        return u -> { double angle = Math.toRadians(start + (end - start) * u), scale = 1 + Math.sin(Math.PI * u) * bulge;
            return local(c, q, Math.cos(angle) * rx * scale, sy + (ey - sy) * u + Math.sin(Math.PI * u) * hb + Math.sin(Math.PI * 2 * u) * twist, Math.sin(angle) * rz * scale); };
    }
    private static Vec3 local(Vec3 c, Basis q, double x, double y, double z) {
        return c.add(q.right.scale(x)).add(0, y, 0).add(q.forward.scale(z));
    }
    private static Vec3 localVector(Basis q, double x, double y, double z) {
        return q.right.scale(x).add(0, y, 0).add(q.forward.scale(z));
    }
    private static float groundPulse(float f) {
        float[] a = { 7.1F, 9, 16.05F, 18.55F, 19.20F, 20, 22.45F };
        float p = 0;
        for (float x : a) p = Math.max(p, NarukamiTimeline21.gaussian(f, x, .48F));
        return p;
    }
    private static float sine2(float v) { float s = Mth.sin(v); return s * s; }
}