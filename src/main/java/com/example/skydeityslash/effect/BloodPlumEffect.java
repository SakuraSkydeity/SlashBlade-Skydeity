package com.example.skydeityslash.effect;

import com.example.skydeityslash.SkydeitySlash;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * 血梅香：每级每秒直接扣除 10 点真伤（无视护甲/抗性/限伤）。
 * 等级为 5 的倍数时的爆炸伤害（50% 最大生命）由 SkydeitySlash.GameEvents 在叠加时触发。
 */
public class BloodPlumEffect extends MobEffect {
    public BloodPlumEffect() {
        super(MobEffectCategory.HARMFUL, 0xFF2D2D);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0; // 每秒结算一次
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        SkydeitySlash.GameEvents.applyTrueDamage(entity, null, 10.0f * (amplifier + 1));
    }
}
