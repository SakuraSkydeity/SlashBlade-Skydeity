package com.example.skydeityslash.entity;

import com.example.skydeityslash.SkydeitySlash;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.entity.Projectile;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 「璃月」胡桃円刀刀光：复刻原版円刀的环形刀光视觉，
 * 每道刀光对周围 4 格内生物造成一次 520 点真伤（魔法伤害、无视护甲）。
 */
public class EntityHutaoCircleSlash extends EntitySlashEffect {
    /** 单段真伤伤害 */
    public static final double DAMAGE = 520.0;
    /** 判定范围（格） */
    private static final double RANGE = 4.0;
    /** 已命中目标（每目标仅命中一次） */
    private final IntOpenHashSet alreadyHits = new IntOpenHashSet();

    public EntityHutaoCircleSlash(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
    }

    /** 与原版円刀一致：刀光本身不播放音效 */
    @Override
    public net.minecraft.sounds.SoundEvent getSlashSound() {
        return SoundEvents.EMPTY;
    }

    /** 返回 null 以抑制原版内置的范围近战判定，伤害改由 hitCheck 精确结算 */
    @Override
    public Entity getShooter() {
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount % 2 == 0) {
            hitCheck();
        }
    }

    /** 对范围内生物造成一次 520 点真伤 */
    private void hitCheck() {
        Entity owner = getOwner();
        AABB box = getBoundingBox().inflate(RANGE);
        List<LivingEntity> targets = level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != owner && e.isAlive() && !e.isSpectator()
                        && e.canBeHitByProjectile() && !alreadyHits.contains(e.getId()));
        if (targets.isEmpty()) return;
        if (owner instanceof Player player) {
            targets.removeIf(t -> t instanceof Player targetPlayer && !player.canHarmPlayer(targetPlayer));
        }
        for (LivingEntity target : targets) {
            alreadyHits.add(target.getId());
            SkydeitySlash.GameEvents.applyTrueDamage(target, owner, (float) DAMAGE);
        }
    }
}
