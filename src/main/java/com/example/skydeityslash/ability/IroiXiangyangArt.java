package com.example.skydeityslash.ability;

import com.example.skydeityslash.SkydeitySlash;
import mods.flammpfeil.slashblade.SlashBlade.RegistryEvents;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * 「向阳」九千五百万年前的分歧 —— iroi 专属剑技（SA）。
 * 释放后在原地迸发一团聚集的白云（羊毛白粒子，细拖尾），
 * 前方命中生物受到 50% 最大生命真伤；同时从目标上方召唤 16 把幻影剑扎落，并带小幅突进。
 */
public class IroiXiangyangArt {

    /** 幻影剑颜色：iroi 主题粉紫（与 SE40/SE50 剑气一致） */
    public static final int PHANTOM_COLOR = 0xFF55FF;

    public static void doIroiXiangyang(LivingEntity user) {
        if (!(user instanceof ServerPlayer player)) return;
        Level level = player.level();
        if (level.isClientSide) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        Vec3 dir = player.getLookAngle().normalize();
        Vec3 origin = player.position().add(0.0, 1.2, 0.0);

        // 1. 前方命中生物：50% 最大生命真伤（复用 applyTrueDamage）
        AABB box = new AABB(origin, origin).inflate(6.0);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && !e.isSpectator());
        for (LivingEntity target : targets) {
            Vec3 to = target.position().subtract(player.position());
            if (to.dot(dir) < 0) continue;
            if (to.lengthSqr() > 36) continue;
            SkydeitySlash.GameEvents.applyTrueDamage(target, player, target.getMaxHealth() * 0.5f);
        }

        // 2. 释放点一团聚集白云 + 沿身体后方细细的拖尾
        spawnWhiteCluster(serverLevel, origin, dir);

        // 3. 从目标上方召唤 16 把库默认色幻影剑，放慢速度徐徐扎落（浓一点、慢一点，便于看清）
        spawnPhantomSwords(player, level, origin);

        // 小幅突进
        player.setDeltaMovement(player.getDeltaMovement().add(dir.x * 0.5, 0.1, dir.z * 0.5));
    }

    /** 一团粗大的白色聚团往前飞，身后拖着细细的拖尾（慢速推进 8 格，粒子浓可直视） */
    private static void spawnWhiteCluster(ServerLevel serverLevel, Vec3 origin, Vec3 dir) {
        Vector3f white = new Vector3f(1.0f, 1.0f, 1.0f);
        int steps = 20;               // 慢速：每 2 tick 前进一步，总推进 8 格
        for (int i = 0; i < steps; i++) {
            final int idx = i;
            serverLevel.getServer().tell(new TickTask(
                    serverLevel.getServer().getTickCount() + idx * 2,
                    () -> {
                        Vec3 pos = origin.add(dir.scale(idx * 0.4));
                        // 粗大聚团：一小团浓密的白粒（尺寸较大、紧密聚集，肉眼清晰可见）
                        for (int k = 0; k < 12; k++) {
                            double ox = pos.x + (serverLevel.random.nextDouble() - 0.5) * 0.55;
                            double oy = pos.y + (serverLevel.random.nextDouble() - 0.5) * 0.4;
                            double oz = pos.z + (serverLevel.random.nextDouble() - 0.5) * 0.55;
                            serverLevel.sendParticles(new DustParticleOptions(white, 0.35f),
                                    ox, oy, oz, 1, 0, 0, 0, 0.02);
                        }
                        // 身后细拖尾：两粒更细小的白粒
                        for (int t = 1; t <= 2; t++) {
                            Vec3 back = pos.subtract(dir.scale(0.35 * t));
                            serverLevel.sendParticles(new DustParticleOptions(white, 0.10f),
                                    back.x, back.y, back.z, 1, 0, 0, 0, 0.004);
                        }
                    }));
        }
    }

    /** 从目标/准星上方召唤 16 把幻影剑扎落（与胡桃幻影剑相同的模组基础召唤剑，保证可见；每把 52 伤） */
    private static void spawnPhantomSwords(ServerPlayer player, Level level, Vec3 origin) {
        ISlashBladeState state = player.getMainHandItem().getCapability(CapabilitySlashBlade.BLADESTATE, null).orElse(null);
        Entity tgt = state != null ? state.getTargetEntity(level) : null;
        final Vec3 aim;
        if (tgt instanceof LivingEntity tl && tl.isAlive() && !tl.isRemoved()) {
            aim = tl.getBoundingBox().getCenter();
        } else {
            aim = origin.add(player.getLookAngle().normalize().scale(6.0));
        }
        for (int i = 0; i < 16; i++) {
            double ang = (i / 16.0) * Math.PI * 2.0;
            Vec3 start = aim.add(Math.cos(ang) * 2.8, 6.0, Math.sin(ang) * 2.8);
            EntityAbstractSummonedSword sword = new EntityAbstractSummonedSword(RegistryEvents.SummonedSword, level);
            sword.setPos(start);
            sword.setShooter(player);
            sword.setColor(PHANTOM_COLOR);
            sword.setRoll(0.0F);
            sword.setDamage(52.0);
            Vec3 aimDir = aim.subtract(start);
            sword.shoot(aimDir.x, aimDir.y, aimDir.z, 1.2F, 0.0F);
            level.addFreshEntity(sword);
        }
    }
}