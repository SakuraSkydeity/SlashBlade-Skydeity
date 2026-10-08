package com.example.skydeityslash.client;

import com.example.skydeityslash.entity.EntityXuanfengRing;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 「翾风回雪」圆环渲染器 —— 环绕锁定目标/落点绘制**淡蓝渐变**的动态圆环。
 * 使用 GlowGeometry.GLOW（POSITION_COLOR·加法混合·无贴图）：三段轨道弧环 + 四层倾斜悬浮环（交错同心）
 * + 一组**居中版竖弧**（enlight 的竖直圆弧，换成淡蓝并与圆环同心）。
 */
public class RenderXuanfengRing extends EntityRenderer<EntityXuanfengRing> {
    private static final ResourceLocation NONE = new ResourceLocation("skydeityslash", "xuanfeng_ring");
    private static final int LIFETIME = EntityXuanfengRing.LIFETIME;

    /**
     * 竖弧组摆放高度（格，相对实体原点）—— 与悬浮环同心。
     * ★ 原来这里是 {@code translate(0, 0, -2.92F)} 硬补 Z：原表的 cy 均值≈1.98、每条弧的 cx/cz 又各不相同，
     * 一条平移补不齐六条 ⇒ 那几条弧看着"偏位"。现在几何侧自带"减组均值"（{@code verticalArcsCentered}），
     * 这里只负责摆高。
     */
    private static final float ARC_CENTER_Y = 1.35F;
    /** 竖弧组缩放（1 = 原尺寸；收到 0.85 与圆环尺度更协调） */
    private static final float ARC_SCALE = .85F;
    /**
     * 竖弧的淡蓝配色：{带色 r,g,b, 芯色 r,g,b} —— 与圆环是**同一套色**（带的蓝 = 圆环 {@code CORE}`.18/.48/1.00`，
     * 芯的淡蓝 = 圆环 {@code EDGE}`.46/.74/1.00`）。
     * ★ 2026-09-28：两端都换成蓝（原来芯色是近白 `.80/.93/1.00`，加法混合下就是一圈发白的镶边）。
     */
    private static final float[] ARC_RGB = {.18F, .48F, 1.00F, .46F, .74F, 1.00F};

    public RenderXuanfengRing(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override
    public ResourceLocation getTextureLocation(EntityXuanfengRing e) { return NONE; }

    @Override
    public void render(EntityXuanfengRing entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        float frame = Mth.clamp(entity.tickCount + partial, 0, LIFETIME);
        Matrix4f m = pose.last().pose();
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();

        Vec3 entityWorld = entity.getPosition(partial);
        Vec3 center = new Vec3(0, 0, 0); // 实体自身即落点，圆环相对其为中心
        Vec3 camera = cam.getPosition().subtract(entityWorld);
        OdetteRingGeometry.Basis basis = OdetteRingGeometry.basis(entity.getRingDirection());
        long seed = entity.getSeed();

        // 环类几何每帧现算，段数按距离降档：远处同一个环只占几十像素，段数减半看不出。
        GlowGeometry.setDetail(FxLod.tier(entity, cam));

        OdetteRingGeometry.renderRings(m, buffer.getBuffer(GlowGeometry.GLOW), frame, center, basis, camera, seed);

        // 竖弧（enlight 的"竖直圆弧"）—— 用**居中版**几何：每条弧自带绕 Y 的自转角 ⇒ 六个弧面互相穿插
        // （原来是一叠互相平行的面、只差一点 z，看着就是"最后画出来的几个偏位"）。
        float arcAge = Mth.clamp(entity.tickCount + partial, 0, LIFETIME);
        Matrix4f arcM = new Matrix4f(m).translate(0, ARC_CENTER_Y, 0);
        GlowGeometry.verticalArcsCentered(arcM, buffer.getBuffer(GlowGeometry.GLOW), arcAge, ARC_RGB, ARC_SCALE);

        super.render(entity, yaw, partial, pose, buffer, light);
    }
}