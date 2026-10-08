package com.example.skydeityslash.client;

import com.example.skydeityslash.entity.EntityChikuiFlower;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 「赤葵」第二种 SA 的花朵渲染器 —— 纯几何（{@link ChikuiFlowerGeometry}），加法混合，无贴图。
 *
 * <p>**花面正对相机**（公告板）：取 `相机 − 实体` 作平面法线 n，
 * 平面内基 `u = n × 世界UP`（水平）、`v = u × n`（竖直），再把这两个基交给几何类。
 * 竖直方向特意用 `u × n` 而不是 `n × u` —— 后者得到的是"世界向下"，会让花上下颠倒。
 *
 * <p>走 {@link GlowGeometry#GLOW}（POSITION_COLOR + 加法混合 + 不写深度），
 * 和「剑体始觉 / 鸣雷神」同一套管线。
 */
public class RenderChikuiFlower extends EntityRenderer<EntityChikuiFlower> {

    private static final ResourceLocation NONE =
            new ResourceLocation("skydeityslash", "chikui_flower");
    private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);

    public RenderChikuiFlower(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(EntityChikuiFlower entity) {
        return NONE;
    }

    @Override
    public void render(EntityChikuiFlower entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        float age = Mth.clamp(entity.tickCount + partial, 0f, EntityChikuiFlower.LIFETIME);

        // 距离降档：远处减段数（近处保持原值）
        ChikuiFlowerGeometry.setDetail(FxLod.tier(entity, entityRenderDispatcher.camera));

        // 公告板平面基：n 朝相机、u 水平、v 竖直
        Vec3 camLocal = this.entityRenderDispatcher.camera.getPosition()
                .subtract(entity.getX(), entity.getY(), entity.getZ());
        Vec3 n = camLocal.lengthSqr() < 1.0E-8 ? new Vec3(0, 0, 1) : camLocal.normalize();
        Vec3 u = n.cross(WORLD_UP);
        if (u.lengthSqr() < 1.0E-6) u = n.cross(new Vec3(1, 0, 0));
        u = u.normalize();
        Vec3 v = u.cross(n);

        VertexConsumer vc = buffer.getBuffer(GlowGeometry.GLOW);
        Matrix4f m = pose.last().pose();
        ChikuiFlowerGeometry.draw(m, vc, age, u, v);

        super.render(entity, yaw, partial, pose, buffer, light);
    }
}
