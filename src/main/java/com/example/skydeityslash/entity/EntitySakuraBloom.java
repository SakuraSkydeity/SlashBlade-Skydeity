package com.example.skydeityslash.entity;

import com.example.skydeityslash.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 「瞳中深渊渊底之吻」绽开在目标身上的**几何樱花**（纯视觉实体）—— 5 片花瓣、绕中心均分 72°，
 * 每片是一条短而略弯的三角带（花瓣形，根部与尖端都收成尖）。存在 {@link #LIFETIME} tick（0.6 秒）。
 *
 * 服务端只负责存活计时；**几何完全由 {@code RenderSakuraBloom} 用固定参数绘制**
 * （连续几何线条/面，不是粒子）。花面**始终正对相机**（渲染器里按相机方向取平面基），
 * 所以任何角度看都是完整一朵五瓣花 —— 参考同类实现：{@link EntityBloomSlash} / {@link EntityChikuiArc}。
 */
public class EntitySakuraBloom extends Entity {

    /** 存在时长（tick）—— 长出 4 + 停留 5 + 淡出 3 */
    public static final int LIFETIME = 12;

    public EntitySakuraBloom(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    /** 在 center（一般是命中目标的胸口）生成一朵樱花 */
    public static EntitySakuraBloom spawn(Level level, Vec3 center) {
        EntitySakuraBloom e = new EntitySakuraBloom(ModEntities.SAKURA_BLOOM.get(), level);
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
        // 花瓣总长约 0.9 格 —— 剔除箱要覆盖整朵花，否则偏头就被整体剔除
        return getBoundingBox().inflate(2.0);
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
