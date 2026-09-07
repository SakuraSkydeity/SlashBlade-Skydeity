package com.example.skydeityslash.entity;

import com.example.skydeityslash.registry.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * 「狐」剑技：剑体始觉 + 鸣雷神 组合结界。
 * 服务端自驱动时间轴：伤害波次/减速/拉拽/击飞/音效/粒子全部融合并去重。
 * 客户端由 RenderFoxEnlightenedField 用 1.21.1 原生 RenderType 重建多层视觉。
 */
public class EntityFoxEnlightenedField extends Entity {
    public static final int LIFETIME = 64;

    private int ownerId = -1;
    private float baseDamage = 1.0f;
    private float yaw;

    public EntityFoxEnlightenedField(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public static EntityFoxEnlightenedField spawn(Level level, LivingEntity owner, Vec3 center, float damage) {
        EntityFoxEnlightenedField field = new EntityFoxEnlightenedField(ModEntities.FOX_FIELD.get(), level);
        field.ownerId = owner.getId();
        field.baseDamage = damage;
        field.yaw = owner.getYRot();
        field.setPos(center.x, center.y, center.z);
        level.addFreshEntity(field);
        return field;
    }

    public float getYaw() { return yaw; }
    public LivingEntity getOwner() {
        return level().getEntity(ownerId) instanceof LivingEntity le ? le : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        LivingEntity owner = getOwner();
        if (owner == null || !owner.isAlive()) { discard(); return; }
        ServerLevel server = (ServerLevel) level();
        int t = tickCount;
        spawnParticles(server, t);
        // 伤害波次：剑体始觉 每 4 tick 一波（8~32），鸣雷神 的闪电击并入、仅表现不重复计数
        if (t >= 8 && t <= 32 && ((t - 8) & 3) == 0) {
            float weight = t == 8 ? 0.38f : t == 32 ? 1.08f : 0.56f;
            hit(owner, baseDamage * weight, t == 32);
        }
        if (t >= LIFETIME) discard();
    }

    private void hit(LivingEntity owner, float damage, boolean finisher) {
        AABB area = new AABB(getX() - 8.2, getY() - 0.65, getZ() - 8.2,
                getX() + 8.2, getY() + 4.8, getZ() + 8.2);
        List<LivingEntity> found = level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e != owner && e.isAlive() && !e.isSpectator());
        Vec3 forward = flatForward();
        Vec3 fieldCenter = position().add(0, 1.1, 0);
        Vec3 pullCenter = fieldCenter.add(forward.scale(2.15));
        DamageSource src = owner instanceof Player p
                ? level().damageSources().playerAttack(p)
                : level().damageSources().mobAttack(owner);
        for (LivingEntity target : found) {
            target.invulnerableTime = 0;
            Vec3 center = target.getBoundingBox().getCenter();
            double horizontal = Math.sqrt(Math.pow(center.x - getX(), 2) + Math.pow(center.z - getZ(), 2));
            float falloff = net.minecraft.util.Mth.clamp((float) (1.0 - horizontal / 11.07), 0.62f, 1.0f);
            if (!target.hurt(src, damage * falloff)) continue;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    finisher ? 38 : 26, (finisher ? 4 : 3) - 1, false, false));
            Vec3 motion;
            if (finisher) motion = forward.scale(0.52).add(0, 0.32, 0);
            else {
                Vec3 pull = pullCenter.subtract(center);
                motion = pull.lengthSqr() > 1.0E-6 ? pull.normalize().scale(0.13) : Vec3.ZERO;
                motion = motion.add(0, tickCount >= 20 ? 0.22 : 0.13, 0);
            }
            Vec3 mv = target.getDeltaMovement();
            double s = finisher ? 0.36 : 0.52;
            target.setDeltaMovement(mv.x * s + motion.x, mv.y * s + motion.y, mv.z * s + motion.z);
        }
    }

    private void spawnParticles(ServerLevel server, int t) {
        // 金环展开（剑体始觉）+ 紫环（鸣雷神）
        if (t >= 2 && t <= 40) {
            float open = smoother(net.minecraft.util.Mth.clamp((t - 2f) / 24f, 0, 1));
            int count = 10 + (int) (open * 26);
            for (int i = 0; i < count; i++) {
                double ang = random.nextDouble() * Math.PI * 2 + t * 0.36;
                double radius = 1.0 + open * 6.0 + random.nextDouble() * 0.5;
                double y = 0.1 + random.nextDouble() * (0.5 + open * 2.4);
                dust(server, getX() + Math.cos(ang) * radius, getY() + y, getZ() + Math.sin(ang) * radius,
                        0.55f + 0.45f * open, 0.20f + 0.66f * open, 1.0f);
                if ((i & 3) == 0)
                    server.sendParticles(ParticleTypes.END_ROD, getX() + Math.cos(ang) * radius, getY() + y,
                            getZ() + Math.sin(ang) * radius, 0,
                            -Math.sin(ang) * 0.06, 0.018, Math.cos(ang) * 0.06, 0.035);
            }
            for (int i = 0; i < count / 2; i++) {
                double ang = random.nextDouble() * Math.PI * 2 - t * 0.22;
                double radius = (1.6 + open * 7.4) * 0.8 + random.nextDouble() * 0.6;
                double y = 0.12 + random.nextDouble() * (0.6 + open * 2.6);
                dust(server, getX() + Math.cos(ang) * radius, getY() + y, getZ() + Math.sin(ang) * radius,
                        0.62f, 0.52f, 0.95f);
            }
        }
        // 前方爆鸣光团（剑体始觉）+ 闪电星芒（鸣雷神）
        if (t == 6 || t == 12 || t == 20 || t == 26 || t == 32) {
            Vec3 c = position().add(flatForward().scale(2.35)).add(0, 1.25, 0);
            server.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, t == 32 ? 34 : 18, 0.78, 0.58, 0.78, 0.16);
            int n = t == 32 ? 42 : 24;
            for (int i = 0; i < n; i++)
                dust(server, c.x + (random.nextDouble() - 0.5) * 1.4, c.y + (random.nextDouble() - 0.5) * 0.96,
                        c.z + (random.nextDouble() - 0.5) * 1.4, 1.0f, 0.86f, 1.0f);
            for (int i = 0; i < 14; i++)
                dust(server, c.x + (random.nextDouble() - 0.5) * 1.6, c.y + (random.nextDouble() - 0.5) * 1.6,
                        c.z + (random.nextDouble() - 0.5) * 1.6, 0.7f, 0.6f, 1.0f);
        }
        // 余辉消散
        if (t >= 34 && (t & 1) == 0) {
            Vec3 f = flatForward();
            Vec3 right = new Vec3(-f.z, 0, f.x);
            for (int i = 0; i < 5; i++) {
                double ahead = 0.8 + random.nextDouble() * 5.4;
                double side = (random.nextDouble() - 0.5) * 5.8;
                double y = 0.45 + random.nextDouble() * 2.35;
                Vec3 p = position().add(f.scale(ahead)).add(right.scale(side)).add(0, y, 0);
                Vec3 motion = f.scale(0.04 + random.nextDouble() * 0.09)
                        .add(right.scale((random.nextDouble() - 0.5) * 0.10));
                server.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 0,
                        motion.x, 0.06, motion.z, 0.035);
            }
        }
    }

    private void dust(ServerLevel server, double x, double y, double z, float r, float g, float b) {
        server.sendParticles(new DustParticleOptions(new Vector3f(Math.max(0.001f, r), Math.max(0.001f, g), Math.max(0.001f, b)), 1.0f),
                x, y, z, 0, 0, 0, 0, 1);
    }

    private static float smoother(float t) {
        t = net.minecraft.util.Mth.clamp(t, 0, 1);
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private Vec3 flatForward() {
        double yy = Math.toRadians(yaw);
        return new Vec3(-net.minecraft.util.Mth.sin((float) yy), 0, net.minecraft.util.Mth.cos((float) yy)).normalize();
    }

    @Override public boolean isPickable() { return false; }
    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        ownerId = tag.getInt("OwnerId");
        baseDamage = tag.getFloat("Damage");
        yaw = tag.getFloat("Yaw");
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("OwnerId", ownerId);
        tag.putFloat("Damage", baseDamage);
        tag.putFloat("Yaw", yaw);
    }
}