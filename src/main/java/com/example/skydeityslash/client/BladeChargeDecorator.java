package com.example.skydeityslash.client;

import com.example.skydeityslash.item.BladeCharge;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.IItemDecorator;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;

/**
 * 把充能条挂到拔刀剑上。
 *
 * <p>装饰器只能按 **Item** 注册（所有命名刀都是 {@code slashblade:slashblade}），所以这里注册一次，
 * 进来以后按刀定义名过滤 —— 只有 {@link BladeCharge} 里登记过配色的刀才画条。
 *
 * <p>调用点在 {@code GuiGraphics.renderItemDecorations} 的**最后**（此时 pose 已出栈），
 * 所以拿到的 {@code xOffset / yOffset} 是槽位的绝对坐标，直接用它算原版耐久条那一格。
 */
public final class BladeChargeDecorator implements IItemDecorator {

    private static final ResourceLocation SLASHBLADE_ID = new ResourceLocation("slashblade", "slashblade");

    /** 在 mod 事件总线上登记（仅客户端）。 */
    public static void register(RegisterItemDecorationsEvent event) {
        Item blade = BuiltInRegistries.ITEM.get(SLASHBLADE_ID);
        if (blade == Items.AIR) return;
        event.register(blade, new BladeChargeDecorator());
    }

    @Override
    public boolean render(GuiGraphics gui, Font font, ItemStack stack, int xOffset, int yOffset) {
        BladeCharge.Config cfg = BladeCharge.configOf(stack);
        if (cfg == null) return false;
        ChargeBar.render(gui, xOffset, yOffset, BladeCharge.get(stack), cfg.max(), cfg.stops());
        // 只用了 GuiGraphics 的 fill，没动 RenderState ⇒ 返回 false
        return false;
    }
}
