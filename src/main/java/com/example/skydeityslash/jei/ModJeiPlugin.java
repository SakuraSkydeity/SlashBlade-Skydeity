package com.example.skydeityslash.jei;

import com.example.skydeityslash.SkydeitySlash;
import com.example.skydeityslash.recipe.EnchantingApparatusRecipe;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI 插件：把附魔装置（新月台）多方块合成表显示到 JEI 中。
 * 表面：mods.toml 已把 jei 声明为可选依赖；未安装 JEI 时本类不会被加载。
 */
@JeiPlugin
public class ModJeiPlugin implements IModPlugin {

    public static final mezz.jei.api.recipe.RecipeType<EnchantingApparatusRecipe> RECIPE_TYPE =
            new mezz.jei.api.recipe.RecipeType<>(
                    new ResourceLocation(SkydeitySlash.MODID, "enchanting_apparatus"),
                    EnchantingApparatusRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(SkydeitySlash.MODID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new EnchantingApparatusRecipeCategory(
                        registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        List<EnchantingApparatusRecipe> recipes = new ArrayList<>();
        for (EnchantingApparatusRecipe recipe
                : level.getRecipeManager().getAllRecipesFor(EnchantingApparatusRecipe.TYPE)) {
            recipes.add(recipe);
        }
        registration.addRecipes(RECIPE_TYPE, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
                new ItemStack(com.example.skydeityslash.registry.ModBlocks.FROST_MOON_BASE_ITEM.get()),
                RECIPE_TYPE);
    }
}