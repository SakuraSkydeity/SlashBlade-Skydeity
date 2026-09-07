package com.example.skydeityslash.entity;

import com.example.skydeityslash.SkydeitySlash;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 一笛清影化烟魂·水墨烟影（纯视觉粒子实体，无碰撞无渲染，trackingRange 0）。
 * 由 24 粒连续 Dust 组成半透明人形轮廓（水墨蓝），对周围 5 格内敌对生物造成 10% 最大生命魔法伤害；
 * 从天而降墨绿色山水粒子，并绽出一圈向外扩散的水墨涟漪。
 */
public class EntitySmokeShadow extends Entity {

    private static final Vector3f INK_BLUE = new Vector3f(0.55f, 0.87f, 1.0f);
    private static final Vector3f PALE = new Vector3f(0.90f, 0.97f, 1.0f);
    private static final Vector3f INK_GREEN = new Vector3f(0.32f, 0.84f, 0.58f);

    private Vec3 base = Vec3.ZERO;
    private int life = 0;
    private boolean dealt = false;
    private int ownerId = -1;

    public EntitySmokeShadow(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        // 纯服务端粒子实体，无需同步数据
    }

    public void init(Vec3 base, LivingEntity owner) {
        this.base = base;
        this.ownerId = owner.getId();
        this.setPos(base);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        base = new Vec3(tag.getDouble("bx"), tag.getDouble("by"), tag.getDouble("bz"));
        life = tag.getInt("life");
        dealt = tag.getBoolean("dealt");
        ownerId = tag.getInt("owner");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("bx", base.x); tag.putDouble("by", base.y); tag.putDouble("bz", base.z);
        tag.putInt("life", life);
        tag.putBoolean("dealt", dealt);
        tag.putInt("owner", ownerId);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!(level() instanceof ServerLevel sl)) { discard(); return; }
        life++;

        LivingEntity owner = ownerId >= 0 && level().getEntity(ownerId) instanceof LivingEntity le ? le : null;

        if (!dealt) {
            dealt = true;
            dealAoE(sl, owner);
        }

        // 24 粒人形轮廓（水墨蓝→透白，向上渐淡）
        Vec3[] pts = silhouettePoints();
        for (int i = 0; i < pts.length; i++) {
            float t = i / (float) (pts.length - 1);
            Vector3f col = lerp(INK_BLUE, PALE, t);
            sl.sendParticles(new DustParticleOptions(col, 0.7f - i * 0.015f),
                    pts[i].x, pts[i].y, pts[i].z, 1, 0, 0, 0, 0);
        }

        // 从天而降墨绿色山水粒子
        if (life % 2 == 0) {
            for (int k = 0; k < 6; k++) {
                double a = level().random.nextDouble() * Math.PI * 2;
                double r = 1.0 + level().random.nextDouble() * 2.6;
                double sy = base.y + 3.0 + level().random.nextDouble() * 2.5;
                sl.sendParticles(new DustParticleOptions(INK_GREEN, 0.5f),
                        base.x + Math.cos(a) * r, sy, base.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
        }

        // 绽开水墨涟漪圈（向外扩散）
        double rr = life * 0.35;
        if (rr < 4.6) {
            float fade = Math.max(0.05f, 0.8f - life * 0.03f);
            for (int s = 0; s < 16; s++) {
                double a = s / 16.0 * Math.PI * 2;
                Vector3f rc = lerp(INK_BLUE, PALE, fade);
                sl.sendParticles(new DustParticleOptions(rc, 0.5f),
                        base.x + Math.cos(a) * rr, base.y + 0.05, base.z + Math.sin(a) * rr, 1, 0, 0, 0, 0);
            }
        }

        if (life > 30) discard();
    }

    /** 对周围 5 格内敌对生物造成其最大生命 10% 的魔法伤害 */
    private void dealAoE(ServerLevel sl, LivingEntity owner) {
        AABB box = new AABB(base.x - 5, base.y - 2, base.z - 5, base.x + 5, base.y + 4, base.z + 5);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box,
                le -> le.isAlive() && !le.isSpectator() && le != owner && le instanceof Enemy)) {
            e.invulnerableTime = 0;
            if (owner != null) {
                SkydeitySlash.GameEvents.applyTrueDamage(e, owner, e.getMaxHealth() * 0.10f);
            } else {
                SkydeitySlash.GameEvents.applyTrueDamage(e, null, e.getMaxHealth() * 0.10f);
            }
        }
    }

    /** 简化人形骨架（自底向上），重采样为 24 粒 */
    private Vec3[] silhouettePoints() {
        double bd = 0.10, bw = 0.16;
        List<Vec3> sk = new ArrayList<>();
        // 双腿
        sk.add(base.add(-bd, 0, -bd)); sk.add(base.add(-0.05, 0.30, -bd)); sk.add(base.add(-bd, 0.58, -bd));
        sk.add(base.add(bd, 0, bd)); sk.add(base.add(0.05, 0.30, bd)); sk.add(base.add(bd, 0.58, bd));
        // 躯干两侧
        sk.add(base.add(-bd, 0.58, -bd)); sk.add(base.add(-bw, 0.80, -0.06)); sk.add(base.add(-bw, 0.98, 0));
        sk.add(base.add(bd, 0.58, bd)); sk.add(base.add(bw, 0.80, 0.06)); sk.add(base.add(bw, 0.98, 0));
        // 双臂
        sk.add(base.add(-bw, 0.80, -0.06)); sk.add(base.add(-0.34, 0.88, -0.05)); sk.add(base.add(-0.42, 0.70, -0.02));
        sk.add(base.add(bw, 0.80, 0.06)); sk.add(base.add(0.34, 0.88, 0.05)); sk.add(base.add(0.42, 0.70, 0.02));
        // 颈与头
        sk.add(base.add(-bw, 0.98, 0)); sk.add(base.add(0, 1.12, 0)); sk.add(base.add(0, 1.30, 0));
        sk.add(base.add(bw, 0.98, 0));

        Vec3[] arr = sk.toArray(new Vec3[0]);
        Vec3[] res = new Vec3[24];
        for (int i = 0; i < 24; i++) {
            double tt = (double) i / 23 * (arr.length - 1);
            int a = (int) tt;
            double f = tt - a;
            res[i] = (a + 1 < arr.length) ? arr[a].lerp(arr[a + 1], f) : arr[arr.length - 1];
        }
        return res;
    }

    private static Vector3f lerp(Vector3f a, Vector3f b, float t) {
        return new Vector3f(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }
}