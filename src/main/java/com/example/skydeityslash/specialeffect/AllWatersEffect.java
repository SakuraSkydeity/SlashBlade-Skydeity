package com.example.skydeityslash.specialeffect;

import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;

/**
 * 「众水、众方、众民与众律法」：手持时每秒恢复 2% 最大生命，
 * 每秒提升 20% 攻击力（最高 +200%）。行为在 SkydeitySlash.GameEvents#onUpdate 中实现。
 */
public class AllWatersEffect extends SpecialEffect {
    public AllWatersEffect() {
        super(30, true, true);
    }
}
