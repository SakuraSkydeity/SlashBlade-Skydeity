package com.example.skydeityslash.ability;

import com.example.skydeityslash.SkydeitySlash;
import com.example.skydeityslash.entity.EntityEffectPreview;
import mods.flammpfeil.slashblade.SlashBlade.RegistryEvents;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 「向阳」九千五百万年前的分歧 —— iroi 专属剑技（SA）。
 *
 * 本技能**不含自定义粒子**（原来的白色旋转光线已整段删掉），表现全靠：
 *  1. 前方 6 格内生物立刻受到 50% 最大生命真伤；
 *  2. 目标处放出鸣雷神「十字连斩」特效（等价于指令 {@code /skydeityslash preview naru 1}），
 *     冲击帧到达时对周围造成 520 真伤；
 *  3. 6 把**粉紫幻影剑**（SlashBlade 召唤剑）从目标上方**依次**扎落，
 *     每把落地对落点周围造成 52 真伤（复用 {@link SkydeitySlash.GameEvents#applyTrueDamage}）；
 *  4. 释放时小幅突进。
 *
 * ⚠ 注意：SlashBlade 的 {@code EntityAbstractSummonedSword.setDelay()} **不控制发射延时**
 * （它只在命中目标后倒数，"贴脸保持 N tick 再爆开"），所以想「依次落下」必须自己按 tick 排队 —— 见下方常量。
 */
public class IroiXiangyangArt {

    /** 幻影剑颜色：iroi 主题粉紫 */
    public static final int PHANTOM_COLOR = 0x9B2E8C;

    // ======================= 可调参数（时间/数值都在这一块） =======================

    /** 幻影剑数量 */
    private static final int PHANTOM_COUNT = 6;
    /** ★ 相邻两把幻影剑的间隔（tick）—— **「依次落下」就是靠它调控**：20 = 1 秒一把，想更慢就调大、想更快就调小 */
    private static final int PHANTOM_INTERVAL = 20;
    /** 幻影剑生成高度（落点上方几格） */
    private static final double PHANTOM_HEIGHT = 7.0;
    /** 幻影剑从生成到落到位需要的 tick（决定真伤结算时刻；与落速联动） */
    private static final int PHANTOM_FALL_TICKS = 8;
    /** 单把幻影剑落地真伤 */
    private static final float PHANTOM_DAMAGE = 52.0f;
    /** 6 个落点绕目标的小幅散布半径（格），避免完全叠在一起看不出「一把一把」 */
    private static final double PHANTOM_SPREAD = 1.4;
    /** 落点散布起始相位，让第一把正对目标 */
    private static final double PHANTOM_PHASE = -Math.PI / 2.0;

    /** 鸣雷神特效：type 0 = naru 组 */
    private static final int NARU_TYPE = 0;
    /** naru 子特效编号 1 = 十字连斩序列（等同 /skydeityslash preview naru 1） */
    private static final int NARU_STAGE_CROSS = 1;
    /** ★ naru 特效冲击帧的延时（tick）—— 特效放出后多久结算伤害 */
    private static final int NARU_IMPACT_DELAY = 10;
    /** naru 冲击真伤 */
    private static final float NARU_DAMAGE = 520.0f;
    /** naru 冲击判定半径（格） */
    private static final double NARU_RADIUS = 3.5;

    // ==============================================================================

    public static void doIroiXiangyang(LivingEntity user) {
        if (!(user instanceof ServerPlayer player)) return;
        Level level = player.level();
        if (level.isClientSide) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        Vec3 dir = player.getLookAngle().normalize();
        Vec3 origin = player.position().add(0.0, 1.2, 0.0);

        // 1. 前方命中生物：50% 最大生命真伤（复用 applyTrueDamage）
        AABB box = new AABB(origin, origin).inflate(6.0);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && !e.isSpectator());
        for (LivingEntity target : targets) {
            Vec3 to = target.position().subtract(player.position());
            if (to.dot(dir) < 0) continue;
            if (to.lengthSqr() > 36) continue;
            SkydeitySlash.GameEvents.applyTrueDamage(target, player, target.getMaxHealth() * 0.5f);
        }

        // 落点：锁定目标优先，否则准星前方 6 格
        final Vec3 aim = resolveAim(player, serverLevel, origin);

        // 2. 鸣雷神「十字连斩」特效 + 冲击 520 真伤
        spawnNaruCross(player, serverLevel, aim);

        // 3. 6 把粉紫幻影剑依次扎落，每把落地 52 真伤
        spawnPhantomSwords(player, serverLevel, aim);

        // 4. 小幅突进
        player.setDeltaMovement(player.getDeltaMovement().add(dir.x * 0.5, 0.1, dir.z * 0.5));
    }

    /** 取落点：优先 SA 锁定目标（用脚底位置，落点才贴地），否则玩家身前 6 格。 */
    private static Vec3 resolveAim(ServerPlayer player, ServerLevel level, Vec3 origin) {
        ISlashBladeState state = player.getMainHandItem().getCapability(CapabilitySlashBlade.BLADESTATE, null).orElse(null);
        Entity tgt = state != null ? state.getTargetEntity(level) : null;
        if (tgt instanceof LivingEntity tl && tl.isAlive() && !tl.isRemoved()) {
            return tl.position();
        }
        return origin.add(player.getLookAngle().normalize().scale(6.0));
    }

    /** 在落点放出鸣雷神「十字连斩」子特效（= /skydeityslash preview naru 1），冲击帧到达时结算 520 真伤。 */
    private static void spawnNaruCross(ServerPlayer player, ServerLevel level, Vec3 aim) {
        EntityEffectPreview.spawn(level, aim.add(0.0, 0.5, 0.0), player.getLookAngle(),
                NARU_TYPE, NARU_STAGE_CROSS);
        level.getServer().tell(new TickTask(level.getServer().getTickCount() + NARU_IMPACT_DELAY, () -> {
            AABB hit = new AABB(aim, aim).inflate(NARU_RADIUS);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, hit,
                    x -> x != player && x.isAlive() && !x.isSpectator())) {
                SkydeitySlash.GameEvents.applyTrueDamage(e, player, NARU_DAMAGE);
            }
        }));
    }

    /**
     * 6 把幻影剑「依次」扎落：用 TickTask 每隔 {@link #PHANTOM_INTERVAL} tick 放一把，
     * 每把再排一个 {@link #PHANTOM_FALL_TICKS} 之后的落地结算（52 真伤）。
     */
    private static void spawnPhantomSwords(ServerPlayer player, ServerLevel level, Vec3 aim) {
        final int now = level.getServer().getTickCount();
        for (int i = 0; i < PHANTOM_COUNT; i++) {
            final int idx = i;
            // 该把剑的落点（绕目标一圈小幅散开）
            final Vec3 land = landPoint(aim, idx);
            // ① 发射时刻：从落点上方竖直扎下来
            level.getServer().tell(new TickTask(now + idx * PHANTOM_INTERVAL,
                    () -> spawnOnePhantom(player, level, land)));
            // ② 落地时刻：结算 52 真伤（纯视觉剑自身不带伤害）
            level.getServer().tell(new TickTask(now + idx * PHANTOM_INTERVAL + PHANTOM_FALL_TICKS, () -> {
                AABB hit = new AABB(land, land).inflate(2.0, 1.5, 2.0);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, hit,
                        x -> x != player && x.isAlive() && !x.isSpectator())) {
                    SkydeitySlash.GameEvents.applyTrueDamage(e, player, PHANTOM_DAMAGE);
                }
            }));
        }
    }

    private static Vec3 landPoint(Vec3 aim, int idx) {
        double ang = PHANTOM_PHASE + idx * (Math.PI * 2.0 / PHANTOM_COUNT);
        return aim.add(Math.cos(ang) * PHANTOM_SPREAD, 0.0, Math.sin(ang) * PHANTOM_SPREAD);
    }

    /** 放出一把纯视觉的粉紫幻影剑，从落点上方竖直向下飞（伤害由落地 TickTask 结算，故 damage=0）。 */
    private static void spawnOnePhantom(ServerPlayer player, ServerLevel level, Vec3 land) {
        Vec3 start = land.add(0.0, PHANTOM_HEIGHT, 0.0);
        EntityAbstractSummonedSword sword = new EntityAbstractSummonedSword(RegistryEvents.SummonedSword, level);
        sword.setPos(start);
        sword.setShooter(player);
        sword.setColor(PHANTOM_COLOR);
        sword.setRoll(0.0F);
        sword.setDamage(0.0F);      // 纯展示：伤害改由落地时结算的 52 真伤
        Vec3 down = land.subtract(start);
        sword.shoot(down.x, down.y, down.z, (float) (PHANTOM_HEIGHT / PHANTOM_FALL_TICKS), 0.0F);
        level.addFreshEntity(sword);
    }
}
