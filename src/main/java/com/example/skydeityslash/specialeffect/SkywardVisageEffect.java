package com.example.skydeityslash.specialeffect;

import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;

/**
 * 「触及苍穹永恒的面容」：按下特殊移动键（SPRINT）激活，效果持续期间刀身发出天青色光芒。
 * 激活与刀身发光的逻辑在 SkydeitySlash.GameEvents#onInputCommand / onUpdate 中实现。
 */
public class SkywardVisageEffect extends SpecialEffect {
    public SkywardVisageEffect() {
        super(50, true, true);
    }
}