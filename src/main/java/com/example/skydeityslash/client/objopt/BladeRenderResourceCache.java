package com.example.skydeityslash.client.objopt;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.init.DefaultResources;
import mods.flammpfeil.slashblade.item.ItemSlashBladeDetune;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * "刀的 NBT → 实际模型 / 贴图位置"的解析结果缓存。
 *
 * <p>原版 {@code SlashBladeTEISR.stackDefaultModel} 每次调用都要取 NBT、
 * 从 translationKey 里切出 id、查一遍注册表、再把字符串 parse 成 ResourceLocation。
 * 这个结果对同一把刀终生不变，但物品栏、掉落物、展示框每帧都会问一次。
 *
 * <p>逻辑按原版逐条复刻，只加缓存，并顺手修掉一个边界问题：原版用
 * {@code key.substring(5)} 硬切 {@code "item."} 前缀，遇到畸形 translationKey
 * 会抛 {@code StringIndexOutOfBounds}。
 */
public final class BladeRenderResourceCache {

    private static final Map<String, ResourceLocation> PARSED = new ConcurrentHashMap<>();
    private static final Map<String, ResourceLocation[]> DEFINITION = new ConcurrentHashMap<>();

    private BladeRenderResourceCache() {
    }

    public static ResourceLocation resolveModel(ItemStack stack) {
        return resolve(stack, "ModelName", true);
    }

    public static ResourceLocation resolveTexture(ItemStack stack) {
        return resolve(stack, "TextureName", false);
    }

    public static void clear() {
        PARSED.clear();
        DEFINITION.clear();
    }

    private static ResourceLocation resolve(ItemStack stack, String key, boolean model) {
        ResourceLocation fallback = model
                ? DefaultResources.resourceDefaultModel
                : DefaultResources.resourceDefaultTexture;

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("bladeState")) {
            return fallback;
        }
        CompoundTag state = stack.getTagElement("bladeState");
        if (state == null) {
            return fallback;
        }
        String name = state.getString(key);

        // Detune（未磨砺的刀）是唯一绕过定义表、直接用自己的 NBT 取模型的一类，不能走缓存。
        if (!(stack.getItem() instanceof ItemSlashBladeDetune)) {
            String translationKey = state.getString("translationKey");
            if (translationKey != null && !translationKey.isBlank()) {
                ResourceLocation[] definition = lookupDefinition(translationKey);
                ResourceLocation fromDefinition = model ? definition[0] : definition[1];
                if (fromDefinition != null) {
                    name = fromDefinition.toString();
                }
            }
        }

        if (name == null || name.isBlank()) {
            return fallback;
        }
        return parse(name, fallback);
    }

    private static ResourceLocation parse(String value, ResourceLocation fallback) {
        ResourceLocation cached = PARSED.get(value);
        if (cached != null) {
            return cached;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        ResourceLocation result = parsed != null ? parsed : fallback;
        PARSED.put(value, result);
        return result;
    }

    /**
     * 查命名刀定义表，返回 {@code [model, texture]}。未命中会缓存成长度为 2 的 null 数组，
     * 避免每帧重复查注册表。
     */
    private static ResourceLocation[] lookupDefinition(String translationKey) {
        ResourceLocation[] cached = DEFINITION.get(translationKey);
        if (cached != null) {
            return cached;
        }

        String prefix = "item.";
        ResourceLocation id = null;
        if (translationKey.startsWith(prefix) && translationKey.length() > prefix.length()) {
            String raw = translationKey.substring(prefix.length());
            int split = raw.indexOf('.');
            if (split > 0 && split < raw.length() - 1) {
                id = ResourceLocation.tryParse(raw.substring(0, split) + ":" + raw.substring(split + 1));
            }
        }

        ResourceLocation[] resolved = new ResourceLocation[2];
        if (id != null) {
            try {
                SlashBladeDefinition definition = BladeModelManager.getClientSlashBladeRegistry().get(id);
                if (definition != null) {
                    resolved[0] = definition.getRenderDefinition().getModelName();
                    resolved[1] = definition.getRenderDefinition().getTextureName();
                }
            } catch (RuntimeException ignored) {
                // 注册表还没准备好时留空，下次仍然会重新查 —— 这里不缓存失败结果之外的东西。
            }
        }

        DEFINITION.put(translationKey, resolved);
        return resolved;
    }
}
