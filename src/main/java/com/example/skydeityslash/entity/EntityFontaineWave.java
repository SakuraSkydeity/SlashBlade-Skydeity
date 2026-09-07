package com.example.skydeityslash.entity;

import com.example.skydeityslash.SkydeitySlash;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * 「枫丹」剑气：继承内置刀波（改用真伤直扣），命中时对目标直接扣除伤害等额的
 * 真伤（无视护甲/抗性/限伤），并按伤害等额治疗持刀者（吸血），随后消散。
 */
public class EntityFontaineWave extends EntityDrive {
    public EntityFontaineWave(EntityType<? extends mods.flammpfeil.slashblade.entity.Projectile> type, Level level) {
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
