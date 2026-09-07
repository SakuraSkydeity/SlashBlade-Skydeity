package com.example.skydeityslash.client;

import com.example.skydeityslash.entity.EntitySevenThunders21;
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
 * 鸣雷神 特效渲染器 —— 五材质分层忠实重建（来自 1.20KaBlade RenderSevenThunders NarukamiDivinity）。
 * 依原版顺序依次叠加 COMPOSITE / ENERGY / LIGHTNING / CROSS / PARTICLE 五通道，
 * 全用 POSITION_COLOR 加法混合（源码本就主要靠颜色+alpha 决定光影，无需贴图）。
 */
public class RenderSevenThunders21 extends EntityRenderer<EntitySevenThunders21> {
    private static final ResourceLocation NONE = new ResourceLocation("skydeityslash", "seven_thunders");
    private static final int LIFETIME = EntitySevenThunders21.LIFETIME;

    public RenderSevenThunders21(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override
    public ResourceLocation getTextureLocation(EntitySevenThunders21 e) { return NONE; }

    @Override
    public void render(EntitySevenThunders21 entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        float age = Mth.clamp(entity.tickCount + partial, 0, LIFETIME);
        float frame = age * 40.0F / 54.0F; // 原版 F0-F40 时间轴
        Matrix4f m = pose.last().pose();
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();

        Vec3 entityWorld = entity.getPosition(partial);
        Vec3 owner = entity.getOwnerAnchor(partial).subtract(entityWorld);
        Vec3 target = entity.getTargetAnchor(partial).subtract(entityWorld);
        Vec3 camera = cam.getPosition().subtract(entityWorld);
        Vec3 direction = entity.getThunderDirection();
        NarukamiRenderLayer21.Basis basis = NarukamiRenderLayer21.basis(direction);
        long seed = entity.getSeed();

        drawPass(m, buffer.getBuffer(GlowGeometry.GLOW), frame, owner, target, basis, camera, seed, NarukamiRenderLayer21.Material.COMPOSITE);
        drawPass(m, buffer.getBuffer(GlowGeometry.GLOW), frame, owner, target, basis, camera, seed, NarukamiRenderLayer21.Material.ENERGY);
        drawPass(m, buffer.getBuffer(GlowGeometry.GLOW), frame, owner, target, basis, camera, seed, NarukamiRenderLayer21.Material.LIGHTNING);
        drawPass(m, buffer.getBuffer(GlowGeometry.GLOW), frame, owner, target, basis, camera, seed, NarukamiRenderLayer21.Material.CROSS);
        drawPass(m, buffer.getBuffer(GlowGeometry.GLOW), frame, owner, target, basis, camera, seed, NarukamiRenderLayer21.Material.PARTICLE);

        super.render(entity, yaw, partial, pose, buffer, light);
    }

    private void drawPass(Matrix4f m, VertexConsumer b, float frame, Vec3 owner, Vec3 target,
                          NarukamiRenderLayer21.Basis basis, Vec3 camera, long seed,
                          NarukamiRenderLayer21.Material material) {
        NarukamiRenderLayer21.render(m, b, material, frame, owner, target, basis, camera, seed);
    }
}