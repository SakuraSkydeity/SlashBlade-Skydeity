package com.example.skydeityslash.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 简易 OBJ 解析渲染器（v / vt / f）。
 * SlashBlade Resharped 2.0.5 自带 WavefrontObject 的 loadObjModel 用 startsWith("v")
 * 判断顶点，会连 "vt"/"vn" 一起吞掉，导致带纹理坐标的伞模型整把采样贴图左上角而几乎不可见。
 * 这里自己按标准 OBJ 语义解析，忠实还原原版伞模型的 UV 贴图。
 */
public final class ObjModel {
    private static final ResourceLocation LOC =
            new ResourceLocation("skydeityslash", "effects/umbrella/umbrella.obj");

    private final List<float[]> verts = new ArrayList<>(); // {x,y,z}
    private final List<float[]> uvs = new ArrayList<>();   // {u,v}
    private final List<float[]> rpos = new ArrayList<>();  // 展平后的每个三角角：位置
    private final List<float[]> ruv = new ArrayList<>();   // 展平后的每个三角角：uv
    private final List<int[]> tris = new ArrayList<>();    // 每三角三个角在 rpos/ruv 的索引

    private static ObjModel cached;
    private static boolean cachedTried;

    public static ObjModel load() {
        if (!cachedTried) {
            cachedTried = true;
            try {
                cached = new ObjModel();
            } catch (Exception e) {
                cached = null;
            }
        }
        return cached;
    }

    private ObjModel() throws Exception {
        var opt = Minecraft.getInstance().getResourceManager().getResource(LOC);
        if (opt.isEmpty()) {
            throw new IllegalStateException("missing .obj");
        }
        try (InputStream in = opt.get().open();
             BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim().replaceAll("\\s+", " ");
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                String[] tok = line.split(" ");
                switch (tok[0]) {
                    case "v" -> {
                        if (tok.length >= 4) {
                            verts.add(new float[]{
                                    Float.parseFloat(tok[1]), Float.parseFloat(tok[2]), Float.parseFloat(tok[3])});
                        }
                    }
                    case "vt" -> {
                        if (tok.length >= 3) {
                            uvs.add(new float[]{Float.parseFloat(tok[1]), Float.parseFloat(tok[2])});
                        }
                    }
                    case "f" -> parseFace(tok);
                    default -> {
                    }
                }
            }
        }
        if (verts.isEmpty() || tris.isEmpty()) {
            throw new IllegalStateException("empty obj");
        }
    }

    private void parseFace(String[] tok) {
        List<Integer> corners = new ArrayList<>(tok.length - 1);
        for (int i = 1; i < tok.length; i++) {
            String[] idx = tok[i].split("/");
            int pos = Integer.parseInt(idx[0].trim()) - 1;
            if (pos < 0 || pos >= verts.size()) {
                continue;
            }
            float u = 0, v = 0;
            if (idx.length >= 2 && !idx[1].isEmpty()) {
                int vt = Integer.parseInt(idx[1].trim()) - 1;
                if (vt >= 0 && vt < uvs.size()) {
                    float[] t = uvs.get(vt);
                    u = t[0];
                    v = t[1];
                }
            }
            rpos.add(verts.get(pos));
            ruv.add(new float[]{u, v});
            corners.add(rpos.size() - 1);
        }
        for (int j = 1; j + 1 < corners.size(); j++) {
            tris.add(new int[]{corners.get(0), corners.get(j), corners.get(j + 1)});
        }
    }

    /** 按原版 SlashBlade 解析器的约定翻转 v 采样（v'=1-v），保证贴图方向与剑体始觉等一致。 */
    public void tessellate(VertexConsumer vc, PoseStack pose, int light) {
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        for (int[] t : tris) {
            float[] p0 = rpos.get(t[0]), p1 = rpos.get(t[1]), p2 = rpos.get(t[2]);
            Vec3 ab = new Vec3(p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]);
            Vec3 ac = new Vec3(p2[0] - p0[0], p2[1] - p0[1], p2[2] - p0[2]);
            Vec3 n = ab.cross(ac).normalize();
            putCorner(vc, m, nm, t[0], n, light);
            putCorner(vc, m, nm, t[1], n, light);
            putCorner(vc, m, nm, t[2], n, light);
        }
    }

    private void putCorner(VertexConsumer vc, Matrix4f m, Matrix3f nm, int corner, Vec3 n, int light) {
        float[] p = rpos.get(corner);
        float[] uv = ruv.get(corner);
        vc.vertex(m, p[0], p[1], p[2])
                .color(255, 255, 255, 255)
                .uv(uv[0], 1.0f - uv[1])
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light);
        // 1.20：normal 必须在 endVertex 前设置，否则顶点元素未填满直接抛异常
        if (n != null) {
            org.joml.Vector3f nrm = nm.transform(new org.joml.Vector3f((float) n.x, (float) n.y, (float) n.z)).normalize();
            vc.normal(nm, nrm.x, nrm.y, nrm.z);
        }
        vc.endVertex();
    }
}