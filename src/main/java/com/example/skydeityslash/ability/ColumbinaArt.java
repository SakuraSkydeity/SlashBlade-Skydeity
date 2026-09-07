package com.example.skydeityslash.ability;

import com.example.skydeityslash.SkydeitySlash;
import com.example.skydeityslash.particle.VanillaEffectSpawner;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 「诺德卡莱」为夜增辉与君遥伴剑技：在目标处召唤 12 道天蓝色拔刀剑剑气从天而降，每道造成 52 点伤害。
 */
public class ColumbinaArt {
    private static final int BLADE_COUNT = 12;
    private static final double DAMAGE = 52.0;

    public static void doColumbina(LivingEntity user) {
        if (!(user instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;
        if (!(player.level() instanceof ServerLevel server)) return;

        // 锁定目标：优先采用 SlashBlade 的锁定目标（shift 锁定时设定），无锁定则回退到骑乘/准星
        Level ilevel = player.level();
        ISlashBladeState state = player.getMainHandItem().getCapability(CapabilitySlashBlade.BLADESTATE, null).orElse(null);
        Entity target = (state != null) ? state.getTargetEntity(ilevel) : null;
        if (!(target instanceof LivingEntity) || !target.isAlive()) {
            target = null;
            if (player.getVehicle() != null) target = player.getVehicle();
            else {
                var hit = player.pick(30.0D, 1.0F, false);
                if (hit.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) target = ((net.minecraft.world.phys.EntityHitResult) hit).getEntity();
            }
            if (!(target instanceof LivingEntity le) || !le.isAlive()) target = null;
        }

        Vec3 targetPos = (target instanceof LivingEntity te)
                ? te.position().add(0, te.getBbHeight() * 0.5, 0)
                : player.position().add(player.getLookAngle().x * 6.0, 0.9, player.getLookAngle().z * 6.0);

        // 保留原 SA 粒子第一段：目标处释放 frostflourish 螺旋冰花
        VanillaEffectSpawner.frostFlourish(server, targetPos);

        // 12 道天蓝色剑气从天而降：目标上方随机散布，直直落下，因撞到目标/地面而消散
        for (int i = 0; i < BLADE_COUNT; i++) {
            double a = server.random.nextDouble() * Math.PI * 2;
            double off = 0.4 + server.random.nextDouble() * 1.4;
            double sx = targetPos.x + Math.cos(a) * off;
            double sz = targetPos.z + Math.sin(a) * off;
            double sy = targetPos.y + 18.0 + server.random.nextDouble() * 5.0;

            EntityDrive blade = new EntityDrive(SlashBlade.RegistryEvents.Drive, server);
            blade.setPos(sx, sy, sz);
            blade.setBaseSize(0.5f);            // 小的那种剑气
            blade.setDamage(0.01);              // 原生伤害近乎 0：命中伤害统一由 homing 锁定按 52 真伤结算
            blade.setColor(0x87CEEB);            // 天蓝
            blade.setShooter(player);
            blade.setSpeed(0.9f);
            blade.setLifetime(40.0f);            // 保证落程内有足够存活时间，命中即消散
            blade.setKnockBack(KnockBacks.cancel); // 击退统一由通用工具处理，避免重复
            blade.setRotationRoll(server.random.nextFloat() * 360f);
            blade.shoot(0.0, -1.0, 0.0, blade.getSpeed(), 0.0f);
            server.addFreshEntity(blade);
            // 锁定目标：剑气每 tick 追踪该目标的实时位置，命中即消散
            if (target instanceof LivingEntity lock && lock.isAlive()) {
                SkydeitySlash.GameEvents.registerColumbinaHoming(blade.getId(), lock.getId());
            }
        }

        // 保留原 SA 粒子第二段：在玩家身边延时释放 frost_nova 冰霜新星（一个放完再放另一个）
        SkydeitySlash.GameEvents.scheduleColumbinaNova(player);
    }
}