package com.example.skydeityslash.entity;

import com.example.skydeityslash.SkydeitySlash;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * 「枫丹」深蓝幻影剑：继承潮汐幻影剑（垂直下落较慢，改用真伤直扣），
 * 命中时对目标直接扣除伤害等额的真伤（无视护甲/抗性/限伤），并按伤害等额治疗持刀者（吸血）。
 */
public class EntityFontaineTideSword extends EntityTideSword {
    public EntityFontaineTideSword(EntityType<EntityFontaineTideSword> type, Level level) {
        super(type, level);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (getShooter() instanceof LivingEntity owner
                && result.getEntity() instanceof LivingEntity target) {
            float dmg = (float) getDamage();
            SkydeitySlash.GameEvents.applyTrueDamage(target, owner, dmg);
            owner.heal(dmg);
        }
        discard();
    }
}
