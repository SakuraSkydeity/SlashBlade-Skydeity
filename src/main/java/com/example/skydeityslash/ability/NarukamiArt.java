package com.example.skydeityslash.ability;

import com.example.skydeityslash.entity.EntitySevenThunders21;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 「鸣雷神」剑技逻辑：在玩家前方释放紫霆雷狱结界供查看与伤害（原版 NarukamiDivinity 视觉）。
 */
public class NarukamiArt {
    public static void doNarukami(LivingEntity user) {
        if (!(user instanceof ServerPlayer player)) return;
        Level level = player.level();
        if (level.isClientSide) return;

        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        Vec3 dir = flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 target = player.position().add(dir.scale(6.0)).add(0, 1.0, 0);
        EntitySevenThunders21.spawn(level, player, target, dir, 1.0f);
    }

    private NarukamiArt() {}
}