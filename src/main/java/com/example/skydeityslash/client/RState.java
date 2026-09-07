package com.example.skydeityslash.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * RenderStateShard 的受保护着色状态常量在 1.20 无法从外部类直接访问，
 * 故用 RenderType 子类导出并提供公开的 composite 工厂。
 */
public final class RState extends RenderType {
    private RState() {
        super("skydeityslash_rstate_shard", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.TRIANGLES, 0x8000, false, false, () -> {}, () -> {});
    }

    // ---- 受保护着色状态（继承自 RenderStateShard，这里改导出为 public 别名） ----
    public static final RenderStateShard.ShaderStateShard SHADER_POSITION_COLOR = POSITION_COLOR_SHADER;
    public static final RenderStateShard.ShaderStateShard SHADER_ENTITY_TRANSLUCENT = RENDERTYPE_ENTITY_TRANSLUCENT_SHADER;
    public static final RenderStateShard.TransparencyStateShard TRANSPARENCY_ADDITIVE = ADDITIVE_TRANSPARENCY;
    public static final RenderStateShard.TransparencyStateShard TRANSPARENCY_TRANSLUCENT = TRANSLUCENT_TRANSPARENCY;
    public static final RenderStateShard.CullStateShard CULL_NONE = NO_CULL;
    public static final RenderStateShard.WriteMaskStateShard WRITE_COLOR = COLOR_WRITE;
    public static final RenderStateShard.WriteMaskStateShard WRITE_COLOR_DEPTH = COLOR_DEPTH_WRITE;
    public static final RenderStateShard.OutputStateShard OUTPUT_MAIN = MAIN_TARGET;
    public static final RenderStateShard.LightmapStateShard TEXTURING_LIGHTMAP = LIGHTMAP;
    public static final RenderStateShard.OverlayStateShard TEXTURING_OVERLAY = OVERLAY;

    public static RenderType composite(String name, VertexFormat format, VertexFormat.Mode mode,
                                       int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                                       RenderType.CompositeState state) {
        return create(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, state);
    }
}