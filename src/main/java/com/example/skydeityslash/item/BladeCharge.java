package com.example.skydeityslash.item;

import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 拔刀剑「充能」的**通用数据层** —— 与具体是哪把刀无关，与画在哪儿也无关。
 *
 * <p>要点：
 * <ul>
 *   <li>数值存在**物品自己的 NBT**里（{@link #NBT_CHARGE}）。SlashBlade 的
 *       {@code ItemSlashBlade.getShareTag()} 返回的就是 {@code stack.getTag()}（把 bladeState 塞进同一个
 *       tag），所以这个键会**随容器同步自动传到客户端**，不需要自定义包。</li>
 *   <li>每把刀可以有自己的上限与配色，按**刀定义名**（{@code named_blades/<名>.json} 的文件名）登记，
 *       没登记过的刀既不显示条也不参与充能。</li>
 *   <li>{@link #NBT_OWNER} 记"上一任持有者"。换了人或离开过背包，第一次右键时会被判为不匹配而清零。</li>
 * </ul>
 */
public final class BladeCharge {
    private BladeCharge() {}

    /** 充能值的 NBT 键 */
    public static final String NBT_CHARGE = "skydeity_charge";
    /** 持有者 UUID 的 NBT 键 */
    public static final String NBT_OWNER = "skydeity_charge_owner";

    /**
     * 一把刀的充能配置。
     *
     * <p>★ 这里**只管条**（每把刀的条造型一样，只换颜色与上限）。**环不做抽象** —— 每把刀的环造型
     * 都不一样，各写自己的类（如 {@code client/FurinaHalo}），由渲染入口按刀名分发。
     *
     * @param max   满值（右键一次 +1）
     * @param stops 充能条的**色标**（ARGB），从左到右；2 个 = 普通双端渐变，
     *              N 个 = 多段渐变（如 sakurafox 的 淡绿→深绿→墨绿→深蓝→浅蓝）。
     *              整条 13 列按 {@code stops} 均摊插值，所以段数多于列数也没问题。
     */
    public record Config(int max, int[] stops) {
        public Config {
            if (stops == null || stops.length == 0) {
                stops = new int[]{0xFFFFFFFF, 0xFFFFFFFF};
            } else {
                stops = stops.clone();   // 防止外部数组被改
            }
        }
    }

    /** 造一个配置（色标用变参，写起来短一点）：{@code grad(30, 左, 右)} 或 {@code grad(30, c1, c2, c3, ...)}。 */
    public static Config grad(int max, int... stops) {
        return new Config(max, stops);
    }

    /** 默认满值（右键一次 +1，所以也就是"右键多少次"） */
    public static final int DEFAULT_MAX = 30;

    /** 写死颜色的刀：刀定义名 → 配置 */
    private static final Map<String, Config> CONFIGS = new HashMap<>();

    /**
     * **颜色跟着刀自己走**的刀：条的两端由这把刀的 `state.getColorCode()`（= json 里的
     * {@code summon_sword_color} / 幻影剑色）现场推出来 ⇒ 以后改 json 的颜色，条会跟着变，
     * 不用动代码。想微调就直接把它们挪进 {@link #register} 那一种、写死颜色。
     */
    private static final Set<String> AUTO_THEMED = new HashSet<>();

    /** 登记一把刀并**写死**颜色（优先于自动配色）。 */
    public static void register(String bladeName, Config config) {
        CONFIGS.put(bladeName, config);
    }

    /** 登记一把刀，条的配色**从这把刀自身的颜色**自动推（亮一档 → 暗一档）。 */
    public static void registerAuto(String bladeName) {
        AUTO_THEMED.add(bladeName);
    }

    static {
        // ★ 下面这些都是**手工调过**的颜色（写死）。只有还在 registerAuto 里的刀才走"照主题色自动推"。

        // 芙宁娜「枫丹」：饱和蓝 → 深蓝
        register("slash_furina", grad(30,
                0xFF2E7BFF, 0xFF0A2E8A));

        // 奥黛塔：奶白 → 蓝白
        // ★ 中间那个色标是必须的：奶白与蓝白都是高明度低饱和，两点直接线性插值会在中段滑过一段**中性灰**
        //   （算出来正好是 #E7E7E7），垫一个带蓝的冰白就把这一下岔开了。
        register("odette", grad(30,
                0xFFFFF5E2,   // 奶白
                0xFFDCE9FF,   // 冰蓝白（过渡）
                0xFFA8C8FF)); // 蓝白

        // 残虹：深红（同色相内做一点明暗，免得整条死板的纯色）
        register("zankou", grad(30,
                0xFFAE1028,   // 深红
                0xFF56030F)); // 更深的暗红

        // 胡桃：桃红 → 梅红 → 深梅
        register("hutao", grad(30,
                0xFFFF9BB0,   // 桃红
                0xFFB0304F,   // 梅红
                0xFF5E0E26)); // 深梅

        // 樱狐：淡绿 → 深绿 → 墨绿 → 深蓝 → 浅蓝（5 段）
        register("sakurafox", grad(30,
                0xFFB6F0BE,   // 淡绿
                0xFF1F7A3D,   // 深绿
                0xFF0C3A24,   // 墨绿
                0xFF123C7A,   // 深蓝
                0xFF86C8FF)); // 浅蓝

        // 其余高阶刀：条由各刀自己的颜色自动推（吃 json 的 summon_sword_color），配色不对再单独改。
        registerAuto("columbina");    // #2E62A6 蓝
        registerAuto("linnea");       // #8B6508 金枝棕
        registerAuto("iroi");         // #FF55FF 粉紫
        // 中间刀 genshin / nevertoever 是锻造材料，不参与充能。
    }

    /**
     * 取刀定义名（如 {@code slash_furina}）。不是拔刀剑、或状态缺失时返回 {@code null}。
     * <p>{@code state.getTranslationKey()} 形如 {@code item.skydeityslash.slash_furina}，取最后一个点之后。
     */
    public static String bladeName(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemSlashBlade)) return null;
        ISlashBladeState state = stack.getCapability(CapabilitySlashBlade.BLADESTATE).orElse(null);
        if (state == null) return null;
        String key = state.getTranslationKey();
        int dot = key.lastIndexOf('.');
        return dot < 0 ? key : key.substring(dot + 1);
    }

    /** 这把刀的充能配置；没登记过返回 {@code null}（= 不画条、不充能）。 */
    public static Config configOf(ItemStack stack) {
        String name = bladeName(stack);
        if (name == null) return null;
        Config fixed = CONFIGS.get(name);
        if (fixed != null) return fixed;
        return AUTO_THEMED.contains(name) ? themed(DEFAULT_MAX, themeColor(stack)) : null;
    }

    /** 这把刀自己的主题色（= json 的 {@code summon_sword_color}）；没设就给白。 */
    private static int themeColor(ItemStack stack) {
        ISlashBladeState state = stack.getCapability(CapabilitySlashBlade.BLADESTATE).orElse(null);
        if (state == null) return 0xFFFFFF;
        int rgb = state.getColorCode() & 0xFFFFFF;
        return rgb == 0 ? 0xFFFFFF : rgb;
    }

    /**
     * 由主题色推出一对渐变端色。★ 用 HSV 而不是"往白/黑插值"：很多刀的主题色本身很浅
     * （如奥黛塔 `#C9E6FF`），线性插到黑会变灰；这里只动**饱和度下限**与**明度**，色相始终保留
     * ⇒ 亮端是"更亮的同色"，暗端是"更深的同色"，不会脏。
     */
    private static Config themed(int max, int rgb) {
        float[] hsv = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        float hue = hsv[0], sat = hsv[1];
        int from = java.awt.Color.HSBtoRGB(hue, Math.max(.35F, sat * .80F), 1.00F) & 0xFFFFFF;
        int to = java.awt.Color.HSBtoRGB(hue, Math.min(1F, Math.max(.55F, sat)), .42F) & 0xFFFFFF;
        return grad(max, 0xFF000000 | from, 0xFF000000 | to);
    }

    public static int get(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(NBT_CHARGE);
    }

    /** 写值（会自动夹在 0~max）。返回是否真的变了。 */
    public static boolean set(ItemStack stack, int value) {
        Config cfg = configOf(stack);
        if (cfg == null) return false;
        int v = clamp(value, cfg.max());
        if (get(stack) == v) return false;
        stack.getOrCreateTag().putInt(NBT_CHARGE, v);
        return true;
    }

    /** 加 delta，返回加完之后的值。 */
    public static int add(ItemStack stack, int delta) {
        Config cfg = configOf(stack);
        if (cfg == null) return 0;
        int v = clamp(get(stack) + delta, cfg.max());
        stack.getOrCreateTag().putInt(NBT_CHARGE, v);
        return v;
    }

    /** 是否已满（满 = 身后那圈环出现）。 */
    public static boolean isFull(ItemStack stack) {
        Config cfg = configOf(stack);
        return cfg != null && get(stack) >= cfg.max();
    }

    /** 清零：充能值与持有者一起清（下次右键会重新认主）。 */
    public static void clear(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove(NBT_CHARGE);
        tag.remove(NBT_OWNER);
    }

    public static UUID owner(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(NBT_OWNER) ? tag.getUUID(NBT_OWNER) : null;
    }

    public static void setOwner(ItemStack stack, UUID id) {
        stack.getOrCreateTag().putUUID(NBT_OWNER, id);
    }

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(max, v));
    }
}
