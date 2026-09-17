package com.example.skydeityslash.client;

import com.example.skydeityslash.entity.EntityCutLines;
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
 * 「吻痕窥梦梦魇生花」的**横切割刀痕**渲染器 —— 几道**横着的细红圆柱**依次划过目标，像被切了几刀。
 *
 * 每道刀痕是一根**六棱柱**（和 SA 摆线同一套做法，不是粒子、不是朝向相机的平面带子）：
 *  · 位置/方位/长度/滑动量都由**固定参数表**给出（不随机，每次触发一模一样）；
 *  · 每道错开 {@link #STAGGER} tick 起步，从**中心向两端长出**（像刀锋推过去），
 *    同时沿自身横向滑一小段 —— 合起来就是"切过去"的感觉；
 *  · 末尾整体淡出。
 *
 * 渲染类型：POSITION_COLOR + 普通半透明（红色用半透明；加法混合会发闷）。
 */
public class RenderCutLines extends EntityRenderer<EntityCutLines> {

    private static final ResourceLocation NONE = new ResourceLocation("skydeityslash", "cut_lines");
    private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);

    private static final RenderType CUT_TYPE = RState.composite(
            "skydeityslash:cut_lines",
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

    // ---------------------------------------------------------------- 固定布局表（不随机）
    /** 每道刀痕的方位（度）—— 故意不等分，看着才像乱刀 */
    private static final double[] AZIMUTH_DEG = {17.0, 112.0, 66.0, 203.0, 268.0, 331.0};
    /**
     * 每道刀痕的**俯仰倾角（度）**—— 相对水平面，正 = 一端翘起、负 = 一端垂落。
     * 各道正负/大小都不同 ⇒ 六刀不再是"一排平行线"，从各个角度看都是乱刀。
     */
    private static final double[] PITCH_DEG = {6.0, -12.0, 14.0, -5.0, 10.0, -15.0};
    /** 每道刀痕的高度偏移（格）—— 中心附近**聚拢**（相邻 0.18 格） */
    private static final double[] Y_OFF = {-0.45, -0.27, -0.09, 0.09, 0.27, 0.45};
    /** 每道刀痕的长度（格）—— **更长**，且长短差得大（才有凌乱感） */
    private static final double[] LENGTH = {6.5, 2.6, 5.5, 3.1, 6.1, 2.7};
    /** 每道刀痕沿自身横向的滑动量（格）—— 正向/反向交替，做出"切过去"的位移（也各不同） */
    private static final double[] SLIDE = {0.32, -0.22, 0.38, -0.30, 0.25, -0.36};

    private static final int LINES = AZIMUTH_DEG.length;

    // ---------------------------------------------------------------- 观感参数
    /** 圆柱半径（格）—— **更细**（直径 1.6 厘米） */
    private static final float RADIUS = 0.008f;
    /** 棱数（6 边已经够圆） */
    private static final int SIDES = 6;
    /** 每根圆柱沿长度分几段（直线，够用即可） */
    private static final int SEGMENTS = 4;
    /** 单道刀痕从中心长到两端所需 tick */
    private static final float GROW_TICKS = 2.5f;
    /** 相邻两道的起步间隔（tick） */
    private static final float STAGGER = 1.2f;
    /** 末尾淡出 tick */
    private static final float FADE_TICKS = 4.0f;
    /** 刀痕颜色：纯红（比主线亮一档，才看得出是"切割"） */
    private static final int RED = 0xC51C24;

    public RenderCutLines(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(EntityCutLines entity) {
        return NONE;
    }

    @Override
    public void render(EntityCutLines entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        float age = entity.tickCount + partial;
        float life = EntityCutLines.LIFETIME;
        if (age > life) return;
        float fade = age > life - FADE_TICKS
                ? Mth.clamp((life - age) / FADE_TICKS, 0f, 1f) : 1f;
        if (fade <= 0.01f) return;
        fade = smooth(fade);

        VertexConsumer vc = buffer.getBuffer(CUT_TYPE);
        Matrix4f m = pose.last().pose();

        for (int i = 0; i < LINES; i++) {
            float local = age - i * STAGGER;
            if (local <= 0f) continue;                        // 还没轮到这道
            float grow = smooth(Mth.clamp(local / GROW_TICKS, 0f, 1f));
            double ang = Math.toRadians(AZIMUTH_DEG[i]);
            double pitch = Math.toRadians(PITCH_DEG[i]);
            // 水平分量（方位角）+ 竖直翘起（俯仰角）⇒ 每道刀痕的"倾斜"都不一样
            Vec3 flat = new Vec3(Math.cos(ang), 0.0, Math.sin(ang));
            Vec3 dir = flat.scale(Math.cos(pitch)).add(0.0, Math.sin(pitch), 0.0).normalize();
            // 滑动方向：垂直于刀痕、且尽量水平（翘起来的那侧）
            Vec3 side = dir.cross(WORLD_UP);
            side = side.lengthSqr() < 1.0E-8 ? new Vec3(-flat.z, 0.0, flat.x) : side.normalize();
            Vec3 center = new Vec3(0.0, Y_OFF[i], 0.0).add(side.scale(SLIDE[i] * grow));
            double half = LENGTH[i] * 0.5 * grow;
            drawRod(vc, m, center.subtract(dir.scale(half)), center.add(dir.scale(half)), fade);
        }
    }

    /** 画一根细圆柱（六棱柱） */
    private static void drawRod(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float alpha) {
        Vec3 axis = b.subtract(a);
        if (axis.lengthSqr() < 1.0E-8) return;
        axis = axis.normalize();
        Vec3 u = axis.cross(WORLD_UP);
        if (u.lengthSqr() < 1.0E-6) u = axis.cross(new Vec3(1, 0, 0));
        u = u.normalize();
        Vec3 v = axis.cross(u);

        double step = Math.PI * 2.0 / SIDES;
        for (int k = 0; k < SEGMENTS; k++) {
            Vec3 ca = a.add(b.subtract(a).scale(k / (float) SEGMENTS));
            Vec3 cb = a.add(b.subtract(a).scale((k + 1) / (float) SEGMENTS));
            for (int s = 0; s < SIDES; s++) {
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

    private static void ringVtx(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 u, Vec3 v,
                                double ang, float alpha) {
        double cs = Math.cos(ang) * RADIUS;
        double sn = Math.sin(ang) * RADIUS;
        float x = (float) (c.x + u.x * cs + v.x * sn);
        float y = (float) (c.y + u.y * cs + v.y * sn);
        float z = (float) (c.z + u.z * cs + v.z * sn);
        vc.vertex(m, x, y, z)
                .color((RED >> 16) & 0xFF, (RED >> 8) & 0xFF, RED & 0xFF,
                        Mth.clamp((int) (alpha * 255.0f), 0, 255))
                .endVertex();
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
