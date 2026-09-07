package com.example.skydeityslash.specialeffect;

import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;

/**
 * 「幽蝶能留一缕芳」：手持时原地不动 3 秒后，将半径 3 格内所有生物生命值降为 50%，
 * 每一轮静止周期只触发一次，玩家移动后才可再次静止 3 秒触发。
 * 行为在 SkydeitySlash.GameEvents 中实现。
 */
public class PlumBlossomEffect extends SpecialEffect {
    public PlumBlossomEffect() {
        super(40, true, true);
    }
}
