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
 * 「超位法术」—— 巨型法阵 + 天光 + 光柱的纯视觉特效主体。
 *
 * <p>本实体**不带任何伤害、也不加载任何贴图**：只负责存活计时与同步半径，
 * 地面上那张大法阵（多重环 / 96 刻度 / 24 条放射线 / 符文带 / 十二边形 / 六芒十二芒星 / 内心）
 * 和所有发光体都在 {@code OverrankGeometry} 里用**圆弧与直线**逐帧现算。
 *
 * <p>时间线（设计值见 {@code OverrankGeometry} 的时间轴常量；实际播放整体快 1/4）：
 * <pre>
 *   0 ─ 40    法阵自中心展开（圆弧按角度"画"出来）
 *   34 ─ 76   蓄力：光柱升起、光幕旋转、符文螺旋上腾
 *   110       爆发：光柱暴涨 + 天光贯下 + 冲击环外扩
 *   148 ─ 194 消散
 * </pre>
 *
 * <p>指令：{@code /skydeityslash effect overrank}
 */
public class EntityOverrankMagic extends Entity {

    /**
     * 存在时长（tick）。
     * ★ 200 是设计时长；实际按 {@code OverrankGeometry.TIME_SCALE}（4/3 倍速）播放 ⇒ **150 tick（7.5 秒）**。
     * 改整体快慢时，这一项要和 TIME_SCALE 同步（= 200 / TIME_SCALE）。
     */
    public static final int LIFETIME = 150;

    /** 法阵半径（格），默认直径 20 格 */
    public static final float DEFAULT_RADIUS = 12.0f;

    /** 贴地抬升（格）—— 避免与地面 z-fighting；几何都取在它之上 */
    public static final double GROUND_LIFT = 0.05;

    private static final EntityDataAccessor<Float> RADIUS =
            SynchedEntityData.defineId(EntityOverrankMagic.class, EntityDataSerializers.FLOAT);

    public EntityOverrankMagic(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** 在 pos 处展开法阵，yaw 只决定符文带的初始朝向（法阵本身是旋转对称的）。 */
    public static EntityOverrankMagic spawn(Level level, Vec3 pos, float yaw) {
        return spawn(level, pos, yaw, DEFAULT_RADIUS);
    }

    public static EntityOverrankMagic spawn(Level level, Vec3 pos, float yaw, float radius) {
        EntityOverrankMagic e = new EntityOverrankMagic(ModEntities.OVERRANK_MAGIC.get(), level);
        e.setPos(pos.x, pos.y + GROUND_LIFT, pos.z);
        e.setYRot(yaw);
        e.entityData.set(RADIUS, radius);
        level.addFreshEntity(e);
        return e;
    }

    /** 渲染用的年龄（tick） */
    public float getAge() {
        return tickCount;
    }

    public float getRadius() {
        return entityData.get(RADIUS);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (tickCount > LIFETIME) discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    /** 剔除箱：法阵铺开 20 格、光柱高达 30 余格，四周各留余量 */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(28.0);
    }

    /**
     * 可见距离：默认实现按 0.2 的碰撞箱算，只有十几个格子，法阵会在还看得见的时候被剔掉。
     * 这里给一个随半径增长、但有上界（72 格）的距离。
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        double limit = 72.0 + getRadius() * 2.0;
        return dist < limit * limit;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(RADIUS, DEFAULT_RADIUS);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(RADIUS, tag.contains("Radius") ? tag.getFloat("Radius") : DEFAULT_RADIUS);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Radius", entityData.get(RADIUS));
    }
}
