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

    /** 幻影剑颜色：iroi 主题粉紫（比原 0xFF55FF 更暗，避免太亮刺眼） */
    public static final int PHANTOM_COLOR = 0x9B2E8C;

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

        // 2. 白色旋转光线：一条白线绕圆心旋转，同时整体向前推进
        spawnWhiteSpiral(serverLevel, origin, dir);

        // 3. 从目标上方召唤 16 把库默认色幻影剑，放慢速度徐徐扎落（浓一点、慢一点，便于看清）
        spawnPhantomSwords(player, serverLevel, origin);

        // 小幅突进
        player.setDeltaMovement(player.getDeltaMovement().add(dir.x * 0.5, 0.1, dir.z * 0.5));
    }

    /** 白色粒子绕一个圆心做圆周运动，同时整体向前移动（圆圈自旋前进，已调慢便于看清） */
    private static void spawnWhiteSpiral(ServerLevel serverLevel, Vec3 origin, Vec3 dir) {
        Vector3f white = new Vector3f(1.0f, 1.0f, 1.0f);
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 cross = up.cross(dir);
        Vec3 p1 = (cross.lengthSqr() < 0.0001 ? new Vec3(1, 0, 0) : cross).normalize();
        Vec3 p2 = p1.cross(dir).normalize();
        int steps = 30;                       // 每 6 tick 一步，慢速推进约 6.6 格（全程约 9 秒，可看清）
        double radius = 1.2;                  // 绕心圆周半径
        int ringPts = 20;                     // 每个圆环的粒子数，密一点更清晰
        double rotPerStep = Math.PI * 2.0 / 15.0; // 每步转 1/15 圈，全程约 2 圈，旋转明显且缓慢
        double fwd = 0.22;                    // 每步保持圆周运动圆心前进 0.22 格
        for (int i = 0; i < steps; i++) {
            final int idx = i;
            serverLevel.getServer().tell(new TickTask(
                    serverLevel.getServer().getTickCount() + idx * 6,
                    () -> {
                        Vec3 center = origin.add(dir.scale(idx * fwd));
                        double base = idx * rotPerStep;   // 整环随步进自旋 = 圆周运动
                        for (int k = 0; k < ringPts; k++) {
                            double th = base + k * (Math.PI * 2.0 / ringPts);
                            Vec3 p = center
                                    .add(p1.scale(Math.cos(th) * radius))
                                    .add(p2.scale(Math.sin(th) * radius));
                            serverLevel.sendParticles(new DustParticleOptions(white, 0.22f),
                                    p.x, p.y, p.z, 1, 0, 0, 0, 0.01);
                        }
                    }));
        }
    }

    /** 从目标/准星上方以圆周一圈生成 16 把幻影剑，各自悬浮等待后再依次扎落（每把间隔 8 tick，每把 52 伤） */
    private static void spawnPhantomSwords(ServerPlayer player, ServerLevel level, Vec3 origin) {
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
            sword.setDelay(i * 8);     // 依次落下：每把悬浮等待 i*8 tick 再发射
            Vec3 aimDir = aim.subtract(start);
            sword.shoot(aimDir.x, aimDir.y, aimDir.z, 1.2F, 0.0F);
            level.addFreshEntity(sword);
        }
    }
}