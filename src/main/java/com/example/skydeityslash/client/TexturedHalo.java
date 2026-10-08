package com.example.skydeityslash.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * 「身后两个**贴图环**反向自转」这套**机制** —— 只描述**怎么画**，不描述任何一把刀长什么样。
 *
 * <p>★ 抽象边界（2026-10-04 调整过一次，原因见下）：
 * <ul>
 *   <li>环的**造型**现在全在贴图里 ⇒ "每把刀不一样"这件事由**贴图 + 一组常量**表达，
 *       代码完全同构。所以把机制抽到这里，每把刀仍旧各有一个自己的类
 *       （{@code FurinaHalo / SakurafoxHalo / ZankouHalo}），里面只有常量 + 一行转发。</li>
 *   <li>以后某把刀要长出自己的花样（多一层、加个中心图案……），就在它自己的类里写，不影响别人。</li>
 * </ul>
 * 早先"环不做抽象"那条针对的是**代码画几何**的版本（各环形状真的不同）；换成贴图后前提变了。
 *
 * <p><b>朝向</b>：**固定在玩家背后、跟着身体转**（不是朝相机的公告板）。做法是先把姿态
 * {@code rotateY(180° - bodyYaw)} 转到"身体系"：局部 +X = 玩家左手侧、+Y = 世界竖直、+Z = 身体正后方，
 * 两个环就画在这个局部 XY 平面里（z=0）。第三人称从背后看是正对着的一整圈，且跟着身体转。
 *
 * <p><b>坐标系提醒</b>：{@code RenderPlayerEvent.Post} 拿到的 PoseStack 原点 = 玩家位置，
 * 但**轴仍然是世界系**（相机旋转乘在矩阵链的外侧，不在实体局部帧里）—— 所以"世界竖直"直接
 * {@code translate(0, y, 0)} 就是对的，**不需要** cameraOrientation()。
 *
 * <p><b>遮挡</b>：走 {@link RState#texturedGlow}（自发光 + 双面 + 不写深度、但深度测试仍开）
 * ⇒ 被玩家身体正确挡住，只在轮廓外露出来。
 */
public final class TexturedHalo {
    private TexturedHalo() {}

    /** 环贴图统一放这个目录下（assets/skydeityslash/ 之后的部分，含扩展名前的文件名） */
    public static final String TEX_DIR = "textures/cycring_entity/";

    /** 超过这个距离整组不画 */
    public static final float VIEW = 64F;
    /**
     * 呼吸（亮度轻微起伏，让它不像一张死贴图）。
     * ★ 实现是**等比缩放顶点 RGB**，不是改 alpha —— 见 {@link #render} 的说明。
     */
    public static final float PULSE_SPEED = .055F, PULSE_AMT = .06F;

    // ==================== 共用摆位（身体系：+X = 玩家左手侧，+Y = 世界竖直，+Z = 身后）====================
    /** 环心高度（脚底起算，格） */
    public static final float CENTER_Y = 1.62F;
    /**
     * 左右偏移。**负 = 玩家右手侧，正 = 玩家左手侧**。
     * ★ `rotateY(180°−yaw)` 把局部 (1,0,0) 映到世界 `(-cos yaw, 0, -sin yaw)`：yaw=0（面朝南）时是
     * (-1,0,0)=西 = 面朝南者的**左手边** ⇒ **局部 +X = 玩家左手侧**。
     * 第三人称从背后看**不镜像**（你站在别人背后朝同一个方向看，他的左手就在你左边），
     * 所以 **正值 = 画面左、负值 = 画面右**。默认取负 ⇒ 环落在**右上角**。
     */
    public static final float SIDE = -.52F;
    /** 往身后推的距离（格）。≈0.4 是"贴近身体但不穿模"的位置 */
    public static final float BACK = .44F;

    /**
     * 一组"双贴图环"的全部参数 —— **纯数据，不含行为**。
     *
     * @param key     缓存键（= 刀定义名，用来给两张贴图各生成一个 RenderType）
     * @param texOut  外环贴图
     * @param texIn   内环贴图
     * @param sizeOut 外环那张贴图铺开多大（格，正方形边长）—— 半径 = size/2 × 贴图里环的占比
     * @param sizeIn  内环那张贴图铺开多大（格）
     * @param spinOut 外环自转（度 / tick）
     * @param spinIn  内环自转（度 / tick），一般与外环**反号**
     * @param tint    顶点色**当滤镜**（着色器是 {@code color = texture * vertexColor}，即乘法）；
     *                贴图本身已经饱和就给 {@code {1,1,1}}，需要压掉某几档近白才给偏色
     * @param alpha   顶点 alpha。★ **加法混合下它完全不参与运算**（见 {@link #render}），
     *                只有 {@code additive = false}（半透明）时它才是"不透明度"
     * @param centerY 环心高度（格，脚底起算）
     * @param side    左右偏移（正=玩家左手侧；第三人称背后视角里 正=画面左）
     * @param back    往身后推的距离（格）
     * @param additive {@code true} = 加法混合（发光感，适合**亮色**贴图 + 暗背景）；
     *                 {@code false} = 普通半透明（适合**暗色**贴图 —— 加法混合在亮背景上会看不见）
     */
    public record Cfg(String key,
                      ResourceLocation texOut, ResourceLocation texIn,
                      float sizeOut, float sizeIn,
                      float spinOut, float spinIn,
                      float[] tint, float alpha,
                      float centerY, float side, float back,
                      boolean additive) {

        /** 贴图路径组装：{@code textures/cycring_entity/<file>.png} */
        public static ResourceLocation tex(String file) {
            return new ResourceLocation("skydeityslash", TEX_DIR + file + ".png");
        }

        /**
         * 用**共用摆位**（{@link TexturedHalo#CENTER_Y} / {@link TexturedHalo#SIDE} / {@link TexturedHalo#BACK}）造一份配置。
         * 贴图按 `刀定义名_cyc_out.png` / `_cyc_in.png` 约定取，所以调用处只要写刀名 + 造型参数。
         */
        public static Cfg behind(String name, float sizeOut, float sizeIn,
                                 float spinOut, float spinIn,
                                 float[] tint, float alpha, boolean additive) {
            return behind(name, sizeOut, sizeIn, spinOut, spinIn, tint, alpha, additive, SIDE);
        }

        /**
         * 同上，但**左右位置自己给**（{@code side} 正 = 玩家左手侧 = 第三人称背后视角的画面左；
         * 负 = 画面右）。想让某把刀的环落在"另一边"就传 `+SIDE`。
         */
        public static Cfg behind(String name, float sizeOut, float sizeIn,
                                 float spinOut, float spinIn,
                                 float[] tint, float alpha, boolean additive, float side) {
            return new Cfg(name, tex(name + "_cyc_out"), tex(name + "_cyc_in"),
                    sizeOut, sizeIn, spinOut, spinIn, tint, alpha,
                    CENTER_Y, side, BACK, additive);
        }
    }

    /**
     * 一张**独立贴图元素**：贴图 + 尺寸 + 固有旋转 + **自己的一份摆位**。
     * 配色与混合方式仍沿用所属刀的 {@link Cfg}（tint / alpha / additive），只有位置可以不一样 ——
     * 例如"拉线"出现时要挪到身体正后方，就靠这个。
     */
    public record Sprite(ResourceLocation tex, float size, float rotateDeg,
                         float centerY, float side, float back) {}

    /**
     * 画一把刀的整组法环。
     *
     * @param pose        玩家渲染时的姿态栈（原点 = 玩家位置，世界系）
     * @param buffer      渲染缓冲
     * @param player      持有者（别人手持满充能的刀时也会画）
     * @param partialTick 插值
     * @param cfg         这把刀的造型参数
     */
    public static void render(PoseStack pose, MultiBufferSource buffer, Player player,
                              float partialTick, Cfg cfg) {
        render(pose, buffer, player, partialTick, cfg, 1F);
    }

    /**
     * 同上，但整组再乘一个缩放。
     *
     * @param scale 整组缩放（1 = 原大小，&gt;1 放大）；两个环的尺寸都按它等比放大
     */
    public static void render(PoseStack pose, MultiBufferSource buffer, Player player,
                              float partialTick, Cfg cfg, float scale) {
        if (!inRange(player, cfg.centerY())) return;
        float time = player.tickCount + partialTick;
        int[] c = vertexColor(cfg, time);
        pushBodyFrame(pose, player, partialTick, cfg.centerY(), cfg.side(), cfg.back());
        quad(pose, buffer.getBuffer(typeOf(cfg.texOut(), cfg.additive())),
                cfg.sizeOut() * scale, cfg.spinOut() * time, c);
        quad(pose, buffer.getBuffer(typeOf(cfg.texIn(), cfg.additive())),
                cfg.sizeIn() * scale, cfg.spinIn() * time, c);
        pose.popPose();
    }

    /**
     * 在**同一个"身后平面"**里画一张独立贴图（用于"拉线"这类不属于那两个环的元素）。
     * 摆位取 {@code sprite} 自己的，配色与混合方式沿用 {@code cfg}。
     *
     * @param scale 缩放乘子（1 = {@code sprite.size()} 原大小）
     */
    public static void renderSprite(PoseStack pose, MultiBufferSource buffer, Player player,
                                    float partialTick, Cfg cfg, Sprite sprite, float scale) {
        if (!inRange(player, sprite.centerY())) return;
        int[] c = vertexColor(cfg, player.tickCount + partialTick);
        pushBodyFrame(pose, player, partialTick, sprite.centerY(), sprite.side(), sprite.back());
        quad(pose, buffer.getBuffer(typeOf(sprite.tex(), cfg.additive())),
                sprite.size() * scale, sprite.rotateDeg(), c);
        pose.popPose();
    }

    /** 相机离得太远就整组不画 */
    private static boolean inRange(Player player, float centerY) {
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        return cam.getPosition()
                .distanceToSqr(player.getX(), player.getY() + centerY, player.getZ()) <= VIEW * VIEW;
    }

    /**
     * 把姿态推到"身体系"：原点 = 环心，局部 +X = 玩家左手侧、+Y = 世界竖直、+Z = 身体正后方，
     * XY 平面 ⟂ 身体朝向（跟着身体转）。调用方负责 popPose()。
     */
    private static void pushBodyFrame(PoseStack pose, Player player, float partialTick,
                                      float centerY, float side, float back) {
        float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot);
        pose.pushPose();
        pose.translate(0F, centerY, 0F);
        pose.mulPose(Axis.YP.rotationDegrees(180F - bodyYaw));
        pose.translate(side, 0F, back);
    }

    /**
     * 顶点色（当滤镜用，乘法）。
     *
     * <p>★★ 呼吸必须**等比缩放顶点 RGB**，不能只改 alpha：
     * 加法混合用的是 `blendFunc(ONE, ONE)`（见 {@code RenderStateShard.ADDITIVE_TRANSPARENCY} 的字节码），
     * 那是个 **2 参数** blendFunc ⇒ 颜色和 alpha 的因子都是 (ONE, ONE) ⇒ **顶点 alpha 压根不参与**，
     * 改它不会改变画面亮度（早先"用 alpha 控亮度"的说法是错的）。
     * 只有半透明走的是 `blendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ...)`，alpha 才有效。
     * 等比缩放 RGB（三通道乘同一个系数）既能真的变暗、又不会偏色，
     * 系数只往**下**走（[1−AMT, 1]），避免超 1 被夹掉造成色相漂移。
     */
    private static int[] vertexColor(Cfg cfg, float time) {
        float pulse = 1F - PULSE_AMT * (.5F - .5F * Mth.sin(time * PULSE_SPEED));
        return new int[]{
                ch(cfg.tint()[0] * pulse),
                ch(cfg.tint()[1] * pulse),
                ch(cfg.tint()[2] * pulse),
                ch(cfg.alpha()),
        };
    }

    /** 0~1 的系数 → 0~255 的顶点色分量 */
    private static int ch(float v) {
        return (int) (Mth.clamp(v, 0F, 1F) * 255F);
    }

    // ==================================================================
    // 贴图面：一张正对身后的 quad，绕自身法线自转
    // ==================================================================
    private static void quad(PoseStack pose, VertexConsumer vc, float size, float spinDeg, int[] c) {
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(spinDeg));
        Matrix4f m = pose.last().pose();
        float h = size * .5F;
        // 顶点顺序：左下 → 右下 → 右上 → 左上（贴图的上边贴在 +Y）
        vertex(m, vc, -h, -h, 0F, 1F, c);
        vertex(m, vc, h, -h, 1F, 1F, c);
        vertex(m, vc, h, h, 1F, 0F, c);
        vertex(m, vc, -h, h, 0F, 0F, c);
        pose.popPose();
    }

    private static void vertex(Matrix4f m, VertexConsumer vc, float x, float y, float u, float v, int[] c) {
        vc.vertex(m, x, y, 0F)
                .color(c[0], c[1], c[2], c[3])
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0F, 0F, 1F)
                .endVertex();
    }

    /** RenderType 按（贴图 × 混合方式）缓存 —— 每帧新建 RenderType 会不断注册新对象，必须在类里存住。 */
    private static final Map<String, RenderType> TYPES = new HashMap<>();

    private static RenderType typeOf(ResourceLocation tex, boolean additive) {
        return TYPES.computeIfAbsent(tex + (additive ? "|a" : "|t"),
                k -> RState.texturedGlow(
                        "skydeityslash:cyc_" + tex.getPath().replace('/', '_').replace(".png", ""),
                        tex, additive));
    }
}
