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
 * 「向阳」第二形态收尾的**紫色光球群**（纯视觉实体）：8 个紫色圆球在中心周围缓慢漂浮，
 * **2 秒**后一起淡出消失。
 *
 * <p>服务端只负责存活计时与同步一个"散布半径"；**几何完全由 {@code client.IroiOrbGeometry}
 * 每帧现算**（UV 球，加法混合发光，不用贴图、也不是粒子）。参考同类实现：{@link EntityChikuiFlower}。
 */
public class EntityIroiOrb extends Entity {

    /** 存在时长（tick）—— **2 秒** */
    public static final int LIFETIME = 40;

    /** 球心离中心的散布半径（格） */
    private static final EntityDataAccessor<Float> SPREAD = SynchedEntityData.defineId(
            EntityIroiOrb.class, EntityDataSerializers.FLOAT);

    public EntityIroiOrb(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    /**
     * 在 {@code center} 周围铺开一组紫球。
     *
     * @param spread 球心离中心的散布半径（格）；<= 0.05 时取默认 1.4
     */
    public static EntityIroiOrb spawn(Level level, Vec3 center, float spread) {
        EntityIroiOrb e = new EntityIroiOrb(ModEntities.IROI_ORB.get(), level);
        e.setPos(center.x, center.y, center.z);
        e.entityData.set(SPREAD, spread <= .05F ? 1.4F : spread);
        level.addFreshEntity(e);
        return e;
    }

    /** 球心离中心的散布半径（格） */
    public float getSpread() {
        return entityData.get(SPREAD);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount >= LIFETIME) discard();
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }

    @Override
    public AABB getBoundingBoxForCulling() {
        // 散布半径 + 向外飘散 + 上浮 + 球本身 —— 箱子要罩住整组，否则偏头就被整体剔除
        return getBoundingBox().inflate(6.0);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(SPREAD, 1.4F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(SPREAD, tag.contains("Spread") ? tag.getFloat("Spread") : 1.4F);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Spread", entityData.get(SPREAD));
    }
}
