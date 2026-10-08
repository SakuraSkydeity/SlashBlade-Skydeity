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
 * 「向阳」**第二形态：天降打击**的程序化光束（视觉实体，**伤害由 {@code ability.IroiXiangyangArt} 结算**）。
 *
 * <p>一整套炮击演出（几何见 {@code client.IroiBeamGeometry}）：
 * 高空先亮起**引导环**（环心十字星）、垂下一条**瞄准引线**、落点积起一小簇**聚能**（蓄势 8 tick）→
 * 光束从顶端扫到命中点（5 tick）→ 命中瞬间落点**闪光**、随后原版紫尘沿光柱浮起 → 消散。
 * 光束本身是一根**锥形光柱**（越靠炮口越粗）＋一根**同色亮芯**（同一种紫，中心更亮而不是更白 ⇒ 不分层）。
 * ★ **命中点不带任何贴地光斑/圆环**（地面保持干净）。实体位置 = **命中点**，
 * 水平方位角决定"光是从哪一侧打下来的"，长度倍率决定这道比别的长还是短。
 *
 * <p>服务端只负责存活计时与同步方位角/长度；**几何完全由 {@code client.IroiBeamGeometry} 每帧现算**
 * （连续几何面，不是粒子、也不用贴图）。参考同类实现：{@link EntityChikuiFlower} / {@link EntityChikuiArc}。
 */
public class EntityIroiBeam extends Entity {

    /** 存在时长（tick）—— 蓄势 8 + 扫下 5 + 保持约 13 + 淡出 16；与 {@code IroiBeamGeometry.LIFETIME} 对齐 */
    public static final int LIFETIME = 42;

    /** 光束**基准**长度（格）—— 实际长度 = 本值 × 长度倍率；与 {@code IroiBeamGeometry.LENGTH} 保持一致 */
    public static final double BEAM_LENGTH = 38.0;

    /** 长度倍率的允许上限（剔除箱按它算，别让最长的那些被提前剔掉） */
    private static final double MAX_SCALE = 1.6;

    /**
     * 水平方位角（度）—— **光束相对命中点"从哪一侧的斜上方来"**。
     * 用同步数据而不是 {@code setYRot}：实体出生那一帧就要朝向正确，否则第一帧会看到光束歪一下。
     */
    private static final EntityDataAccessor<Float> BEAM_YAW = SynchedEntityData.defineId(
            EntityIroiBeam.class, EntityDataSerializers.FLOAT);

    /** 长度倍率（1.0 = {@link #BEAM_LENGTH} 格）—— 一次连射的几道**长短不一**靠它 */
    private static final EntityDataAccessor<Float> BEAM_SCALE = SynchedEntityData.defineId(
            EntityIroiBeam.class, EntityDataSerializers.FLOAT);

    public EntityIroiBeam(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    /** 在 {@code hit}（命中点）生成一道斜光束，长度取默认倍率 */
    public static EntityIroiBeam spawn(Level level, Vec3 hit, float yawDeg) {
        return spawn(level, hit, yawDeg, 1F);
    }

    /**
     * 在 {@code hit}（命中点，一般取锁定目标的身体中心或准星落点）生成一道斜光束。
     *
     * @param yawDeg      水平方位角（MC 约定：0 = +Z，90 = −X），决定光束从哪个方向斜着打过来
     * @param lengthScale 长度倍率（1.0 = {@link #BEAM_LENGTH} 格），用来做"长短不一"
     */
    public static EntityIroiBeam spawn(Level level, Vec3 hit, float yawDeg, float lengthScale) {
        EntityIroiBeam e = new EntityIroiBeam(ModEntities.IROI_BEAM.get(), level);
        e.setPos(hit.x, hit.y, hit.z);
        e.entityData.set(BEAM_YAW, yawDeg);
        e.entityData.set(BEAM_SCALE, lengthScale <= .05F ? 1F : lengthScale);
        level.addFreshEntity(e);
        return e;
    }

    /** 光束的水平方位角（度） */
    public float getBeamYaw() {
        return entityData.get(BEAM_YAW);
    }

    /** 光束的长度倍率（1.0 = 基准长度） */
    public float getLengthScale() {
        return entityData.get(BEAM_SCALE);
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
        // 光束最长可达"基准 × 1.6"格 —— 剔除箱必须罩住整条光，否则视线侧偏时整道会被"整体剔除"而消失
        return getBoundingBox().inflate(BEAM_LENGTH * MAX_SCALE + 6.0);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(BEAM_YAW, 0F);
        this.entityData.define(BEAM_SCALE, 1F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(BEAM_YAW, tag.getFloat("Yaw"));
        entityData.set(BEAM_SCALE, tag.contains("Scale") ? tag.getFloat("Scale") : 1F);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Yaw", entityData.get(BEAM_YAW));
        tag.putFloat("Scale", entityData.get(BEAM_SCALE));
    }
}
