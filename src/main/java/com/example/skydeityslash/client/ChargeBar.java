package com.example.skydeityslash.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/**
 * 通用「渐变充能条」—— 画在**原版耐久条那一格**上，配色由调用方传入，不含任何刀的专属逻辑。
 * （条的造型每把刀都一样，只有颜色不同，所以只有这一块做抽象；环不做，见 {@link FurinaHalo}。）
 *
 * <p>为什么可以直接占这一格：拔刀剑的 {@code ItemSlashBlade} 覆写了 {@code isBarVisible} 并**恒返回 false**，
 * 所以原版那条耐久条对拔刀剑永远不会出现，这个位置是空的。
 *
 * <p>★ **尺寸/位置与原版 1.20.1 逐像素一致**（照抄自 {@code GuiGraphics.renderItemDecorations}）：
 * <pre>
 *   int bx = x + 2, by = y + 13;
 *   fill(guiOverlay, bx, by, bx + 13, by + 2, 0xFF000000);            // 底衬：13×2 不透明黑
 *   fill(guiOverlay, bx, by, bx + barWidth, by + 1, color);           // 前景：**只有 1 像素高**，贴上沿
 * </pre>
 * 即：底衬 {@code (x+2, y+13)~(x+15, y+15)}，前景 {@code (x+2, y+13)~(x+2+w, y+14)}。
 * 差别只是原版前景用 {@code getBarColor()} 的绿黄红渐变，我们换成按刀传入的配色。
 *
 * <p>★ 用 {@link RenderType#guiOverlay()}（**无深度测试**）画，所以不受 z 影响、必定盖在物品图标之上 ——
 * 这正是原版的做法（原版只有"数量文字"才 `translate(0,0,200)`）。早先用普通 {@code fill()} 会被刀子模型
 * 盖住（"条显示在物品后面、看不见"），就是因为普通 GUI 类型是带深度测试的。
 *
 * <p>渐变按**整条 13 列**铺开（左端第一个色标 → 右端最后一个色标），填充时逐列揭示，
 * 所以"左边这个色、右边那个色"在任何进度下都成立。宽度按原版那样取整（{@code Math.round}）。
 *
 * <p>色标数量不限：2 个就是普通双端渐变，N 个就是多段渐变（如 sakurafox 的
 * 淡绿→深绿→墨绿→深蓝→浅蓝）。13 列在 N−1 段之间**均摊**，列数少于段数时多余的色标仍会参与插值
 * （只是落在相邻两列之间），不会丢。
 */
public final class ChargeBar {
    private ChargeBar() {}

    /** 条在槽位内的偏移：与原版一致 */
    public static final int OFFSET_X = 2, OFFSET_Y = 13;
    /** 条宽（= 原版 13 像素） */
    public static final int WIDTH = 13;
    /** 底衬高度（= 原版 2 像素） */
    public static final int TRACK_H = 2;
    /** 前景高度（= 原版 1 像素，只有底衬的上沿那一条） */
    public static final int FILL_H = 1;

    /** 底衬色：原版用的就是**不透明黑** */
    private static final int TRACK = 0xFF000000;

    /** 是否显示充能条 —— 由 Shift 切换（见 {@code ModClientEvents.onClientTick}） */
    public static boolean visible = true;

    /**
     * 画一根充能条。
     *
     * @param g     画布
     * @param slotX  槽位左上角 x（{@code IItemDecorator} 给的 xOffset）
     * @param slotY  槽位左上角 y（yOffset）
     * @param value  当前值
     * @param max    满值
     * @param stops  色标（ARGB），从左到右；至少 1 个，2 个以上才有渐变
     */
    public static void render(GuiGraphics g, int slotX, int slotY, int value, int max, int[] stops) {
        if (!visible || max <= 0 || stops == null || stops.length == 0) return;
        int x = slotX + OFFSET_X;
        int y = slotY + OFFSET_Y;
        RenderType overlay = RenderType.guiOverlay();

        // 底衬（原版同款）
        g.fill(overlay, x, y, x + WIDTH, y + TRACK_H, TRACK);

        int filled = Math.round(Mth.clamp(value / (float) max, 0F, 1F) * WIDTH);
        for (int i = 0; i < filled; i++) {
            g.fill(overlay, x + i, y, x + i + 1, y + FILL_H, colorAt(stops, i, WIDTH));
        }
    }

    /**
     * 第 i 列的颜色：把 0~1 的进度拉伸到 {@code stops.length - 1} 段上做分段线性插值（含 alpha 通道）。
     * 段与段是**均摊**的 —— 例如 5 个色标就是 4 段各占 1/4 条宽。
     */
    private static int colorAt(int[] stops, int i, int total) {
        if (stops.length == 1) return stops[0];
        float t = total <= 1 ? 0F : i / (float) (total - 1);
        float seg = t * (stops.length - 1);
        int idx = (int) seg;
        if (idx >= stops.length - 1) idx = stops.length - 2;   // 最后一列贴右端，别越界
        return lerpColor(stops[idx], stops[idx + 1], seg - idx);
    }

    private static int lerpColor(int from, int to, float t) {
        int a = lerp((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);
        int r = lerp((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, t);
        int gg = lerp((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, t);
        int b = lerp(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    private static int lerp(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }
}
