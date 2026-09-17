package com.example.skydeityslash.entity;

import com.example.skydeityslash.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 「吻痕窥梦梦魇生花」的**横切割刀痕**（纯视觉实体）—— 几道横着的**细红圆柱**同时划过目标，
 * 像被切了几刀。存在 {@link #LIFETIME} tick（0.7 秒）。
 *
 * 服务端只负责存活计时；**几何完全由 {@code RenderCutLines} 用固定参数绘制**
 * （细圆柱 = 六棱柱，和 SA 摆线同一套做法，不是粒子）。
 * 参考同类实现：{@link EntityBloomSlash} / {@link EntityChikuiArc}。
 */
public class EntityCutLines extends Entity {

    /** 存在时长（tick）—— 依次划出（每道错开 1.2t）+ 停留 + 淡出 */
    public static final int LIFETIME = 14;

    public EntityCutLines(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    /** 在 center（一般是命中目标的身体中段）生成一组横切割刀痕 */
    public static EntityCutLines spawn(Level level, Vec3 center) {
        EntityCutLines e = new EntityCutLines(ModEntities.CUT_LINES.get(), level);
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
        // 每道最长约 4 格 —— 剔除箱必须覆盖，否则视线侧偏就整体消失
        return getBoundingBox().inflate(5.0);
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
