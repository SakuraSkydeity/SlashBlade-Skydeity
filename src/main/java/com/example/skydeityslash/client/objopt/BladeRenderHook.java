package com.example.skydeityslash.client.objopt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import java.awt.Color;
import java.util.function.Function;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * 刀模渲染的三层降级调度。
 *
 * <p>每次渲染按顺序尝试，任何一层不成立就落下一层：
 * <ol>
 *   <li><b>静态顶点缓冲</b>：几何、颜色固定，且光照要么本就固定（物品栏 / 展示框），
 *       要么对画面无影响（着色器不读光照贴图的发光层）时，直接复用显存里的数据，
 *       CPU 端一个顶点都不碰；</li>
 *   <li><b>立即写</b>：有烘焙网格但条件不满足（刀光滚动 UV、半透明淡出等），
 *       从烘焙数组逐顶点写。仍然不遍历对象树、不新建临时向量；</li>
 *   <li><b>原版</b>：拿不到网格（组名重名、烘焙失败）时原样调 tessellateOnly。</li>
 * </ol>
 *
 * <p><b>先把 {@link RenderOverrideEvent} 派发出去，被取消就立刻返回什么都不做。</b>
 * 其他附属（包括本 mod 自己的 GameEvents）就是靠这个事件换模型、换贴图的，
 * 抢在前面硬渲染等于把扩展点废掉。
 */
public final class BladeRenderHook {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation SHADOW_TEXTURE =
            ResourceLocation.tryParse("minecraft:textures/misc/white.png");
    /** 阴影阶段代理盒用的不透明 RenderType。 */
    private static final RenderType SHADOW_RENDER_TYPE = RenderType.entitySolid(SHADOW_TEXTURE);

    /** 只打一次，用来确认这条接管路径真的跑起来了（被别的 mod 抢走时看不到）。 */
    private static volatile boolean loggedFirstHit;
    private static volatile boolean warnedOnce;

    private BladeRenderHook() {
    }

    /**
     * 对应 {@code BladeRenderState.renderOverrided} 的 9 参重载。
     *
     * @param color            {@code BladeRenderState} 的静态颜色，由 mixin 传入
     * @param shadowProxy      shadow 阶段是否用 8 段代理盒顶替高模（只有主入口开启）
     * @param lightIndependent 该入口的着色器**是否不使用光照贴图**。
     *                         为 true 时顶点里的 packedLight 是死数据（画面与光照无关），
     *                         于是可以把光照统一写成一个常量，让所有光照下的同一把刀
     *                         **共用同一个顶点缓冲** —— 剑雨这类分布在不同亮度位置、
     *                         却用同一个模型和颜色的实体，靠这一条才能落到缓存上。
     */
    public static void renderOverrided(
            ItemStack stack,
            WavefrontObject model,
            String target,
            ResourceLocation texture,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            Function<ResourceLocation, RenderType> renderTypeFactory,
            boolean enableEffect,
            Color color,
            boolean shadowProxy,
            boolean lightIndependent) {

        if (!ObjOpt.isActive()) {
            return;
        }
        if (!loggedFirstHit) {
            loggedFirstHit = true;
            LOGGER.info("[Skydeity] 刀模渲染接管已生效（静态缓冲 / 立即写 / 阴影代理盒均启用）");
        }

        RenderOverrideEvent event = RenderOverrideEvent.onRenderOverride(
                stack, model, target, texture, poseStack, buffers, packedLight, renderTypeFactory, enableEffect);
        if (event.isCanceled()) {
            // 与原版一致：被别人接管时完全不碰全局状态。
            return;
        }

        try {
            int argb = ARGB32.color(color.getAlpha(), color.getRed(), color.getGreen(), color.getBlue());
            ResourceLocation resolvedTexture = event.getTexture();
            RenderType renderType = event.getGetRenderType().apply(resolvedTexture);
            if (renderType == null) {
                return;
            }
            PoseStack eventPose = event.getPoseStack();
            int eventLight = event.getPackedLightIn();
            String eventTarget = event.getTarget();
            ObjBakedMesh.BakedGroup group = singleGroup(event.getModel(), eventTarget);

            // 阴影阶段：用 8 段 AABB 代理盒顶替高模。
            // 阴影贴图分辨不出刀身上的细节，但原版要把全部顶点跑一遍深度 pass，
            // 同屏多把高模刀时这块开销最大。
            if (ShaderPackCompat.isRenderingShadowPass()) {
                if (shadowProxy && group != null
                        && group.renderShadowProxy(
                                event.getBuffer().getBuffer(SHADOW_RENDER_TYPE), eventPose, eventLight, -1)) {
                    return;
                }
                // 没有代理数据（顶点太少不值得）时原样渲染。
                VertexConsumer consumer = event.getBuffer().getBuffer(renderType);
                event.getModel().tessellateOnly(consumer, eventPose, eventLight, argb, eventTarget);
                return;
            }

            // 光照无关的入口统一把光照烘焙成一个常量：
            // 顶点数据因此与所在位置无关，剑雨/发光层这类"同模型同色、只位置不同"的
            // 绘制就能共用一份缓冲（原版是每处光照各写一遍顶点）。
            int bakeLight = lightIndependent ? 0 : eventLight;

            boolean reusable = group != null
                    && (lightIndependent || !stack.isEmpty())
                    && color.getAlpha() == 255
                    && Face.alphaOverride == Face.alphaNoOverride
                    && ShaderPackCompat.allowsStaticBuffer()
                    && BladeDisplayContext.allowsStaticBuffer();

            boolean drawn = reusable
                    && BladeVboCache.tryRender(group, renderType, resolvedTexture, eventPose, bakeLight, argb, false);

            if (!drawn) {
                VertexConsumer consumer = event.getBuffer().getBuffer(renderType);
                if (group != null) {
                    group.render(consumer, eventPose, eventLight, argb);
                } else {
                    event.getModel().tessellateOnly(consumer, eventPose, eventLight, argb, eventTarget);
                }
            }

            // 附魔闪光：同一批顶点换一个 RenderType 再画一遍。
            if (stack.isEnchanted() && event.isEnableEffect()) {
                boolean itemGlint = eventTarget.startsWith("item_");
                RenderType glintType = itemGlint
                        ? BladeRenderState.SLASHBLADE_ITEM_GLINT
                        : BladeRenderState.SLASHBLADE_GLINT;
                ResourceLocation glintTexture = itemGlint
                        ? ItemRenderer.ENCHANTED_GLINT_ITEM
                        : ItemRenderer.ENCHANTED_GLINT_ENTITY;
                boolean glintDrawn = reusable
                        && BladeVboCache.tryRender(group, glintType, glintTexture, eventPose, bakeLight, argb, true);
                if (!glintDrawn) {
                    VertexConsumer consumer = event.getBuffer().getBuffer(glintType);
                    if (group != null) {
                        group.render(consumer, eventPose, eventLight, argb);
                    } else {
                        event.getModel().tessellateOnly(consumer, eventPose, eventLight, argb, eventTarget);
                    }
                }
            }
        } catch (Throwable t) {
            // 优化本身出问题不能影响游戏：这里吞掉异常，画面最多少一层特效。
            // 真出问题时会由 BladeVboCache 的失败路径永久降级，下次就不会再进来。
            logOnce(t);
        } finally {
            // 三个全局状态必须复位，否则会污染后续普通模型渲染。
            resetTransientState();
        }
    }

    /**
     * 直接渲染一个组，不经过静态缓冲。
     *
     * <p>给刀光这类每帧都在动、还带滚动 UV 与位置相关 alpha 的效果用：它们本来就
     * 不可能进缓存，但至少可以省掉对象树遍历与逐顶点临时向量分配。
     *
     * <p>**同样要发 {@link RenderOverrideEvent}**：原版 {@code renderOverrided*} 会发，
     * 这里必须保持一致，否则会悄悄废掉其他附属对刀光的扩展点。
     *
     * @param effectLod 是否使用距离降级后的低模网格
     */
    public static void renderDirect(
            WavefrontObject model,
            String target,
            ResourceLocation texture,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int color,
            Function<ResourceLocation, RenderType> renderTypeFactory,
            boolean effectLod) {
        if (!ObjOpt.isActive()) {
            return;
        }
        RenderOverrideEvent event = RenderOverrideEvent.onRenderOverride(
                ItemStack.EMPTY, model, target, texture, poseStack, buffers, packedLight, renderTypeFactory, false);
        if (event.isCanceled()) {
            return;
        }
        try {
            ResourceLocation resolvedTexture = event.getTexture();
            RenderType renderType = event.getGetRenderType().apply(resolvedTexture);
            if (renderType == null) {
                return;
            }
            PoseStack eventPose = event.getPoseStack();
            int eventLight = event.getPackedLightIn();
            String eventTarget = event.getTarget();
            VertexConsumer consumer = event.getBuffer().getBuffer(renderType);
            ObjBakedMesh.BakedGroup group = singleGroup(event.getModel(), eventTarget);
            if (group == null) {
                event.getModel().tessellateOnly(consumer, eventPose, eventLight, color, eventTarget);
            } else if (effectLod) {
                group.renderEffectLod(consumer, eventPose, eventLight, color);
            } else {
                group.render(consumer, eventPose, eventLight, color);
            }
        } catch (Throwable t) {
            logOnce(t);
        } finally {
            resetTransientState();
        }
    }

    private static void logOnce(Throwable t) {
        if (!warnedOnce) {
            warnedOnce = true;
            LOGGER.warn("[Skydeity] 渲染接管异常，本次回退原版渲染: ", t);
        }
    }

    private static ObjBakedMesh.BakedGroup singleGroup(WavefrontObject model, String target) {
        if (model instanceof BakedMeshHolder holder) {
            ObjBakedMesh baked = holder.objopt$getBakedMesh();
            return baked == null ? null : baked.getSingleGroup(target);
        }
        return null;
    }

    public static void resetTransientState() {
        Face.resetAlphaOverride();
        Face.resetUvOperator();
        BladeRenderState.resetCol();
    }
}
