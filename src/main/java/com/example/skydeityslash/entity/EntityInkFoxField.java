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
 * 「墨渊·蝶舞九天」实体（sakurafox 新 SA）。
 * 服务端自驱动时间轴：墨蝶双翼展开 + 墨环扩散 + 上冲墨柱，对前方区域内生物造成魔法伤害与减速。
 * 客户端由 RenderInkFoxField 用 POSITION_COLOR 加法混合重建多层视觉（亮芯 + 淡外包）。
 */
public class EntityInkFoxField extends Entity {
    public static final int LIFETIME = 64;

    private static final Vector3f TEAL = new Vector3f(0.30f, 0.74f, 0.48f);    // 山水绿
    private static final Vector3f LITE = new Vector3f(0.46f, 0.88f, 0.64f);    // 翠玉
    private static final Vector3f MOON = new Vector3f(0.92f, 0.98f, 0.95f);     // 月白

    private int ownerId = -1;
    private float baseDamage = 1.0f;
    private float yaw;

    public EntityInkFoxField(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public static EntityInkFoxField spawn(Level level, LivingEntity owner, Vec3 center, float damage) {
        EntityInkFoxField field = new EntityInkFoxField(ModEntities.INK_FOX_FIELD.get(), level);
        field.ownerId = owner.getId();
        field.baseDamage = damage;
        field.yaw = owner.getYRot();
        field.setPos(center.x, center.y, center.z);
        level.addFreshEntity(field);
        return field;
    }

    public float getYaw() { return yaw; }
    public float getBaseDamage() { return baseDamage; }
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
        // 伤害波次：墨蝶振翅 5 波（10~42），最后一波受击把敌人拉向中心
        if (t >= 10 && t <= 42 && ((t - 10) & 4) == 0) {
            float weight = t == 10 ? 0.40f : t == 42 ? 1.15f : 0.62f;
            hit(owner, baseDamage * weight, t == 42);
        }
        if (t >= LIFETIME) discard();
    }

    private void hit(LivingEntity owner, float damage, boolean finisher) {
        AABB area = new AABB(getX() - 7.5, getY() - 0.6, getZ() - 7.5,
                getX() + 7.5, getY() + 4.6, getZ() + 7.5);
        List<LivingEntity> found = level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e != owner && e.isAlive() && !e.isSpectator());
        Vec3 fieldCenter = position().add(0, 1.05, 0);
        DamageSource src = owner instanceof Player p
                ? level().damageSources().playerAttack(p)
                : level().damageSources().mobAttack(owner);
        for (LivingEntity target : found) {
            target.invulnerableTime = 0;
            Vec3 center = target.getBoundingBox().getCenter();
            double horizontal = Math.sqrt(Math.pow(center.x - getX(), 2) + Math.pow(center.z - getZ(), 2));
            float falloff = net.minecraft.util.Mth.clamp((float) (1.0 - horizontal / 9.5), 0.60f, 1.0f);
            if (!target.hurt(src, damage * falloff)) continue;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    finisher ? 40 : 24, (finisher ? 3 : 2) - 1, false, false));
            if (finisher) {
                Vec3 pull = fieldCenter.subtract(center);
                Vec3 dir = pull.lengthSqr() > 1.0E-6 ? pull.normalize().scale(0.30) : Vec3.ZERO;
                Vec3 mv = target.getDeltaMovement();
                target.setDeltaMovement(mv.x * 0.30 + dir.x, mv.y * 0.30 + 0.18, mv.z * 0.30 + dir.z);
                target.hasImpulse = true;
            }
        }
    }

    private void spawnParticles(ServerLevel server, int t) {
        // 山环扩散墨带（山水绿），近浓远淡
        if (t >= 2 && t <= 42) {
            float open = smoother(net.minecraft.util.Mth.clamp((t - 2f) / 26f, 0, 1));
            int count = 8 + (int) (open * 24);
            for (int i = 0; i < count; i++) {
                double ang = random.nextDouble() * Math.PI * 2 + t * 0.34;
                double radius = 1.2 + open * 5.6 + random.nextDouble() * 0.5;
                double y = 0.1 + random.nextDouble() * (0.5 + open * 2.2);
                dust(server, getX() + Math.cos(ang) * radius, getY() + y, getZ() + Math.sin(ang) * radius,
                        0.30f + 0.30f * (float) open, 0.74f, 0.48f);
            }
            // 翠玉峰脊点缀沿扇面
            for (int i = 0; i < count / 3; i++) {
                double side = (random.nextDouble() * 2 - 1) * (2.2 + open * 3.4);
                double ahead = random.nextDouble() * (1.6 + open * 3.2);
                Vec3 f = flatForward();
                Vec3 right = new Vec3(-f.z, 0, f.x);
                Vec3 p = position().add(f.scale(ahead)).add(right.scale(side)).add(0, 0.15 + random.nextDouble() * 1.9, 0);
                dust(server, p.x, p.y, p.z, 0.46f, 0.88f, 0.64f);
            }
        }
        // 中央凝光团（月白核）
        if (t == 6 || t == 14 || t == 22 || t == 30 || t == 42) {
            Vec3 c = position().add(0, 1.3, 0);
            server.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, t == 42 ? 30 : 16, 0.72, 0.55, 0.72, 0.14);
            for (int i = 0; i < (t == 42 ? 26 : 12); i++)
                dust(server, c.x + (random.nextDouble() - 0.5) * 1.3, c.y + (random.nextDouble() - 0.5) * 1.3,
                        c.z + (random.nextDouble() - 0.5) * 1.3, 0.92f, 0.98f, 0.95f);
        }
        // 余辉飘散
        if (t >= 44 && (t & 1) == 0) {
            for (int i = 0; i < 5; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = random.nextDouble() * 6.4;
                double y = 0.4 + random.nextDouble() * 2.6;
                dust(server, getX() + Math.cos(a) * r, getY() + y, getZ() + Math.sin(a) * r,
                        0.30f, 0.74f, 0.48f);
            }
        }
    }

    private void dust(ServerLevel server, double x, double y, double z, float r, float g, float b) {
        server.sendParticles(new DustParticleOptions(new Vector3f(Math.max(0.001f, r), Math.max(0.001f, g), Math.max(0.001f, b)), 0.8f),
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