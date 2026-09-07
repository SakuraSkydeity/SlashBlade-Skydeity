package com.example.skydeityslash.ability;

import com.example.skydeityslash.entity.EntityFontaineTideSword;
import com.example.skydeityslash.entity.EntityFontaineWave;
import com.example.skydeityslash.registry.ModEntities;
import mods.flammpfeil.slashblade.SlashBlade.RegistryEvents;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.awt.Color;

/**
 * 「枫丹」万众狂欢剑技逻辑（重做）。
 * 阶段一：8 方向 × 2 轮 = 16 道剑气，全部从玩家身前向前发射（横/竖/左右斜交替，
 *         全部为浅蓝色），每道无视护甲真伤 52 点；
 * 阶段二：释放瞬间获得 2 秒抗性提升 4；
 * 阶段三：深蓝色幻影剑垂直落下瞄准敌人，造成 1314 点伤害；
 * 阶段四：以玩家为中心，在左右后方生成距离一格、断开前方的圆环粒子，
 *         连续生成并像涟漪一样向外散开（蓝/深蓝/蓝白色）。
 * 全部伤害命中时按等额生命吸血（由吸血实体实现）。
 */
public class FontaineCarnival {
    /** 浅蓝色（剑气/幻影剑） #7be6ef */
    public static final int LIGHT_BLUE = 0x7BE6EF;
    /** 深蓝色（大幻影剑） #2766e7 */
    public static final int DARK_BLUE = 0x2766E7;
    /** 蓝白色（涟漪粒子） #afe9ff */
    public static final int BLUE_WHITE = 0xAFE9FF;
    /** 剑气单发伤害（真伤） */
    public static final double WAVE_DAMAGE = 52.0;
    /** 剑气数量 */
    public static final int WAVE_COUNT = 16;
    /** 大幻影剑伤害 */
    public static final double BIG_SWORD_DAMAGE = 1314.0;
    /** 大幻影剑相对释放的延时（tick，20 = 1 秒） */
    public static final int BIG_SWORD_DELAY = 20;

    public static void doCarnival(LivingEntity user) {
        if (!(user instanceof ServerPlayer player)) return;
        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) return;
        ISlashBladeState state = player.getMainHandItem().getCapability(CapabilitySlashBlade.BLADESTATE, null).orElse(null);
        if (state == null) return;

        // 释放剑技时刀身特效颜色改为浅蓝色
        state.setEffectColor(new Color(LIGHT_BLUE));

        // 阶段一：8 方向 × 2 轮 = 16 道剑气，全部从玩家身前向前发射（横/竖/左右斜交替）
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 0.001 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = new Vec3(0, 1, 0);
        // 前方扇形：以 look 为轴，向左右/上下/斜向展开
        Vec3 lookLeft = look.subtract(right.scale(0.5)).normalize();
        Vec3 lookRight = look.add(right.scale(0.5)).normalize();
        Vec3 lookUp = look.add(up.scale(0.5)).normalize();
        Vec3 lookDown = look.subtract(up.scale(0.5)).normalize();
        Vec3 lookUpLeft = look.subtract(right.scale(0.5)).add(up.scale(0.5)).normalize();
        Vec3 lookUpRight = look.add(right.scale(0.5)).add(up.scale(0.5)).normalize();
        Vec3 lookDownRight = look.add(right.scale(0.5)).subtract(up.scale(0.5)).normalize();
        // 横/竖/左右斜交替排列
        Vec3[] dirs = {
                look, lookUp, lookUpLeft, lookLeft,
                lookDown, lookUpRight, lookRight, lookDownRight
        };
        // 横=0°、竖=90°、左右斜=±45°
        float[] rolls = {0, 90, 45, 0, 90, -45, 0, -45};
        Vec3 spawn = player.getEyePosition().add(look.scale(1.5));
        for (int i = 0; i < WAVE_COUNT; i++) {
            final int idx = i;
            serverLevel.getServer().tell(new TickTask(serverLevel.getServer().getTickCount() + idx, () -> {
                EntityFontaineWave wave = new EntityFontaineWave(RegistryEvents.Drive, level);
                level.addFreshEntity(wave);
                wave.setPos(spawn.x, spawn.y, spawn.z);
                wave.setDamage(WAVE_DAMAGE);
                wave.setSpeed(1.4F);
                wave.setColor(LIGHT_BLUE);
                wave.setOwner(player);
                wave.setRotationRoll(rolls[idx % 8]);
                wave.setIsCritical(false);
                wave.setKnockBack(KnockBacks.cancel);
                wave.setLifetime(40.0F);
                wave.shoot(dirs[idx % 8].x, dirs[idx % 8].y, dirs[idx % 8].z, wave.getSpeed(), 0.0F);
            }));
        }

        // 阶段二：释放瞬间获得 2 秒抗性提升 4
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 3));

        // 阶段三：深蓝色幻影剑垂直落下瞄准敌人（1314 伤害，延时 1 秒）
        Entity target = state.getTargetEntity(level);
        Vec3 basePos = target != null ? target.position()
                : player.getEyePosition().add(player.getLookAngle().scale(5.0));
        Vec3 rainPos = basePos.add(0.0, 7.0, 0.0);
        EntityFontaineTideSword bigSword = new EntityFontaineTideSword(ModEntities.FONTAINE_TIDE_SWORD.get(), level);
        bigSword.setOwner(player);
        bigSword.setColor(DARK_BLUE);
        bigSword.setRoll(0.0F);
        bigSword.setDamage(BIG_SWORD_DAMAGE);
        bigSword.startRiding(player, true);
        bigSword.setDelay(BIG_SWORD_DELAY);
        bigSword.setPos(rainPos);
        bigSword.setXRot(-90.0F);
        level.addFreshEntity(bigSword);

        // 阶段四：水波涟漪（以玩家为中心，从 2 格扩散到 4 格，上下波动，动画加快并逐渐消散）
        Vec3 center = player.position().add(0.0, 1.0, 0.0);
        double frontAngle = Math.atan2(look.x, look.z); // 玩家面朝方向在 XZ 平面的角度
        double frontGap = Math.toRadians(50.0);         // 前方断开缺口（±50° 内不生成）
        int totalTicks = 16;                            // 连续生成 0.8 秒（动画更快）
        int ringCount = 24;                             // 每圈粒子数
        int[] rippleColors = {LIGHT_BLUE, DARK_BLUE, BLUE_WHITE};
        for (int t = 0; t < totalTicks; t++) {
            final int delay = t;
            serverLevel.getServer().tell(new TickTask(serverLevel.getServer().getTickCount() + delay, () -> {
                // 涟漪半径从 2 格扩散到 4 格
                double radius = 2.0 + delay * (2.0 / (totalTicks - 1));
                // 波动幅度随时间衰减（涟漪消散）
                double amp = 0.55 * (1.0 - delay * 0.6 / totalTicks);
                // 粒子大小随时间衰减（消散）
                float scale = 1.8f * (1.0f - delay * 0.5f / totalTicks);
                for (int i = 0; i < ringCount; i++) {
                    double angle = frontAngle + Math.PI * 2 * i / ringCount;
                    double diff = normalizeAngle(angle - frontAngle);
                    if (Math.abs(diff) < frontGap) continue;
                    double px = center.x + Math.sin(angle) * radius;
                    double pz = center.z + Math.cos(angle) * radius;
                    // 上下波动：频率更高、相位变化更快（动画加快）
                    double py = center.y + Math.sin(angle * 4 + delay * 1.5) * amp;
                    int color = rippleColors[delay % rippleColors.length];
                    serverLevel.sendParticles(new DustParticleOptions(toVec(color), scale),
                            px, py, pz, 1, 0, 0, 0, 0);
                }
            }));
        }
    }

    /** 将角度规范化到 [-π, π] */
    private static double normalizeAngle(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    /** 将 RGB 整数颜色转换为粒子颜色向量 */
    private static Vector3f toVec(int rgb) {
        return new Vector3f(((rgb >> 16) & 0xFF) / 255.0f,
                ((rgb >> 8) & 0xFF) / 255.0f,
                (rgb & 0xFF) / 255.0f);
    }
}
