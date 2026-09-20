package com.example.skydeityslash.mixin.client;

import com.example.skydeityslash.client.objopt.ObjOpt;
import mods.flammpfeil.slashblade.client.renderer.event.ModelResourceLoader;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 资源包重载前清掉显存缓存与解析缓存。
 *
 * <p>必须清在 HEAD 而不是 RETURN：重载会把 {@code BladeModelManager} 里的
 * WavefrontObject 整体换掉，旧的顶点缓冲对应的几何与模型已失效，
 * 而重载过程中就可能触发新模型的渲染。
 */
@Mixin(value = ModelResourceLoader.class, remap = false)
public abstract class MixinModelResourceLoader {

    @Inject(method = "loadResources(Lnet/minecraft/server/packs/resources/ResourceManager;)V",
            at = @At("HEAD"), remap = false)
    private void skydeity$clearBeforeReload(ResourceManager resourceManager, CallbackInfo ci) {
        ObjOpt.onResourceReload();
    }
}
