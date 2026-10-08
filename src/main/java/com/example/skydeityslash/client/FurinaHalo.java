package com.example.skydeityslash.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;

/**
 * 芙宁娜「枫丹」满充能法环 —— 内外两张蓝色贴图环、反向自转、固定在身后偏右上。
 *
 * <p>贴图：{@code textures/cycring_entity/furina_cyc_out.png}（外）与 {@code furina_cyc_in.png}（内），都是 32×32。
 * 机制（怎么画）在 {@link TexturedHalo}，这里只有"这把刀长什么样"。
 *
 * <p>★ **TINT 是唯一的调色旋钮**：自发光着色器里 {@code color = texture * vertexColor}，顶点色是**乘法**。
 * 贴图里有几档接近白的浅蓝（`.73/.92/1.00`），不加滤镜直接画会**发白**；乘上这个偏蓝的三元组以后
 * 红/绿被压下去、蓝保留 ⇒ 整体变饱和蓝。
 * ★★ 注意：**加法混合下顶点 alpha 不参与运算**（`blendFunc(ONE, ONE)`，见 {@link TexturedHalo#render}），
 * 所以 {@link TexturedHalo.Cfg#alpha()} 在这里**改不动亮度** —— 觉得还太亮，就继续把 TINT 往下压
 * （例如 `.44/.66/.85`），别再动 alpha。
 *
 * <p>★ 两种尺寸都铺 {@code 1.77} 格（32×32 ⇒ 每像素 0.055 格，与其他刀的像素密度一致）：
 * 外环在贴图里占半宽的 0.945 ⇒ 实际半径约 **0.84 格**；内环占 0.517 ⇒ 约 **0.46 格**。
 */
public final class FurinaHalo {
    private FurinaHalo() {}

    public static final TexturedHalo.Cfg CFG = TexturedHalo.Cfg.behind(
            "furina",
            1.77F, 1.77F,          // 外 / 内 贴图铺开尺寸（格）
            .30F, -.52F,           // 外 / 内 自转（度每 tick，反向）
            new float[]{.55F, .82F, 1.00F},   // ★ 顶点色滤镜（压红绿、留蓝 ⇒ 不发白）；加法混合下这才是亮度旋钮
            1F,                    // 加法混合下 alpha 无效，给 1 免得误导
            true);                 // 加法混合（贴图是亮蓝，发光感）

    public static void render(PoseStack pose, MultiBufferSource buffer, Player player, float partialTick) {
        TexturedHalo.render(pose, buffer, player, partialTick, CFG);
    }
}
