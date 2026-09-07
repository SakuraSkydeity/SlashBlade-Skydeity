package com.example.skydeityslash.emi;

import com.example.skydeityslash.SkydeitySlash;
import com.example.skydeityslash.recipe.EnchantingApparatusRecipe;
import com.example.skydeityslash.registry.ModBlocks;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * EMI 插件：把新月台多方块合成表显示到 EMI 中。
 * 未安装 EMI 时本类不会被 EmiEntrypoint 扫描加载。
 */
@EmiEntrypoint
public class ModEmiPlugin implements EmiPlugin {

    public static final EmiRecipeCategory CATEGORY = new EmiRecipeCategory(
            new ResourceLocation(SkydeitySlash.MODID, "enchanting_apparatus"),
            EmiStack.of(new ItemStack(ModBlocks.FROST_MOON_BASE_ITEM.get())));

    @Override
    public void register(EmiRegistry registry) {
        registry.addCategory(CATEGORY);
        registry.addWorkstation(CATEGORY, EmiStack.of(new ItemStack(ModBlocks.FROST_MOON_BASE_ITEM.get())));

        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        for (EnchantingApparatusRecipe recipe
                : level.getRecipeManager().getAllRecipesFor(EnchantingApparatusRecipe.TYPE)) {
            registry.addRecipe(new EnchantingApparatusEmiRecipe(recipe));
        }
    }
}