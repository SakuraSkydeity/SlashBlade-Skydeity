package com.example.skydeityslash.jei;

import com.example.skydeityslash.SkydeitySlash;
import com.example.skydeityslash.recipe.EnchantingApparatusRecipe;
import com.example.skydeityslash.registry.ModBlocks;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.List;

/**
 * JEI 配方类别：中央显示核心物(reagent)，外围显示基座耗材，右侧为产物。
 */
public class EnchantingApparatusRecipeCategory
        implements IRecipeCategory<EnchantingApparatusRecipe> {

    private static final int WIDTH = 106;
    private static final int HEIGHT = 76;
    private static final int GRID_X = 6;
    private static final int GRID_Y = 6;
    private static final int SLOT = 26;          // 加大格子间距，让四周间隔更明显
    private static final int OUTPUT_X = 88;      // 产物在右侧，与 3x3 网格隔开一段距离
    private static final int OUTPUT_Y = 32;      // 垂直居中

    /** 3x3 网格中外围 8 个槽位（col,row），index 0..7 顺时针 */
    private static final int[][] RING = {
            {0, 0}, {1, 0}, {2, 0}, {2, 1},
            {2, 2}, {1, 2}, {0, 2}, {0, 1}
    };

    private final IDrawable background;
    private final IDrawable icon;

    public EnchantingApparatusRecipeCategory(IGuiHelper helper) {
        this.background = helper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = helper.createDrawableIngredient(
                VanillaTypes.ITEM_STACK,
                new ItemStack(ModBlocks.FROST_MOON_BASE_ITEM.get()));
    }

    @Override
    public mezz.jei.api.recipe.RecipeType<EnchantingApparatusRecipe> getRecipeType() {
        return ModJeiPlugin.RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.skydeityslash.enchanting_apparatus");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, EnchantingApparatusRecipe recipe, IFocusGroup focuses) {
        List<Ingredient> inputs = recipe.getIngredients();
        // 核心物(reagent)放 3x3 正中间
        builder.addSlot(RecipeIngredientRole.INPUT, GRID_X + SLOT, GRID_Y + SLOT)
                .addIngredients(inputs.get(0));
        // 基座耗材放 3x3 外围一圈
        for (int i = 1; i < inputs.size() && i <= 8; i++) {
            int col = RING[i - 1][0], row = RING[i - 1][1];
            builder.addSlot(RecipeIngredientRole.INPUT, GRID_X + col * SLOT, GRID_Y + row * SLOT)
                    .addIngredients(inputs.get(i));
        }
        // 产物放在最右侧、与 3x3 网格隔开一段距离
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .addItemStack(computeOutput(recipe));
    }

    /** 计算展示用产物：拔刀配方生成完整拔刀，普通配方复制 JSON 产物 */
    private static ItemStack computeOutput(EnchantingApparatusRecipe recipe) {
        Ingredient reagent = recipe.getIngredients().get(0);
        ItemStack[] reagentStacks = reagent.getItems();
        ItemStack reagentStack = reagentStacks.length > 0 ? reagentStacks[0] : ItemStack.EMPTY;
        RegistryAccess registryAccess = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.registryAccess() : null;
        return recipe.getResult(Collections.emptyList(), reagentStack, registryAccess);
    }
}