package com.example.skydeityslash.specialeffect;

import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;

/**
 * 「流涌灵息之刺」：按特殊移动键激活，10 秒内拔刀剑左/右键攻击转为两倍真实伤害
 * （每次攻击造成两次魔法伤害）。行为在 SkydeitySlash.GameEvents 中实现。
 */
public class FlowRushEffect extends SpecialEffect {
    public FlowRushEffect() {
        super(50, true, true);
    }
}
