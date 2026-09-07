package com.example.skydeityslash.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 露米：仙乡的赠别礼带来的增益。持续持有可累计攻击力提升，
 * buff 等级与时长在玩家攻击时刷新（叠加逻辑见 SkydeitySlash.GameEvents.applyLumi）。
 * 攻击力加成档位由 GameEvents 在 onUpdate 中按等级与时长套用属性修饰符。
 */
public class LumiEffect extends MobEffect {
    public LumiEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFD066);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return false; // 无自动结算，攻击力提升由 onUpdate 处理
    }
}