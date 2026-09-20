package com.example.skydeityslash.mixin.client;

import com.example.skydeityslash.client.objopt.BakedMeshHolder;
import com.example.skydeityslash.client.objopt.ObjBakedMesh;
import com.example.skydeityslash.client.objopt.ObjOpt;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.InputStream;
import java.util.ArrayList;
import mods.flammpfeil.slashblade.client.renderer.model.obj.GroupObject;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把"每帧遍历对象树"换成"加载时烘焙一次"。
 *
 * <p>两个构造器都注入在 RETURN，即 OBJ 解析完、groupObjects 已填好之后立刻烘焙，
 * 这样第一次渲染就是热路径，不会再有"第一次打开物品栏 / 第一次挥刀"的卡顿。
 *
 * <p>四个 tessellate* 全部在 HEAD 处 cancel 并改走烘焙网格。注意**只有烘焙成功
 * 才 cancel**：烘焙失败时原样落回原版实现，不会出现什么都不画的情况。
 *
 * <p>本 mixin 注入的都是 SlashBlade 自己的方法（非 vanilla），所以一律
 * {@code remap = false}；本工程没有配置 refmap，这一点必须保持。
 */
@Mixin(value = WavefrontObject.class, remap = false)
public abstract class MixinWavefrontObject implements BakedMeshHolder {

    @Shadow(remap = false)
    public ArrayList<GroupObject> groupObjects;

    @Unique
    private ObjBakedMesh skydeity$baked;

    @Inject(method = "<init>(Lnet/minecraft/resources/ResourceLocation;)V", at = @At("RETURN"), remap = false)
    private void skydeity$bakeFromResource(ResourceLocation resource, CallbackInfo ci) {
        this.skydeity$bake();
    }

    @Inject(method = "<init>(Ljava/lang/String;Ljava/io/InputStream;)V", at = @At("RETURN"), remap = false)
    private void skydeity$bakeFromStream(String fileName, InputStream stream, CallbackInfo ci) {
        this.skydeity$bake();
    }

    @Unique
    private void skydeity$bake() {
        // 模块被启动参数关掉时不烘焙，省一份内存。
        if (!ObjOpt.isActive()) {
            return;
        }
        try {
            this.skydeity$baked = ObjBakedMesh.bake(this.groupObjects);
        } catch (Throwable t) {
            // 烘焙失败不影响游戏，退回原版渲染路径即可。
            this.skydeity$baked = null;
        }
    }

    @Override
    public ObjBakedMesh objopt$getBakedMesh() {
        return this.skydeity$baked;
    }

    @Inject(
            method = "tessellateAll(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack;II)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void skydeity$tessellateAll(VertexConsumer consumer, PoseStack poseStack, int light, int color, CallbackInfo ci) {
        if (this.skydeity$baked != null) {
            this.skydeity$baked.renderAll(consumer, poseStack, light, color);
            ci.cancel();
        }
    }

    @Inject(
            method = "tessellateOnly(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack;II[Ljava/lang/String;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void skydeity$tessellateOnly(VertexConsumer consumer, PoseStack poseStack, int light, int color,
                                         String[] groupNames, CallbackInfo ci) {
        if (this.skydeity$baked != null) {
            this.skydeity$baked.renderOnly(consumer, poseStack, light, color, groupNames);
            ci.cancel();
        }
    }

    @Inject(
            method = "tessellatePart(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack;IILjava/lang/String;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void skydeity$tessellatePart(VertexConsumer consumer, PoseStack poseStack, int light, int color,
                                         String partName, CallbackInfo ci) {
        if (this.skydeity$baked != null) {
            this.skydeity$baked.renderPart(consumer, poseStack, light, color, partName);
            ci.cancel();
        }
    }

    @Inject(
            method = "tessellateAllExcept(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack;II[Ljava/lang/String;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void skydeity$tessellateAllExcept(VertexConsumer consumer, PoseStack poseStack, int light, int color,
                                              String[] excludedNames, CallbackInfo ci) {
        if (this.skydeity$baked != null) {
            this.skydeity$baked.renderAllExcept(consumer, poseStack, light, color, excludedNames);
            ci.cancel();
        }
    }
}
