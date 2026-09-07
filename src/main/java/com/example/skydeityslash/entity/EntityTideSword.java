package com.example.skydeityslash.entity;

import mods.flammpfeil.slashblade.entity.EntityHeavyRainSwords;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 潮汐幻影剑：与普通幻影剑相同，但发射（下落）速度更慢。
 */
public class EntityTideSword extends EntityHeavyRainSwords {
    /** 下落速度（普通幻影剑为 4.0） */
    private static final float FALL_SPEED = 2.0f;

    private long tideFireTime = -1;

    public EntityTideSword(EntityType<? extends EntityTideSword> type, Level level) {
        super(type, level);
    }

    @Override
    public void rideTick() {
        if (itFired()) {
            if (tideFireTime <= tickCount) {
                setPos(position());
                setRot(getYRot(), -90.0F);
                stopRiding();
                Vec3 motion = new Vec3(0, -1, 0);
                shoot(motion.x, motion.y, motion.z, FALL_SPEED, 2.0f);
                tickCount = 0;
                return;
            }
        } else {
            setDeltaMovement(Vec3.ZERO);
            baseTick();
            setPos(position());
            setRot(getYRot(), -90.0F);
            if (!itFired()) {
                tideFireTime = tickCount + 10 + getDelay();
                doFire();
            }
        }
    }
}
