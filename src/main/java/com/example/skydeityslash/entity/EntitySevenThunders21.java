package com.example.skydeityslash.entity;

import com.example.skydeityslash.registry.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 鸣雷神 1.21.1 实体（原 1.20KaBlade EntitySevenThunders 忠实移植）。
 * 服务端自驱动时间轴：伤害波次/减速/击退/音效/粒子。
 * 客户端由 RenderSevenThunders21 用 NarukamiRenderLayer21 五材质重建视觉。
 */
public class EntitySevenThunders21 extends Entity {
    public static final int LIFETIME = 54;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(
            EntitySevenThunders21.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(
            EntitySevenThunders21.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(
            EntitySevenThunders21.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_X = SynchedEntityData.defineId(
            EntitySevenThunders21.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Y = SynchedEntityData.defineId(
            EntitySevenThunders21.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Z = SynchedEntityData.defineId(
            EntitySevenThunders21.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_X = SynchedEntityData.defineId(
            EntitySevenThunders21.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_Y = SynchedEntityData.defineId(
            EntitySevenThunders21.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_Z = SynchedEntityData.defineId(
            EntitySevenThunders21.class, EntityDataSerializers.FLOAT);

    public EntitySevenThunders21(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public static EntitySevenThunders21 spawn(Level level, LivingEntity owner, Vec3 targetAnchor, Vec3 direction, float damage) {
        EntitySevenThunders21 e = new EntitySevenThunders21(ModEntities.SEVEN_THUNDERS.get(), level);
        e.setPos(owner.getX(), owner.getY(), owner.getZ());
        e.entityData.set(OWNER_ID, owner.getId());
        e.entityData.set(DAMAGE, damage);
        Vec3 dir = direction.lengthSqr() < 1.0E-8 ? new Vec3(0, 0, 1) : direction.normalize();
        e.entityData.set(DIR_X, (float) dir.x); e.entityData.set(DIR_Y, (float) dir.y); e.entityData.set(DIR_Z, (float) dir.z);
        Vec3 offset = targetAnchor.subtract(owner.position());
        e.entityData.set(TARGET_X, (float) offset.x); e.entityData.set(TARGET_Y, (float) offset.y); e.entityData.set(TARGET_Z, (float) offset.z);
        // 锁定目标：最近生物
        List<Entity> found = level.getEntitiesOfClass(Entity.class, owner.getBoundingBox().inflate(24),
                x -> x instanceof LivingEntity lv && lv != owner && lv.isAlive() && !lv.isSpectator());
        if (!found.isEmpty()) {
            double best = Double.MAX_VALUE;
            int bestId = -1;
            for (Entity f : found) {
                double d = f.distanceToSqr(e);
                if (d < best) { best = d; bestId = f.getId(); }
            }
            e.entityData.set(TARGET_ID, bestId);
        } else {
            e.entityData.set(TARGET_ID, -1);
        }
        level.addFreshEntity(e);
        return e;
    }

    public float getBaseDamage() { return entityData.get(DAMAGE); }
    public long getSeed() { return (long) getId() * 341873128712L; }
    public Vec3 getThunderDirection() {
        Vec3 d = new Vec3(entityData.get(DIR_X), entityData.get(DIR_Y), entityData.get(DIR_Z));
        return d.lengthSqr() < 1.0E-8 ? new Vec3(0, 0, 1) : d.normalize();
    }
    public Vec3 getStoredTargetAnchor() {
        return position().add(entityData.get(TARGET_X), entityData.get(TARGET_Y), entityData.get(TARGET_Z));
    }
    public Vec3 getTargetAnchor() {
        Entity e = level().getEntity(entityData.get(TARGET_ID));
        return e != null && e.isAlive() ? e.getBoundingBox().getCenter() : getStoredTargetAnchor();
    }
    public Vec3 getTargetAnchor(float partial) {
        Entity e = level().getEntity(entityData.get(TARGET_ID));
        if (e == null || !e.isAlive()) return getStoredTargetAnchor();
        Vec3 c = e.getBoundingBox().getCenter();
        return c.add(e.xo - e.getX(), e.yo - e.getY(), e.zo - e.getZ());
    }
    public Vec3 getOwnerAnchor(float partial) {
        LivingEntity owner = getOwner();
        if (owner == null) return position();
        return new Vec3(owner.xo + (owner.getX() - owner.xo) * partial,
                owner.yo + (owner.getY() - owner.yo) * partial,
                owner.zo + (owner.getZ() - owner.zo) * partial);
    }
    public LivingEntity getOwner() {
        Entity e = level().getEntity(entityData.get(OWNER_ID));
        return e instanceof LivingEntity le ? le : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        LivingEntity owner = getOwner();
        if (owner == null || !owner.isAlive()) { discard(); return; }
        ServerLevel server = (ServerLevel) level();
        playSounds(server);
        // 鸣雷神 伤害波次（原版 narukamiHit 时间表）
        int[] times = {5, 9, 10, 24, 25, 26, 28, 29, 31};
        float[] weights = {0.12F, 0.14F, 0.14F, 0.08F, 0.08F, 0.08F, 0.08F, 0.08F, 0.20F};
        for (int i = 0; i < times.length; i++) {
            if (tickCount == times[i]) narukamiHit(owner, i, getBaseDamage() * weights[i]);
        }
        if (tickCount >= LIFETIME) discard();
    }

    private void narukamiHit(LivingEntity owner, int index, float damage) {
        Vec3 center = index == 0 ? getTargetAnchor() : owner.position().add(0, 1.15, 0);
        double radius = index == 0 ? 3.25 : index < 3 ? 6.0 : 4.35;
        List<LivingEntity> receivers = radiusTargets(center, radius);
        if (index > 0 && index < 3) {
            List<LivingEntity> merged = new ArrayList<>(receivers);
            merged.addAll(radiusTargets(getTargetAnchor(), 4.55));
            Entity locked = level().getEntity(entityData.get(TARGET_ID));
            if (locked instanceof LivingEntity lv && lv.isAlive() && lv != owner) merged.add(lv);
            receivers = distinct(merged);
        }
        boolean hit = false;
        for (LivingEntity target : receivers) {
            target.invulnerableTime = 0;
            if (!target.hurt(source(owner), damage)) continue;
            hit = true;
            Vec3 push = target.position().subtract(owner.position());
            if (push.lengthSqr() > 1.0E-6) {
                double strength = index == 8 ? 0.72 : 0.18;
                push = push.normalize().scale(strength).add(0, 0.10, 0);
                target.setDeltaMovement(target.getDeltaMovement().scale(0.5).add(push));
                target.hasImpulse = true;
            }
        }
        spawnHitParticles(server(), center, index, hit);
    }

    private List<LivingEntity> radiusTargets(Vec3 center, double radius) {
        AABB box = new AABB(center.x - radius, center.y - radius * 0.78, center.z - radius,
                center.x + radius, center.y + radius * 0.78, center.z + radius);
        LivingEntity owner = getOwner();
        List<LivingEntity> all = level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != owner && e.isAlive() && !e.isSpectator());
        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity e : distinct(all)) {
            if (e.getBoundingBox().getCenter().distanceToSqr(center) <= radius * radius) result.add(e);
        }
        return result;
    }

    private List<LivingEntity> distinct(List<LivingEntity> list) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : list) { if (!out.contains(e)) out.add(e); }
        return out;
    }

    private void playSounds(ServerLevel server) {
        Vec3 anchor = getTargetAnchor();
        LivingEntity owner = getOwner();
        Vec3 ownerAnchor = owner != null ? owner.position() : position();
        if (tickCount == 4) {
            server.playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.08F, 1.28F);
            server.playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.72F, 0.72F);
        } else if (tickCount == 8 || tickCount == 10) {
            server.playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.94F, tickCount == 8 ? 0.62F : 0.78F);
        } else if (tickCount == 20) {
            server.playSound(null, ownerAnchor.x, ownerAnchor.y + 1.1, ownerAnchor.z, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 0.82F, 1.52F);
            server.playSound(null, ownerAnchor.x, ownerAnchor.y + 1.1, ownerAnchor.z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.64F, 0.58F);
        } else if (tickCount == 24 || tickCount == 28) {
            server.playSound(null, ownerAnchor.x, ownerAnchor.y + 1.1, ownerAnchor.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.92F, tickCount == 24 ? 1.54F : 1.82F);
        } else if (tickCount == 31) {
            server.playSound(null, ownerAnchor.x, ownerAnchor.y + 0.5, ownerAnchor.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.88F, 1.64F);
        }
    }

    private void spawnHitParticles(ServerLevel server, Vec3 center, int hitIndex, boolean hit) {
        int count = hit ? 10 + hitIndex * 2 : 4;
        server.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, count, 0.72, 0.28, 1.0, 1.0);
        server.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, Math.max(4, count / 2), 0.75, 0.55, 0.75, 0.055);
    }

    private ServerLevel server() { return (ServerLevel) level(); }

    private DamageSource source(LivingEntity owner) {
        return owner instanceof Player p ? level().damageSources().playerAttack(p) : level().damageSources().mobAttack(owner);
    }

    @Override public boolean isPickable() { return false; }
    @Override public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(9);
    }
    @Override protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1); this.entityData.define(TARGET_ID, -1);
        this.entityData.define(DAMAGE, 1.0F); this.entityData.define(DIR_X, 0F); this.entityData.define(DIR_Y, 0F); this.entityData.define(DIR_Z, 1F);
        this.entityData.define(TARGET_X, 0F); this.entityData.define(TARGET_Y, 1.25F); this.entityData.define(TARGET_Z, 5F);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(OWNER_ID, tag.getInt("OwnerId"));
        entityData.set(TARGET_ID, tag.getInt("TargetId"));
        entityData.set(DAMAGE, tag.getFloat("Damage"));
        entityData.set(DIR_X, tag.getFloat("Dx")); entityData.set(DIR_Y, tag.getFloat("Dy")); entityData.set(DIR_Z, tag.getFloat("Dz"));
        entityData.set(TARGET_X, tag.getFloat("Tx")); entityData.set(TARGET_Y, tag.getFloat("Ty")); entityData.set(TARGET_Z, tag.getFloat("Tz"));
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("OwnerId", entityData.get(OWNER_ID));
        tag.putInt("TargetId", entityData.get(TARGET_ID));
        tag.putFloat("Damage", getBaseDamage());
        tag.putFloat("Dx", entityData.get(DIR_X)); tag.putFloat("Dy", entityData.get(DIR_Y)); tag.putFloat("Dz", entityData.get(DIR_Z));
        tag.putFloat("Tx", entityData.get(TARGET_X)); tag.putFloat("Ty", entityData.get(TARGET_Y)); tag.putFloat("Tz", entityData.get(TARGET_Z));
    }
}