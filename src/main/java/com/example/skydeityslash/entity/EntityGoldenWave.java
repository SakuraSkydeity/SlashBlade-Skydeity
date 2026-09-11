package com.example.skydeityslash.entity;

import com.example.skydeityslash.SkydeitySlash;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

/**
 * linnea 金色剑气波：沿发射方向飞行，每个目标命中一次造成 52 真伤，黄色拖尾，飞出约 10 格后消散。
 */
public class EntityGoldenWave extends EntityDrive {

    /** 金色（黄） */
    private static final int GOLDEN_COLOR = 0xFFE040;
    /** 最大射程（格） */
    private static final double MAX_RANGE = 10.0;

    private Vec3 startPos;
    private final Set<Integer> hitEntities = new HashSet<>();

    public EntityGoldenWave(EntityType<? extends mods.flammpfeil.slashblade.entity.Projectile> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        // 记录起点（首次 tick 时）
        if (startPos == null) startPos = position();

        if (level() instanceof ServerLevel serverLevel) {
            // 黄色粒子拖尾（细，沿运动方向后方松散一列）
            Vec3 pos = position();
            Vec3 vel = getDeltaMovement();
            for (int i = 0; i < 3; i++) {
                serverLevel.sendParticles(new DustParticleOptions(new Vector3f(0.80f, 0.68f, 0.22f), 0.4f),
                        pos.x - vel.x * i, pos.y - vel.y * i, pos.z - vel.z * i,
                        1, 0.08, 0.08, 0.08, 0);
            }

            // 命中：每个目标结算一次 52 真伤
            AABB aabb = getBoundingBox().inflate(0.8);
            for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, aabb,
                    e -> e != getShooter() && e.isAlive() && !e.isSpectator())) {
                if (hitEntities.add(target.getId())) {
                    SkydeitySlash.GameEvents.applyTrueDamage(target, getShooter(), 52.0f);
                }
            }

            // 飞出约 10 格后消散
            Vec3 d = position().subtract(startPos);
            if (d.horizontalDistanceSqr() > MAX_RANGE * MAX_RANGE) {
                discard();
            }
        }
    }
}