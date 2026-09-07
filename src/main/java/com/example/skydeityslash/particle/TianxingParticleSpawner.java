package com.example.skydeityslash.particle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 天星视觉效果的原版粒子（复刻 FDBossesTianxingVisualBridge 的原版回退逻辑）。
 * 仅客户端调用（天星实体在 isClientSide 分支里驱动），全程使用原版粒子。
 */
public final class TianxingParticleSpawner {
    private TianxingParticleSpawner() {
    }

    /** 飞行拖尾：沿反向轨迹拖出灼热余烬（FLAME/LAVA），无黑色烟尘。 */
    public static void flightTrail(Level level, Entity projectile) {
        Vec3 movement = projectile.getDeltaMovement();
        double distance = movement.length();
        if (distance <= 1.0E-5) {
            return;
        }
        Vec3 back = movement.scale(-1.0 / distance);
        for (int k = 0; k < 4; k++) {
            double off = 1.2 + level.random.nextDouble() * 2.0;
            double x = projectile.getX() + back.x * off + (level.random.nextDouble() - 0.5) * 1.0;
            double y = projectile.getY() + back.y * off + (level.random.nextDouble() - 0.5) * 1.0;
            double z = projectile.getZ() + back.z * off + (level.random.nextDouble() - 0.5) * 1.0;
            ParticleOptions ember = ParticleTypes.FLAME;
            level.addParticle(ember, true, x, y, z, 0.0, 0.0, 0.0);
        }
    }

    /** 落地爆炸：大爆炸 + 火柱/火星/火焰余烬 + 土石飞溅，无黑色烟尘。 */
    public static void impact(Level level, Vec3 pos) {
        level.addParticle(ParticleTypes.EXPLOSION, true, pos.x, pos.y + 0.5, pos.z, 0.0, 0.0, 0.0);
        // 火柱：上升火焰（橙红）
        for (int i = 0; i < 90; i++) {
            double vx = level.random.nextGaussian() * 0.6;
            double vy = Math.abs(level.random.nextGaussian()) * 0.55 + 0.15;
            double vz = level.random.nextGaussian() * 0.6;
            level.addParticle(ParticleTypes.FLAME, true, pos.x, pos.y, pos.z, vx, vy, vz);
        }
        // 灼热火星：橙亮四散（纯火焰色，无黑色）
        for (int i = 0; i < 60; i++) {
            double ang = level.random.nextDouble() * Math.PI * 2.0;
            double hs = 0.3 + level.random.nextDouble() * 0.9;
            level.addParticle(ParticleTypes.FLAME, true, pos.x, pos.y, pos.z,
                    Math.cos(ang) * hs, level.random.nextDouble() * 0.9, Math.sin(ang) * hs);
        }
        // 土石飞溅
        BlockPos center = BlockPos.containing(pos);
        BlockState state = level.getBlockState(center.below());
        if (!state.isAir()) {
            for (int i = 0; i <= 48; i++) {
                float angle = (float) (i * 0.1308997f) + (level.random.nextFloat() * 2.0f - 1.0f) * 0.065f;
                double dx = Math.cos(angle);
                double dz = Math.sin(angle);
                for (int row = 1; row <= 3; row++) {
                    double speed = 0.45 * row + (level.random.nextFloat() * 2.0f - 1.0f) * 0.25;
                    double vs = 0.3 * row - level.random.nextFloat() * 0.12;
                    level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), true,
                            pos.x, pos.y, pos.z, dx * speed, vs, dz * speed);
                }
            }
        }
        // 火焰余烬：全 FLAME（无 LAVA 黑色斑点、无 SMOKE 黑烟）
        for (int i = 0; i < 220; i++) {
            double vy = level.random.nextDouble();
            double hs = 0.2 + level.random.nextDouble() * 0.75;
            double ang = level.random.nextDouble() * Math.PI * 2.0;
            level.addParticle(ParticleTypes.FLAME, true, pos.x, pos.y, pos.z, Math.cos(ang) * hs, vy, Math.sin(ang) * hs);
        }
        // 火焰扩散
        for (int i = 0; i < 90; i++) {
            double ang = level.random.nextDouble() * Math.PI * 2.0;
            double hs = 0.9 + level.random.nextDouble() * 1.6;
            level.addParticle(ParticleTypes.FLAME, true, pos.x, pos.y, pos.z,
                    Math.cos(ang) * hs, level.random.nextDouble() * Math.max(0.1, 1.5 - hs), Math.sin(ang) * hs);
        }
        level.addParticle(ParticleTypes.EXPLOSION, true, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0);
    }
}
