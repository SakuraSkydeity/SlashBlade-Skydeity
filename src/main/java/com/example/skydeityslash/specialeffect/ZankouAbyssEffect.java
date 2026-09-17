package com.example.skydeityslash.specialeffect;

import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

/**
 * 「瞳中深渊渊底之吻」：zankou 专属，**每次命中**回 2 点生命（不超过最大生命）；
 * **每 3 次命中**在目标身上绽开一朵几何樱花（{@code EntitySakuraBloom}）、
 * 目标脚下再撒 10 片樱花花瓣向外散开，并额外结算 52 点真实伤害。
 * 行为在 {@code SkydeitySlash.GameEvents#onBladeHit} 中实现。
 *
 * 名称沿用 zankou 专属的原版暗红（§4 = #AA0000）：纯色、不渐变、不加粗。
 */
public class ZankouAbyssEffect extends SpecialEffect {
    public ZankouAbyssEffect() {
        super(30, true, true);
    }

    @Override
    public Component getDescription() {
        int col = 0xAA0000; // MC 原版 dark_red
        return Component.literal("瞳中深渊渊底之吻")
                .withStyle(st -> st.withColor(TextColor.fromRgb(col)).withItalic(false).withBold(false));
    }
}
