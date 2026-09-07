package com.example.skydeityslash.entity;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 笛烟和声魂断绝·笛烟一线（纯视觉粒子实体，trackingRange 0）。
 * 从刀尖甩出一道水墨细线（3~5 粒首尾相衔 Dust，山水翠绿→深墨），沿攻击方向泼出并贯穿目标，墨尾带飞白断点。
 * 命中即对目标造成 12% 最大生命魔法伤害（magic 不吃护甲，无视无敌）——由触发事件负责，本实体只负责画线。
 */
public class EntityInkBloom extends Entity {

    private static final Vector3f INK_GREEN = new Vector3f(0.32f, 0.84f, 0.58f);  // 山水翠绿
    private static final Vector3f DEEP_INK = new Vector3f(0.05f, 0.08f, 0.07f);   // 深墨

    private Vec3 from = Vec3.ZERO;      // 刀尖起点
    private Vec3 dir = Vec3.ZERO;       // 攻击方向（单位）
    private Vec3 endd = Vec3.ZERO;      // 贯穿终点（越过目标）
    private int life = 0;

    public EntityInkBloom(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        // 纯服务端粒子实体，无需同步数据
    }

    public void init(Vec3 from, LivingEntity owner, LivingEntity target) {
        this.from = from;
        Vec3 tp = target.position().add(0, target.getBbHeight() * 0.5f, 0);
        Vec3 dv = tp.subtract(from);
        this.dir = dv.lengthSqr() > 1e-8 ? dv.normalize() : new Vec3(0, 0, 1);
        this.endd = tp.add(this.dir.scale(0.9));          // 贯穿目标后再延伸一小段
        this.setPos(from);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        from = new Vec3(tag.getDouble("fx"), tag.getDouble("fy"), tag.getDouble("fz"));
        dir = new Vec3(tag.getDouble("dx"), tag.getDouble("dy"), tag.getDouble("dz"));
        endd = new Vec3(tag.getDouble("ex"), tag.getDouble("ey"), tag.getDouble("ez"));
        life = tag.getInt("life");
        setPos(from);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("fx", from.x); tag.putDouble("fy", from.y); tag.putDouble("fz", from.z);
        tag.putDouble("dx", dir.x); tag.putDouble("dy", dir.y); tag.putDouble("dz", dir.z);
        tag.putDouble("ex", endd.x); tag.putDouble("ey", endd.y); tag.putDouble("ez", endd.z);
        tag.putInt("life", life);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!(level() instanceof ServerLevel sl)) { discard(); return; }
        life++;

        if (life > 12) { discard(); return; }

        // 水墨细线沿攻击方向贯穿，首尾相衔 3~5 粒，山水翠绿 → 深墨
        double prog = Math.min(life / 12.0, 1.0);
        Vec3 head = from.lerp(endd, prog);
        Vec3 d2 = dir.scale(0.14);
        dust(sl, head.subtract(d2), INK_GREEN, 0.6f);
        dust(sl, head, lerp(INK_GREEN, DEEP_INK, 0.5f), 0.75f);
        dust(sl, head.add(d2), lerp(INK_GREEN, DEEP_INK, 0.85f), 0.55f);
        // 墨尾带飞白断点：尾端随机空拍，营造飞白感
        if (sl.random.nextFloat() < 0.55f) {
            dust(sl, head.add(dir.scale(0.30)), DEEP_INK, 0.35f);
        }
        setPos(head.x, head.y, head.z);
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