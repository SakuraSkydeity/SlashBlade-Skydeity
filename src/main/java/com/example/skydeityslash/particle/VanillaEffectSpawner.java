package com.example.skydeityslash.particle;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 纯原版粒子特效生成（frostflourish / frost_nova），服务端一次性发射。
 */
public class VanillaEffectSpawner {
    /**
     * frostflourish：24 条弧形光臂螺旋迸发（原版 dust 渐变蓝→青→薄荷绿）+ 中心电火花闪光。
     * 数量更密、速度更慢，并叠加 glow 余辉与 cloud 淡烟，让整体缓慢消散。
     */
    public static void frostFlourish(ServerLevel level, Vec3 origin) {
        RandomSource rand = level.getRandom();
        // 中心闪光：密集电火花，缓慢向外飘
        for (int i = 0; i < 56; i++) {
            double a = rand.nextDouble() * Math.PI * 2;
            double r = 0.05 + rand.nextDouble() * 0.4;
            double px = origin.x + r * Math.cos(a);
            double pz = origin.z + r * Math.sin(a);
            double py = origin.y + (rand.nextDouble() - 0.5) * 0.35;
            double sp = 0.07 + rand.nextDouble() * 0.10;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, px, py, pz, 0,
                    sp * Math.cos(a), (rand.nextDouble() - 0.5) * 0.06, sp * Math.sin(a), 1.0);
        }
        // 螺旋光臂：24 臂 × 9 点，速度放缓，保持悬浮感
        for (int i = 0; i < 24; i++) {
            double baseAngle = i * (Math.PI * 2) / 24 + (rand.nextDouble() - 0.5) * 0.10;
            for (int k = 1; k <= 9; k++) {
                double t = k / 9.0;
                double radius = 3.2 * t;
                double angle = baseAngle + 1.15 * t;
                double px = origin.x + radius * Math.cos(angle);
                double pz = origin.z + radius * Math.sin(angle);
                double py = origin.y + (rand.nextDouble() - 0.5) * 0.16;

                double radX = Math.cos(angle), radZ = Math.sin(angle);
                double tanX = -Math.sin(angle), tanZ = Math.cos(angle);
                double speed = 0.19 * (1.0 - 0.4 * t);
                double vx = speed * (radX + 0.5 * tanX);
                double vz = speed * (radZ + 0.5 * tanZ);
                double vy = (rand.nextDouble() - 0.5) * 0.04;

                level.sendParticles(new DustParticleOptions(gradient(t), 0.45f),
                        px, py, pz, 0, vx, vy, vz, 1.0);
                // 每 2 点补一粒 glow 余辉：寿命长、缓慢淡出
                if (k % 2 == 0) {
                    level.sendParticles(ParticleTypes.GLOW, px, py, pz, 0,
                            vx * 0.3, vy * 0.3, vz * 0.3, 1.0);
                }
            }
        }
        // 中心淡烟：cloud 缓慢扩散，令爆裂后残留更久
        for (int i = 0; i < 18; i++) {
            double a = rand.nextDouble() * Math.PI * 2;
            double r = rand.nextDouble() * 0.8;
            level.sendParticles(ParticleTypes.CLOUD,
                    origin.x + Math.cos(a) * r, origin.y + (rand.nextDouble() - 0.5) * 0.4,
                    origin.z + Math.sin(a) * r, 1,
                    Math.cos(a) * 0.05, 0.03, Math.sin(a) * 0.05, 0.02);
        }
    }

    /**
     * frost_nova：中心白色闪光 + 3 层青色(glow)膨胀环向外扩散 + 最外层 cloud 冲击波。
     */
    public static void frostNova(ServerLevel level, Vec3 origin) {
        RandomSource rand = level.getRandom();
        for (int i = 0; i < 32; i++) {
            double a = rand.nextDouble() * Math.PI * 2;
            double r = 0.05 + rand.nextDouble() * 0.2;
            double px = origin.x + r * Math.cos(a);
            double pz = origin.z + r * Math.sin(a);
            double py = origin.y + (rand.nextDouble() - 0.5) * 0.4;
            double sp = 0.5 + rand.nextDouble() * 0.7;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, px, py, pz, 0,
                    sp * Math.cos(a), (rand.nextDouble() - 0.5) * 0.3, sp * Math.sin(a), 1.0);
        }
        for (int ring = 0; ring < 3; ring++) {
            double radius = 1.2 + ring * 1.1;
            for (int k = 0; k < 36; k++) {
                double angle = k * (Math.PI * 2) / 36;
                double px = origin.x + radius * Math.cos(angle);
                double pz = origin.z + radius * Math.sin(angle);
                double py = origin.y + (rand.nextDouble() - 0.5) * 0.25;
                double out = 0.55 + ring * 0.38;
                level.sendParticles(ParticleTypes.GLOW, px, py, pz, 0,
                        out * Math.cos(angle), (rand.nextDouble() - 0.5) * 0.08, out * Math.sin(angle), 1.0);
            }
        }
        for (int k = 0; k < 30; k++) {
            double angle = k * (Math.PI * 2) / 30;
            double px = origin.x + 3.0 * Math.cos(angle);
            double pz = origin.z + 3.0 * Math.sin(angle);
            double py = origin.y + 0.3 + (rand.nextDouble() - 0.5) * 0.4;
            level.sendParticles(ParticleTypes.CLOUD, px, py, pz, 0,
                    0.8 * Math.cos(angle), 0, 0.8 * Math.sin(angle), 1.0);
        }
    }

    private static Vector3f gradient(double t) {
        double[] stops = {0.00, 0.18, 0.45, 0.70, 1.00};
        float[][] colors = {
                {0.92f, 0.95f, 1.00f},  // 近白
                {0.10f, 0.16f, 0.38f},  // 藏青
                {0.16f, 0.36f, 0.78f},  // 中蓝
                {0.18f, 0.75f, 0.82f},  // 青
                {0.55f, 0.94f, 0.75f}   // 薄荷绿
        };
        int idx = 0;
        while (idx < stops.length - 2 && t > stops[idx + 1]) idx++;
        double seg = (t - stops[idx]) / (stops[idx + 1] - stops[idx]);
        if (seg < 0) seg = 0;
        if (seg > 1) seg = 1;
        float[] a = colors[idx], b = colors[idx + 1];
        return new Vector3f(
                a[0] + (b[0] - a[0]) * (float) seg,
                a[1] + (b[1] - a[1]) * (float) seg,
                a[2] + (b[2] - a[2]) * (float) seg);
    }
}
