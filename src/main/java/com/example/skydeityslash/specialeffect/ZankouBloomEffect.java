package com.example.skydeityslash.specialeffect;

import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

/**
 * 「花开见血血染双瞳」：按下特殊行动键蓄势，之后**接下来 3 次攻击**每次都额外结算 52 点真实伤害
 * 并在目标身上绽开一簇暗红血花；3 次用完即止。
 * 行为在 {@code SkydeitySlash.GameEvents} 中实现（特殊键 → 加次数；命中 → 结算并递减）。
 *
 * 名称沿用 zankou 专属的原版暗红（§4 = #AA0000）：纯色、不渐变、不加粗。
 */
public class ZankouBloomEffect extends SpecialEffect {
    public ZankouBloomEffect() {
        super(50, true, true);
    }

    @Override
    public Component getDescription() {
        int col = 0xAA0000; // MC 原版 dark_red
        return Component.literal("花开见血血染双瞳")
                .withStyle(st -> st.withColor(TextColor.fromRgb(col)).withItalic(false).withBold(false));
    }
}
