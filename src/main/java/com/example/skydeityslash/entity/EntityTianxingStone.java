package com.example.skydeityslash.entity;

import com.example.skydeityslash.particle.TianxingParticleSpawner;
import com.example.skydeityslash.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * 天星·陨石（复刻 ysjxspells ShitouEntity）。
 * 自天空法阵（array_sky）中心出发，斜向直坠向地面法阵中心；
 * 飞行中留灼热余烬拖尾，落地触发爆炸烟尘/土石飞溅（客户端粒子）。
 */
public class EntityTianxingStone extends Projectile {
    private static final double SPEED = 0.5;
    private static final double MAX_RANGE = 80.0;
    private static final int MAX_LIFETIME = 120;
    private static final byte IMPACT_VISUAL_EVENT = 4;

    private Vec3 origin = Vec3.ZERO;
    private Vec3 impactCenter;
    private boolean impacted;

    public EntityTianxingStone(EntityType<? extends EntityTianxingStone> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    /** 从天空法阵位置斜向坠向地面法阵中心。 */
    public static void spawn(Level level, Vec3 skyPos, Vec3 groundPos) {
        EntityTianxingStone e = new EntityTianxingStone(ModEntities.TIANXING_STONE.get(), level);
        Vec3 dir = groundPos.subtract(skyPos);
        Vec3 norm = dir.lengthSqr() <= 1.0E-6 ? new Vec3(0, -1, 0) : dir.normalize();
        e.setPos(skyPos.x, skyPos.y, skyPos.z);
        e.setDeltaMovement(norm.scale(SPEED));
        e.origin = skyPos;
        e.impactCenter = groundPos;
        e.updateRotationFromMotion();
        level.addFreshEntity(e);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 movement = getDeltaMovement();
        if (level().isClientSide) {
            if (movement.lengthSqr() > 1.0E-6) {
                TianxingParticleSpawner.flightTrail(level(), this);
                setPos(getX() + movement.x, getY() + movement.y, getZ() + movement.z);
                updateRotationFromMotion();
            }
            return;
        }
        if (impacted || movement.lengthSqr() <= 1.0E-6 || tickCount > MAX_LIFETIME
                || position().distanceToSqr(origin) > MAX_RANGE * MAX_RANGE) {
            discard();
            return;
        }
        if (impactCenter == null) {
            discard();
            return;
        }
        Vec3 start = position();
        Vec3 remaining = impactCenter.subtract(start);
        if (remaining.lengthSqr() <= movement.lengthSqr() || remaining.dot(movement) <= 0.0) {
            handleImpact(impactCenter);
            return;
        }
        Vec3 end = start.add(movement);
        setPos(end.x, end.y, end.z);
        updateRotationFromMotion();
    }

    private void handleImpact(Vec3 center) {
        if (impacted) {
            return;
        }
        impacted = true;
        setPos(center.x, center.y, center.z);
        if (level() instanceof ServerLevel server) {
            server.broadcastEntityEvent(this, IMPACT_VISUAL_EVENT);
            server.playSound(null, center.x, center.y, center.z,
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2f, 0.85f + server.random.nextFloat() * 0.2f);
        }
        discard();
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == IMPACT_VISUAL_EVENT) {
            TianxingParticleSpawner.impact(level(), position());
            return;
        }
        super.handleEntityEvent(id);
    }

    private void updateRotationFromMotion() {
        Vec3 movement = getDeltaMovement();
        double horizontal = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
        if (horizontal < 1.0E-6) {
            return;
        }
        float yaw = (float) (Math.atan2(movement.x, movement.z) * (180.0 / Math.PI));
        float pitch = (float) (Math.atan2(movement.y, horizontal) * (180.0 / Math.PI));
        if (tickCount <= 1) {
            yRotO = yaw;
            xRotO = pitch;
        }
        setYRot(yaw);
        setXRot(pitch);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("OriginX")) {
            this.origin = new Vec3(tag.getDouble("OriginX"), tag.getDouble("OriginY"), tag.getDouble("OriginZ"));
        }
        if (tag.contains("ImpactX")) {
            this.impactCenter = new Vec3(tag.getDouble("ImpactX"), tag.getDouble("ImpactY"), tag.getDouble("ImpactZ"));
        }
        this.impacted = tag.getBoolean("Impacted");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("OriginX", this.origin.x);
        tag.putDouble("OriginY", this.origin.y);
        tag.putDouble("OriginZ", this.origin.z);
        if (this.impactCenter != null) {
            tag.putDouble("ImpactX", this.impactCenter.x);
            tag.putDouble("ImpactY", this.impactCenter.y);
            tag.putDouble("ImpactZ", this.impactCenter.z);
        }
        tag.putBoolean("Impacted", this.impacted);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return true;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
