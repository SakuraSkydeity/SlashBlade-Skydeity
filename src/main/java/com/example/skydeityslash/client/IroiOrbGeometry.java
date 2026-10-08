package com.example.skydeityslash.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 「向阳」第二形态收尾**紫色光球群**的程序化几何 —— 纯代码绘制（UV 球，无贴图、无粒子），加法混合。
 *
 * <p>一组 {@link #COUNT} 个紫色小球：球心分散在一圈里、高度依次错开、各自以不同相位**上下漂浮**，
 * 并在 2 秒里**同时向外飘散**（半径随进度增长、略微上浮 —— 前慢后快，所以是"慢慢散开"）；
 * 时间轴是"淡入 → 漂浮 + 飘散 → 淡出"，总长 {@link #LIFETIME} tick（= 实体寿命，2 秒）。
 *
 * <p>★ 球色：**一支饱和紫、不含任何白档** —— 没有假光照、没有高光、没有描边，
 * 只有一点"上下明度渐变"（底部 {@link #LOW} → 顶部 {@link #HIGH}，**只往暗走**）。
 * ★ 老版本用"底深紫 → 顶亮紫 + 假光照"暗示体积，加法混合下上半球直接亮成白色（用户否掉过两次）：
 * 在加法通道里**"提亮"就是往白走**，想要体积只能往同色相的**暗端**压。
 *
 * <p>★ alpha 预算：球是封闭曲面，视线穿孔会穿过**前面 + 后面**两层 ⇒ 单球贡献约
 * {@code 2 × 0.20 × 紫 ≈ 0.4}；8 个球彼此分开、不叠在同一像素上，所以整体不超预算。
 *
 * <p>★ 坐标系：**实体本地坐标**，原点 = 这组球的中心（y=0 就是传入的中心高度）。
 */
public final class IroiOrbGeometry {

    private IroiOrbGeometry() {
    }

    private static final float TAU = (float) Math.PI * 2F;

    /** 球数 */
    private static final int COUNT = 8;
    /** 球的基准半径（格） */
    private static final float ORB_RADIUS = .22F;
    /** UV 球的分段（纬 / 经）—— 这么小的球，8×12 已经看不出多边形 */
    private static final int RING = 8, SEG = 12;
    /** 存在时长（tick）—— 与 {@code EntityIroiOrb.LIFETIME} 对齐 */
    public static final float LIFETIME = 40F;
    /** 开场淡入 / 收尾淡出（tick） */
    private static final float FADE_IN = 4F, FADE_OUT = 10F;
    /** 上下漂浮的幅度（格）与速度（弧度/tick） */
    private static final float BOB = .16F, BOB_SPEED = .16F;
    /** ★ **向外飘散**：2 秒内半径再外扩这么多倍 {@code spread}（起点 0.45~0.8 倍 → 越飘越开） */
    private static final float DRIFT = 1.10F;
    /** 飘散时同时上浮的高度（格） */
    private static final float RISE = .55F;
    /** 球心的高度范围（相对中心，格） */
    private static final float Y_MIN = .30F, Y_MAX = 1.50F;
    /**
     * 球色（顶部 / 本色）：一支**饱和紫** —— ★ 不含任何白档，也不要有任何"提亮"成分。
     */
    private static final float[] HIGH = {.72F, .07F, .94F};
    /**
     * 球色（底部）：**同一支紫**压暗一档 —— 就是那句"带一点点渐变即可"。
     * ★ 渐变只往**暗**走（底部更暗、顶部才是本色）⇒ 永远不会出现"上面发白"（那是老版本踩过的坑：
     * 用"往上提亮 + 假光照"暗示体积，加法混合下直接亮成白色）。
     */
    private static final float[] LOW = {.50F, .03F, .66F};
    /** 每个球每一层的 alpha（★ 见类注释的预算） */
    private static final float ALPHA = .20F;

    public static void draw(Matrix4f m, VertexConsumer vc, float age, float spread) {
        float fade = smooth(Mth.clamp(age / FADE_IN, 0F, 1F))
                * (1F - smooth(Mth.clamp((age - (LIFETIME - FADE_OUT)) / FADE_OUT, 0F, 1F)));
        if (fade <= .01F) return;
        float drift = smooth(Mth.clamp(age / LIFETIME, 0F, 1F));    // 0→1：整组球"飘散"的进度

        for (int i = 0; i < COUNT; i++) {
            // 固定伪随机：方位角大致均分但带抖动、起点半径/高度/大小/相位各不相同 ⇒ 看着是"随意飘着的 8 颗"
            double ang = i * (TAU / COUNT) + det(i, 1.7F) * .55F;
            // ★ 向外飘散：半径随进度增长（前慢后快，所以是"慢慢散开"而不是"一下子炸开"）
            float rr = spread * ((.45F + .35F * det(i, 3.1F)) + DRIFT * drift);
            float y = Y_MIN + (Y_MAX - Y_MIN) * det(i, 5.3F)
                    + Mth.sin(age * BOB_SPEED + det(i, 7.7F) * TAU) * BOB
                    + RISE * drift;
            float size = ORB_RADIUS * (.78F + .45F * det(i, 9.1F));
            ball(m, vc, new Vec3(Math.cos(ang) * rr, y, Math.sin(ang) * rr), size, fade);
        }
    }

    /** 一个 UV 球（经纬网格，正反两面都画 ⇒ 任何角度看都是实心球）。★ 同一支紫，只有一点上下明度渐变 */
    private static void ball(Matrix4f m, VertexConsumer vc, Vec3 c, float radius, float env) {
        final float cx = (float) c.x, cy = (float) c.y, cz = (float) c.z;
        final float a = ALPHA * env;
        for (int i = 0; i < RING; i++) {
            float p0 = -1.5708F + (float) Math.PI * i / RING;
            float p1 = -1.5708F + (float) Math.PI * (i + 1) / RING;
            float cp0 = Mth.cos(p0), sp0 = Mth.sin(p0), cp1 = Mth.cos(p1), sp1 = Mth.sin(p1);
            // 明度参数：0 = 球底（暗档）→ 1 = 球顶（本色）；同一行两个纬度各带一个值 ⇒ 逐顶点插值就是那点渐变
            float h0 = sp0 * .5F + .5F, h1 = sp1 * .5F + .5F;
            for (int j = 0; j < SEG; j++) {
                float t0 = TAU * j / SEG, t1 = TAU * (j + 1) / SEG;
                float ct0 = Mth.cos(t0), st0 = Mth.sin(t0), ct1 = Mth.cos(t1), st1 = Mth.sin(t1);
                quad(m, vc, h0, h1, a,
                        cx + cp0 * ct0 * radius, cy + sp0 * radius, cz + cp0 * st0 * radius,
                        cx + cp1 * ct0 * radius, cy + sp1 * radius, cz + cp1 * st0 * radius,
                        cx + cp1 * ct1 * radius, cy + sp1 * radius, cz + cp1 * st1 * radius,
                        cx + cp0 * ct1 * radius, cy + sp0 * radius, cz + cp0 * st1 * radius);
            }
        }
    }

    private static void quad(Matrix4f m, VertexConsumer vc, float h0, float h1, float a,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        vv(m, vc, x0, y0, z0, h0, a);
        vv(m, vc, x1, y1, z1, h1, a);
        vv(m, vc, x2, y2, z2, h1, a);
        vv(m, vc, x0, y0, z0, h0, a);
        vv(m, vc, x2, y2, z2, h1, a);
        vv(m, vc, x3, y3, z3, h0, a);
    }

    /** {@code h} = 0 底部暗档 → 1 顶部本色；两个色都是同一支紫，所以只会"变深/变浅"、不会跑到白 */
    private static void vv(Matrix4f m, VertexConsumer vc, float x, float y, float z, float h, float a) {
        float t = Mth.clamp(h, 0F, 1F);
        vc.vertex(m, x, y, z)
                .color(ci(LOW[0] + (HIGH[0] - LOW[0]) * t),
                        ci(LOW[1] + (HIGH[1] - LOW[1]) * t),
                        ci(LOW[2] + (HIGH[2] - LOW[2]) * t),
                        ci(a))
                .endVertex();
    }

    private static int ci(float v) {
        return Mth.clamp((int) (v * 255F), 0, 255);
    }

    /** 稳定的伪随机（0..1）：同一个 i/k 永远同一个值 ⇒ 每次释放的球群布局一致 */
    private static float det(int i, float salt) {
        float v = Mth.sin(i * 12.9898F + salt * 78.233F) * 43758.547F;
        return v - (float) Math.floor(v);
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0F, 1F);
        return t * t * (3F - 2F * t);
    }
}
