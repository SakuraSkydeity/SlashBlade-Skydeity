package com.example.skydeityslash.entity;

import com.example.skydeityslash.SkydeitySlash;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 雨间蝶舞·水墨蝶（纯视觉粒子实体，无碰撞无渲染，trackingRange 0）。
 * 蝶体 = 3 粒首尾相衔的 Dust（墨绿→月白渐变），沿正弦轨迹绕玩家盘旋半圈后扑向最近敌人；
 * 命中时炸成 6 片细烟羽（青→白）+ 甩出 4 条向上扬起的水墨绸带，并对目标造成最大生命 10% 的魔法伤害。
 */
public class EntityInkButterfly extends Entity {

    private static final Vector3f INK_GREEN = new Vector3f(0.32f, 0.84f, 0.58f);
    private static final Vector3f MOON_WHITE = new Vector3f(0.88f, 0.97f, 0.96f);
    private static final Vector3f INK_BLUE = new Vector3f(0.55f, 0.87f, 1.0f);
    private static final Vector3f PALE_WHITE = new Vector3f(0.95f, 0.99f, 1.0f);

    private Vec3 ownerPos = Vec3.ZERO;   // 盘旋基准（玩家脚部）
    private Vec3 startPos = Vec3.ZERO;
    private int targetId = -1;
    private Vec3 targetPos = Vec3.ZERO;
    private int life = 0;
    private boolean dived = false;
    private Vec3 prev = null;

    public EntityInkButterfly(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        // 纯服务端粒子实体，无需同步数据
    }

    public void init(Vec3 owner, LivingEntity target) {
        this.ownerPos = owner;
        this.targetId = target.getId();
        this.targetPos = target.position().add(0, target.getBbHeight() * 0.5f, 0);
        Vec3 c = owner.add(0, 2.1, 0);
        this.startPos = c.add(0, 0, 1.9);
        this.setPos(startPos.x, startPos.y, startPos.z);
        this.prev = startPos;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerPos = new Vec3(tag.getDouble("o"), tag.getDouble("oy"), tag.getDouble("oz"));
        targetPos = new Vec3(tag.getDouble("tx"), tag.getDouble("ty"), tag.getDouble("tz"));
        targetId = tag.getInt("tid");
        life = tag.getInt("life");
        dived = tag.getBoolean("dived");
        prev = position();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("o", ownerPos.x); tag.putDouble("oy", ownerPos.y); tag.putDouble("oz", ownerPos.z);
        tag.putDouble("tx", targetPos.x); tag.putDouble("ty", targetPos.y); tag.putDouble("tz", targetPos.z);
        tag.putInt("tid", targetId);
        tag.putInt("life", life);
        tag.putBoolean("dived", dived);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!(level() instanceof ServerLevel sl)) { discard(); return; }
        life++;

        LivingEntity target = targetId >= 0 && level().getEntity(targetId) instanceof LivingEntity le
                ? le : null;
        if (target != null && target.isAlive()) {
            targetPos = target.position().add(0, target.getBbHeight() * 0.5f, 0);
        }

        Vec3 ctr = ownerPos.add(0, 2.1, 0);
        Vec3 here;
        if (!dived) {
            // 盘旋阶段：绕基准(origin 右手侧起)转半圈（0→π）
            double a = Math.PI * Math.min(life / 18.0, 1.0);
            double bob = Math.sin(life * 0.55) * 0.5;
            here = ctr.add(Math.cos(a) * 1.9, bob, Math.sin(a) * 1.9);
            if (life >= 18) dived = true;
        } else {
            // 扑击阶段：当前点→目标点的缓动直线（带轻微的蛇形摆动）
            Vec3 from = position();
            Vec3 to = targetPos;
            double p = Math.min((life - 18) / 10.0, 1.0);
            double e = 1 - Math.pow(1 - p, 2);
            here = from.lerp(to, e);
            double wob = Math.sin(life * 0.9) * 0.12;
            here = here.add(0, 0, 0).add(wob, Math.cos(life * 0.6) * 0.08, 0);
            if (here.distanceToSqr(to) < 0.6) {
                explode(sl, to, target);
                discard();
                return;
            }
        }
        setPos(here.x, here.y, here.z);

        // 蝶体：沿运动切线首尾 3 粒 Dust（墨绿→月白），微升摆尾
        Vec3 vel = here.subtract(prev);
        Vec3 dir = vel.lengthSqr() > 1e-6 ? vel.normalize() : new Vec3(0, 0.2, 0);
        Vec3 d0 = here.subtract(dir.scale(0.05));
        Vec3 d1 = here;
        Vec3 d2 = here.add(dir.scale(0.05));
        dust(sl, d0, INK_GREEN, 0.55f);
        dust(sl, d1, new Vector3f(0.60f, 0.90f, 0.77f), 0.7f);
        dust(sl, d2, MOON_WHITE, 0.8f);
        prev = here;

        if (life > 40) discard(); // 兜底：找不到目标不无限存活
    }

    /** 命中爆炸：6 片细烟羽（青→白）+ 4 条向上扬起的水墨绸带，并造成 10% 最大生命魔法伤害 */
    private void explode(ServerLevel sl, Vec3 at, LivingEntity target) {
        at = at.add(0, 0.2, 0);
        // 6 片细烟羽：径向 + 上飘，青→白，末端细
        for (int k = 0; k < 6; k++) {
            double a = k / 6.0 * Math.PI * 2;
            Vec3 d = new Vec3(Math.cos(a), 0.62, Math.sin(a)).normalize();
            Vec3 c = at;
            for (int i = 0; i < 5; i++) {
                float t = i / 4f;
                c = at.add(d.scale(0.35f + i * 0.32)).add(0, i * 0.12, 0);
                Vector3f col = lerp(INK_BLUE, PALE_WHITE, t);
                dust(sl, c, col, (1 - t) * 0.65f + 0.2f);
            }
        }
        // 4 条向上扬起的水墨绸带：从爆点斜向上甩出的弧线，墨绿→水墨蓝
        for (int k = 0; k < 4; k++) {
            double a = (k / 4.0 * Math.PI * 2) + 0.39;
            Vec3 along = new Vec3(Math.cos(a), 1.0, Math.sin(a)).normalize();
            for (int i = 0; i < 7; i++) {
                float t = i / 6f;
                double up = Math.sin(t * Math.PI) * 1.1;
                Vec3 c = at.add(along.scale(0.35f + t * 1.4)).add(0, up, 0);
                Vector3f col = lerp(INK_GREEN, INK_BLUE, t);
                dust(sl, c, col, (1 - t) * 0.7f + 0.15f);
            }
        }
        // 命中伤害：直接扣除最大生命 10% 的真伤（无视护甲/抗性/限伤）
        if (target != null && target.isAlive()) {
            target.invulnerableTime = 0;
            SkydeitySlash.GameEvents.applyTrueDamage(target, null, target.getMaxHealth() * 0.10f);
        }
    }

    /** 求 12 格内距玩家最近的敌对生物（供生成时确定扑击目标） */
    public static LivingEntity nearestHostile(LivingEntity player, LivingEntity fallback) {
        AABB box = player.getBoundingBox().inflate(12.0);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box,
                t -> t.isAlive() && t != player && !t.isSpectator())) {
            double d = player.distanceToSqr(e);
            if (d < bestD) { bestD = d; best = e; }
        }
        return best != null ? best : fallback;
    }

    private void dust(ServerLevel sl, Vec3 p, Vector3f col, float size) {
        sl.sendParticles(new DustParticleOptions(clamp(col), size), p.x, p.y, p.z, 1, 0, 0, 0, 0);
    }

    private static Vector3f lerp(Vector3f a, Vector3f b, float t) {
        return new Vector3f(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }

    private static Vector3f clamp(Vector3f v) {
        return new Vector3f(Math.min(1, Math.max(0, v.x)), Math.min(1, Math.max(0, v.y)), Math.min(1, Math.max(0, v.z)));
    }
}