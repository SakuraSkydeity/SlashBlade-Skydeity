package com.example.skydeityslash.entity;

import com.example.skydeityslash.SkydeitySlash;
import com.example.skydeityslash.registry.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 钤印伞特效 —— 惰性特效宿主（无碰撞、无 AI、无阴影、纯视觉）。
 * 粒子全部在 tick 内每帧持续生成（水墨 + 墨绿 + 水墨蓝），保证可见且堆料丰富：
 * MODE_STAY   «令花神离恨无泪·原地»（特殊行动+W）：伞留在原地，绕伞浓郁水墨山水（层峦/浓淡/烟云）。
 * MODE_BEACON «令花神离恨无泪·推伞»（特殊行动+A）：伞向前缓慢推 6 格，身后随伞拖出「上下起伏的丝绸拖尾」。
 * MODE_ORBIT  «芙蓉花琼楼吹彻笛声寒»（特殊行动+S）：环绕旋转时呈现「山陵一样的中空圆柱」，仅表层粒子，随高度墨绿→水墨蓝，再消散。
 * MODE_THROW  «芙蓉花·投伞»（特殊行动+D）：由玩家位置缓慢上浮最高 6 格后悬停，垂落绸尾，散去。
 * 所有 MODE 再次按下对应按键均传送到伞处（传送在释放端）。
 */
public class EntityRainUmbrella extends Entity {
    public static final int LIFETIME = 60;            // 3 秒
    public static final int MODE_BEACON = 0;          // 令花神离恨无泪：向前推伞
    public static final int MODE_ORBIT = 1;           // 芙蓉花琼楼：山陵圆柱环绕
    public static final int MODE_THROW = 2;           // 芙蓉花琼楼·投伞：缓慢上浮至最高 6 格
    public static final int MODE_STAY = 3;            // 令花神·原地：伞留在原地，绕伞水墨山水
    public static final double BEACON_TRAVEL = 9.0;   // 令花神推伞距离（格，原 6 → +3）
    public static final double THROW_RISE = 9.0;      // 投伞缓慢上升的最高高度（格，原 6 → +3）
    public static final int THROW_RISE_DUR = 90;      // 投伞上升所用 tick（距离+3，速度等比提升）

    // 山水体系三色（整体提亮）：深青灰山影 / 山水翠绿 / 天青水蓝，另加月白迎光高点
    private static final Vector3f INK_BLACK = new Vector3f(0.20f, 0.40f, 0.42f);
    private static final Vector3f INK_GREEN = new Vector3f(0.32f, 0.84f, 0.58f);
    private static final Vector3f INK_BLUE  = new Vector3f(0.55f, 0.87f, 1.00f);
    private static final Vector3f INK_PALE  = new Vector3f(0.88f, 0.97f, 0.96f);

    private static final EntityDataAccessor<Integer> DATA_MODE =
            SynchedEntityData.defineId(EntityRainUmbrella.class, EntityDataSerializers.INT);

    private int mode = MODE_BEACON;
    private Vec3 anchor = Vec3.ZERO;
    private double orbRadius = 0;
    private double orbAngle = 0;
    private float orbSpeed = 0.07f;
    private int ownerId = -1;
    private boolean applyDebuff = false;
    private Vec3 moveDir = null;
    private double travelled = 0;
    private double throwTargetY = 0;

    public EntityRainUmbrella(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_MODE, MODE_BEACON);
    }

    public int getEffectMode() {
        return entityData.get(DATA_MODE);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.mode = tag.getInt("umbrella_mode");
        this.orbRadius = tag.getDouble("umbrella_radius");
        this.orbAngle = tag.getDouble("umbrella_angle");
        this.orbSpeed = tag.getFloat("umbrella_speed");
        this.ownerId = tag.getInt("umbrella_owner");
        this.applyDebuff = tag.getBoolean("umbrella_debuff");
        this.anchor = new Vec3(tag.getDouble("umbrella_ax"), tag.getDouble("umbrella_ay"), tag.getDouble("umbrella_az"));
        entityData.set(DATA_MODE, this.mode);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("umbrella_mode", mode);
        tag.putDouble("umbrella_radius", orbRadius);
        tag.putDouble("umbrella_angle", orbAngle);
        tag.putFloat("umbrella_speed", orbSpeed);
        tag.putInt("umbrella_owner", ownerId);
        tag.putBoolean("umbrella_debuff", applyDebuff);
        tag.putDouble("umbrella_ax", anchor.x);
        tag.putDouble("umbrella_ay", anchor.y);
        tag.putDouble("umbrella_az", anchor.z);
    }

    public static EntityRainUmbrella spawnBeacon(Level level, LivingEntity owner, Vec3 center, Vec3 dir) {
        EntityRainUmbrella u = new EntityRainUmbrella(ModEntities.UMBRELLA.get(), level);
        setup(u, MODE_BEACON, owner, center, 0, 0, 0, true);
        u.moveDir = dir;
        u.travelled = 0;
        u.setPos(center.x, center.y, center.z);
        level.addFreshEntity(u);
        return u;
    }

    public static EntityRainUmbrella spawnThrow(Level level, LivingEntity owner, Vec3 center, double riseHeight) {
        EntityRainUmbrella u = new EntityRainUmbrella(ModEntities.UMBRELLA.get(), level);
        setup(u, MODE_THROW, owner, center, 0, 0, 0, false);
        u.anchor = center;
        u.throwTargetY = center.y + riseHeight;
        u.setPos(center.x, center.y, center.z);
        level.addFreshEntity(u);
        return u;
    }

    /** 令花神·原地：伞留在生成位置不动，绕伞原地展现浓郁水墨山水特效。 */
    public static EntityRainUmbrella spawnStay(Level level, LivingEntity owner, Vec3 center) {
        EntityRainUmbrella u = new EntityRainUmbrella(ModEntities.UMBRELLA.get(), level);
        setup(u, MODE_STAY, owner, center, 0, 0, 0, false);
        u.setPos(center.x, center.y, center.z);
        level.addFreshEntity(u);
        return u;
    }

    public static List<EntityRainUmbrella> spawnOrbit(Level level, LivingEntity owner, Vec3 center,
                                                      int count, double radius) {
        List<EntityRainUmbrella> list = new ArrayList<>();
        double start = level.random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            EntityRainUmbrella u = new EntityRainUmbrella(ModEntities.UMBRELLA.get(), level);
            double a = start + (Math.PI * 2 * i) / count;
            // 速度随半径等比提升（基准半径 4.0，速度 0.07 rad/tick → 比原 0.13 更慢的线速度）
            float speed = 0.07f * (float) (radius / 4.0);
            setup(u, MODE_ORBIT, owner, center, radius, a, speed, false);
            u.orbAngle = a;
            u.setPos(center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius);
            level.addFreshEntity(u);
            list.add(u);
        }
        return list;
    }

    private static void setup(EntityRainUmbrella u, int mode, LivingEntity owner, Vec3 center,
                              double radius, double angle, float speed, boolean debuff) {
        u.mode = mode;
        u.anchor = center;
        u.orbRadius = radius;
        u.orbAngle = angle;
        u.orbSpeed = speed;
        u.ownerId = owner.getId();
        u.applyDebuff = debuff;
        u.entityData.set(DATA_MODE, mode);
    }

    public int getOwnerId() {
        return ownerId;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        int life = switch (mode) {
            case MODE_ORBIT -> 100;
            case MODE_THROW -> 200;
            case MODE_STAY -> 120;
            default -> LIFETIME;
        };
        if (tickCount >= life) {
            discard();
            return;
        }

        float tsec = tickCount * 0.16f;

        if (mode == MODE_ORBIT) {
            orbAngle += orbSpeed;
            setPos(anchor.x + Math.cos(orbAngle) * orbRadius,
                    anchor.y,
                    anchor.z + Math.sin(orbAngle) * orbRadius);
            if (level() instanceof ServerLevel sl) {
                Entity owner = level().getEntity(ownerId);
                if (owner instanceof LivingEntity li) {
                    AABB box = new AABB(anchor.x - 4, anchor.y - 1.0, anchor.z - 4,
                            anchor.x + 4, anchor.y + 3.0, anchor.z + 4);
                    for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box,
                        t -> t.isAlive() && t.getId() != ownerId && !t.isSpectator())) {
                    e.invulnerableTime = 0;
                    SkydeitySlash.GameEvents.applyTrueDamage(e, li, e.getHealth() * 0.25f + 5.0f);
                }
                }
                spawnOrbitSilk(sl, tsec);
            }
        } else if (mode == MODE_BEACON) {
            if (moveDir != null && travelled < BEACON_TRAVEL) {
                double next = Math.min(travelled + BEACON_TRAVEL / LIFETIME, BEACON_TRAVEL);
                double step = next - travelled;
                travelled = next;
                setPos(getX() + moveDir.x * step, getY(), getZ() + moveDir.z * step);
                anchor = position();
            }
            if (level() instanceof ServerLevel sl) {
                spawnBeaconSilk(sl, tsec);
            }
            // 令花神·推伞：以伞当前位置为基准，9 格远 × 5 格宽 × 高 4.5 的区域内，
            // 每 0.5 秒对生物直接扣除 25% 最大生命的真伤（无视护甲/抗性/限伤）
            if (level() instanceof ServerLevel sl2 && tickCount % 10 == 0) {
                LivingEntity beaconOwner = level().getEntity(ownerId) instanceof LivingEntity bo ? bo : null;
                Vec3 fwd = (moveDir != null && moveDir.lengthSqr() > 0.001) ? moveDir.normalize() : new Vec3(0, 0, 1);
                Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
                Vec3 p = position();
                AABB box = new AABB(
                        p.x + side.x * 2.5, p.y - 1.5, p.z + side.z * 2.5,
                        p.x + fwd.x * 9.0 - side.x * 2.5, p.y + 3.0, p.z + fwd.z * 9.0 - side.z * 2.5);
                for (LivingEntity e : sl2.getEntitiesOfClass(LivingEntity.class, box,
                        t -> t.isAlive() && t.getId() != ownerId && !t.isSpectator())) {
                    e.invulnerableTime = 0;
                    SkydeitySlash.GameEvents.applyTrueDamage(e, beaconOwner, e.getMaxHealth() * 0.25f);
                }
            }
        } else if (mode == MODE_THROW) {
            // 投伞：不依赖 getY() 判定，直接按 tickCount 平滑上浮至 throwTargetY（先慢后快），到顶后悬停
            double total = throwTargetY - anchor.y;
            double t = Math.min(1.0, tickCount / (double) THROW_RISE_DUR);
            double eased = t * t;
            setPos(getX(), anchor.y + total * eased, getZ());
            if (level() instanceof ServerLevel sl) {
                spawnThrowSilk(sl, tsec);
            }
        } else if (mode == MODE_STAY) {
            // 伞留在原地，绕伞原地展现浓郁水墨山水
            if (level() instanceof ServerLevel sl) {
                spawnStayInk(sl, tsec);
            }
        }
    }

    /** MODE_BEACON：向前推伞时向伞后拖出「上下起伏的山水绸缎」，四股并列、横向摇曳、末端飘散并渐变变亮，顶部再涌一缕烟云。 */
    private void spawnBeaconSilk(ServerLevel sl, float tsec) {
        Vec3 d = (moveDir != null && moveDir.lengthSqr() > 0.001) ? moveDir : new Vec3(0, 0, 1);
        Vec3 perp = new Vec3(-d.z, 0, d.x);   // 水平垂直方向，用于多股绸缎横向铺开
        for (int lane = 0; lane < 4; lane++) {
            double laneOff = (lane - 1.5) * 0.8;
            int n = 16;
            for (int k = 0; k < n; k++) {
                double t = k * 0.42;
                double frag = (double) k / (n - 1);
                // 双正弦叠加起伏：幅度大，绸带如山峦连绵起伏、飘逸
                double rise = Math.sin(tsec * 1.1 + k * 0.7 + lane * 1.8) * 1.05
                            + Math.sin(tsec * 0.48 + k * 0.34) * 0.6;
                double sway = Math.sin(tsec * 0.8 + k * 0.55 + lane * 1.3) * 0.65;
                double bx = getX() - d.x * t + perp.x * (laneOff + sway);
                double by = getY() + 1.0 + rise + lane * 0.2;
                double bz = getZ() - d.z * t + perp.z * (laneOff + sway);
                sl.sendParticles(new DustParticleOptions(silkRamp(frag), Math.max(0.3f, 1.0f - k * 0.05f)),
                        bx, by, bz, 1, 0, 0, 0, 0);
            }
        }
        // 顶部一缕升腾的淡青云烟，强化水墨「流云」感
        for (int k = 0; k < 8; k++) {
            double t = k * 0.5;
            double px = getX() - d.x * t * 0.6 + Math.sin(tsec * 0.5 + k) * 0.7;
            double py = getY() + 1.8 + t * 0.5 + Math.sin(tsec * 0.9 + k * 0.7) * 0.5;
            double pz = getZ() - d.z * t * 0.6 + Math.cos(tsec * 0.6 + k) * 0.7;
            sl.sendParticles(new DustParticleOptions((k % 2 == 0) ? INK_BLUE : INK_PALE,
                            Math.max(0.2f, 0.6f - k * 0.05f)),
                    px, py, pz, 1, 0, 0, 0, 0);
        }
    }

    /** MODE_THROW：随伞缓慢上浮的垂落绸尾，向下摇曳、上下起伏，末端散开并向月白变亮。 */
    private void spawnThrowSilk(ServerLevel sl, float tsec) {
        int n = 16;
        for (int k = 0; k < n; k++) {
            double t = k * 0.42;
            double frag = (double) k / (n - 1);
            double sway = Math.sin(tsec * 0.9 + k * 0.55) * 0.85
                        + Math.sin(tsec * 0.4 + k * 0.3) * 0.5;
            double px = getX() + sway;
            double py = getY() - t + Math.sin(tsec * 1.25 + k * 0.85) * 0.5;
            double pz = getZ() + Math.sin(tsec * 0.6 + k) * 0.55;
            sl.sendParticles(new DustParticleOptions(silkRamp(frag), Math.max(0.3f, 0.95f - k * 0.05f)),
                    px, py, pz, 1, 0, 0, 0, 0);
        }
        // 一束向伞身收拢的短丝 + 一缕垂落的淡雾，让尾部更蓬松
        for (int k = 0; k < 8; k++) {
            double t = k * 0.4 + 0.4;
            double px = getX() + Math.sin(tsec + k * 1.1) * 0.75;
            double py = getY() - t + Math.sin(tsec * 0.9 + k) * 0.4;
            double pz = getZ() + Math.cos(tsec * 0.7 + k) * 0.75;
            sl.sendParticles(new DustParticleOptions((k % 2 == 0) ? INK_PALE : INK_BLUE,
                            Math.max(0.2f, 0.6f - k * 0.045f)),
                    px, py, pz, 1, 0, 0, 0, 0);
        }
    }

    /** MODE_ORBIT：山陵中空圆柱，每段高度环随角度呈「连绵山脊」起伏，颜色由底部山影渐变到顶部天青水蓝（整体提亮）。 */
    private void spawnOrbitSilk(ServerLevel sl, float tsec) {
        double[] hs = {0.6, 1.5, 2.4, 3.3, 4.2};
        for (int hi = 0; hi < hs.length; hi++) {
            double base = hs[hi];
            double frag = (double) hi / (hs.length - 1);
            Vector3f col = lerpColor(INK_GREEN, INK_BLUE, frag);
            if (hi <= 1) col = lerpColor(INK_BLACK, INK_GREEN, frag * 1.6 + 0.2); // 底部青灰山影渐亮
            double rr = 6.9 + Math.sin(hi * 2.4 + tsec * 0.05) * 0.14;
            int n = 22 + hi * 6;
            for (int i = 0; i < n; i++) {
                double a = tsec * 0.02 + i * (Math.PI * 2 / n);
                // 山脉起伏：环高随角度双正弦起伏，连绵且幅度更大
                double h = base
                        + Math.sin(a * 3.0 + tsec * 1.1 + hi) * 0.42
                        + Math.sin(a * 1.3 - tsec * 0.5 + hi) * 0.22;
                sl.sendParticles(new DustParticleOptions(col, 0.6f),
                        anchor.x + Math.cos(a) * rr, anchor.y + h, anchor.z + Math.sin(a) * rr, 1, 0, 0, 0, 0);
            }
        }
        // 空心芯内青灰淡雾 + 顶部一缕升腾烟云，强化「空心山体、层峦」感
        sl.sendParticles(new DustParticleOptions(new Vector3f(0.35f, 0.58f, 0.72f), 0.5f),
                anchor.x, anchor.y + 1.9, anchor.z, 10, 0.25, 1.6, 0.25, 0.03);
        for (int k = 0; k < 8; k++) {
            double t = k * 0.5;
            double px = anchor.x + Math.sin(tsec * 0.45 + k) * 1.2;
            double py = anchor.y + 4.6 + t * 0.4 + Math.sin(tsec * 0.8 + k * 0.7) * 0.5;
            double pz = anchor.z + Math.cos(tsec * 0.55 + k) * 1.2;
            sl.sendParticles(new DustParticleOptions((k % 2 == 0) ? INK_BLUE : INK_PALE,
                            Math.max(0.2f, 0.6f - k * 0.05f)),
                    px, py, pz, 1, 0, 0, 0, 0);
        }
    }

    /** MODE_STAY：伞留在原地，绕伞展现浓郁「水墨山水」——底层浓墨底盘、层层起伏山脊、青绿浓淡晕染、顶部淡青云雾上涌。 */
    private void spawnStayInk(ServerLevel sl, float tsec) {
        double bx = getX(), by = getY(), bz = getZ();
        // 底部浓墨底盘：一圈密粒子、深浅交错，像水墨山脚
        for (int i = 0; i < 16; i++) {
            double a = (i / 16.0) * Math.PI * 2 + tsec * 0.05;
            double rr = 7.2 + (i % 3) * 0.35;
            Vector3f col = (i % 2 == 0) ? INK_BLACK : INK_GREEN;
            sl.sendParticles(new DustParticleOptions(col, 0.8f),
                    bx + Math.cos(a) * rr, by + 0.3, bz + Math.sin(a) * rr, 1, 0, 0, 0, 0);
        }
        // 层峦：4 层起伏山脊，绕伞铺开，高度随角度+时间起伏，颜色由浓翠→天青（近浓远淡）
        for (int layer = 0; layer < 4; layer++) {
            double baseH = 0.6 + layer * 1.05;
            double rw = 7.6 + layer * 0.95;
            double frag = layer / 3.0;
            Vector3f col = (frag < 0.5)
                    ? lerpColor(INK_BLACK, INK_GREEN, frag * 2 + 0.3)
                    : lerpColor(INK_GREEN, INK_BLUE, (frag - 0.5) * 2);
            int n = 20 + layer * 6;
            for (int i = 0; i < n; i++) {
                double ang = (i / (double) n) * Math.PI * 2;
                double rr = rw + Math.sin(ang * 3 + layer * 1.7) * 0.5;
                double h = baseH
                        + Math.sin(ang * 3.5 + tsec * 1.15 + layer * 1.5) * 0.9
                        + Math.sin(ang * 1.3 - tsec * 0.55) * 0.5;
                sl.sendParticles(new DustParticleOptions(col, 0.7f),
                        bx + Math.cos(ang) * rr, by + h, bz + Math.sin(ang) * rr, 1, 0, 0, 0, 0);
            }
        }
        // 顶部淡青云雾：向上涌、向四周漂移，像留白与流云
        for (int k = 0; k < 12; k++) {
            double t = k * 0.55;
            double px = bx + Math.sin(tsec * 0.4 + k * 0.8) * 7.15;
            double py = by + 1.4 + t + Math.sin(tsec * 0.7 + k) * 0.6;
            double pz = bz + Math.cos(tsec * 0.45 + k * 0.7) * 7.15;
            Vector3f col = (k % 3 == 0) ? INK_BLUE : INK_PALE;
            sl.sendParticles(new DustParticleOptions(col, Math.max(0.2f, 0.7f - k * 0.045f)),
                    px, py, pz, 1, 0, 0, 0, 0);
        }
    }

    /** 出水山绸缎的渐变：山水翠绿 → 天青水蓝 → 月白迎光。 */
    private static Vector3f silkRamp(double f) {
        double ff = Math.min(1.0, Math.max(0.0, f));
        if (ff < 0.5) return lerpColor(INK_GREEN, INK_BLUE, ff * 2);
        return lerpColor(INK_BLUE, INK_PALE, (ff - 0.5) * 2);
    }

    private static Vector3f lerpColor(Vector3f a, Vector3f b, double t) {
        float f = (float) t;
        return new Vector3f(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, a.z + (b.z - a.z) * f);
    }
}