package com.example.skydeityslash.registry;

import com.example.skydeityslash.SkydeitySlash;
import com.example.skydeityslash.specialeffect.AllWatersEffect;
import com.example.skydeityslash.specialeffect.ButterflyBlazeEffect;
import com.example.skydeityslash.specialeffect.ButterflyDanceEffect;
import com.example.skydeityslash.specialeffect.FlowRushEffect;
import com.example.skydeityslash.specialeffect.JiangwanSnowEffect;
import com.example.skydeityslash.specialeffect.PhantomDanceEffect;
import com.example.skydeityslash.specialeffect.PlumBlossomEffect;
import com.example.skydeityslash.specialeffect.SinnerDanceEffect;
import com.example.skydeityslash.specialeffect.SkywardVisageEffect;
import com.example.skydeityslash.specialeffect.XuanfengHuixueEffect;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/**
 * 自定义特效（SE）注册表。
 * SE 是附加在刀上的被动/触发效果，通过监听 SlashBladeEvent 实现行为。
 */
public class ModSpecialEffects {
    public static final DeferredRegister<SpecialEffect> SPECIAL_EFFECTS =
            DeferredRegister.create(SpecialEffect.REGISTRY_KEY, SkydeitySlash.MODID);

    /** 「众水、众方、众民与众律法」：手持时每秒恢复 2% 生命、提升 20% 攻击力（最高 +200%） */
    public static final RegistryObject<SpecialEffect> ALL_WATERS_EFFECT =
            SPECIAL_EFFECTS.register("all_waters", AllWatersEffect::new);

    /** 「罪人舞步旋」：手持时每秒给周围 5 格内生物施加虚弱 5 与缓慢 10 */
    public static final RegistryObject<SpecialEffect> SINNER_DANCE_EFFECT =
            SPECIAL_EFFECTS.register("sinner_dance", SinnerDanceEffect::new);

    /** 「蝶火燎原」：手持时每秒恢复 10 点生命；致死时免疫死亡并扣除 10 级经验、给予伤害吸收 10 */
    public static final RegistryObject<SpecialEffect> BUTTERFLY_BLAZE_EFFECT =
            SPECIAL_EFFECTS.register("butterfly_blaze", ButterflyBlazeEffect::new);

    /** 「幽蝶能留一缕芳」：手持原地不动 3 秒后，将半径 3 格内生物生命降为 50%，本轮静止只触发一次，移动后可再次触发 */
    public static final RegistryObject<SpecialEffect> PLUM_BLOSSOM_EFFECT =
            SPECIAL_EFFECTS.register("plum_blossom", PlumBlossomEffect::new);

    /** 「江晚雪霁冬」：手持时玩家获得飞行能力 */
    public static final RegistryObject<SpecialEffect> JIANGWAN_SNOW_EFFECT =
            SPECIAL_EFFECTS.register("jiangwan_snow", JiangwanSnowEffect::new);

    /** 「幻灵夜舞」：召唤白色幻影剑环绕造成持续伤害，帧伤 52，每秒 +52，10 秒后不变 */
    public static final RegistryObject<SpecialEffect> PHANTOM_DANCE_EFFECT =
            SPECIAL_EFFECTS.register("phantom_dance", PhantomDanceEffect::new);

    /** 「翾风回雪」：每次右键时跟随一道追踪剑气，造成 52 点伤害 */
    public static final RegistryObject<SpecialEffect> XUANFENG_HUIXUE_EFFECT =
            SPECIAL_EFFECTS.register("xuanfeng_huixue", XuanfengHuixueEffect::new);

    /** 「流涌灵息之刺」：按特殊移动键激活，10 秒内所有攻击转为两倍真实伤害（两次魔法伤害） */
    public static final RegistryObject<SpecialEffect> FLOW_RUSH_EFFECT =
            SPECIAL_EFFECTS.register("flow_rush", FlowRushEffect::new);

    /** 「彼岸蝶舞」：按特殊移动键激活，10 秒内所有攻击（含 SA 幻影剑）给目标叠加血梅香（等级 +1、持续 5 秒），等级为 5 的倍数时爆炸扣除 50% 最大生命 */
    public static final RegistryObject<SpecialEffect> BUTTERFLY_DANCE_EFFECT =
            SPECIAL_EFFECTS.register("butterfly_dance", ButterflyDanceEffect::new);

    /** 「触及苍穹永恒的面容」：按下特殊移动键激活，10 秒内刀身散发天青色光芒 */
    public static final RegistryObject<SpecialEffect> SKYWARD_VISAGE_EFFECT =
            SPECIAL_EFFECTS.register("skyward_visage", SkywardVisageEffect::new);

    /** 「钤印·令花神离恨无泪」：需求等级 30，墨绿→叶绿（最暗） */
    public static final RegistryObject<SpecialEffect> SEAL_BLOSSOM_EFFECT =
            SPECIAL_EFFECTS.register("seal_blossom", () -> gradientSE(30, 0x114200, 0x3A7A2E, "钤印·令花神离恨无泪"));

    /** 「钤印·芙蓉花琼楼吹彻笛声寒」：需求等级 40 */
    public static final RegistryObject<SpecialEffect> SEAL_LOTUS_EFFECT =
            SPECIAL_EFFECTS.register("seal_lotus", () -> gradientSE(40, 0x1A5A0A, 0x4C9A3A, "钤印·芙蓉花琼楼吹彻笛声寒"));

    /** 「银簪·离魂烟暖笛烟化蝶舞」：需求等级 50 */
    public static final RegistryObject<SpecialEffect> SILVER_PIN_MOTH_EFFECT =
            SPECIAL_EFFECTS.register("silver_pin_moth", () -> gradientSE(50, 0x236B14, 0x5EB346, "银簪·离魂烟暖笛烟化蝶舞"));

    /** 「银簪·尽弑天下负心人」：需求等级 60 */
    public static final RegistryObject<SpecialEffect> SILVER_PIN_SLAYER_EFFECT =
            SPECIAL_EFFECTS.register("silver_pin_slayer", () -> gradientSE(60, 0x2C7C1E, 0x70CC52, "银簪·尽弑天下负心人"));

    /** 「雨间蝶舞伞犹温，江清晓荷月近人」：需求等级 70 */
    public static final RegistryObject<SpecialEffect> RAIN_BUTTERFLY_EFFECT =
            SPECIAL_EFFECTS.register("rain_butterfly", () -> gradientSE(70, 0x358D28, 0x82E55E, "雨间蝶舞伞犹温，江清晓荷月近人"));

    /** 「笛烟和声魂断绝，墨色浸染芙蓉生」：需求等级 80 */
    public static final RegistryObject<SpecialEffect> FLUTE_SOUL_EFFECT =
            SPECIAL_EFFECTS.register("flute_soul", () -> gradientSE(80, 0x3E9E32, 0x94FE6A, "笛烟和声魂断绝，墨色浸染芙蓉生"));

    /** 「一笛清影化烟魂，落入江湖伞犹温」：需求等级 90 */
    public static final RegistryObject<SpecialEffect> FLUTE_SHADOW_EFFECT =
            SPECIAL_EFFECTS.register("flute_shadow", () -> gradientSE(90, 0x47AF3C, 0xA6FF76, "一笛清影化烟魂，落入江湖伞犹温"));

    /** 「离恨烟·公孙离」：需求等级 100，墨绿→叶绿（最亮） */
    public static final RegistryObject<SpecialEffect> GONGSUN_LI_EFFECT =
            SPECIAL_EFFECTS.register("gongsun_li", () -> gradientSE(100, 0x50C046, 0xB8FF82, "离恨烟·公孙离"));

    /** 「喜或悲的谕告」：需求等级 30，手持时每秒恢复 5 生命；致死时免死、扣除 10 级经验并喷发黄橙金粒子（名称纯橙黄、不渐变） */
    public static final RegistryObject<SpecialEffect> JOY_SORROW_OMEN_EFFECT =
            SPECIAL_EFFECTS.register("joy_sorrow_omen", () -> orangeSE(30, "喜或悲的谕告"));

    /** 「仙乡的赠别礼」：需求等级 40，攻击命中给自身叠加露米(5s，等级+1，上限10)，类雪梅香逻辑（名称纯橙黄、不渐变） */
    public static final RegistryObject<SpecialEffect> CELESTIAL_FAREWELL_EFFECT =
            SPECIAL_EFFECTS.register("celestial_farewell", () -> orangeSE(40, "仙乡的赠别礼"));

    /** 「月兆堕天的落羽」：需求等级 50，环绕飞鸟粒子+范围每帧 52 真实伤害 + 缓慢 10（名称纯橙黄、不渐变） */
    public static final RegistryObject<SpecialEffect> FALLING_MOON_FEATHER_EFFECT =
            SPECIAL_EFFECTS.register("falling_moon_feather", () -> orangeSE(50, "月兆堕天的落羽"));

    /** 「新月自己的法则」：需求等级 30，columbina 专属（名称纯浅蓝原版色、不渐变） */
    public static final RegistryObject<SpecialEffect> NEW_MOON_LAW_EFFECT =
            SPECIAL_EFFECTS.register("new_moon_law", () -> lightBlueSE(30, "新月自己的法则"));

    /** 「柔光凝露梦湖起波」：需求等级 40，columbina 专属（名称纯浅蓝原版色、不渐变） */
    public static final RegistryObject<SpecialEffect> ROUGUANG_NINGLU_EFFECT =
            SPECIAL_EFFECTS.register("rouguang_ninglu", () -> lightBlueSE(40, "柔光凝露梦湖起波"));

    /** 「花岚云翳山岩树影」：需求等级 50，columbina 专属（名称纯浅蓝原版色、不渐变） */
    public static final RegistryObject<SpecialEffect> HUALAN_YUNYI_EFFECT =
            SPECIAL_EFFECTS.register("hualan_yunyi", () -> lightBlueSE(50, "花岚云翳山岩树影"));

    /** 「自诩宇宙的精华」：需求等级 30，iroi 专属（名称纯粉紫原版色、不渐变） */
    public static final RegistryObject<SpecialEffect> IROI_SE30 =
            SPECIAL_EFFECTS.register("iroi_se30", () -> pinkPurpleSE(30, "自诩宇宙的精华"));

    /** 「未来自我连续性假设」：需求等级 40，iroi 专属（名称纯粉紫原版色、不渐变） */
    public static final RegistryObject<SpecialEffect> IROI_SE40 =
            SPECIAL_EFFECTS.register("iroi_se40", () -> pinkPurpleSE(40, "未来自我连续性假设"));

    /** 「妄想彼端的森林萤火」：需求等级 50，iroi 专属（名称纯粉紫原版色、不渐变） */
    public static final RegistryObject<SpecialEffect> IROI_SE50 =
            SPECIAL_EFFECTS.register("iroi_se50", () -> pinkPurpleSE(50, "妄想彼端的森林萤火"));

    /** 等级限制 + 纯粉紫色（MC 原版 light_purple、不渐变）名字的装饰性 SE */
    private static SpecialEffect pinkPurpleSE(int level, String text) {
        return new SpecialEffect(level) {
            @Override
            public Component getDescription() {
                int col = 0xFF55FF; // MC 原版 light_purple
                return Component.literal(text)
                        .withStyle(st -> st.withColor(TextColor.fromRgb(col)).withItalic(false).withBold(false));
            }
        };
    }

    /** 等级限制 + 纯浅蓝色（原版色系、不渐变）名字的装饰性 SE */
    private static SpecialEffect lightBlueSE(int level, String text) {
        return new SpecialEffect(level) {
            @Override
            public Component getDescription() {
                int col = 0x55FFFF; // 原版浅蓝（aqua）
                return Component.literal(text)
                        .withStyle(st -> st.withColor(TextColor.fromRgb(col)).withItalic(false).withBold(false));
            }
        };
    }

    /** 等级限制 + 纯橙黄色（原版色系、不渐变）名字的装饰性 SE */
    private static SpecialEffect orangeSE(int level, String text) {
        return new SpecialEffect(level) {
            @Override
            public Component getDescription() {
                int col = 0xFFA500; // 原版橙黄
                return Component.literal(text)
                        .withStyle(st -> st.withColor(TextColor.fromRgb(col)).withItalic(false).withBold(false));
            }
        };
    }

    /** 等级限制 + 墨色渐变名字的装饰性 SE（墨绿 → 浅墨绿，不加粗、非斜体） */
    private static SpecialEffect gradientSE(int level, int c0, int c1, String text) {
        return new SpecialEffect(level) {
            @Override
            public Component getDescription() {
                Component cn = Component.empty();
                int n = text.length();
                for (int i = 0; i < n; i++) {
                    double t = (n <= 1) ? 0 : (double) i / (n - 1);
                    int col = lerp(c0, c1, t);
                    cn = Component.empty().append(cn).append(Component.literal(text.substring(i, i + 1))
                            .withStyle(st -> st.withColor(TextColor.fromRgb(col)).withItalic(false).withBold(false)));
                }
                return cn;
            }
        };
    }

    public static int lerp(int a, int b, double t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) Math.round(ar + (br - ar) * t);
        int g = (int) Math.round(ag + (bg - ag) * t);
        int bl = (int) Math.round(ab + (bb - ab) * t);
        return (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, bl);
    }
}
