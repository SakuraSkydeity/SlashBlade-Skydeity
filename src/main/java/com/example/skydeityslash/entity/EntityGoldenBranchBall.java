package com.example.skydeityslash.entity;

import com.example.skydeityslash.registry.ModEntities;
import com.example.skydeityslash.SkydeitySlash;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * 「诺德卡莱」霜结的誓金枝 —— 金色誓金枝球砸落实体。
 * 阶段一：从目标点上方坠落（金色球体 + 拖尾粒子）；
 * 阶段二：落地冲击，金黄/橙黄爆炸伤害 + 扩散金环 + 上升火花 + 金色枝冠。
 */
public class EntityGoldenBranchBall extends Entity {
    public static final int FALL_MAX = 70;
    public static final int IMPACT_LIFE = 24;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(
            EntityGoldenBranchBall.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> TARGET_X = SynchedEntityData.defineId(
            EntityGoldenBranchBall.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_Y = SynchedEntityData.defineId(
            EntityGoldenBranchBall.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_Z = SynchedEntityData.defineId(
            EntityGoldenBranchBall.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(
            EntityGoldenBranchBall.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> IMPACTED = SynchedEntityData.defineId(
            EntityGoldenBranchBall.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> IMPACT_TICK = SynchedEntityData.defineId(
            EntityGoldenBranchBall.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET_ENTITY = SynchedEntityData.defineId(
            EntityGoldenBranchBall.class, EntityDataSerializers.INT);

    public EntityGoldenBranchBall(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public static EntityGoldenBranchBall spawn(Level level, LivingEntity owner, Vec3 target, float damage, int targetEntityId) {
        EntityGoldenBranchBall e = new EntityGoldenBranchBall(ModEntities.GOLDEN_BRANCH_BALL.get(), level);
        e.setPos(target.x, target.y + 5.0, target.z);
        e.entityData.set(OWNER_ID, owner.getId());
        e.entityData.set(TARGET_X, (float) target.x);
        e.entityData.set(TARGET_Y, (float) target.y);
        e.entityData.set(TARGET_Z, (float) target.z);
        e.entityData.set(DAMAGE, damage);
        e.entityData.set(IMPACTED, false);
        e.entityData.set(IMPACT_TICK, -1);
        e.entityData.set(TARGET_ENTITY, targetEntityId);
        level.addFreshEntity(e);
        return e;
    }

    public Vec3 getTarget() {
        return new Vec3(entityData.get(TARGET_X), entityData.get(TARGET_Y), entityData.get(TARGET_Z));
    }
    public float getBaseDamage() { return entityData.get(DAMAGE); }
    public boolean isImpacted() { return entityData.get(IMPACTED); }
    public int getImpactAge() {
        int start = entityData.get(IMPACT_TICK);
        return start < 0 ? 0 : tickCount - start;
    }
    public LivingEntity getOwner() {
        Entity e = level().getEntity(entityData.get(OWNER_ID));
        return e instanceof LivingEntity le ? le : null;
    }
    /** 被锁定的目标实体（shift 锁定时传入）；无效则返回 null，球只朝固定落点砸下 */
    public LivingEntity getTrackedTarget() {
        int id = entityData.get(TARGET_ENTITY);
        if (id < 0) return null;
        Entity e = level().getEntity(id);
        return e instanceof LivingEntity le ? le : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        LivingEntity owner = getOwner();
        if (owner == null || !owner.isAlive()) { discard(); return; }
        ServerLevel server = (ServerLevel) level();
        if (!isImpacted()) {
            // 更快：朝锁定目标的实时位置（无锁定则固定落点）以 1.4 格/tick 追踪砸下
            LivingEntity track = getTrackedTarget();
            Vec3 land = getTarget();
            if (track != null && track.isAlive()) land = track.getBoundingBox().getCenter().add(0, -0.2, 0);
            Vec3 to = land.subtract(position());
            double dist2 = to.lengthSqr();
            if (dist2 < 1.5 * 1.5 || tickCount >= FALL_MAX) {
                setPos(land.x, land.y, land.z);
                entityData.set(IMPACTED, true);
                entityData.set(IMPACT_TICK, tickCount);
                doImpact(server);
            } else {
                setDeltaMovement(to.normalize().scale(1.4));
                move(MoverType.SELF, getDeltaMovement());
                spawnFallParticles(server);
            }
        } else {
            if (getImpactAge() >= IMPACT_LIFE) { discard(); return; }
            spawnImpactParticles(server);
        }
    }

    /** 坠落阶段粒子：金色拖尾 + 环绕金环 + 金色火花 */
    private void spawnFallParticles(ServerLevel server) {
        double x = getX(), y = getY(), z = getZ();
        // 拖尾：每帧 2 粒金色粉尘
        server.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.78F, 0.25F), 1.4F),
                x, y - 0.4, z, 2, 0.15, 0.1, 0.15, 0.0);
        // 环绕金环：每 3 帧一圈 8 粒
        if (tickCount % 3 == 0) {
            for (int i = 0; i < 8; i++) {
                double ang = i / 8.0 * Math.PI * 2;
                server.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.4F), 0.6F),
                        x + Math.cos(ang) * 1.65, y, z + Math.sin(ang) * 1.65, 1, 0, 0, 0, 0);
            }
        }
        // 金色火花：每 5 帧 3 粒向上飘
        if (tickCount % 5 == 0) {
            server.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.9F, 0.55F), 0.4F),
                    x, y, z, 3, 0.3, 0.2, 0.3, 0.02);
        }
    }

    private void doImpact(ServerLevel server) {
        Vec3 t = getTarget();
        server.playSound(null, t.x, t.y, t.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7F, 1.2F);
        server.playSound(null, t.x, t.y, t.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8F, 1.4F);
        LivingEntity owner = getOwner();
        AABB box = new AABB(t.x - 4.5, t.y - 2.0, t.z - 4.5, t.x + 4.5, t.y + 2.0, t.z + 4.5);
        List<LivingEntity> list = level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != owner && e.isAlive() && !e.isSpectator());
        for (LivingEntity target : list) {
            if (target.getBoundingBox().getCenter().distanceToSqr(t) > 4.5 * 4.5) continue;
            target.invulnerableTime = 0;
            // 真实伤害：最大生命 50% + 520 固定伤害（无视护甲/抗性/限伤）
            SkydeitySlash.GameEvents.applyTrueDamage(target, owner,
                    target.getMaxHealth() * 0.5f + 520.0f);
            Vec3 push = target.position().subtract(t);
            if (push.lengthSqr() > 1e-6) {
                push = push.normalize().scale(0.55).add(0, 0.35, 0);
                target.setDeltaMovement(target.getDeltaMovement().scale(0.4).add(push));
                target.hasImpulse = true;
            }
        }
        // 鸟群惊飞：12 只金色/月白小鸟沿径向展翅飞离
        for (int i = 0; i < 12; i++) {
            double ang = i / 12.0 * Math.PI * 2 + level().random.nextDouble() * 0.4;
            spawnBird(server, t, Math.cos(ang), Math.sin(ang), 1.0F);
        }
        // 羽毛飘落：更多
        server.sendParticles(new DustParticleOptions(new Vector3f(0.97F, 0.92F, 0.78F), 0.5F),
                t.x, t.y + 0.5, t.z, 30, 0.8, 0.5, 0.8, 0.1);
        // 金色闪光：落地瞬间密集爆发
        server.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.9F, 0.6F), 1.0F),
                t.x, t.y + 0.3, t.z, 25, 0.5, 0.4, 0.5, 0.05);
        // 上升金色火花
        server.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.88F, 0.45F), 0.5F),
                t.x, t.y + 0.3, t.z, 20, 0.6, 0.3, 0.6, 0.3);
        // 金色冲击波环：3 圈扩散金环
        for (int ring = 0; ring < 3; ring++) {
            double rr = 1.5 + ring * 1.8;
            for (int i = 0; i < 12; i++) {
                double ang = i / 12.0 * Math.PI * 2;
                server.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.3F), 0.7F),
                        t.x + Math.cos(ang) * rr, t.y + 0.2, t.z + Math.sin(ang) * rr, 1, 0, 0, 0, 0);
            }
        }
    }

    private void spawnImpactParticles(ServerLevel server) {
        Vec3 t = getTarget();
        int age = getImpactAge();
        // 持续有鸟振翅飞起
        if (age % 3 == 0) {
            double ang = level().random.nextDouble() * Math.PI * 2;
            spawnBird(server, t, Math.cos(ang), Math.sin(ang), 0.8F);
        }
        if (age % 2 == 0) {
            server.sendParticles(new DustParticleOptions(new Vector3f(0.97F, 0.92F, 0.78F), 0.45F),
                    t.x + (level().random.nextDouble() - 0.5) * 1.6, t.y + 0.4,
                    t.z + (level().random.nextDouble() - 0.5) * 1.6, 2, 0.1, 0.25, 0.1, 0.05);
        }
        // 持续上升的金色火花
        if (age % 4 == 0) {
            server.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.4F), 0.45F),
                    t.x + (level().random.nextDouble() - 0.5) * 2.0, t.y + 0.3,
                    t.z + (level().random.nextDouble() - 0.5) * 2.0, 3, 0.1, 0.35, 0.1, 0.05);
        }
    }

    /** 一只鸟 = 金色鸟身 + 月白翅尖，沿飞行方向 + 上抛形成展翅弧线 */
    private void spawnBird(ServerLevel server, Vec3 origin, double dirX, double dirZ, float scale) {
        server.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.35F), 0.9F * scale),
                origin.x, origin.y + 0.3, origin.z, 3, dirX * 0.35, 0.45, dirZ * 0.35, 0.9);
        server.sendParticles(new DustParticleOptions(new Vector3f(0.96F, 0.96F, 0.9F), 0.55F * scale),
                origin.x + dirX * 0.4, origin.y + 0.5, origin.z + dirZ * 0.4, 2, dirX * 0.5, 0.3, dirZ * 0.5, 0.7);
    }

    @Override public boolean isPickable() { return false; }
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(8); }
    @Override protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
        this.entityData.define(TARGET_X, 0F); this.entityData.define(TARGET_Y, 0F); this.entityData.define(TARGET_Z, 0F);
        this.entityData.define(DAMAGE, 1.0F);
        this.entityData.define(IMPACTED, false);
        this.entityData.define(IMPACT_TICK, -1);
        this.entityData.define(TARGET_ENTITY, -1);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(OWNER_ID, tag.getInt("OwnerId"));
        entityData.set(TARGET_X, tag.getFloat("Tx")); entityData.set(TARGET_Y, tag.getFloat("Ty")); entityData.set(TARGET_Z, tag.getFloat("Tz"));
        entityData.set(DAMAGE, tag.getFloat("Damage"));
        entityData.set(IMPACTED, tag.getBoolean("Impacted"));
        entityData.set(IMPACT_TICK, tag.getInt("ImpactTick"));
        entityData.set(TARGET_ENTITY, tag.getInt("TargetEntity"));
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("OwnerId", entityData.get(OWNER_ID));
        tag.putFloat("Tx", entityData.get(TARGET_X)); tag.putFloat("Ty", entityData.get(TARGET_Y)); tag.putFloat("Tz", entityData.get(TARGET_Z));
        tag.putFloat("Damage", getBaseDamage());
        tag.putBoolean("Impacted", isImpacted());
        tag.putInt("ImpactTick", entityData.get(IMPACT_TICK));
        tag.putInt("TargetEntity", entityData.get(TARGET_ENTITY));
    }
}
