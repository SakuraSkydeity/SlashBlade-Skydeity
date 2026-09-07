package com.example.skydeityslash.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

/** 带灰色 lore 说明的方块物品。 */
public class LoreBlockItem extends BlockItem {

    private final String lore;

    public LoreBlockItem(Block block, Properties props, String lore) {
        super(block, props);
        this.lore = lore;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(lore));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}