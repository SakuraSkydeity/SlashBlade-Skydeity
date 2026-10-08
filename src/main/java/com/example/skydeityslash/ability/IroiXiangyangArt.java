package com.example.skydeityslash.ability;

import com.example.skydeityslash.SkydeitySlash;
import com.example.skydeityslash.entity.EntityEffectPreview;
import com.example.skydeityslash.entity.EntityIroiBeam;
import com.example.skydeityslash.entity.EntityIroiOrb;
import mods.flammpfeil.slashblade.SlashBlade.RegistryEvents;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 「向阳」九千五百万年前的分歧 —— iroi 专属剑技（SA）。
 *
 * <p>★ **两种形态交替释放**（同一个 SA 键，放完一种自动切到另一种，来回轮替。与 zankou 的 SA 同一套写法）：
 * <ul>
 *   <li><b>形态 ①「向阳」</b>（下面 1~4 条那套完整效果）：
 *     前方 6 格内生物立即受 50% 最大生命真伤 → 目标处放出鸣雷神「十字连斩」+ 冲击 520 真伤
 *     → 6 把粉紫幻影剑依次扎落（每把 52 真伤）→ 小幅突进。</li>
 *   <li><b>形态 ②「天降打击」</b>：**一道**光束**直接瞄准 SA 锁定的目标**（没有锁定才落在准星前方），
 *     从天上任意一侧以约 **68° 的陡角**砸在目标**脚下**。单道 = **一根锥形紫光柱 + 同色亮芯**（{@link EntityIroiBeam}，全紫单色不分层）；
 *     命中瞬间落点在轴线上来一发短促**闪光**，随后**原版紫尘沿光柱浮起**（本类发的粒子，替代原来代码画的符文）；
 *     落点**没有贴地光斑**，只有一小簇紫色粒子散开；收尾时落点再飘起
 *     **8 个紫色光球**（{@link EntityIroiOrb}，上下漂浮 2 秒后消散）。
 *     每道光束结算 **52 真伤 × 2 次**（命中瞬间 + 8 tick 后各一次）。</li>
 * </ul>
 *
 * <p>形态 ① 不含自定义粒子（原来的白色旋转光线已整段删掉），表现全靠上面的几何特效与幻影剑。
 *
 * <p>⚠ 注意：SlashBlade 的 {@code EntityAbstractSummonedSword.setDelay()} **不控制发射延时**
 * （它只在命中目标后倒数，"贴脸保持 N tick 再爆开"），所以想「依次落下」必须自己按 tick 排队 —— 见下方常量。
 */
public class IroiXiangyangArt {

    /** 幻影剑颜色：iroi 主题粉紫 */
    public static final int PHANTOM_COLOR = 0x9B2E8C;

    // ======================= 形态交替（与 ChikuiFentian 同一套做法） =======================
    /**
     * 每个玩家**下一次**该放哪种形态：{@code false} = 形态①「向阳」、{@code true} = 形态②「斜光轰击」。
     * 每次释放都翻转 ⇒ 两种形态来回交替。表只存在服务端内存里，服务器重启 / 重登会回到形态①。
     */
    private static final Map<UUID, Boolean> NEXT_IS_BEAM = new HashMap<>();

    private static boolean takeBeamTurn(ServerPlayer player) {
        boolean beam = NEXT_IS_BEAM.getOrDefault(player.getUUID(), Boolean.FALSE);
        NEXT_IS_BEAM.put(player.getUUID(), !beam);
        return beam;
    }

    // ======================= 可调参数（时间/数值都在这一块） =======================

    /** 幻影剑数量 */
    private static final int PHANTOM_COUNT = 6;
    /** ★ 相邻两把幻影剑的间隔（tick）—— **「依次落下」就是靠它调控**：20 = 1 秒一把，想更慢就调大、想更快就调小 */
    private static final int PHANTOM_INTERVAL = 20;
    /** 幻影剑生成高度（落点上方几格） */
    private static final double PHANTOM_HEIGHT = 7.0;
    /**
     * 幻影剑从生成到落到位需要的 tick —— **这一项就是「单把剑的生效时间」**：
     * 越小 = 扎得越急、真伤结算越早（落速 = {@link #PHANTOM_HEIGHT} / 本值，自动联动）。
     * ⚠ 它**不影响「依次」的节奏**，节奏由 {@link #PHANTOM_INTERVAL} 单独控制。
     */
    private static final int PHANTOM_FALL_TICKS = 5;
    /** 单把幻影剑落地真伤 */
    private static final float PHANTOM_DAMAGE = 52.0f;
    /** 6 个落点绕目标的小幅散布半径（格），避免完全叠在一起看不出「一把一把」 */
    private static final double PHANTOM_SPREAD = 1.4;
    /**
     * 落点散布起始相位：-90° ⇒ 1 号剑落在目标**正北（−Z）** 1.4 格处，其余五把按 60° 依次排开。
     * ⚠ 这个六边形是**世界轴向固定**的，不随玩家视线旋转（只影响"绕圈的第几把在哪个方位"）。
     */
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

        // ---------- 形态交替：这一次轮到「斜光轰击」就只走光束那条路（6 连射 + 真伤） ----------
        if (takeBeamTurn(player)) {
            spawnBeam(player, serverLevel);
            return;
        }

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

    // ======================= 形态②：斜光轰击的参数 =======================
    /**
     * 一次释放射几道光束。
     * ★ 现在 = **1 道**：只打一束、**直接瞄准 SA 锁定的那个目标**（没有锁定才落在准星前方 6 格），
     * 落点不再随机散落、方向与长度也不再随机 ⇒ 是一记明确的"点名"而不是一轮光雨。
     * 想回到多道只要把这个值调大（>1 时下面自动恢复"随机散落 + 随机来向/长短"的写法）。
     */
    private static final int BEAM_COUNT = 1;
    /** ★ 相邻两道的间隔（tick）—— 「连射」的节奏靠它：5 = 0.25 秒一道（当前），10 = 半秒一道 */
    private static final int BEAM_INTERVAL = 5;
    /** 落点到目标脚下最近 / 最远的距离（格）—— 随机散落在这个环带里，**不排成规整多边形** */
    private static final double BEAM_SPREAD_MIN = .60, BEAM_SPREAD_MAX = 1.90;
    /** 长度倍率的随机范围（1.0 = 基准长度）—— 每道随机 ⇒ 长短不一，且每次释放都不一样 */
    private static final float BEAM_LEN_MIN = .70F, BEAM_LEN_MAX = 1.25F;
    /**
     * ★ 光束从生成到"砸到"命中点所需 tick —— **必须与 {@code IroiBeamGeometry.T_HIT} 一致**
     * （= 蓄势 {@code T_CHARGE 8} + 扫下 {@code T_SWEEP 5}），伤害才不会早于画面。
     */
    private static final int BEAM_HIT_DELAY = 13;
    /** 每道光束的伤害（真伤） */
    private static final float BEAM_DAMAGE = 52.0f;
    /** ★ 每道光束结算几次伤害（两次：命中瞬间一次 + 稍后再补一次） */
    private static final int BEAM_HIT_TIMES = 2;
    /** 两次结算之间的间隔（tick） */
    private static final int BEAM_HIT_GAP = 8;
    /** 伤害判定半径（格）—— 以落点为球心；单道时落点就是目标脚下，这个半径是"溅射"范围 */
    private static final double BEAM_HIT_RADIUS = 3.0;

    // ======================= 形态②：落点的紫色粒子 =======================
    /** 粒子颜色（紫）—— 用原版 redstone dust 粒子（`DustParticleOptions`），**不新增自定义粒子类型** */
    private static final Vector3f PARTICLE_COLOR = new Vector3f(.72F, .30F, 1.00F);
    /** 粒子大小 */
    private static final float PARTICLE_SCALE = 1.30F;
    /** 落地瞬间撒几颗 */
    private static final int PARTICLE_BURST = 16;
    /** 之后每 tick 补几颗（往外散） */
    private static final int PARTICLE_PER_TICK = 4;
    /** 继续补几 tick */
    private static final int PARTICLE_TICKS = 7;

    /** 收尾光球群的散布半径（格）—— 8 个球飘在这个圈里，2 秒里边漂边散、最后一起消散 */
    private static final float ORB_SPREAD = 2.0F;

    // ======================= 形态②：光柱里的原版紫尘（代替代码画的"上升符文"） =======================
    /** 光束与地面的夹角（度）—— ⚠ 必须与 {@code IroiBeamGeometry.PITCH_DEG} 一致，粒子才对得上光柱 */
    private static final double BEAM_PITCH_DEG = 68.0;
    /** 命中后撒多少 tick、每 tick 几颗 */
    private static final int DUST_TICKS = 18, DUST_PER_TICK = 3;
    /** 沿光轴的范围（全长比例，0 = 落点、1 = 炮口）—— 只取下面一段，太高的粒子看不见 */
    private static final double DUST_T_MIN = .04, DUST_T_MAX = .42;
    /** 横向抖动（格）与粒子大小 */
    private static final double DUST_JITTER = .26;
    private static final float DUST_SCALE = 1.10F;

    /**
     * 射 {@link #BEAM_COUNT} 道光束（目前 **1 道**）—— **直接砸在瞄准点上**：
     * 瞄准点 = SA 锁定的那个目标**脚下**（没有锁定才用准星前方 6 格），所以是一记"点名式的天降打击"，
     * 不再像之前 6 道那样随机散落成一轮光雨。来向仍是随机的（从天上任意一侧插下来）。
     *
     * <p>每道光束砸到落点后结算 {@link #BEAM_DAMAGE} 真伤 × {@link #BEAM_HIT_TIMES} 次
     * （第一次与"砸到"同刻，第二次 {@link #BEAM_HIT_GAP} tick 后补）。
     */
    private static void spawnBeam(ServerPlayer player, ServerLevel level) {
        Vec3 landing = resolveBeamHit(player, level);
        final int now = level.getServer().getTickCount();
        final boolean single = BEAM_COUNT <= 1;
        for (int i = 0; i < BEAM_COUNT; i++) {
            // ★ 单道（BEAM_COUNT = 1）：**精确落在瞄准点上**（= 锁定的目标脚下），不散开、不定长
            //   —— "瞄准锁定的目标"就靠这一段；多道时才恢复随机散落（避免 6 道糊成一道）
            double ang = level.random.nextDouble() * Math.PI * 2.0;
            double dist = single ? 0.0
                    : BEAM_SPREAD_MIN
                    + level.random.nextDouble() * (BEAM_SPREAD_MAX - BEAM_SPREAD_MIN);
            final Vec3 hit = landing.add(Math.cos(ang) * dist, 0.0, Math.sin(ang) * dist);
            // ★ 来向随机（光从天上任意一侧插下来，落在哪儿是上面定死的）
            final float yaw = level.random.nextFloat() * 360F;
            final float lenScale = single ? 1.0F
                    : BEAM_LEN_MIN + level.random.nextFloat() * (BEAM_LEN_MAX - BEAM_LEN_MIN);
            final int fire = now + i * BEAM_INTERVAL;
            level.getServer().tell(new TickTask(fire,
                    () -> EntityIroiBeam.spawn(level, hit, yaw, lenScale)));
            // 落地那一刻：伤害结算 + 紫色粒子散开
            level.getServer().tell(new TickTask(fire + BEAM_HIT_DELAY,
                    () -> beamParticles(level, hit)));
            // 命中后：光柱里浮起原版紫尘（替代原来代码画的"上升符文"）
            level.getServer().tell(new TickTask(fire + BEAM_HIT_DELAY,
                    () -> beamDust(level, hit, yaw)));
            for (int k = 0; k < BEAM_HIT_TIMES; k++) {
                final int at = fire + BEAM_HIT_DELAY + k * BEAM_HIT_GAP;
                level.getServer().tell(new TickTask(at, () -> dealBeamDamage(level, player, hit)));
            }
        }
        // 收尾：落点中心飘起 8 个紫色光球（上下漂浮，2 秒后消散）
        level.getServer().tell(new TickTask(now + BEAM_HIT_DELAY + 2,
                () -> EntityIroiOrb.spawn(level, landing, ORB_SPREAD)));
    }

    /**
     * 光柱里的**原版紫尘**：命中后沿光轴从下往上浮起一小段（原版 redstone dust，与落点那簇同一套）。
     *
     * <p>★ 2026-09-26 第四轮：**替代原来代码画的"上升符文"** —— 交给原版粒子管道，省顶点、也更像"被灼起的尘埃"。
     * 方位角/仰角用的是 {@code IroiBeamGeometry} 那套（仰角 {@link #BEAM_PITCH_DEG}），所以粒子正好落在光柱里。
     */
    private static void beamDust(ServerLevel level, Vec3 hit, float yawDeg) {
        final double yaw = Math.toRadians(yawDeg), pit = Math.toRadians(BEAM_PITCH_DEG);
        final double dx = -Math.sin(yaw) * Math.cos(pit);
        final double dy = Math.sin(pit);
        final double dz = Math.cos(yaw) * Math.cos(pit);
        final int now = level.getServer().getTickCount();
        for (int t = 0; t < DUST_TICKS; t++) {
            level.getServer().tell(new TickTask(now + t, () -> {
                for (int k = 0; k < DUST_PER_TICK; k++) {
                    double along = (DUST_T_MIN + level.random.nextDouble() * (DUST_T_MAX - DUST_T_MIN))
                            * EntityIroiBeam.BEAM_LENGTH;
                    double jx = (level.random.nextDouble() - .5) * DUST_JITTER;
                    double jy = (level.random.nextDouble() - .5) * DUST_JITTER;
                    double jz = (level.random.nextDouble() - .5) * DUST_JITTER;
                    level.sendParticles(new DustParticleOptions(PARTICLE_COLOR, DUST_SCALE),
                            hit.x + dx * along + jx, hit.y + dy * along + jy, hit.z + dz * along + jz,
                            1, 0.0, 0.06, 0.0, 0.02);      // 略微向上飘
                }
            }));
        }
    }

    /**
     * 落点的**紫色粒子**：落地瞬间来一小簇，随后几 tick 继续往外散。
     * 用原版 {@code DustParticleOptions}（redstone dust）上色，数量刻意少 —— "一点"就好。
     */
    private static void beamParticles(ServerLevel level, Vec3 hit) {
        final int now = level.getServer().getTickCount();
        for (int t = 0; t < PARTICLE_TICKS; t++) {
            final int idx = t;
            level.getServer().tell(new TickTask(now + idx, () -> {
                boolean burst = idx == 0;
                level.sendParticles(new DustParticleOptions(PARTICLE_COLOR, PARTICLE_SCALE),
                        hit.x, hit.y + .12, hit.z,
                        burst ? PARTICLE_BURST : PARTICLE_PER_TICK,
                        .30, .20, .30,
                        burst ? .10 : .045);
            }));
        }
    }

    /**
     * 一道光束"砸到"时的真伤结算：落点 {@link #BEAM_HIT_RADIUS} 格内的**所有**生物各吃
     * {@link #BEAM_DAMAGE} 真伤（不含施法者与旁观者；友方 NPC 由 {@code applyTrueDamage} 内部豁免）。
     */
    private static void dealBeamDamage(ServerLevel level, ServerPlayer player, Vec3 hit) {
        final double r = BEAM_HIT_RADIUS;
        AABB box = new AABB(hit, hit).inflate(r);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                x -> x != player && x.isAlive() && !x.isRemoved() && !x.isSpectator())) {
            if (e.getBoundingBox().getCenter().distanceToSqr(hit) > r * r) continue;
            SkydeitySlash.GameEvents.applyTrueDamage(e, player, BEAM_DAMAGE);
        }
    }

    /**
     * 光束的**落地**点 —— "天上打击地面" ⇒ 落点取**地面高度**（而不是悬在半空的身体中心）：
     * 锁定目标时取它**脚下** 0.15 格；没有锁定则取玩家脚下高度、准星前方 6 格。
     */
    private static Vec3 resolveBeamHit(ServerPlayer player, ServerLevel level) {
        ISlashBladeState state = player.getMainHandItem()
                .getCapability(CapabilitySlashBlade.BLADESTATE, null).orElse(null);
        Entity tgt = state != null ? state.getTargetEntity(level) : null;
        if (tgt instanceof LivingEntity tl && tl.isAlive() && !tl.isRemoved()) {
            return new Vec3(tl.getX(), tl.getY() + 0.15, tl.getZ());
        }
        Vec3 fwd = player.getLookAngle().normalize().scale(6.0);
        return new Vec3(player.getX() + fwd.x, player.getY() + 0.15, player.getZ() + fwd.z);
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
