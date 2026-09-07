package com.example.skydeityslash.ability;

import com.example.skydeityslash.entity.EntityXuanfengWave;
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
import net.minecraft.world.phys.Vec3;

import java.awt.Color;

/**
 * 「翾风回雪」剑技逻辑。
 * 从玩家斜后方 8 格高处一道白色刀波斜劈而下造成 520 伤害，
 * 划过路径留下白色粒子（逐渐消失），划过区域每帧造成 52 点伤害，释放时刀身特效变为银白色。
 */
public class XuanfengSlashArt {
    /** 银白色 */
    public static final int SILVER_COLOR = 0xC0C0C0;
    /** 白色 */
    public static final int WHITE_COLOR = 0xFFFFFF;
    /** 刀波命中伤害 */
    public static final double WAVE_DAMAGE = 520.0;

    public static void doXuanfeng(LivingEntity user) {
        if (!(user instanceof ServerPlayer player)) return;
        Level level = player.level();
        if (level.isClientSide) return;
        ISlashBladeState state = player.getMainHandItem().getCapability(CapabilitySlashBlade.BLADESTATE, null).orElse(null);
        if (state == null) return;

        state.setEffectColor(new Color(SILVER_COLOR));

        // 从玩家斜后方 8 格高处一道白色刀波斜劈而下（锁定目标则朝目标实时位置劈去），划过路径留下白色粒子
        Vec3 behind = player.position()
                .add(player.getLookAngle().scale(-3.0))
                .add(0.0, 8.0, 0.0);
        Entity tgt = state.getTargetEntity(level);
        Vec3 toFront;
        if (tgt instanceof LivingEntity tl && tl.isAlive() && !tl.isRemoved()) {
            // 锁定目标：刀波朝目标中心斜劈
            toFront = tl.getBoundingBox().getCenter().subtract(behind).normalize();
        } else {
            toFront = player.getLookAngle().scale(0.8).add(0.0, -0.6, 0.0).normalize();
        }
        EntityXuanfengWave wave = new EntityXuanfengWave(RegistryEvents.Drive, level);
        level.addFreshEntity(wave);
        wave.setPos(behind.x, behind.y, behind.z);
        wave.setDamage(WAVE_DAMAGE);
        wave.setSpeed(0.8F);
        wave.setColor(WHITE_COLOR);
        wave.setBaseSize(2.0F);
        wave.setOwner(player);
        wave.setRotationRoll(90.0F);
        wave.setIsCritical(false);
        wave.setKnockBack(KnockBacks.cancel);
        wave.setLifetime(20.0F);
        wave.shoot(toFront.x, toFront.y, toFront.z, wave.getSpeed(), 0.0F);

        // 天降幻影剑：共 2 轮。每轮围绕锁定目标（无锁定则准星前方 6 格）生成 8 把白色幻影剑，
        // 从目标上方 6 格、半径 2.6 的环形向目标扎落，每把 52 真伤（放慢速度，用回原版幻影剑形态避免下落模型变形）
        if (level instanceof ServerLevel serverLevel) {
            spawnSkySwords(player, level, state);   // 第 1 轮
            serverLevel.getServer().tell(new TickTask(serverLevel.getServer().getTickCount() + 10,
                    () -> spawnSkySwords(player, level, state)));  // 第 2 轮（略延时错开）
        }
    }

    /** 天降幻影剑一轮：围绕锁定目标/准星前方，从上方 6 格、半径 2.6 环形扎落 8 把白剑，每把 52 真伤 */
    private static void spawnSkySwords(ServerPlayer player, Level level, ISlashBladeState state) {
        Vec3 look = player.getLookAngle();
        Entity tgt = state.getTargetEntity(level);
        final Vec3 aim;
        if (tgt != null && tgt.isAlive() && !tgt.isRemoved()) {
            aim = tgt.position().add(0.0, tgt.getEyeHeight() * 0.3, 0.0);
        } else {
            aim = player.getEyePosition().add(look.scale(6.0));
        }
        for (int i = 0; i < 8; i++) {
            double ang = (i / 8.0) * Math.PI * 2.0;
            Vec3 start = aim.add(Math.cos(ang) * 2.6, 6.0, Math.sin(ang) * 2.6);
            EntityAbstractSummonedSword sword = new EntityAbstractSummonedSword(RegistryEvents.SummonedSword, level);
            sword.setPos(start);
            sword.setShooter(player);
            sword.setColor(WHITE_COLOR);
            sword.setRoll(0.0F);
            sword.setDamage(52.0);
            Vec3 dir = aim.subtract(start);
            sword.shoot(dir.x, dir.y, dir.z, 1.6F, 0.0F);
            level.addFreshEntity(sword);
        }
    }
}
