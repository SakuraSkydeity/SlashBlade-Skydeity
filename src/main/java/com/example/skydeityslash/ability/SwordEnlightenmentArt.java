package com.example.skydeityslash.ability;

import com.example.skydeityslash.entity.EntityFoxEnlightenedField;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 「剑体始觉」剑技逻辑：仅在玩家前方释放特效结界供查看（无伤害）。
 */
public class SwordEnlightenmentArt {
    public static void doEnlightenment(LivingEntity user) {
        if (!(user instanceof ServerPlayer player)) return;
        Level level = player.level();
        if (level.isClientSide) return;

        Vec3 center = player.position().add(player.getLookAngle().x * 6.0, 0, player.getLookAngle().z * 6.0);
        EntityFoxEnlightenedField.spawn(level, player, center, 0.0f);
    }

    private SwordEnlightenmentArt() {}
}