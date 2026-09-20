package com.example.skydeityslash.client.objopt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.GroupObject;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.FastColor.ARGB32;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * SlashBlade OBJ 模型的烘焙网格。
 *
 * <p>原版 {@code GroupObject.render -> Face.addFaceForRender -> Face.putVertex} 每帧都要
 * 遍历整棵对象树，并且**每个顶点**新建一个 {@code Matrix3f} 和一个 {@code Vector3f}
 * （开了 alphaOverride 时再加一个 {@code Vector4f}）。其中的 {@code Matrix3f} 纯粹是为了
 * 把法线转到世界空间，而这件事交给 shader 的 NormalMat 做完全等价。
 *
 * <p>这里在模型加载完成的那一刻把对象树压成连续 {@code float[]}，渲染期不再触碰
 * Face / Vertex 对象，也不再产生临时向量。法线保持**模型空间**值。
 *
 * <p>除主网格外还预先算好两份派生数据：
 * <ul>
 *   <li><b>阴影代理盒</b>（{@link BakedGroup#renderShadowProxy}）：沿主轴切 8 段的 AABB。
 *       阴影贴图分辨不出刀身上的高模细节，却要把全部顶点跑一遍深度 pass；</li>
 *   <li><b>刀光 LOD 网格</b>（{@link BakedGroup#renderEffectLod}）：把旋转圆盘上的顶点
 *       按角度吸附到栅格，远距离时顶点数大幅下降。</li>
 * </ul>
 *
 * <p>顶点步长 {@value #STRIDE}：
 * <pre>
 *   0..2   位置 x,y,z
 *   3..5   面法线（模型空间）
 *   6..8   顶点法线（模型空间），无顶点法线时与面法线相同
 *   9..10  UV 原始值
 *   11..12 该面的 UV 平均值
 *   13     标志位：1 = 有顶点法线，2 = 有 UV
 * </pre>
 *
 * <p><b>UV 平均值必须一起存</b>：原版 {@code Face.putVertex} 会把每个顶点的 UV 朝
 * "远离该面平均值"的方向推 5e-4 个纹理单位来遮住相邻面的采样缝。只存 UV 不存平均值
 * 就复现不了这个偏移，刀身上会重新出现细黑缝。
 */
public final class ObjBakedMesh {

    public static final int STRIDE = 14;

    private static final int OFF_FLAGS = 13;
    private static final int OFF_AVG_U = 11;
    private static final int OFF_AVG_V = 12;
    private static final int FLAG_VERTEX_NORMAL = 1;
    private static final int FLAG_UV = 2;

    /** 原版遮接缝用的 UV 偏移量。 */
    private static final float TEXEL_SEAM_OFFSET = 5.0E-4F;

    /** 阴影代理盒：沿最长轴切 8 段。 */
    private static final int SHADOW_SEGMENTS = 8;
    private static final int SHADOW_BOX_STRIDE = 6;
    /** 顶点太少时做代理盒反而更慢，不值得。 */
    private static final int SHADOW_PROXY_MIN_VERTICES = 288;

    /** LOD 网格只在三角面上做，顶点太少时也不值得。 */
    private static final int EFFECT_LOD_MIN_VERTICES = 12;
    /** 角度吸附步长 22.5°，正好是圆盘刷 16 段的粒度。 */
    private static final double EFFECT_LOD_ANGLE_STEP = Math.PI / 8.0;

    private static final int[] BOX_TRIANGLES = {
            0, 2, 3, 0, 3, 1, 4, 5, 7, 4, 7, 6, 0, 1, 5, 0, 5, 4,
            2, 6, 7, 2, 7, 3, 0, 4, 6, 0, 6, 2, 1, 3, 7, 1, 7, 5
    };
    private static final float[] BOX_NORMALS = {
            0, 0, -1, 0, 0, 1, 0, -1, 0, 0, 1, 0, -1, 0, 0, 1, 0, 0
    };

    private static final ThreadLocal<Matrix3f> NORMAL_MATRIX = ThreadLocal.withInitial(Matrix3f::new);
    private static final ThreadLocal<Vector4f> ALPHA_POSITION = ThreadLocal.withInitial(Vector4f::new);
    private static final ThreadLocal<Vector3f> NORMAL_SCRATCH = ThreadLocal.withInitial(Vector3f::new);
    private static final ThreadLocal<float[]> SHADOW_POSITIONS = ThreadLocal.withInitial(() -> new float[24]);
    private static final ThreadLocal<float[]> SHADOW_NORMALS = ThreadLocal.withInitial(() -> new float[18]);

    private final List<BakedGroup> groups;
    private final Map<String, List<BakedGroup>> byName;

    private ObjBakedMesh(List<BakedGroup> groups, Map<String, List<BakedGroup>> byName) {
        this.groups = groups;
        this.byName = byName;
    }

    public static ObjBakedMesh bake(List<GroupObject> source) {
        if (source == null || source.isEmpty()) {
            return new ObjBakedMesh(List.of(), Map.of());
        }
        List<BakedGroup> groups = new ArrayList<>(source.size());
        Map<String, List<BakedGroup>> byName = new HashMap<>();
        for (GroupObject group : source) {
            if (group == null || group.name == null || group.faces == null) {
                continue;
            }
            BakedGroup baked = BakedGroup.bake(group);
            if (baked == null || baked.vertexCount == 0) {
                continue;
            }
            groups.add(baked);
            byName.computeIfAbsent(normalize(group.name), ignored -> new ArrayList<>(2)).add(baked);
        }
        return new ObjBakedMesh(List.copyOf(groups), byName);
    }

    public List<BakedGroup> groups() {
        return this.groups;
    }

    /**
     * 按组名取唯一匹配。
     *
     * <p>OBJ 允许组名重复，重名时无法确定该画哪个，返回 null 交回原版处理。
     */
    public BakedGroup getSingleGroup(String name) {
        List<BakedGroup> matches = this.byName.get(normalize(name));
        return matches != null && matches.size() == 1 ? matches.get(0) : null;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------
    // 立即写 VertexConsumer
    // 这条路径支持 uvOperator 与 alphaOverride，所以刀光这类动态效果也能走。
    // ------------------------------------------------------------------

    public void renderAll(VertexConsumer consumer, PoseStack poseStack, int light, int color) {
        for (BakedGroup group : this.groups) {
            group.render(consumer, poseStack, light, color);
        }
    }

    public void renderOnly(VertexConsumer consumer, PoseStack poseStack, int light, int color, String... names) {
        for (BakedGroup group : this.groups) {
            for (String name : names) {
                if (group.name.equalsIgnoreCase(name)) {
                    group.render(consumer, poseStack, light, color);
                }
            }
        }
    }

    public void renderPart(VertexConsumer consumer, PoseStack poseStack, int light, int color, String name) {
        List<BakedGroup> matches = this.byName.get(normalize(name));
        if (matches != null) {
            for (BakedGroup group : matches) {
                group.render(consumer, poseStack, light, color);
            }
        }
    }

    public void renderAllExcept(VertexConsumer consumer, PoseStack poseStack, int light, int color, String... excluded) {
        outer:
        for (BakedGroup group : this.groups) {
            for (String name : excluded) {
                if (group.name.equalsIgnoreCase(name)) {
                    continue outer;
                }
            }
            group.render(consumer, poseStack, light, color);
        }
    }

    // ------------------------------------------------------------------
    // 单个组
    // ------------------------------------------------------------------

    public static final class BakedGroup {

        public final String name;
        public final int drawingMode;
        public final int vertexCount;
        public final boolean hasUv;
        private final float[] data;
        /** 8 段 AABB，每段 6 个 float；顶点太少时为 null。 */
        private final float[] shadowBoxes;
        /** 角度吸附后的低模顶点，首次需要时才算。 */
        private volatile float[] effectLodData;
        private volatile boolean effectLodReady;

        private BakedGroup(String name, int drawingMode, float[] data, int vertexCount, boolean hasUv, float[] shadowBoxes) {
            this.name = name;
            this.drawingMode = drawingMode;
            this.data = data;
            this.vertexCount = vertexCount;
            this.hasUv = hasUv;
            this.shadowBoxes = shadowBoxes;
        }

        static BakedGroup bake(GroupObject source) {
            List<Face> faces = source.faces;
            int total = 0;
            for (Face face : faces) {
                if (face != null && face.vertices != null && face.vertices.length >= 3) {
                    total += face.vertices.length;
                }
            }
            if (total == 0) {
                return null;
            }

            float[] packed = new float[total * STRIDE];
            int cursor = 0;
            boolean anyUv = false;

            for (Face face : faces) {
                if (face == null || face.vertices == null || face.vertices.length < 3) {
                    continue;
                }
                // 原版这里是懒加载：第一次渲染到这一面时才计算面法线，
                // 表现为"第一次打开物品栏 / 第一次挥刀"卡一下。烘焙期直接算掉。
                if (face.faceNormal == null) {
                    face.faceNormal = face.calculateFaceNormal();
                }
                float fnx = face.faceNormal.x;
                float fny = face.faceNormal.y;
                float fnz = face.faceNormal.z;

                boolean hasUv = face.textureCoordinates != null
                        && face.textureCoordinates.length >= face.vertices.length;
                float avgU = 0.0F;
                float avgV = 0.0F;
                if (hasUv) {
                    for (int i = 0; i < face.vertices.length; i++) {
                        avgU += face.textureCoordinates[i].u;
                        avgV += face.textureCoordinates[i].v;
                    }
                    avgU /= face.vertices.length;
                    avgV /= face.vertices.length;
                    anyUv = true;
                }

                for (int i = 0; i < face.vertices.length; i++) {
                    int offset = cursor;
                    cursor += STRIDE;

                    packed[offset] = face.vertices[i].x;
                    packed[offset + 1] = face.vertices[i].y;
                    packed[offset + 2] = face.vertices[i].z;
                    packed[offset + 3] = fnx;
                    packed[offset + 4] = fny;
                    packed[offset + 5] = fnz;

                    int flags = 0;
                    if (face.vertexNormals != null && i < face.vertexNormals.length && face.vertexNormals[i] != null) {
                        packed[offset + 6] = face.vertexNormals[i].x;
                        packed[offset + 7] = face.vertexNormals[i].y;
                        packed[offset + 8] = face.vertexNormals[i].z;
                        flags |= FLAG_VERTEX_NORMAL;
                    } else {
                        packed[offset + 6] = fnx;
                        packed[offset + 7] = fny;
                        packed[offset + 8] = fnz;
                    }

                    if (hasUv) {
                        packed[offset + 9] = face.textureCoordinates[i].u;
                        packed[offset + 10] = face.textureCoordinates[i].v;
                        packed[offset + 11] = avgU;
                        packed[offset + 12] = avgV;
                        flags |= FLAG_UV;
                    }
                    packed[offset + OFF_FLAGS] = flags;
                }
            }

            if (cursor == 0) {
                return null;
            }
            if (cursor < packed.length) {
                packed = Arrays.copyOf(packed, cursor);
            }
            int vertexCount = cursor / STRIDE;
            float[] shadow = vertexCount > SHADOW_PROXY_MIN_VERTICES
                    ? buildShadowProxy(packed, vertexCount, source.glDrawingMode)
                    : null;
            return new BakedGroup(source.name, source.glDrawingMode, packed, vertexCount, anyUv, shadow);
        }

        public boolean isEmpty() {
            return this.vertexCount == 0;
        }

        // ---- 立即写 -------------------------------------------------

        /** 逐顶点塞进 consumer，支持 uvOperator 与 alphaOverride。 */
        void render(VertexConsumer consumer, PoseStack poseStack, int light, int color) {
            renderWith(this.data, consumer, poseStack, light, color);
        }

        /**
         * 距离降级用的低模版本：顶点按角度吸附到 22.5° 栅格。
         *
         * <p>吸附是绕"包围盒最扁那个轴"所在的平面中心做的，所以只对圆盘类网格有效；
         * 其它形状会原样返回主网格，可以无条件调用。
         */
        public void renderEffectLod(VertexConsumer consumer, PoseStack poseStack, int light, int color) {
            renderWith(effectLod(), consumer, poseStack, light, color);
        }

        private float[] effectLod() {
            if (!this.effectLodReady) {
                synchronized (this) {
                    if (!this.effectLodReady) {
                        this.effectLodData = buildEffectLod(this.data, this.vertexCount, this.drawingMode);
                        this.effectLodReady = true;
                    }
                }
            }
            return this.effectLodData;
        }

        private void renderWith(float[] data, VertexConsumer consumer, PoseStack poseStack, int light, int color) {
            if (data == null || data.length == 0) {
                return;
            }
            Matrix4f transform = poseStack.last().pose();
            int red = ARGB32.red(color);
            int green = ARGB32.green(color);
            int blue = ARGB32.blue(color);
            int alpha = ARGB32.alpha(color);

            Vector4f uv = Face.uvOperator;
            float scaleU = uv.x();
            float scaleV = uv.y();
            float offsetU = uv.z();
            float offsetV = uv.w();

            boolean defaultAlpha = Face.alphaOverride == Face.alphaNoOverride;
            Vector4f alphaPos = defaultAlpha ? null : ALPHA_POSITION.get();

            for (int offset = 0; offset < data.length; offset += STRIDE) {
                float x = data[offset];
                float y = data[offset + 1];
                float z = data[offset + 2];
                int flags = (int) data[offset + OFF_FLAGS];

                int vertexAlpha = defaultAlpha
                        ? alpha
                        : Face.alphaOverride.apply(alphaPos.set(x, y, z, 1.0F), alpha);

                consumer.vertex(transform, x, y, z).color(red, green, blue, vertexAlpha);
                if ((flags & FLAG_UV) != 0) {
                    // 接缝偏移要在套用 uvOperator **之后**比较：原版比的就是变换后的值，
                    // uvOperator 若是负缩放，用未变换的值会比出相反方向。
                    float u = data[offset + 9] * scaleU + offsetU;
                    float v = data[offset + 10] * scaleV + offsetV;
                    float averageU = data[offset + OFF_AVG_U] * scaleU + offsetU;
                    float averageV = data[offset + OFF_AVG_V] * scaleV + offsetV;
                    consumer.uv(
                            u + (u > averageU ? -TEXEL_SEAM_OFFSET : TEXEL_SEAM_OFFSET),
                            v + (v > averageV ? -TEXEL_SEAM_OFFSET : TEXEL_SEAM_OFFSET));
                } else {
                    consumer.uv(0.0F, 0.0F);
                }

                int normalBase = (flags & FLAG_VERTEX_NORMAL) != 0 ? 6 : 3;
                consumer.overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(light)
                        .normal(data[offset + normalBase], data[offset + normalBase + 1], data[offset + normalBase + 2])
                        .endVertex();
            }
        }

        /**
         * 写进静态顶点缓冲。
         *
         * <p>调用前必须确认 {@link Face#uvOperator} 是默认值、{@link Face#alphaOverride} 未被改写，
         * 否则顶点数据会随帧变化，缓存住必然是错的（由调用方判定）。
         *
         * <p>按 position → color → uv → overlay → lightmap → normal 顺序把通道全调一遍：
         * BufferBuilder 的每个通道都会检查"当前待填元素"的用途，不匹配的调用会被静默跳过，
         * 所以同一份代码对 POSITION_COLOR_TEX（附魔闪光）与带光照的格式都成立。
         */
        public void writeStatic(VertexConsumer consumer, int light, int color) {
            int red = ARGB32.red(color);
            int green = ARGB32.green(color);
            int blue = ARGB32.blue(color);
            int alpha = ARGB32.alpha(color);
            float[] data = this.data;
            Vector3f scratch = NORMAL_SCRATCH.get();

            for (int offset = 0; offset < data.length; offset += STRIDE) {
                int flags = (int) data[offset + OFF_FLAGS];
                consumer.vertex(data[offset], data[offset + 1], data[offset + 2])
                        .color(red, green, blue, alpha);
                if ((flags & FLAG_UV) != 0) {
                    consumer.uv(
                            data[offset + 9] + seam(data[offset + 9], data[offset + OFF_AVG_U]),
                            data[offset + 10] + seam(data[offset + 10], data[offset + OFF_AVG_V]));
                } else {
                    consumer.uv(0.0F, 0.0F);
                }
                int normalBase = (flags & FLAG_VERTEX_NORMAL) != 0 ? 6 : 3;
                scratch.set(data[offset + normalBase], data[offset + normalBase + 1], data[offset + normalBase + 2])
                        .normalize();
                consumer.overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(light)
                        .normal(scratch.x(), scratch.y(), scratch.z())
                        .endVertex();
            }
        }

        /** 附魔闪光：只需要位置与 UV。 */
        public void writeStaticPositionTexture(VertexConsumer consumer) {
            float[] data = this.data;
            for (int offset = 0; offset < data.length; offset += STRIDE) {
                int flags = (int) data[offset + OFF_FLAGS];
                consumer.vertex(data[offset], data[offset + 1], data[offset + 2]);
                if ((flags & FLAG_UV) != 0) {
                    consumer.uv(
                            data[offset + 9] + seam(data[offset + 9], data[offset + OFF_AVG_U]),
                            data[offset + 10] + seam(data[offset + 10], data[offset + OFF_AVG_V]));
                } else {
                    consumer.uv(0.0F, 0.0F);
                }
                consumer.endVertex();
            }
        }

        // ---- 阴影代理 -----------------------------------------------

        /**
         * 阴影阶段渲染简化盒。
         *
         * <p>阴影贴图根本分辨不出刀身上的高模细节，却要把全部顶点跑一遍深度 pass。
         * 这里沿最长轴切 8 段、每段一个 AABB，同屏多把高模刀时收益最大。
         *
         * @return true 表示已用代理盒画完；false 表示没有代理数据，调用方应自行回退。
         */
        public boolean renderShadowProxy(VertexConsumer consumer, PoseStack poseStack, int light, int color) {
            if (this.shadowBoxes == null) {
                return false;
            }
            Matrix4f transform = poseStack.last().pose();
            Matrix3f normalMatrix = NORMAL_MATRIX.get().set(transform);
            int red = ARGB32.red(color);
            int green = ARGB32.green(color);
            int blue = ARGB32.blue(color);
            int alpha = ARGB32.alpha(color);

            float[] positions = SHADOW_POSITIONS.get();
            float[] normals = SHADOW_NORMALS.get();
            for (int face = 0; face < 6; face++) {
                int n = face * 3;
                float x = BOX_NORMALS[n];
                float y = BOX_NORMALS[n + 1];
                float z = BOX_NORMALS[n + 2];
                float tx = normalMatrix.m00() * x + normalMatrix.m10() * y + normalMatrix.m20() * z;
                float ty = normalMatrix.m01() * x + normalMatrix.m11() * y + normalMatrix.m21() * z;
                float tz = normalMatrix.m02() * x + normalMatrix.m12() * y + normalMatrix.m22() * z;
                float inv = (float) (1.0 / Math.sqrt(tx * tx + ty * ty + tz * tz));
                normals[n] = tx * inv;
                normals[n + 1] = ty * inv;
                normals[n + 2] = tz * inv;
            }

            for (int box = 0; box < this.shadowBoxes.length; box += SHADOW_BOX_STRIDE) {
                float minX = this.shadowBoxes[box];
                float maxX = this.shadowBoxes[box + 1];
                float minY = this.shadowBoxes[box + 2];
                float maxY = this.shadowBoxes[box + 3];
                float minZ = this.shadowBoxes[box + 4];
                float maxZ = this.shadowBoxes[box + 5];

                for (int corner = 0; corner < 8; corner++) {
                    float x = (corner & 1) == 0 ? minX : maxX;
                    float y = (corner & 2) == 0 ? minY : maxY;
                    float z = (corner & 4) == 0 ? minZ : maxZ;
                    int p = corner * 3;
                    positions[p] = transform.m00() * x + transform.m10() * y + transform.m20() * z + transform.m30();
                    positions[p + 1] = transform.m01() * x + transform.m11() * y + transform.m21() * z + transform.m31();
                    positions[p + 2] = transform.m02() * x + transform.m12() * y + transform.m22() * z + transform.m32();
                }

                for (int v = 0; v < BOX_TRIANGLES.length; v++) {
                    int p = BOX_TRIANGLES[v] * 3;
                    int n = v / 6 * 3;
                    consumer.vertex(positions[p], positions[p + 1], positions[p + 2])
                            .color(red, green, blue, alpha)
                            .uv(0.0F, 0.0F)
                            .overlayCoords(OverlayTexture.NO_OVERLAY)
                            .uv2(light)
                            .normal(normals[n], normals[n + 1], normals[n + 2])
                            .endVertex();
                }
            }
            return true;
        }

        private static float seam(float value, float average) {
            return value > average ? -TEXEL_SEAM_OFFSET : TEXEL_SEAM_OFFSET;
        }

        // ---- LOD 网格 -----------------------------------------------

        /**
         * 把圆盘上的顶点按角度吸附到 22.5° 栅格。
         *
         * <p>做法：取包围盒最扁的那个轴当"盘面法线"，在另两轴构成的平面上以包围盒中心为极点，
         * 把每个顶点的极角吸附到栅格 —— 吸附后落在同一角度上的顶点位置相同，
         * 圆盘边缘那圈高密度三角带就被压成十几段。
         *
         * <p>吸附会让相邻顶点重合，塌成零面积的三角形直接丢掉。如果压下来的比例不到 15%
         * （说明这个网格不是圆盘，吸附没意义），原样返回主网格。
         */
        private static float[] buildEffectLod(float[] source, int count, int mode) {
            if (mode != 4 || count < EFFECT_LOD_MIN_VERTICES) {
                return source;
            }

            float[] min = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
            float[] max = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
            for (int v = 0; v < count; v++) {
                int offset = v * STRIDE;
                for (int axis = 0; axis < 3; axis++) {
                    min[axis] = Math.min(min[axis], source[offset + axis]);
                    max[axis] = Math.max(max[axis], source[offset + axis]);
                }
            }

            float[] extent = {max[0] - min[0], max[1] - min[1], max[2] - min[2]};
            int minorAxis = extent[0] <= extent[1] && extent[0] <= extent[2] ? 0 : (extent[1] <= extent[2] ? 1 : 2);
            int firstAxis = (minorAxis + 1) % 3;
            int secondAxis = (minorAxis + 2) % 3;
            float planeExtent = Math.max(extent[firstAxis], extent[secondAxis]);
            // 太扁（几乎是个平面片）或者不够扁（是个立体块）都不适合做角度吸附。
            if (!(planeExtent > 0.0F) || extent[minorAxis] > planeExtent * 0.15F) {
                return source;
            }

            float centerA = (min[firstAxis] + max[firstAxis]) * 0.5F;
            float centerB = (min[secondAxis] + max[secondAxis]) * 0.5F;
            float[] reduced = new float[source.length];
            int output = 0;

            for (int triangle = 0; triangle + 2 < count; triangle += 3) {
                int candidate = output;
                for (int vertex = 0; vertex < 3; vertex++) {
                    int sourceOffset = (triangle + vertex) * STRIDE;
                    System.arraycopy(source, sourceOffset, reduced, candidate, STRIDE);
                    float a = source[sourceOffset + firstAxis] - centerA;
                    float b = source[sourceOffset + secondAxis] - centerB;
                    double radius = Math.sqrt((double) (a * a + b * b));
                    double angle = Math.atan2((double) b, (double) a);
                    double snapped = Math.rint(angle / EFFECT_LOD_ANGLE_STEP) * EFFECT_LOD_ANGLE_STEP;
                    reduced[candidate + firstAxis] = centerA + (float) (Math.cos(snapped) * radius);
                    reduced[candidate + secondAxis] = centerB + (float) (Math.sin(snapped) * radius);
                    candidate += STRIDE;
                }
                if (!isDegenerateTriangle(reduced, output, planeExtent)) {
                    output = candidate;
                }
            }

            return output < (int) (source.length * 0.85F) ? Arrays.copyOf(reduced, output) : source;
        }

        private static boolean isDegenerateTriangle(float[] data, int offset, float scale) {
            int b = offset + STRIDE;
            int c = b + STRIDE;
            float abX = data[b] - data[offset];
            float abY = data[b + 1] - data[offset + 1];
            float abZ = data[b + 2] - data[offset + 2];
            float acX = data[c] - data[offset];
            float acY = data[c + 1] - data[offset + 1];
            float acZ = data[c + 2] - data[offset + 2];
            float crossX = abY * acZ - abZ * acY;
            float crossY = abZ * acX - abX * acZ;
            float crossZ = abX * acY - abY * acX;
            float areaSquared = crossX * crossX + crossY * crossY + crossZ * crossZ;
            float threshold = scale * scale * scale * scale * 1.0E-12F;
            return areaSquared <= threshold;
        }

        // ---- 阴影代理构建 -------------------------------------------

        private static float[] buildShadowProxy(float[] data, int vertexCount, int mode) {
            float[] min = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
            float[] max = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
            for (int v = 0; v < vertexCount; v++) {
                int o = v * STRIDE;
                for (int axis = 0; axis < 3; axis++) {
                    min[axis] = Math.min(min[axis], data[o + axis]);
                    max[axis] = Math.max(max[axis], data[o + axis]);
                }
            }

            float[] extent = {max[0] - min[0], max[1] - min[1], max[2] - min[2]};
            int dominantAxis = extent[0] >= extent[1] && extent[0] >= extent[2] ? 0 : (extent[1] >= extent[2] ? 1 : 2);
            float dominantMin = min[dominantAxis];
            float dominantExtent = extent[dominantAxis];
            if (!(dominantExtent > 0.0F)) {
                return null;
            }

            // 按主轴向把顶点分到 8 段，每段收一个 AABB。
            // 只统计顶点是不够的：细长三角形的边会跨段，必须把每条边与段边界的交点也算进去，
            // 否则刀身中段会出现"一个盒子都没有"的空洞，阴影上直接看到断口。
            float[] bounds = new float[SHADOW_SEGMENTS * SHADOW_BOX_STRIDE];
            Arrays.fill(bounds, Float.NaN);
            for (int v = 0; v < vertexCount; v++) {
                int o = v * STRIDE;
                includePoint(bounds, segmentOf(data[o + dominantAxis], dominantMin, dominantExtent),
                        data[o], data[o + 1], data[o + 2]);
            }

            float segmentLength = dominantExtent / SHADOW_SEGMENTS;
            int primitiveSize = mode == 7 ? 4 : 3;
            for (int primitive = 0; primitive + primitiveSize - 1 < vertexCount; primitive += primitiveSize) {
                for (int edge = 0; edge < primitiveSize; edge++) {
                    int a = (primitive + edge) * STRIDE;
                    int b = (primitive + (edge + 1) % primitiveSize) * STRIDE;
                    float aDom = data[a + dominantAxis];
                    float bDom = data[b + dominantAxis];
                    float span = bDom - aDom;
                    if (Math.abs(span) < 1.0E-6F) {
                        continue;
                    }
                    float edgeMin = Math.min(aDom, bDom);
                    float edgeMax = Math.max(aDom, bDom);
                    int firstSegment = segmentOf(edgeMin, dominantMin, dominantExtent);
                    int lastSegment = segmentOf(edgeMax, dominantMin, dominantExtent);
                    for (int segment = firstSegment; segment <= lastSegment; segment++) {
                        float slice = dominantMin + (segment + 0.5F) * segmentLength;
                        if (slice < edgeMin || slice > edgeMax) {
                            continue;
                        }
                        float t = (slice - aDom) / span;
                        includePoint(bounds, segment,
                                data[a] + t * (data[b] - data[a]),
                                data[a + 1] + t * (data[b + 1] - data[a + 1]),
                                data[a + 2] + t * (data[b + 2] - data[a + 2]));
                    }
                }
            }

            float[] proxy = new float[SHADOW_SEGMENTS * SHADOW_BOX_STRIDE];
            int out = 0;
            // 刀身横截面只有零点几格，投到阴影贴图上会退化成看不见的一条线，
            // 每个轴留一个最小厚度，避免阴影闪烁。
            float minThickness = Math.max(0.02F, dominantExtent / 1024.0F);
            for (int segment = 0; segment < SHADOW_SEGMENTS; segment++) {
                int base = segment * SHADOW_BOX_STRIDE;
                if (Float.isNaN(bounds[base])) {
                    continue;
                }
                float minX = bounds[base];
                float maxX = bounds[base + 1];
                float minY = bounds[base + 2];
                float maxY = bounds[base + 3];
                float minZ = bounds[base + 4];
                float maxZ = bounds[base + 5];
                float start = dominantMin + segment * segmentLength;
                float end = start + segmentLength;
                if (dominantAxis == 0) {
                    minX = start;
                    maxX = end;
                } else if (dominantAxis == 1) {
                    minY = start;
                    maxY = end;
                } else {
                    minZ = start;
                    maxZ = end;
                }
                float[] ax = ensureThickness(minX, maxX, minThickness);
                float[] ay = ensureThickness(minY, maxY, minThickness);
                float[] az = ensureThickness(minZ, maxZ, minThickness);
                proxy[out++] = ax[0];
                proxy[out++] = ax[1];
                proxy[out++] = ay[0];
                proxy[out++] = ay[1];
                proxy[out++] = az[0];
                proxy[out++] = az[1];
            }
            return out == 0 ? null : Arrays.copyOf(proxy, out);
        }

        private static int segmentOf(float value, float dominantMin, float dominantExtent) {
            int segment = (int) ((value - dominantMin) / dominantExtent * SHADOW_SEGMENTS);
            return Math.max(0, Math.min(SHADOW_SEGMENTS - 1, segment));
        }

        private static float[] ensureThickness(float min, float max, float thickness) {
            if (max - min >= thickness) {
                return new float[]{min, max};
            }
            float center = (min + max) * 0.5F;
            return new float[]{center - thickness * 0.5F, center + thickness * 0.5F};
        }

        private static void includePoint(float[] bounds, int segment, float x, float y, float z) {
            int o = segment * SHADOW_BOX_STRIDE;
            if (Float.isNaN(bounds[o])) {
                bounds[o] = bounds[o + 1] = x;
                bounds[o + 2] = bounds[o + 3] = y;
                bounds[o + 4] = bounds[o + 5] = z;
            } else {
                bounds[o] = Math.min(bounds[o], x);
                bounds[o + 1] = Math.max(bounds[o + 1], x);
                bounds[o + 2] = Math.min(bounds[o + 2], y);
                bounds[o + 3] = Math.max(bounds[o + 3], y);
                bounds[o + 4] = Math.min(bounds[o + 4], z);
                bounds[o + 5] = Math.max(bounds[o + 5], z);
            }
        }
    }
}
