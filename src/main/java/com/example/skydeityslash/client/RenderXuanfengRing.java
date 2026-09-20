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
 * 「翾风回雪」白色圆环渲染器 —— 环绕锁定目标/落点绘制纯白动态圆环。
 * 使用 GlowGeometry.GLOW（POSITION_COLOR·加法混合·无贴图），白色三段轨道弧环 + 旋转 ribbon 环。
 */
public class RenderXuanfengRing extends EntityRenderer<EntityXuanfengRing> {
    private static final ResourceLocation NONE = new ResourceLocation("skydeityslash", "xuanfeng_ring");
    private static final int LIFETIME = EntityXuanfengRing.LIFETIME;

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

        // 新增：竖弧（preview enlight 3 竖直圆弧），时间轴用完整 64 tick 便于完整展开
        // 该几何组各条竖弧在局部坐标下中心约在 (≈0, 2, 2.92)，整体偏前；这里把渲染矩阵平移到落点正上方以对齐圆环
        float arcAge = Mth.clamp(entity.tickCount + partial, 0, 64);
        Matrix4f arcM = new Matrix4f(m).translate(0, 0, -2.92F);
        GlowGeometry.renderSubEffect(arcM, buffer.getBuffer(GlowGeometry.GLOW), 3, arcAge,
                cam.getYRot(), cam.getXRot());

        super.render(entity, yaw, partial, pose, buffer, light);
    }
}