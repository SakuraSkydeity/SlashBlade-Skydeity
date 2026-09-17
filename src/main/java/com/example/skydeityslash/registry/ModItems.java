package com.example.skydeityslash.registry;

import com.example.skydeityslash.SkydeitySlash;
import com.example.skydeityslash.item.AntiqueFanItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/**
 * 自定义物品注册表。
 */
public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, SkydeitySlash.MODID);

    /** 潮汐锭 */
    public static final RegistryObject<Item> TIDE_INGOT =
            ITEMS.register("tide_ingot", () -> new Item(new Item.Properties()));

    /** 往生锭 */
    public static final RegistryObject<Item> WANGSHENG_INGOT =
            ITEMS.register("wangsheng_ingot", () -> new Item(new Item.Properties()));

    /** 雪鹄锭 */
    public static final RegistryObject<Item> XUEHU_INGOT =
            ITEMS.register("xuehu_ingot", () -> new Item(new Item.Properties()));

    /** 离烟锭 */
    public static final RegistryObject<Item> LIHENYAN_IGNOT =
            ITEMS.register("lihenyan_ignot", () -> new Item(new Item.Properties()));

    /** 启喻鸟锭 */
    public static final RegistryObject<Item> QIYUNIAO_IGNOT =
            ITEMS.register("qiyuniao_ignot", () -> new Item(new Item.Properties()));

    /** 月鸽锭 */
    public static final RegistryObject<Item> PIGEON_IGNOT =
            ITEMS.register("pigeon_ignot", () -> new Item(new Item.Properties()));

    /** 臆想锭 */
    public static final RegistryObject<Item> IMAGINE_IGNOT =
            ITEMS.register("imagine_ignot", () -> new Item(new Item.Properties()));

    /** 赤葵锭（zankou 专属合成材料） */
    public static final RegistryObject<Item> CHIKUI_IGNOT =
            ITEMS.register("chikui_ignot", () -> new Item(new Item.Properties()));

    /** 新月之扇（右键传送彼岸维度） */
    public static final RegistryObject<Item> ANTIQUEFAN =
            ITEMS.register("antiquefan", AntiqueFanItem::new);
}
