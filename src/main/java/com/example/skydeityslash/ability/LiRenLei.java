package com.example.skydeityslash.ability;

import com.example.skydeityslash.entity.EntityInkFoxField;
import com.example.skydeityslash.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 「离人泪·曲断魂」剑技逻辑：sakurafox 默认剑技，朝准星方向释放「墨渊·蝶舞九天」墨蝶结界。
 */
public class LiRenLei {
    public static void doLiRenLei(LivingEntity user) {
        if (!(user instanceof ServerPlayer player)) return;
        Level level = player.level();
        if (level.isClientSide) return;

        Entity target = null;
        if (player.getVehicle() != null) target = player.getVehicle();
        else {
            var hit = player.pick(30.0D, 1.0F, false);
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) target = ((net.minecraft.world.phys.EntityHitResult) hit).getEntity();
        }
        if (!(target instanceof LivingEntity le) || !le.isAlive()) target = null;

        Vec3 center = (target instanceof LivingEntity te)
                ? te.position()
                : player.position().add(player.getLookAngle().x * 6.0, 0, player.getLookAngle().z * 6.0);

        float damage = 20.0F;
        EntityInkFoxField.spawn(level, player, center, damage);
        // 播放「负心者当诛」人声：挂在此处（组合技步骤 2）是 1.20 下 SA 一定执行的点，
        // 比 PerformSlashArtEvent 更可靠，保证放 SA 必有人声且每次只播一遍
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.FUXINZHE_DANGZHU.get(), SoundSource.PLAYERS, 2.0f, 1.0f);
    }
}