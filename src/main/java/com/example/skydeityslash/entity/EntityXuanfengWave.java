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

import java.util.List;

/**
 * 「翾风回雪」刀波：划过路径留下白色粒子（逐渐消失），划过区域每帧造成 52 点伤害。
 */
public class EntityXuanfengWave extends EntityDrive {
    /** 是否关闭划过路径的白色粒子拖尾（colum 剑气保留粒子，iroi 十字剑气要求去掉） */
    private boolean noParticles = false;

    public EntityXuanfengWave(EntityType<? extends mods.flammpfeil.slashblade.entity.Projectile> type, Level level) {
        super(type, level);
    }

    /** 关闭白色粒子拖尾后，该刀波仅保留伤害与实体本体（无任何粒子） */
    public void setNoParticles(boolean v) {
        this.noParticles = v;
    }

    @Override
    public void tick() {
        super.tick();
        // 伤害结算只在服务端进行。原来这里没做区分，客户端每 tick 也会
        // 遍历一遍自己这边的实体列表，再对每个命中调一次真伤接口 ——
        // 而真伤接口第一行就要求非客户端，所以客户端那份完全是白跑。
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!noParticles) {
            // 划过路径留下白色粒子拖尾（沿运动方向后方，加厚成多层）
            Vec3 pos = position();
            Vec3 vel = getDeltaMovement();
            for (int i = 0; i < 5; i++) {
                serverLevel.sendParticles(new DustParticleOptions(new Vector3f(1.0f, 1.0f, 1.0f), 1.2f),
                        pos.x - vel.x * i * 1.5, pos.y - vel.y * i * 1.5, pos.z - vel.z * i * 1.5,
                        4, 0.2, 0.2, 0.2, 0);
            }
        }
        AABB aabb = getBoundingBox().inflate(1.0);
        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, aabb,
                e -> e != getShooter() && e.isAlive() && !e.isSpectator());
        for (LivingEntity target : targets) {
            SkydeitySlash.GameEvents.applyTrueDamage(target, getShooter(), 52.0f);
        }
    }
}
