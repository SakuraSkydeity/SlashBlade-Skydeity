package com.example.skydeityslash.mixin.client;

import com.example.skydeityslash.client.objopt.BladeDisplayContext;
import com.example.skydeityslash.client.objopt.BladeRenderResourceCache;
import com.example.skydeityslash.client.objopt.ObjOpt;
import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.client.renderer.SlashBladeTEISR;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 记录渲染上下文 + 缓存 NBT → 模型/贴图 的解析结果。
 *
 * <p>两处方法来源不同，remap 设置必须分开：
 * <ul>
 *   <li>{@code renderByItem} 是 vanilla {@code BlockEntityWithoutLevelRenderer} 的方法，
 *       运行时名字是 SRG 的 {@code m_108829_}。本工程未配置 refmap，
 *       所以这里直接写 SRG 名并 {@code remap = false}。</li>
 *   <li>{@code stackDefaultModel} / {@code stackDefaultTexture} 是 SlashBlade 自己的方法，
 *       名字不会被重映射，写明文名 + {@code remap = false}。</li>
 * </ul>
 */
@Mixin(SlashBladeTEISR.class)
public abstract class MixinSlashBladeTEISR {

    private static final String RENDER_BY_ITEM =
            "m_108829_(Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;II)V";

    @Inject(method = RENDER_BY_ITEM, at = @At("HEAD"), remap = false)
    private void skydeity$enterContext(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                                       MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        BladeDisplayContext.push(context);
    }

    @Inject(method = RENDER_BY_ITEM, at = @At("RETURN"), remap = false)
    private void skydeity$exitContext(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                                      MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        BladeDisplayContext.pop();
    }

    @Inject(method = "stackDefaultModel(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void skydeity$resolveModel(ItemStack stack, CallbackInfoReturnable<ResourceLocation> cir) {
        if (ObjOpt.isActive()) {
            cir.setReturnValue(BladeRenderResourceCache.resolveModel(stack));
        }
    }

    @Inject(method = "stackDefaultTexture(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void skydeity$resolveTexture(ItemStack stack, CallbackInfoReturnable<ResourceLocation> cir) {
        if (ObjOpt.isActive()) {
            cir.setReturnValue(BladeRenderResourceCache.resolveTexture(stack));
        }
    }
}
