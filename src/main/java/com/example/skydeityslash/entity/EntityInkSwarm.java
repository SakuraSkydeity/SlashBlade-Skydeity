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
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 雨间蝶舞·墨羽环绕（纯视觉粒子实体，无碰撞无渲染，trackingRange 0）。
 * 攻击命中目标时在目标身边召唤一团聚集的墨绿水墨粒子（核心亮、尾羽拖淡水墨尘），
 * 整团绕目标旋转；期间每 0.5 秒对目标直接扣除 52 点真伤（每秒两次，无视护甲/抗性/限伤）。
 * 玩家停止攻击该目标 1 秒后粒子团消散，伤害随之停止；目标或属主死亡也立即消散。
 */
public class EntityInkSwarm extends Entity {

    private static final Vector3f INK_GREEN = new Vector3f(0.32f, 0.84f, 0.58f);   // 山水翠绿（核心亮）
    private static final Vector3f INK_BLUE = new Vector3f(0.55f, 0.87f, 1.0f);     // 水墨蓝（尾羽淡）
    private static final Vector3f PALE = new Vector3f(0.88f, 0.97f, 0.96f);        // 月白

    private static final int DAMAGE_INTERVAL = 10;   // 每 10 tick（0.5 秒）一次
    private static final int IDLE_TIMEOUT = 20;      // 停止攻击 20 tick（1 秒）后消散

    private int ownerId = -1;
    private int targetId = -1;
    private long lastAttackTime = 0;
    private int life = 0;

    public EntityInkSwarm(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        // 纯服务端粒子实体，无需同步数据
    }

    public void init(LivingEntity owner, LivingEntity target) {
        this.ownerId = owner.getId();
        this.targetId = target.getId();
        this.lastAttackTime = this.level().getGameTime();
        this.setPos(target.position());
    }

    /** 玩家再次命中该目标时刷新计时，粒子团继续停留 */
    public void refreshAttack() {
        this.lastAttackTime = this.level().getGameTime();
    }

    public int getTargetId() { return targetId; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerId = tag.getInt("oid");
        targetId = tag.getInt("tid");
        lastAttackTime = tag.getLong("lat");
        life = tag.getInt("life");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("oid", ownerId);
        tag.putInt("tid", targetId);
        tag.putLong("lat", lastAttackTime);
        tag.putInt("life", life);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!(level() instanceof ServerLevel sl)) { discard(); return; }
        life++;

        LivingEntity target = targetId >= 0 && level().getEntity(targetId) instanceof LivingEntity le ? le : null;
        if (target == null || !target.isAlive()) { discard(); return; }

        LivingEntity owner = ownerId >= 0 && level().getEntity(ownerId) instanceof LivingEntity oe ? oe : null;
        if (owner == null || !owner.isAlive()) { discard(); return; }

        // 玩家停止攻击该目标 1 秒后消散（伤害随之停止）
        if (level().getGameTime() - lastAttackTime > IDLE_TIMEOUT) { discard(); return; }

        // 持续伤害：每 0.5 秒对目标直接扣除 52 点真伤（每秒两次）
        if (life % DAMAGE_INTERVAL == 0) {
            target.invulnerableTime = 0;
            SkydeitySlash.GameEvents.applyTrueDamage(target, owner, 52.0f);
        }

        // 聚集墨绿粒子团绕目标旋转（核心亮、尾羽拖淡水墨尘）
        double orbitAng = life * 0.06;
        double cx = target.getX() + Math.cos(orbitAng) * 0.8;
        double cz = target.getZ() + Math.sin(orbitAng) * 0.8;
        double cy = target.getY() + target.getBbHeight() * 0.5 + Math.sin(life * 0.12) * 0.25;
        int flockCount = 14;
        for (int i = 0; i < flockCount; i++) {
            double off = (i % 2 == 0) ? 0.12 : 0.26;
            double a = sl.random.nextDouble() * Math.PI * 2;
            double px = cx + Math.cos(a) * off;
            double pz = cz + Math.sin(a) * off;
            double py = cy + (sl.random.nextDouble() - 0.5) * 0.35;
            dust(sl, new Vec3(px, py, pz), INK_GREEN, 0.45f);
            double tx = px - Math.cos(a) * off * 0.5;
            double tz = pz - Math.sin(a) * off * 0.5;
            dust(sl, new Vec3(tx, py - 0.2, tz), life % 4 < 2 ? INK_BLUE : PALE, 0.25f);
        }
    }

    private void dust(ServerLevel sl, Vec3 p, Vector3f col, float size) {
        sl.sendParticles(new DustParticleOptions(clamp(col), size), p.x, p.y, p.z, 1, 0, 0, 0, 0);
    }

    private static Vector3f clamp(Vector3f v) {
        return new Vector3f(Math.min(1, Math.max(0, v.x)), Math.min(1, Math.max(0, v.y)), Math.min(1, Math.max(0, v.z)));
    }
}
