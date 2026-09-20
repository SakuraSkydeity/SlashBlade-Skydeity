package com.example.skydeityslash.mixin.client;

import com.example.skydeityslash.client.objopt.BladeRenderHook;
import com.example.skydeityslash.client.objopt.ObjOpt;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.function.Function;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 刀模渲染的收窄点。
 *
 * <h3>为什么拦 6 个 7 参入口，而不是只拦那个 9 参实现</h3>
 * SlashBlade 1.9.65 的 {@code BladeRenderState} 上 6 个 public 渲染入口全部转发到
 * 同一个 9 参 {@code renderOverrided}，单从"收窄"角度看只拦最后那个最省事。
 *
 * <p>只拦 9 参实现是不安全的：**任何**在 7 参层 cancel 的注入（不限于同类优化 mod，
 * 附属 mod 想自定义刀模渲染也会这么做）都会让 9 参实现根本不被调用，
 * 于是只拦下游的写法**完全失效且毫无报错**。所以每个 public 入口都自己拦一份。
 *
 * <p>两个 mixin 注入同一个方法时，Mixin 的处理是"后应用的注入插在更前面"，
 * 而 priority 高的 mixin 后应用 —— 所以 **priority 高 = 先执行**，
 * 先执行者 cancel 之后其余 handler 不会跑。本模块 priority = 5200，
 * 意味着同层注入时本模块确定先接管，结果不随 mods 加载顺序变化。
 *
 * <h3>哪些入口不吃光照</h3>
 * 5 个发光/纯色写入口（{@code ColorWrite}、{@code Luminous}、{@code LuminousDepthWrite}、
 * {@code ReverseLuminous}、{@code ChargeEffect}）用的都是"发光"类着色器，顶点里的光照贴图
 * （{@code UV2}）根本没有被着色器读取，所以传入的 {@code packedLight} 对画面没有影响。
 * 这里把这个事实显式标出来（多传一个 {@code true}），让刀模渲染模块可以放心地
 * 把这些绘制统一成同一个顶点缓冲 —— 剑雨这类"同模型同色、只是散落在不同亮度位置"的
 * 绘制，靠这一条才能真正落到缓存上。
 *
 * <p>主入口 {@code renderOverrided} 用的是普通实体着色器，**吃光照**，所以传 {@code false}，
 * 光照仍然参与缓存键。
 *
 * <p>这些都是 SlashBlade 自己的方法，必须 {@code remap = false}。
 */
@Mixin(value = BladeRenderState.class, remap = false, priority = 5200)
public abstract class MixinBladeRenderState {

    @Shadow(remap = false)
    private static Color col;

    /** 主入口：刀身与鞘，唯一需要阴影代理盒的一个。 */
    @Inject(
            method = "renderOverrided(Lnet/minecraft/world/item/ItemStack;"
                    + "Lmods/flammpfeil/slashblade/client/renderer/model/obj/WavefrontObject;"
                    + "Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void skydeity$renderBase(
            ItemStack stack, WavefrontObject model, String target, ResourceLocation texture,
            PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!ObjOpt.isActive()) {
            return;
        }
        ci.cancel();
        BladeRenderHook.renderOverrided(stack, model, target, texture, poseStack, buffers, light,
                BladeRenderState::getSlashBladeBlend, true, col, true, false);
    }

    @Inject(
            method = "renderOverridedColorWrite(Lnet/minecraft/world/item/ItemStack;"
                    + "Lmods/flammpfeil/slashblade/client/renderer/model/obj/WavefrontObject;"
                    + "Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void skydeity$renderColorWrite(
            ItemStack stack, WavefrontObject model, String target, ResourceLocation texture,
            PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!ObjOpt.isActive()) {
            return;
        }
        ci.cancel();
        BladeRenderHook.renderOverrided(stack, model, target, texture, poseStack, buffers, light,
                BladeRenderState::getSlashBladeBlendColorWrite, true, col, false, true);
    }

    @Inject(
            method = "renderOverridedLuminous(Lnet/minecraft/world/item/ItemStack;"
                    + "Lmods/flammpfeil/slashblade/client/renderer/model/obj/WavefrontObject;"
                    + "Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void skydeity$renderLuminous(
            ItemStack stack, WavefrontObject model, String target, ResourceLocation texture,
            PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!ObjOpt.isActive()) {
            return;
        }
        ci.cancel();
        BladeRenderHook.renderOverrided(stack, model, target, texture, poseStack, buffers, light,
                BladeRenderState::getSlashBladeBlendLuminous, false, col, false, true);
    }

    @Inject(
            method = "renderOverridedLuminousDepthWrite(Lnet/minecraft/world/item/ItemStack;"
                    + "Lmods/flammpfeil/slashblade/client/renderer/model/obj/WavefrontObject;"
                    + "Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void skydeity$renderLuminousDepthWrite(
            ItemStack stack, WavefrontObject model, String target, ResourceLocation texture,
            PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!ObjOpt.isActive()) {
            return;
        }
        ci.cancel();
        BladeRenderHook.renderOverrided(stack, model, target, texture, poseStack, buffers, light,
                BladeRenderState::getSlashBladeBlendLuminousDepthWrite, false, col, false, true);
    }

    @Inject(
            method = "renderOverridedReverseLuminous(Lnet/minecraft/world/item/ItemStack;"
                    + "Lmods/flammpfeil/slashblade/client/renderer/model/obj/WavefrontObject;"
                    + "Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void skydeity$renderReverseLuminous(
            ItemStack stack, WavefrontObject model, String target, ResourceLocation texture,
            PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!ObjOpt.isActive()) {
            return;
        }
        ci.cancel();
        BladeRenderHook.renderOverrided(stack, model, target, texture, poseStack, buffers, light,
                BladeRenderState::getSlashBladeBlendReverseLuminous, false, col, false, true);
    }

    /**
     * 充能特效。它的 RenderType 工厂带随帧变化的 UV 偏移量，原版每次都会新建一个
     * RenderType 实例（缓存键含偏移量），这里保持同一个工厂，只在网格侧吃优化。
     */
    @Inject(
            method = "renderChargeEffect(Lnet/minecraft/world/item/ItemStack;F"
                    + "Lmods/flammpfeil/slashblade/client/renderer/model/obj/WavefrontObject;"
                    + "Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void skydeity$renderChargeEffect(
            ItemStack stack, float animation, WavefrontObject model, String target, ResourceLocation texture,
            PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!ObjOpt.isActive()) {
            return;
        }
        ci.cancel();
        float offset = animation;
        BladeRenderHook.renderOverrided(stack, model, target, texture, poseStack, buffers, light,
                location -> BladeRenderState.getChargeEffect(location, offset * 0.1F % 1.0F, offset * 0.01F % 1.0F),
                false, col, false, true);
    }

    /**
     * 兜底：万一有代码直接调用 9 参重载（不走上面 6 个入口），也接住。
     * 正常情况下这条路不会被走到，因为 7 参入口已经在 HEAD 被取消。
     */
    @Inject(
            method = "renderOverrided(Lnet/minecraft/world/item/ItemStack;"
                    + "Lmods/flammpfeil/slashblade/client/renderer/model/obj/WavefrontObject;"
                    + "Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
                    + "Ljava/util/function/Function;Z)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void skydeity$renderNineArg(
            ItemStack stack, WavefrontObject model, String target, ResourceLocation texture,
            PoseStack poseStack, MultiBufferSource buffers, int light,
            Function<ResourceLocation, RenderType> renderTypeFactory, boolean enableEffect, CallbackInfo ci) {
        if (!ObjOpt.isActive()) {
            return;
        }
        ci.cancel();
        BladeRenderHook.renderOverrided(stack, model, target, texture, poseStack, buffers, light,
                renderTypeFactory, enableEffect, col, false, false);
    }
}
