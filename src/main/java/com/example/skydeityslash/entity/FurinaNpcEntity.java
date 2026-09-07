package com.example.skydeityslash.entity;

import com.example.skydeityslash.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * 芙宁娜人形 NPC：丢下 furina 刀时生成，站桩、无敌、无 AI、不可推动。
 * 右键（主人）回收原刀；超时自动消失，避免实体堆积。
 * 贴图固定为 furina_npc.png，绝不使用玩家自身皮肤。
 * 生成后从掉落点受原版重力自然下落，落地或累计下落超过 5 格后停止。
 */
public class FurinaNpcEntity extends PathfinderMob {
    private static final int MAX_LIFETIME = 20 * 60 * 10; // 10 分钟未回收自动消失
    private static final double MAX_FALL = 5.0; // 累计下落超过 5 格后停止

    /** 主人 UUID 同步到客户端，供渲染器计算朝向（面向主人） */
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(
            FurinaNpcEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    protected UUID ownerId;
    protected ItemStack bladeStack = ItemStack.EMPTY;
    private int life;
    private double fallDistance;
    private boolean stopped;

    public FurinaNpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.setCustomNameVisible(false);
        this.setInvulnerable(true);
    }

    public static FurinaNpcEntity spawn(Level level, UUID ownerId, ItemStack blade, Vec3 pos) {
        FurinaNpcEntity npc = new FurinaNpcEntity(ModEntities.FURINA_NPC.get(), level);
        npc.initNpc(ownerId, blade, pos);
        level.addFreshEntity(npc);
        return npc;
    }

    /** 子类（如胡桃 NPC）复用：设置归属、刀与出生位置 */
    protected void initNpc(UUID ownerId, ItemStack blade, Vec3 pos) {
        // 使用刀掉落时的原始位置，让 NPC 受原版重力自然下落
        this.setPos(pos.x, pos.y, pos.z);
        this.ownerId = ownerId;
        this.bladeStack = blade.copy();
        this.entityData.set(DATA_OWNER, Optional.ofNullable(ownerId));
    }

    public boolean isOwner(Player player) {
        return ownerId != null && ownerId.equals(player.getUUID());
    }

    public ItemStack getBladeStack() { return bladeStack; }

    /** 客户端渲染用：主人 UUID（通过数据同步器同步） */
    public UUID getOwnerUuid() { return entityData.get(DATA_OWNER).orElse(null); }

    /** 彻底隐藏名字标签 */
    @Override
    public boolean shouldShowName() { return false; }

    @Override
    public Component getCustomName() { return null; }

    @Override
    protected void registerGoals() {}

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public void knockback(double strength, double x, double z) {}

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_OWNER, Optional.empty());
    }

    /** 原版重力下落（服务端），落地或停止后不再受重力影响 */
    @Override
    public void travel(Vec3 vec) {
        if (!this.level().isClientSide) {
            if (stopped) {
                this.setDeltaMovement(0, 0, 0);
                return;
            }
            Vec3 delta = this.getDeltaMovement();
            double y = delta.y - 0.08;
            if (y < -2.5) y = -2.5;
            this.setDeltaMovement(new Vec3(delta.x * 0.91, y, delta.z * 0.91));
            this.move(MoverType.SELF, this.getDeltaMovement());
        } else {
            super.travel(vec);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (++life > MAX_LIFETIME) { discard(); }

        if (!stopped) {
            if (this.onGround()) {
                stopped = true;
                setDeltaMovement(0, 0, 0);
            } else {
                fallDistance += Math.max(0, -getDeltaMovement().y);
                if (fallDistance > MAX_FALL) {
                    stopped = true;
                    setDeltaMovement(0, 0, 0);
                }
            }
        }

        // 面向主人：只旋转朝向，不移动
        if (ownerId != null) {
            Player owner = level().getPlayerByUUID(ownerId);
            if (owner != null && owner.isAlive()) {
                double dx = owner.getX() - this.getX();
                double dz = owner.getZ() - this.getZ();
                float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
                this.setYRot(yaw);
                this.yBodyRot = yaw;
                this.yHeadRot = yaw;
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("OwnerId", ownerId);
        if (!bladeStack.isEmpty()) tag.put("Blade", bladeStack.save(new CompoundTag()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OwnerId")) ownerId = tag.getUUID("OwnerId");
        if (tag.contains("Blade")) bladeStack = ItemStack.of(tag.getCompound("Blade"));
    }
}
