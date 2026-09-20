package com.example.skydeityslash.client.objopt;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * 刀模的静态顶点缓冲缓存。
 *
 * <p>原版每次渲染都要把同样的三角形重新塞进 BufferBuilder、再上传一遍。
 * 只要几何、光照、颜色三者都不变，顶点数据就是常量，完全可以只传一次，
 * 之后每帧只 bind + drawWithShader。
 *
 * <p>缓存键里**必须**带 packedLight 与 color —— 它们是被烘焙进顶点数据的。
 * 贴图**不必**进键：纹理是绘制时才通过 {@code setShaderTexture} 绑定的，与顶点数据无关，
 * 这样能省下相当一截显存。
 *
 * <p>上限刻意设得比同类 mod 小：拔刀剑的刀模总量有限，本模块又只在 GUI /
 * 展示框这类静态上下文使用它，给太多预算反而容易长时间占着不被复用。
 */
public final class BladeVboCache {

    private static final long MAX_CACHE_BYTES = 32L * 1024 * 1024;
    private static final int MAX_ENTRIES = 128;
    private static final int BUILDER_CAPACITY = 256 * 1024;

    /** 上传节流：进世界那一瞬间不要一次性发起几十次显存分配。 */
    private static final int MAX_UPLOADS_PER_WINDOW = 6;
    private static final long UPLOAD_WINDOW_MS = 500L;

    private static final Object LOCK = new Object();
    private static final Map<CacheKey, CachedBuffer> CACHE = new LinkedHashMap<>(64, 0.75F, true);
    private static long bytesUsed;
    private static volatile boolean disabled;

    private static long uploadWindowStart;
    private static int uploadWindowCount;
    private static int failuresLogged;

    private BladeVboCache() {
    }

    public static boolean isDisabled() {
        return disabled;
    }

    /**
     * 尝试用缓存缓冲画出这个组。
     *
     * @param positionTextureOnly true 表示只写位置与 UV（附魔闪光），不烘焙光照与颜色
     * @return true 表示已经画完；false 表示调用方必须回退到立即写路径
     */
    public static boolean tryRender(
            ObjBakedMesh.BakedGroup group,
            RenderType renderType,
            ResourceLocation texture,
            PoseStack poseStack,
            int packedLight,
            int color,
            boolean positionTextureOnly) {
        if (disabled || group == null || group.isEmpty() || renderType == null) {
            return false;
        }
        // 这两个全局开关一旦被改写，顶点数据就会随帧变化，此时绝不能复用缓冲。
        if (Face.alphaOverride != Face.alphaNoOverride || !isDefaultUvOperator()) {
            return false;
        }
        if (RenderSystem.getModelViewMatrix() == null || renderType.format() == null) {
            return false;
        }

        CacheKey key = new CacheKey(group, positionTextureOnly, packedLight, color);
        CachedBuffer cached;
        try {
            synchronized (LOCK) {
                if (disabled) {
                    return false;
                }
                cached = CACHE.get(key);
                if (cached == null) {
                    long estimate = estimateBytes(group, renderType);
                    if (bytesUsed + estimate > MAX_CACHE_BYTES || CACHE.size() >= MAX_ENTRIES) {
                        return false;
                    }
                    if (!throttleUpload()) {
                        return false;
                    }
                    cached = build(group, renderType, positionTextureOnly, packedLight, color, estimate);
                    if (cached == null) {
                        return false;
                    }
                    CACHE.put(key, cached);
                    bytesUsed += cached.bytes;
                    evictIfNeeded();
                }
                if (cached.buffer.isInvalid()) {
                    remove(key);
                    return false;
                }
            }
        } catch (Throwable t) {
            // 出过一次异常就说明当前驱动/状态组合不适合这条路，
            // 整条路径永久降级，之后全部走原版渲染。
            logFailure(t);
            disable();
            return false;
        }

        return draw(cached.buffer, renderType, texture, poseStack);
    }

    private static boolean draw(VertexBuffer buffer, RenderType renderType, ResourceLocation texture, PoseStack poseStack) {
        try {
            renderType.setupRenderState();
            try {
                if (texture != null) {
                    RenderSystem.setShaderTexture(0, texture);
                }
                ShaderInstance shader = RenderSystem.getShader();
                if (shader == null) {
                    return false;
                }
                // 模型视图矩阵 = 当前栈顶 × 相机。法线由 shader 的 NormalMat 变换，
                // 所以缓冲里存模型空间法线即可，与原版逐帧变换数值等价。
                Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(poseStack.last().pose());
                buffer.bind();
                try {
                    buffer.drawWithShader(modelView, RenderSystem.getProjectionMatrix(), shader);
                } finally {
                    VertexBuffer.unbind();
                }
                return true;
            } finally {
                renderType.clearRenderState();
            }
        } catch (Throwable t) {
            logFailure(t);
            disable();
            return false;
        }
    }

    private static CachedBuffer build(
            ObjBakedMesh.BakedGroup group,
            RenderType renderType,
            boolean positionTextureOnly,
            int light,
            int color,
            long estimate) {
        BufferBuilder.RenderedBuffer rendered = null;
        try {
            // 顶点格式取自 RenderType 自己，不要凭记忆挑 DefaultVertexFormat 常量。
            VertexFormat format = renderType.format();
            BufferBuilder builder = new BufferBuilder(BUILDER_CAPACITY);
            builder.begin(VertexFormat.Mode.TRIANGLES, format);
            if (positionTextureOnly) {
                group.writeStaticPositionTexture(builder);
            } else {
                group.writeStatic(builder, light, color);
            }
            rendered = builder.endOrDiscardIfEmpty();
            if (rendered == null || rendered.isEmpty()) {
                return null;
            }

            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            try {
                buffer.upload(rendered);
            } finally {
                VertexBuffer.unbind();
            }
            rendered = null;
            return new CachedBuffer(buffer, estimate);
        } catch (Throwable t) {
            logFailure(t);
            return null;
        } finally {
            if (rendered != null) {
                rendered.release();
            }
        }
    }

    private static long estimateBytes(ObjBakedMesh.BakedGroup group, RenderType renderType) {
        int vertexSize = Math.max(12, renderType.format().getVertexSize());
        return (long) group.vertexCount * vertexSize + 256L;
    }

    private static boolean throttleUpload() {
        long now = System.currentTimeMillis();
        if (now - uploadWindowStart >= UPLOAD_WINDOW_MS) {
            uploadWindowStart = now;
            uploadWindowCount = 0;
        }
        return ++uploadWindowCount <= MAX_UPLOADS_PER_WINDOW;
    }

    private static void evictIfNeeded() {
        Iterator<Map.Entry<CacheKey, CachedBuffer>> iterator = CACHE.entrySet().iterator();
        while ((bytesUsed > MAX_CACHE_BYTES || CACHE.size() > MAX_ENTRIES) && iterator.hasNext()) {
            Map.Entry<CacheKey, CachedBuffer> entry = iterator.next();
            bytesUsed -= entry.getValue().bytes;
            entry.getValue().close();
            iterator.remove();
        }
    }

    private static void remove(CacheKey key) {
        CachedBuffer removed = CACHE.remove(key);
        if (removed != null) {
            bytesUsed -= removed.bytes;
            removed.close();
        }
    }

    /**
     * 清空缓存。顶点缓冲是 GL 对象，只能在渲染线程释放，
     * 非渲染线程调用时转发到渲染线程执行。
     */
    public static void clear() {
        if (RenderSystem.isOnRenderThread()) {
            clearNow();
        } else {
            RenderSystem.recordRenderCall(BladeVboCache::clearNow);
        }
    }

    private static void clearNow() {
        synchronized (LOCK) {
            for (CachedBuffer buffer : CACHE.values()) {
                buffer.close();
            }
            CACHE.clear();
            bytesUsed = 0L;
        }
        disabled = false;
    }

    private static void disable() {
        clearNow();
        disabled = true;
    }

    private static boolean isDefaultUvOperator() {
        Vector4f uv = Face.uvOperator;
        return uv.x() == 1.0F && uv.y() == 1.0F && uv.z() == 0.0F && uv.w() == 0.0F;
    }

    private static void logFailure(Throwable t) {
        if (failuresLogged < 3) {
            failuresLogged++;
            System.err.println("[Skydeity/ObjOpt] 顶点缓冲路径异常，已降级回原版渲染: " + t);
        }
    }

    private record CacheKey(ObjBakedMesh.BakedGroup group, boolean positionTextureOnly, int packedLight, int color) {
    }

    private record CachedBuffer(VertexBuffer buffer, long bytes) {
        private void close() {
            try {
                this.buffer.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
