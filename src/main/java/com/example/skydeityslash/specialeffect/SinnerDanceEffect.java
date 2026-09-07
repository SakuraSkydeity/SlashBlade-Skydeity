package com.example.skydeityslash.specialeffect;

import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;

/**
 * 「罪人舞步旋」：手持时每秒给周围 5 格内生物施加虚弱 5 与缓慢 10。
 * 行为在 SkydeitySlash.GameEvents#onUpdate 中实现。
 */
public class SinnerDanceEffect extends SpecialEffect {
    public SinnerDanceEffect() {
        super(40, true, true);
    }
}
