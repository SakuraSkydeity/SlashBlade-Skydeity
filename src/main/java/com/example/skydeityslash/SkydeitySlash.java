package com.example.skydeityslash;

import com.example.skydeityslash.client.ModClientEvents;
import com.example.skydeityslash.entity.FurinaNpcEntity;
import com.example.skydeityslash.entity.EntityXuanfengWave;
import com.example.skydeityslash.entity.EntityInkSwarm;
import com.example.skydeityslash.entity.EntityRainUmbrella;
import com.example.skydeityslash.event.BladeDropHandler;
import com.example.skydeityslash.dimension.MandaravaDimension;
import com.example.skydeityslash.particle.VanillaEffectSpawner;
import com.example.skydeityslash.registry.ModComboStates;
import com.example.skydeityslash.registry.ModSounds;
import com.example.skydeityslash.registry.ModCreativeTabs;
import com.example.skydeityslash.registry.ModEffects;
import com.example.skydeityslash.registry.ModEntities;
import com.example.skydeityslash.registry.ModItems;
import com.example.skydeityslash.registry.ModBlocks;
import com.example.skydeityslash.registry.ModBlockEntities;
import com.example.skydeityslash.registry.ModRecipes;
import com.example.skydeityslash.registry.ModSlashArts;
import com.example.skydeityslash.registry.ModSpecialEffects;
import com.mojang.logging.LogUtils;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.SlashBlade.RegistryEvents;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.event.SlashBladeRegistryEvent;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import mods.flammpfeil.slashblade.util.KnockBacks;
import mods.flammpfeil.slashblade.slasharts.Drive;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 拔刀剑附属主类。
 * 所有拔刀剑自定义注册表（剑技 SA / 连击 ComboState / 特效 SE）
 * 都通过 NeoForge 的 DeferredRegister 挂到 mod 事件总线上。
 */
@Mod(SkydeitySlash.MODID)
public class SkydeitySlash {
    public static final String MODID = "skydeityslash";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SkydeitySlash() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModSlashArts.SLASH_ARTS.register(modEventBus);
        ModComboStates.COMBO_STATES.register(modEventBus);
        ModSpecialEffects.SPECIAL_EFFECTS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModRecipes.TYPES.register(modEventBus);
        ModRecipes.SERIALIZERS.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModEffects.EFFECTS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        modEventBus.addListener(GameEvents::onRegisterAttributes);
        if (FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(ModClientEvents::onRegisterRenderers);
            MinecraftForge.EVENT_BUS.addListener(GameEvents::onItemTooltip);
        }
        MinecraftForge.EVENT_BUS.addListener(GameEvents::onBladeHit);
        MinecraftForge.EVENT_BUS.addListener(GameEvents::onPhantomSwordColor);
        MinecraftForge.EVENT_BUS.addListener(GameEvents::onSlashArcColor);
        MinecraftForge.EVENT_BUS.addListener(GameEvents::onSummonedSwordHit);
        MinecraftForge.EVENT_BUS.addListener(GameEvents::onBladeCreated);
        MinecraftForge.EVENT_BUS.addListener(GameEvents::onUpdate);
        MinecraftForge.EVENT_BUS.addListener(GameEvents::onUpdateAttack);
        MinecraftForge.EVENT_BUS.addListener(GameEvents::onLivingIncomingDamage);
        MinecraftForge.EVENT_BUS.addListener(GameEvents::onLivingDeath);
        MinecraftForge.EVENT_BUS.addListener(GameEvents::onLivingDamagePost);
        MinecraftForge.EVENT_BUS.addListener(GameEvents::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(GameEvents::onInputCommand);
        MinecraftForge.EVENT_BUS.addListener(GameEvents::onDoSlash);
        MinecraftForge.EVENT_BUS.addListener(GameEvents::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(BladeDropHandler::onItemToss);
        MinecraftForge.EVENT_BUS.addListener(BladeDropHandler::onItemEntityJoin);
        MinecraftForge.EVENT_BUS.addListener(BladeDropHandler::onInteractNpc);
        MinecraftForge.EVENT_BUS.addListener(MandaravaDimension::onServerAboutToStart);
        MinecraftForge.EVENT_BUS.addListener(MandaravaDimension::onEntityJoin);
        MinecraftForge.EVENT_BUS.addListener(MandaravaDimension::onLevelLoad);
        MinecraftForge.EVENT_BUS.addListener(MandaravaDimension::onRegisterCommands);
    }

    public static ResourceLocation prefix(String path) {
        return new ResourceLocation(MODID, path);
    }

    /**
     * 拔刀剑命中事件处理。
     */
    public static class GameEvents {
        /** 自定义实体属性注册（如芙宁娜人形 NPC） */
        public static void onRegisterAttributes(EntityAttributeCreationEvent event) {
            event.put(ModEntities.FURINA_NPC.get(), Mob.createMobAttributes().build());
            event.put(ModEntities.HUTAO_NPC.get(), Mob.createMobAttributes().build());
            event.put(ModEntities.ODETTE_NPC.get(), Mob.createMobAttributes().build());
            event.put(ModEntities.LINNEA_NPC.get(), Mob.createMobAttributes().build());
            event.put(ModEntities.COLUMBINA_NPC.get(), Mob.createMobAttributes().build());
            event.put(ModEntities.IROI_NPC.get(), Mob.createMobAttributes().build());
        }

        /** 幽蝶能留一缕芳：记录玩家位置与静止计时 */
        private static final Map<UUID, Vec3> PLUM_LAST_POS = new HashMap<>();
        private static final Map<UUID, Integer> PLUM_STILL_TICKS = new HashMap<>();
        /** 幽蝶能留一缕芳：本轮静止周期是否已触发过（移动后才可再次触发） */
        private static final Map<UUID, Boolean> PLUM_ACTIVATED = new HashMap<>();

        /** 幻灵夜舞：记录连续手持 tick 数（用于伤害随时间增长） */
        private static final Map<UUID, Long> PHANTOM_ACTIVE_TICKS = new HashMap<>();

        /** 流涌灵息之刺：记录效果激活的截止游戏时间（tick） */
        private static final Map<UUID, Long> FLOW_RUSH_ACTIVE = new HashMap<>();
        /** 流涌灵息之刺：防止魔法伤害递归触发的保护标志 */
        private static boolean flowRushApplying = false;

        /** 彼岸蝶舞：记录效果激活的截止游戏时间（tick） */
        private static final Map<UUID, Long> BUTTERFLY_DANCE_ACTIVE = new HashMap<>();

        /** 「触及苍穹永恒的面容」：记录效果激活的截止游戏时间（tick） */
        private static final Map<UUID, Long> SKYWARD_ACTIVE = new HashMap<>();

        /** 钤印伞：玩家 → 当前存活的伞特效实体 id 列表。伞是惰性特效实体（纯视觉，非物理/战斗实体）。 */
        private static final Map<UUID, List<Integer>> UMBRELLA_ENTITIES = new HashMap<>();
        /** 银簪·尽弑天下负心人：记录玩家位置与静止计时（复用幽蝶的静止检测框架） */
        private static final Map<UUID, Vec3> SLAYER_LAST_POS = new HashMap<>();
        private static final Map<UUID, Integer> SLAYER_STILL_TICKS = new HashMap<>();
        /** 「触及苍穹永恒的面容」：扩散等额真实伤害时的递归保护开关 */
        private static boolean skywardSpreading = false;

        /** 雨间蝶舞伞犹温：固定 UUID（生命/护甲/攻击力三种修饰符） */
        private static final UUID RAIN_HEALTH_ID = UUID.fromString("0f6c1f88-1a2b-4c3d-8e4f-1a2b3c4d5e6a");
        private static final UUID RAIN_ARMOR_ID = UUID.fromString("1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d");
        private static final UUID RAIN_ATTACK_ID = UUID.fromString("2b3c4d5e-6f7a-8b9c-0d1e-2f3a4b5c6d7e");
        /** 雨间蝶舞伞犹温：持续增加攻击力的计时与当前倍率 */
        private static final Map<UUID, Long> RAIN_ATTACK_TICKS = new HashMap<>();
        private static final Map<UUID, Long> RAIN_ATTACK_AMP = new HashMap<>();

        /** 幻影剑逐发循环色：每出一把召唤剑推进一步（墨绿→绿→蓝绿 3 停渐进循环） */
        private static final Map<UUID, Integer> PHANTOM_COLOR_STEP = new HashMap<>();

        /** 离恨烟·公孙离：玩家携带该 SE 时的离恨层数（手持每 0.5s +1，上限 100） */
        private static final Map<UUID, Integer> GONGSUN_LAYERS = new HashMap<>();

        /** 仙乡的赠别礼：露米 buff 攻击力瞬态修饰符 ID */
        private static final UUID LUMI_ATTACK_ID = UUID.fromString("3c4d5e6f-7a8b-9c0d-1e2f-3a4b5c6d7e8f");
        /** 仙乡的赠别礼：携带该 SE 时累计的露米持续时长（tick）与当前攻击加成 */
        private static final Map<UUID, Long> LUMI_HELD_TICKS = new HashMap<>();
        private static final Map<UUID, Float> LUMI_ATTACK_BONUS = new HashMap<>();

        /** 柔光凝露梦湖起波：SPRINT 激活后的生效截止时间（gameTime） */
        private static final Map<UUID, Long> ROUGUANG_ACTIVE = new HashMap<>();
        private static boolean rouguangApplying = false;
        /** 诺德卡莱 SA：frostNova 计划在该玩家第 N 个 tick 时于玩家身处释放（用实体 tickCount 计时） */
        private static final Map<UUID, Integer> COLUMBINA_NOVA_TICK = new HashMap<>();
        /** 新月自己的法则：圆环从小到大扩散动画的当前半径步（0..10，用后自删） */
        private static final Map<UUID, Integer> NEWMOON_RING_STEP = new HashMap<>();
        /** 诺德卡莱 SA 剑气追踪：剑气实体ID -> 锁定目标实体ID（命中/消散/目标死亡即自删） */
        private static final Map<Integer, Integer> COLUMBINA_HOMING = new HashMap<>();
        /** 诺德卡莱随行环：10 格半径环随玩家移动生效截止时间（gameTime），重复使用 SA 刷新到当前 + 5 秒 */
        private static final Map<UUID, Long> COLUMBINA_RING_DEADLINE = new HashMap<>();

        /** sakurafox：右键挥砍时刀身墨色（每次挥砍随机换一色、与上次不同），未挥砍前默认墨绿 0x145F31 */
        private static final Map<UUID, Integer> SAKURA_SWING_COLOR = new HashMap<>();
        /** sakurafox：挥砍可选刀身墨色 —— 墨绿 / 暗绿 / 深蓝 */
        private static final int[] SAKURA_SWING_COLORS = {0x145F31, 0x0E3B22, 0x123B6B};

        /** 妄想彼端的森林萤火：SPRINT 激活后的生效截止时间（gameTime），激活期间每 20 刀对锁定目标结算一次 52 真伤并加紫弧 */
        private static final Map<UUID, Long> FOREST_FIREFLY_ACTIVE = new HashMap<>();

        /** 未来自我连续性假设：累计攻击命中次数，每 3 次放一道十字剑气（用后清零） */
        private static final Map<UUID, Integer> IROI_CROSS_SLASH_COUNT = new HashMap<>();

        /** 花开见血血染双瞳：蓄势后剩余的「额外真伤」次数（每次命中 −1，用完即清） */
        private static final Map<UUID, Integer> ZANKOU_BLOOM_CHARGES = new HashMap<>();
        /** 花开见血血染双瞳：蓄势的有效截止时间（gameTime）—— 防止挂机时把次数一直留着 */
        private static final Map<UUID, Long> ZANKOU_BLOOM_DEADLINE = new HashMap<>();
        /** 花开见血血染双瞳：蓄势后带伤的攻击次数 */
        private static final int ZANKOU_BLOOM_CHARGES_MAX = 3;
        /** 花开见血血染双瞳：蓄势窗口（tick）= 30 秒内打完 3 次 */
        private static final int ZANKOU_BLOOM_WINDOW = 600;
        /** 花开见血血染双瞳：每次攻击额外结算的真伤 */
        private static final float ZANKOU_BLOOM_DAMAGE = 52.0f;
        /** 花开见血血染双瞳：蓄势后刀身暗红 */
        private static final int ZANKOU_BLOOM_COLOR = 0x9B0E22;

        /** 瞳中深渊渊底之吻：累计命中次数 —— 每 3 次在目标身上绽开一朵几何樱花（用后清零） */
        private static final Map<UUID, Integer> ZANKOU_ABYSS_COUNT = new HashMap<>();
        /** 瞳中深渊渊底之吻：每 3 次命中额外结算的真伤 */
        private static final float ZANKOU_ABYSS_DAMAGE = 52.0f;
        /** 瞳中深渊渊底之吻：每次命中回复的生命（heal 本身会被钳到最大生命，这里再加一道保险） */
        private static final float ZANKOU_ABYSS_HEAL = 2.0f;
        /** 瞳中深渊渊底之吻：樱花绽开时目标脚下撒几片花瓣 */
        private static final int ZANKOU_ABYSS_PETALS = 10;

        /** 吻痕窥梦梦魇生花：累计命中次数 —— 每 7 次划出横切割刀痕（用后清零） */
        private static final Map<UUID, Integer> ZANKOU_DREAM_COUNT = new HashMap<>();
        /** 吻痕窥梦梦魇生花：几次命中触发一次 */
        private static final int ZANKOU_DREAM_EVERY = 7;
        /** 吻痕窥梦梦魇生花：给目标施加的「缓慢」等级（10 级 = amplifier 9） */
        private static final int ZANKOU_DREAM_SLOW_AMP = 9;
        /** 吻痕窥梦梦魇生花：缓慢持续（tick）—— 6 秒 */
        private static final int ZANKOU_DREAM_SLOW_TICKS = 120;
        /** 吻痕窥梦梦魇生花：触发后**每秒**结算的真伤（流血） */
        private static final float ZANKOU_DREAM_BLEED_DAMAGE = 52.0f;
        /** 吻痕窥梦梦魇生花：每秒结算持续几秒 —— 与「缓慢」同为 6 秒 */
        private static final int ZANKOU_DREAM_BLEED_SECONDS = 6;

        /** 森林萤火：花株生成后固定的原地位置（不跟随玩家），距玩家过远才重置于身前；效果结束即清除 */
        private static final Map<UUID, Vec3> FOREST_FLOWER_POS = new HashMap<>();

        private static boolean isForestFireflyActive(Player player) {
            Long deadline = FOREST_FIREFLY_ACTIVE.get(player.getUUID());
            return deadline != null && deadline > player.level().getGameTime();
        }

        /** 花开见血血染双瞳：蓄势次数是否还有效（超时则自行清除） */
        private static boolean hasZankouBloomCharges(Player player) {
            Integer c = ZANKOU_BLOOM_CHARGES.get(player.getUUID());
            if (c == null || c <= 0) return false;
            Long deadline = ZANKOU_BLOOM_DEADLINE.get(player.getUUID());
            if (deadline == null || deadline <= player.level().getGameTime()) {
                ZANKOU_BLOOM_CHARGES.remove(player.getUUID());
                ZANKOU_BLOOM_DEADLINE.remove(player.getUUID());
                return false;
            }
            return true;
        }

        /** 花开见血血染双瞳：消耗一次蓄势（用完清除） */
        private static void consumeZankouBloom(Player player) {
            int left = ZANKOU_BLOOM_CHARGES.getOrDefault(player.getUUID(), 1) - 1;
            if (left <= 0) {
                ZANKOU_BLOOM_CHARGES.remove(player.getUUID());
                ZANKOU_BLOOM_DEADLINE.remove(player.getUUID());
            } else {
                ZANKOU_BLOOM_CHARGES.put(player.getUUID(), left);
            }
        }

        /** 注册一道名义卡莱剑气锁定指定目标 */
        public static void registerColumbinaHoming(int bladeId, int targetId) {
            COLUMBINA_HOMING.put(bladeId, targetId);
        }

        /** 每 tick 让名义卡莱剑气朝锁定目标的实时位置转向，实现「锁定追踪」，命中时对目标造成 52 真伤 */
        private static void tickColumbinaHoming(ServerLevel level) {
            if (COLUMBINA_HOMING.isEmpty()) return;
            var it = COLUMBINA_HOMING.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                Entity blade = level.getEntity(e.getKey());
                if (!(blade instanceof mods.flammpfeil.slashblade.entity.EntityDrive dr) || !dr.isAlive()) {
                    it.remove();
                    continue;
                }
                Entity tg = level.getEntity(e.getValue());
                if (!(tg instanceof LivingEntity le) || !le.isAlive()) {
                    it.remove();
                    continue;
                }
                Vec3 to = le.getBoundingBox().getCenter().subtract(dr.position());
                double dist = to.length();
                if (dist < 1.4) {  // 命中锁定目标：结算一次 52 真伤并消散剑气
                    applyTrueDamage(le, dr.getShooter(), 52.0f);
                    dr.discard();
                    it.remove();
                    continue;
                }
                double sp = dr.getSpeed();
                dr.setDeltaMovement(to.normalize().scale(sp));
            }
        }

        private static boolean isFlowRushActive(Player player) {
            Long deadline = FLOW_RUSH_ACTIVE.get(player.getUUID());
            return deadline != null && deadline > player.level().getGameTime();
        }

        private static boolean isButterflyDanceActive(Player player) {
            Long deadline = BUTTERFLY_DANCE_ACTIVE.get(player.getUUID());
            return deadline != null && deadline > player.level().getGameTime();
        }

        private static boolean isSkywardActive(Player player) {
            Long deadline = SKYWARD_ACTIVE.get(player.getUUID());
            return deadline != null && deadline > player.level().getGameTime();
        }

        private static boolean isRouguangActive(Player player) {
            Long deadline = ROUGUANG_ACTIVE.get(player.getUUID());
            return deadline != null && deadline > player.level().getGameTime();
        }

        /** 诺德卡莱 SA：把 frostNova 安排到玩家 tick + NOVA_DELAY 后触发（保证「两个粒子一个放完再放另一个」） */
        public static void scheduleColumbinaNova(ServerPlayer player) {
            COLUMBINA_NOVA_TICK.put(player.getUUID(), player.tickCount + 22);
        }

        /** 诺德卡莱随行环：激活即把时长刷新为当前 + 5 秒（最长 5 秒），环随玩家移动并在期间施加增益/减益 */
        public static void activateColumbinaRing(ServerPlayer player) {
            COLUMBINA_RING_DEADLINE.put(player.getUUID(), player.level().getGameTime() + 100);
        }

        /** 彼岸蝶舞：给目标叠加一层血梅香（等级 +1、持续刷新 5 秒），等级为 5 的倍数时爆炸扣除 50% 最大生命 */
        private static void applyBloodPlum(Player attacker, LivingEntity target) {
            MobEffectInstance current = target.getEffect(ModEffects.BLOOD_PLUM.get());
            int currentLevel = current != null ? current.getAmplifier() + 1 : 0;
            int newLevel = currentLevel + 1;
            // 持续获得 buff 增加等级，持续时间始终刷新为 5 秒
            target.addEffect(new MobEffectInstance(ModEffects.BLOOD_PLUM.get(), 100, newLevel - 1));
            if (newLevel % 5 == 0) {
                // 等级为 5 的倍数：爆炸伤害，直接扣除 50% 当前生命（真伤）
                applyTrueDamage(target, attacker, target.getHealth() * 0.5f);
                if (target.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(new DustParticleOptions(new Vector3f(1.0f, 0.1f, 0.2f), 2.0f),
                            target.getX(), target.getY() + 1.0, target.getZ(), 80, 0.6, 0.6, 0.6, 0.1);
                }
            } else {
                if (target.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(new DustParticleOptions(new Vector3f(1.0f, 0.3f, 0.4f), 1.2f),
                            target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.4, 0.4, 0.4, 0.05);
                }
            }
        }

        /** 仙乡的赠别礼：给玩家自身叠加一层露米（等级 +1、持续刷新 5 秒），上限 10 级 */
        private static void applyLumi(Player player) {
            MobEffectInstance current = player.getEffect(ModEffects.LUMI.get());
            int currentLevel = current != null ? current.getAmplifier() + 1 : 0;
            int newLevel = Math.min(currentLevel + 1, 10);
            // 每次攻击刷新持续时间 + 提升等级
            player.addEffect(new MobEffectInstance(ModEffects.LUMI.get(), 100, newLevel - 1));
        }

        /** 移除仙乡的赠别礼施加的攻击力修饰符 */
        private static void removeLumiModifier(Player player) {
            if (player.getAttribute(Attributes.ATTACK_DAMAGE) != null)
                player.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(LUMI_ATTACK_ID);
        }

        /** 流涌灵息之刺 / 彼岸蝶舞：按下特殊移动键时激活对应效果（10 秒） */
        public static void onInputCommand(InputCommandEvent event) {
            ServerPlayer player = event.getEntity();
            ItemStack stack = player.getMainHandItem();
            ISlashBladeState state = stack.getCapability(CapabilitySlashBlade.BLADESTATE, null).orElse(null);
            if (state == null) return;
            if (!event.getCurrent().contains(InputCommand.SPRINT) || event.getOld().contains(InputCommand.SPRINT)) return;
            // 令花神：特殊行动 + W（前）→ 伞留在原地，原地浓郁水墨山水；+ A（左）→ 向前推伞 6 格（各模式再按对应键传送）
            boolean hasBlossom = state.hasSpecialEffect(ModSpecialEffects.SEAL_BLOSSOM_EFFECT.getId())
                    && SpecialEffect.isEffective(ModSpecialEffects.SEAL_BLOSSOM_EFFECT.getId(), player.experienceLevel);
            if (hasBlossom && event.getCurrent().contains(InputCommand.FORWARD)) {
                handleUmbrellaPress(player, EntityRainUmbrella.MODE_STAY);
            }
            if (hasBlossom && event.getCurrent().contains(InputCommand.LEFT)) {
                handleUmbrellaPress(player, EntityRainUmbrella.MODE_BEACON);
            }

            // 钤印伞·芙蓉花琼楼吹彻笛声寒：特殊行动 + S（后退）→ 环绕旋转；+ D（向右）→ 投伞上浮
            // 两个分支各自独立判定按键（S 与 D 互不依赖），持有该 SE 即可用
            boolean hasSealLotus = state.hasSpecialEffect(ModSpecialEffects.SEAL_LOTUS_EFFECT.getId())
                    && SpecialEffect.isEffective(ModSpecialEffects.SEAL_LOTUS_EFFECT.getId(), player.experienceLevel);
            if (hasSealLotus && event.getCurrent().contains(InputCommand.BACK)) {
                handleUmbrellaPress(player, EntityRainUmbrella.MODE_ORBIT);
            }
            if (hasSealLotus && event.getCurrent().contains(InputCommand.RIGHT)) {
                handleUmbrellaPress(player, EntityRainUmbrella.MODE_THROW);
            }

            // 流涌灵息之刺：10 秒内攻击转为两倍真实伤害，刀身发出蓝光
            if (state.hasSpecialEffect(ModSpecialEffects.FLOW_RUSH_EFFECT.getId())
                    && SpecialEffect.isEffective(ModSpecialEffects.FLOW_RUSH_EFFECT.getId(), player.experienceLevel)) {
                FLOW_RUSH_ACTIVE.put(player.getUUID(), player.level().getGameTime() + 200);
                state.setEffectColor(new Color(0xAFE9FF));
                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(new DustParticleOptions(new Vector3f(0.69f, 0.91f, 1.0f), 1.5f),
                            player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.5, 0.5, 0.5, 0.1);
                }
            }

            // 彼岸蝶舞：10 秒内攻击给目标叠加血梅香，刀身发出红光
            if (state.hasSpecialEffect(ModSpecialEffects.BUTTERFLY_DANCE_EFFECT.getId())
                    && SpecialEffect.isEffective(ModSpecialEffects.BUTTERFLY_DANCE_EFFECT.getId(), player.experienceLevel)) {
                BUTTERFLY_DANCE_ACTIVE.put(player.getUUID(), player.level().getGameTime() + 200);
                state.setEffectColor(new Color(0xFF6B6B));
                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(new DustParticleOptions(new Vector3f(1.0f, 0.2f, 0.3f), 1.5f),
                            player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.5, 0.5, 0.5, 0.1);
                }
            }

            // 触及苍穹永恒的面容：按下特殊移动键激活，10 秒内效果生效
            if (state.hasSpecialEffect(ModSpecialEffects.SKYWARD_VISAGE_EFFECT.getId())
                    && SpecialEffect.isEffective(ModSpecialEffects.SKYWARD_VISAGE_EFFECT.getId(), player.experienceLevel)) {
                SKYWARD_ACTIVE.put(player.getUUID(), player.level().getGameTime() + 200);
                if (player.level() instanceof ServerLevel serverLevel) {
                    // 闪光粒子：白色为主，少量奶白色亮蓝色
                    serverLevel.sendParticles(new DustParticleOptions(new Vector3f(1.0f, 1.0f, 1.0f), 1.5f),
                            player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.5, 0.5, 0.5, 0.1);
                    serverLevel.sendParticles(new DustParticleOptions(new Vector3f(0.76f, 0.85f, 1.0f), 1.5f),
                            player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.6, 0.6, 0.6, 0.12);
                }
            }

            // 喜或悲的谕告：按下特殊行动，推开周围 5 格内所有生物（复用通用击退工具）
            if (state.hasSpecialEffect(ModSpecialEffects.JOY_SORROW_OMEN_EFFECT.getId())
                    && SpecialEffect.isEffective(ModSpecialEffects.JOY_SORROW_OMEN_EFFECT.getId(), player.experienceLevel)) {
                int pushed = knockbackNearbyEntities(player.level(), player.position(), 5.0, 1.8, player);
                if (player.level() instanceof ServerLevel serverLevel && pushed > 0) {
                    serverLevel.sendParticles(new DustParticleOptions(new Vector3f(0.9f, 0.96f, 1.0f), 1.2f),
                            player.getX(), player.getY() + 1.0, player.getZ(), 24, 5.0, 5.0, 5.0, 0.2);
                }
            }

            // 柔光凝露梦湖起波：按下特殊行动激活，10 秒内攻击附带目标 1% 最大生命真实伤害，刀身浅蓝光
            if (state.hasSpecialEffect(ModSpecialEffects.ROUGUANG_NINGLU_EFFECT.getId())
                    && SpecialEffect.isEffective(ModSpecialEffects.ROUGUANG_NINGLU_EFFECT.getId(), player.experienceLevel)) {
                ROUGUANG_ACTIVE.put(player.getUUID(), player.level().getGameTime() + 200);
                state.setEffectColor(new Color(0xAAE5FF));
                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(new DustParticleOptions(new Vector3f(0.67f, 0.9f, 1.0f), 1.5f),
                            player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.5, 0.5, 0.5, 0.1);
                }
            }

            // 妄想彼端的森林萤火：按下特殊行动键激活，10 秒内周身 sculk_soul 粒子 + 身边一株粉紫花，每 20 刀对锁定目标结算 52 真伤紫色弧线
            if (state.hasSpecialEffect(ModSpecialEffects.IROI_SE50.getId())
                    && SpecialEffect.isEffective(ModSpecialEffects.IROI_SE50.getId(), player.experienceLevel)) {
                FOREST_FIREFLY_ACTIVE.put(player.getUUID(), player.level().getGameTime() + 200);
                state.setEffectColor(new Color(0xFF55FF));
                if (player.level() instanceof ServerLevel serverLevel) {
                    // 按下瞬间只在 1 秒内出现一小批 soul 粒子，随即消散（不做 10 秒持续）
                    serverLevel.sendParticles(ParticleTypes.SCULK_SOUL,
                            player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.5, 0.6, 0.5, 0.04);
                    serverLevel.sendParticles(new DustParticleOptions(new Vector3f(1.0f, 0.33f, 1.0f), 1.1f),
                            player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.6, 0.6, 0.6, 0.06);
                    // 激活瞬间立即在前方生出一株粉紫花，花持续可见
                    spawnForestFireflyFlower(serverLevel, player);
                }
            }

            // 花开见血血染双瞳：按下特殊行动键蓄势 → 接下来 3 次攻击各额外结算 52 真伤（暗红血花），30 秒内打完
            if (state.hasSpecialEffect(ModSpecialEffects.ZANKOU_SE50.getId())
                    && SpecialEffect.isEffective(ModSpecialEffects.ZANKOU_SE50.getId(), player.experienceLevel)) {
                ZANKOU_BLOOM_CHARGES.put(player.getUUID(), ZANKOU_BLOOM_CHARGES_MAX);
                ZANKOU_BLOOM_DEADLINE.put(player.getUUID(), player.level().getGameTime() + ZANKOU_BLOOM_WINDOW);
                state.setEffectColor(new Color(ZANKOU_BLOOM_COLOR));
                if (player.level() instanceof ServerLevel serverLevel) {
                    // 蓄势瞬间：在锁定目标（没有则身前 3 格）绽开一次同样的暗红刀痕（几何弧线，非粒子）
                    Entity bloomAim = state.getTargetEntity(serverLevel);
                    Vec3 bloomCenter = (bloomAim != null && bloomAim.isAlive() && !bloomAim.isRemoved())
                            ? bloomAim.position().add(0.0, bloomAim.getBbHeight() * 0.5, 0.0)
                            : player.getEyePosition().add(player.getLookAngle().scale(3.0));
                    com.example.skydeityslash.entity.EntityBloomSlash.spawn(
                            serverLevel, bloomCenter, player.getLookAngle());
                }
            }
        }

        /**
         * 真正的真伤：直接扣除目标生命值，完全跳过护甲/抗性/保护附魔/无敌帧及任何「限伤」计算，
         * 不经过 hurt() 伤害管线。血量 ≤0 时直接结算致死。
         */
        public static void applyTrueDamage(LivingEntity target, Entity attacker, float amount) {
            if (target == null || target.level().isClientSide) return;
            if (!target.isAlive() || target.isRemoved() || target.isDeadOrDying()) return;
            if (amount <= 0f) return;
            // 丢刀生成的友方 NPC（FurinaNpcEntity 及子类）一律不受真伤，避免剑气误杀自己的刀；怪物不受影响
            if (target instanceof FurinaNpcEntity) return;

            // 击杀归属：登记最后伤害来源，保证战利品/经验正确归属给攻击者
            if (attacker instanceof Player attackerPlayer) {
                target.setLastHurtByPlayer(attackerPlayer);
            }

            float remaining = target.getHealth() - amount;
            if (remaining <= 0f) {
                // 致死：直接清零并结算死亡（触发掉落、经验、玩家死亡流程）
                target.setHealth(0f);
                target.die(trueDamageSource(target, attacker));
            } else {
                target.setHealth(remaining);
            }
        }

        /** 为致死结算构造真伤来源（仅用于死亡消息/归属，不参与减伤计算） */
        private static DamageSource trueDamageSource(LivingEntity target, Entity attacker) {
            Level level = target.level();
            Holder<DamageType> holder = null;
            try {
                holder = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,
                                new ResourceLocation(SkydeitySlash.MODID, "true_damage")));
            } catch (Exception ex) {
                holder = null;
            }
            if (holder != null && attacker != null) return new DamageSource(holder, attacker, attacker);
            if (holder != null) return new DamageSource(holder);
            return attacker != null
                    ? level.damageSources().indirectMagic(attacker, attacker)
                    : level.damageSources().genericKill();
        }

        /** 推开 center 周围 radius 格内所有非 ignore 活体生物：水平远离中心 + 轻微上抛，越近推力越强。返回被推开数量。（可复用） */
        public static int knockbackNearbyEntities(Level level, Vec3 center, double radius, double power, Entity ignore) {
            if (level.isClientSide) return 0;
            double r = radius;
            AABB box = new AABB(center.x - r, center.y - r, center.z - r, center.x + r, center.y + r, center.z + r);
            int count = 0;
            for (Entity e : level.getEntities(ignore, box, e2 -> e2 != ignore && e2.isAlive() && e2 instanceof LivingEntity)) {
                Vec3 delta = e.position().subtract(center);
                double dist = delta.horizontalDistance();
                if (dist < 1.0E-4) {
                    // 完全重叠时给随机方向，避免除零
                    delta = new Vec3(level.random.nextDouble() - 0.5, 0, level.random.nextDouble() - 0.5);
                    dist = delta.length();
                }
                Vec3 dir = delta.normalize();
                double falloff = Math.max(0.1, 1.0 - (dist / r)); // 越近推力越强
                double force = power * falloff;
                e.setDeltaMovement(e.getDeltaMovement().add(dir.x * force, 0.25 * falloff, dir.z * force));
                e.hurtMarked = true;
                count++;
            }
            return count;
        }

        /** 钤印伞：若玩家已有一把存活伞则传送到伞处并清掉伞；否则按模式立伞 */
        private static void handleUmbrellaPress(ServerPlayer player, int mode) {
            UUID uuid = player.getUUID();
            List<Integer> ids = UMBRELLA_ENTITIES.get(uuid);
            Entity first = (ids != null && !ids.isEmpty()) ? player.level().getEntity(ids.get(0)) : null;

            if (first instanceof EntityRainUmbrella u && u.isAlive()
                    && !player.level().isClientSide && player.level().dimension().equals(u.level().dimension())) {
                // 再次按下：强制位移传送到伞所在坐标（无条件到达，清残速防被拉扯回去），随后伞消失
                Vec3 target = u.position().add(0, 1, 0);
                player.teleportTo((ServerLevel) u.level(),
                        target.x, target.y, target.z, player.getYRot(), player.getXRot());
                player.setDeltaMovement(0, 0, 0);
                player.fallDistance = 0f;
                setUmbrellasDead(player);
                UMBRELLA_ENTITIES.remove(uuid);
                // 投伞（芙蓉花·特殊行动+D）传送到位后补 2 秒缓降（40 tick）
                if (mode == EntityRainUmbrella.MODE_THROW) {
                    player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0));
                }
                return;
            }

            // 重新立伞
            setUmbrellasDead(player);
            UMBRELLA_ENTITIES.remove(uuid);
            List<Integer> spawned = new ArrayList<>();
            if (mode == EntityRainUmbrella.MODE_BEACON) {
                // 令花神：伞朝玩家面前缓慢推 6 格
                Vec3 look = player.getLookAngle();
                Vec3 dir = new Vec3(look.x, 0, look.z);
                if (dir.lengthSqr() < 1e-6) dir = new Vec3(0, 0, 1);
                spawned.add(EntityRainUmbrella.spawnBeacon(player.level(), player, player.position(), dir.normalize()).getId());
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        ModSounds.DISHENG_SHENGHUA.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
            } else if (mode == EntityRainUmbrella.MODE_STAY) {
                // 令花神·原地：伞留在玩家位置，绕伞原地水墨山水
                spawned.add(EntityRainUmbrella.spawnStay(player.level(), player, player.position()).getId());
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        ModSounds.YANGUO_WUHENJI.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
            } else if (mode == EntityRainUmbrella.MODE_THROW) {
                // 芙蓉花投伞：由玩家位置缓慢上浮最高 6 格后悬停，等待传送
                spawned.add(EntityRainUmbrella.spawnThrow(player.level(), player,
                        player.position(), EntityRainUmbrella.THROW_RISE).getId());
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        ModSounds.DIYAN_HUAHUN.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
            } else {
                for (EntityRainUmbrella u : EntityRainUmbrella.spawnOrbit(player.level(), player, player.position(), 1, 7.0)) {
                    spawned.add(u.getId());
                }
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        ModSounds.HUANGUI_HEQING.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            // 粒子由伞实体每帧持续生成（W/S/A/D 各有丝绸/山陵/投伞水墨特效）
            UMBRELLA_ENTITIES.put(uuid, spawned);
        }

        /** 清掉该玩家已存在的所有伞特效实体 */
        private static void setUmbrellasDead(ServerPlayer player) {
            List<Integer> ids = UMBRELLA_ENTITIES.get(player.getUUID());
            if (ids == null) return;
            for (int id : ids) {
                if (player.level().getEntity(id) instanceof EntityRainUmbrella u && u.isAlive()) {
                    u.discard();
                }
            }
        }

        public static void onBladeHit(SlashBladeEvent.HitEvent event) {
            var state = event.getSlashBladeState();
            // 仙乡的赠别礼：每次挥刀命中目标时给自己叠加一层露米（刷新 5 秒 + 提升等级，上限 10 级）
            if (event.getUser() instanceof Player farewellAttacker
                    && state.hasSpecialEffect(ModSpecialEffects.CELESTIAL_FAREWELL_EFFECT.getId())
                    && SpecialEffect.isEffective(ModSpecialEffects.CELESTIAL_FAREWELL_EFFECT.getId(), farewellAttacker.experienceLevel)) {
                applyLumi(farewellAttacker);
            }
            // 彼岸蝶舞：效果激活期间，玩家每次普通攻击给目标叠加一层血梅香
            if (event.getUser() instanceof Player attacker
                    && state.hasSpecialEffect(ModSpecialEffects.BUTTERFLY_DANCE_EFFECT.getId())
                    && isButterflyDanceActive(attacker)) {
                applyBloodPlum(attacker, event.getTarget());
            }
            // 雨间蝶舞·墨羽环绕：玩家挥刀（右键 SA）命中时，在目标身边召唤/刷新一团聚集的墨绿水墨粒子绕其旋转；
            // 粒子团存活期间每 0.5 秒对目标造成 52 点魔法伤害（每秒两次），玩家停止攻击该目标 1 秒后粒子团消散、伤害停止
            if (event.getUser() instanceof Player butterflyOwner
                    && state.hasSpecialEffect(ModSpecialEffects.RAIN_BUTTERFLY_EFFECT.getId())
                    && SpecialEffect.isEffective(ModSpecialEffects.RAIN_BUTTERFLY_EFFECT.getId(), butterflyOwner.experienceLevel)
                    && butterflyOwner.level() instanceof ServerLevel bs) {
                LivingEntity bt = event.getTarget();
                EntityInkSwarm existing = null;
                for (Entity e : bs.getAllEntities()) {
                    if (e instanceof EntityInkSwarm sw && sw.getTargetId() == bt.getId()) { existing = sw; break; }
                }
                if (existing != null) {
                    existing.refreshAttack();
                } else {
                    EntityInkSwarm sw = new EntityInkSwarm(ModEntities.INK_SWARM.get(), bs);
                    sw.init(butterflyOwner, bt);
                    bs.addFreshEntity(sw);
                }
            }
            // 一笛清影化烟魂，落入江湖伞犹温：攻击命中目标使其失去 AI（复用触及苍穹的 setNoAi 取消机制）
            if (event.getUser() instanceof Player shadowOwner
                    && state.hasSpecialEffect(ModSpecialEffects.FLUTE_SHADOW_EFFECT.getId())
                    && SpecialEffect.isEffective(ModSpecialEffects.FLUTE_SHADOW_EFFECT.getId(), shadowOwner.experienceLevel)) {
                LivingEntity ht = event.getTarget();
                if (ht instanceof Mob mob) mob.setNoAi(true);
            }
            // 花开见血血染双瞳：蓄势期间每次命中额外结算 52 真伤并绽开一簇暗红血花，共 3 次
            if (event.getUser() instanceof Player bloomAttacker
                    && state.hasSpecialEffect(ModSpecialEffects.ZANKOU_SE50.getId())
                    && SpecialEffect.isEffective(ModSpecialEffects.ZANKOU_SE50.getId(), bloomAttacker.experienceLevel)
                    && hasZankouBloomCharges(bloomAttacker)) {
                LivingEntity bloomTarget = event.getTarget();
                applyTrueDamage(bloomTarget, bloomAttacker, ZANKOU_BLOOM_DAMAGE);
                consumeZankouBloom(bloomAttacker);
                if (bloomAttacker.level() instanceof ServerLevel bs) {
                    // 特效 = 原本 zankou SA 那套暗红刀痕（六条凌乱弧线，连续几何线条、非粒子），绽开在命中目标身上
                    com.example.skydeityslash.entity.EntityBloomSlash.spawn(bs,
                            bloomTarget.position().add(0.0, bloomTarget.getBbHeight() * 0.5, 0.0),
                            bloomAttacker.getLookAngle());
                }
            }

            // 瞳中深渊渊底之吻：每次命中回 2 血；每 3 次命中在目标身上绽开一朵几何樱花 + 额外 52 真伤，
            // 同时在目标脚下撒 10 片樱花花瓣向外散开
            if (event.getUser() instanceof Player abyssAttacker
                    && state.hasSpecialEffect(ModSpecialEffects.ZANKOU_SE30.getId())
                    && SpecialEffect.isEffective(ModSpecialEffects.ZANKOU_SE30.getId(), abyssAttacker.experienceLevel)) {
                LivingEntity abyssTarget = event.getTarget();
                // 回血 —— heal 内部会经 setHealth 钳到最大生命，这里再加一道显式保险
                if (abyssAttacker.getHealth() < abyssAttacker.getMaxHealth()) {
                    abyssAttacker.heal(ZANKOU_ABYSS_HEAL);
                }
                UUID abyssId = abyssAttacker.getUUID();
                int abyssCount = ZANKOU_ABYSS_COUNT.getOrDefault(abyssId, 0) + 1;
                if (abyssCount >= 3) {
                    ZANKOU_ABYSS_COUNT.put(abyssId, 0);
                    applyTrueDamage(abyssTarget, abyssAttacker, ZANKOU_ABYSS_DAMAGE);
                    if (abyssAttacker.level() instanceof ServerLevel asl) {
                        // 几何樱花：5 片花瓣绕中心均分 72°（纯视觉实体，连续几何、非粒子）
                        com.example.skydeityslash.entity.EntitySakuraBloom.spawn(asl,
                                abyssTarget.position().add(0.0, abyssTarget.getBbHeight() * 0.6, 0.0));
                        // 目标脚下撒 10 片樱花花瓣，向外散开
                        Vec3 abyssFeet = abyssTarget.position();
                        for (int i = 0; i < ZANKOU_ABYSS_PETALS; i++) {
                            double a = asl.random.nextDouble() * Math.PI * 2.0;
                            double sp = 0.10 + asl.random.nextDouble() * 0.10;
                            asl.sendParticles(ParticleTypes.CHERRY_LEAVES,
                                    abyssFeet.x + Math.cos(a) * 0.3, abyssFeet.y + 0.15,
                                    abyssFeet.z + Math.sin(a) * 0.3,
                                    1, Math.cos(a) * sp, 0.06, Math.sin(a) * sp, 0.02);
                        }
                    }
                } else {
                    ZANKOU_ABYSS_COUNT.put(abyssId, abyssCount);
                }
            }

            // 吻痕窥梦梦魇生花：每 7 次命中划出几道横着的细红圆柱刀痕，并给目标施加「缓慢 10」
            if (event.getUser() instanceof Player dreamAttacker
                    && state.hasSpecialEffect(ModSpecialEffects.ZANKOU_SE40.getId())
                    && SpecialEffect.isEffective(ModSpecialEffects.ZANKOU_SE40.getId(), dreamAttacker.experienceLevel)) {
                LivingEntity dreamTarget = event.getTarget();
                UUID dreamId = dreamAttacker.getUUID();
                int dreamCount = ZANKOU_DREAM_COUNT.getOrDefault(dreamId, 0) + 1;
                if (dreamCount >= ZANKOU_DREAM_EVERY) {
                    ZANKOU_DREAM_COUNT.put(dreamId, 0);
                    dreamTarget.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                            ZANKOU_DREAM_SLOW_TICKS, ZANKOU_DREAM_SLOW_AMP));
                    if (dreamAttacker.level() instanceof ServerLevel dsl) {
                        // 横切割刀痕：几道横着的细红圆柱同时划过目标（纯视觉实体，连续几何、非粒子）
                        com.example.skydeityslash.entity.EntityCutLines.spawn(dsl,
                                dreamTarget.position().add(0.0, dreamTarget.getBbHeight() * 0.5, 0.0));
                        // 持续流血：每秒 52 真伤，共 6 秒（与「缓慢 10」同时长；目标中途死亡/消失即停跳）
                        final LivingEntity bleedTarget = dreamTarget;
                        final int bleedStart = dsl.getServer().getTickCount();
                        for (int sec = 1; sec <= ZANKOU_DREAM_BLEED_SECONDS; sec++) {
                            final int delay = sec * 20;
                            dsl.getServer().tell(new TickTask(bleedStart + delay, () -> {
                                if (bleedTarget.isAlive() && !bleedTarget.isRemoved()) {
                                    applyTrueDamage(bleedTarget, dreamAttacker, ZANKOU_DREAM_BLEED_DAMAGE);
                                }
                            }));
                        }
                    }
                } else {
                    ZANKOU_DREAM_COUNT.put(dreamId, dreamCount);
                }
            }
        }

        /**
         * SlashBlade「集中度（练度）」的 C 级下限。低于 C 时 SlashBlade 的刀光渲染器会把颜色强行换成灰色，
         * 所以刀光弧的 rank 至少要抬到这里，颜色才不会被丢掉。（只影响这一发光弧，不改变玩家练度）
         */
        private static final float ARC_MIN_RANK = 2.5f;

        /**
         * 右键挥砍的「刀光弧」（EntitySlashEffect）按刀上色。两步缺一不可：
         *  1) 颜色直接取刀状态里当前的颜色 —— 也就是本模组 onUpdate 每 tick 按 SE 写入的颜色，
         *     没有 SE 覆盖时就是刀定义 JSON 里的 summon_sword_color；
         *  2) rank < C 时渲染器会丢掉颜色画成灰的（SlashBlade 的练度分级），所以把这一发光弧的
         *     rank 抬到 C 以上。
         * 本模组自己的 EntityHutaoCircleSlash（胡桃 SA 环形刀光）自行设色，跳过。
         */
        public static void onSlashArcColor(EntityJoinLevelEvent event) {
            if (!(event.getLevel() instanceof ServerLevel)) return;
            if (!(event.getEntity() instanceof mods.flammpfeil.slashblade.entity.EntitySlashEffect slash)) return;
            if (slash instanceof com.example.skydeityslash.entity.EntityHutaoCircleSlash) return;
            if (!(slash.getOwner() instanceof Player owner) || !owner.isAlive()) return;

            ISlashBladeState st = owner.getMainHandItem()
                    .getCapability(CapabilitySlashBlade.BLADESTATE, null).orElse(null);
            if (st == null) return;

            slash.setColor(st.getEffectColor().getRGB());
            if (slash.getRank() < ARC_MIN_RANK) slash.setRank(ARC_MIN_RANK);
        }

        /** 幻影剑逐发循环色：召唤剑进入世界时按属主所持刀的 slash_art 上色（FURINA 蓝 / 胡桃桃红 / 天鹅蓝白 / sakurafox 走暗墨绿循环） */
        public static void onPhantomSwordColor(EntityJoinLevelEvent event) {
            if (!(event.getEntity() instanceof EntityAbstractSummonedSword sword)) return;
            if (!(sword.level() instanceof ServerLevel)) return;
            if (!(sword.getOwner() instanceof Player owner) || !owner.isAlive()) return;
            // linnea 金色剑气波由代码自设颜色，不改色
            if (sword instanceof com.example.skydeityslash.entity.EntityGoldenWave) return;
            // 纯展示幻影剑（伤害为 0，如森林萤火的紫色弧线）保持其自身颜色，不统一改色
            if (sword.getDamage() <= 0.0) return;
            int bladeColor = phantomBladeColorOf(owner);
            if (bladeColor >= 0) {
                sword.setColor(bladeColor);
                return;
            }
            // 未识别（默认/无名刀/sakurafox）→ 保留暗墨绿循环
            UUID u = owner.getUUID();
            int step = PHANTOM_COLOR_STEP.getOrDefault(u, 0);
            PHANTOM_COLOR_STEP.put(u, step + 1);
            sword.setColor(phantomSwordColor(step));
        }

        /** 按持有刀的 slash_art 判定幻影剑专属色：FURINA 蓝 / 胡桃 桃红 / 天鹅 蓝白；其余（含 sakurafox）返回 -1 走暗墨绿循环 */
        private static int phantomBladeColorOf(Player owner) {
            ItemStack main = owner.getMainHandItem();
            ISlashBladeState st = main.getCapability(CapabilitySlashBlade.BLADESTATE, null).orElse(null);
            if (st == null) return -1;
            // linnea 刀：无 SA，按模型识别，幻影剑暗色金黄 #8B6508
            if (st.getModel().map(m -> "model/named/linnea.obj".equals(m.getPath())).orElse(false)) {
                return 0x8B6508;
            }
            // iroi 刀：幻影剑统一为粉紫 #9B2E8C（较暗，避免太亮）
            if (st.getModel().map(m -> "model/named/iroi.obj".equals(m.getPath())).orElse(false)) {
                return 0x9B2E8C;
            }
            ResourceLocation art = st.getSlashArtsKey();
            if (art == null || !art.getNamespace().equals(SkydeitySlash.MODID)) return -1;
            String p = art.getPath();
            if ("fontaine_carnival".equals(p)) return 0x3D8BFF; // FURINA 蓝
            if ("liyue_butterfly".equals(p)) return 0xD62828;  // hutao 红色（原为 0xFF7F9E 桃红偏亮）
            if ("xuanfeng_huixue".equals(p)) return 0xC9E6FF;  // ODETTE 蓝白
            if ("columbina".equals(p)) return 0x2E62A6;        // COLUM 深海蓝（比天蓝更暗、不刺眼、无渐变）
            if ("chikui_fentian".equals(p)) return 0x9B0E22;   // zankou 暗红（与刀痕/刀身同色系）
            return -1;
        }

        /** 幻影剑 5 色循环（暗墨系，柔和不刺眼——已整体压暗适配 sakurafox）：深墨绿→墨绿→暗绿→青绿→蓝绿，每发换一色 */
        private static final int[] PHANTOM_COLORS = {
                0x072A16, // 深墨绿（更暗）
                0x10391F, // 墨绿（更暗）
                0x1E552B, // 暗绿（更暗）
                0x21604A, // 青绿（更暗）
                0x23555B  // 蓝绿（更暗）
        };

        private static int phantomSwordColor(int step) {
            return PHANTOM_COLORS[Math.floorMod(step, PHANTOM_COLORS.length)];
        }

        /** 彼岸蝶舞：召唤物（波刀/幻影剑）命中时给目标叠加一层血梅香 */
        public static void onSummonedSwordHit(SlashBladeEvent.SummonedSwordOnHitEntityEvent event) {
            if (event.getSummonedSword().getOwner() instanceof Player attacker) {
                ItemStack atkStack = attacker.getMainHandItem();
                ISlashBladeState atkState = atkStack.getCapability(CapabilitySlashBlade.BLADESTATE, null).orElse(null);
                if (atkState != null && atkState.hasSpecialEffect(ModSpecialEffects.BUTTERFLY_DANCE_EFFECT.getId())
                        && isButterflyDanceActive(attacker)
                        && event.getTarget() instanceof LivingEntity livingTarget) {
                    applyBloodPlum(attacker, livingTarget);
                }
            }
        }

        /** 刀创建时：默认携带 5201314 妖魂与 52 锻造，并添加深灰色物品描述 */
        public static void onBladeCreated(SlashBladeRegistryEvent.Post event) {
            var name = event.getSlashBladeDefinition().getName();
            ItemStack stack = event.getBlade();
            if (name.equals(prefix("slash_furina")) || name.equals(prefix("hutao"))
                    || name.equals(prefix("odette")) || name.equals(prefix("sakurafox"))
                    || name.equals(prefix("linnea")) || name.equals(prefix("columbina"))
                    || name.equals(prefix("iroi")) || name.equals(prefix("zankou"))) {
                stack.getCapability(CapabilitySlashBlade.BLADESTATE, null)
                        .ifPresent(state -> {
                            state.setProudSoulCount(5201314);
                            state.setRefine(52);
                        });
            }
            if (name.equals(prefix("slash_furina"))) {
                setGradientName(stack, "芙宁娜「枫丹」不休独舞·静水流涌之辉", new int[]{
                        0x87CEEB, 0x88CFEE, 0x89CFF0, // 芙宁娜：淡天蓝
                        0x00BFFF, 0x00BFFF, 0x00BFFF, 0x00BFFF, // 「枫丹」：深天蓝
                        0x00CED1, 0x00CCD6, 0x00CBDB, 0x00C9E0, // 不休独舞
                        0x7D8C7D, // ·
                        0x00C7E5, 0x00C6EB, 0x00C4F0, 0x00C2F5, 0x00C1FA, 0x00BFFF // 静水流涌之辉
                });
                setLore(stack,
                        Component.literal("\u00a78众水、众方、众民与众律法，受万众之托付承水立足于国度，手持每秒恢复 2% 最大生命，攻击力每秒 +20%，上限 +200%。"),
                        Component.literal("\u00a78罪人舞步旋，以浮华假面立于万众之前，以五百年孤独守护命运，手持每秒给周围 5 格内生物施加虚弱 5 + 缓慢 10；濒死时免疫死亡、扣除 10 级经验。"),
                        Component.literal("\u00a78流涌灵息，荒芒之力，按下特殊移动攻击转为两倍真实伤害，持续 10 秒；期间受到的伤害会转化为额外生命，效果结束消失。"));
            } else if (name.equals(prefix("hutao"))) {
                setGradientName(stack, "胡桃「璃月」引蝶·雪霁梅香", new int[]{
                        0x8B0000, 0xDC143C, // 胡桃：暗红 → 绯红
                        0xCC7722, 0xD78022, 0xE28821, 0xED9121, // 「璃月」：暗金 → 亮橙
                        0xC80815, 0xCC0A1D, // 引蝶
                        0x7D8C7D, // ·
                        0xD00D25, 0xD40F2C, 0xD81234, 0xDC143C // 雪霁梅香
                });
                setLore(stack,
                        Component.literal("\u00a78梅花香自苦寒来，女孩终有一日带上自己的梅花，手持原地不动 3 秒后，将周围 3 格内所有生物生命值改为当前生命的 50%。"),
                        Component.literal("\u00a78蝶火燎原往生送别阴阳两界，手持每秒恢复 10 点生命；濒死时免疫死亡、扣除 10 级经验、获得伤害吸收 10。"),
                        Component.literal("\u00a78赤团开时斜飞去，最不安神晴又复雨，按下特殊移动攻击会给目标添加血梅香状态，受到一次攻击叠一层，每层每秒造成 10 点伤害，等级为 5 的倍数时爆炸扣除目标当前生命 50%，持续 5 秒。"));
            } else if (name.equals(prefix("odette"))) {
                setGradientName(stack, "奥黛塔「至冬」雪鹄·白湖冬羽", new int[]{
                        0xF0FFFF, 0xCBF9FD, 0xA5F3FC, // 奥黛塔：淡青白 → 浅天蓝
                        0xDCDCDC, 0xD9D9D9, 0xD6D6D6, 0xD3D3D3, // 「至冬」：银灰
                        0x93C5FD, 0x9FCBFD, // 雪鹄：浅天蓝
                        0x7D8C7D, // ·
                        0xB7D7FD, 0xC3DDFE, 0xCFE3FE, 0xDBEAFE // 白湖冬羽：淡蓝渐变
                });
                setLore(stack,
                        Component.literal("\u00a78幻灵夜舞之美，舞台之上演绎优雅天鹅终有黑暗，召唤白色幻影剑环绕造成持续伤害，帧伤 52，上限10秒每秒 +52。"),
                        Component.literal("\u00a78翾风回雪之冠无人恋爱白天鹅后的落寞，手持获得力量 10；受到远程伤害或攻击者距离 5 格以上时闪避。"),
                        Component.literal("\u00a78触及苍穹永恒的面容，按下特殊行动被你攻击命中的目标会无AI，受到的伤害会等转化为魔法伤害，扩散给周围 8 格内的其他生物同时发光。"));
            } else if (name.equals(prefix("sakurafox"))) {
                // 渐变刀名：墨染江湖「离恨烟」烟魂·墨蝶翩跹·狐樱（加粗、非斜体，逐字 RGB 上色）
                setGradientName(stack, "墨染江湖「离恨烟」烟魂·墨蝶翩跹·狐樱浔", new int[]{
                        0x114200, 0x1F550F, 0x2C671F, 0x3A7A2E, // 墨染江湖：墨绿 → 浅墨绿
                        0x1D7500, 0x167224, 0x0E7047, 0x076D6B, 0x006B8F, // 「离恨烟」
                        0x005875, 0x00303F, // 烟魂（变深）
                        0x7D8C7D, // ·
                        0x7D8C7D, 0x677668, 0x506053, 0x3A4A3E, // 墨蝶翩跹（墨色渐变）
                        0x7D8C7D, // ·
                        0xA65AD0, 0xFF9FC9, 0xFFF5E1 // 狐樱（紫粉色渐变）+ 浔（色卡奶白，加粗）
                });
                setLore(stack, Component.literal("\u00a78朱颜胜狐，恰似绯樱初绽，樱色灵狐寄身刀刃，随风而来，此后去留两相难。"));
            } else if (name.equals(prefix("linnea"))) {
                // 渐变刀名：莉奈娅「诺德卡莱」启喻鸟·博闻异旅（加粗、非斜体，逐字 RGB 上色，· 灰色不加粗）
                setGradientName(stack, "莉奈娅「诺德卡莱」启喻鸟·博闻异旅", new int[]{
                        0xF28500, 0xF9A200, 0xFFBF00, // 莉奈娅：橙金 → 金黄
                        0xE0FFFF, 0xE3FEFF, 0xE6FCFF, 0xEAFBFF, 0xEDF9FF, 0xF0F8FF, // 「诺德卡莱」：淡青 → 淡蓝白
                        0xFFBF00, 0xFDBE0B, 0xFBBE17, // 启喻鸟
                        0x7D8C7D, // ·
                        0xFABD22, 0xF8BC2D, 0xF6BC39, 0xF4BB44 // 博闻异旅
                });
                // 灰色 lore，按句号拆分为多行（与 odette 一致）
                setLore(stack,
                        Component.literal("\u00a78喜或悲的谕告，每秒恢复 5 点生命，按下特殊行动按键，推开周围 5 格内所有生物，免死扣除 10 级。"),
                        Component.literal("\u00a78仙乡的赠别礼，远走他乡的流年，每次命中获得 1 层露米buff，增加攻击力。"),
                        Component.literal("\u00a78月兆堕天的落羽，黄金猎犬之梦，召唤飞鸟每 0.5 秒对周围 5 格内实体造成 52 点魔法伤害，并施加缓慢 10。"));
            } else if (name.equals(prefix("columbina"))) {
                // 渐变刀名：哥伦比娅「诺德卡莱」霜月·帷间夜曲月之少女
                // 哥伦比娅「诺德卡莱」(淡青→淡蓝白, 用指定六色) + 霜月 / 帷间夜曲月之少女(淡蓝渐变到蓝) ，· 灰色不加粗
                setGradientName(stack, "哥伦比娅「诺德卡莱」霜月·帷间夜曲月之少女", new int[]{
                        0xE0FFFF, 0xE6FCFF, 0xEDF9FF, 0xF3EBFF, // 哥伦比娅
                        0xE0FFFF, 0xE3FEFF, 0xE6FCFF, 0xEAFBFF, 0xEDF9FF, 0xF0F8FF, // 「诺德卡莱」
                        0xC9FFFF, 0xB8F0FF, // 霜月
                        0x7D8C7D, // ·
                        0xAFE0FF, 0xA0D4FF, 0x92C8FF, 0x83BCFF, 0x75B0FF, 0x66A4FF, 0x5898FF, 0x4A8CFF // 帷间夜曲月之少女
                });
                // 显式重排 SE：setSpecialEffects 会把内部 HashSet 换成有序 ArrayList，保证按等级从低到高显示
                // （jiangwan 10 → 新月 30 → 柔光 40 → 花岚 50）
                stack.getCapability(CapabilitySlashBlade.BLADESTATE, null)
                        .ifPresent(st -> {
                            net.minecraft.nbt.ListTag seList = new net.minecraft.nbt.ListTag();
                            seList.add(net.minecraft.nbt.StringTag.valueOf(ModSpecialEffects.JIANGWAN_SNOW_EFFECT.getId().toString()));
                            seList.add(net.minecraft.nbt.StringTag.valueOf(ModSpecialEffects.NEW_MOON_LAW_EFFECT.getId().toString()));
                            seList.add(net.minecraft.nbt.StringTag.valueOf(ModSpecialEffects.ROUGUANG_NINGLU_EFFECT.getId().toString()));
                            seList.add(net.minecraft.nbt.StringTag.valueOf(ModSpecialEffects.HUALAN_YUNYI_EFFECT.getId().toString()));
                            st.setSpecialEffects(seList);
                        });
                // 灰色 lore，以句号为换行（与 linnea/odette 一致）
                setLore(stack,
                        Component.literal("\u00a78新月自己的法则为夜增辉，手持持续回饱和，每 2.5s 治疗 20 生命并清除全部负面效果。"),
                        Component.literal("\u00a78柔光凝露梦湖起波与君遥伴，按下特殊行动键拔刀攻击命中额外造成目标最大生命 1% 真伤。"),
                        Component.literal("\u00a78花岚云翳山岩树影为君所歌，每次挥砍额外发射一道小剑气，受致命伤时扣 10 级经验免疫死亡。"));
            } else if (name.equals(prefix("iroi"))) {
                // 渐变刀名：伊洛伊(#20B2AA)「向阳」(#F7C030)臆想·三十八亿年的海市蜃楼(#BA55D3)，·灰色不加粗
                setGradientName(stack, "伊洛伊「向阳」臆想·三十八亿年的海市蜃楼", new int[]{
                        0x188680, 0x1EA9A2, 0x25CDC3, // 伊洛伊
                        0xB99024, 0xDAAA2A, 0xFBC331, 0xFFDD37, // 「向阳」
                        0x8C409E, 0xD662F3, // 臆想
                        0x7D8C7D, // ·
                        0x8C409E, 0x9444A8, 0x9C47B1, 0xA44BBA, 0xAD4FC4, 0xB553CD, 0xBD56D7, 0xC55AE0, 0xCE5EE9, 0xD662F3 // 三十八亿年的海市蜃楼
                });
                // 显式重排 SE：保证按等级从低到高显示（jiangwan_snow 10 → iroi_se30 → iroi_se40 → iroi_se50）
                stack.getCapability(CapabilitySlashBlade.BLADESTATE, null)
                        .ifPresent(st -> {
                            net.minecraft.nbt.ListTag seList = new net.minecraft.nbt.ListTag();
                            seList.add(net.minecraft.nbt.StringTag.valueOf(ModSpecialEffects.JIANGWAN_SNOW_EFFECT.getId().toString()));
                            seList.add(net.minecraft.nbt.StringTag.valueOf(ModSpecialEffects.IROI_SE30.getId().toString()));
                            seList.add(net.minecraft.nbt.StringTag.valueOf(ModSpecialEffects.IROI_SE40.getId().toString()));
                            seList.add(net.minecraft.nbt.StringTag.valueOf(ModSpecialEffects.IROI_SE50.getId().toString()));
                            st.setSpecialEffects(seList);
                        });
                // 灰色 lore，以句号为换行（与 columbina/linnea 一致）
                setLore(stack,
                        Component.literal("\u00a78自诩宇宙的精华，这就是宇宙大爆炸的声音，手持时每 5 秒清空自身全部负面效果并瞬回 10 生命。"),
                        Component.literal("\u00a78未来自我连续性假设，手持此刀每 3 次攻击命中目标，朝目标放一道 52 真伤十字剑气。"),
                        Component.literal("\u00a78妄想彼端的森林萤火，回到最初的梦境中，按下特殊行动键召唤盈蓄花株每 1 秒对周围 3.5 格内生物造成 52 真伤。"));
            } else if (name.equals(prefix("zankou"))) {
                // 渐变刀名：残虹(#ED2CA6)「赤葵」(#E40C4D)饲火(#A23D06)·殷红幻景的暮落残阳(#DE1753)
                // 每段各自「深 → 指定色」渐变，「·」灰色不加粗、其余加粗（走 setGradientName 的统一规则）
                setGradientName(stack, "残虹「赤葵」饲火·殷红幻景的暮落残阳", new int[]{
                        0xA1176E, 0xED2CA6,                                                                  // 残虹
                        0x9B012F, 0xB30137, 0xCC013E, 0xE40C4D,                                              // 「赤葵」
                        0x6E2700, 0xA23D06,                                                                  // 饲火
                        0x7D8C7D,                                                                            // ·
                        0x970934, 0xA00937, 0xA90A3A, 0xB20A3D, 0xBA0B40, 0xC30B43, 0xCC0C46, 0xD50D49, 0xDE1753 // 殷红幻景的暮落残阳
                });
                // 显式重排 SE：保证按等级从低到高显示（jiangwan_snow 10 → zankou_se30 → zankou_se40 → zankou_se50）
                stack.getCapability(CapabilitySlashBlade.BLADESTATE, null)
                        .ifPresent(st -> {
                            net.minecraft.nbt.ListTag seList = new net.minecraft.nbt.ListTag();
                            seList.add(net.minecraft.nbt.StringTag.valueOf(ModSpecialEffects.JIANGWAN_SNOW_EFFECT.getId().toString()));
                            seList.add(net.minecraft.nbt.StringTag.valueOf(ModSpecialEffects.ZANKOU_SE30.getId().toString()));
                            seList.add(net.minecraft.nbt.StringTag.valueOf(ModSpecialEffects.ZANKOU_SE40.getId().toString()));
                            seList.add(net.minecraft.nbt.StringTag.valueOf(ModSpecialEffects.ZANKOU_SE50.getId().toString()));
                            st.setSpecialEffects(seList);
                        });
                // 灰色 lore，以句号为换行（§8 = 深灰色，与其他刀完全一致）
                setLore(stack,
                        Component.literal("\u00a78瞳中深渊，渊底之吻，在血泊当中看见了一抹惊悚而瑰丽的红色，每次命中回复自身 2 点生命，每 3 次命中对目标追加 52 点真实伤害。"),
                        Component.literal("\u00a78吻痕窥梦，梦魇生花，如丝绒般温柔，免疫远程攻击，每 7 次命中使目标缓慢 10，每秒造成 52 点真伤，持续 6 秒。"),
                        Component.literal("\u00a78花开见血，血染双瞳，血型罪孽的过往，按下特殊行动键使下三次攻击每次追加 52 点真实伤害。"));
            }
        }

        /** 渐变刀名：逐字 RGB 上色，加粗非斜体，「·」灰色不加粗（sakurafox 命名方法） */
        private static void setGradientName(ItemStack stack, String chars, int[] colors) {
            MutableComponent grad = Component.empty();
            for (int i = 0; i < chars.length(); i++) {
                String ch = chars.substring(i, i + 1);
                int col = colors[i];
                boolean bold = !"·".equals(ch);
                grad.append(Component.literal(ch)
                        .withStyle(s -> s.withColor(TextColor.fromRgb(col)).withBold(bold).withItalic(false)));
            }
            stack.setHoverName(grad);
        }

        /** 1.20.1 用 NBT 而非 DataComponents 存物品描述（display.Lore 为 JSON 列表） */
        private static void setLore(ItemStack stack, Component... lines) {
            ListTag lore = new ListTag();
            for (Component c : lines) {
                lore.add(StringTag.valueOf(Component.Serializer.toJson(c)));
            }
            stack.getOrCreateTagElement("display").put("Lore", lore);
        }

        /** 花岚云翳山岩树影：仿照灼霜刀「burst_drive」，每次挥砍（右键攻击动画）额外发射一道小的剑气 */
        public static void onDoSlash(SlashBladeEvent.DoSlashEvent event) {
            try {
                ISlashBladeState state = event.getSlashBladeState();
                if (state == null) return;

                // sakurafox：每次挥砍（右键攻击动画）随机换一种刀身墨色（墨绿/暗绿/深蓝），且与上一次不同
                if (state.getModel().map(m -> "model/named/sakurafox.obj".equals(m.getPath())).orElse(false)
                        && event.getUser() instanceof Player sakuraUser) {
                    UUID suid = sakuraUser.getUUID();
                    int prev = SAKURA_SWING_COLOR.getOrDefault(suid, Integer.MIN_VALUE);
                    List<Integer> pool = new ArrayList<>();
                    for (int c : SAKURA_SWING_COLORS) {
                        if (c != prev) pool.add(c);
                    }
                    int pick = pool.isEmpty() ? SAKURA_SWING_COLORS[0]
                            : pool.get(sakuraUser.getRandom().nextInt(pool.size()));
                    SAKURA_SWING_COLOR.put(suid, pick);
                    state.setEffectColor(new Color(pick));
                }

                if (!state.hasSpecialEffect(ModSpecialEffects.HUALAN_YUNYI_EFFECT.getId())) return;
                LivingEntity user = event.getUser();
                if (user == null || user.level().isClientSide) return;
                if (user instanceof Player player
                        && !SpecialEffect.isEffective(ModSpecialEffects.HUALAN_YUNYI_EFFECT.getId(), player.experienceLevel)) return;
                Drive.doSlash(event.getUser(),
                        event.getRoll(), event.getYRot(),
                        10, state.getColorCode(),
                        Vec3.ZERO, false,
                        event.getDamage(),
                        null, 1.5f);
            } catch (Throwable ignored) { }
        }

        /** 每 tick 更新：实现「众水」「罪人舞步旋」「蝶火燎原」的每秒效果 */
        public static void onUpdate(SlashBladeEvent.UpdateEvent event) {
            if (!event.isSelected()) return;
            Entity entity = event.getEntity();
            if (!(entity instanceof Player player)) return;
            ISlashBladeState state = event.getSlashBladeState();

            // 诺德卡莱 SA 剑气锁定追踪：每 tick 朝目标实时位置转向
            if (player.level() instanceof ServerLevel homingLevel) {
                tickColumbinaHoming(homingLevel);
            }

            boolean hasAllWaters = state.hasSpecialEffect(ModSpecialEffects.ALL_WATERS_EFFECT.getId());
            boolean hasSinnerDance = state.hasSpecialEffect(ModSpecialEffects.SINNER_DANCE_EFFECT.getId());
            boolean hasButterflyBlaze = state.hasSpecialEffect(ModSpecialEffects.BUTTERFLY_BLAZE_EFFECT.getId());
            boolean hasPlumBlossom = state.hasSpecialEffect(ModSpecialEffects.PLUM_BLOSSOM_EFFECT.getId());
            boolean hasPhantomDance = state.hasSpecialEffect(ModSpecialEffects.PHANTOM_DANCE_EFFECT.getId());
            boolean hasXuanfengHuixue = state.hasSpecialEffect(ModSpecialEffects.XUANFENG_HUIXUE_EFFECT.getId());
            boolean hasFlowRush = state.hasSpecialEffect(ModSpecialEffects.FLOW_RUSH_EFFECT.getId());
            boolean hasButterflyDance = state.hasSpecialEffect(ModSpecialEffects.BUTTERFLY_DANCE_EFFECT.getId());
            boolean hasSkywardVisage = state.hasSpecialEffect(ModSpecialEffects.SKYWARD_VISAGE_EFFECT.getId());
            boolean hasSilverPinSlayer = state.hasSpecialEffect(ModSpecialEffects.SILVER_PIN_SLAYER_EFFECT.getId());
            boolean hasGongsunLi = state.hasSpecialEffect(ModSpecialEffects.GONGSUN_LI_EFFECT.getId());
            boolean hasJoySorrowOmen = state.hasSpecialEffect(ModSpecialEffects.JOY_SORROW_OMEN_EFFECT.getId());
            boolean hasCelestialFarewell = state.hasSpecialEffect(ModSpecialEffects.CELESTIAL_FAREWELL_EFFECT.getId());
            boolean hasFallingMoonFeather = state.hasSpecialEffect(ModSpecialEffects.FALLING_MOON_FEATHER_EFFECT.getId());
            boolean hasNewMoonLaw = state.hasSpecialEffect(ModSpecialEffects.NEW_MOON_LAW_EFFECT.getId());
            boolean hasIroiUniverse = state.hasSpecialEffect(ModSpecialEffects.IROI_SE30.getId());
            boolean hasIroiFuture = state.hasSpecialEffect(ModSpecialEffects.IROI_SE40.getId());
            boolean hasIroiFirefly = state.hasSpecialEffect(ModSpecialEffects.IROI_SE50.getId());
            if (!hasAllWaters && !hasSinnerDance && !hasButterflyBlaze && !hasPlumBlossom && !hasPhantomDance && !hasXuanfengHuixue && !hasFlowRush && !hasButterflyDance && !hasSkywardVisage && !hasSilverPinSlayer && !hasGongsunLi && !hasJoySorrowOmen && !hasCelestialFarewell && !hasFallingMoonFeather && !hasNewMoonLaw && !hasIroiUniverse && !hasIroiFuture && !hasIroiFirefly) return;

            if (hasAllWaters
                    && SpecialEffect.isEffective(ModSpecialEffects.ALL_WATERS_EFFECT.getId(), player.experienceLevel)) {
                state.setEffectColor(new Color(0x7BE6EF));
                if (player.tickCount % 20 == 0) {
                    player.heal(player.getMaxHealth() * 0.02f);
                    float amp = state.getAttackAmplifier();
                    state.setAttackAmplifier(Math.min(amp + 0.2f, 2.0f));
                }
            }

            if (hasSinnerDance
                    && SpecialEffect.isEffective(ModSpecialEffects.SINNER_DANCE_EFFECT.getId(), player.experienceLevel)) {
                state.setEffectColor(new Color(0x7BE6EF));
                if (player.tickCount % 20 == 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 100, 0));
                    Level level = event.getLevel();
                    AABB aabb = player.getBoundingBox().inflate(5.0);
                    List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, aabb,
                            e -> e != player && !e.isSpectator());
                    for (LivingEntity target : targets) {
                        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 4));
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 9));
                    }
                }
            }

            if (hasButterflyBlaze
                    && SpecialEffect.isEffective(ModSpecialEffects.BUTTERFLY_BLAZE_EFFECT.getId(), player.experienceLevel)) {
                state.setEffectColor(new Color(0xFF0000));
                if (player.tickCount % 20 == 0) {
                    player.heal(10.0f);
                }
            }

            if (hasPlumBlossom
                    && SpecialEffect.isEffective(ModSpecialEffects.PLUM_BLOSSOM_EFFECT.getId(), player.experienceLevel)) {
                state.setEffectColor(new Color(0xFF0000));
                UUID uuid = player.getUUID();
                Vec3 pos = player.position();
                Vec3 last = PLUM_LAST_POS.get(uuid);
                if (last != null && last.distanceToSqr(pos) < 0.01) {
                    // 原地不动：一轮静止周期内只触发一次
                    if (!PLUM_ACTIVATED.getOrDefault(uuid, false)) {
                        int ticks = PLUM_STILL_TICKS.getOrDefault(uuid, 0) + 1;
                        if (ticks >= 60) {
                            Level level = event.getLevel();
                            AABB aabb = player.getBoundingBox().inflate(3.0);
                            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, aabb,
                                    e -> e != player && !e.isSpectator());
                            for (LivingEntity target : targets) {
                                target.setHealth(target.getHealth() * 0.5f);
                            }
                            PLUM_STILL_TICKS.put(uuid, 0);
                            PLUM_ACTIVATED.put(uuid, true);
                        } else {
                            PLUM_STILL_TICKS.put(uuid, ticks);
                        }
                    }
                } else {
                    // 玩家移动：重置，需再次静止 3 秒方可再次触发
                    PLUM_STILL_TICKS.put(uuid, 0);
                    PLUM_ACTIVATED.put(uuid, false);
                }
                PLUM_LAST_POS.put(uuid, pos);
            }

            // 银簪·尽弑天下负心人：手持持续获得饱和；站着不动 2 秒（复用幽蝶的静止检测框架），
            // 清空自身所有负面效果，并把它们的拷贝施加到周围 4 格生物
            if (hasSilverPinSlayer
                    && SpecialEffect.isEffective(ModSpecialEffects.SILVER_PIN_SLAYER_EFFECT.getId(), player.experienceLevel)) {
                state.setEffectColor(new Color(0x70CC52));
                if (player.tickCount % 20 == 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 40, 0));
                }
                UUID uuid = player.getUUID();
                Vec3 pos = player.position();
                Vec3 last = SLAYER_LAST_POS.get(uuid);
                if (last == null || last.distanceToSqr(pos) < 0.01) {
                    int ticks = SLAYER_STILL_TICKS.getOrDefault(uuid, 0) + 1;
                    if (ticks >= 40) { // 2 秒
                        Level level = event.getLevel();
                        List<MobEffectInstance> negatives = new ArrayList<>();
                        for (MobEffectInstance inst : player.getActiveEffects()) {
                            if (!inst.getEffect().isBeneficial()) negatives.add(inst);
                        }
                        for (MobEffectInstance n : negatives) player.removeEffect(n.getEffect());
                        if (!negatives.isEmpty()) {
                            AABB aabb = player.getBoundingBox().inflate(4.0);
                            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, aabb,
                                    t -> t != player && t.isAlive() && !t.isSpectator())) {
                                for (MobEffectInstance n : negatives) {
                                    e.addEffect(new MobEffectInstance(n.getEffect(), n.getDuration(), n.getAmplifier()));
                                }
                            }
                        }
                        SLAYER_STILL_TICKS.put(uuid, 0);
                    } else {
                        SLAYER_STILL_TICKS.put(uuid, ticks);
                    }
                } else {
                    SLAYER_STILL_TICKS.put(uuid, 0);
                }
                SLAYER_LAST_POS.put(uuid, pos);
            }

            if (hasPhantomDance
                    && SpecialEffect.isEffective(ModSpecialEffects.PHANTOM_DANCE_EFFECT.getId(), player.experienceLevel)) {
                state.setEffectColor(new Color(0xF0F0F0));
                UUID uuid = player.getUUID();
                long activeTicks = PHANTOM_ACTIVE_TICKS.getOrDefault(uuid, 0L) + 1;
                PHANTOM_ACTIVE_TICKS.put(uuid, activeTicks);

                if (player.tickCount % 20 == 0) {
                    int seconds = (int) Math.min(activeTicks / 20, 10);
                    float damage = 52.0f + 52.0f * seconds;
                    Level level = event.getLevel();
                    AABB aabb = player.getBoundingBox().inflate(3.0);
                    List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, aabb,
                            e -> e != player && !e.isSpectator());
                    for (LivingEntity target : targets) {
                        applyTrueDamage(target, player, damage);
                    }
                }

                if (event.getLevel() instanceof ServerLevel serverLevel) {
                    double angle = player.tickCount * 0.3;
                    Vec3 center = player.position().add(0, 1.2, 0);
                    for (int i = 0; i < 1; i++) {
                        double a = angle + i * Math.PI;
                        double x = center.x + Math.cos(a) * 1.5;
                        double z = center.z + Math.sin(a) * 1.5;
                        double y = center.y + Math.sin(a * 2) * 0.5;
                        serverLevel.sendParticles(new DustParticleOptions(new Vector3f(1.0f, 1.0f, 1.0f), 1.2f),
                                x, y, z, 1, 0, 0, 0, 0);
                    }
                }
            }

            if (hasXuanfengHuixue
                    && SpecialEffect.isEffective(ModSpecialEffects.XUANFENG_HUIXUE_EFFECT.getId(), player.experienceLevel)) {
                if (player.tickCount % 20 == 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 9));
                }
            }

            // 自诩宇宙的精华（iroi）：手持每 5 秒脚下 cherry_leaves 一圈、清除全部负面效果并瞬间治疗 10
            if (hasIroiUniverse
                    && SpecialEffect.isEffective(ModSpecialEffects.IROI_SE30.getId(), player.experienceLevel)) {
                state.setEffectColor(new Color(0xFF55FF));
                if (player.tickCount % 100 == 0) {
                    for (MobEffectInstance n : new ArrayList<>(player.getActiveEffects())) {
                        if (!n.getEffect().isBeneficial()) player.removeEffect(n.getEffect());
                    }
                    player.heal(10.0f);
                    if (event.getLevel() instanceof ServerLevel sl) {
                        spawnCherryButterfly(sl, player);
                    }
                }
            }

            // 未来自我连续性假设（iroi）：手持时刀身紫光；免死扣 10 级经 onLivingHurt 另行处理（攻击附赠剑气已按需求移除）
            if (hasIroiFuture
                    && SpecialEffect.isEffective(ModSpecialEffects.IROI_SE40.getId(), player.experienceLevel)) {
                state.setEffectColor(new Color(0xFF55FF));
            }

            // 妄想彼端的森林萤火（iroi）：激活期间周身 sculk_soul 粒子 + 身前固定一株粉紫花（不跟随）；每 1s 对锁定目标结算 52 真伤并放紫色弧线
            if (hasIroiFirefly
                    && SpecialEffect.isEffective(ModSpecialEffects.IROI_SE50.getId(), player.experienceLevel)
                    && isForestFireflyActive(player)) {
                if (event.getLevel() instanceof ServerLevel ffSl) {
                    // 周身的 soul 粒子只在按下瞬间出现（见输入事件），此处只持续生出花株与结算真伤
                    if (player.tickCount % 8 == 0) spawnForestFireflyFlower(ffSl, player);
                    // 每 1s 对花株周围 3.5 格内所有生物结算 52 真伤（并放紫色弧线）
                    if (player.tickCount % 20 == 0) {
                        Vec3 fpos = FOREST_FLOWER_POS.get(player.getUUID());
                        if (fpos != null) {
                            double r = 3.5;
                            AABB fbox = new AABB(fpos.x - r, fpos.y - 1, fpos.z - r,
                                    fpos.x + r, fpos.y + 3, fpos.z + r);
                            List<LivingEntity> targets = ffSl.getEntitiesOfClass(LivingEntity.class, fbox,
                                    e -> e != player && e.isAlive() && !e.isSpectator());
                            for (LivingEntity t : targets) {
                                applyTrueDamage(t, player, 52.0f);
                                spawnPurpleArcTo(ffSl, player, t);
                            }
                        }
                    }
                }
            }
            // 森林萤火未激活/效果不足以生效时，清理遗留的花株固定位置，避免残留
            if (hasIroiFirefly && !isForestFireflyActive(player)) {
                FOREST_FLOWER_POS.remove(player.getUUID());
            }

            // 流涌灵息之刺：效果激活期间刀身持续发出蓝光
            if (hasFlowRush && isFlowRushActive(player)) {
                state.setEffectColor(new Color(0xAFE9FF));
            }

            // 彼岸蝶舞：效果激活期间刀身持续发出红光
            if (hasButterflyDance && isButterflyDanceActive(player)) {
                state.setEffectColor(new Color(0xFF6B6B));
            }

            // 触及苍穹永恒的面容：效果激活期间使自身与周围 8 格内生物发光
            if (hasSkywardVisage && isSkywardActive(player)) {
                if (player.tickCount % 20 == 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0));
                    for (LivingEntity e : event.getLevel().getEntitiesOfClass(LivingEntity.class,
                            player.getBoundingBox().inflate(8.0),
                            x -> x != player && !x.isSpectator())) {
                        e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0));
                    }
                }
            }

            // 流涌灵息之刺：效果结束后清除转化来的伤害吸收
            if (hasFlowRush && !isFlowRushActive(player)) {
                if (player.hasEffect(MobEffects.ABSORPTION)) {
                    player.removeEffect(MobEffects.ABSORPTION);
                }
            }

            // 离恨烟·公孙离：手持每 0.5s 离恨层数 +1（上限 100），身上的水墨印记「离恨烟」（8 颗墨珠绕身回转）
            // 随层数逐档点亮并变色；满层(100)时全身月白烟雾袅绕
            if (hasGongsunLi
                    && SpecialEffect.isEffective(ModSpecialEffects.GONGSUN_LI_EFFECT.getId(), player.experienceLevel)) {
                UUID uuid = player.getUUID();
                int layer = GONGSUN_LAYERS.getOrDefault(uuid, 0);
                if (player.tickCount % 10 == 0 && layer < 100) {
                    GONGSUN_LAYERS.put(uuid, Math.min(100, layer + 1));
                    layer = GONGSUN_LAYERS.getOrDefault(uuid, 0);
                }
                state.setEffectColor(new Color(gongsunColor(layer)));
                if (player.level() instanceof ServerLevel sl) {
                    double ang0 = player.tickCount * 0.12;
                    int lit = Math.min(8, (int) Math.ceil(layer * 8.0 / 100.0));
                    Vector3f col = rgbVec(gongsunColor(layer));
                    Vector3f dim = new Vector3f(col.x * 0.15f, col.y * 0.15f, col.z * 0.15f);
                    double cyp = player.getY() + player.getBbHeight() * 0.15; // 降近脚边
                    for (int i = 0; i < 8; i++) {
                        double a = ang0 + i / 8.0 * Math.PI * 2;
                        double r = 1.6 + Math.sin(player.tickCount * 0.1 + i * 2.1) * 0.12;
                        double y = cyp + Math.sin(player.tickCount * 0.1 + i) * 0.2;
                        boolean on = i < lit;
                        sl.sendParticles(new DustParticleOptions(on ? col : dim, on ? 0.45f : 0.22f),
                                player.getX() + Math.cos(a) * r, y, player.getZ() + Math.sin(a) * r,
                                1, 0, 0, 0, 0);
                    }
                    // 满层：全身月白烟袅缭绕
                    if (layer >= 100) {
                        for (int k = 0; k < 4; k++) {
                            double a = sl.random.nextDouble() * Math.PI * 2;
                            double r = 0.6 + sl.random.nextDouble() * 0.9;
                            double yy = player.getY() + sl.random.nextDouble() * player.getBbHeight();
                            sl.sendParticles(new DustParticleOptions(new Vector3f(0.88f, 0.97f, 0.96f), 0.7f),
                                    player.getX() + Math.cos(a) * r, yy, player.getZ() + Math.sin(a) * r,
                                    1, 0, 0, 0, 0);
                        }
                    }
                }
            }

            // 喜或悲的谕告：手持时每秒恢复 5 点生命
            if (hasJoySorrowOmen
                    && SpecialEffect.isEffective(ModSpecialEffects.JOY_SORROW_OMEN_EFFECT.getId(), player.experienceLevel)) {
                if (player.tickCount % 20 == 0) {
                    player.heal(5.0f);
                }
            }

            // 月兆堕天的落羽：飞鸟聚成一团紧簇的鸟群（核心亮、尾羽拖极淡金尘），
            // 整团绕玩家身边以较大半径盘旋；对周身 5 格内实体每 0.5 秒（1 秒 2 次）52 点真实伤害并施加缓慢 10
            if (hasFallingMoonFeather
                    && SpecialEffect.isEffective(ModSpecialEffects.FALLING_MOON_FEATHER_EFFECT.getId(), player.experienceLevel)) {
                Level wlevel = event.getLevel();
                if (wlevel instanceof ServerLevel sl) {
                    // 鸟群整体中心：绕玩家水平公转 + 轻微高度起伏，呈一团聚簇
                    double orbitAng = player.tickCount * 0.06;
                    double cx = player.getX() + Math.cos(orbitAng) * 3.5;
                    double cz = player.getZ() + Math.sin(orbitAng) * 3.5;
                    double cy = player.getY() + 1.25 + Math.sin(player.tickCount * 0.12) * 0.25;
                    // 鸟群数量：紧簇的少量粒子（亮核心 + 拖尾金尘）
                    int flockCount = 14;
                    for (int i = 0; i < flockCount; i++) {
                        // 每只鸟在群中心附近紧密聚拢（近核心密、外围疏，整体收拢）
                        double off = (i % 2 == 0) ? 0.12 : 0.26;
                        double a = sl.random.nextDouble() * Math.PI * 2;
                        double px = cx + Math.cos(a) * off;
                        double pz = cz + Math.sin(a) * off;
                        double py = cy + (sl.random.nextDouble() - 0.5) * 0.35;
                        // 核心亮金黄
                        sl.sendParticles(new DustParticleOptions(new Vector3f(1.0f, 0.85f, 0.20f), 0.45f),
                                px, py, pz, 1, 0.02, 0.02, 0.02, 0);
                        // 每只鸟尾部拖出极淡金尘（朝群心反方向）
                        double tx = px - Math.cos(a) * off * 0.5;
                        double tz = pz - Math.sin(a) * off * 0.5;
                        sl.sendParticles(new DustParticleOptions(new Vector3f(1.0f, 0.80f, 0.35f), 0.25f),
                                tx, py - 0.2, tz, 1, 0, 0.06, 0, 0.01);
                    }
                }
                // 1 秒 2 次（每 10 tick）造成伤害
                if (player.tickCount % 10 == 0) {
                    AABB faabb = player.getBoundingBox().inflate(5.0);
                    for (LivingEntity t : wlevel.getEntitiesOfClass(LivingEntity.class, faabb,
                            e -> e != player && !e.isSpectator())) {
                        applyTrueDamage(t, player, 52.0f);
                        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 9));
                    }
                }
            }

            // 新月自己的法则：持有获得饱和；每 2.5s 治疗 20 点并驱散所有负面效果 + 圆环从小到大散发的水波粒子
            if (hasNewMoonLaw
                    && SpecialEffect.isEffective(ModSpecialEffects.NEW_MOON_LAW_EFFECT.getId(), player.experienceLevel)) {
                state.setEffectColor(new Color(0x55FFFF));
                if (player.tickCount % 20 == 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 40, 0));
                }
                if (player.tickCount % 50 == 0) {
                    player.heal(20.0f);
                    if (player.level() instanceof ServerLevel newMoonSl) {
                        // 驱散所有负面效果（收集后逐个移除，避免并发修改）
                        player.getActiveEffects().stream()
                                .filter(e -> e.getEffect().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL)
                                .map(MobEffectInstance::getEffect)
                                .collect(java.util.stream.Collectors.toList())
                                .forEach(player::removeEffect);
                        // 触发圆环动画：从最小半径开始，每 tick 向外扩一步
                        NEWMOON_RING_STEP.put(player.getUUID(), 0);
                        // 受疗者身上飘 3 粒粉白 Dust 微粒
                        newMoonSl.sendParticles(new DustParticleOptions(new Vector3f(1.0f, 0.85f, 0.9f), 0.8f),
                                player.getX(), player.getY() + 1.2, player.getZ(), 3, 0.15, 0.3, 0.15, 0);
                    }
                }
                // 圆环从小到大扩散：每 tick 在更大的半径处补一圈浅青 Dust + 前缘 GLOW
                if (player.level() instanceof ServerLevel newMoonSl) {
                    Integer ringStep = NEWMOON_RING_STEP.get(player.getUUID());
                    if (ringStep != null) {
                        double r = 0.5 + 0.25 * ringStep;              // 半径 0.5 → 3（缩小最大范围）
                        float size = 1.2f - 0.6f * (ringStep / 10.0f); // 渐淡
                        double aBase = newMoonSl.random.nextDouble() * Math.PI * 2;
                        for (int i = 0; i < 20; i++) {
                            double a = aBase + i / 20.0 * Math.PI * 2;
                            newMoonSl.sendParticles(new DustParticleOptions(new Vector3f(0.667f, 1.0f, 1.0f), size),
                                    player.getX() + Math.cos(a) * r, player.getY() + 0.3, player.getZ() + Math.sin(a) * r,
                                    1, Math.cos(a) * 0.25, 0, Math.sin(a) * 0.25, 0);
                        }
                        // 前缘 5 粒 GLOW 余辉缓慢上浮
                        for (int i = 0; i < 5; i++) {
                            double a = aBase + i / 5.0 * Math.PI * 2;
                            newMoonSl.sendParticles(ParticleTypes.GLOW,
                                    player.getX() + Math.cos(a) * (r + 0.3), player.getY() + 0.35, player.getZ() + Math.sin(a) * (r + 0.3),
                                    1, 0, 0.06, 0, 1.0);
                        }
                        if (ringStep >= 10) newMoonSl.getServer().execute(() -> NEWMOON_RING_STEP.remove(player.getUUID()));
                        else NEWMOON_RING_STEP.put(player.getUUID(), ringStep + 1);
                    }
                }
            }

            // 仙乡的赠别礼：根据露米 buff 等级，每秒提升攻击力 10×等级 点（上限 等级×100）
            if (hasCelestialFarewell
                    && SpecialEffect.isEffective(ModSpecialEffects.CELESTIAL_FAREWELL_EFFECT.getId(), player.experienceLevel)) {
                MobEffectInstance lumi = player.getEffect(ModEffects.LUMI.get());
                if (lumi != null) {
                    int level = lumi.getAmplifier() + 1;
                    long held = LUMI_HELD_TICKS.getOrDefault(player.getUUID(), 0L) + 1;
                    LUMI_HELD_TICKS.put(player.getUUID(), held);
                    // 每 20 tick（1 秒）增加 10×等级 点，上限为 等级×100
                    if (held % 20 == 0) {
                        float bonus = LUMI_ATTACK_BONUS.getOrDefault(player.getUUID(), 0f) + 10.0f * level;
                        float cap = level * 100.0f;
                        LUMI_ATTACK_BONUS.put(player.getUUID(), Math.min(bonus, cap));
                    }
                    float bonus = LUMI_ATTACK_BONUS.getOrDefault(player.getUUID(), 0f);
                    removeLumiModifier(player);
                    player.getAttribute(Attributes.ATTACK_DAMAGE).addTransientModifier(
                            new AttributeModifier(LUMI_ATTACK_ID, "celestial_farewell_attack", bonus, AttributeModifier.Operation.ADDITION));
                } else {
                    removeLumiModifier(player);
                    LUMI_HELD_TICKS.remove(player.getUUID());
                    LUMI_ATTACK_BONUS.remove(player.getUUID());
                }
            } else {
                removeLumiModifier(player);
                LUMI_HELD_TICKS.remove(player.getUUID());
                LUMI_ATTACK_BONUS.remove(player.getUUID());
            }

            // sakurafox：刀身墨色置于各 SE 刀身色之后，保证始终以「每次挥砍随机色」为准（墨绿/暗绿/深蓝），未挥砍前默认墨绿 0x145F31
            if (state.getModel().map(m -> "model/named/sakurafox.obj".equals(m.getPath())).orElse(false)) {
                state.setEffectColor(new Color(SAKURA_SWING_COLOR.getOrDefault(player.getUUID(), 0x145F31)));
            }
        }

        /** 攻击伤害结算：将「众水」累积的攻击力倍率应用到伤害上 */
        public static void onUpdateAttack(SlashBladeEvent.UpdateAttackEvent event) {
            ISlashBladeState state = event.getSlashBladeState();
            if (state.hasSpecialEffect(ModSpecialEffects.ALL_WATERS_EFFECT.getId())) {
                float amp = state.getAttackAmplifier();
                if (amp > 0f) {
                    event.setNewDamage(event.getNewDamage() * (1.0 + amp));
                }
            }
        }

        /** 「翾风回雪」闪避远程伤害；「罪人舞步旋」「蝶火燎原」致死免疫；「流涌灵息之刺」两倍真实伤害 */
        public static void onLivingIncomingDamage(LivingDamageEvent event) {
            // 流涌灵息之刺：效果激活期间，玩家所有拔刀攻击转为两倍真实伤害
            if (!flowRushApplying && event.getSource().getEntity() instanceof Player attacker) {
                ItemStack atkStack = attacker.getMainHandItem();
                ISlashBladeState atkState = atkStack.getCapability(CapabilitySlashBlade.BLADESTATE, null).orElse(null);
                if (atkState != null && atkState.hasSpecialEffect(ModSpecialEffects.FLOW_RUSH_EFFECT.getId())
                        && isFlowRushActive(attacker)) {
                    LivingEntity target = event.getEntity();
                    float amount = event.getAmount();
                    event.setCanceled(true);
                    flowRushApplying = true;
                    try {
                        applyTrueDamage(target, attacker, amount);
                        applyTrueDamage(target, attacker, amount);
                    } finally {
                        flowRushApplying = false;
                    }
                    return;
                }
            }

            if (!(event.getEntity() instanceof Player player)) return;
            ItemStack stack = player.getMainHandItem();
            ISlashBladeState state = stack.getCapability(CapabilitySlashBlade.BLADESTATE, null).orElse(null);
            if (state == null) return;

            // 流涌灵息之刺：效果激活期间，玩家受到的伤害转化为「伤害吸收」（类似金苹果的金色生命效果），效果结束自动清除
            if (state.hasSpecialEffect(ModSpecialEffects.FLOW_RUSH_EFFECT.getId())
                    && isFlowRushActive(player)) {
                float dmg = event.getAmount();
                event.setCanceled(true);
                // 每级伤害吸收提供约 8 点（4 颗心），按累计吸收生命给出足够等级，最小为 I 级
                float acc = player.getAbsorptionAmount() + dmg;
                int amp = (int) ((acc - 1f) / 8f);
                amp = Math.max(0, Math.min(amp, 127));
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 480, amp));
                return;
            }

            // 翾风回雪：闪避远程伤害（弓箭/魔法等）—— 具体实现在 dodgeRangedDamage(...)，供多个 SE 共用
            boolean hasXuanfeng = state.hasSpecialEffect(ModSpecialEffects.XUANFENG_HUIXUE_EFFECT.getId())
                    && SpecialEffect.isEffective(ModSpecialEffects.XUANFENG_HUIXUE_EFFECT.getId(), player.experienceLevel);
            if (hasXuanfeng && dodgeRangedDamage(event, player)) return;

            // 吻痕窥梦梦魇生花：同样闪避远程攻击 —— **直接调用上面同一个方法**（不复制粘贴）
            boolean hasZankouDream = state.hasSpecialEffect(ModSpecialEffects.ZANKOU_SE40.getId())
                    && SpecialEffect.isEffective(ModSpecialEffects.ZANKOU_SE40.getId(), player.experienceLevel);
            if (hasZankouDream && dodgeRangedDamage(event, player)) return;

            // 银簪·离魂烟暖笛烟化蝶舞：远程免疫 —— **同样复用 dodgeRangedDamage**，只是额外加了抗性提升 5 与蝶光粒子
            boolean hasSilverPinMoth = state.hasSpecialEffect(ModSpecialEffects.SILVER_PIN_MOTH_EFFECT.getId())
                    && SpecialEffect.isEffective(ModSpecialEffects.SILVER_PIN_MOTH_EFFECT.getId(), player.experienceLevel);
            if (hasSilverPinMoth && dodgeRangedDamage(event, player)) {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 4)); // 抗性提升5，5秒
                if (player.level() instanceof ServerLevel sl) {
                    DustParticleOptions deep = new DustParticleOptions(new Vector3f(0.16f, 0.42f, 0.22f), 0.8f);
                    DustParticleOptions mute = new DustParticleOptions(new Vector3f(0.22f, 0.58f, 0.30f), 0.8f);
                    DustParticleOptions mid = new DustParticleOptions(new Vector3f(0.32f, 0.78f, 0.42f), 0.8f);
                    for (int i = 0; i < 48; i++) {
                        double a = player.level().random.nextDouble() * Math.PI * 2;
                        double r = 0.4 + player.level().random.nextDouble() * 1.4;
                        double y = player.getY() + player.getBbHeight() * player.level().random.nextDouble();
                        DustParticleOptions p = switch (i % 3) { case 0 -> deep; case 1 -> mute; default -> mid; };
                        sl.sendParticles(p,
                                player.getX() + Math.cos(a) * r, y, player.getZ() + Math.sin(a) * r,
                                1, 0, 0, 0, 0);
                    }
                }
                return;
            }

            // ===== 致死免疫（免死）=====
            // LivingDamageEvent 在 actuallyHurt 里是「扣血之前」触发的（已反编译确认），
            // 取消它会把本次伤害量置 0 → 血量根本不会掉。所以这里的拦截本身是对的。
            if (event.getAmount() < player.getHealth() + player.getAbsorptionAmount()) return;
            if (applyDeathWard(player)) {
                event.setCanceled(true);
            }
        }

        // ==================== 致死免疫（免死）====================

        /** 具备「致死免疫」的全部 SE（等级需求由 SE 自身决定：joy_sorrow_omen 30 / iroi_se40 40 / hualan_yunyi 50 / flute_soul 80） */
        private static final List<RegistryObject<SpecialEffect>> DEATH_WARDS = List.of(
                ModSpecialEffects.SINNER_DANCE_EFFECT,
                ModSpecialEffects.BUTTERFLY_BLAZE_EFFECT,
                ModSpecialEffects.JOY_SORROW_OMEN_EFFECT,
                ModSpecialEffects.FLUTE_SOUL_EFFECT,
                ModSpecialEffects.HUALAN_YUNYI_EFFECT,
                ModSpecialEffects.IROI_SE40);

        /** 免死「代价」（扣级 + 粒子）的最小间隔 tick：免死本身每次都生效，只是代价不重复刷屏 */
        private static final int DEATH_WARD_COST_COOLDOWN = 10;
        /** 兜底免死时补回的血量（占最大生命的比例，最少 1 点）——死亡流程里血量已见底，必须补回正数 */
        private static final float DEATH_WARD_REVIVE_RATIO = 0.2f;
        /** 每名玩家上次结算免死代价的 tick */
        private static final Map<UUID, Long> DEATH_WARD_LAST_COST = new HashMap<>();

        /**
         * 玩家手上（主手<b>或副手</b>）是否有该免死 SE 且当前等级满足其需求。
         * 原实现只查主手 —— 副手持刀、或主手拿了别的武器/工具时，免死会完全不生效。
         */
        private static boolean hasDeathWard(Player player, RegistryObject<SpecialEffect> se) {
            ResourceLocation id = se.getId();
            for (ItemStack held : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
                ISlashBladeState heldState = held.getCapability(CapabilitySlashBlade.BLADESTATE, null).orElse(null);
                if (heldState != null && heldState.hasSpecialEffect(id)
                        && SpecialEffect.isEffective(id, player.experienceLevel)) {
                    return true;
                }
            }
            return false;
        }

        /** 是否有任意一个生效的免死 SE（主手/副手任一即可） */
        private static boolean hasAnyDeathWard(Player player) {
            for (RegistryObject<SpecialEffect> se : DEATH_WARDS) {
                if (hasDeathWard(player, se)) return true;
            }
            return false;
        }

        /**
         * 免死判定 + 结算：返回 true 表示本次致死被免掉（调用方负责取消伤害/死亡事件）。
         * 代价与粒子最多每 {@link #DEATH_WARD_COST_COOLDOWN} tick 结算一次，避免连续致死把等级瞬间扣光。
         */
        private static boolean applyDeathWard(Player player) {
            if (!hasAnyDeathWard(player)) return false;
            player.invulnerableTime = 20;

            long now = player.level().getGameTime();
            Long last = DEATH_WARD_LAST_COST.get(player.getUUID());
            if (last != null && now - last < DEATH_WARD_COST_COOLDOWN) return true;
            DEATH_WARD_LAST_COST.put(player.getUUID(), now);

            drainDeathWardCost(player);
            playDeathWardEffects(player);
            return true;
        }

        /**
         * 免死代价：扣经验等级（原本固定扣 10 级）。
         * <b>绝不把等级扣到该 SE 的需求线以下</b> —— 原实现硬扣 10 级，flute_soul(需 80) 会掉到 70、
         * hualan_yunyi(50→40)、iroi_se40(40→30)、joy_sorrow_omen(30→20) 全部当场失效，
         * 于是「免死」变成一次性的、下一次致死就被杀死。这是本次修复的核心。
         */
        private static void drainDeathWardCost(Player player) {
            int cur = player.experienceLevel;
            int drain = Math.min(10, Math.max(0, cur));
            for (RegistryObject<SpecialEffect> se : DEATH_WARDS) {
                if (!hasDeathWard(player, se)) continue;
                int req = 0;
                try {
                    req = SpecialEffect.getRequestLevel(se.getId());
                } catch (Exception ignored) {
                    // 极端情况下（未注册等）不设限
                }
                drain = Math.min(drain, Math.max(0, cur - req));
            }
            if (drain > 0) player.giveExperienceLevels(-drain);
        }

        /**
         * 免死兜底：拦截**不经过致死伤害判定**就进入死亡流程的情况（其它 mod 直接 die()/setHealth(0)、
         * 部分环境死亡、指令等）。die() 的第一句就是 ForgeHooks.onLivingDeath，取消它即不会死亡。
         */
        public static void onLivingDeath(LivingDeathEvent event) {
            if (!(event.getEntity() instanceof Player player)) return;
            if (player.level().isClientSide) return;
            if (!applyDeathWard(player)) return;

            event.setCanceled(true);
            // 能走到这里说明血量已经见底，必须补回正数；否则 isDeadOrDying() 恒为 true，会被反复判死、无法正常受击
            player.setHealth(Math.min(player.getMaxHealth(),
                    Math.max(1.0f, player.getMaxHealth() * DEATH_WARD_REVIVE_RATIO)));
            player.clearFire();
        }

        /** 免死触发时的表现：各 SE 各自的粒子与附加效果 */
        private static void playDeathWardEffects(Player player) {
            boolean hasSinnerDance = hasDeathWard(player, ModSpecialEffects.SINNER_DANCE_EFFECT);
            boolean hasButterflyBlaze = hasDeathWard(player, ModSpecialEffects.BUTTERFLY_BLAZE_EFFECT);
            boolean hasJoySorrowOmen = hasDeathWard(player, ModSpecialEffects.JOY_SORROW_OMEN_EFFECT);
            boolean hasFluteSoul = hasDeathWard(player, ModSpecialEffects.FLUTE_SOUL_EFFECT);
            boolean hasHualanYunyi = hasDeathWard(player, ModSpecialEffects.HUALAN_YUNYI_EFFECT);
            boolean hasIroiFuture = hasDeathWard(player, ModSpecialEffects.IROI_SE40);

            if (hasIroiFuture) {
                // 未来自我连续性假设（iroi）：免死时周身喷发一圈粉紫粒子（亮中心、四周渐淡）
                if (player.level() instanceof ServerLevel sl) {
                    DustParticleOptions f1 = new DustParticleOptions(new Vector3f(1.0f, 0.33f, 1.0f), 1.4f);
                    DustParticleOptions f2 = new DustParticleOptions(new Vector3f(1.0f, 0.62f, 1.0f), 1.4f);
                    DustParticleOptions f3 = new DustParticleOptions(new Vector3f(1.0f, 0.78f, 1.0f), 1.4f);
                    for (int i = 0; i < 60; i++) {
                        double a = sl.random.nextDouble() * Math.PI * 2;
                        double r = 0.6 + sl.random.nextDouble() * 2.2;
                        double y = player.getY() + player.getBbHeight() * sl.random.nextDouble();
                        DustParticleOptions p = switch (i % 3) { case 0 -> f1; case 1 -> f2; default -> f3; };
                        sl.sendParticles(p,
                                player.getX() + Math.cos(a) * r, y, player.getZ() + Math.sin(a) * r,
                                1, 0, 0, 0, 0);
                    }
                }
            }

            if (hasSinnerDance) {
                // 烟花：深蓝渐变到浅蓝
                spawnFirework(player, new Vector3f(0.0f, 0.0f, 0.8f), new Vector3f(0.53f, 0.81f, 0.98f));
            }
            if (hasButterflyBlaze) {
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, 9));
                // 烟花：深红渐变到亮红
                spawnFirework(player, new Vector3f(0.55f, 0.0f, 0.0f), new Vector3f(1.0f, 0.27f, 0.0f));
            }
            if (hasJoySorrowOmen) {
                // 喜或悲的谕告：周身喷发一圈黄色→橙色→金色粒子（亮中心、四周渐淡）
                if (player.level() instanceof ServerLevel sl) {
                    DustParticleOptions yellow = new DustParticleOptions(new Vector3f(1.0f, 0.90f, 0.25f), 1.4f);
                    DustParticleOptions orange = new DustParticleOptions(new Vector3f(1.0f, 0.60f, 0.15f), 1.4f);
                    DustParticleOptions gold  = new DustParticleOptions(new Vector3f(1.0f, 0.78f, 0.05f), 1.4f);
                    for (int i = 0; i < 60; i++) {
                        double a = player.level().random.nextDouble() * Math.PI * 2;
                        double r = 0.6 + player.level().random.nextDouble() * 2.2;
                        double y = player.getY() + player.getBbHeight() * player.level().random.nextDouble();
                        DustParticleOptions p = switch (i % 3) { case 0 -> yellow; case 1 -> orange; default -> gold; };
                        sl.sendParticles(p,
                                player.getX() + Math.cos(a) * r, y, player.getZ() + Math.sin(a) * r,
                                1, 0, 0, 0, 0);
                    }
                }
            }
            if (hasFluteSoul) {
                // 笛烟和声魂断绝：周身爆发一圈墨色→山水翠绿→水墨蓝粒子（亮中心、四周渐淡）
                if (player.level() instanceof ServerLevel sl) {
                    DustParticleOptions inkDeep = new DustParticleOptions(new Vector3f(0.16f, 0.42f, 0.22f), 1.4f);
                    DustParticleOptions inkGreen = new DustParticleOptions(new Vector3f(0.32f, 0.84f, 0.58f), 1.4f);
                    DustParticleOptions inkBlue = new DustParticleOptions(new Vector3f(0.55f, 0.87f, 1.0f), 1.4f);
                    for (int i = 0; i < 60; i++) {
                        double a = player.level().random.nextDouble() * Math.PI * 2;
                        double r = 0.6 + player.level().random.nextDouble() * 2.2;
                        double y = player.getY() + player.getBbHeight() * player.level().random.nextDouble();
                        DustParticleOptions p = switch (i % 3) { case 0 -> inkDeep; case 1 -> inkGreen; default -> inkBlue; };
                        sl.sendParticles(p,
                                player.getX() + Math.cos(a) * r, y, player.getZ() + Math.sin(a) * r,
                                1, 0, 0, 0, 0);
                    }
                    // 笛烟和声魂断绝：身前竖立一把折扇（扇轴在下方、扇面竖直朝上展开正对玩家），墨绿色/深绿色/水墨色
                    {
                        Vec3 flook = player.getLookAngle();
                        double hx = flook.x, hz = flook.z;
                        double hl = Math.sqrt(hx * hx + hz * hz);
                        if (hl < 0.001) { hx = 0; hz = 1; } else { hx /= hl; hz /= hl; }
                        double rx = -hz, rz = hx; // 右侧轴：扇面在「竖直(上) × 右」平面内展开，正对玩家
                        // 扇轴：玩家身前 1.9 格（比原先远一格）、约胸口高度（扇骨自此处向上散开）
                        double bx = player.getX() + hx * 1.9, by = player.getY() + 0.7, bz = player.getZ() + hz * 1.9;
                        DustParticleOptions fanInk   = new DustParticleOptions(new Vector3f(0.13f, 0.13f, 0.14f), 1.0f); // 水墨色
                        DustParticleOptions fanGreen = new DustParticleOptions(new Vector3f(0.08f, 0.37f, 0.19f), 1.0f); // 墨绿色
                        DustParticleOptions fanDeep  = new DustParticleOptions(new Vector3f(0.035f, 0.20f, 0.10f), 1.0f); // 深绿色
                        for (int a = -6; a <= 6; a++) {
                            double ang = a * Math.PI / 12.0;  // 每根扇骨与竖直方向夹角（±90°，合计 180° 扇面）
                            double dx = Math.sin(ang);        // 右向分量
                            double dy = Math.cos(ang);        // 竖直（向上）分量
                            DustParticleOptions rib = (Math.abs(a) >= 5) ? fanInk
                                    : (Math.abs(a) >= 3 ? fanGreen : fanDeep);
                            for (double r = 0.3; r <= 1.75; r += 0.11) {
                                sl.sendParticles(rib,
                                        bx + rx * dx * r,
                                        by + dy * r,
                                        bz + rz * dx * r,
                                        1, 0, 0, 0, 0);
                            }
                        }
                    }
                }
            }
            if (hasHualanYunyi) {
                // 花岚云翳山岩树影：免死 + 周身喷发一圈天蓝粒子（亮中心、四周渐淡）
                if (player.level() instanceof ServerLevel sl) {
                    DustParticleOptions c1 = new DustParticleOptions(new Vector3f(0.53f, 0.81f, 0.92f), 1.4f);
                    DustParticleOptions c2 = new DustParticleOptions(new Vector3f(0.33f, 0.75f, 1.0f), 1.4f);
                    DustParticleOptions c3 = new DustParticleOptions(new Vector3f(0.80f, 0.95f, 1.0f), 1.4f);
                    for (int i = 0; i < 60; i++) {
                        double a = player.level().random.nextDouble() * Math.PI * 2;
                        double r = 0.6 + player.level().random.nextDouble() * 2.2;
                        double y = player.getY() + player.getBbHeight() * player.level().random.nextDouble();
                        DustParticleOptions p = switch (i % 3) { case 0 -> c1; case 1 -> c2; default -> c3; };
                        sl.sendParticles(p,
                                player.getX() + Math.cos(a) * r, y, player.getZ() + Math.sin(a) * r,
                                1, 0, 0, 0, 0);
                    }
                }
            }
        }

        /** 「触及苍穹永恒的面容」：效果激活期间，攻击命中目标使其失去 AI；玩家受到的伤害等量转为魔法伤害扩散给周围 8 格内其他生物 */
        public static void onLivingDamagePost(LivingDamageEvent event) {
            if (skywardSpreading) return;

            // 未来自我连续性假设（iroi）：手持该刀且效果激活时，每 3 次攻击命中目标，朝目标放出一道十字剑气（两道翾风回雪刀波一横一竖交叉，52 伤，无粒子）
            if (event.getSource().getEntity() instanceof Player iroiAtk) {
                LivingEntity iroiTg = event.getEntity();
                ItemStack iroiStk = iroiAtk.getMainHandItem();
                ISlashBladeState iroiSt = iroiStk.getCapability(CapabilitySlashBlade.BLADESTATE, null).orElse(null);
                if (iroiSt != null
                        && iroiSt.hasSpecialEffect(ModSpecialEffects.IROI_SE40.getId())
                        && SpecialEffect.isEffective(ModSpecialEffects.IROI_SE40.getId(), iroiAtk.experienceLevel)
                        && iroiAtk.level() instanceof ServerLevel iroiSl) {
                    UUID iroiId = iroiAtk.getUUID();
                    int cnt = IROI_CROSS_SLASH_COUNT.getOrDefault(iroiId, 0) + 1;
                    IROI_CROSS_SLASH_COUNT.put(iroiId, cnt);
                    if (cnt >= 3) {
                        IROI_CROSS_SLASH_COUNT.put(iroiId, 0);
                        fireCrossSword(iroiSl, iroiAtk, iroiTg);
                    }
                }
            }

            // 柔光凝露梦湖起波：激活期间，玩家拔刀攻击命中目标时额外造成目标最大生命 1% 的真实（魔法）伤害 + 天蓝弧光粒子
            if (!rouguangApplying && event.getSource().getEntity() instanceof Player attacker) {
                LivingEntity rgTarget = event.getEntity();
                ItemStack rgStack = attacker.getMainHandItem();
                ISlashBladeState rgState = rgStack.getCapability(CapabilitySlashBlade.BLADESTATE, null).orElse(null);
                if (rgState != null
                        && rgState.hasSpecialEffect(ModSpecialEffects.ROUGUANG_NINGLU_EFFECT.getId())
                        && isRouguangActive(attacker)) {
                    float bonus = rgTarget.getMaxHealth() * 0.01f;
                    if (bonus > 0.01f) {
                        rouguangApplying = true;
                        try {
                            rgTarget.invulnerableTime = 0;
                            applyTrueDamage(rgTarget, attacker, bonus);
                            if (rgTarget.level() instanceof ServerLevel rgSl) {
                                spawnSkyArcHit(rgSl, rgTarget);
                            }
                        } finally {
                            rouguangApplying = false;
                        }
                    }
                }
            }

            // 玩家受伤：把实际受到的伤害等量转为魔法伤害，扩散给周围 8 格内其他生物
            if (event.getEntity() instanceof Player player && isSkywardActive(player)) {
                ItemStack syStack = player.getMainHandItem();
                ISlashBladeState syState = syStack.getCapability(CapabilitySlashBlade.BLADESTATE, null).orElse(null);
                if (syState != null && syState.hasSpecialEffect(ModSpecialEffects.SKYWARD_VISAGE_EFFECT.getId())) {
                    float dmg = event.getAmount();
                    if (dmg > 0f) {
                        skywardSpreading = true;
                        try {
                            AABB aabb = player.getBoundingBox().inflate(8.0);
                            for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, aabb,
                                    x -> x != player && x.isAlive() && !x.isSpectator())) {
                                applyTrueDamage(e, player, dmg);
                            }
                        } finally {
                            skywardSpreading = false;
                        }
                    }
                }
                return;
            }

            // 玩家攻击命中目标：使其失去 AI
            if (!(event.getSource().getEntity() instanceof Player attacker)) return;
            ItemStack atkStack = attacker.getMainHandItem();
            ISlashBladeState atkState = atkStack.getCapability(CapabilitySlashBlade.BLADESTATE, null).orElse(null);
            if (atkState == null
                    || !atkState.hasSpecialEffect(ModSpecialEffects.SKYWARD_VISAGE_EFFECT.getId())
                    || !isSkywardActive(attacker)) return;
            if (event.getEntity() instanceof Mob mob) {
                mob.setNoAi(true);
            }
        }

        /**
         * 「远程闪避」的公共实现 —— 原本是 odette·翾风回雪里内联的那套，现在抽出来供多个 SE 共用
         * （翾风回雪 / 银簪·离魂烟暖笛烟化蝶舞 / 吻痕窥梦梦魇生花，调用方只负责判断 SE 是否生效）。
         * 内容与原来完全一致：取消本次伤害 + 1s 无敌 + 1s 隐身 + 白灰烟花爆开。
         *
         * @return 是否确实闪避了（不是远程攻击则返回 false，调用方继续往下走）
         */
        private static boolean dodgeRangedDamage(LivingDamageEvent event, Player player) {
            if (!isRangedDamage(event.getSource(), player)) return false;
            event.setCanceled(true);
            player.invulnerableTime = 20;
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20, 0));
            spawnFirework(player, new Vector3f(1.0f, 1.0f, 1.0f), new Vector3f(0.85f, 0.85f, 0.85f));
            return true;
        }

        /** 判断是否为远程伤害（弓箭 / 魔法 / 火球等，或攻击者距离 5 格以上） */
        private static boolean isRangedDamage(DamageSource source, Player player) {
            if (source.is(DamageTypes.ARROW)
                    || source.is(DamageTypes.TRIDENT)
                    || source.is(DamageTypes.MAGIC)
                    || source.is(DamageTypes.INDIRECT_MAGIC)
                    || source.is(DamageTypes.WITHER)
                    || source.is(DamageTypes.FIREBALL)
                    || source.is(DamageTypes.UNATTRIBUTED_FIREBALL)
                    || source.is(DamageTypes.DRAGON_BREATH)
                    || source.is(DamageTypes.THORNS)) {
                return true;
            }
            // 攻击者距离玩家 5 格以上时视为远程攻击（覆盖远古守卫者激光等特殊远程）
            Entity attacker = source.getEntity();
            if (attacker instanceof LivingEntity livingAttacker) {
                return livingAttacker.distanceToSqr(player) > 25.0;
            }
            return false;
        }

        /** 离恨烟·公孙离：根据离恨层数返回墨珠当前档的颜色（深墨→墨绿→水墨蓝→天青水蓝） */
        private static int gongsunColor(int layer) {
            if (layer >= 76) return 0x8CDFFF;  // 天青水蓝
            if (layer >= 51) return 0x5285BF; // 水墨蓝
            if (layer >= 26) return 0x52D694; // 墨绿
            return 0x0D1412;                  // 深墨
        }

        /** int RGB → 0~1 的 Vector3f（用于 Dust 粒子上色） */
        private static Vector3f rgbVec(int rgb) {
            return new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
        }

        /** 柔光凝露梦湖起波：每次命中只召唤一条斜向的星形电火（ELECTRIC_SPARK），从目标斜上方斜插向头顶 */
        private static void spawnSkyArcHit(ServerLevel sl, LivingEntity target) {
            // 目标头顶落点
            double tx = target.getX();
            double tz = target.getZ();
            double top = target.getY() + target.getBbHeight() + 0.05;
            // 斜向：随机水平方位 + 随机倾斜，一条直线斜插向头顶
            double a = sl.random.nextDouble() * Math.PI * 2;      // 绕目标的水平方位角
            double tilt = 0.25 + sl.random.nextDouble() * 0.6;    // 倾斜程度（越大越斜）
            double len = 2.4 + sl.random.nextDouble() * 1.4;      // 电火长度
            double horiz = len * Math.sin(tilt);
            double vert = len * Math.cos(tilt);
            double sx = tx + Math.cos(a) * horiz;
            double sz = tz + Math.sin(a) * horiz;
            double sy = top + vert;
            // 沿「起点 → 头顶」这条直线均匀落点，点够密以连成一条线
            int seg = 22;
            for (int k = 0; k <= seg; k++) {
                double f = k / (double) seg;
                double px = sx + (tx - sx) * f;
                double py = sy + (top - sy) * f;
                double pz = sz + (tz - sz) * f;
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, px, py, pz, 1, 0, 0, 0, 0);
            }
        }

        /** 自诩宇宙的精华（iroi）：脚下聚集一朵蝴蝶形状的樱花（蝶翼前大后小、左右对称，静态不下落不乱飘，仅生成） */
        private static void spawnCherryButterfly(ServerLevel sl, Player player) {
            double x = player.getX(), z = player.getZ();
            double y = player.getY() + 0.15; // 玩家脚边
            // 蝶身：中央一列
            for (int i = 0; i < 4; i++) {
                sl.sendParticles(ParticleTypes.CHERRY_LEAVES, x, y, z, 1, 0, 0, 0, 0);
            }
            // 左右两翼，各由前翅（大）与后翅（小）两片扁椭圆组成
            for (int side = -1; side <= 1; side += 2) {
                for (int i = 0; i < 12; i++) {
                    double t = (i / 12.0) * Math.PI * 2;
                    double px = Math.cos(t) * 0.40;
                    double pz = side * (0.30 + Math.sin(t) * 0.42);
                    sl.sendParticles(ParticleTypes.CHERRY_LEAVES, x + px, y, z + pz, 1, 0, 0, 0, 0);
                }
                for (int i = 0; i < 8; i++) {
                    double t = (i / 8.0) * Math.PI * 2;
                    double px = -0.20 + Math.cos(t) * 0.26;
                    double pz = side * (0.16 + Math.sin(t) * 0.26);
                    sl.sendParticles(ParticleTypes.CHERRY_LEAVES, x + px, y, z + pz, 1, 0, 0, 0, 0);
                }
            }
        }

        /** 妄天彼端的森林萤火（iroi）：在身前较远处生成一株粉紫/浅紫（亮中心）小花的粒子花株，固定原地、花头用圆形生成 */
        private static void spawnForestFireflyFlower(ServerLevel sl, Player player) {
            // 花株固定生成在身前 3 格、存为原地位置，此后原地持续重绘不跟随玩家；距玩家超过 7 格才重置于面前
            UUID fid = player.getUUID();
            Vec3 pos = FOREST_FLOWER_POS.get(fid);
            if (pos == null || player.distanceToSqr(pos.x, pos.y, pos.z) > 49.0) {
                Vec3 front = player.getEyePosition().add(player.getLookAngle().normalize().scale(3.0));
                pos = new Vec3(front.x, player.getY() + 0.1, front.z);
                FOREST_FLOWER_POS.put(fid, pos);
            }
            double bx = pos.x;
            double bz = pos.z;
            double by = pos.y;
            // 花茎（青绿，微微内弯成弧）
            Vector3f stem = new Vector3f(0.45f, 0.85f, 0.55f);
            for (int i = 0; i < 8; i++) {
                double h = by + i * 0.12;
                double curve = Math.sin(i / 8.0 * Math.PI) * 0.05;
                sl.sendParticles(new DustParticleOptions(stem, 1.0f), bx + curve, h, bz, 1, 0, 0, 0, 0.01);
            }
            double top = by + 1.0;
            // 圆形花头：外→内三层同心圆环由浅紫到亮紫聚拢成圆盘，中心最亮
            Vector3f light = new Vector3f(1.0f, 0.80f, 1.0f); // 浅紫外圈
            Vector3f mid = new Vector3f(1.0f, 0.62f, 1.0f);   // 粉紫中圈
            Vector3f deep = new Vector3f(1.0f, 0.35f, 1.0f);  // 亮紫内圈
            double[] radii = {0.22, 0.14, 0.06};
            int[] counts = {10, 8, 6};
            Vector3f[] cols = {light, mid, deep};
            for (int r = 0; r < 3; r++) {
                int n = counts[r];
                double rad = radii[r];
                double offset = r * 0.6; // 错开角度使环更圆润
                for (int i = 0; i < n; i++) {
                    double a = (i / (double) n) * Math.PI * 2 + offset;
                    double px = bx + Math.cos(a) * rad;
                    double pz = bz + Math.sin(a) * rad;
                    sl.sendParticles(new DustParticleOptions(cols[r], 1.2f), px, top, pz, 1, 0, 0, 0, 0.01);
                }
            }
            // 亮花心
            sl.sendParticles(new DustParticleOptions(deep, 1.6f), bx, top, bz, 8, 0.05, 0.02, 0.05, 0.01);
        }

        /** 妄天彼端的森林萤火（iroi）：朝锁定目标放出一道紫色幻影剑弧线与上凸弧光粒子（真伤已即时结算） */
        private static void spawnPurpleArcTo(ServerLevel sl, Player player, LivingEntity target) {
            Vec3 origin = player.getEyePosition().add(player.getLookAngle().normalize().scale(3.0)).add(0, 2.2, 0);
            Vec3 aim = target.getBoundingBox().getCenter();
            EntityAbstractSummonedSword sword = new EntityAbstractSummonedSword(
                    mods.flammpfeil.slashblade.SlashBlade.RegistryEvents.SummonedSword, sl);
            sword.setPos(origin);
            sword.setShooter(player);
            sword.setColor(0xFF55FF);
            sword.setRoll(0.0F);
            sword.setDamage(0.0F); // 纯展示，52 真伤已即时结算给锁定目标
            Vec3 d = aim.subtract(origin);
            sword.shoot(d.x, d.y, d.z, 1.6F, 0.0F);
            sl.addFreshEntity(sword);
            Vector3f arcCol = new Vector3f(1.0f, 0.5f, 1.0f);
            for (int k = 1; k <= 12; k++) {
                float t = k / 12.0f;
                double gx = origin.x + (aim.x - origin.x) * t;
                double gy = origin.y + (aim.y - origin.y) * t + Math.sin(t * Math.PI) * 1.8;
                double gz = origin.z + (aim.z - origin.z) * t;
                sl.sendParticles(new DustParticleOptions(arcCol, 0.7f), gx, gy, gz, 1, 0, 0, 0, 0.02);
            }
        }

        /** 未来自我连续性假设（iroi）：朝目标/准星方向放出一道十字剑气（两道翾风回雪刀波一横一竖交叉成十字，52 伤，无粒子） */
        private static void fireCrossSword(ServerLevel sl, Player player, LivingEntity target) {
            Vec3 origin = player.getEyePosition().add(player.getLookAngle().normalize().scale(1.5)).add(0, -0.2, 0);
            Vec3 aim;
            if (target != null && target.isAlive() && !target.isRemoved()) {
                aim = target.getBoundingBox().getCenter();
            } else {
                aim = origin.add(player.getLookAngle().normalize().scale(8.0));
            }
            Vec3 dir = aim.subtract(origin).normalize();
            spawnIroiWave(sl, player, origin, dir, 0.0F);  // 横向剑气
            spawnIroiWave(sl, player, origin, dir, 90.0F); // 竖向剑气（与横向交叉成十字）
        }

        /** 放出一道翾风回雪刀波剑气（52 伤，约 10 格，无粒子），roll 决定刀体横/竖 */
        private static void spawnIroiWave(ServerLevel sl, Player player, Vec3 origin, Vec3 dir, float roll) {
            EntityXuanfengWave wave = new EntityXuanfengWave(RegistryEvents.Drive, sl);
            wave.setNoParticles(true); // iroi 十字剑气去掉白色粒子拖尾，纯剑气本体
            sl.addFreshEntity(wave);
            wave.setPos(origin.x, origin.y, origin.z);
            wave.setDamage(52.0);
            wave.setSpeed(0.9F);
            wave.setColor(0xFF55FF);
            wave.setBaseSize(1.6F);
            wave.setOwner(player);
            wave.setRotationRoll(roll);
            wave.setIsCritical(false);
            wave.setKnockBack(KnockBacks.cancel);
            wave.setLifetime(14.0F);
            wave.shoot(dir.x, dir.y, dir.z, wave.getSpeed(), 0.0F);
        }

        /** 「江晚雪霁冬」：手持时玩家获得创造飞行能力（双击空格起飞/落下），放下刀后取消 */
        public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.START) return;
            Player player = event.player;
            if (player.level().isClientSide) return;
            // 诺德卡莱 SA：到点释放 frost_nova（玩家身边），保证两个粒子效果顺序播放
            Integer novaTick = COLUMBINA_NOVA_TICK.get(player.getUUID());
            if (novaTick != null) {
                if (player.tickCount >= novaTick) {
                    COLUMBINA_NOVA_TICK.remove(player.getUUID());
                    if (player.level() instanceof ServerLevel novaSl) {
                        VanillaEffectSpawner.frostNova(novaSl, player.position().add(0, 0.6, 0));
                    }
                }
            }
            // 诺德卡莱随行环：环生效期间随玩家移动生成 10 格粒子环，给玩家抗性提升 5，范围内生物缓慢 10
            UUID ringId = player.getUUID();
            long ringDeadline = COLUMBINA_RING_DEADLINE.getOrDefault(ringId, -1L);
            if (ringDeadline > player.level().getGameTime()) {
                if (player.level() instanceof ServerLevel ringSl) {
                    Vec3 rp = player.position().add(0, 0.2, 0);
                    double rot = player.level().getGameTime() * 0.02;
                    int ringPts = 72; // 一条圆环，更高密度
                    for (int k = 0; k < ringPts; k++) {
                        double th = rot + k * (Math.PI * 2.0 / ringPts);
                        slRingParticle(ringSl, rp.x + Math.cos(th) * 10.0, rp.y, rp.z + Math.sin(th) * 10.0);
                    }
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 4));
                    for (LivingEntity re : ringSl.getEntitiesOfClass(LivingEntity.class,
                            player.getBoundingBox().inflate(10.0),
                            x -> x.isAlive() && !x.isSpectator())) {
                        if (re == player) continue;
                        if (re.position().distanceToSqr(rp) > 100.0) continue;
                        re.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 9));
                    }
                }
            } else {
                COLUMBINA_RING_DEADLINE.remove(ringId);
            }
            ItemStack stack = player.getMainHandItem();
            ISlashBladeState state = stack.getCapability(CapabilitySlashBlade.BLADESTATE, null).orElse(null);
            boolean hasJiangwan = state != null
                    && state.hasSpecialEffect(ModSpecialEffects.JIANGWAN_SNOW_EFFECT.getId())
                    && SpecialEffect.isEffective(ModSpecialEffects.JIANGWAN_SNOW_EFFECT.getId(), player.experienceLevel);
            if (hasJiangwan) {
                if (!player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = true;
                    player.onUpdateAbilities();
                }
            } else if (player.getAbilities().mayfly && !player.isCreative() && !player.isSpectator()) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }

            boolean hasPhantomDance = state != null
                    && state.hasSpecialEffect(ModSpecialEffects.PHANTOM_DANCE_EFFECT.getId());
            if (!hasPhantomDance) {
                PHANTOM_ACTIVE_TICKS.remove(player.getUUID());
            }

            boolean hasButterflyDance = state != null
                    && state.hasSpecialEffect(ModSpecialEffects.BUTTERFLY_DANCE_EFFECT.getId());
            if (!hasButterflyDance) {
                BUTTERFLY_DANCE_ACTIVE.remove(player.getUUID());
            }

            boolean hasSkyward = state != null
                    && state.hasSpecialEffect(ModSpecialEffects.SKYWARD_VISAGE_EFFECT.getId());
            if (!hasSkyward) {
                SKYWARD_ACTIVE.remove(player.getUUID());
            }

            boolean hasFlowRushInHand = state != null
                    && state.hasSpecialEffect(ModSpecialEffects.FLOW_RUSH_EFFECT.getId());
            if (!hasFlowRushInHand) {
                if (player.hasEffect(MobEffects.ABSORPTION)) {
                    player.removeEffect(MobEffects.ABSORPTION);
                }
            }

            boolean hasRouguangInHand = state != null
                    && state.hasSpecialEffect(ModSpecialEffects.ROUGUANG_NINGLU_EFFECT.getId());
            if (!hasRouguangInHand) {
                ROUGUANG_ACTIVE.remove(player.getUUID());
            }

            // 离恨烟·公孙离：不手持带该 SE 的刀时清空离恨层数
            boolean hasGongsunInHand = state != null
                    && state.hasSpecialEffect(ModSpecialEffects.GONGSUN_LI_EFFECT.getId());
            if (!hasGongsunInHand) {
                GONGSUN_LAYERS.remove(player.getUUID());
            }

            // 花开见血血染双瞳：不手持带该 SE 的刀时清空蓄势次数
            boolean hasZankouBloomInHand = state != null
                    && state.hasSpecialEffect(ModSpecialEffects.ZANKOU_SE50.getId());
            if (!hasZankouBloomInHand) {
                ZANKOU_BLOOM_CHARGES.remove(player.getUUID());
                ZANKOU_BLOOM_DEADLINE.remove(player.getUUID());
            }

            // 雨间蝶舞伞犹温，江清晓荷月近人：背包（含主/副手）持有任一把带该 SE 的刀时
            // 给予 +52 生命上限 / +52 护甲，并持续增加攻击力（每 20 tick +0.1，上限 +10）
            boolean hasRain = hasEffectiveRain(player, player.getMainHandItem())
                    || hasEffectiveRain(player, player.getOffhandItem());
            if (!hasRain) {
                for (ItemStack s : player.getInventory().items) {
                    if (!s.isEmpty() && hasEffectiveRain(player, s)) { hasRain = true; break; }
                }
            }
            UUID uuid = player.getUUID();
            if (hasRain) {
                removeRainModifiers(player);
                player.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(
                        new AttributeModifier(RAIN_HEALTH_ID, "rain_butterfly_health", 52.0, AttributeModifier.Operation.ADDITION));
                player.getAttribute(Attributes.ARMOR).addTransientModifier(
                        new AttributeModifier(RAIN_ARMOR_ID, "rain_butterfly_armor", 52.0, AttributeModifier.Operation.ADDITION));
                long ticks = RAIN_ATTACK_TICKS.getOrDefault(uuid, 0L) + 1;
                RAIN_ATTACK_TICKS.put(uuid, ticks);
                if (ticks % 20 == 0) {
                    long amp = RAIN_ATTACK_AMP.getOrDefault(uuid, 0L) + 52L; // 每秒 +52
                    RAIN_ATTACK_AMP.put(uuid, Math.min(amp, 5201314L));       // 上限 5201314
                }
                long atk = RAIN_ATTACK_AMP.getOrDefault(uuid, 0L);
                player.getAttribute(Attributes.ATTACK_DAMAGE).addTransientModifier(
                        new AttributeModifier(RAIN_ATTACK_ID, "rain_butterfly_attack", atk, AttributeModifier.Operation.ADDITION));
            } else {
                removeRainModifiers(player);
                RAIN_ATTACK_TICKS.remove(uuid);
                RAIN_ATTACK_AMP.remove(uuid);
            }
        }

        /** 该刀是否携带「银簪·离魂烟」被动（原雨间蝶舞的效果整体迁移到此 SE）且当前等级生效 */
        private static boolean hasEffectiveRain(Player player, ItemStack stack) {
            ISlashBladeState st = stack.getCapability(CapabilitySlashBlade.BLADESTATE, null).orElse(null);
            if (st == null) return false;
            if (st.hasSpecialEffect(ModSpecialEffects.SILVER_PIN_MOTH_EFFECT.getId()))
                return SpecialEffect.isEffective(ModSpecialEffects.SILVER_PIN_MOTH_EFFECT.getId(), player.experienceLevel);
            return false;
        }

        /** 诺德卡莱随行环的一粒粒子：深蓝偏青尘埃（颜色更深、浓度更高） */
        private static void slRingParticle(ServerLevel sl, double x, double y, double z) {
            sl.sendParticles(new DustParticleOptions(new Vector3f(0.16f, 0.42f, 0.80f), 1.0f), x, y, z, 1, 0, 0, 0, 0.04);
        }

        /** 移除雨间蝶舞施加的生命/护甲/攻击力修饰符 */
        private static void removeRainModifiers(Player player) {
            if (player.getAttribute(Attributes.MAX_HEALTH) != null)
                player.getAttribute(Attributes.MAX_HEALTH).removeModifier(RAIN_HEALTH_ID);
            if (player.getAttribute(Attributes.ARMOR) != null)
                player.getAttribute(Attributes.ARMOR).removeModifier(RAIN_ARMOR_ID);
            if (player.getAttribute(Attributes.ATTACK_DAMAGE) != null)
                player.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(RAIN_ATTACK_ID);
        }

        /** 在玩家位置播放烟花粒子特效（颜色从 from 渐变到 to） */
        private static void spawnFirework(Player player, Vector3f from, Vector3f to) {
            if (!(player.level() instanceof ServerLevel serverLevel)) return;
            Vec3 pos = player.position().add(0.0, 1.0, 0.0);
            serverLevel.sendParticles(
                    new DustColorTransitionOptions(from, to, 1.0f),
                    pos.x, pos.y, pos.z,
                    150, 1.2, 1.2, 1.2, 0.2);
        }

        /** SE 描述文本 → 需求等级（用于把工具条上按 HashSet 乱序显示的 SE 重排为等级升序） */
        private static final Map<String, Integer> SE_DESC_LEVEL = new LinkedHashMap<>();

        /** SlashBlade 用 HashSet 存 specialEffects，工具条显示顺序是哈希序；这里按等级升序重排 SE 行 */
        public static void onItemTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
            if (!(event.getItemStack().getItem() instanceof ItemSlashBlade)) return;
            List<Component> list = event.getToolTip();
            if (list.isEmpty() || list.size() < 2) return;
            if (SE_DESC_LEVEL.isEmpty()) initSeDescLevel();

            List<Integer> idxs = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                if (seLineLevelOf(list.get(i)) >= 0) idxs.add(i);
            }
            if (idxs.size() < 2) return;
            // SlashBlade 在一次调用中连续追加所有 SE 行，故此块为连续区间
            int first = idxs.get(0);
            int last = idxs.get(idxs.size() - 1);
            if (last - first + 1 != idxs.size()) return; // 非连续则不动手，避免误排序
            List<Component> block = new ArrayList<>(list.subList(first, last + 1));
            block.sort(Comparator.comparingInt(c -> Math.max(0, seLineLevelOf(c))));
            for (int i = first; i <= last; i++) {
                list.set(i, block.get(i - first));
            }
        }

        private static void initSeDescLevel() {
            ResourceLocation[] ids = {
                    ModSpecialEffects.ALL_WATERS_EFFECT.getId(),
                    ModSpecialEffects.SINNER_DANCE_EFFECT.getId(),
                    ModSpecialEffects.BUTTERFLY_BLAZE_EFFECT.getId(),
                    ModSpecialEffects.PLUM_BLOSSOM_EFFECT.getId(),
                    ModSpecialEffects.JIANGWAN_SNOW_EFFECT.getId(),
                    ModSpecialEffects.PHANTOM_DANCE_EFFECT.getId(),
                    ModSpecialEffects.XUANFENG_HUIXUE_EFFECT.getId(),
                    ModSpecialEffects.FLOW_RUSH_EFFECT.getId(),
                    ModSpecialEffects.BUTTERFLY_DANCE_EFFECT.getId(),
                    ModSpecialEffects.SKYWARD_VISAGE_EFFECT.getId(),
                    ModSpecialEffects.SEAL_BLOSSOM_EFFECT.getId(),
                    ModSpecialEffects.SEAL_LOTUS_EFFECT.getId(),
                    ModSpecialEffects.SILVER_PIN_MOTH_EFFECT.getId(),
                    ModSpecialEffects.SILVER_PIN_SLAYER_EFFECT.getId(),
                    ModSpecialEffects.RAIN_BUTTERFLY_EFFECT.getId(),
                    ModSpecialEffects.FLUTE_SOUL_EFFECT.getId(),
                    ModSpecialEffects.FLUTE_SHADOW_EFFECT.getId(),
                    ModSpecialEffects.GONGSUN_LI_EFFECT.getId(),
                    ModSpecialEffects.JOY_SORROW_OMEN_EFFECT.getId(),
                    ModSpecialEffects.CELESTIAL_FAREWELL_EFFECT.getId(),
                    ModSpecialEffects.FALLING_MOON_FEATHER_EFFECT.getId(),
                    ModSpecialEffects.NEW_MOON_LAW_EFFECT.getId(),
                    ModSpecialEffects.ROUGUANG_NINGLU_EFFECT.getId(),
                    ModSpecialEffects.HUALAN_YUNYI_EFFECT.getId()
            };
            for (ResourceLocation id : ids) {
                try {
                    int lvl = SpecialEffect.getRequestLevel(id);
                    Component d = SpecialEffect.getDescription(id);
                    SE_DESC_LEVEL.put(d.getString(), lvl);
                } catch (Exception ignored) {
                    // 注册未完成时跳过，稍后重试
                }
            }
        }

        /** 匹配该工具条行是否为 SE 行：是则返回该 SE 的需求等级，否则返回 -1 */
        private static int seLineLevelOf(Component c) {
            String s = c.getString();
            if (s == null || s.isEmpty()) return -1;
            String bestDesc = null;
            for (String desc : SE_DESC_LEVEL.keySet()) {
                if (desc.isEmpty() || !s.startsWith(desc)) continue;
                if (bestDesc == null || desc.length() > bestDesc.length()) bestDesc = desc;
            }
            if (bestDesc == null) return -1;
            return SE_DESC_LEVEL.get(bestDesc);
        }

        /** 特效查看命令：/skydeityslash effect ink_fox | tianxing */
        public static void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
            var root = event.getDispatcher().register(
                    net.minecraft.commands.Commands.literal("skydeityslash")
                            .then(net.minecraft.commands.Commands.literal("effect")
                                    .then(net.minecraft.commands.Commands.literal("ink_fox")
                                            .executes(ctx -> spawnEffect(ctx.getSource(), "ink_fox")))
                                    .then(net.minecraft.commands.Commands.literal("tianxing")
                                            .executes(ctx -> spawnEffect(ctx.getSource(), "tianxing"))))
                            .then(net.minecraft.commands.Commands.literal("preview")
                                    .then(net.minecraft.commands.Commands.literal("naru")
                                            .then(net.minecraft.commands.Commands.argument("stage",
                                                    com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 5))
                                                    .executes(ctx -> spawnPreview(ctx.getSource(), "naru",
                                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "stage")))))
                                    .then(net.minecraft.commands.Commands.literal("enlight")
                                            .then(net.minecraft.commands.Commands.argument("sub",
                                                    com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 8))
                                                    .executes(ctx -> spawnPreview(ctx.getSource(), "enlight",
                                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "sub")))))));
        }

        private static int spawnEffect(net.minecraft.commands.CommandSourceStack src, String kind) {
            if (!(src.getEntity() instanceof ServerPlayer player)) return 0;
            Level level = player.level();
            Vec3 center = player.position().add(player.getLookAngle().x * 6.0, 0, player.getLookAngle().z * 6.0);
            if ("ink_fox".equals(kind)) {
                com.example.skydeityslash.entity.EntityInkFoxField.spawn(level, player, center, 25.0f);
            } else if ("tianxing".equals(kind)) {
                // 天星：地面法阵从小到大 → 天空出现 array_sky → 天星斜落
                com.example.skydeityslash.entity.EntityTianxingFaz.spawnGround(level, center, 5.0f);
            } else if ("sword".equals(kind)) {
                // 全息剑：在身前上方 7 格生成一张剑的全息贴图，竖直下落 7 格后消散
                com.example.skydeityslash.entity.EntitySwordHologram.spawn(level, center);
            } else if ("ghostbutterfly".equals(kind)) {
                // 幽灵蝶：在身前空中撒 5 只（一对左右对称 + 一只缓慢自转 + 两只随机散布）
                com.example.skydeityslash.entity.EntityGhostButterfly.spawnCluster(
                        level, center.add(0, 0.6, 0), player.getLookAngle());
            }
            return 1;
        }

        /** 子特效预览：/skydeityslash preview naru <0-5> | enlight <0-8> —— 逐个触发单个子特效便于测试取舍 */
        private static int spawnPreview(net.minecraft.commands.CommandSourceStack src, String group, int id) {
            if (!(src.getEntity() instanceof ServerPlayer player)) return 0;
            Level level = player.level();
            Vec3 center = player.position().add(player.getLookAngle().x * 6.0, 0, player.getLookAngle().z * 6.0);
            int type = "enlight".equalsIgnoreCase(group) ? 1 : 0;
            com.example.skydeityslash.entity.EntityEffectPreview.spawn(level, center.add(0, .5, 0),
                    player.getLookAngle(), type, id);
            return 1;
        }
    }
}
