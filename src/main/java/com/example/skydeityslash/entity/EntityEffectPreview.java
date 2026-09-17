package com.example.skydeityslash.entity;

import com.example.skydeityslash.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 单子特效预览实体 —— 只触发 naru（0=鸣雷神）或 enlight（1=剑体始觉）的一个阶段/子特效。
 * 服务端仅存活计时；客户端 RenderEffectPreview 按类型+ID 分发渲染。
 */
public class EntityEffectPreview extends Entity {
    public static final int LIFETIME_NARU = 54;
    public static final int LIFETIME_ENLIGHT = 64;

    private static final EntityDataAccessor<Integer> TYPE =
            SynchedEntityData.defineId(EntityEffectPreview.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> EFFECT_ID =
            SynchedEntityData.defineId(EntityEffectPreview.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DIR_X =
            SynchedEntityData.defineId(EntityEffectPreview.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Y =
            SynchedEntityData.defineId(EntityEffectPreview.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Z =
            SynchedEntityData.defineId(EntityEffectPreview.class, EntityDataSerializers.FLOAT);

    public EntityEffectPreview(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    /** 在 center 生成子特效预览。type: 0=naru, 1=enlight；id 为阶段/子特效编号。 */
    public static EntityEffectPreview spawn(Level level, Vec3 center, Vec3 direction, int type, int effectId) {
        EntityEffectPreview e = new EntityEffectPreview(ModEntities.EFFECT_PREVIEW.get(), level);
        e.setPos(center.x, center.y, center.z);
        Vec3 dir = direction.lengthSqr() < 1.0E-8 ? new Vec3(0, 0, 1) : direction.normalize();
        e.entityData.set(TYPE, type);
        e.entityData.set(EFFECT_ID, effectId);
        e.entityData.set(DIR_X, (float) dir.x);
        e.entityData.set(DIR_Y, (float) dir.y);
        e.entityData.set(DIR_Z, (float) dir.z);
        level.addFreshEntity(e);
        return e;
    }

    public long getSeed() { return (long) getId() * 64372241613L; }

    public int getEffectType() { return entityData.get(TYPE); }

    public int getEffectId() { return entityData.get(EFFECT_ID); }

    public Vec3 getLookDirection() {
        Vec3 d = new Vec3(entityData.get(DIR_X), entityData.get(DIR_Y), entityData.get(DIR_Z));
        return d.lengthSqr() < 1.0E-8 ? new Vec3(0, 0, 1) : d.normalize();
    }

    public int getLifetime() {
        return getEffectType() == 0 ? LIFETIME_NARU : LIFETIME_ENLIGHT;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount >= getLifetime()) discard();
    }

    @Override public boolean isPickable() { return false; }
    @Override public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(9);
    }
    @Override protected void defineSynchedData() {
        this.entityData.define(TYPE, 0);
        this.entityData.define(EFFECT_ID, 0);
        this.entityData.define(DIR_X, 0F);
        this.entityData.define(DIR_Y, 0F);
        this.entityData.define(DIR_Z, 1F);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(TYPE, tag.getInt("Typ"));
        entityData.set(EFFECT_ID, tag.getInt("Sub"));
        entityData.set(DIR_X, tag.getFloat("Dx"));
        entityData.set(DIR_Y, tag.getFloat("Dy"));
        entityData.set(DIR_Z, tag.getFloat("Dz"));
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Typ", entityData.get(TYPE));
        tag.putInt("Sub", entityData.get(EFFECT_ID));
        tag.putFloat("Dx", entityData.get(DIR_X));
        tag.putFloat("Dy", entityData.get(DIR_Y));
        tag.putFloat("Dz", entityData.get(DIR_Z));
    }
}