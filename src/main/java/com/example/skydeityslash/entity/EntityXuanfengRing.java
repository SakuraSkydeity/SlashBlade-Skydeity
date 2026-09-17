package com.example.skydeityslash.entity;

import com.example.skydeityslash.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 「翾风回雪」白色圆环视觉实体 —— 环绕锁定目标/落点生成。
 * 服务端仅负责存活计时（纯视觉、无伤害碰撞）；
 * 客户端由 RenderXuanfengRing 用 OdetteRingGeometry 绘制白色三段轨道弧环 + 旋转 ribbon 环
 * （移植自鸣雷神 RING_BACK 轨道弧环 + groundField 旋转环，改为纯白）。
 */
public class EntityXuanfengRing extends Entity {
    public static final int LIFETIME = 64;

    private static final EntityDataAccessor<Float> DIR_X = SynchedEntityData.defineId(
            EntityXuanfengRing.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Y = SynchedEntityData.defineId(
            EntityXuanfengRing.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Z = SynchedEntityData.defineId(
            EntityXuanfengRing.class, EntityDataSerializers.FLOAT);

    public EntityXuanfengRing(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    /** 在 center(落点/锁定目标)处生成白色圆环视觉。direction 为朝向基（取玩家视线水平方向）。 */
    public static EntityXuanfengRing spawn(Level level, Vec3 center, Vec3 direction) {
        EntityXuanfengRing e = new EntityXuanfengRing(ModEntities.XUANFENG_RING.get(), level);
        e.setPos(center.x, center.y, center.z);
        Vec3 dir = direction.lengthSqr() < 1.0E-8 ? new Vec3(0, 0, 1) : direction.normalize();
        e.entityData.set(DIR_X, (float) dir.x);
        e.entityData.set(DIR_Y, (float) dir.y);
        e.entityData.set(DIR_Z, (float) dir.z);
        level.addFreshEntity(e);
        return e;
    }

    public long getSeed() { return (long) getId() * 64372241613L; }

    public Vec3 getRingDirection() {
        Vec3 d = new Vec3(entityData.get(DIR_X), entityData.get(DIR_Y), entityData.get(DIR_Z));
        return d.lengthSqr() < 1.0E-8 ? new Vec3(0, 0, 1) : d.normalize();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount >= LIFETIME) discard();
    }

    @Override public boolean isPickable() { return false; }
    @Override public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(9);
    }
    @Override protected void defineSynchedData() {
        this.entityData.define(DIR_X, 0F);
        this.entityData.define(DIR_Y, 0F);
        this.entityData.define(DIR_Z, 1F);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(DIR_X, tag.getFloat("Dx"));
        entityData.set(DIR_Y, tag.getFloat("Dy"));
        entityData.set(DIR_Z, tag.getFloat("Dz"));
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Dx", entityData.get(DIR_X));
        tag.putFloat("Dy", entityData.get(DIR_Y));
        tag.putFloat("Dz", entityData.get(DIR_Z));
    }
}