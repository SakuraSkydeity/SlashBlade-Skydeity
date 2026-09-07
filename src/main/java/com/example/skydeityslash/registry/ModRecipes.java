package com.example.skydeityslash.registry;

import com.example.skydeityslash.SkydeitySlash;
import com.example.skydeityslash.recipe.EnchantingApparatusRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 自定义配方注册表（附魔装置多方块合成）。
 */
public class ModRecipes {

    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, SkydeitySlash.MODID);

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, SkydeitySlash.MODID);

    /** 附魔装置多方块合成类型 */
    public static final RegistryObject<RecipeType<?>> APPARATUS_TYPE =
            TYPES.register("enchanting_apparatus", () -> EnchantingApparatusRecipe.TYPE);

    /** 附魔装置配方序列化器 */
    public static final RegistryObject<RecipeSerializer<?>> APPARATUS_SERIALIZER =
            SERIALIZERS.register("enchanting_apparatus", EnchantingApparatusRecipe.Serializer::new);

    @SuppressWarnings("unchecked")
    public static RecipeType<EnchantingApparatusRecipe> apparatusType() {
        return (RecipeType<EnchantingApparatusRecipe>) APPARATUS_TYPE.get();
    }
}