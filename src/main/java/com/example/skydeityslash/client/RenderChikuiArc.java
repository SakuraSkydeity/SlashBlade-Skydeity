package com.example.skydeityslash.client;

import com.example.skydeityslash.entity.EntityChikuiArc;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 「赤葵」焚天烬灭舞的**暗红摆线**渲染器。
 *
 * 主体是一条**极细的深暗红圆柱**（真正有体积的棱柱，不是"朝向相机的平面带子"），
 * 圆柱上方再飘几条**长短不一的火焰弧线**。
 *
 * 钟摆式两段运动（实体在玩家脚下，本类按 {@link #PIVOT_Y} 把支点抬到头顶上方）：
 *  · 第 ① 段（{@link #SWING_TICKS} tick）：与地面夹角 60° → 0°，用 t² 加速摆下来；
 *  · 第 ② 段（{@link #DROP_TICKS} tick）：角度锁死在水平，**整条线保持水平一起平移到地面**，
 *    触地即消失（不做原地停留）。
 *
 * ⚠️ 三条关键几何约束（都踩过坑）：
 *  ① **支点不能压到眼睛高度** —— 一条穿过光心的直线在屏幕上**投影成一个点**，
 *     这时把 LENGTH 从 10 改到 96 都毫无变化（用户反复说"不够长"就是这个）。
 *  ② **火舌一律朝"世界正上方"生长**（{@link #WORLD_UP}），**不跟相机转**。
 *     早先用过"正对相机的横向 `w = axis × (中点 → 相机)`"，火焰会随视角旋转 —— 用户明确要求"一直朝上"。
 *     截面做成 {@link #FLAME_SIDES} 边形小柱，所以从任何角度看都有厚度、不会变成一张薄纸。
 *  ③ **剔除箱要覆盖特效真实范围**（见 {@code EntityChikuiArc.getBoundingBoxForCulling()}）。
 *
 * 渲染类型：POSITION_COLOR + 普通半透明（暗色用半透明；加法混合会发闷）。
 */
public class RenderChikuiArc extends EntityRenderer<EntityChikuiArc> {

    private static final ResourceLocation NONE = new ResourceLocation("skydeityslash", "chikui_arc");
    private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);

    /** 自定义渲染类型：顶点色、三角面、半透明、不剔除、只写颜色 */
    private static final RenderType LINE_TYPE = RState.composite(
            "skydeityslash:chikui_arc",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            0x8000, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RState.SHADER_POSITION_COLOR)
                    .setTransparencyState(RState.TRANSPARENCY_TRANSLUCENT)
                    .setCullState(RState.CULL_NONE)
                    .setWriteMaskState(RState.WRITE_COLOR)
                    .setOutputState(RState.OUTPUT_MAIN)
                    .createCompositeState(false));

    // ---------------------------------------------------------------- 摆动参数
    /**
     * 支点高度（相对玩家脚底，格）—— 线的固定端挂在玩家**头顶稍上方**。
     * ⚠️ 不能压到眼睛高度：线一旦正好穿过摄像机眼睛，透视会把整条线**投影成一个点**。
     */
    private static final double PIVOT_Y = 3.0;
    /** 摆臂长度（格）：线就是这条摆臂 */
    private static final double LENGTH = 25.0;
    /** 起始角（度）：与地面成 60°（抬起） */
    private static final double SWING_START_DEG = 60.0;
    /** 结束角（度）：落回水平（0°） */
    private static final double SWING_END_DEG = 0.0;
    /** 摆动阶段所需 tick */
    private static final float SWING_TICKS = 17.0f;
    /** 落地阶段所需 tick —— 摆到水平后整条线**保持水平一起平移下坠**到地面，落地即消失 */
    private static final float DROP_TICKS = 8.0f;
    /** 落地高度（相对玩家脚底，格）—— 0.03 ≈ 地面 */
    private static final double GROUND_Y = 0.03;

    // ---------------------------------------------------------------- 线体（圆柱）
    /** 圆柱半径（格）—— **极细**；想粗一点就加大（0.012 ≈ 直径 2.4 厘米） */
    private static final float ROD_RADIUS = 0.012f;
    /** 圆柱的棱数（6 边已经很圆了；反正只有两厘米粗，看不出棱） */
    private static final int ROD_SIDES = 6;
    /** 沿线的细分段数（直线，不用太多） */
    private static final int ROD_SEGMENTS = 16;
    /** 线的透明度 —— 实心，保证"纯暗红"看得清楚 */
    private static final float LINE_ALPHA = 1.0f;
    /** 线的颜色：深暗红（zankou 主题色压深一档） */
    private static final int DARK_RED = 0x7A0A1A;
    /** 由支点向外长出的 tick */
    private static final float GROW_TICKS = 3.0f;
    /** 落地瞬间的收尾淡出 tick */
    private static final float FADE_TICKS = 3.0f;

    // ---------------------------------------------------------------- 火焰（长短不一的弧线）
    /**
     * 沿线一共几簇火舌 —— **密集度**靠它。当前 140：沿 25 格每隔约 0.18 格一簇，
     * 而条宽 {@link #FLAME_STROKE}=0.28 已经大于这个间距 ⇒ 火舌互相重叠、连成**一整条密集火带**。
     */
    private static final int FLAME_LICKS = 140;
    /** 每条火舌的细分段数 */
    private static final int FLAME_SUB = 6;
    /** 火舌长度范围（格）—— 「往外伸多长」；调大 = 火焰在**垂直于线**的方向更宽 */
    private static final float FLAME_LEN_MIN = 0.35f;
    private static final float FLAME_LEN_MAX = 1.70f;
    /** 火舌末端相对自身长度的侧向偏移（0.22 ≈ 倾斜 12°）—— **按比例**，短火舌不会被弯歪、能一直"立着" */
    private static final float FLAME_BEND = 0.22f;
    /**
     * 火舌宽度（格，向尖端收细到 0）—— 这个宽度是**沿主线长度方向**的
     * （火舌带子由 {@code c = normal × tan} 张开，tan 基本沿着 {@code up}，所以 c 落在摆臂方向上）。
     * 想要"火焰在长度方向上更宽"就调它；配合 {@link #FLAME_LICKS} 一起看。
     */
    private static final float FLAME_STROKE = 0.28f;
    /** 火舌的截面边数 —— 小柱体的侧面数，4 边就已经有"厚度 + 圆润"的观感（太细，看不出棱） */
    private static final int FLAME_SIDES = 4;
    /** 火舌整体透明度（淡淡的，只是给线镶一层薄火） */
    private static final float FLAME_ALPHA = 0.55f;
    /** 火焰闪烁快慢（弧度/tick） */
    private static final float FLAME_FLICKER = 0.50f;
    /** 火焰配色：短的更亮（赤红）→ 长的更暗（暗红） */
    private static final int FLAME_HOT = 0xE0442A;
    private static final int FLAME_DEEP = 0xB0121F;

    public RenderChikuiArc(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(EntityChikuiArc entity) {
        return NONE;
    }

    @Override
    public void render(EntityChikuiArc entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        float age = entity.tickCount + partial;
        float life = EntityChikuiArc.LIFETIME;
        if (age > life) return;

        float fade = age > life - FADE_TICKS
                ? Mth.clamp((life - age) / FADE_TICKS, 0f, 1f) : 1f;
        if (fade <= 0.01f) return;
        fade = smooth(fade);
        float grow = smooth(Mth.clamp(age / GROW_TICKS, 0f, 1f));

        // 摆动平面 = 顺着玩家面朝方向的那面竖直平面：场内的横向轴就是视线的水平投影
        Vec3 look = entity.getArcDirection();
        Vec3 fwd = new Vec3(look.x, 0.0, look.z);
        if (fwd.lengthSqr() < 1.0E-8) fwd = new Vec3(0, 0, 1);
        fwd = fwd.normalize();

        // 摆臂角度：60° → 0°，用 t² 加速（起步慢、末端快）
        float p = Mth.clamp(age / SWING_TICKS, 0f, 1f);
        double deg = Mth.lerp(p * p, (float) SWING_START_DEG, (float) SWING_END_DEG);

        // 摆到水平之后：整条线保持水平、一起平移到地面（不再原地停一个点），落地即消失
        double pivotY = PIVOT_Y;
        if (age > SWING_TICKS) {
            float q = Mth.clamp((age - SWING_TICKS) / DROP_TICKS, 0f, 1f);
            pivotY = PIVOT_Y - (PIVOT_Y - GROUND_Y) * smooth(q);
        }

        double el = Math.toRadians(deg);
        Vec3 axis = fwd.scale(Math.cos(el)).add(0.0, Math.sin(el), 0.0);
        axis = axis.lengthSqr() < 1.0E-8 ? new Vec3(0, 1, 0) : axis.normalize();

        Vec3 pivot = new Vec3(0.0, pivotY, 0.0);           // 实体局部坐标（实体在玩家脚下）
        Vec3 dir = axis.scale(LENGTH);                     // 支点 → 末端

        VertexConsumer vc = buffer.getBuffer(LINE_TYPE);
        Matrix4f m = pose.last().pose();

        drawRod(vc, m, pivot, axis, dir, grow, LINE_ALPHA * fade);
        drawFlame(vc, m, pivot, axis, dir, grow, age, fade);
    }

    // ================================================================ 线体：极细圆柱

    /** 沿摆臂生成一个六棱柱（只有两厘米粗，看上去就是一根很细的圆柱） */
    private static void drawRod(VertexConsumer vc, Matrix4f m, Vec3 pivot, Vec3 axis, Vec3 dir,
                                float grow, float alpha) {
        if (alpha <= 0.01f) return;
        // 横截面基：两个互相垂直、且都垂直于摆臂的单位向量
        Vec3 u = axis.cross(WORLD_UP);
        if (u.lengthSqr() < 1.0E-6) u = axis.cross(new Vec3(1, 0, 0));
        u = u.normalize();
        Vec3 v = axis.cross(u);

        double step = Math.PI * 2.0 / ROD_SIDES;
        for (int k = 0; k < ROD_SEGMENTS; k++) {
            float ua = grow * (k / (float) ROD_SEGMENTS);
            float ub = grow * ((k + 1) / (float) ROD_SEGMENTS);
            Vec3 ca = pivot.add(dir.scale(ua));
            Vec3 cb = pivot.add(dir.scale(ub));
            // 全程同粗细（用户明确要求，不做两端收尖）
            for (int s = 0; s < ROD_SIDES; s++) {
                double a0 = s * step;
                double a1 = (s + 1) * step;
                ringVtx(vc, m, ca, u, v, a0, alpha);
                ringVtx(vc, m, ca, u, v, a1, alpha);
                ringVtx(vc, m, cb, u, v, a1, alpha);

                ringVtx(vc, m, ca, u, v, a0, alpha);
                ringVtx(vc, m, cb, u, v, a1, alpha);
                ringVtx(vc, m, cb, u, v, a0, alpha);
            }
        }
    }

    /** 圆柱某一站位、某一角度上的环上顶点 */
    private static void ringVtx(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 u, Vec3 v,
                                double ang, float alpha) {
        double cs = Math.cos(ang) * ROD_RADIUS;
        double sn = Math.sin(ang) * ROD_RADIUS;
        float x = (float) (c.x + u.x * cs + v.x * sn);
        float y = (float) (c.y + u.y * cs + v.y * sn);
        float z = (float) (c.z + u.z * cs + v.z * sn);
        color(vc, m, x, y, z, DARK_RED, alpha);
    }

    // ================================================================ 火焰：长短不一的弧线

    /**
     * 火舌：沿摆臂每隔一小段立起一簇，**一律朝世界正上方生长**（不随相机转动），
     * 只在水平方向（沿摆臂）微微弯曲 ⇒ 无论从哪个角度看，火焰都是"立着"的。
     *
     * 每簇的**长度 / 弯曲 / 相位**都由 {@link #hash} 决定 → 长短不一、且每次释放都一样；
     * 再乘一个每簇独立相位的闪烁（长度跟着起伏）→ 此起彼伏。
     *
     * 形状：截面是 {@link #FLAME_SIDES} 边形小柱 ⇒ 有**厚度**（不是一张薄纸）；
     * 半径按**半椭圆包络** `√(1−t²)` 收细到 0 ⇒ 根部最宽、末端圆头，轮廓**圆润**（不是直线收尖）。
     */
    private static void drawFlame(VertexConsumer vc, Matrix4f m, Vec3 pivot, Vec3 axis, Vec3 dir,
                                  float grow, float age, float fade) {
        float base = ROD_RADIUS * 1.1f;      // 从圆柱表面起
        float halfBase = FLAME_STROKE * 0.5f;
        for (int i = 0; i < FLAME_LICKS; i++) {
            // 沿线位置（带一点抖动，但不打乱顺序）
            float u0 = (i + 0.30f + hash(i, 0) * 0.40f) / FLAME_LICKS;
            if (u0 > grow) continue;                       // 还没长到这儿
            float len = FLAME_LEN_MIN + hash(i, 1) * (FLAME_LEN_MAX - FLAME_LEN_MIN);
            float bend = (hash(i, 2) - 0.5f) * 2f * FLAME_BEND;
            double phase = hash(i, 3) * Math.PI * 2.0;
            // 每簇独立闪烁 + 长度跟着起伏 → 长短不一
            float fl = 0.32f + 0.68f * (0.5f + 0.5f * (float) Math.sin(age * FLAME_FLICKER + phase));
            float lenEff = len * (0.55f + 0.45f * fl);
            float alpha = FLAME_ALPHA * fl * fade;
            if (alpha <= 0.02f) continue;
            int rgb = lerpColor(FLAME_DEEP, FLAME_HOT, Mth.clamp(1f - len / FLAME_LEN_MAX, 0f, 1f));

            // 根部锚点：摆臂上的位置再沿世界 UP 抬一点，免得火舌长进柱子里面
            Vec3 anchor = pivot.add(dir.scale(u0)).add(WORLD_UP.scale(base));
            Vec3 prev = anchor;
            float prevHalf = halfBase;
            for (int j = 1; j <= FLAME_SUB; j++) {
                float t = j / (float) FLAME_SUB;
                // ★ 生长方向恒为世界 UP（不跟相机转）；弯曲只在水平方向（沿摆臂）且按长度成比例
                Vec3 cur = anchor
                        .add(WORLD_UP.scale(lenEff * t))
                        .add(axis.scale((double) bend * lenEff * t * t));
                // 半椭圆包络 → 圆润的轮廓（根部 widest、末端切向收成圆头）
                float curHalf = halfBase * (float) Math.sqrt(Math.max(0.0f, 1.0f - t * t));
                Vec3 tan = cur.subtract(prev);
                if (tan.lengthSqr() < 1.0E-10) {
                    prev = cur;
                    prevHalf = curHalf;
                    continue;
                }
                tan = tan.normalize();
                // 截面基：与火舌轴垂直的一对单位向量。纯色圆环，取哪一对都一样 ⇒ 不必管相机
                Vec3 ref = Math.abs(tan.y) < 0.9 ? WORLD_UP : new Vec3(1.0, 0.0, 0.0);
                Vec3 u = tan.cross(ref);
                u = u.lengthSqr() < 1.0E-8 ? new Vec3(1.0, 0.0, 0.0) : u.normalize();
                Vec3 v = tan.cross(u).normalize();
                flameSeg(vc, m, prev, cur, u, v, prevHalf, curHalf, rgb,
                        alpha * (1f - (j - 1) / (float) FLAME_SUB), alpha * (1f - t));
                prev = cur;
                prevHalf = curHalf;
            }
        }
    }

    /** 火舌柱体的一段侧面：{@link #FLAME_SIDES} 边形环之间的四边形（两端半径不同 → 自然收细） */
    private static void flameSeg(VertexConsumer vc, Matrix4f m, Vec3 c0, Vec3 c1, Vec3 u, Vec3 v,
                                 float r0, float r1, int rgb, float a0, float a1) {
        double step = Math.PI * 2.0 / FLAME_SIDES;
        for (int s = 0; s < FLAME_SIDES; s++) {
            double b0 = s * step;
            double b1 = (s + 1) * step;
            Vec3 p00 = ringPoint(c0, u, v, b0, r0);
            Vec3 p01 = ringPoint(c0, u, v, b1, r0);
            Vec3 p10 = ringPoint(c1, u, v, b0, r1);
            Vec3 p11 = ringPoint(c1, u, v, b1, r1);
            color(vc, m, p00, rgb, a0);
            color(vc, m, p01, rgb, a0);
            color(vc, m, p11, rgb, a1);

            color(vc, m, p00, rgb, a0);
            color(vc, m, p11, rgb, a1);
            color(vc, m, p10, rgb, a1);
        }
    }

    /** 环上一点：中心 c、截面基 u/v、角度 ang、半径 r */
    private static Vec3 ringPoint(Vec3 c, Vec3 u, Vec3 v, double ang, float r) {
        double cs = Math.cos(ang) * r;
        double sn = Math.sin(ang) * r;
        return new Vec3(c.x + u.x * cs + v.x * sn,
                c.y + u.y * cs + v.y * sn,
                c.z + u.z * cs + v.z * sn);
    }

    // ================================================================ 小工具

    /** 稳定的伪随机（0..1）：同一个 i/k 永远同一个值 → 每次释放布局一致 */
    private static float hash(int i, int k) {
        int h = i * 0x27D4EB2D + k * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x2545F491;
        h ^= h >>> 13;
        return (h & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t), g = (int) (ag + (bg - ag) * t), bl = (int) (ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static void color(VertexConsumer vc, Matrix4f m, Vec3 p, int rgb, float a) {
        color(vc, m, (float) p.x, (float) p.y, (float) p.z, rgb, a);
    }

    private static void color(VertexConsumer vc, Matrix4f m, float x, float y, float z, int rgb, float a) {
        vc.vertex(m, x, y, z)
                .color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF,
                        Mth.clamp((int) (a * 255.0f), 0, 255))
                .endVertex();
    }

    /** 平滑（smoothstep） */
    private static float smooth(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
