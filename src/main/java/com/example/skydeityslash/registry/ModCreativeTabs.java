package com.example.skydeityslash.registry;

import com.example.skydeityslash.SkydeitySlash;
import com.example.skydeityslash.registry.ModBlocks;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/**
 * 自定义创造模式标签页注册表。
 */
public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SkydeitySlash.MODID);

    /** 潮汐页：图标为枫丹刀（furina），包含锭与全部拔刀 */
    public static final RegistryObject<CreativeModeTab> TIDE_TAB =
            CREATIVE_TABS.register("tide_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.skydeityslash.tide"))
                    .icon(() -> {
                        ItemStack stack = new ItemStack(SBItems.slashblade);
                        stack.getCapability(CapabilitySlashBlade.BLADESTATE, null).ifPresent(state -> {
                            state.setModel(SkydeitySlash.prefix("model/named/columbina.obj"));
                            state.setTexture(SkydeitySlash.prefix("model/named/columbina.png"));
                        });
                        return stack;
                    })
                    .displayItems((params, output) -> {
                        output.accept(new ItemStack(ModItems.TIDE_INGOT.get()));
                        output.accept(new ItemStack(ModItems.WANGSHENG_INGOT.get()));
                        output.accept(new ItemStack(ModItems.XUEHU_INGOT.get()));
                        output.accept(new ItemStack(ModItems.LIHENYAN_IGNOT.get()));
                        output.accept(new ItemStack(ModItems.QIYUNIAO_IGNOT.get()));
                        output.accept(new ItemStack(ModItems.PIGEON_IGNOT.get()));
                        output.accept(new ItemStack(ModItems.IMAGINE_IGNOT.get()));
                        output.accept(new ItemStack(ModItems.CHIKUI_IGNOT.get()));
                        output.accept(new ItemStack(ModItems.ANTIQUEFAN.get()));
                        output.accept(new ItemStack(ModBlocks.CONSTANT_MOON_PEDESTAL_ITEM.get()));
                        output.accept(new ItemStack(ModBlocks.FROST_MOON_BASE_ITEM.get()));
                        addBlade(params, output, "slash_furina");
                        addBlade(params, output, "hutao");
                        addBlade(params, output, "odette");
                        addBlade(params, output, "linnea");
                        addBlade(params, output, "columbina");
                        addBlade(params, output, "sakurafox");
                        addBlade(params, output, "iroi");
                        addBlade(params, output, "zankou");
                    })
                    .build());

    private static void addBlade(CreativeModeTab.ItemDisplayParameters params,
                                 CreativeModeTab.Output output, String name) {
        var bladeKey = ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY,
                SkydeitySlash.prefix(name));
        params.holders().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY)
                .get(bladeKey)
                .ifPresent(def -> output.accept(def.value().getBlade()));
    }
}
