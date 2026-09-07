package com.example.skydeityslash.specialeffect;

import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;

/**
 * 「幻灵夜舞」：召唤白色幻影剑环绕身边造成持续伤害，
 * 帧伤 52，每秒增加 52 点，持续 10 秒后不变。行为在 SkydeitySlash.GameEvents 中实现。
 */
public class PhantomDanceEffect extends SpecialEffect {
    public PhantomDanceEffect() {
        super(30, true, true);
    }
}
