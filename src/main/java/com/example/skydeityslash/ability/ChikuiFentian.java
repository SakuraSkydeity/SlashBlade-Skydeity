package com.example.skydeityslash.ability;

import com.example.skydeityslash.SkydeitySlash;
import com.example.skydeityslash.entity.EntityChikuiArc;
import com.example.skydeityslash.entity.EntityChikuiFlower;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 「赤葵」焚天烬灭舞 —— zankou 专属 SA。
 *
 * <p>★ **两种形态交替释放**：同一个 SA，每次释放后自动切到另一种（见 {@link #takeFlowerTurn}）——
 * <ul>
 *   <li><b>形态 ①「焚天烬灭舞」</b>（奇数次的释放）：下面 1~4 条那套完整效果。</li>
 *   <li><b>形态 ②「绽花」</b>（偶数次的释放）：**一朵程序化花朵**（{@link EntityChikuiFlower}，
 *       8+8 花瓣 + 亮芯 + 花蕊，纯几何加法混合、非粒子）。**有锁定目标就绽在目标身上**，
 *       没有锁定才绽在玩家身前 {@link #FLOWER_DIST} 格；绽放瞬间对花心
 *       {@link #FLOWER_DAMAGE_RADIUS} 格内所有生物结算 {@link #FLOWER_DAMAGE} 真伤，
 *       之后每秒再来 {@link #FLOWER_BLEED_DAMAGE} 真伤，直到花消散（{@link #FLOWER_SECONDS} 秒）。</li>
 * </ul>
 *
 * <p>形态 ① 的四步：
 *  1. **斩击伤害**：朝面朝方向长 {@link #SLASH_LEN} 格、宽 {@link #SLASH_HALF_WIDTH}×2 格
 *     （上下各留 {@link #SLASH_UP} / {@link #SLASH_DOWN} 格）的方框内，每个生物各 {@link #SLASH_DAMAGE} 点真伤。
 *  2. 以**玩家为圆心**放一条**暗红摆线**（{@code EntityChikuiArc} ＋ {@code RenderChikuiArc}）：
 *     支点在玩家头顶稍上方、摆臂 25 格，与地面的夹角从 60° 加速摆到 0°（水平），
 *     之后整条线保持水平一起落到地面消失 —— 一条极细的深暗红圆柱，柱上飘着长短不一的火光弧线。
 *  3. 玩家**身边**横向排开 6 把**暗红幻影剑**，依次（每 {@link #PHANTOM_INTERVAL} tick 一把）
 *     朝锁定目标 / 准星方向射出（纯视觉，剑自身 damage = 0）。
 *  4. 斩击方框对应的地面上铺满樱花花瓣粒子。
 */
public class ChikuiFentian {

    /** 释放时写入刀身的特效色（暗红；若之后有 SE 设色会被覆盖） */
    public static final int BLADE_COLOR = 0x9B0E22;
    /** 幻影剑颜色：暗红（与刀痕同色系） */
    public static final int PHANTOM_COLOR = 0x9B0E22;

    // ===================== 形态交替 =====================
    /**
     * 每个玩家**下一次**该放哪种形态：{@code false} = 形态①（焚天烬灭舞）、{@code true} = 形态②（绽花）。
     * 每次释放都翻转，于是两种形态来回交替。表只存在于服务端内存里 ——
     * 服务器重启 / 玩家重登会回到默认（形态①先放）。
     */
    private static final Map<UUID, Boolean> NEXT_IS_FLOWER = new HashMap<>();

    /**
     * 取出「这一次」该放哪种形态，并把标记翻给下一次 —— **两种形态来回放的唯一开关**。
     * 第一次调用返回 {@code false}（先放完整版），之后 true/false 交替。
     */
    private static boolean takeFlowerTurn(ServerPlayer player) {
        boolean flower = NEXT_IS_FLOWER.getOrDefault(player.getUUID(), Boolean.FALSE);
        NEXT_IS_FLOWER.put(player.getUUID(), !flower);
        return flower;
    }

    // ===================== 形态②：绽花 =====================
    /** 花心在玩家**身前**多远（格，沿水平面朝方向）—— **仅在没有锁定目标时**使用 */
    private static final double FLOWER_DIST = 7.0;
    /** 花心相对玩家**脚底**抬高多少（格）—— 抬到胸口偏上，第一/第三人称都看得清 */
    private static final double FLOWER_LIFT = 2.0;

    /** 绽花瞬间结算的真伤 */
    private static final float FLOWER_DAMAGE = 1314.0f;
    /** 绽放之后每秒（每 20 tick）结算的真伤，一直持续到花消散 */
    private static final float FLOWER_BLEED_DAMAGE = 52.0f;
    /** 绽花伤害半径（格）：以花心为球心，范围内**所有**生物都吃伤害 */
    private static final double FLOWER_DAMAGE_RADIUS = 4.0;
    /** 绽花持续几秒 —— 与 {@code EntityChikuiFlower.LIFETIME}（60 tick）对齐 */
    private static final int FLOWER_SECONDS = 3;

    // ---- 绽花的樱花粒子（★ 2026-09-28 加：用户要求"也加一点樱花的粒子效果，不要太多"）----
    /** 绽放瞬间撒的花瓣数（就一蓬，别铺满） */
    private static final int FLOWER_SAKURA_BURST = 16;
    /** 之后每隔几 tick 补几颗 —— 3 颗 / 4 tick，总量约 50 颗/朵，是"一点"不是"一片" */
    private static final int FLOWER_SAKURA_EVERY = 4, FLOWER_SAKURA_PER = 3;
    /** 花瓣离花心的散布半径（格） */
    private static final double FLOWER_SAKURA_SPREAD = 2.2;

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

        // ---------- 形态交替：这一次轮到"绽花"就走绽花那条路（位置 + 伤害都在里面） ----------
        if (takeFlowerTurn(player)) {
            bloomFlower(player, sl, state);
            return;
        }

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
     * 形态②的花朵应该绽在哪里（**无锁定目标时**）：玩家**身前** {@link #FLOWER_DIST} 格
     * （沿水平面朝方向）、脚底上方 {@link #FLOWER_LIFT} 格。
     * SA 本体与调试指令共用这一处，避免两边算得不一样。
     */
    public static Vec3 flowerCenter(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 fwd = new Vec3(look.x, 0.0, look.z);
        fwd = fwd.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : fwd.normalize();
        return player.position().add(fwd.scale(FLOWER_DIST)).add(0.0, FLOWER_LIFT, 0.0);
    }

    /**
     * 形态②「绽花」：**有锁定目标就绽在锁定目标身上**（目标身体中心），没有锁定才绽在玩家身前。
     *
     * <p>花本身仍是**不带攻击逻辑**的视觉实体，伤害统一在这里结算：绽放瞬间对花心
     * {@link #FLOWER_DAMAGE_RADIUS} 格内的所有生物结算 {@link #FLOWER_DAMAGE} 真伤，
     * 之后每 20 tick 再结算一次 {@link #FLOWER_BLEED_DAMAGE} 真伤，
     * 直到花消散（{@link #FLOWER_SECONDS} 秒 —— 与 {@code EntityChikuiFlower.LIFETIME} 对齐）。
     *
     * <p>花心坐标用 {@code final} 固定下来：持续伤害按"绽开时的那一点"结算，不跟着目标跑。
     */
    private static void bloomFlower(ServerPlayer player, ServerLevel sl, ISlashBladeState state) {
        Entity target = state == null ? null : state.getTargetEntity(sl);
        boolean hasTarget = target != null && target.isAlive() && !target.isRemoved();
        final Vec3 center = hasTarget
                ? target.position().add(0.0, target.getBbHeight() * 0.5, 0.0)
                : flowerCenter(player);
        EntityChikuiFlower.spawn(sl, center);

        final int now = sl.getServer().getTickCount();
        spawnFlowerSakura(sl, center, now);          // ★ 花开了就有几点樱花瓣飘着（量很小）
        dealFlowerDamage(sl, player, center, FLOWER_DAMAGE);
        for (int sec = 1; sec <= FLOWER_SECONDS; sec++) {
            final int delay = sec * 20;
            sl.getServer().tell(new TickTask(now + delay,
                    () -> dealFlowerDamage(sl, player, center, FLOWER_BLEED_DAMAGE)));
        }
    }

    /**
     * 绽花周围的**少量樱花粒子**：绽放瞬间一小蓬 {@link #FLOWER_SAKURA_BURST} 颗，
     * 之后每 {@link #FLOWER_SAKURA_EVERY} tick 补 {@link #FLOWER_SAKURA_PER} 颗，
     * 撒到花开始淡出（寿命的 2/3）为止 —— 全生命周期约 50 颗，"一点点"的量级。
     *
     * <p>★ 只用 {@link ParticleTypes#CHERRY_LEAVES}：它是**贴图核实过的纯粉色**樱花花瓣
     * （理由与形态①的 {@code spawnGroundSakura} 相同，别再混 {@code SPORE_BLOSSOM_AIR} 那种偏绿的）。
     */
    private static void spawnFlowerSakura(ServerLevel sl, Vec3 center, int now) {
        sl.sendParticles(ParticleTypes.CHERRY_LEAVES,
                center.x, center.y + .4, center.z, FLOWER_SAKURA_BURST,
                FLOWER_SAKURA_SPREAD * .55, FLOWER_SAKURA_SPREAD * .45, FLOWER_SAKURA_SPREAD * .55, .02);
        int until = EntityChikuiFlower.LIFETIME * 2 / 3;      // 淡出之前撒完
        for (int t = FLOWER_SAKURA_EVERY; t < until; t += FLOWER_SAKURA_EVERY) {
            final int delay = t;
            sl.getServer().tell(new TickTask(now + delay, () -> sl.sendParticles(ParticleTypes.CHERRY_LEAVES,
                    center.x, center.y + .35, center.z, FLOWER_SAKURA_PER,
                    FLOWER_SAKURA_SPREAD * .45, FLOWER_SAKURA_SPREAD * .30, FLOWER_SAKURA_SPREAD * .45, .015)));
        }
    }

    /**
     * 花心周围 {@link #FLOWER_DAMAGE_RADIUS} 格内的**所有**生物结算一次真伤。
     * 不含施法者自己（花绽在身边时不该自伤）与旁观者；友方 NPC 由 {@code applyTrueDamage} 内部豁免。
     */
    private static void dealFlowerDamage(ServerLevel sl, ServerPlayer player, Vec3 center, float amount) {
        final double r = FLOWER_DAMAGE_RADIUS;
        AABB box = new AABB(center, center).inflate(r);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && !e.isRemoved() && !e.isSpectator())) {
            if (e.getBoundingBox().getCenter().distanceToSqr(center) > r * r) continue;
            SkydeitySlash.GameEvents.applyTrueDamage(e, player, amount);
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
