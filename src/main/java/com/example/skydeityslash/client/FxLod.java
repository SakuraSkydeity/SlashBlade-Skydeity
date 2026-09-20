package com.example.skydeityslash.client;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 特效几何的细节档位。
 *
 * <p>本 mod 的特效几何（{@link GlowGeometry}、{@link InkFoxGeometry} 等）都是每帧现算的
 * 三角面，环绕圆环类形状的段数直接决定单帧要写多少顶点。近距离时高段数必须保留
 * （看得出多边形感），但远处同一个特效只占屏幕几十个像素，段数减半完全看不出来。
 *
 * <p>所以这里只做一件事：按特效到摄像机的距离返回一个档位，由各个几何类据此调整
 * **内部**的段数常量。近档一律保持原值 —— 也就是贴脸看的时候几何与原来逐顶点一致。
 */
public final class FxLod {

    /** 近距离：与原版一致，不做任何削减。 */
    public static final int FULL = 0;
    /** 中距离：段数约七成。 */
    public static final int MID = 1;
    /** 远距离：段数约一半。 */
    public static final int FAR = 2;

    private static final double MID_SQR = 24.0 * 24.0;
    private static final double FAR_SQR = 56.0 * 56.0;

    private FxLod() {
    }

    /** 由摄像机位置决定档位。传 null 时按最高细节处理，宁可多画也不要少画。 */
    public static int tier(Entity entity, Camera camera) {
        if (entity == null || camera == null) {
            return FULL;
        }
        Vec3 cam = camera.getPosition();
        double distanceSqr = entity.distanceToSqr(cam.x, cam.y, cam.z);
        if (distanceSqr <= MID_SQR) {
            return FULL;
        }
        return distanceSqr <= FAR_SQR ? MID : FAR;
    }

    /** 档位对应的段数缩放百分比。 */
    public static int percent(int tier) {
        if (tier == FULL) {
            return 100;
        }
        return tier == MID ? 72 : 48;
    }
}
