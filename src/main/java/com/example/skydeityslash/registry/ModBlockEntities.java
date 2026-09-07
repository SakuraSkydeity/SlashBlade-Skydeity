package com.example.skydeityslash.registry;

import com.example.skydeityslash.SkydeitySlash;
import com.example.skydeityslash.blockentity.ArcanePedestalBlockEntity;
import com.example.skydeityslash.blockentity.EnchantingApparatusBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.core.registries.Registries;

/**
 * 自定义方块实体类型注册表。
 */
public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SkydeitySlash.MODID);

    /** 恒月基座方块实体（存放 1 个耗材） */
    public static final RegistryObject<BlockEntityType<ArcanePedestalBlockEntity>> ARCANE_PEDESTAL =
            BLOCK_ENTITIES.register("arcane_pedestal",
                    () -> BlockEntityType.Builder
                            .of(ArcanePedestalBlockEntity::new, ModBlocks.CONSTANT_MOON_PEDESTAL.get())
                            .build(null));

    /** 霜月祭祀方块实体（多方块合成逻辑） */
    public static final RegistryObject<BlockEntityType<EnchantingApparatusBlockEntity>> ENCHANTING_APPARATUS =
            BLOCK_ENTITIES.register("enchanting_apparatus",
                    () -> BlockEntityType.Builder
                            .of(EnchantingApparatusBlockEntity::new, ModBlocks.FROST_MOON_BASE.get())
                            .build(null));
}