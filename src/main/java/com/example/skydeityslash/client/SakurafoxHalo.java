package com.example.skydeityslash.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;

/**
 * 樱狐满充能法环 —— 一套**循环动画**，一段一段轮着来（时间轴见类顶部常量）：
 *
 * <pre>
 * ① 双环自转        {@link #T_SPIN}       tick（10 秒）—— 内外两张贴图环反向转
 * ② 快速放大        {@link #T_GROW}       tick（0.6 秒）—— 转完后整组从 1.0 放到 {@link #GROW_TO}
 * ③ 换成"拉线"图                       —— **直接出现**（无放缩动画），位置见 {@link #LINE}
 * ④ 停留            {@link #T_HOLD}       tick（2 秒）—— 线保持在 {@link #LINE} 那个位置
 * ⑤ 回到 ①，整轮 {@link #CYCLE} ≈ 12.6 秒
 * </pre>
 *
 * <p>时钟用 {@code player.tickCount + partialTick}（与自转同源），所以两端各自计算也一致、别人看你也是同一相位。
 *
 * <p>贴图都在 {@code textures/cycring_entity/}：{@code sakurafox_cyc_out/in.png}（双环）与
 * {@code sakurafox_line.png}（拉线）。机制在 {@link TexturedHalo}。
 *
 * <p>★ 双环与拉线都用**普通半透明**（{@code additive = false}）：贴图是暗墨绿（主色 `#0C201C` / `#285C52` / `#163A34`），
 * 加法混合在亮背景上等于没加、还只显示贴图里亮的部分；半透明是"替换 + 按 alpha 混合"，暗色反而清晰。
 * 拉线沿用同一份 {@code CFG} 的 tint 与混合方式。
 */
public final class SakurafoxHalo {
    private SakurafoxHalo() {}

    // ==================== 双环 ====================
    /**
     * 双环（内/外），两张都 32×32、都铺 1.77 格 ⇒ 每像素 0.055 格。
     *
     * <p>★ 位置在**画面左上**：`SIDE` 传的是 `+SIDE`（正 = 玩家左手侧；第三人称从背后看不镜像
     * ⇒ 画面左）。芙宁娜/残虹走默认的 `SIDE`（负 ⇒ 画面右上），所以樱狐是"另一边"。
     */
    public static final TexturedHalo.Cfg CFG = TexturedHalo.Cfg.behind(
            "sakurafox",
            1.77F, 1.77F,
            .30F, -.52F,               // 外 / 内 自转（度每 tick，反向）
            new float[]{1F, 1F, 1F},   // 不加滤镜（贴图本身已是设计好的配色）
            .92F,                      // 半透明下 alpha = 真正的不透明度
            false,                     // 半透明（暗色贴图）
            -TexturedHalo.SIDE);       // ★ 左右镜像 ⇒ 落到画面左上（"另一边"）

    // ==================== 拉线 ====================
    /**
     * 拉线的环心高度（格）。双环是 {@link TexturedHalo#CENTER_Y} = 1.62（在头顶之上），
     * 这里给 1.05 ⇒ 落到**身子后方**、明显比环低。
     *
     * <p>★ 别只看这个数：这张图的**视觉重心不在画布中心**（质心在画布偏上，约 0.28 格），
     * 所以"看得见的那一坨"实际会落在 {@code 1.05 + 0.28 ≈ 1.33} 格（肩/头的高度）。
     * 想再往下压就把这个数继续减（别低于 ~0.9，否则斜向的下半截会压到地面里）。
     */
    public static final float LINE_CENTER_Y = 1.05F;

    /**
     * 拉线那张图 —— **独立元素**：尺寸、固有旋转、摆位都自己一份（配色与混合方式仍用 {@link #CFG}）。
     *
     * <p>位置：从双环那个位置**往右下挪到身子正后方** ⇒ `side = 0`（左右归中）、
     * `centerY = {@link #LINE_CENTER_Y}`（比双环低一截）。
     */
    public static final TexturedHalo.Sprite LINE = new TexturedHalo.Sprite(
            TexturedHalo.Cfg.tex("sakurafox_line"),
            1.77F,                     // 铺开尺寸（格）—— 与双环同尺寸
            45F,                       // 固有旋转：这张图本身是斜的，向左（目视逆时针）转 45° 摆正
            LINE_CENTER_Y,             // 环心高度（比双环低 ⇒ 更靠下）
            0F,                        // 左右 = 0 ⇒ 落在**身子正后方**
            TexturedHalo.BACK);

    // ==================== 时间轴（tick，1 秒 = 20 tick）====================
    /** ① 双环自转时长 */
    public static final int T_SPIN = 200;
    /** ② 转完后快速放大的时长 */
    public static final int T_GROW = 12;
    /** ② 放大到多少倍 */
    public static final float GROW_TO = 1.30F;
    /** ③ 拉线出现后停留的时长（"然后再等 2s 换回两个环"）—— 拉线本身没有放缩动画 */
    public static final int T_HOLD = 40;
    /** 整轮循环长度 */
    public static final int CYCLE = T_SPIN + T_GROW + T_HOLD;

    public static void render(PoseStack pose, MultiBufferSource buffer, Player player, float partialTick) {
        float t = (player.tickCount + partialTick) % CYCLE;

        // ① 双环自转
        if (t < T_SPIN) {
            TexturedHalo.render(pose, buffer, player, partialTick, CFG);
            return;
        }
        t -= T_SPIN;

        // ② 转完后快速放大（放完立刻切走，读作"撑开"的一下）
        if (t < T_GROW) {
            TexturedHalo.render(pose, buffer, player, partialTick, CFG,
                    1F + (GROW_TO - 1F) * (t / T_GROW));
            return;
        }

        // ③ 拉线：直接出现在 LINE 那个位置，不再放大/缩小
        TexturedHalo.renderSprite(pose, buffer, player, partialTick, CFG, LINE, 1F);
    }
}
