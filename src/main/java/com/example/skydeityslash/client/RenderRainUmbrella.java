package com.example.skydeityslash.client;

import com.example.skydeityslash.SkydeitySlash;
import com.example.skydeityslash.entity.EntityRainUmbrella;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 钤印伞渲染器：渲染旋转的伞模型（原版 pledge_of_rain 的伞，参数逐字照搬其
 * RenderEntityRainUmbrella：translate(y+1)、scale 140、每 tick 绕 Y 旋转 10°、
 * 入/退场用 sin 缩放，且不用背面剔除）。
 * 令花神的地面涟漪、芙蓉花琼楼的地面法阵均由实体端 MC 粒子表现，此处不再绘制。
 */
public class RenderRainUmbrella extends EntityRenderer<EntityRainUmbrella> {
    // 白色模型渲染：不再使用伞的图案贴图，用纯白 1x1 贴图 + 白色顶点色 → 整把伞呈白色
    private static final ResourceLocation UMB_TEX =
            new ResourceLocation(SkydeitySlash.MODID, "effects/umbrella/white.png");

    // 与 SlashBlade 自己的带贴图刀模一致：无剔除、带贴图与光照。避免剔除令伞半边面消失。
    private static final RenderType UMB_RENDER_TYPE = RState.composite(
            "skydeityslash:umbrella_translucent",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.TRIANGLES,
            0x8000, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RState.SHADER_ENTITY_TRANSLUCENT)
                    .setTextureState(new RenderStateShard.TextureStateShard(UMB_TEX, false, false))
                    .setTransparencyState(RState.TRANSPARENCY_TRANSLUCENT)
                    .setCullState(RState.CULL_NONE)
                    .setLightmapState(RState.TEXTURING_LIGHTMAP)
                    .setOverlayState(RState.TEXTURING_OVERLAY)
                    .setWriteMaskState(RState.WRITE_COLOR_DEPTH)
                    .createCompositeState(false));

    public RenderRainUmbrella(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(EntityRainUmbrella entity) {
        return UMB_TEX;
    }

    @Override
    public void render(EntityRainUmbrella entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        float age = entity.tickCount + partial;

        // 伞模型：参数逐字照搬原版 RenderEntityRainUmbrella（translate y+1、scale 140、入/退场 sin 缩放）
        ObjModel obj = ObjModel.load();
        if (obj != null) {
            pose.pushPose();
            pose.translate(0, 1, 0);
            float tk = age;
            float scale = 280f;
            if (tk >= 0 && tk <= 20) {
                float bl = (float) Math.sin(tk / 40d * Math.PI);
                scale = 280f * bl;
            } else if (tk > 70 && tk <= 75) {
                float bl = (float) Math.sin((75 - tk) / 40d * Math.PI);
                scale = 280f * bl;
            } else if (tk > 75) {
                scale = 0f;
            }
            pose.scale(scale, scale, scale);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(age * 10f));
            VertexConsumer vc = buffer.getBuffer(UMB_RENDER_TYPE);
            obj.tessellate(vc, pose, LightTexture.FULL_BRIGHT); // 全亮，不受环境光照影响偏暗
            pose.popPose();
        }

        super.render(entity, yaw, partial, pose, buffer, light);
    }
}