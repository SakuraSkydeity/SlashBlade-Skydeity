package com.example.skydeityslash.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

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
    /** 自发光：不吃光照（vsh 里声明了 UV2 却从不使用）—— 画贴图特效正合适 */
    public static final RenderStateShard.ShaderStateShard SHADER_ENTITY_EMISSIVE = RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER;
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

    /**
     * 「带贴图的特效片」专用 RenderType：自发光 + **双面**（CULL_NONE，一张 quad 正反都能看）
     * + 不写深度（但深度测试仍开着 ⇒ 会被人物身体正确遮挡）。
     *
     * <p>★ 贴图固定 **blur=false / mipmap=false**（NEAREST、无 mipmap）—— 项目的特效贴图都是纯像素画
     * （alpha 只有 0/255），开线性滤波或 mipmap 边缘就会糊。
     *
     * @param additive {@code true} = 加法混合（发光感，暗背景下最好看）；
     *                 {@code false} = 普通半透明（像素画更清晰，亮背景下更显眼）
     */
    public static RenderType texturedGlow(String name, ResourceLocation texture, boolean additive) {
        return composite(
                name,
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                0x200, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(SHADER_ENTITY_EMISSIVE)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(additive ? TRANSPARENCY_ADDITIVE : TRANSPARENCY_TRANSLUCENT)
                        .setCullState(CULL_NONE)
                        .setWriteMaskState(WRITE_COLOR)
                        .setOutputState(OUTPUT_MAIN)
                        .createCompositeState(false));
    }
}