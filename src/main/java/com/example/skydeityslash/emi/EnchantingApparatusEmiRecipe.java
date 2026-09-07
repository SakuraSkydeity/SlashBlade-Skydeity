package com.example.skydeityslash.emi;

import com.example.skydeityslash.recipe.EnchantingApparatusRecipe;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Collections;

/** EMI 显示：3×3 网格、核心物居中、四周耗材间隔加大，产物单独放最右侧并隔开一段距离。 */
public class EnchantingApparatusEmiRecipe extends BasicEmiRecipe {

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

    public EnchantingApparatusEmiRecipe(EnchantingApparatusRecipe recipe) {
        super(ModEmiPlugin.CATEGORY, recipe.getId(), WIDTH, HEIGHT);
        for (Ingredient ingredient : recipe.getIngredients()) {
            this.inputs.add(EmiIngredient.of(ingredient));
        }
        this.outputs.add(EmiStack.of(computeOutput(recipe)));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        // 核心物(reagent)放 3x3 正中间
        widgets.addSlot(this.inputs.get(0), GRID_X + SLOT, GRID_Y + SLOT).recipeContext(this);
        // 基座耗材放 3x3 外围一圈
        for (int i = 1; i < this.inputs.size() && i <= 8; i++) {
            int col = RING[i - 1][0], row = RING[i - 1][1];
            widgets.addSlot(this.inputs.get(i), GRID_X + col * SLOT, GRID_Y + row * SLOT)
                    .recipeContext(this);
        }
        // 产物单独放最右侧、与 3x3 网格隔开一段距离
        widgets.addSlot(this.outputs.get(0), OUTPUT_X, OUTPUT_Y).recipeContext(this);
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