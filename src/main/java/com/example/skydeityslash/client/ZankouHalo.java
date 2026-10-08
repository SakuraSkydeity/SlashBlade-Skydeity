package com.example.skydeityslash.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;

/**
 * 残虹满充能法环 —— 内外两张贴图环、反向自转、固定在身后偏右上。
 *
 * <p>贴图：{@code textures/cycring_entity/zankou_cyc_out.png}（外）与 {@code zankou_cyc_in.png}（内），都是 32×32。
 * 机制在 {@link TexturedHalo}。
 *
 * <p>★ 贴图主色是**亮粉紫**（`#FFA0CE` / `#F668B0` / `#CC348E`，另有 `#8A3AA0` 紫）——
 * 够亮、够饱和，所以**不加滤镜**（{@code {1,1,1}}）。
 *
 * <p>★ **用普通半透明，不是加法混合**：加法是 `dst + src`，而亮天空本身已经有 `(0.66, 0.79, 0.94)`
 * 的亮度，粉紫贴图（峰值 `(1.00, 0.63, 0.81)`）叠上去三通道**全部溢出被夹到 1.0 ⇒ 在天空背景下整圈变白**。
 * 半透明是"替换 + 按 alpha 混合"，粉紫色在任何背景上都保得住。
 * 想换成发光感就把这个 `false` 改成 `true`（一行），代价就是亮背景下会发白。
 *
 * <p>两张贴图都铺 {@code 1.77} 格：外环占半宽 0.872 ⇒ 实际半径约 **0.77 格**；内环占 0.356 ⇒ 约 **0.32 格**。
 */
public final class ZankouHalo {
    private ZankouHalo() {}

    public static final TexturedHalo.Cfg CFG = TexturedHalo.Cfg.behind(
            "zankou",
            1.77F, 1.77F,
            .30F, -.52F,
            new float[]{1F, 1F, 1F},   // 不加滤镜（贴图本身够饱和）
            .92F,                      // 半透明下 alpha = 真正的不透明度
            false);                    // ★ 半透明（加法混合在亮天空下会整圈发白，见类注释）

    public static void render(PoseStack pose, MultiBufferSource buffer, Player player, float partialTick) {
        TexturedHalo.render(pose, buffer, player, partialTick, CFG);
    }
}
