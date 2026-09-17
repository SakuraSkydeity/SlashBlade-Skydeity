package com.example.skydeityslash.specialeffect;

import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

/**
 * 「吻痕窥梦梦魇生花」：zankou 专属。
 *  · **被动**：受到远程伤害时闪避（**直接复用** odette·翾风回雪那套实现
 *    {@code GameEvents#dodgeRangedDamage}，不另写一份）。
 *  · **每 7 次命中**：在目标身上划出几道横着的细红圆柱刀痕（{@code EntityCutLines}），
 *    并给目标施加**缓慢 10**。
 * 行为在 {@code SkydeitySlash.GameEvents#onBladeHit / onLivingIncomingDamage} 中实现。
 *
 * 名称沿用 zankou 专属的原版暗红（§4 = #AA0000）：纯色、不渐变、不加粗。
 */
public class ZankouDreamEffect extends SpecialEffect {
    public ZankouDreamEffect() {
        super(40, true, true);
    }

    @Override
    public Component getDescription() {
        int col = 0xAA0000; // MC 原版 dark_red
        return Component.literal("吻痕窥梦梦魇生花")
                .withStyle(st -> st.withColor(TextColor.fromRgb(col)).withItalic(false).withBold(false));
    }
}
