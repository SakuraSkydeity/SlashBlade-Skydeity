package com.example.skydeityslash.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 「向阳」第二形态**天降打击**（光炮）的程序化几何 —— 纯代码绘制（无贴图），加法混合。
 *
 * <p>★ 2026-09-26 第四轮（按反馈改）：
 * <ul>
 *   <li><b>符文删掉</b>：原来沿轴上升的代码符文整段移除，改由 {@code IroiXiangyangArt} 放**原版粒子**
 *       （redstone dust，和落点那簇同一套）沿光轴浮起 —— 粒子交给原版管道，省顶点也更"尘土感"。</li>
 *   <li><b>整体改成"光炮"该有的样子</b>：光束**加宽到落点处直径 0.84 格**、并沿轴**向上张开**（锥形，越靠炮口越粗，
 *       {@link #TAPER}）；横向剖面改成"**宽平顶 + 两端柔化**" ⇒ 是一根有体量的光柱，而不是一条细线。</li>
 *   <li><b>中心亮而淡</b>：亮芯半径加粗（0.06 → 0.12 直径）、alpha 反而降低 ⇒ 中心只是"柔和地更亮一点"，
 *       不再是一条一眼就看出来的亮线（上一版 2×0.20 太显眼）。</li>
 *   <li><b>加"命中一击"</b>：命中瞬间在落点来一发**短促的纵向闪光**（{@link #impactFlash}，先粗后细、5 tick 内收掉）
 *       —— 光炮的"打中了"就靠这一下（★ 仍然**不是贴地光斑/圆环**，地面保持干净）。</li>
 *   <li><b>引导环</b>：环带变细（{@link #RING_W1}/{@link #RING_W2}），环心由"十字"改成**十字星**
 *       （中心方块 + 四条收尖的臂，与地面法阵同一套图形语言）。</li>
 * </ul>
 *
 * <p>时间轴（与 {@code EntityIroiBeam.LIFETIME} 对齐）：
 * <pre>
 *   0  ─ 8    蓄势：高空**引导环**亮起（两圈细环反向自转 + 环心十字星）；**瞄准引线**自顶端向下长出来；落点**聚能**
 *   8  ─ 13   **光束**从顶端扫到命中点
 *   13        命中：落点**闪光**（伤害也在这一刻结算；原版紫尘随后沿光轴浮起）
 *   13 ─ 26   保持
 *   26 ─ 42   消散
 * </pre>
 *
 * <p>★ 全光束**只有一个颜色**（高饱和紫，中心更亮靠 alpha 而不是换色 ⇒ 不出"分层"）；没有贴图、没有圆柱壳、没有外层淡雾。
 * ★ 命中点**没有贴地光斑/圆环**；末端再往下伸 {@link #BELOW} 格**扎进地里**。
 * ★ 坐标系：**实体本地坐标**，原点 = 命中点（实体就生成在那里），y=0 是命中点所在高度。
 */
public final class IroiBeamGeometry {

    private IroiBeamGeometry() {
    }

    private static final float DEG = (float) Math.PI / 180F;

    // ================================================================ 时间轴（与 EntityIroiBeam.LIFETIME 对齐）
    /** 蓄势（引导环 + 引线 + 落点聚能） */
    private static final float T_CHARGE = 8F;
    /** 光束从顶端扫到命中点所需 tick */
    private static final float T_SWEEP = 5F;
    /** 命中时刻 —— ⚠ 必须与 {@code IroiXiangyangArt.BEAM_HIT_DELAY} 对齐，伤害才不会早于画面 */
    public static final float T_HIT = T_CHARGE + T_SWEEP;
    /** 命中闪光的持续 tick */
    private static final float T_FLASH = 5F;
    /** 整体淡出 */
    private static final float T_FADE_START = 26F;
    private static final float T_FADE_DUR = 16F;

    // ================================================================ 尺寸
    /** 沿轴的细分段数（内芯与光带共用） */
    private static final int SEG = 14;
    /** 光束**基准**长度（格）—— 实际长度 = 本值 × 实体的长度倍率；与 {@code EntityIroiBeam.BEAM_LENGTH} 一致 */
    private static final float LENGTH = 38F;
    /** 与地面的夹角（度）—— 越接近 90° 越竖直 */
    private static final float PITCH_DEG = 68F;
    /**
     * 光束末端**插入地下的深度**（格）—— 落点整体往下多伸这么长。
     * ★ 这样末端不会被地面"齐平切掉"而显得悬空，看上去是**扎进地里**。
     */
    private static final float BELOW = .80F;
    /**
     * **锥形张开**：半径沿轴从 {@code 1.0×} 涨到 {@code (1+TAPER)×}（0 = 粗细均匀）。
     * ★ 光炮的体量感多半来自这个"越往上越粗"，均匀圆柱看着像一根管子。
     */
    private static final float TAPER = .55F;

    // ---- 内芯（中心那一点更亮）----
    /** 内芯圆柱的棱数 */
    private static final int ROD_SIDES = 8;
    /** 内芯半径（格）—— 比上一版粗一倍（直径 0.06 → 0.12），配合更低的 alpha ⇒ "亮而淡" */
    private static final float ROD_RADIUS = .060F;
    /**
     * 整条光束**唯一的颜色** —— 一支**深紫**（0.52/0.04/0.78，比原来的 0.75/0.08/1.00 暗约三成）。
     * ★ 2026-09-27：这次只改**iroi 光束**（其他特效保持原色）—— 内芯与光带共用这一支深色，
     * 不再有偏白的亮档；亮度只由 alpha 的**大小**决定，不用颜色去拉。
     */
    private static final float[] BEAM_RGB = {.52F, .04F, .78F};
    /** 内芯颜色 = 光束颜色（★ 完全同色：中心"更亮"只靠 alpha，换色就会显出"一圈不同色的芯"） */
    private static final float[] ROD_RGB = BEAM_RGB;
    /** 内芯每层的 alpha（★ 圆柱正反两面都会画到 ⇒ 轴心实际是 2 × 本值）—— 压到 .12 才"淡" */
    private static final float A_ROD = .10F;

    // ---- 外层（光柱本体）----
    /** 光柱**基准半径**（格）⇒ 落点处直径 0.84 格、炮口处再 ×(1+TAPER) */
    private static final float RADIUS = .42F;
    /** 光带颜色 = 光束颜色（同一个常量，绝不出现第二支色） */
    private static final float[] VEIL_RGB = BEAM_RGB;
    /** 光带横向的 6 个采样位置（对称） */
    private static final float[] VEIL_F = {-1F, -.8F, -.35F, .35F, .8F, 1F};
    /**
     * 每一列的 alpha 倍率：**平顶**（中间四列全是 1，即整条带子等亮）＋ 两端柔化。
     * ★ 2026-09-27：不再用"中心尖峰"那种 alpha 梯度去造亮度层次 —— 那样看着就是"半透明的一层套一层"。
     *   （本条仅针对光束；其他特效的配色未动。）
     */
    private static final float[] VEIL_A = {0F, 1F, 1F, 1F, 1F, 0F};
    /** 光带轴心处的 alpha */
    private static final float A_VEIL = .075F;

    // ---- 顶部引导环（炮口）----
    /** 引线半径（格） */
    private static final float THREAD_R = .055F;
    /** 引线每层的 alpha */
    private static final float A_THREAD = .11F;
    /** 引导环的两圈半径（格）与自转速度（度/tick，反向） */
    private static final float RING_R1 = 3.0F, RING_R2 = 1.9F, RING_SPIN = 3.4F;
    /** 引导环环带半宽（格）—— ★ 细一圈（原来 .13/.10 太粗） */
    private static final float RING_W1 = .055F, RING_W2 = .045F;
    /** 引导环每层的 alpha */
    private static final float A_RING1 = .22F, A_RING2 = .20F;
    /** 环心十字星：中心方块半宽 / 臂长（格，相对内圈半径） */
    private static final float RING_STAR = .46F, STAR_ARM = .34F, STAR_TH = .105F;
    /** 环心十字星每层的 alpha */
    private static final float A_STAR = .22F;

    // ---- 落点聚能（蓄势时在命中点积起来的一小簇光）----
    /** 聚能的高度（格，沿轴向上；tube 里 length 传 1 就是"格"）与半径（格） */
    private static final float SPARK_H = 1.7F, SPARK_R = .085F;
    /** 聚能每层的 alpha（★ 与引线会重叠 ⇒ 两个都要压低） */
    private static final float A_SPARK = .13F;

    // ---- 命中闪光（"打中了"的那一下）----
    /** 闪光沿轴的高度（格）、起止半径（格）与每层 alpha */
    private static final float FLASH_H = 3.2F, FLASH_R0 = .92F, FLASH_R1 = .16F, A_FLASH = .18F;

    /** 给渲染器用：整条特效的时长（tick） */
    public static final float LIFETIME = 42F;

    /**
     * 画一道光束（局部坐标，原点 = 命中点）。
     *
     * @param yawDeg      水平方位角：光束**从这一侧**的斜上方打过来
     * @param lengthScale 长度倍率（1.0 = {@link #LENGTH} 格）
     * @param viewYaw     相机偏航（度）—— 柔光带要正对相机
     * @param viewPitch   相机俯仰（度）
     */
    public static void draw(Matrix4f m, VertexConsumer vc, float age, float yawDeg, float lengthScale,
                            float viewYaw, float viewPitch) {
        float fade = 1F - smooth(stage(age, T_FADE_START, T_FADE_DUR));
        if (fade <= .01F) return;

        final float length = LENGTH * (lengthScale <= .05F ? 1F : lengthScale);

        float charge = smooth(Mth.clamp(age / T_CHARGE, 0F, 1F));
        float sweep = smooth(Mth.clamp((age - T_CHARGE) / T_SWEEP, 0F, 1F));
        float tLow = 1F - sweep;

        // 轴向单位向量（从命中点指向光源）：水平方位取 yaw、与地面成 PITCH_DEG
        float yaw = yawDeg * DEG, pit = PITCH_DEG * DEG;
        Vec3 d = new Vec3(-Mth.sin(yaw) * Mth.cos(pit), Mth.sin(pit), Mth.cos(yaw) * Mth.cos(pit)).normalize();
        Vec3 u = d.cross(new Vec3(0.0, 1.0, 0.0));
        u = u.lengthSqr() < 1.0E-8 ? new Vec3(1.0, 0.0, 0.0) : u.normalize();
        Vec3 v = d.cross(u).normalize();

        // 相机的"右 / 上"方向（与 GlowGeometry.glowBall 同一套约定）
        float dy = viewYaw * DEG, dp = viewPitch * DEG;
        float sy = Mth.sin(dy), cy = Mth.cos(dy), sp = Mth.sin(dp), cp = Mth.cos(dp);
        Vec3 camRight = new Vec3(-cy, 0.0, -sy);
        Vec3 camUp = new Vec3(-sy * sp, cp, cy * sp);

        // ---- 蓄势：炮口引导环 + 自上而下的瞄准引线 + 落点聚能 ----
        guideRing(m, vc, d, u, v, age, charge, fade, length);
        thread(m, vc, d, u, v, charge, sweep, fade, length);
        chargeSpark(m, vc, d, u, v, age, charge, sweep, fade);

        // ---- 光束：一片柔光带（带锥度）+ 一根同色亮芯 ----
        if (tLow < 1F) {
            veil(m, vc, d, camRight, camUp, length, tLow, fade * A_VEIL);
            rod(m, vc, d, u, v, length, tLow, fade);
        }

        // ---- 命中那一下的闪光 ----
        impactFlash(m, vc, d, u, v, age, fade);
    }

    // ================================================================ 炮口引导环

    /**
     * 高空的**引导环**（炮口）：两圈**细**环反向自转，环心一颗**十字星**（中心方块 + 四条收尖的臂）。
     * ★ 环带只 .055/.045 格宽；十字星与地面法阵同一套图形语言。
     */
    private static void guideRing(Matrix4f m, VertexConsumer vc, Vec3 d, Vec3 u, Vec3 v,
                                  float age, float charge, float fade, float length) {
        if (charge <= .01F) return;
        Vec3 c = d.scale(length - BELOW);           // 光束顶端
        float on = charge * fade;
        float spinDeg = age * RING_SPIN;
        band(m, vc, c, u, v, RING_R1, RING_W1, 16, spinDeg, VEIL_RGB, on * A_RING1);
        band(m, vc, c, u, v, RING_R2, RING_W2, 12, -spinDeg, ROD_RGB, on * A_RING2);
        star(m, vc, c, u, v, spinDeg * DEG * .55F, RING_R2 * STAR_ARM, STAR_TH, on * A_STAR);
    }

    /** 一圈**扁平环带**（法阵式细环）：中心 c、平面基 u/v、半径 radius、带宽 ±halfW、sides 段。 */
    private static void band(Matrix4f m, VertexConsumer vc, Vec3 c, Vec3 u, Vec3 v,
                             float radius, float halfW, int sides, float spinDeg,
                             float[] rgb, float a) {
        if (a <= .003F) return;
        float s = spinDeg * DEG;
        for (int i = 0; i < sides; i++) {
            double b0 = s + Math.PI * 2.0 * i / sides, b1 = s + Math.PI * 2.0 * (i + 1) / sides;
            Vec3 in0 = c.add(u.scale(Math.cos(b0) * (radius - halfW))).add(v.scale(Math.sin(b0) * (radius - halfW)));
            Vec3 in1 = c.add(u.scale(Math.cos(b1) * (radius - halfW))).add(v.scale(Math.sin(b1) * (radius - halfW)));
            Vec3 out0 = c.add(u.scale(Math.cos(b0) * (radius + halfW))).add(v.scale(Math.sin(b0) * (radius + halfW)));
            Vec3 out1 = c.add(u.scale(Math.cos(b1) * (radius + halfW))).add(v.scale(Math.sin(b1) * (radius + halfW)));
            quad(m, vc, in0, rgb, a, out0, rgb, a, out1, rgb, a, in1, rgb, a);
        }
    }

    /**
     * **十字星**（四角星）：中心一个方块 + 四条收尖的臂，画在环所在的平面上、整体随环慢慢转。
     * ★ 与"十字"（两根直条交叉 = 加号）的区别就是**臂是收尖的三角形**，远看是星星。
     */
    private static void star(Matrix4f m, VertexConsumer vc, Vec3 c, Vec3 u, Vec3 v,
                             float rot, float arm, float th, float a) {
        if (a <= .003F) return;
        Vec3 ax = u.scale(Mth.cos(rot)).add(v.scale(Mth.sin(rot)));
        Vec3 ay = u.scale(-Mth.sin(rot)).add(v.scale(Mth.cos(rot)));
        // 中心方块
        quad(m, vc,
                c.add(ax.scale(-th)).add(ay.scale(-th)), ROD_RGB, a,
                c.add(ax.scale(th)).add(ay.scale(-th)), ROD_RGB, a,
                c.add(ax.scale(th)).add(ay.scale(th)), ROD_RGB, a,
                c.add(ax.scale(-th)).add(ay.scale(th)), ROD_RGB, a);
        // 上 / 下 / 右 / 左 四条收尖的臂
        tri(m, vc, c.add(ax.scale(-th)).add(ay.scale(th)), ROD_RGB, a,
                c.add(ax.scale(th)).add(ay.scale(th)), ROD_RGB, a,
                c.add(ay.scale(th + arm)), ROD_RGB, a);
        tri(m, vc, c.add(ax.scale(-th)).add(ay.scale(-th)), ROD_RGB, a,
                c.add(ax.scale(th)).add(ay.scale(-th)), ROD_RGB, a,
                c.add(ay.scale(-th - arm)), ROD_RGB, a);
        tri(m, vc, c.add(ax.scale(th)).add(ay.scale(-th)), ROD_RGB, a,
                c.add(ax.scale(th)).add(ay.scale(th)), ROD_RGB, a,
                c.add(ax.scale(th + arm)), ROD_RGB, a);
        tri(m, vc, c.add(ax.scale(-th)).add(ay.scale(-th)), ROD_RGB, a,
                c.add(ax.scale(-th)).add(ay.scale(th)), ROD_RGB, a,
                c.add(ax.scale(-th - arm)), ROD_RGB, a);
    }

    // ================================================================ 蓄势引线 / 聚能 / 命中闪光

    /**
     * **瞄准引线**：一根细亮线，蓄势时**自顶端向下长出来**（前缘渐隐），光束扫下来时迅速让位
     * （否则引线会和光束叠在同一条轴上）。
     */
    private static void thread(Matrix4f m, VertexConsumer vc, Vec3 d, Vec3 u, Vec3 v,
                               float charge, float sweep, float fade, float length) {
        float grow = smooth(Mth.clamp((charge - .12F) / .80F, 0F, 1F));     // 0→1：从上往下长
        float gone = (1F - sweep) * (1F - smooth(Mth.clamp(sweep / .25F, 0F, 1F)));
        if (grow <= .02F || gone <= .02F) return;
        float headT = 1F - grow;                                           // 前缘所在的轴参数
        final int seg = 10;
        for (int i = 0; i < seg; i++) {
            float t0 = i / (float) seg, t1 = (i + 1) / (float) seg;
            if (t1 <= headT) continue;                                     // 还没长到这里
            float vis = smooth(Mth.clamp((t0 - headT) / .18F, 0F, 1F));
            float a = fade * gone * A_THREAD * vis;
            if (a <= .003F) continue;
            tube(m, vc, d, u, v, THREAD_R, THREAD_R, 6, t0, t1, length, ROD_RGB, a, a);
        }
    }

    /**
     * 落点**聚能**：蓄势时在命中点贴着轴线积起来的一小簇光（**不是贴地光斑/圆环**）。
     * ★ 存在的意义：引导环在 30 多格高的天上，正常视角经常看不到 —— 这一簇才是"蓄势"看得见的那一笔。
     */
    private static void chargeSpark(Matrix4f m, VertexConsumer vc, Vec3 d, Vec3 u, Vec3 v,
                                    float age, float charge, float sweep, float fade) {
        float on = smooth(Mth.clamp((charge - .25F) / .75F, 0F, 1F))
                * (1F - sweep) * (1F - smooth(Mth.clamp(sweep / .30F, 0F, 1F))) * fade;
        if (on <= .02F) return;
        float pulse = 1F + .16F * Mth.sin(age * .75F);                     // 轻微起伏，像在"积"
        float r = SPARK_R * pulse;
        tube(m, vc, d, u, v, r, r * .5F, 8, 0F, SPARK_H, 1F, ROD_RGB,
                on * A_SPARK * 1.25F, on * A_SPARK * .35F);
    }

    /**
     * **命中闪光**：命中那一瞬在落点贴着轴线炸开的一小段光，**先粗后细、5 tick 收掉**。
     * ★ 这一下就是"光炮打中了"的手感来源（★ 不是贴地光斑/圆环，地面仍然干净）。
     */
    private static void impactFlash(Matrix4f m, VertexConsumer vc, Vec3 d, Vec3 u, Vec3 v,
                                    float age, float fade) {
        float t = age - T_HIT;
        if (t < 0F || t > T_FLASH) return;
        float k = 1F - t / T_FLASH;                                        // 1 → 0
        float a = k * k * fade * A_FLASH;
        if (a <= .004F) return;
        float r0 = FLASH_R1 + (FLASH_R0 - FLASH_R1) * k;                   // 由粗收细
        tube(m, vc, d, u, v, r0, r0 * .45F, 10, 0F, FLASH_H, 1F, ROD_RGB, a, a * .25F);
    }

    // ================================================================ 内芯圆柱

    /** 沿轴生成一根细棱柱（正反两面都画 ⇒ 任何角度看都是实心柱），半径随锥度张开 */
    private static void rod(Matrix4f m, VertexConsumer vc, Vec3 d, Vec3 u, Vec3 v,
                            float length, float tLow, float fade) {
        for (int i = 0; i < SEG; i++) {
            float t0 = i / (float) SEG, t1 = (i + 1) / (float) SEG;
            float a0 = axisEnv(t0, tLow) * fade * A_ROD, a1 = axisEnv(t1, tLow) * fade * A_ROD;
            if (a0 <= .003F && a1 <= .003F) continue;
            tube(m, vc, d, u, v, ROD_RADIUS * taper(t0), ROD_RADIUS * taper(t1), ROD_SIDES,
                    t0, t1, length, ROD_RGB, a0, a1);
        }
    }

    // ================================================================ 外层（一片柔光带，带锥度）

    /**
     * **正对相机的柔光带**：宽度方向取"相机右向量在垂直于光轴的平面上的投影"，所以永远侧对观察者；
     * 横向 alpha 是"**宽平顶 + 两端柔化**"，半径沿轴按 {@link #TAPER} 张开 ⇒ 一根有体量、边缘不生硬的光柱。
     * ★ 这是光束**唯一**的外层（兜底圆柱壳与淡雾都已删）。
     */
    private static void veil(Matrix4f m, VertexConsumer vc, Vec3 d, Vec3 camRight, Vec3 camUp,
                             float length, float tLow, float alpha) {
        Vec3 w = camRight.subtract(d.scale(camRight.dot(d)));
        if (w.lengthSqr() < 1.0E-6) w = camUp.subtract(d.scale(camUp.dot(d)));
        if (w.lengthSqr() < 1.0E-6) return;
        w = w.normalize();

        for (int i = 0; i < SEG; i++) {
            float t0 = i / (float) SEG, t1 = (i + 1) / (float) SEG;
            float e0 = axisEnv(t0, tLow) * alpha, e1 = axisEnv(t1, tLow) * alpha;
            if (e0 <= .002F && e1 <= .002F) continue;
            for (int c = 0; c < VEIL_F.length - 1; c++) {
                Vec3 p00 = pt(d, w, t0, VEIL_F[c], length);
                Vec3 p01 = pt(d, w, t0, VEIL_F[c + 1], length);
                Vec3 p10 = pt(d, w, t1, VEIL_F[c], length);
                Vec3 p11 = pt(d, w, t1, VEIL_F[c + 1], length);
                quad(m, vc,
                        p00, VEIL_RGB, e0 * VEIL_A[c],
                        p01, VEIL_RGB, e0 * VEIL_A[c + 1],
                        p11, VEIL_RGB, e1 * VEIL_A[c + 1],
                        p10, VEIL_RGB, e1 * VEIL_A[c]);
            }
        }
    }

    /** 光带上的一个点：沿轴 t（0 = 命中点那一端）、横向 f（{@link #VEIL_F} 里的取值） */
    private static Vec3 pt(Vec3 d, Vec3 w, float t, float f, float length) {
        return d.scale(length * t - BELOW).add(w.scale(RADIUS * taper(t) * f));
    }

    /** 锥度：沿轴 t 处的半径倍率（0 端 = 1.0，炮口端 = 1 + {@link #TAPER}） */
    private static float taper(float t) {
        return 1F + TAPER * Mth.clamp(t, 0F, 1F);
    }

    /** 一段柱面（[t0, t1] × 一圈 sides 边，两端半径可不同）。沿轴位置整体下移 {@link #BELOW} 格 */
    private static void tube(Matrix4f m, VertexConsumer vc, Vec3 d, Vec3 u, Vec3 v,
                             float r0, float r1, int sides, float t0, float t1, float length,
                             float[] rgb, float a0, float a1) {
        Vec3 c0 = d.scale(length * t0 - BELOW), c1 = d.scale(length * t1 - BELOW);
        for (int s = 0; s < sides; s++) {
            double b0 = Math.PI * 2.0 * s / sides, b1 = Math.PI * 2.0 * (s + 1) / sides;
            quad(m, vc,
                    ring(c0, u, v, b0, r0), rgb, a0, ring(c0, u, v, b1, r0), rgb, a0,
                    ring(c1, u, v, b1, r1), rgb, a1, ring(c1, u, v, b0, r1), rgb, a1);
        }
    }

    /** 圆柱横截面上的一点：中心 c、截面基 u/v、角度 ang、半径 radius */
    private static Vec3 ring(Vec3 c, Vec3 u, Vec3 v, double ang, float radius) {
        double cs = Math.cos(ang) * radius, sn = Math.sin(ang) * radius;
        return new Vec3(c.x + u.x * cs + v.x * sn, c.y + u.y * cs + v.y * sn, c.z + u.z * cs + v.z * sn);
    }

    // ================================================================ 包络 / 工具

    /**
     * 沿轴方向的强度包络（内芯与光带共用）：
     * · {@code t < tLow} 的部分还没扫到 ⇒ 0；
     * · 刚扫到的"头部"用 12% 的长度渐起（否则是一条硬切边）；
     * · 远端（t=1）必须归 0 —— 高空留一条平切硬边是光束类特效的经典翻车点。
     */
    private static float axisEnv(float t, float tLow) {
        if (t < tLow) return 0F;
        float head = smooth(Mth.clamp((t - tLow) / .12F, 0F, 1F));
        return (float) Math.pow(1F - t, .6F) * head;
    }

    private static void tri(Matrix4f m, VertexConsumer vc,
                            Vec3 p0, float[] c0, float a0,
                            Vec3 p1, float[] c1, float a1,
                            Vec3 p2, float[] c2, float a2) {
        vv(m, vc, p0, c0, a0);
        vv(m, vc, p1, c1, a1);
        vv(m, vc, p2, c2, a2);
    }

    private static void quad(Matrix4f m, VertexConsumer vc,
                             Vec3 p0, float[] c0, float a0, Vec3 p1, float[] c1, float a1,
                             Vec3 p2, float[] c2, float a2, Vec3 p3, float[] c3, float a3) {
        tri(m, vc, p0, c0, a0, p1, c1, a1, p2, c2, a2);
        tri(m, vc, p0, c0, a0, p2, c2, a2, p3, c3, a3);
    }

    private static void vv(Matrix4f m, VertexConsumer vc, Vec3 p, float[] c, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color(ci(c[0]), ci(c[1]), ci(c[2]), ci(a))
                .endVertex();
    }

    private static int ci(float v) {
        return Mth.clamp((int) (v * 255F), 0, 255);
    }

    private static float stage(float age, float start, float dur) {
        return Mth.clamp((age - start) / dur, 0F, 1F);
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0F, 1F);
        return t * t * (3F - 2F * t);
    }
}
