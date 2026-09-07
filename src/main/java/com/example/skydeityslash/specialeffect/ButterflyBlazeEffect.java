package com.example.skydeityslash.specialeffect;

import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;

/**
 * 「蝶火燎原」：手持时每秒恢复 10 点生命；受到致死伤害时免疫死亡、
 * 扣除 10 级经验并给予伤害吸收 10。行为在 SkydeitySlash.GameEvents 中实现。
 */
public class ButterflyBlazeEffect extends SpecialEffect {
    public ButterflyBlazeEffect() {
        super(30, true, true);
    }
}
