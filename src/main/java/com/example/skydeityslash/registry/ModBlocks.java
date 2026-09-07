package com.example.skydeityslash.registry;

import com.example.skydeityslash.SkydeitySlash;
import com.example.skydeityslash.block.ArcanePedestalBlock;
import com.example.skydeityslash.block.EnchantingApparatusBlock;
import com.example.skydeityslash.item.LoreBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.core.registries.Registries;

/**
 * 自定义方块注册表（附魔装置多方块合成组件）。
 * 同时把对应的方块物品注册到 ModItems.ITEMS，便于获取与放入创造栏。
 */
public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, SkydeitySlash.MODID);

    /** 霜月祭祀（多方块合成中央方块，原名附魔装置） */
    public static final RegistryObject<Block> FROST_MOON_BASE =
            BLOCKS.register("frost_moon_base", EnchantingApparatusBlock::new);

    /** 恒月基座（放耗材，原名奥术基座） */
    public static final RegistryObject<Block> CONSTANT_MOON_PEDESTAL =
            BLOCKS.register("constant_moon_pedestal", ArcanePedestalBlock::new);

    /** 方块物品（带灰色 lore：可从彼岸维度最西方找到） */
    private static final String BLOCK_LORE = "\u00a77好像可以在新月之扇传送的维度最西方找到";

    public static final RegistryObject<Item> FROST_MOON_BASE_ITEM =
            ModItems.ITEMS.register("frost_moon_base",
                    () -> new LoreBlockItem(FROST_MOON_BASE.get(), new Item.Properties(), BLOCK_LORE));

    public static final RegistryObject<Item> CONSTANT_MOON_PEDESTAL_ITEM =
            ModItems.ITEMS.register("constant_moon_pedestal",
                    () -> new LoreBlockItem(CONSTANT_MOON_PEDESTAL.get(), new Item.Properties(), BLOCK_LORE));
}