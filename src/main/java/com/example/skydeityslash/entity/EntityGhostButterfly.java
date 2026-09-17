package com.example.skydeityslash.entity;

import com.example.skydeityslash.registry.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 幽灵蝶特效：一只很小的「幽灵蝴蝶」贴图悬浮在空中，轻微起伏飘动，3 秒后淡出消散。
 * 纯视觉，无碰撞、无伤害。
 * 指令：/skydeityslash effect ghostbutterfly （一次在身前空中撒 5 只）
 */
public class EntityGhostButterfly extends Entity {

    /** 总生命周期（tick）—— 3 秒后消失 */
    public static final int LIFE = 60;

    /** 自转速度（度/tick）：>0 时在屏幕平面内慢慢旋转 */
    private static final EntityDataAccessor<Float> SPIN = SynchedEntityData.defineId(
            EntityGhostButterfly.class, EntityDataSerializers.FLOAT);

    public EntityGhostButterfly(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** 在 pos 处生成一只幽灵蝶（不自转） */
    public static EntityGhostButterfly spawn(Level level, Vec3 pos) {
        return spawn(level, pos, 0f);
    }

    /** 在 pos 处生成一只幽灵蝶；spinDegPerTick 为自转速度（度/tick，0=不自转） */
    public static EntityGhostButterfly spawn(Level level, Vec3 pos, float spinDegPerTick) {
        EntityGhostButterfly e = new EntityGhostButterfly(ModEntities.GHOST_BUTTERFLY.get(), level);
        e.setPos(pos.x, pos.y, pos.z);
        e.entityData.set(SPIN, spinDegPerTick);
        level.addFreshEntity(e);
        return e;
    }

    /**
     * 一次撒一簇 5 只：一对左右严格对称 + 一只缓慢自转 + 两只随机散布。
     * center 为簇中心，look 决定对称轴（用玩家的水平朝向即可）。
     */
    public static void spawnCluster(Level level, Vec3 center, Vec3 look) {
        Vec3 lx = new Vec3(look.x, 0, look.z);
        lx = lx.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : lx.normalize();
        Vec3 right = new Vec3(-lx.z, 0, lx.x);

        // ① 左右对称的一对
        spawn(level, center.add(right.scale(1.7)).add(0, 1.4, 0));
        spawn(level, center.subtract(right.scale(1.7)).add(0, 1.4, 0));
        // ② 中间一只缓慢自转（约 0.55°/tick）
        spawn(level, center.add(lx.scale(0.4)).add(0, 2.1, 0), 0.55f);
        // ③ 其余两只随机散布
        var rnd = level.getRandom();
        for (int i = 0; i < 2; i++) {
            spawn(level, center
                    .add(right.scale((rnd.nextDouble() - 0.5) * 4.6))
                    .add(lx.scale((rnd.nextDouble() - 0.5) * 2.4))
                    .add(0, 0.9 + rnd.nextDouble() * 1.9, 0));
        }
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

        // 淡淡的光尘拖尾（暖橙色，与蝴蝶同色）
        if (tickCount % 5 == 0 && level() instanceof ServerLevel sl) {
            sl.sendParticles(new DustParticleOptions(new Vector3f(1.0f, 0.72f, 0.46f), 0.30f),
                    getX() + (random.nextDouble() - 0.5) * 0.4,
                    getY() + random.nextDouble() * 0.3,
                    getZ() + (random.nextDouble() - 0.5) * 0.4,
                    1, 0, 0.09, 0, 0.01);
        }

        if (tickCount > LIFE) {
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(4.0);
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
