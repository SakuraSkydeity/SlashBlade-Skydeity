package com.example.skydeityslash.specialeffect;

import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;

/**
 * 「彼岸蝶舞」：按特殊移动键激活，10 秒内所有攻击给目标添加血梅香状态，
 * 受到一次攻击叠一层，5 层消除并扣除目标 50% 最大生命。
 * 行为在 SkydeitySlash.GameEvents 中实现。
 */
public class ButterflyDanceEffect extends SpecialEffect {
    public ButterflyDanceEffect() {
        super(50, true, true);
    }
}
