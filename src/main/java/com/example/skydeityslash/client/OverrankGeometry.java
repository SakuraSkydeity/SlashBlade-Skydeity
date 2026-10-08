package com.example.skydeityslash.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * 「超位法术」的程序化加法几何 —— 与贴图法阵分工：
 * <ul>
 *   <li><b>贴图</b>（{@code RenderOverrankMagic} 里的 array/runes 两层）负责地面上的线稿；</li>
 *   <li><b>这里</b>负责一切"发光体"：光柱、光墙、升腾符文、冲击环、核心光球。</li>
 * </ul>
 *
 * <p>全部走 {@link GlowGeometry#GLOW}（POSITION_COLOR + 加法混合 + 不写深度 + 无剔除），
 * 逐顶点给 alpha，靠叠加出亮度。坐标系是**实体本地坐标**：y=0 就是地面。
 *
 * <p>时间轴（下面的刻度是**设计值**；实际播放时整体按 {@link #TIME_SCALE}（= 4/3 倍速）压缩，
 * ⇒ 实际时长 = 200 × 0.75 = 150 tick，与 {@code EntityOverrankMagic.LIFETIME} 一致）：
 * <pre>
 *   2  ─ 24   法阵自中心展开、地面泛光亮起
 *   34 ─ 76   蓄力：光柱升起、光墙旋转、符文沿螺旋上腾
 *   110       爆发：光柱暴涨、天光贯下、冲击环外扩
 *   148 ─ 194 余韵消散
 * </pre>
 */
public final class OverrankGeometry {
    private OverrankGeometry() {
    }

    private static final float TAU = (float) Math.PI * 2F;
    private static final float DEG = (float) Math.PI / 180F;

    // ---------------- 时间轴（唯一调控点） ----------------
    // ★ 这里全部是**设计值**（对应 200 tick 的原始节奏）。整体快慢由 {@link #TIME_SCALE} 统一控制，
    //   调整时**不要**动下面这些常量。当前 TIME_SCALE = 4/3 ⇒ 整条特效时长 = 200 × 0.75 = 150 tick。
    /** 法阵展开 */
    public static final float T_DRAW_START = 2F;
    public static final float T_DRAW_DUR = 22F;
    /** 蓄力 */
    public static final float T_CHARGE_START = 34F;
    public static final float T_CHARGE_DUR = 42F;
    /** 爆发（整条特效的高潮帧） */
    public static final float T_BURST = 110F;
    public static final float T_BURST_DUR = 13F;
    /** 消散 */
    public static final float T_FADE_START = 148F;
    public static final float T_FADE_DUR = 46F;

    /**
     * ★ **整体动画速度倍率**（唯一的总开关）= 1 / "持续时间倍率"。
     * <p>当前为 {@code 1/0.75 = 4/3}：整条特效比设计节奏**快 1/4**，总时长压到原来的 75%
     * （200 → 150 tick，见 {@code EntityOverrankMagic.LIFETIME}）。
     * <p>实现方式：外部（渲染器 / 指令）一律传**原始 tick**，进入 {@link #draw}/{@link #drawSolid}/
     * {@link #lifeEnvelope} 时把 age 乘上本系数，之后内部所有 {@code stage(...)} 都按压缩后的时间算 ——
     * 所以时间轴常量与那些散落的局部时长（如展开分层的 14F/16F）会**一起**加速，不用逐处改。
     * <p>想恢复原速就写 1F；想再快就继续放大（例如 1.5F = 快一半）。
     */
    public static final float TIME_SCALE = 1F / 0.75F;

    // ---------------- 配色（蓝紫为主 + 紫罗兰点缀；冷白只留给"最中心一点"） ----------------
    // ★ 2026-09-24：主色由"金（琥珀）"换回蓝紫。判据是三通道的关系 ——
    //   蓝紫"偏蓝"时 G 高于 R；"偏紫"时 R 反超 G（越接近紫罗兰 VR 就越紫）。
    //   当前这版是**在偏蓝的基础上再往紫挪了一档**（R 升 / G 降），与纯紫的 VR 仍留有区分。
    // ★ POSITION_COLOR 通道不吃光照与后处理，颜色就是顶点色：三通道拉开就够鲜艳，叠到地面上也不会发灰。
    //   （变量名 `G*` 沿用历史 —— 它们原本是 Gold 系，现已改为 BlueViolet。）
    private static final float GR = .44F, GG = .38F, GB = 1.00F;     // 主蓝紫（偏紫）
    private static final float GHR = .68F, GHG = .62F, GHB = 1.00F;  // 亮蓝紫
    private static final float GDR = .28F, GDG = .16F, GDB = .92F;   // 暗蓝紫
    private static final float VR = .48F, VG = .10F, VB = 1.00F;     // 紫罗兰
    private static final float VHR = .72F, VHG = .40F, VHB = 1.00F;  // 亮紫
    // 注：原「暗紫」三兄弟（VDR/VDG/VDB）随"紫色十二边形"一起移除 —— 法阵**中心区域**
    //     按要求只留主色三角形（六芒星 + 内心），其余一概不进中心。
    private static final float WR = .85F, WG = .92F, WB = 1.00F;     // 冷白（只在最中心一点；原暖白偏黄，随主色一并转冷）

    /** 升腾符文数量 */
    private static final int SHARD_N = 28;
    /** 贴地放射光带数量 */
    private static final int SPOKE_N = 24;

    /** 暗球（竖直圈数 / 每圈段数） */
    private static final int ORB_RING_FULL = 16, ORB_SEG_FULL = 28;
    private static int ORB_RING = ORB_RING_FULL, ORB_SEG = ORB_SEG_FULL;

    /**
     * 整体尺寸系数 = 当前半径 / 设计基准 8 格。
     *
     * <p>地面法阵的元素半径都写成"倍率 × radius"，天然跟着半径缩放；但空中那些东西
     * （光柱粗细/高度、天光半径、光幕、符文碎片、暗球）是用**绝对格数**写的，
     * 所以这里统一乘一个 K，才能做到"整体放大"而不是"只有地上的圈变大"。
     */
    private static float K = 1F;

    // ---------------- 段数（按距离降档） ----------------
    private static final int RING_FULL = 128, CYL_FULL = 48;
    private static int RING_SEG = RING_FULL, CYL_SEG = CYL_FULL;

    /** 按 {@link FxLod} 档位调整段数；近档保持原值（贴脸看时几何与设计完全一致）。 */
    public static void setDetail(int tier) {
        int p = FxLod.percent(tier);
        RING_SEG = Math.max(24, RING_FULL * p / 100);
        CYL_SEG = Math.max(14, CYL_FULL * p / 100);
        ARC_SEG = Math.max(12, ARC_FULL * p / 100);
        ORB_RING = Math.max(8, ORB_RING_FULL * p / 100);
        ORB_SEG = Math.max(10, ORB_SEG_FULL * p / 100);
    }

    // ---------------- 包络：public 版收**原始 tick**（内部乘 TIME_SCALE）；`*At` 版收**已压缩时间** ----------------
    // ★ 这个分工是 TIME_SCALE 的关键：draw()/drawSolid() 一进来就把 age 压缩好，之后内部一律用 `*At`，
    //   避免二次缩放；而渲染器单独调的 public 方法自己负责压缩。
    /** 爆发闪光包络：T_BURST 处瞬间到 1，随后 13 tick 内衰到 0（爆发前恒为 0）。 */
    public static float burstFlash(float age) {
        return burstFlashAt(age * TIME_SCALE);
    }

    private static float burstFlashAt(float age) {
        if (age < T_BURST) return 0F;
        return 1F - smoother(stage(age, T_BURST, T_BURST_DUR));
    }

    /** 法阵整体展开进度（0→1，缓出），渲染贴图层也要用 */
    public static float drawProgress(float age) {
        return smoother(stage(age * TIME_SCALE, T_DRAW_START, T_DRAW_DUR));
    }

    /** 蓄力进度（0→1） */
    public static float chargeProgress(float age) {
        return smoother(stage(age * TIME_SCALE, T_CHARGE_START, T_CHARGE_DUR));
    }

    /** 整体存活包络（末端淡出） */
    public static float lifeEnvelope(float age) {
        return lifeEnvelopeAt(age * TIME_SCALE);
    }

    private static float lifeEnvelopeAt(float age) {
        return 1F - smoother(stage(age, T_FADE_START, T_FADE_DUR));
    }

    // ==================================================================
    public static void draw(Matrix4f m, VertexConsumer vc, float rawAge, float radius,
                            float viewYaw, float viewPitch) {
        final float age = rawAge * TIME_SCALE;      // ★ 时间压缩只做这一次，之后全用 age
        float life = lifeEnvelopeAt(age);
        if (life <= .01F) return;
        K = radius / 8F;                 // 以设计基准 8 格为 1.0
        float draw = drawProgressAt(age);
        float charge = chargeProgressAt(age);
        float flash = burstFlashAt(age);

        groundArray(m, vc, age, radius, life);
        groundPulses(m, vc, age, radius, charge, life);
        liftRings(m, vc, age, radius, charge, life);
        runeShards(m, vc, age, radius, life);
        burstShock(m, vc, age, radius, life);
        orbFlash(m, vc, age, charge, flash, life, viewYaw, viewPitch);
    }

    private static float drawProgressAt(float age) {
        return smoother(stage(age, T_DRAW_START, T_DRAW_DUR));
    }

    private static float chargeProgressAt(float age) {
        return smoother(stage(age, T_CHARGE_START, T_CHARGE_DUR));
    }

    // ==================================================================
    // 地面法阵 —— **纯代码绘制**（只有曲线与直线，没有贴图）
    // ==================================================================
    // 为什么不用贴图：这一版之前贴了一张 256² 的线稿，问题有三 ——
    //   ① 颜色被 alpha 混合和量化限制住，想调亮调暗要重新生成、重压、重打包；
    //   ② 贴图是"画在地面上的一张图"，线宽固定，离远了细线会闪、离近了又是位图；
    //   ③ 半透明线稿很容易一片发白、发灰。
    // 改成几何后每一根线都能单独给颜色/透明度，粗细按世界尺寸给，远近平滑降档，
    // 而且省掉了两张贴图（jar 里少 22.8 KB）。下面所有半径都是**设计稿的比例**（1.0 = 法阵半径）。
    // ==================================================================

    /** 符文带：24 个符文；每个 3~5 笔，端点落在 3×3 网格上（与设计稿同一套生成规则） */
    private static final int RUNE_N = 24;
    /** 刻度数量（每 8 个加长） */
    private static final int TICK_N = 96;
    private static final float[] LU = {-1F, -1F, -1F, 0F, 0F, 1F, 1F, 1F};   // 3×3 网格 u（径向）
    private static final float[] LV = {-1F, 0F, 1F, -1F, 1F, -1F, 0F, 1F};   // 3×3 网格 v（切向）

    private static void groundArray(Matrix4f m, VertexConsumer vc, float age, float radius, float life) {
        float reveal = drawProgressAt(age);
        if (life <= .01F || reveal <= .01F) return;
        float charge = chargeProgressAt(age);
        float flash = burstFlashAt(age);
        float boost = 1F + charge * .22F + flash * .55F;
        float spinMain = age * (.20F + .34F * charge) * DEG;
        float spinRune = -age * (.52F + .78F * charge) * DEG;
        final float y = .014F;                       // 略高于地面，避开与地面的深度冲突
        // 分层淡入（外→内），配合圆弧"画出来"的展开动画
        float fA = smoother(stage(age, T_DRAW_START, 14F)) * life;
        float fB = smoother(stage(age, T_DRAW_START + 5F, 14F)) * life;
        float fC = smoother(stage(age, T_DRAW_START + 10F, 16F)) * life;
        float fD = smoother(stage(age, T_DRAW_START + 14F, 16F)) * life;
        if (fA <= .01F) return;

        // ---- 1 外框双线 ----
        // ★ 半径比例整体"往外挪"了一点（0.985/0.966 → 0.990/0.973），并且刻度带拉高
        //   （0.876~0.950 → 0.878~0.962），让最外面这几圈的**间隙**变大、不再挤成一坨。
        arc(m, vc, y, radius * .990F, .066F, 0F, reveal, GR, GG, GB, fA * .40F * boost);
        arc(m, vc, y, radius * .973F, .032F, .12F, reveal, GDR, GDG, GDB, fA * .26F * boost);

        // ---- 2 刻度带：96 根，每 8 根加长 ----
        if (fA > .05F) {
            for (int i = 0; i < TICK_N; i++) {
                float a = i * TAU / TICK_N + spinMain;
                boolean longTick = (i % 8 == 0);
                float r0 = radius * (longTick ? .878F : .912F);
                float r1 = radius * (longTick ? .962F : .948F);
                float ca = Mth.cos(a), sa = Mth.sin(a);
                float in0 = smoother(stage(age, T_DRAW_START + 2F + (i % 12) * .5F, 10F));
                if (in0 <= .01F) continue;
                gLine(m, vc, y, ca * r0, sa * r0, ca * r1, sa * r1,
                        longTick ? .026F : .014F,
                        longTick ? GHR : GR, longTick ? GHG : GG, longTick ? GHB : GB,
                        fA * (longTick ? .30F : .20F) * in0 * boost);
            }
        }

        // ---- 3 主环 + 4 24 条短放射线（只在外圈那一圈里，不从圆心射出去）----
        arc(m, vc, y, radius * .878F, .072F, 0F, reveal, GR, GG, GB, fA * .34F * boost);
        if (fB > .01F) {
            for (int i = 0; i < 24; i++) {
                float a = i * TAU / 24F + (float) Math.PI / 48F + spinMain;
                float ca = Mth.cos(a), sa = Mth.sin(a);
                gLine(m, vc, y, ca * radius * .836F, sa * radius * .836F,
                        ca * radius * .884F, sa * radius * .884F, .019F,
                        GDR, GDG, GDB, fB * .22F * boost);
            }
        }

        // ---- 5 中环双线（符文带外边界）----
        arc(m, vc, y, radius * .828F, .066F, 0F, reveal, GR, GG, GB, fB * .32F * boost);
        arc(m, vc, y, radius * .818F, .030F, .1F, reveal, GDR, GDG, GDB, fB * .22F * boost);

        // ---- 6 符文带 ----------------------------------------------
        // ★ 内边界原本是"双线"（0.664 + 0.656），和外圈的几条合并在一起看起来就是好几圈叠着，
        //   按要求**并成一圈**（0.664）。外边界仍是双线，保留设计稿的"双框"感。
        arc(m, vc, y, radius * .664F, .053F, 0F, reveal, GR, GG, GB, fC * .30F * boost);
        if (fC > .02F) {
            for (int i = 0; i < RUNE_N; i++) {
                float aC = i * TAU / RUNE_N + spinRune;
                float ca = Mth.cos(aC), sa = Mth.sin(aC);
                float rc = radius * .746F;                 // 带子中心（外边界往外挪后同步）
                float cx = ca * rc, cz = sa * rc;
                float hu = radius * .0722F, hv = radius * .072F;
                float cr, cg, cb;
                if (i % 3 == 0) { cr = GHR; cg = GHG; cb = GHB; }
                else if (i % 3 == 1) { cr = VHR; cg = VHG; cb = VHB; }
                else { cr = VR; cg = VG; cb = VB; }
                float ai = fC * (.28F + det(i, 9.1F) * .10F) * boost;
                int strokes = 3 + (int) (det(i, 11.3F) * 2.999F);
                for (int k = 0; k < strokes; k++) {
                    int i0 = (int) (det(i, 21.7F + k * 3.1F) * 7.999F);
                    int i1 = (int) (det(i, 41.3F + k * 5.7F) * 7.999F);
                    float u0 = LU[i0] * hu, v0 = LV[i0] * hv;
                    float u1 = LU[i1] * hu, v1 = LV[i1] * hv;
                    gLine(m, vc, y,
                            cx + ca * u0 - sa * v0, cz + sa * u0 + ca * v0,
                            cx + ca * u1 - sa * v1, cz + sa * u1 + ca * v1,
                            .031F, cr, cg, cb, ai);
                }
                if (det(i, 77.7F) < .40F) {
                    float u0 = (det(i, 88.1F) - .5F) * 1.4F * hu;
                    float v0 = (det(i, 99.3F) - .5F) * 1.4F * hv;
                    gDot(m, vc, y, cx + ca * u0 - sa * v0, cz + sa * u0 + ca * v0,
                            .040F, cr, cg, cb, ai * .9F);
                }
            }
        }

        if (fD <= .01F) return;

        // ---- 7 中心：蓝紫六芒星（两个三角形，主色）----
        // ★ 按要求精简中心区域（"中间太乱"）：原设计里的**紫色十二边形**与**两个紫色三角形**
        //   （旋转 30° 的那一对）已整组移除；六芒星只留主色这一对，中心因此干净很多。
        poly(m, vc, y, radius, .558F, 3, (float) Math.PI / 2F, spinMain, GR, GG, GB, fD * .30F * boost);
        poly(m, vc, y, radius, .558F, 3, (float) Math.PI / 2F + (float) Math.PI / 3F, spinMain,
                GR, GG, GB, fD * .30F * boost);

        // ---- 8 内心双环（主色）----
        // ★ 原「12 条短小向外辐射的紫色短线 + 紫色端点 + 紫内环 + 紫八边形」整组移除：
        //   那几笔是"中间太乱"的另一半来源，且都是紫色。
        arc(m, vc, y, radius * .472F, .059F, 0F, reveal, GR, GG, GB, fD * .30F * boost);
        arc(m, vc, y, radius * .458F, .032F, .15F, reveal, GDR, GDG, GDB, fD * .20F * boost);

        // ---- 9 心：圆 + 四芒星 + 一点亮心 ----
        arc(m, vc, y, radius * .140F, .049F, 0F, reveal, GHR, GHG, GHB, fD * .34F * boost);
        star(m, vc, y, radius, .130F, .042F, 4, spinMain, GHR, GHG, GHB, fD * .32F * boost);
        gDot(m, vc, y, 0F, 0F, radius * .040F, GHR, GHG, GHB, fD * .42F * boost);
        gDot(m, vc, y, 0F, 0F, radius * .018F, WR, WG, WB, fD * .50F * boost);
    }

    /** 正多边形（边数 n，圆半径 r，rot 起始角），用贴地直线画 */
    private static void poly(Matrix4f m, VertexConsumer vc, float y, float radius, float r, int n,
                             float rot, float spin, float cr, float cg, float cb, float a) {
        for (int i = 0; i < n; i++) {
            float a0 = rot + i * TAU / n + spin, a1 = rot + (i + 1) * TAU / n + spin;
            float r0 = r * radius;
            gLine(m, vc, y, Mth.cos(a0) * r0, Mth.sin(a0) * r0,
                    Mth.cos(a1) * r0, Mth.sin(a1) * r0, .031F, cr, cg, cb, a);
            gDot(m, vc, y, Mth.cos(a1) * r0, Mth.sin(a1) * r0, .031F, cr, cg, cb, a);
        }
    }

    /** 星形多边形（2n 个顶点，外径 rOut / 内径 rIn） */
    private static void star(Matrix4f m, VertexConsumer vc, float y, float radius,
                             float rOut, float rIn, int n, float spin,
                             float cr, float cg, float cb, float a) {
        for (int i = 0; i < 2 * n; i++) {
            float a0 = spin + i * (float) Math.PI / n, a1 = spin + (i + 1) * (float) Math.PI / n;
            float d0 = (i % 2 == 0 ? rOut : rIn) * radius;
            float d1 = ((i + 1) % 2 == 0 ? rOut : rIn) * radius;
            gLine(m, vc, y, Mth.cos(a0) * d0, Mth.sin(a0) * d0,
                    Mth.cos(a1) * d1, Mth.sin(a1) * d1, .031F, cr, cg, cb, a);
            // ★ 每个折点补一个圆点：两条边的软端在这里相接，不补点的话尖角处会看着"缺一截"。
            gDot(m, vc, y, Mth.cos(a1) * d1, Mth.sin(a1) * d1, .031F, cr, cg, cb, a);
        }
    }

    // ---------------- 蓄力期的心跳脉冲：地面环反复向外扩 ----------------
    // ★ 起点定在**半径 75%** 处（0.22R → 0.60R → 0.75R，2026-09-24 再次外移）：
    //   扩散环完全落在法阵外圈一带，中心区域始终留给六芒星与内心，不再"从圆心冒出来"。
    private static final float PULSE_R0 = .75F;
    /** 蓄力扩散环终点（占半径比例）—— 起点外移后同步外推，保持"推出阵外"的行程 */
    private static final float PULSE_R1 = 1.29F;
    /** 爆发冲击环终点（占半径比例）—— 比蓄力环再远一点 */
    private static final float SHOCK_R1 = 1.44F;

    private static void groundPulses(Matrix4f m, VertexConsumer vc, float age, float radius,
                                     float charge, float life) {
        if (age < T_CHARGE_START) return;
        float on = smoother(stage(age, T_CHARGE_START, 16F)) * life * (.35F + charge * .65F);
        if (on <= .01F) return;
        final float cyc = 24F;
        for (int i = 0; i < 3; i++) {
            float t = ((age - T_CHARGE_START + i * cyc / 3F) % cyc) / cyc;
            float r = radius * (PULSE_R0 + (PULSE_R1 - PULSE_R0) * fastOut(t));
            float a = on * (1F - t) * (1F - t) * .60F;
            ring(m, vc, .074F, r, .09F + .07F * t, VR, VG, VB, a);
        }
    }

    // ---------------- 蓄力：一圈圈从地面浮起的环 ----------------
    private static void liftRings(Matrix4f m, VertexConsumer vc, float age, float radius,
                                  float charge, float life) {
        if (age < T_CHARGE_START) return;
        for (int i = 0; i < 5; i++) {
            float st = T_CHARGE_START + i * 11F;
            float t = stage(age, st, 36F);
            if (t <= 0F || t >= 1F) continue;
            float env = edge(t) * life;
            if (env <= .01F) continue;
            float y = lerp(fastOut(Mth.clamp(t * 1.7F, 0F, 1F)), .18F, 7.4F * K);
            float r = lerp(smoother(t), radius * .88F, radius * (.50F - i * .02F));
            ring(m, vc, y, r, .10F + charge * .05F, i % 2 == 0 ? VR : GR,
                    i % 2 == 0 ? VG : GG, i % 2 == 0 ? VB : GB, env * .40F);
        }
    }

    // ---------------- 升腾符文碎片（螺旋上升 + 自转） ----------------
    private static void runeShards(Matrix4f m, VertexConsumer vc, float age, float radius, float life) {
        float on = smoother(stage(age, T_CHARGE_START - 6F, 18F)) * life;
        if (on <= .01F) return;
        for (int i = 0; i < SHARD_N; i++) {
            float speed = .010F + det(i, 2.3F) * .009F;
            float t = (age * speed + det(i, 1.7F)) % 1F;
            float y = (.35F + t * 10.4F) * K;
            float rr = radius * (.86F - .50F * t) + Mth.sin(age * .05F + i) * .22F * K;
            float ang = age * (.55F + det(i, 3.1F) * .80F) * DEG + det(i, 4.2F) * TAU;
            float size = (.10F + det(i, 5.3F) * .10F) * K;
            float a = on * (float) Math.sin(Math.PI * t) * (.50F + det(i, 6.1F) * .50F);
            int k = i % 6;
            float cr = k == 0 ? GHR : (k < 3 ? VHR : GR);
            float cg = k == 0 ? GHG : (k < 3 ? VHG : GG);
            float cb = k == 0 ? GHB : (k < 3 ? VHB : GB);
            shard(m, vc, Mth.cos(ang) * rr, y, Mth.sin(ang) * rr, size, age * .13F + i,
                    cr, cg, cb, a);
        }
    }

    // ---------------- 爆发：两圈向外扩张的贴地冲击环 ----------------
    // ★ 起点与蓄力脉冲一致（{@link #PULSE_R0} = 半径 75%），终点 {@link #SHOCK_R1} 再远一点。
    private static void burstShock(Matrix4f m, VertexConsumer vc, float age, float radius, float life) {
        if (age < T_BURST) return;
        for (int i = 0; i < 2; i++) {
            float t = stage(age, T_BURST + i * 5F, 30F);
            if (t <= 0F || t >= 1F) continue;
            float a = (1F - t) * (1F - t) * .70F * life;
            ring(m, vc, .10F + i * .05F + t * .10F,
                    radius * (PULSE_R0 + (SHOCK_R1 - PULSE_R0) * fastOut(t)),
                    .28F - t * .18F, i == 0 ? GHR : GR, i == 0 ? GHG : GG, i == 0 ? GHB : GB, a);
        }
    }

    // ---------------- 空中暗球：加法通道里只留"爆发一瞬"的闪光 ----------------
    // 球体本身走 drawSolid() 的半透明通道 —— 因为它**不是光**，是一颗实心球：
    // 用加法去画就会变成一团半透明的亮光（以前那版就是这样，用户反馈"太亮、发白、有透明感"）。
    // 这里只保留爆发那十几 tick 的一闪，不做常驻发光。
    private static void orbFlash(Matrix4f m, VertexConsumer vc, float age, float charge,
                                 float flash, float life, float viewYaw, float viewPitch) {
        if (flash <= .02F) return;
        float on = smoother(stage(age, T_CHARGE_START + 12F, 20F)) * life;
        if (on <= .01F) return;
        float y = (1.5F + charge * 3.0F) * K;
        float rad = (.62F + charge * .34F) * K;
        flare(m, vc, 0F, y, 0F, rad * 1.5F, VR, VG, VB, on * flash * .22F, viewYaw, viewPitch);
    }

    // ==================================================================
    // 第二趟：实体（半透明混合，**不是加法**）
    // ==================================================================
    /**
     * 实体通道的 RenderType：半透明混合 + 写深度。
     * 写深度是必要的 —— 球体自己会自我遮挡，靠深度测试才能把它画成一颗实心球而不是一团糊。
     */
    public static final RenderType SOLID = RState.composite(
            "skydeityslash:overrank_solid",
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

    /**
     * 只画"实体"部分（目前是空中那颗暗球）。
     * ★ 必须在 {@link #draw} **之后**、同一帧用同一个 radius 调用 —— 这样球才会盖在光柱之上，
     * 而不是被后面的加法光重新照亮。
     */
    public static void drawSolid(Matrix4f m, VertexConsumer vc, float rawAge, float radius) {
        final float age = rawAge * TIME_SCALE;      // 与 draw() 同一约定：压缩只做一次
        float life = lifeEnvelopeAt(age);
        if (life <= .01F) return;
        float on = smoother(stage(age, T_CHARGE_START + 12F, 20F));
        if (on <= .01F) return;
        float charge = chargeProgressAt(age);
        orb(m, vc, (1.5F + charge * 3.0F) * K, (.62F + charge * .34F) * K, on * life);
    }

    /**
     * 实心暗球（UV 球）：按竖直高度做明暗渐变（上浅下深），横向再叠一点固定方向的"假光照"，
     * 所以它有体积感而不是一块平板。
     *
     * <p>颜色刻意压得很深（最亮处也只有 0.25 左右）、alpha 接近不透明 —— 要的就是"一颗深色的球"，
     * 而不是"一团发亮的光"。POSITION_COLOR 通道不走光照，所以明暗必须烘进顶点色里。
     */
    private static void orb(Matrix4f m, VertexConsumer vc, float cy, float rad, float a) {
        for (int i = 0; i < ORB_RING; i++) {
            float p0 = -1.5708F + (float) Math.PI * i / ORB_RING;
            float p1 = -1.5708F + (float) Math.PI * (i + 1) / ORB_RING;
            float cp0 = Mth.cos(p0), sp0 = Mth.sin(p0), cp1 = Mth.cos(p1), sp1 = Mth.sin(p1);
            float h = ((sp0 + sp1) * .5F) * .5F + .5F;          // 0 底 → 1 顶
            for (int j = 0; j < ORB_SEG; j++) {
                float t0 = TAU * j / ORB_SEG, t1 = TAU * (j + 1) / ORB_SEG;
                float ct0 = Mth.cos(t0), st0 = Mth.sin(t0), ct1 = Mth.cos(t1), st1 = Mth.sin(t1);
                float lit = .74F + .26F * (ct0 * .5F + st0 * .86F);
                float r = (.055F + h * .195F) * lit;
                float g = (.035F + h * .125F) * lit;
                float b = (.145F + h * .315F) * lit;
                quadQ(m, vc,
                        cp0 * ct0 * rad, cy + sp0 * rad, cp0 * st0 * rad,
                        cp1 * ct0 * rad, cy + sp1 * rad, cp1 * st0 * rad,
                        cp1 * ct1 * rad, cy + sp1 * rad, cp1 * st1 * rad,
                        cp0 * ct1 * rad, cy + sp0 * rad, cp0 * st1 * rad,
                        r, g, b, a);
            }
        }
    }

    // ==================================================================
    // 图元
    // ==================================================================
    private static void vv(Matrix4f m, VertexConsumer vc, float x, float y, float z,
                           float r, float g, float b, float a) {
        vc.vertex(m, x, y, z)
                .color((int) (Mth.clamp(r, 0, 1) * 255), (int) (Mth.clamp(g, 0, 1) * 255),
                        (int) (Mth.clamp(b, 0, 1) * 255), (int) (Mth.clamp(a, 0, 1) * 255))
                .endVertex();
    }

    private static void quadQ(Matrix4f m, VertexConsumer vc,
                              float x0, float y0, float z0, float x1, float y1, float z1,
                              float x2, float y2, float z2, float x3, float y3, float z3,
                              float r, float g, float b, float a) {
        vv(m, vc, x0, y0, z0, r, g, b, a);
        vv(m, vc, x1, y1, z1, r, g, b, a);
        vv(m, vc, x2, y2, z2, r, g, b, a);
        vv(m, vc, x0, y0, z0, r, g, b, a);
        vv(m, vc, x2, y2, z2, r, g, b, a);
        vv(m, vc, x3, y3, z3, r, g, b, a);
    }

    private static void quadA(Matrix4f m, VertexConsumer vc,
                              float x0, float y0, float z0, float x1, float y1, float z1,
                              float x2, float y2, float z2, float x3, float y3, float z3,
                              float r, float g, float b,
                              float a0, float a1, float a2, float a3) {
        vv(m, vc, x0, y0, z0, r, g, b, a0);
        vv(m, vc, x1, y1, z1, r, g, b, a1);
        vv(m, vc, x2, y2, z2, r, g, b, a2);
        vv(m, vc, x0, y0, z0, r, g, b, a0);
        vv(m, vc, x2, y2, z2, r, g, b, a2);
        vv(m, vc, x3, y3, z3, r, g, b, a3);
    }

    /** 水平圆环（径向 alpha 渐变：中心亮、内外边缘淡出，所以不会有硬边） */
    private static void ring(Matrix4f m, VertexConsumer vc, float y, float radius, float width,
                             float r, float g, float b, float a) {
        if (a <= .004F || radius <= .03F) return;
        float inner = Math.max(.02F, radius - width * .5F), mid = radius, outer = radius + width * .5F;
        final float edgeA = .12F, coreA = .95F;
        for (int i = 0; i < RING_SEG; i++) {
            float a0 = i * TAU / RING_SEG, a1 = (i + 1) * TAU / RING_SEG;
            float ca = Mth.cos(a0), sa = Mth.sin(a0), cb = Mth.cos(a1), sb = Mth.sin(a1);
            quadA(m, vc, ca * outer, y, sa * outer, cb * outer, y, sb * outer,
                    cb * mid, y, sb * mid, ca * mid, y, sa * mid,
                    r, g, b, a * edgeA, a * edgeA, a * coreA, a * coreA);
            quadA(m, vc, ca * mid, y, sa * mid, cb * mid, y, sb * mid,
                    cb * inner, y, sb * inner, ca * inner, y, sa * inner,
                    r, g, b, a * coreA, a * coreA, a * edgeA, a * edgeA);
        }
    }

    // ---------------- 贴地线稿的三个图元（曲线 / 直线 / 点） ----------------
    /** 圆弧段数（整圈）。线很细，段长与线宽同量级就够，不需要 RING_FULL 那么密。 */
    private static final int ARC_FULL = 120;
    private static int ARC_SEG = ARC_FULL;
    /** 点的扇形段数 */
    private static final int DOT_FULL = 10;

    /**
     * 贴地圆弧：半径 radius、线宽 width，从 aStart 起画 reveal 比例（0~1）的一圈 ——
     * 展开动画就是靠它"把阵画出来"。横向 alpha 中间满、内外边缘淡出，所以没有硬边。
     */
    private static void arc(Matrix4f m, VertexConsumer vc, float y, float radius, float width,
                            float aStart, float reveal, float r, float g, float b, float a) {
        if (a <= .004F || radius <= .03F || reveal <= .01F) return;
        int seg = Math.max(3, (int) Math.ceil(ARC_SEG * reveal));
        float span = TAU * reveal;
        float inner = Math.max(.02F, radius - width * .5F), mid = radius, outer = radius + width * .5F;
        final float edgeA = .10F, coreA = 1F;
        for (int i = 0; i < seg; i++) {
            float t0 = aStart + i * span / seg, t1 = aStart + (i + 1) * span / seg;
            // 收尾 2 段渐隐，免得正在"画"的那一端是个生硬的切口。
            // ★ 但整圈画满（reveal=1）时**必须停止渐隐** —— 否则每个闭合圆上都会缺一小段暗弧，
            //   看上去就是"圆环的线断开了"（之前用户反馈的断线就是这个）。
            float fade = (reveal < .999F && i >= seg - 2) ? (seg - i - 1) / 2F : 1F;
            float aa = a * fade;
            float ca = Mth.cos(t0), sa = Mth.sin(t0), cb = Mth.cos(t1), sb = Mth.sin(t1);
            quadA(m, vc, ca * outer, y, sa * outer, cb * outer, y, sb * outer,
                    cb * mid, y, sb * mid, ca * mid, y, sa * mid,
                    r, g, b, aa * edgeA, aa * edgeA, aa * coreA, aa * coreA);
            quadA(m, vc, ca * mid, y, sa * mid, cb * mid, y, sb * mid,
                    cb * inner, y, sb * inner, ca * inner, y, sa * inner,
                    r, g, b, aa * coreA, aa * coreA, aa * edgeA, aa * edgeA);
        }
    }

    /** 贴地直线段（横向中间满、两侧 0）。所有直边、刻度、符文笔画都走它。 */
    private static void gLine(Matrix4f m, VertexConsumer vc, float y,
                              float x0, float z0, float x1, float z1, float halfW,
                              float r, float g, float b, float a) {
        if (a <= .004F) return;
        float dx = x1 - x0, dz = z1 - z0;
        float len = Mth.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-5F) return;
        float nx = -dz / len * halfW, nz = dx / len * halfW;
        quadA(m, vc, x0 - nx, y, z0 - nz, x0, y, z0, x1, y, z1, x1 - nx, y, z1 - nz,
                r, g, b, 0F, a, a, 0F);
        quadA(m, vc, x0, y, z0, x0 + nx, y, z0 + nz, x1 + nx, y, z1 + nz, x1, y, z1,
                r, g, b, a, 0F, 0F, a);
    }

    /** 贴地圆点（中心满 → 边缘 0），用于端点 / 关节补圆 / 中心亮点 */
    private static void gDot(Matrix4f m, VertexConsumer vc, float y, float x, float z,
                             float radius, float r, float g, float b, float a) {
        if (a <= .004F || radius <= .004F) return;
        for (int i = 0; i < DOT_FULL; i++) {
            float t0 = i * TAU / DOT_FULL, t1 = (i + 1) * TAU / DOT_FULL;
            vv(m, vc, x, y, z, r, g, b, a);
            vv(m, vc, x + Mth.cos(t0) * radius, y, z + Mth.sin(t0) * radius, r, g, b, 0F);
            vv(m, vc, x + Mth.cos(t1) * radius, y, z + Mth.sin(t1) * radius, r, g, b, 0F);
        }
    }

    /** 交叉双片竖菱形 —— 符文碎片（两面都画，加法混合下像个会转的小晶体） */
    private static void shard(Matrix4f m, VertexConsumer vc, float x, float y, float z,
                              float size, float spin, float r, float g, float b, float a) {
        if (a <= .004F) return;
        float h = size, w = size * .42F;
        for (int k = 0; k < 2; k++) {
            float ang = spin + k * (float) Math.PI * .5F;
            float ca = Mth.cos(ang) * w, sa = Mth.sin(ang) * w;
            quadQ(m, vc,
                    x, y + h, z,
                    x + ca, y, z + sa,
                    x, y - h, z,
                    x - ca, y, z - sa,
                    r, g, b, a);
        }
    }

    /** 相机朝向的发光圆盘（核心） */
    private static void flare(Matrix4f m, VertexConsumer vc, float x, float y, float z,
                              float radius, float r, float g, float b, float a,
                              float viewYaw, float viewPitch) {
        if (a <= .004F) return;
        float dy = viewYaw * DEG, dp = viewPitch * DEG;
        float sy = Mth.sin(dy), cy = Mth.cos(dy), sp = Mth.sin(dp), cp = Mth.cos(dp);
        // 相机右向量 / 上向量（与 GlowGeometry.glowBall 同一套约定）
        float rx = -cy, ry = 0F, rz = -sy;
        float ux = -sy * sp, uy = cp, uz = cy * sp;
        for (int i = 0; i < CYL_SEG; i++) {
            float t0 = i * TAU / CYL_SEG, t1 = (i + 1) * TAU / CYL_SEG;
            float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
            vv(m, vc, x, y, z, r, g, b, a);
            vv(m, vc, x + (rx * c0 + ux * s0) * radius, y + (ry * c0 + uy * s0) * radius,
                    z + (rz * c0 + uz * s0) * radius, r, g, b, 0F);
            vv(m, vc, x + (rx * c1 + ux * s1) * radius, y + (ry * c1 + uy * s1) * radius,
                    z + (rz * c1 + uz * s1) * radius, r, g, b, 0F);
        }
    }

    // ---------------- 小工具 ----------------
    private static float stage(float age, float start, float dur) {
        return Mth.clamp((age - start) / dur, 0F, 1F);
    }

    private static float smoother(float t) {
        t = Mth.clamp(t, 0F, 1F);
        return t * t * t * (t * (t * 6F - 15F) + 10F);
    }

    private static float fastOut(float t) {
        t = Mth.clamp(t, 0F, 1F);
        float i = 1F - t;
        return 1F - i * i * i * i;
    }

    /** 两端为 0、中间为 1 的包络 */
    private static float edge(float t) {
        return smoother(Mth.clamp(t / .18F, 0F, 1F))
                * (1F - smoother(Mth.clamp((t - .72F) / .28F, 0F, 1F)));
    }

    private static float lerp(float t, float a, float b) {
        return a + (b - a) * t;
    }

    private static float det(int i, float salt) {
        float v = Mth.sin(i * 12.9898F + salt * 78.233F) * 43758.547F;
        return v - (float) Math.floor(v);
    }
}
