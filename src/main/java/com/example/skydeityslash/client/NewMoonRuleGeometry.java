package com.example.skydeityslash.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * 「新月法则」（columbina SA 脚下那圈法阵）的**程序化几何** —— 纯代码绘制，**不用贴图**。
 *
 * <p>图形只有一样东西：**一圈蓝白十字星**（每颗 = 中心方块 + 四条收尖的臂，即四角星），
 * 分散在法阵各处、大小与方位都错开（写死的表，不是随机）。
 * ★ 2026-09-26 第二轮：**去掉了原来居中的那个月亮**（用户说不需要），
 * 十字星**变多（5 → 9 颗）、变小、散得更开**。
 *
 * <p>★ **不要 alpha 花活**：十字星一律**一个纯色、一个 alpha**（没有渐变、没有光晕、没有内外浓淡），
 * 只有顶点 alpha 会乘上整条特效的淡入淡出 —— 也就是"把原来那张贴图抽象成几何、观感不变"。
 * 配色取自原来那张 `newmoonrule.png` 的浅蓝（蓝白）。
 *
 * <p>走 {@link #FLAT}（POSITION_COLOR + 半透明混合 + 写深度 + 无剔除）：
 * 颜色就是顶点色、不吃光照，和贴图版"满亮贴图"的观感一致；写深度是为了让它正常贴在地面上。
 * 坐标系 = 渲染器里已经放平的那个本地平面（局部 +X / +Y 就是地面，原点 = 法阵中心），单位是格。
 */
public final class NewMoonRuleGeometry {

    private NewMoonRuleGeometry() {
    }

    /** 平面着色的 RenderType（半透明混合 + 写深度 + 无剔除）。 */
    public static final RenderType FLAT = RState.composite(
            "skydeityslash:newmoonrule_flat",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            0x8000, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RState.SHADER_POSITION_COLOR)
                    .setTransparencyState(RState.TRANSPARENCY_TRANSLUCENT)
                    .setCullState(RState.CULL_NONE)
                    .setWriteMaskState(RState.WRITE_COLOR_DEPTH)
                    .setOutputState(RState.OUTPUT_MAIN)
                    .createCompositeState(false));

    /** 十字星颜色：蓝白（取自原贴图的浅蓝那支） */
    private static final float[] STAR_RGB = {.68F, .88F, .98F};

    /**
     * 九颗十字星：**半径（格）/ 方位角（度）/ 臂长（格）/ 臂宽（格）** —— 都是写死的表，
     * 所以每次释放的布局完全一样（不是随机）。法阵半径是 5，半径 2.7~4.45 属于"分散在四周"；
     * 角度刻意不均分（彼此错开 46°~58°），看起来像随手撒的而不是排出来的。
     */
    private static final float[] STAR_R = {4.20F, 2.95F, 3.60F, 4.45F, 3.15F, 3.85F, 2.70F, 4.05F, 3.40F};
    private static final float[] STAR_A = {-78F, -20F, 34F, 82F, 128F, 172F, -128F, -52F, 6F};
    private static final float[] STAR_ARM = {.34F, .30F, .38F, .26F, .32F, .36F, .28F, .40F, .31F};
    private static final float[] STAR_TH = {.085F, .075F, .095F, .065F, .080F, .090F, .070F, .100F, .078F};

    /**
     * 画整个法阵（调用方已经把 pose 放平到地面、并按展开进度缩放过）。
     *
     * @param alpha 整条特效的淡入淡出（唯一一个作用于 alpha 的量；图形内部没有别的 alpha 变化）
     */
    public static void draw(Matrix4f m, VertexConsumer vc, float alpha) {
        for (int i = 0; i < STAR_R.length; i++) {
            cross(m, vc, STAR_R[i], STAR_A[i], STAR_ARM[i], STAR_TH[i], alpha);
        }
    }

    // ================================================================ 十字星

    /** 一颗十字星：中心方块 + 四条收尖的臂（四角星），整体一个颜色、一个 alpha。 */
    private static void cross(Matrix4f m, VertexConsumer vc, float r, float deg, float arm, float th,
                              float alpha) {
        float cx = Mth.cos(deg * ((float) Math.PI / 180F)) * r;
        float cy = Mth.sin(deg * ((float) Math.PI / 180F)) * r;
        // 中心方块
        quad2(m, vc, cx - th, cy - th, cx + th, cy - th, cx + th, cy + th, cx - th, cy + th, alpha);
        // 上 / 下 / 右 / 左 四条臂（往外收成尖）
        tri2(m, vc, cx - th, cy + th, cx + th, cy + th, cx, cy + th + arm, alpha);
        tri2(m, vc, cx - th, cy - th, cx + th, cy - th, cx, cy - th - arm, alpha);
        tri2(m, vc, cx + th, cy - th, cx + th, cy + th, cx + th + arm, cy, alpha);
        tri2(m, vc, cx - th, cy - th, cx - th, cy + th, cx - th - arm, cy, alpha);
    }

    // ================================================================ 工具

    private static void quad2(Matrix4f m, VertexConsumer vc,
                              float x0, float y0, float x1, float y1,
                              float x2, float y2, float x3, float y3, float a) {
        vv(m, vc, a, x0, y0);
        vv(m, vc, a, x1, y1);
        vv(m, vc, a, x2, y2);

        vv(m, vc, a, x0, y0);
        vv(m, vc, a, x2, y2);
        vv(m, vc, a, x3, y3);
    }

    private static void tri2(Matrix4f m, VertexConsumer vc,
                             float x0, float y0, float x1, float y1, float x2, float y2, float a) {
        vv(m, vc, a, x0, y0);
        vv(m, vc, a, x1, y1);
        vv(m, vc, a, x2, y2);
    }

    private static void vv(Matrix4f m, VertexConsumer vc, float a, float x, float y) {
        vc.vertex(m, x, y, 0F)
                .color(ci(STAR_RGB[0]), ci(STAR_RGB[1]), ci(STAR_RGB[2]), ci(a))
                .endVertex();
    }

    private static int ci(float v) {
        return Mth.clamp((int) (v * 255F), 0, 255);
    }
}
