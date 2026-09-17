package com.example.skydeityslash.registry;

import com.example.skydeityslash.SkydeitySlash;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 自定义剑技（SA）注册表。
 * SA 决定释放时进入哪个连击状态（ComboState）。
 */
public class ModSlashArts {
    public static final DeferredRegister<SlashArts> SLASH_ARTS =
            DeferredRegister.create(SlashArts.REGISTRY_KEY, SkydeitySlash.MODID);

    /** 「枫丹」万众狂欢：释放后进入 fontaine_carnival_combo 连击状态，名字显示为渐变（白→浅蓝 + 浅青→天蓝） */
    public static final RegistryObject<SlashArts> FONTAINE_CARNIVAL_SA =
            SLASH_ARTS.register("fontaine_carnival",
                    () -> new SlashArts(e -> ModComboStates.FONTAINE_CARNIVAL_COMBO.getId()) {
                        @Override
                        public Component getDescription() {
                            return gradient(FC_CHARS, FC_COLORS);
                        }
                    });

    /** 「璃月」蝶引来生：释放后进入 liyue_butterfly_combo 连击状态（效果空置），名字显示为渐变（红→浅红 + 紫红→浅紫） */
    public static final RegistryObject<SlashArts> LIYUE_BUTTERFLY_SA =
            SLASH_ARTS.register("liyue_butterfly",
                    () -> new SlashArts(e -> ModComboStates.LIYUE_BUTTERFLY_COMBO.getId()) {
                        @Override
                        public Component getDescription() {
                            return gradient(LB_CHARS, LB_COLORS);
                        }
                    });

    /** 「翾风回雪」：释放后进入 xuanfeng_combo 连击状态，名字显示为渐变（深灰→浅灰 + 白→浅蓝） */
    public static final RegistryObject<SlashArts> XUANFENG_SA =
            SLASH_ARTS.register("xuanfeng_huixue",
                    () -> new SlashArts(e -> ModComboStates.XUANFENG_COMBO.getId()) {
                        @Override
                        public Component getDescription() {
                            return gradient(XF_CHARS, XF_COLORS);
                        }
                    });

    /** 「离人泪·曲断魂」：「狐」剑技，名字显示为渐变（提亮+鲜艳、非斜体、不加粗），释放后进入 li_ren_lei_combo */
    public static final RegistryObject<SlashArts> LI_REN_LEI_SA =
            SLASH_ARTS.register("li_ren_lei",
                    () -> new SlashArts(e -> ModComboStates.LI_REN_LEI_COMBO.getId()) {
                        @Override
                        public Component getDescription() {
                            Component cn = Component.empty();
                            int n = Math.min(SA_CHARS.length(), SA_COLORS.length);
                            for (int i = 0; i < n; i++) {
                                String ch = SA_CHARS.substring(i, i + 1);
                                int col = vivid(SA_COLORS[i]);
                                cn = Component.empty().append(cn).append(Component.literal(ch)
                                        .withStyle(s -> s.withColor(TextColor.fromRgb(col)).withItalic(false).withBold(false)));
                            }
                            return cn;
                        }
                    });

    /** 「诺德卡莱」霜结的誓金枝：linnea 专属 SA，名字显示为渐变（浅蓝「诺德卡莱」+ 橙黄霜结的誓金枝），释放后进入 golden_branch_combo */
    public static final RegistryObject<SlashArts> GOLDEN_BRANCH_SA =
            SLASH_ARTS.register("golden_branch",
                    () -> new SlashArts(e -> ModComboStates.GOLDEN_BRANCH_COMBO.getId()) {
                        @Override
                        public Component getDescription() {
                            Component cn = Component.empty();
                            int n = Math.min(GB_CHARS.length(), GB_COLORS.length);
                            for (int i = 0; i < n; i++) {
                                String ch = GB_CHARS.substring(i, i + 1);
                                int col = vivid(GB_COLORS[i]);
                                cn = Component.empty().append(cn).append(Component.literal(ch)
                                        .withStyle(s -> s.withColor(TextColor.fromRgb(col)).withItalic(false).withBold(false)));
                            }
                            return cn;
                        }
                    });

    /** 「诺德卡莱」为夜增辉与君遥伴：columbina 专属 SA，释放后进入 columbina_combo（目标处 frostflourish + 身边 frost_nova），名字为渐变 */
    public static final RegistryObject<SlashArts> COLUMBINA_SA =
            SLASH_ARTS.register("columbina",
                    () -> new SlashArts(e -> ModComboStates.COLUMBINA_COMBO.getId()) {
                        @Override
                        public Component getDescription() {
                            Component cn = Component.empty();
                            int n = Math.min(CB_CHARS.length(), CB_COLORS.length);
                            for (int i = 0; i < n; i++) {
                                String ch = CB_CHARS.substring(i, i + 1);
                                int col = CB_COLORS[i];
                                cn = Component.empty().append(cn).append(Component.literal(ch)
                                        .withStyle(s -> s.withColor(TextColor.fromRgb(col)).withItalic(false).withBold(false)));
                            }
                            return cn;
                        }
                    });

    /** 逐字符渐变着色（直接使用给定颜色，不额外提亮，非斜体、不加粗） */
    private static Component gradient(String chars, int[] colors) {
        Component cn = Component.empty();
        int n = Math.min(chars.length(), colors.length);
        for (int i = 0; i < n; i++) {
            String ch = chars.substring(i, i + 1);
            int col = colors[i];
            cn = Component.empty().append(cn).append(Component.literal(ch)
                    .withStyle(s -> s.withColor(TextColor.fromRgb(col)).withItalic(false).withBold(false)));
        }
        return cn;
    }

    /** 提亮 + 提饱和（让渐变更亮、更鲜艳） */
    private static int vivid(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        double l = ((max + min) / 2.0) / 255.0;
        double s = (max == min) ? 0 : (max - min) / (double) (255 - Math.abs(max + min - 255));
        l = Math.min(1.0, l * 1.12 + 0.10);   // 提亮
        s = Math.min(1.0, Math.max(s, 0.80)); // 提饱和
        double c = (1 - Math.abs(2 * l - 1)) * s;
        double hp = hueOf(rgb);
        double x = c * (1 - Math.abs((hp / 60.0) % 2 - 1));
        double m = l - c / 2;
        double rr, gg, bb;
        switch (((int) hp / 60) % 6) {
            case 0 -> { rr = c; gg = x; bb = 0; }
            case 1 -> { rr = x; gg = c; bb = 0; }
            case 2 -> { rr = 0; gg = c; bb = x; }
            case 3 -> { rr = 0; gg = x; bb = c; }
            case 4 -> { rr = x; gg = 0; bb = c; }
            default -> { rr = c; gg = 0; bb = x; }
        }
        int R = (int) Math.round((rr + m) * 255);
        int G = (int) Math.round((gg + m) * 255);
        int B = (int) Math.round((bb + m) * 255);
        return (Math.min(255, R) << 16) | (Math.min(255, G) << 8) | Math.min(255, B);
    }

    private static double hueOf(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        if (max == min) return 0;
        double d = max - min, h;
        if (max == r) h = (g - b) / d + (g < b ? 6 : 0);
        else if (max == g) h = (b - r) / d + 2;
        else h = (r - g) / d + 4;
        return h * 60;
    }

    private static final String SA_CHARS = "离人泪·曲断魂「金饰红珠」离恨楼·曲罢魂散空余人";

    /** 离人泪·曲断魂(墨蓝→蓝→墨绿) 「金饰红珠」(橘→亮梅红) 离恨楼·曲罢魂散空余人(墨绿→叶绿→墨蓝) */
    private static final int[] SA_COLORS = {
            0x35456E, 0x3D4F82, 0x46619C, 0x4F72B4, 0x48708F, 0x466C5E, 0x3F5B4A,
            0xF58F2D, 0xF78543, 0xF97B59, 0xFB726E, 0xFD6884, 0xFF5E9A,
            0x3C5A47, 0x446B4B, 0x4C7F52, 0x579159, 0x61A663, 0x6CB86E,
            0x63A16E, 0x5A8A74, 0x4E7480, 0x435E74, 0x38465E
    };

    private static final String GB_CHARS = "「诺德卡莱」霜结的誓金枝";

    /** 「诺德卡莱」(浅蓝 #87CEEB→#A5F3FC) 霜结的誓金枝(橙黄 #FF9500→#FFD700) */
    private static final int[] GB_COLORS = {
            0x87CEEB, 0x8DD5EE, 0x93DDF2, 0x99E4F5, 0x9FECF9, 0xA5F3FC,
            0xFF9500, 0xFFA200, 0xFFAF00, 0xFFBD00, 0xFFCA00, 0xFFD700
    };

    private static final String FC_CHARS = "「枫丹」万众狂欢";

    /** 「枫丹」(白→浅蓝) 万众狂欢(浅青→天蓝) */
    private static final int[] FC_COLORS = {
            0xFFFFFF, 0xEBF7FF, 0xD6EEFF, 0xC2E6FF,
            0x55FFFF, 0x70FCFF, 0x8AF9FF, 0xA5F6FF
    };

    private static final String LB_CHARS = "「璃月」蝶引来生";

    /** 「璃月」(红→浅红) 蝶引来生(紫红→浅紫) */
    private static final int[] LB_COLORS = {
            0xFF5555, 0xFF6A6A, 0xFF8080, 0xFF9595,
            0xFF55FF, 0xFF70FF, 0xFF8AFF, 0xFFA5FF
    };

    private static final String CB_CHARS = "「诺德卡莱」为夜增辉与君遥伴";

    /** 「诺德卡莱」(浅蓝 #87CEEB→#A5F3FC，与 linnea 的 golden_branch 同款) 为夜增辉与君遥伴(蓝色渐变到天蓝色) */
    private static final int[] CB_COLORS = {
            0x87CEEB, 0x8DD5EE, 0x93DDF2, 0x99E4F5, 0x9FECF9, 0xA5F3FC,
            0x1E90FF, 0x2C9AFF, 0x3BA5FF, 0x49AFEB, 0x58BAEF, 0x66C4F3, 0x74CDFF, 0x87CEEB
    };

    private static final String XF_CHARS = "「至冬」翾风回雪";

    /** 「至冬」(深灰→浅灰) 翾风回雪(白→浅蓝) */
    private static final int[] XF_COLORS = {
            0x555555, 0x666666, 0x777777, 0x888888,
            0xFFFFFF, 0xEBF7FF, 0xD6EEFF, 0xC2E6FF
    };

    /** 「向阳」九千五百万年前的分歧：iroi 专属 SA，释放后进入 iroi_xiangyang_combo，名字渐变（金「向阳」+ 紫「九千五百万年前的分歧」） */
    public static final RegistryObject<SlashArts> IROI_SA =
            SLASH_ARTS.register("iroi_xiangyang",
                    () -> new SlashArts(e -> ModComboStates.IROI_XIANGYANG_COMBO.getId()) {
                        @Override
                        public Component getDescription() {
                            return gradient(IROI_CHARS, IROI_COLORS);
                        }
                    });

    private static final String IROI_CHARS = "「向阳」九千五百万年前的分歧";

    /** 「向阳」(金 #F7C030→浅金) 九千五百万年前的分歧(紫 #BA55D3→浅紫) */
    private static final int[] IROI_COLORS = {
            0xF7C030, 0xFACB4A, 0xFCDA60, 0xFFEA78,
            0xBA55D3, 0xC35EDB, 0xCC67E3, 0xD570EB, 0xDE79F2, 0xE782FA, 0xF08BFF, 0xE992FF, 0xE39BFF, 0xE2A5FF
    };

    /**
     * 「赤葵」焚天烬灭舞：zankou 专属 SA，释放后进入 chikui_combo 连击状态，
     * 特效 = 斩击真伤 + 暗红摆线（顶端立着密集火焰）+ 6 把幻影剑（`ability/ChikuiFentian`），
     * 名字为渐变：「赤葵」#E40C4D 系 / 焚天烬灭舞 #710404 系。
     */
    public static final RegistryObject<SlashArts> CHIKUI_SA =
            SLASH_ARTS.register("chikui_fentian",
                    () -> new SlashArts(e -> ModComboStates.CHIKUI_COMBO.getId()) {
                        @Override
                        public Component getDescription() {
                            return gradient(CHIKUI_CHARS, CHIKUI_COLORS);
                        }
                    });

    private static final String CHIKUI_CHARS = "「赤葵」焚天烬灭舞";

    /** 「赤葵」(#9B012F→#E40145，深绯→赤) 焚天烬灭舞(#710404→#873232，暗血红) */
    private static final int[] CHIKUI_COLORS = {
            0x9B012F, 0xB30137, 0xCC013E, 0xE40145,
            0x710404, 0x761010, 0x7C1B1B, 0x812626, 0x873232
    };
}
