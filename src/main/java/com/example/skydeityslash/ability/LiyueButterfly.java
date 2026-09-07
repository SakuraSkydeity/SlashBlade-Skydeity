package com.example.skydeityslash.ability;

import com.example.skydeityslash.entity.EntityHutaoCircleSlash;
import com.example.skydeityslash.registry.ModEntities;
import mods.flammpfeil.slashblade.SlashBlade.RegistryEvents;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.awt.Color;

/**
 * 「璃月」蝶引来生剑技逻辑。
 * 阶段一：复刻原版円刀——4 道红色环形刀光围绕玩家依次展开（180°/90°/0°/-90°），每段 520 点真伤；
 * 阶段二：玩家左右两侧各一簇红色幻影剑（每侧 5 行，剑数 3/4/4/5/3，共 38），
 *         左右对称、错落不规律排布，全部朝锁定目标或准星汇聚飞行，单发 52 点真伤。
 */
public class LiyueButterfly {
    /** 刀身特效颜色：深红色 */
    public static final int WAVE_COLOR = 0x8B0000;
    /** 刀光/幻影剑颜色：红色 */
    public static final int SWORD_COLOR = 0xFF0000;
    /** 结束粒子：深红色 */
    public static final int DARK_RED = 0x8B0000;
    /** 结束粒子：粉红色（少量） */
    public static final int PINK = 0xFF69B4;
    /** 幻影剑生成延时（tick），让円刀先展示 */
    public static final int SWORD_DELAY = 12;
    /** 幻影剑距玩家身后的距离（格） */
    public static final double BACK_DIST = 2.5;

    public static void doButterfly(LivingEntity user) {
        if (!(user instanceof ServerPlayer player)) return;
        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) return;
        ISlashBladeState state = player.getMainHandItem().getCapability(CapabilitySlashBlade.BLADESTATE, null).orElse(null);
        if (state == null) return;

        // 释放剑技时刀身特效颜色改为深红色
        state.setEffectColor(new Color(WAVE_COLOR));

        // 阶段一：円刀——4 道红色环形刀光依次展开，每段 520 真伤
        Vec3 slashPos = player.position().add(0.0, player.getEyeHeight() * 0.75, 0.0)
                .add(player.getLookAngle().scale(0.3));
        float[] yaws = {180.0F, 90.0F, 0.0F, -90.0F};
        for (int i = 0; i < yaws.length; i++) {
            final float yaw = yaws[i];
            final int delay = i * 2;
            serverLevel.getServer().tell(new TickTask(serverLevel.getServer().getTickCount() + delay,
                    () -> spawnCircleSlash(player, level, slashPos, yaw)));
        }

        // 阶段二：玩家身后扇形排布幻影剑，全部聚焦于同一点（锁定目标或准星）
        Entity target = state.getTargetEntity(level);
        Vec3 look = player.getLookAngle();
        Vec3 lookXZ = new Vec3(look.x, 0, look.z);
        if (lookXZ.lengthSqr() < 0.0001) {
            lookXZ = new Vec3(0, 0, 1);
        }
        lookXZ = lookXZ.normalize();
        Vec3 back = lookXZ.scale(-1.0);
        Vec3 side = new Vec3(-lookXZ.z, 0, lookXZ.x);
        Vec3 up = new Vec3(0, 1, 0);

        // 瞄准点（准星指向/锁定目标位置）：每把剑从自身位置指向该点直线射出，汇聚一点而不平行
        final Vec3 aim;
        if (target != null && target.isAlive() && !target.isRemoved()) {
            aim = target.position().add(0.0, target.getEyeHeight() * 0.5, 0.0);
        } else {
            aim = player.getEyePosition().add(look.scale(10.0));
        }

        // 幻影剑排布：每侧 5 行，剑数 [3,4,4,5,3]（共 19/侧、38 全），间距较紧；
        // 各列横向错落、上下行前后微错，左右两侧严格对称镜像，全部朝向准星/锁定目标汇聚飞行
        double vGap = 0.18;
        double[] yBands = {-0.36, -0.18, 0.0, 0.18, 0.36};
        double[][] colDist = {
                {0.90, 1.22, 1.52},            // 第 1 行 3 把
                {0.80, 1.12, 1.44, 1.76},       // 第 2 行 4 把
                {0.96, 1.30, 1.62, 1.98},       // 第 3 行 4 把
                {0.84, 1.10, 1.40, 1.72, 2.04}, // 第 4 行 5 把
                {1.00, 1.34, 1.66}              // 第 5 行 3 把
        };
        Vec3 center = player.getEyePosition();
        final Vec3 forward = lookXZ;
        // 幻影剑在玩家眼部高度生成，严格对准准星视线（不额外抬升），汇聚到准星锁定点
        double tiltLift = 0.0;
        serverLevel.getServer().tell(new TickTask(serverLevel.getServer().getTickCount() + SWORD_DELAY, () -> {
            for (int r = 0; r < colDist.length; r++) {
                double yOff = yBands[r];
                for (double d : colDist[r]) {
                    double fwd = (r - 2) * 0.18;      // 上下行前后轻微错落，更自然
                    Vec3 base = center.add(forward.scale(fwd)).add(0, yOff + tiltLift, 0);
                    spawnSword(player, level, aim, base.add(side.scale(d)));
                    spawnSword(player, level, aim, base.subtract(side.scale(d)));
                }
            }
        }));

        // SA 结束：目标位置高 3 格处三色粒子（深红/红/少量粉红）形成下落螺旋。
        // 粒子随螺旋从顶部向底部推进而先产生者先消散，从而看到由顶到底逐渐消失的效果。
        Vec3 pCenter = (target != null && target.isAlive() && !target.isRemoved())
                ? target.position().add(0.0, target.getEyeHeight() * 0.3, 0.0)
                : player.getEyePosition().add(look.scale(3.0));
        final int steps = 48;
        final double startH = 3.2, endH = 0.3, startR = 1.8, endR = 0.3;
        for (int i = 0; i < steps; i++) {
            final int idx = i;
            final Vec3 pc = pCenter;
            serverLevel.getServer().tell(new TickTask(serverLevel.getServer().getTickCount() + SWORD_DELAY + idx, () -> {
                double t = (double) idx / (steps - 1);
                double rad = startR + (endR - startR) * t;
                double ang = idx * 0.55;
                double h = startH - (startH - endH) * t;
                int col = (idx % 8 == 0) ? PINK : (idx % 2 == 0 ? SWORD_COLOR : DARK_RED);
                serverLevel.sendParticles(new DustParticleOptions(colorVec(col), 0.9f),
                        pc.x + Math.cos(ang) * rad, pc.y + h, pc.z + Math.sin(ang) * rad,
                        6, 0.06, 0.06, 0.06, 0.0);
                // 内圈：更靠近中心、偏转 60°，让粒子更浓，同样随顶部先消散
                serverLevel.sendParticles(new DustParticleOptions(colorVec(col), 0.9f),
                        pc.x + Math.cos(ang + 1.0472) * rad * 0.55, pc.y + h + 0.12, pc.z + Math.sin(ang + 1.0472) * rad * 0.55,
                        4, 0.04, 0.04, 0.04, 0.0);
            }));
        }
    }

    /** 颜色整型转粒子色向量 */
    private static Vector3f colorVec(int rgb) {
        return new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
    }

    /** 生成一道环形刀光（与原版円刀相同的定位与朝向） */
    private static void spawnCircleSlash(ServerPlayer player, Level level, Vec3 pos, float yaw) {
        EntityHutaoCircleSlash slash = new EntityHutaoCircleSlash(RegistryEvents.SlashEffect, level);
        slash.setPos(pos.x, pos.y, pos.z);
        slash.setOwner(player);
        slash.setRotationRoll(0.0F);
        slash.setYRot(player.getYRot() - 22.5F + yaw);
        slash.setXRot(0.0F);
        slash.setColor(SWORD_COLOR);
        slash.setKnockBack(KnockBacks.cancel);
        level.addFreshEntity(slash);
    }

    /** 生成一把幻影剑：使用模组自带召唤剑（RegistryEvents.SummonedSword），每把从自身位置朝向瞄准点直线飞出（汇聚一点，不平行） */
    private static void spawnSword(ServerPlayer player, Level level, Vec3 aim, Vec3 pos) {
        EntityAbstractSummonedSword sword = new EntityAbstractSummonedSword(RegistryEvents.SummonedSword, level);
        sword.setPos(pos);
        sword.setShooter(player);
        sword.setColor(SWORD_COLOR);
        sword.setRoll(0.0F);
        sword.setDamage(52.0);
        Vec3 dir = aim.subtract(pos);
        sword.shoot(dir.x, dir.y, dir.z, 1.6F, 0.0F);
        level.addFreshEntity(sword);
    }
}
