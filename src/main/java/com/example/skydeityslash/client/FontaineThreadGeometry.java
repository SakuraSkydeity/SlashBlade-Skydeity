package com.example.skydeityslash.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 「枫丹」全息剑**落地后四周丝线汇聚**的几何层 —— **纯代码绘制**（不用粒子、不用贴图），加法混合。
 *
 * <p>表现：绕落点一圈 {@link #COUNT} 条细丝，**每条起点各不相同**（方位、半径、高度都错开），
 * 而**终点是同一个**（剑根部 {@link #END_Y} 那一点）；每条都是一道**弧**，并且随时间被"抽"向终点
 * —— 可见线段两端柔化，所以是一根根**会走的丝线**。
 *
 * <p>★ 曲线是**三次贝塞尔**（两个控制点）：
 * <ol>
 *   <li>控制点 1 = 弦中点 + 向外 + 向上 ⇒ 主弧（幅度逐条不同）；</li>
 *   <li>控制点 2 = **贴着终点、横向偏出去一点**（{@link #HOOK} / {@link #HOOK_UP}）
 *       ⇒ 丝线是"绕/钩"进剑根的，**靠近终点那一段也是弯的**，不是笔直插进去（2026-09-28 加的）。</li>
 * </ol>
 * 再叠一层**垂直于弧平面的正弦波动**（{@link #WAVE}），包络是**平顶**的（{@link #pointOn}）
 * ⇒ 起伏一直保持到很靠近终点才收住。只有一道平面弧时，看得见的那一小段仍像直线，必须靠这个"离开平面"。
 *
 * <p>★ 为什么改成 Java 画（原本是服务端 dust 粒子）：粒子拼出来的线本质是"一串点"，
 * 要连成线得每 tick 把中间那一段补齐，既费粒子又仍看得出颗粒；几何版是真正的连续带子，
 * 宽度、颜色、透明度都能逐顶点给，弧线也能精确算。而且它**不需要同步**——渲染器按
 * {@code entity.tickCount} 现算进度即可。
 *
 * <p>★ 每段同时铺两条带子（面内 + 垂直于面）⇒ 任何角度看都不会退化成一根零宽的线。
 */
public final class FontaineThreadGeometry {

    private FontaineThreadGeometry() {}

    /** 丝线条数（绕落点一圈） */
    private static final int COUNT = 12;
    /** 每条丝的细分段数（越大越"丝滑"）—— ★ 2026-09-28：14 → 22 */
    private static final int SEG = 22;
    /** 丝线宽度（格）—— 很细，是"丝" */
    private static final float WIDTH = .055F;
    /** 终点（剑根）相对实体原点的高度 */
    private static final double END_Y = .18;
    /**
     * 起点半径 / 高度范围（格）：每条在其间取不同的值 ⇒ "每个起点也不同"。
     * ★ 2026-09-28 按"初始的位子再远一点"整体外移：半径 `2.30~3.45 → **3.30~4.80**`、高度 `0.85~2.20 → **1.00~2.60**`。
     */
    private static final double R_MIN = 3.30, R_MAX = 4.80;
    private static final double H_MIN = 1.00, H_MAX = 2.60;
    /**
     * 主弧的鼓起量：向外（占半径比例）+ 向上（格）。
     * ★ 2026-09-28 按"丝滑一点、不要一直线"加大：`.34/.55` → **`.68/.80`**（弧更足）。
     */
    private static final double BOW_OUT = .68, BOW_UP = .80;
    /**
     * ★ 2026-09-28 新增：**靠近终点那一段的"钩"** —— 第二个控制点贴着终点、但在**水平面内横向**偏出去
     * （{@link #HOOK} × 半径，方向逐条左右不同）并抬高 {@link #HOOK_UP} 格。
     * 于是丝线是**弯着绕进剑根**的，而不是到最后一段还是直的。
     */
    private static final double HOOK = .30, HOOK_UP = .45;
    /**
     * 沿丝线的**波动**（垂直于弧平面的正弦扰动，单位格）。
     * ★ 两端仍收在原来的点上（{@link #pointOn} 的包络在 t=1 处归零），
     * 频率刻意压在 **1 个波以内**（0.55~0.95 圈）：要的是**一条顺滑的长 S**，不是一串小涟漪 ——
     * 频率上到 2 圈时看着像"抖动的触手"，不叫丝滑。
     */
    private static final double WAVE = .16, WAVE_MIN = .55, WAVE_MAX = .95;
    /** 可见线段长度（占整条丝的比例）与两端柔化比例 —— ★ 可见段加长到 0.72，弧形才看得出来 */
    private static final float TRAIL = .72F, SOFT = .25F;
    /** 颜色：芯一支蓝、两侧更淡的蓝（横向渐变，做法与翾风回雪环一致；**不用近白**） */
    private static final float[] CORE = {.28F, .58F, 1.00F};
    private static final float[] EDGE = {.48F, .75F, 1.00F};
    private static final float[] COL_F = {-1F, -.42F, .42F, 1F};
    private static final float[] COL_A = {0F, 1F, 1F, 0F};

    /**
     * @param p     落地后的进度 0→1（0 = 刚落地，1 = 收完）
     * @param alpha 整条效果的透明度（与全息剑的淡出同步）
     */
    public static void draw(Matrix4f m, VertexConsumer vc, float p, float alpha) {
        if (alpha <= .01F || p >= 1F) return;
        float head = ease(p);                          // 丝线前沿：起点 → 终点
        float tail = Math.max(0F, head - TRAIL);       // 尾巴落后一截 ⇒ 看着是"被抽过去"
        Vec3 end = new Vec3(0, END_Y, 0);
        for (int i = 0; i < COUNT; i++) {
            double ang = i * (Math.PI * 2.0 / COUNT) + .37;
            double r = R_MIN + (R_MAX - R_MIN) * hash01(i, 1.7F);
            double h = H_MIN + (H_MAX - H_MIN) * hash01(i, 4.3F);
            Vec3 start = new Vec3(Math.cos(ang) * r, h, Math.sin(ang) * r);
            Vec3 out = new Vec3(start.x, 0, start.z);
            out = out.lengthSqr() < 1E-8 ? new Vec3(0, 0, 1) : out.normalize();
            Vec3 side = new Vec3(-out.z, 0, out.x);     // 水平面内、与 out 垂直 ⇒ 收尾那段的"钩"方向
            float sgn = hash01(i, 11.3F) < .5F ? -1F : 1F;
            // 控制点 1：弦中点 + 向外 + 向上 ⇒ 主弧；幅度逐条不同
            Vec3 c1 = start.add(end).scale(.5)
                    .add(out.scale(r * BOW_OUT * (.72 + .56 * hash01(i, 7.9F))))
                    .add(0, BOW_UP * (.45 + .85 * hash01(i, 9.1F)), 0);
            // 控制点 2：贴着终点、但横向偏出去一点 ⇒ **靠近终点也是弯的**（左右方向逐条不同）
            Vec3 c2 = end.add(side.scale(r * HOOK * sgn * (.50 + .80 * hash01(i, 12.7F))))
                    .add(0, HOOK_UP * (.40 + .85 * hash01(i, 13.9F)), 0);
            Vec3 planeN = planeNormal(start, end, c1, c2);
            float a = alpha * (.55F + .45F * hash01(i, 2.3F));
            // 每条的波动幅度 / 频率 / 相位都不同 ⇒ 12 根丝各自飘，不会像 12 条同款弧线
            strand(m, vc, start, c1, c2, end, planeN, tail, head, a,
                    WAVE * (.65 + .70 * hash01(i, 5.5F)),
                    WAVE_MIN + (WAVE_MAX - WAVE_MIN) * hash01(i, 6.1F),
                    hash01(i, 8.3F) * Math.PI * 2.0);
        }
    }

    /** 一条丝：把 [tail, head] 这段（带波动的）曲线铺成两条交叉的渐变带子 */
    private static void strand(Matrix4f m, VertexConsumer vc, Vec3 p0, Vec3 c1, Vec3 c2, Vec3 p1,
                               Vec3 planeN, float tail, float head, float alpha,
                               double waveAmp, double waveFreq, double wavePhase) {
        if (head <= tail + 1E-4F) return;
        Vec3 prev = pointOn(p0, c1, c2, p1, planeN, tail, waveAmp, waveFreq, wavePhase);
        for (int k = 0; k < SEG; k++) {
            float t0 = tail + (head - tail) * k / SEG, t1 = tail + (head - tail) * (k + 1) / SEG;
            Vec3 cur = pointOn(p0, c1, c2, p1, planeN, t1, waveAmp, waveFreq, wavePhase);
            Vec3 dir = cur.subtract(prev);
            if (dir.lengthSqr() < 1E-10) { prev = cur; continue; }
            dir = dir.normalize();
            float v0 = (t0 - tail) / (head - tail), v1 = (t1 - tail) / (head - tail);
            float w0 = WIDTH * taper(v0), w1 = WIDTH * taper(v1);
            Vec3 side = planeN.cross(dir).normalize();          // 面内
            band(m, vc, prev, cur, side, w0, w1, alpha);
            band(m, vc, prev, cur, dir.cross(side).normalize(), w0 * .55F, w1 * .55F, alpha * .5F);
            prev = cur;
        }
    }

    /**
     * 丝线上的一点 = 三次贝塞尔 + **垂直于弧平面的正弦波动**。
     * ★ 包络用 {@code sin(π t)^0.35}（**平顶**）：起伏在整个中段基本满幅、到很靠近终点才迅速收住
     * ⇒ 收尾那一段同样是弯的；同时 t=0 / t=1 处包络恰好为 0 ⇒ **起点与终点仍在原来的两个点上**
     * （12 条丝才真的汇聚到同一个剑根）。
     */
    private static Vec3 pointOn(Vec3 p0, Vec3 c1, Vec3 c2, Vec3 p1, Vec3 planeN, float t,
                                double amp, double freq, double phase) {
        Vec3 base = bez(p0, c1, c2, p1, t);
        if (amp <= 1E-6) return base;
        double env = Math.pow(Math.sin(Math.PI * t), .35);
        double w = amp * env * Math.sin(t * freq * Math.PI * 2.0 + phase);
        return base.add(planeN.scale(w));
    }

    /** 弧平面法线（波动沿它起伏）：优先用主弧的弯曲方向，退化时用第二个控制点兜底 */
    private static Vec3 planeNormal(Vec3 p0, Vec3 p1, Vec3 c1, Vec3 c2) {
        Vec3 base = p1.subtract(p0), mid = p0.add(p1).scale(.5);
        Vec3 n = base.cross(c1.subtract(mid));
        if (n.lengthSqr() < 1E-8) n = base.cross(c2.subtract(p0));
        return n.lengthSqr() < 1E-8 ? new Vec3(0, 1, 0) : n.normalize();
    }

    /** 可见线段两端收细（0/1 处细、中间满） */
    private static float taper(float v) {
        return SOFT + (1F - SOFT) * (float) Math.pow(Math.max(0, Math.sin(Math.PI * Mth.clamp(v, 0, 1))), .55);
    }

    /** 三次贝塞尔（两个控制点：主弧 + 收尾的钩） */
    private static Vec3 bez(Vec3 p0, Vec3 c1, Vec3 c2, Vec3 p1, float t) {
        float u = 1F - t;
        return p0.scale(u * u * u).add(c1.scale(3F * u * u * t))
                .add(c2.scale(3F * u * t * t)).add(p1.scale(t * t * t));
    }

    /** 收束曲线：前慢后快（"被抽过去"的手感） */
    private static float ease(float p) {
        float t = Mth.clamp(p, 0F, 1F);
        return 1F - (1F - t) * (1F - t);
    }

    /** 横向 4 列渐变的细带（外缘 alpha 0 收边 ⇒ 没有硬边、也没有白） */
    private static void band(Matrix4f m, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 side,
                             float wa, float wb, float alpha) {
        if (alpha <= .004F) return;
        for (int k = 0; k < 3; k++) {
            float f0 = COL_F[k] * .5F, f1 = COL_F[k + 1] * .5F;
            float[] c0 = k == 0 ? EDGE : CORE, c1 = k == 2 ? EDGE : CORE;
            float a0 = alpha * COL_A[k], a1 = alpha * COL_A[k + 1];
            Vec3 p0 = a.add(side.scale(wa * f0)), p1 = b.add(side.scale(wb * f0));
            Vec3 p2 = b.add(side.scale(wb * f1)), p3 = a.add(side.scale(wa * f1));
            vv(m, vc, p0, c0, a0); vv(m, vc, p1, c0, a0); vv(m, vc, p2, c1, a1);
            vv(m, vc, p0, c0, a0); vv(m, vc, p2, c1, a1); vv(m, vc, p3, c1, a1);
        }
    }

    private static void vv(Matrix4f m, VertexConsumer vc, Vec3 p, float[] c, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color(Mth.clamp(c[0], 0, 1), Mth.clamp(c[1], 0, 1), Mth.clamp(c[2], 0, 1), Mth.clamp(a, 0, 1))
                .endVertex();
    }

    /** 稳定的伪随机（0..1）：同一个 i/salt 永远同一个值 ⇒ 每次落地丝的分布一致（不会一次一个样） */
    private static float hash01(int i, float salt) {
        float v = Mth.sin(i * 12.9898F + salt * 78.233F) * 43758.547F;
        return v - (float) Math.floor(v);
    }
}
