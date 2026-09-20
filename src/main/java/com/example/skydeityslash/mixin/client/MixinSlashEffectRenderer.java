package com.example.skydeityslash.mixin.client;

import com.example.skydeityslash.client.objopt.SlashEffectRenderHook;
import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.client.renderer.entity.SlashEffectRenderer;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 刀光的距离降级。
 *
 * <p>原版每次都要把四层（S 级高光 / D 级 / B 级 / 主体）全画一遍，每层都是完整高模；
 * 同屏几道刀光时这里是最不值得的开销。接管后按到相机的距离分档减层 + 换低模网格。
 *
 * <p>只有能拿到烘焙网格时才 cancel，否则原版实现继续跑（不会出现刀光消失）。
 *
 * <p>priority 5200 是本工程里最高的：同类优化 mod 也注入这个方法，抬高优先级让
 * 本模块确定性接管，而不是看 mods 加载顺序。
 */
@Mixin(value = SlashEffectRenderer.class, remap = false, priority = 5200)
public abstract class MixinSlashEffectRenderer {

    @Inject(
            method = "render(Lmods/flammpfeil/slashblade/entity/EntitySlashEffect;"
                    + "FFLcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void skydeity$renderWithDistanceLod(
            EntitySlashEffect entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            CallbackInfo ci) {
        if (SlashEffectRenderHook.tryRender(entity, partialTicks, poseStack, buffers, packedLight)) {
            ci.cancel();
        }
    }
}
