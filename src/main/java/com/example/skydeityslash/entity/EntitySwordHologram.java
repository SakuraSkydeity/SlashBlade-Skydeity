package com.example.skydeityslash.entity;

import com.example.skydeityslash.SkydeitySlash;
import com.example.skydeityslash.registry.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 全息剑特效：在指定点上方 7 格生成，竖直自上下落 7 格，落地后很快消散。
 * · 指令用法（纯视觉）：/skydeityslash effect sword
 * · 剑技用法（带落地伤害）：{@link #spawnFall(Level, Vec3, LivingEntity, float)}
 */
public class EntitySwordHologram extends Entity {

    /** 下落总格数 */
    public static final double FALL_DISTANCE = 7.0;
    /** 下落时长（tick） */
    public static final int FALL_TICKS = 24;
    /** 落地后停留（tick）—— 越快消失越好 */
    public static final int LINGER_TICKS = 12;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(
            EntitySwordHologram.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(
            EntitySwordHologram.class, EntityDataSerializers.FLOAT);

    /** 起始高度（仅服务端使用） */
    private double startY;

    public EntitySwordHologram(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** 纯视觉：在 center 上方 7 格生成，向下落 7 格（指令用） */
    public static EntitySwordHologram spawn(Level level, Vec3 center) {
        return spawnAt(level, center.add(0, FALL_DISTANCE, 0), null, 0f);
    }

    /** 带落地伤害：剑落点造成 damage 点真伤（剑技用） */
    public static EntitySwordHologram spawnFall(Level level, Vec3 landPos, LivingEntity owner, float damage) {
        return spawnAt(level, landPos.add(0, FALL_DISTANCE, 0), owner, damage);
    }

    private static EntitySwordHologram spawnAt(Level level, Vec3 startPos, LivingEntity owner, float damage) {
        EntitySwordHologram e = new EntitySwordHologram(ModEntities.SWORD_HOLOGRAM.get(), level);
        e.startY = startPos.y;
        e.setPos(startPos.x, startPos.y, startPos.z);
        e.entityData.set(OWNER_ID, owner == null ? -1 : owner.getId());
        e.entityData.set(DAMAGE, damage);
        level.addFreshEntity(e);
        return e;
    }

    public LivingEntity getOwnerEntity() {
        Entity e = level().getEntity(entityData.get(OWNER_ID));
        return e instanceof LivingEntity le ? le : null;
    }

    public float getImpactDamage() {
        return entityData.get(DAMAGE);
    }

    /** 下落进度 0~1 */
    public float getFallProgress() {
        return Math.min(1.0f, tickCount / (float) FALL_TICKS);
    }

    public boolean isLanded() {
        return tickCount >= FALL_TICKS;
    }

    /** 整个生命周期总时长（渲染器用于淡出） */
    public int getTotalLife() {
        return FALL_TICKS + LINGER_TICKS;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        if (tickCount <= FALL_TICKS) {
            // 加速下落：y 从 startY 降到 startY - 7
            double p = tickCount / (double) FALL_TICKS;
            setPos(getX(), startY - FALL_DISTANCE * p * p, getZ());
            // 下落拖尾：淡蓝光尘
            if (tickCount % 2 == 0 && level() instanceof ServerLevel sl) {
                sl.sendParticles(new DustParticleOptions(new Vector3f(0.62f, 0.82f, 1.0f), 0.65f),
                        getX(), getY() + 1.6, getZ(), 2, 0.35, 1.3, 0.35, 0.02);
            }
        } else if (tickCount == FALL_TICKS + 1) {
            // 落地：一圈浅蓝光环 + 结算伤害
            if (level() instanceof ServerLevel sl) {
                for (int i = 0; i < 24; i++) {
                    double a = i / 24.0 * Math.PI * 2;
                    sl.sendParticles(new DustParticleOptions(new Vector3f(0.70f, 0.88f, 1.0f), 0.7f),
                            getX() + Math.cos(a) * 1.1, getY() + 0.1, getZ() + Math.sin(a) * 1.1,
                            1, 0, 0, 0, 0);
                }
                doImpact(sl);
            }
        } else if (tickCount > getTotalLife()) {
            discard();
        }
    }

    /** 落地结算：对落点周围生物造成真伤（仅带伤害的用法） */
    private void doImpact(ServerLevel sl) {
        float dmg = getImpactDamage();
        if (dmg <= 0f) return;
        LivingEntity owner = getOwnerEntity();
        AABB box = getBoundingBox().inflate(2.5, 1.5, 2.5);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box,
                x -> x.isAlive() && !x.isSpectator() && x != owner)) {
            SkydeitySlash.GameEvents.applyTrueDamage(e, owner, dmg);
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(8.0);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
        this.entityData.define(DAMAGE, 0f);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.startY = tag.getDouble("StartY");
        this.entityData.set(OWNER_ID, tag.getInt("OwnerId"));
        this.entityData.set(DAMAGE, tag.getFloat("Damage"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("StartY", startY);
        tag.putInt("OwnerId", entityData.get(OWNER_ID));
        tag.putFloat("Damage", entityData.get(DAMAGE));
    }
}
