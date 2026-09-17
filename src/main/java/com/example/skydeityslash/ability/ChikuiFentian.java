package com.example.skydeityslash.ability;

import com.example.skydeityslash.SkydeitySlash;
import com.example.skydeityslash.entity.EntityChikuiArc;
import mods.flammpfeil.slashblade.SlashBlade.RegistryEvents;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.List;

/**
 * 「赤葵」焚天烬灭舞 —— zankou 专属 SA。
 *
 *  1. **斩击伤害**：朝面朝方向长 {@link #SLASH_LEN} 格、宽 {@link #SLASH_HALF_WIDTH}×2 格
 *     （上下各留 {@link #SLASH_UP} / {@link #SLASH_DOWN} 格）的方框内，每个生物各 {@link #SLASH_DAMAGE} 点真伤。
 *  2. 以**玩家为圆心**放一条**暗红摆线**（{@code EntityChikuiArc} ＋ {@code RenderChikuiArc}）：
 *     支点在玩家头顶稍上方、摆臂 25 格，与地面的夹角从 60° 加速摆到 0°（水平），
 *     之后整条线保持水平一起落到地面消失 —— 一条极细的深暗红圆柱，柱上飘着长短不一的火光弧线。
 *  3. 玩家**身边**横向排开 6 把**暗红幻影剑**，依次（每 {@link #PHANTOM_INTERVAL} tick 一把）
 *     朝锁定目标 / 准星方向射出（纯视觉，剑自身 damage = 0）。
 */
public class ChikuiFentian {

    /** 释放时写入刀身的特效色（暗红；若之后有 SE 设色会被覆盖） */
    public static final int BLADE_COLOR = 0x9B0E22;
    /** 幻影剑颜色：暗红（与刀痕同色系） */
    public static final int PHANTOM_COLOR = 0x9B0E22;

    // ===================== 幻影剑参数 =====================
    /** 幻影剑数量 */
    private static final int PHANTOM_COUNT = 6;
    /** 第一把剑的延时（tick）—— 让竖线先转起来 */
    private static final int PHANTOM_START_DELAY = 4;
    /** ★ 相邻两把的间隔（tick）—— 「一个个发射」靠它调控：
     *  4 = 0.2 秒（太快，看着像齐射）；**8 = 0.4 秒（当前，能明显看出是一把一把射）**；12 = 0.6 秒（很慢）。 */
    private static final int PHANTOM_INTERVAL = 8;
    /** 飞行速度（格/tick） */
    private static final double PHANTOM_SPEED = 1.6;
    /** 每把剑在玩家身边的横向基础偏移（格） */
    private static final double PHANTOM_SIDE = 0.85;
    /** 每向外一对的横向增量（格） */
    private static final double PHANTOM_SIDE_STEP = 0.30;
    /** 每把剑相对眼睛的高度偏移（格）—— 上/中/下错开 */
    private static final double[] PHANTOM_Y = {-0.28, 0.04, 0.36};
    /** 生成点相对玩家的向后偏移（格）—— 略靠后，向前射出更自然 */
    private static final double PHANTOM_BACK = 0.40;
    /** 没锁定目标时瞄准准星前方多少格 */
    private static final double AIM_DIST = 8.0;

    // ===================== 斩击伤害 =====================
    /** 斩击范围：从玩家脚下起、朝面朝方向长 {@link #SLASH_LEN} 格 */
    private static final double SLASH_LEN = 25.0;
    /** 斩击范围：线的左右各 {@link #SLASH_HALF_WIDTH} 格 → 总宽 3 格 */
    private static final double SLASH_HALF_WIDTH = 1.5;
    /** 斩击范围：向上 / 向下各留多少（不是"扁平一条"，留点厚度才打得到站着的怪） */
    private static final double SLASH_UP = 3.0;
    private static final double SLASH_DOWN = 1.0;
    /** 范围内每个生物结算的真伤（520 = 一次斩击的总量） */
    private static final float SLASH_DAMAGE = 520.0f;

    // ===================== 地面樱花 =====================
    /** 樱花铺开时长（tick）—— 与摆线同时长，边摆边铺 */
    private static final int SAKURA_TICKS = 24;
    /** 每 tick 撒多少颗（三种粒子轮换）—— 「铺满」的密度靠它 */
    private static final int SAKURA_PER_TICK = 24;
    /** 樱花离地高度（格） */
    private static final double SAKURA_Y = 0.08;

    public static void doChikui(LivingEntity user) {
        if (!(user instanceof ServerPlayer player)) return;
        Level level = player.level();
        if (!(level instanceof ServerLevel sl)) return;

        ISlashBladeState state = player.getMainHandItem()
                .getCapability(CapabilitySlashBlade.BLADESTATE, null).orElse(null);
        if (state != null) state.setEffectColor(new Color(BLADE_COLOR));

        // ---------- ① 斩击伤害：朝面朝方向 25 格 × 宽 3 格（× 上下 3/1 格）内每个生物各 520 真伤 ----------
        dealSlashDamage(player, sl);

        Vec3 look = player.getLookAngle();
        // 水平朝向基（樱花铺地、幻影剑排列都用它）；final 以便在 tick 任务里引用
        Vec3 flatRaw = new Vec3(look.x, 0.0, look.z);
        final Vec3 flat = flatRaw.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : flatRaw.normalize();
        final Vec3 side = new Vec3(-flat.z, 0.0, flat.x);      // 水平"右"方向

        // ---------- ② 摆线：以玩家为圆心（实体放在玩家脚下，渲染器据此在身前那面竖直平面里摆） ----------
        EntityChikuiArc.spawn(level, player.position(), look);

        // ---------- ③ 地面樱花：斩击方框对应的地面上（25 格 × 宽 3 格）铺满三种原版花瓣粒子 ----------
        spawnGroundSakura(sl, player, flat, side, sl.getServer().getTickCount());

        // ---------- ④ 幻影剑：玩家身边 6 把，依次射向目标（纯视觉） ----------

        Entity target = state == null ? null : state.getTargetEntity(level);
        boolean hasTarget = target != null && target.isAlive() && !target.isRemoved();
        // 瞄准点：锁定目标胸口；无锁定则准星前方
        final Vec3 aim = hasTarget
                ? target.position().add(0.0, target.getBbHeight() * 0.5, 0.0)
                : player.getEyePosition().add(look.scale(AIM_DIST));

        Vec3 eye = player.getEyePosition();
        final int now = sl.getServer().getTickCount();
        for (int i = 0; i < PHANTOM_COUNT; i++) {
            // 左右交替：0→左1 1→右1 2→左2 3→右2 4→左3 5→右3
            int k = i / 2;
            double d = (i % 2 == 0 ? -1.0 : 1.0) * (PHANTOM_SIDE + PHANTOM_SIDE_STEP * k);
            final Vec3 spawn = eye
                    .add(side.scale(d))
                    .add(flat.scale(-PHANTOM_BACK))
                    .add(0.0, PHANTOM_Y[k % PHANTOM_Y.length], 0.0);
            final int fireTick = now + PHANTOM_START_DELAY + i * PHANTOM_INTERVAL;
            sl.getServer().tell(new TickTask(fireTick, () -> fireSword(player, sl, aim, spawn)));
        }
    }

    /**
     * 斩击伤害：以玩家为原点，朝**面朝方向**长 {@link #SLASH_LEN} 格、
     * 左右各 {@link #SLASH_HALF_WIDTH} 格（总宽 3 格）、向上 {@link #SLASH_UP} / 向下 {@link #SLASH_DOWN} 格的方框内，
     * 所有生物各结算一次 {@link #SLASH_DAMAGE} 点真实伤害（复用 {@code GameEvents.applyTrueDamage}）。
     *
     * 实现：先用一个"松"的轴对齐大盒子粗筛（`getEntitiesOfClass` 只吃轴对齐盒），
     * 再逐个用「沿朝向的距离 / 侧向偏移 / 高度差」做精确判定 —— 这样方框能跟着玩家朝向转。
     */
    private static void dealSlashDamage(ServerPlayer player, ServerLevel level) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = flat.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 side = new Vec3(-flat.z, 0.0, flat.x);      // 水平"右"方向
        Vec3 origin = player.position();

        AABB broad = new AABB(origin, origin.add(flat.scale(SLASH_LEN)))
                .inflate(SLASH_HALF_WIDTH + 2.0, SLASH_UP + 2.0, SLASH_HALF_WIDTH + 2.0);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, broad,
                e -> e != player && e.isAlive() && !e.isRemoved());
        for (LivingEntity e : candidates) {
            Vec3 rel = e.getBoundingBox().getCenter().subtract(origin);
            double along = rel.dot(flat);
            if (along < 0.0 || along > SLASH_LEN) continue;
            if (Math.abs(rel.dot(side)) > SLASH_HALF_WIDTH) continue;
            if (rel.y < -SLASH_DOWN || rel.y > SLASH_UP) continue;
            SkydeitySlash.GameEvents.applyTrueDamage(e, player, SLASH_DAMAGE);
        }
    }

    /**
     * 地面樱花：在斩击方框对应的**地面**上（沿面朝方向 {@link #SLASH_LEN} 格、宽 {@link #SLASH_HALF_WIDTH}×2 格，
     * 即 25 × 3 格）铺满花瓣粒子。只用 {@link ParticleTypes#CHERRY_LEAVES} —— 它是**贴图核实过的纯粉色**
     * 樱花花瓣（cherry_0~11.png，RGB 约 246,182,218 / 232,151,194 / 223,116,173）。
     *
     * ⚠️ 另外两种别再混进来：{@code SPORE_BLOSSOM_AIR} 是**偏绿白**的浮空孢子（不走贴图，颜色写死在代码里），
     * 用户一眼就看出"有绿色"；{@code FALLING_SPORE_BLOSSOM} 也一并不用，保证绝无杂色。
     * 按 tick 分批撒（每 tick {@link #SAKURA_PER_TICK} 颗），铺开的节奏与摆线一致。
     */
    private static void spawnGroundSakura(ServerLevel sl, ServerPlayer player, Vec3 flat, Vec3 side, int now) {
        final Vec3 origin = player.position();
        for (int t = 0; t < SAKURA_TICKS; t++) {
            final int tt = t;
            sl.getServer().tell(new TickTask(now + tt, () -> {
                for (int i = 0; i < SAKURA_PER_TICK; i++) {
                    double along = sl.random.nextDouble() * SLASH_LEN;
                    double lateral = (sl.random.nextDouble() * 2.0 - 1.0) * SLASH_HALF_WIDTH;
                    Vec3 p = origin.add(flat.scale(along)).add(side.scale(lateral));
                    double y = p.y + SAKURA_Y + sl.random.nextDouble() * 0.22;
                    sl.sendParticles(ParticleTypes.CHERRY_LEAVES,
                            p.x, y, p.z, 1, 0.06, 0.02, 0.06, 0.01);
                }
            }));
        }
    }

    /** 射出一把纯视觉暗红幻影剑（damage = 0，只有外观） */
    private static void fireSword(ServerPlayer player, ServerLevel level, Vec3 aim, Vec3 spawn) {
        EntityAbstractSummonedSword sword = new EntityAbstractSummonedSword(RegistryEvents.SummonedSword, level);
        sword.setPos(spawn);
        sword.setShooter(player);
        sword.setColor(PHANTOM_COLOR);
        sword.setRoll(0.0F);
        sword.setDamage(0.0F);
        Vec3 dir = aim.subtract(spawn);
        if (dir.lengthSqr() < 1.0E-8) return;
        sword.shoot(dir.x, dir.y, dir.z, (float) PHANTOM_SPEED, 0.0F);
        level.addFreshEntity(sword);
    }
}
