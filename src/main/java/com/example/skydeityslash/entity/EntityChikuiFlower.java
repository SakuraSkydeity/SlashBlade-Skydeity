package com.example.skydeityslash.entity;

import com.example.skydeityslash.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 「赤葵」第二种 SA 的**程序化花朵**（视觉实体）—— 一朵悬浮的赤红大花：
 * 8 片外层 + 8 片内层花瓣、亮芯与花蕊。存在 {@link #LIFETIME} tick（**3 秒**）。
 *
 * <p>服务端只负责存活计时；**几何完全由 {@code client.ChikuiFlowerGeometry} 用固定参数每帧现算**
 * （连续几何面，不是粒子、不用贴图），花面**始终正对相机**（渲染器按公告板取平面基），
 * 所以任何角度看都是完整一朵花 —— 参考同类实现：{@link EntitySakuraBloom} / {@link EntityChikuiArc}。
 *
 * <p>伤害不在这里结算（本实体不带任何攻击逻辑）：绽放瞬间的 1314 真伤与之后每秒 52 的持续真伤
 * 都由 {@code ability.ChikuiFentian} 在服务端按花心位置结算，时长与 {@link #LIFETIME} 对齐。
 */
public class EntityChikuiFlower extends Entity {

    /** 存在时长（tick）—— 张开 16 + 全开约 18 + 淡出 16，按要求整体 **3 秒** */
    public static final int LIFETIME = 60;

    public EntityChikuiFlower(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    /** 在 center（花心所在位置）绽开一朵花 */
    public static EntityChikuiFlower spawn(Level level, Vec3 center) {
        EntityChikuiFlower e = new EntityChikuiFlower(ModEntities.CHIKUI_FLOWER.get(), level);
        e.setPos(center.x, center.y, center.z);
        level.addFreshEntity(e);
        return e;
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
        // 花瓣半径约 2.9 格 —— 剔除箱要罩住整朵花，否则偏头就被整体剔除而消失
        return getBoundingBox().inflate(6.0);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
