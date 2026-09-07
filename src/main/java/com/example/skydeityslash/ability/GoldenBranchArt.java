package com.example.skydeityslash.ability;

import com.example.skydeityslash.entity.EntityGoldenBranchBall;
import com.example.skydeityslash.entity.EntityGoldenWave;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 「诺德卡莱」霜结的誓金枝剑技逻辑：朝准星方向（或骑乘目标）降下金色誓金枝球，
 * 落地时金黄/橙黄爆炸并造成范围伤害。
 */
public class GoldenBranchArt {
    public static void doGoldenBranch(LivingEntity user) {
        if (!(user instanceof ServerPlayer player)) return;
        Level level = player.level();
        if (level.isClientSide) return;

        // 释放时周身金色粒子：地面金环 + 中段金环 + 上升火花
        ServerLevel server = (ServerLevel) level;
        Vec3 p = player.position();
        for (int i = 0; i < 24; i++) {
            double ang = i / 24.0 * Math.PI * 2;
            server.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.4F), 0.8F),
                    p.x + Math.cos(ang) * 1.8, p.y + 0.2, p.z + Math.sin(ang) * 1.8, 1, 0, 0, 0, 0);
        }
        for (int i = 0; i < 16; i++) {
            double ang = i / 16.0 * Math.PI * 2;
            server.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.9F, 0.6F), 0.6F),
                    p.x + Math.cos(ang) * 1.2, p.y + 1.6, p.z + Math.sin(ang) * 1.2, 1, 0, 0, 0, 0);
        }
        server.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.9F, 0.55F), 0.5F),
                p.x, p.y + 0.5, p.z, 30, 1.2, 0.8, 1.2, 0.3);

        Entity target = null;
        ISlashBladeState state = player.getMainHandItem().getCapability(CapabilitySlashBlade.BLADESTATE, null).orElse(null);
        Entity locked = (state != null) ? state.getTargetEntity(level) : null;
        if (locked instanceof LivingEntity le && le.isAlive()) target = locked;
        if (!(target instanceof LivingEntity le2) || !le2.isAlive()) {
            target = null;
            if (player.getVehicle() != null) target = player.getVehicle();
            else {
                var hit = player.pick(30.0D, 1.0F, false);
                if (hit.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) target = ((net.minecraft.world.phys.EntityHitResult) hit).getEntity();
            }
            if (!(target instanceof LivingEntity le3) || !le3.isAlive()) target = null;
        }

        Vec3 center = (target instanceof LivingEntity te)
                ? te.position()
                : player.position().add(player.getLookAngle().x * 6.0, 0, player.getLookAngle().z * 6.0);

        float damage = 30.0F;
        EntityGoldenBranchBall.spawn(level, player, center, damage, target == null ? -1 : target.getId());

        // 新增（不修改上面原有效果）：向四周发射一圈金色剑气波，每道 52 真伤，约 10 格后消失
        spawnGoldenWaves(server, player, p);
    }

    /** 朝四周（水平 16 向）各放出一道金色剑气波，命中一次 52 真伤，飞出约 10 格消散 */
    private static void spawnGoldenWaves(ServerLevel server, ServerPlayer player, Vec3 p) {
        int count = 16;
        float speed = 0.9F;
        Vec3 spawn = p.add(0, 1.0, 0);
        for (int i = 0; i < count; i++) {
            double ang = (i / (double) count) * Math.PI * 2;
            Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
            EntityGoldenWave wave = new EntityGoldenWave(SlashBlade.RegistryEvents.Drive, server);
            wave.setPos(spawn);
            wave.setShooter(player);
            wave.setDamage(0.01);
            wave.setSpeed(speed);
            wave.setBaseSize(0.5f);
            wave.setKnockBack(KnockBacks.cancel);
            wave.setRotationRoll(0.0f);
            server.addFreshEntity(wave);
            wave.shoot(dir.x, 0, dir.z, speed, 0.0f);
        }
    }
}
