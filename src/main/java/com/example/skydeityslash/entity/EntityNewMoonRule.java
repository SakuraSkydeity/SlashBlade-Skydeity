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
 * NewMoonRule（新月法则）—— 在落点**地面**铺开一圈 10×10 格的徽记：一个**蓝色新月** + **五颗蓝白十字星**。
 *
 * <p>纯视觉：无碰撞、无伤害、无 AI；服务端只负责存活计时，客户端由 {@code RenderNewMoonRule}
 * 用 {@code client/NewMoonRuleGeometry} **现算几何**画出来（**不再用贴图** —— 原来是贴
 * {@code effects/newmoonrule/newmoonrule.png}，2026-09-26 改成代码画）；法阵正前方朝向实体 yaw。
 *
 * <p>指令：{@code /skydeityslash effect newmoonrule}
 */
public class EntityNewMoonRule extends Entity {

    /** 存在时长（tick）—— 5 秒，与 columbina SA 的随行环一致 */
    public static final int LIFETIME = 100;

    /** 徽记边长（格）—— 10 × 10 */
    public static final float SIZE = 10.0f;

    /** 贴地抬升（格）—— 稍微抬起一点，避开与地面的深度冲突（z-fighting） */
    public static final double GROUND_LIFT = 0.05;

    /** 自转速度（度/tick）；0 = 不转。需要缓转时在 {@link #spawn(Level, Vec3, float, float)} 里给值即可 */
    private static final EntityDataAccessor<Float> SPIN = SynchedEntityData.defineId(
            EntityNewMoonRule.class, EntityDataSerializers.FLOAT);

    public EntityNewMoonRule(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** 在 pos（通常取玩家脚下）平铺徽记，yaw 决定贴图朝向；不自转 */
    public static EntityNewMoonRule spawn(Level level, Vec3 pos, float yaw) {
        return spawn(level, pos, yaw, 0f);
    }

    /** 在 pos 处平铺徽记；spinDegPerTick 为绕竖直轴的自转速度（度/tick，0 = 不转） */
    public static EntityNewMoonRule spawn(Level level, Vec3 pos, float yaw, float spinDegPerTick) {
        EntityNewMoonRule e = new EntityNewMoonRule(ModEntities.NEW_MOON_RULE.get(), level);
        e.setPos(pos.x, pos.y + GROUND_LIFT, pos.z);
        e.setYRot(yaw);
        e.entityData.set(SPIN, spinDegPerTick);
        level.addFreshEntity(e);
        return e;
    }

    /** 渲染用的年龄（tick） */
    public float getAge() {
        return tickCount;
    }

    /** 自转速度（度/tick） */
    public float getSpin() {
        return entityData.get(SPIN);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (tickCount > LIFETIME) {
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    /** 剔除箱：四边形向四周各伸出 SIZE/2，别让它在视野边缘被误剔 */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(SIZE * 0.5 + 1.0);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(SPIN, 0f);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(SPIN, tag.getFloat("Spin"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Spin", entityData.get(SPIN));
    }
}
