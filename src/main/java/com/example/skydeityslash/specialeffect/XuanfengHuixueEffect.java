package com.example.skydeityslash.specialeffect;

import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;

/**
 * 「翾风回雪」：手持时给予自身力量 10；受到远程伤害（弓箭/魔法等）时闪避，
 * 并给予 1 秒隐身与烟花爆炸粒子特效。行为在 SkydeitySlash.GameEvents 中实现。
 */
public class XuanfengHuixueEffect extends SpecialEffect {
    public XuanfengHuixueEffect() {
        super(40, true, true);
    }
}
