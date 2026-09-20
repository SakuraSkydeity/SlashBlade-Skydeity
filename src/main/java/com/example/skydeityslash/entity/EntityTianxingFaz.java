package com.example.skydeityslash.entity;

import com.example.skydeityslash.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * 天星·地面/天空法阵（复刻 ysjxspells FazEntity + FazRenderer）。
 * 地面法阵（array_bg）：释放后从小到大展开，并作为整条时间线的主控；
 * 天空法阵（array_sky）：高空引导法阵，天星自其中心斜向坠下。
 * 纯视觉，无碰撞无 AI。
 */
public class EntityTianxingFaz extends Entity {
    public static final int VARIANT_DADI = 3;
    public static final int VARIANT_DADI_2 = 4;

    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(
            EntityTianxingFaz.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> VISIBLE = SynchedEntityData.defineId(
            EntityTianxingFaz.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> WARMUP_TICKS = SynchedEntityData.defineId(
            EntityTianxingFaz.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTIVE_TICKS = SynchedEntityData.defineId(
            EntityTianxingFaz.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VISUAL_VARIANT = SynchedEntityData.defineId(
            EntityTianxingFaz.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> EXPANSION_TICKS = SynchedEntityData.defineId(
            EntityTianxingFaz.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> PHASE_START = SynchedEntityData.defineId(
            EntityTianxingFaz.class, EntityDataSerializers.LONG);

    /** 地面法阵主控的时间线标记（仅服务端） */
    private boolean skySpawned;
    private boolean stoneSpawned;
    private Vec3 skyPos;

    public EntityTianxingFaz(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    /** 地面法阵（array_bg）：从小到大展开，并主控「天空法阵 → 天星斜落」时间线。 */
    public static EntityTianxingFaz spawnGround(Level level, Vec3 pos, float radius) {
        EntityTianxingFaz e = new EntityTianxingFaz(ModEntities.TIANXING_FAZ.get(), level);
        e.setPos(pos.x, pos.y, pos.z);
        e.entityData.set(VISUAL_VARIANT, VARIANT_DADI);
        e.entityData.set(RADIUS, radius);
        e.entityData.set(WARMUP_TICKS, 0);
        e.entityData.set(ACTIVE_TICKS, 88);
        e.entityData.set(VISIBLE, true);
        e.entityData.set(EXPANSION_TICKS, 26);
        e.setPhaseAge(0);
        level.addFreshEntity(e);
        return e;
    }

    /** 天空法阵（array_sky）：高空引导法阵。 */
    public static EntityTianxingFaz spawnSky(Level level, Vec3 pos, float radius) {
        EntityTianxingFaz e = new EntityTianxingFaz(ModEntities.TIANXING_FAZ.get(), level);
        e.setPos(pos.x, pos.y, pos.z);
        e.entityData.set(VISUAL_VARIANT, VARIANT_DADI_2);
        e.entityData.set(RADIUS, radius);
        e.entityData.set(WARMUP_TICKS, 0);
        e.entityData.set(ACTIVE_TICKS, 44);
        e.entityData.set(VISIBLE, true);
        e.entityData.set(EXPANSION_TICKS, 12);
        e.setPhaseAge(0);
        level.addFreshEntity(e);
        return e;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        int age = getPhaseAgeTicks();
        int warmup = entityData.get(WARMUP_TICKS);
        int active = entityData.get(ACTIVE_TICKS);
        if (age >= warmup + active) {
            discard();
            return;
        }
        if (entityData.get(VISUAL_VARIANT) == VARIANT_DADI) {
            if (age >= 34 && !skySpawned) {
                skySpawned = true;
                skyPos = computeSkyPos();
                spawnSky(level(), skyPos, 5.0f);
            }
            if (age >= 42 && !stoneSpawned) {
                stoneSpawned = true;
                if (skyPos == null) skyPos = computeSkyPos();
                EntityTianxingStone.spawn(level(), skyPos, position());
            }
        }
    }

    /** 天空法阵位置：相对地面中心水平偏移 4.5 格、抬高 14 格，令天星斜向坠落。 */
    private Vec3 computeSkyPos() {
        double ang = level().random.nextDouble() * Math.PI * 2;
        double off = 4.5;
        return position().add(Math.cos(ang) * off, 14.0, Math.sin(ang) * off);
    }

    // ---- 渲染取数（与 FazEntity 同逻辑） ----
    public boolean isVisibleSigil() { return entityData.get(VISIBLE); }
    public float getRadius() { return entityData.get(RADIUS); }
    public int getVisualVariant() { return entityData.get(VISUAL_VARIANT); }

    public float getRenderAlpha(float partial) {
        float age = getPhaseAge(partial);
        int warmup = entityData.get(WARMUP_TICKS);
        int active = entityData.get(ACTIVE_TICKS);
        if (age < warmup) return 0.0f;
        float aa = age - warmup;
        float fadeIn = Mth.clamp(aa / 8.0f, 0.0f, 1.0f);
        float fadeOut = Mth.clamp((active - aa) / 12.0f, 0.0f, 1.0f);
        return Math.min(fadeIn, fadeOut) * 0.72f;
    }

    public float getExpansionProgress(float partial) {
        float age = getPhaseAge(partial);
        int warmup = entityData.get(WARMUP_TICKS);
        if (age < warmup) return 0.0f;
        float aa = age - warmup;
        float p = Mth.clamp(aa / entityData.get(EXPANSION_TICKS), 0.0f, 1.0f);
        return 1.0f - (1.0f - p) * (1.0f - p);
    }

    public float getRenderRotation(float partial) {
        return getPhaseAge(partial) * 2.5f;
    }

    private void setPhaseAge(int age) { entityData.set(PHASE_START, level().getGameTime() - age); }
    private int getPhaseAgeTicks() { return (int) (level().getGameTime() - entityData.get(PHASE_START)); }
    private float getPhaseAge(float partial) { return level().getGameTime() + partial - entityData.get(PHASE_START); }

    @Override
    protected void defineSynchedData() {
        entityData.define(RADIUS, 5.0f);
        entityData.define(VISIBLE, false);
        entityData.define(WARMUP_TICKS, 12);
        entityData.define(ACTIVE_TICKS, 120);
        entityData.define(VISUAL_VARIANT, 0);
        entityData.define(EXPANSION_TICKS, 24);
        entityData.define(PHASE_START, 0L);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(RADIUS, tag.getFloat("Radius"));
        entityData.set(WARMUP_TICKS, tag.getInt("WarmupTicks"));
        entityData.set(ACTIVE_TICKS, tag.getInt("ActiveTicks"));
        entityData.set(VISUAL_VARIANT, tag.getInt("VisualVariant"));
        entityData.set(EXPANSION_TICKS, tag.getInt("ExpansionTicks"));
        entityData.set(PHASE_START, tag.getLong("PhaseStart"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Radius", entityData.get(RADIUS));
        tag.putInt("WarmupTicks", entityData.get(WARMUP_TICKS));
        tag.putInt("ActiveTicks", entityData.get(ACTIVE_TICKS));
        tag.putInt("VisualVariant", entityData.get(VISUAL_VARIANT));
        tag.putInt("ExpansionTicks", entityData.get(EXPANSION_TICKS));
        tag.putLong("PhaseStart", entityData.get(PHASE_START));
    }

    @Override
    public boolean isPickable() { return false; }

    /**
     * 距离剔除：超过"固定余量 + 法阵半径两倍"就不再渲染。
     *
     * <p>这里原本直接返回 {@code true}，等于对任意距离都渲染。但法阵的模型只有
     * 0.5×0.5 的碰撞箱，原版按碰撞箱算出的可见距离只有 32 格，远小于法阵实际铺开
     * 的范围，所以当初被强制打开了。正确做法是给出一个**随半径增长、但有上界**的
     * 距离：既不会在应该看得见的时候突然消失，也不会在几百格外还占着渲染。
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        double limit = 64.0 + getRadius() * 2.0;
        return dist < limit * limit;
    }
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
