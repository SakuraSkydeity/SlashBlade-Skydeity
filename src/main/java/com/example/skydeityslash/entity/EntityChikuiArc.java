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
 * 「赤葵」焚天烬灭舞的暗红摆线（纯视觉实体）—— 一条**极细的深暗红圆柱**，像**钟摆**一样：
 * 一端挂在**玩家头顶稍上方**、摆臂 25 格，与地面的夹角从 60° 加速摆到 0°（水平）；
 * 摆到水平后**整条线保持水平一起平移到地面**，触地即消失；柱上飘着长短不一的火光弧线。
 * 实体生成在玩家脚下（渲染器再按 {@code PIVOT_Y} 把支点抬到头顶上方）。
 *
 * 服务端只负责存活计时与同步一个朝向基（释放时玩家的视线）；**几何与运动完全由
 * {@code RenderChikuiArc} 用固定参数绘制**（连续线条，不是粒子；摆动平面顺着玩家面朝方向）。
 * 参考同类实现：{@link EntityXuanfengRing}。
 */
public class EntityChikuiArc extends Entity {

    /** 存在时长（tick）≈1.25 秒（17 tick 摆到水平 + 8 tick 平移到地面后即消失） */
    public static final int LIFETIME = 25;

    /**
     * 整条线沿**水平面朝方向**的平移量（格）—— 正值 = 往画面深处（远离玩家）挪，**负值 = 往玩家身后挪**。
     * 取 -3.0：整条摆臂（支点 + 线 + 火焰）一起往玩家背后挪 3 格。
     */
    public static final double ARC_OFFSET = -3.0;

    private static final EntityDataAccessor<Float> DIR_X = SynchedEntityData.defineId(
            EntityChikuiArc.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Y = SynchedEntityData.defineId(
            EntityChikuiArc.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Z = SynchedEntityData.defineId(
            EntityChikuiArc.class, EntityDataSerializers.FLOAT);

    public EntityChikuiArc(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    /**
     * 在 center（玩家位置）生成，direction 为摆动平面的朝向基（取释放者视线）。
     * 实际落点会沿水平朝向再平移 {@link #ARC_OFFSET} 格（往画面深处挪，见该常量的说明）。
     */
    public static EntityChikuiArc spawn(Level level, Vec3 center, Vec3 direction) {
        EntityChikuiArc e = new EntityChikuiArc(ModEntities.CHIKUI_ARC.get(), level);
        Vec3 dir = direction.lengthSqr() < 1.0E-8 ? new Vec3(0, 0, 1) : direction.normalize();
        // 水平化的朝向 —— 整条线沿它平移（支点、摆臂、火焰一起走）
        Vec3 fwd = new Vec3(dir.x, 0.0, dir.z);
        fwd = fwd.lengthSqr() < 1.0E-8 ? new Vec3(0, 0, 1) : fwd.normalize();
        Vec3 at = center.add(fwd.scale(ARC_OFFSET));
        e.setPos(at.x, at.y, at.z);
        e.entityData.set(DIR_X, (float) dir.x);
        e.entityData.set(DIR_Y, (float) dir.y);
        e.entityData.set(DIR_Z, (float) dir.z);
        level.addFreshEntity(e);
        return e;
    }

    /** 摆动平面的朝向基（水平化的视线方向），渲染器据此在玩家正前方那面竖直平面里摆 */
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
        // 摆臂 25 格 —— 剔除箱必须覆盖整条线，否则视线侧偏时整条线会被"整体剔除"而消失
        return getBoundingBox().inflate(34.0);
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
