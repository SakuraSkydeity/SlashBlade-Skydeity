package com.example.skydeityslash.client.objopt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank.ConcentrationRanks;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

/**
 * 刀光（{@code EntitySlashEffect}）的距离降级渲染。
 *
 * <p>原版每次都要把四层（S 级高光 / D 级 / B 级 / 主体）全部渲染一遍，
 * 每层都走完整高模；而同屏可能同时存在好几道刀光，远处的那些在屏幕上只有几十像素。
 *
 * <p>本模块按到相机的距离分档：
 * <table>
 *   <tr><td>≤16 格</td><td>全细节，四层都画，完整网格</td></tr>
 *   <tr><td>≤28 格</td><td>省掉 S 级高光层，改用吸附后的低模网格</td></tr>
 *   <tr><td>&gt;28 格</td><td>只留主体层，低模网格</td></tr>
 *   <tr><td>启用光影包</td><td>直接按远距离处理（光影会替换 shader，静态假设不稳）</td></tr>
 *   <tr><td>阴影贴图阶段</td><td>整道刀光不绘制</td></tr>
 * </table>
 *
 * <p>四层的颜色、缩放、UV 滚动量、alpha 全部逐条复刻原版
 * {@code SlashEffectRenderer.render}，只改"画几层、用多密的网格"。
 *
 * <p>刀光的模型与贴图在原版里是写死的两个资源常量，且该渲染器没有对外暴露
 * 替换入口，所以这里直接用同一对常量；只要拿不到烘焙网格就返回 false，
 * 由原版实现继续渲染。
 */
public final class SlashEffectRenderHook {

    private static final ResourceLocation SLASH_MODEL =
            ResourceLocation.tryParse("slashblade:model/util/slash.obj");
    private static final ResourceLocation SLASH_TEXTURE =
            ResourceLocation.tryParse("slashblade:model/util/slash.png");
    private static final String TARGET = "base";

    /** 近处：全细节。 */
    private static final double HIGH_DETAIL_DISTANCE_SQ = 16.0 * 16.0;
    /** 中距离：减一层。 */
    private static final double MEDIUM_DETAIL_DISTANCE_SQ = 28.0 * 28.0;

    private static final float Y_SCALE = 0.03F;
    private static final float BASE_SCALE = 1.2F;
    /** 练度低于 C 时原版会把颜色压成中性灰。 */
    private static final int NEUTRAL_COLOR = 5592405;
    private static final int OUTER_COLOR = 2236962;
    private static final int MID_COLOR = 4210752;

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 只打一次，用来确认刀光这条接管路径真的跑起来了。 */
    private static volatile boolean loggedFirstHit;

    private SlashEffectRenderHook() {
    }

    /**
     * @return true 表示本模块已接管（调用方应 cancel 原方法）；false 表示交给原版
     */
    public static boolean tryRender(
            EntitySlashEffect entity,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight) {
        if (!ObjOpt.isActive()) {
            return false;
        }

        WavefrontObject model = BladeModelManager.getInstance().getModel(SLASH_MODEL);
        if (!(model instanceof BakedMeshHolder holder)) {
            return false;
        }
        ObjBakedMesh baked = holder.objopt$getBakedMesh();
        if (baked == null) {
            return false;
        }
        ObjBakedMesh.BakedGroup group = baked.getSingleGroup(TARGET);
        if (group == null) {
            // 组名对不上（模型被换过）就不接管，交回原版。
            return false;
        }

        if (!loggedFirstHit) {
            loggedFirstHit = true;
            LOGGER.info("[Skydeity] 刀光距离降级已生效");
        }

        // 阴影贴图里根本看不出刀光（加法混合的透明片），直接接管成"不画"。
        if (ShaderPackCompat.isRenderingShadowPass()) {
            return true;
        }

        int lifetime = entity.getLifetime();
        if (lifetime <= 0) {
            // 原版这里会除以 0 得到 NaN；我们直接跳过这一帧。
            return true;
        }

        boolean shaderPack = ShaderPackCompat.isShaderPackInUse();
        double distanceSq = distanceToCameraSquared(entity);
        boolean highDetail = !shaderPack && distanceSq <= HIGH_DETAIL_DISTANCE_SQ;
        boolean mediumDetail = !shaderPack && distanceSq <= MEDIUM_DETAIL_DISTANCE_SQ;
        boolean useLod = !highDetail;

        float progress = Math.min((float) lifetime, (float) entity.tickCount + partialTicks) / (float) lifetime;
        double lifeRatio = Mth.clamp(
                ((float) lifetime - ((float) entity.tickCount + partialTicks)) / (float) lifetime, 0.0, 1.0);
        double inverse = lifeRatio - 1.0;
        int alpha = (0xFF & (int) (255.0 * (-(inverse * inverse * inverse * inverse) + 1.0))) << 24;
        if ((alpha >>> 24) == 0) {
            // 完全透明，不用画也不用算矩阵。
            return true;
        }

        int color = entity.getColor() & 0xFFFFFF;
        ConcentrationRanks rank = entity.getRankCode();
        if (rank.level < ConcentrationRanks.C.level) {
            color = NEUTRAL_COLOR;
        }

        poseStack.pushPose();
        try {
            poseStack.mulPose(Axis.YP.rotationDegrees(
                    -Mth.lerp(partialTicks, entity.yRotO, entity.getYRot()) - 90.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTicks, entity.xRotO, entity.getXRot())));
            poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getRotationRoll()));
            poseStack.mulPose(Axis.YP.rotationDegrees(entity.getRotationOffset() - 135.0F * progress));
            poseStack.scale(1.0F, 0.25F, 1.0F);
            poseStack.scale(BASE_SCALE, BASE_SCALE, BASE_SCALE);

            float baseSize = entity.getBaseSize();
            float scale = baseSize * Mth.lerp(progress, 0.03F, 0.035F);

            // 第一层：S 级外圈高光。只在最近距离画。
            if (highDetail && ConcentrationRanks.S.level <= rank.level) {
                pushPass(poseStack);
                try {
                    float windScale = baseSize * Mth.lerp(progress, 0.035F, 0.03F);
                    poseStack.scale(windScale, Y_SCALE, windScale);
                    Face.setAlphaOverride(Face.alphaOverrideYZZ);
                    Face.setUvOperator(1.0F, 1.0F, 0.0F, -0.8F + progress * 0.3F);
                    BladeRenderHook.renderDirect(model, TARGET, SLASH_TEXTURE, poseStack, buffers, packedLight,
                            OUTER_COLOR | alpha, BladeRenderState::getSlashBladeBlendColorWrite, false);
                } finally {
                    poseStack.popPose();
                }
            }

            // 第二层：D 级。中近距离画。
            if (mediumDetail && ConcentrationRanks.D.level <= rank.level) {
                pushPass(poseStack);
                try {
                    poseStack.scale(scale, Y_SCALE, scale);
                    Face.setAlphaOverride(Face.alphaOverrideYZZ);
                    Face.setUvOperator(1.0F, 1.0F, 0.0F, -0.35F - progress * 0.15F);
                    BladeRenderHook.renderDirect(model, TARGET, SLASH_TEXTURE, poseStack, buffers, packedLight,
                            color | alpha, BladeRenderState::getSlashBladeBlendColorWrite, useLod);
                } finally {
                    poseStack.popPose();
                }
            }

            // 第三层：B 级柔光。中近距离画。
            if (mediumDetail && ConcentrationRanks.B.level <= rank.level) {
                pushPass(poseStack);
                try {
                    float windScale = baseSize * Mth.lerp(progress, 0.03F, 0.0375F);
                    poseStack.scale(windScale, Y_SCALE, windScale);
                    Face.setAlphaOverride(Face.alphaOverrideYZZ);
                    Face.setUvOperator(1.0F, 1.0F, 0.0F, -0.5F - progress * 0.2F);
                    BladeRenderHook.renderDirect(model, TARGET, SLASH_TEXTURE, poseStack, buffers, packedLight,
                            MID_COLOR | alpha, BladeRenderState::getSlashBladeBlendLuminous, useLod);
                } finally {
                    poseStack.popPose();
                }
            }

            // 第四层：主体。任何距离都画，只是远处换成低模网格。
            pushPass(poseStack);
            try {
                poseStack.scale(scale, Y_SCALE, scale);
                Face.setAlphaOverride(Face.alphaOverrideYZZ);
                Face.setUvOperator(1.0F, 1.0F, 0.0F, -0.35F - progress * 0.15F);
                BladeRenderHook.renderDirect(model, TARGET, SLASH_TEXTURE, poseStack, buffers, packedLight,
                        color | alpha, BladeRenderState::getSlashBladeBlendLuminous, useLod);
            } finally {
                poseStack.popPose();
            }
        } catch (Throwable t) {
            // 中途出问题就把这一帧交回原版：先复位被改过的全局状态，再返回 false。
            BladeRenderHook.resetTransientState();
            return false;
        } finally {
            poseStack.popPose();
        }

        return true;
    }

    /** 与原版一致：每层之间都重新压一次矩阵栈。 */
    private static void pushPass(PoseStack poseStack) {
        poseStack.pushPose();
    }

    private static double distanceToCameraSquared(EntitySlashEffect entity) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity camera = minecraft.getCameraEntity();
        if (camera == null) {
            camera = minecraft.player;
        }
        return camera == null ? 0.0 : entity.distanceToSqr(camera);
    }
}
