package com.example.skydeityslash.recipe;

import com.example.skydeityslash.SkydeitySlash;
import com.example.skydeityslash.registry.ModRecipes;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 附魔装置（Enchanting Apparatus）多方块合成配方。
 * 核心理念（来自 ars_nouveau）：
 *   - reagent       ：放入装置中央的基底/核心物品
 *   - pedestalItems ：散放在四周奥术基座上的耗材（无序匹配）
 *   - output        ：合成产物
 *   - keepNbt       ：是否保留核心物品的 NBT
 *   - blade         ：可选的拔刀定义注册名；设置后产物为对应的完整拔刀（带模型/贴图/技能）
 */
public class EnchantingApparatusRecipe implements Recipe<Container> {

    public static final RecipeType<EnchantingApparatusRecipe> TYPE = new RecipeType<>() {
        @Override
        public String toString() {
            return SkydeitySlash.MODID + ":enchanting_apparatus";
        }
    };

    private final ResourceLocation id;
    private final Ingredient reagent;
    private final List<Ingredient> pedestalItems;
    private final ItemStack result;
    private final boolean keepNbt;
    /** 拔刀定义注册名；为 null 时按普通物品产物处理 */
    @Nullable
    private final ResourceLocation bladeId;

    public EnchantingApparatusRecipe(ResourceLocation id, Ingredient reagent,
                                     List<Ingredient> pedestalItems, ItemStack result,
                                     boolean keepNbt, @Nullable ResourceLocation bladeId) {
        this.id = id;
        this.reagent = reagent;
        this.pedestalItems = pedestalItems;
        this.result = result;
        this.keepNbt = keepNbt;
        this.bladeId = bladeId;
    }

    /**
     * 多方块匹配：核心物匹配 + 基座耗材数量相等 + 耗材无序匹配。
     */
    public boolean isMatch(ItemStack catalyst, List<ItemStack> inputs) {
        if (!reagent.test(catalyst)) return false;
        List<ItemStack> in = inputs.stream().filter(s -> !s.isEmpty()).collect(Collectors.toList());
        if (in.size() != pedestalItems.size()) return false;
        // 无序匹配：每个配方耗材都能对应一个尚未匹配的基座物品
        boolean[] used = new boolean[in.size()];
        return matchBacktrack(0, in, used);
    }

    private boolean matchBacktrack(int idx, List<ItemStack> in, boolean[] used) {
        if (idx == pedestalItems.size()) return true;
        Ingredient need = pedestalItems.get(idx);
        for (int i = 0; i < in.size(); i++) {
            if (used[i]) continue;
            if (need.test(in.get(i))) {
                used[i] = true;
                if (matchBacktrack(idx + 1, in, used)) return true;
                used[i] = false;
            }
        }
        return false;
    }

    /**
     * 生成产物（可选择保留核心物品 NBT）。
     * 若配方指定了拔刀定义，则产物为对应拔刀（带模型/贴图/技能）。
     */
    public ItemStack getResult(List<ItemStack> pedestalInputs, ItemStack reagentStack,
                               @Nullable RegistryAccess registryAccess) {
        if (bladeId != null && registryAccess != null) {
            try {
                var ref = registryAccess.lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY)
                        .get(ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY, bladeId));
                if (ref.isPresent()) {
                    ItemStack blade = ref.get().value().getBlade().copy();
                    if (keepNbt && reagentStack.hasTag()) {
                        blade.getOrCreateTag().merge(reagentStack.getTag().copy());
                    }
                    return blade;
                }
            } catch (Exception ignored) {
            }
        }
        ItemStack out = result.copy();
        if (keepNbt && reagentStack.hasTag()) {
            out.setTag(reagentStack.getTag().copy());
        }
        return out;
    }

    public boolean keepNbtOfReagent() {
        return keepNbt;
    }

    public List<Ingredient> getPedestalItems() {
        return pedestalItems;
    }

    // ---------- 以下是 Recipe<Container> 接口必要实现 ----------

    @Override
    public boolean matches(Container container, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.add(reagent);
        list.addAll(pedestalItems);
        return list;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.APPARATUS_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return TYPE;
    }

    /** 配方 JSON 序列化器（与 ars_nouveau 的 enchanting_apparatus 格式一致）。 */
    public static class Serializer implements RecipeSerializer<EnchantingApparatusRecipe> {

        @Override
        public EnchantingApparatusRecipe fromJson(ResourceLocation id, JsonObject json) {
            Ingredient reagent = Ingredient.fromJson(GsonHelper.getAsJsonArray(json, "reagent"));
            JsonObject outputJson = GsonHelper.getAsJsonObject(json, "output");
            ItemStack output = ShapedRecipe.itemStackFromJson(outputJson);
            boolean keepNbt = json.has("keepNbtOfReagent")
                    && GsonHelper.getAsBoolean(json, "keepNbtOfReagent");
            ResourceLocation bladeId = outputJson.has("blade")
                    ? ResourceLocation.tryParse(outputJson.get("blade").getAsString())
                    : null;
            List<Ingredient> pedestalItems = new ArrayList<>();
            JsonArray arr = GsonHelper.getAsJsonArray(json, "pedestalItems");
            for (JsonElement el : StreamSupport.stream(arr.spliterator(), false)
                    .collect(Collectors.toList())) {
                if (el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    if (obj.has("item") || obj.has("tag")) {
                        pedestalItems.add(Ingredient.fromJson(obj));
                    }
                } else {
                    pedestalItems.add(Ingredient.fromJson(el));
                }
            }
            return new EnchantingApparatusRecipe(id, reagent, pedestalItems, output, keepNbt, bladeId);
        }

        @Override
        public EnchantingApparatusRecipe fromNetwork(ResourceLocation id, net.minecraft.network.FriendlyByteBuf buf) {
            Ingredient reagent = Ingredient.fromNetwork(buf);
            ItemStack output = buf.readItem();
            boolean keepNbt = buf.readBoolean();
            ResourceLocation bladeId = buf.readBoolean() ? buf.readResourceLocation() : null;
            int size = buf.readInt();
            List<Ingredient> pedestalItems = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                try {
                    pedestalItems.add(Ingredient.fromNetwork(buf));
                } catch (Exception e) {
                    break;
                }
            }
            return new EnchantingApparatusRecipe(id, reagent, pedestalItems, output, keepNbt, bladeId);
        }

        @Override
        public void toNetwork(net.minecraft.network.FriendlyByteBuf buf, EnchantingApparatusRecipe recipe) {
            recipe.reagent.toNetwork(buf);
            buf.writeItem(recipe.result);
            buf.writeBoolean(recipe.keepNbt);
            buf.writeBoolean(recipe.bladeId != null);
            if (recipe.bladeId != null) {
                buf.writeResourceLocation(recipe.bladeId);
            }
            buf.writeInt(recipe.pedestalItems.size());
            for (Ingredient i : recipe.pedestalItems) {
                i.toNetwork(buf);
            }
        }
    }
}