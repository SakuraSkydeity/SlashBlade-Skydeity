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
 * 「花开见血血染双瞳」的暗红刀痕（纯视觉实体）—— 就是原本 zankou SA 那套**六条凌乱弧线**，
 * 原样搬到 SE 里：在**命中目标身边**绽开，存在约 2 秒。
 *
 * 服务端只负责存活计时与同步一个朝向基；**几何完全由 {@code RenderBloomSlash} 用固定参数表绘制**
 * （连续线条，不是粒子；布局固定不随机，只按攻击者视线朝向摆一圈）。
 * 参考同类实现：{@link EntityXuanfengRing} / {@link EntityChikuiArc}。
 */
public class EntityBloomSlash extends Entity {

    /** 存在时长（tick）≈2 秒（6 条弧起步间隔 2.1t × 每条 9t 划完 + 停留 + 淡出） */
    public static final int LIFETIME = 40;

    private static final EntityDataAccessor<Float> DIR_X = SynchedEntityData.defineId(
            EntityBloomSlash.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Y = SynchedEntityData.defineId(
            EntityBloomSlash.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Z = SynchedEntityData.defineId(
            EntityBloomSlash.class, EntityDataSerializers.FLOAT);

    public EntityBloomSlash(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    /** 在 center（命中目标身上）生成，direction 为刀痕的朝向基（取攻击者视线） */
    public static EntityBloomSlash spawn(Level level, Vec3 center, Vec3 direction) {
        EntityBloomSlash e = new EntityBloomSlash(ModEntities.BLOOM_SLASH.get(), level);
        e.setPos(center.x, center.y, center.z);
        Vec3 dir = direction.lengthSqr() < 1.0E-8 ? new Vec3(0, 0, 1) : direction.normalize();
        e.entityData.set(DIR_X, (float) dir.x);
        e.entityData.set(DIR_Y, (float) dir.y);
        e.entityData.set(DIR_Z, (float) dir.z);
        level.addFreshEntity(e);
        return e;
    }

    /** 朝向基（水平化的视线方向），渲染器据此摆放一圈刀痕 */
    public Vec3 getArcDirection() {
        Vec3 d = new Vec3(entityData.get(DIR_X), entityData.get(DIR_Y), entityData.get(DIR_Z));
        return d.lengthSqr() < 1.0E-8 ? new Vec3(0, 0, 1) : d.normalize();
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
        return getBoundingBox().inflate(6.0);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DIR_X, 0F);
        this.entityData.define(DIR_Y, 0F);
        this.entityData.define(DIR_Z, 1F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(DIR_X, tag.getFloat("Dx"));
        entityData.set(DIR_Y, tag.getFloat("Dy"));
        entityData.set(DIR_Z, tag.getFloat("Dz"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Dx", entityData.get(DIR_X));
        tag.putFloat("Dy", entityData.get(DIR_Y));
        tag.putFloat("Dz", entityData.get(DIR_Z));
    }
}
