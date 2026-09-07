package com.example.skydeityslash.dimension;

import com.example.skydeityslash.SkydeitySlash;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * mandarava 专属虚空维度：
 *  <ul>
 *   <li>世界启动时把随模组打包的存档地图区域文件释放到该维度文件夹</li>
 *   <li>该维度不生成任何生物（natural:false + doMobSpawning:false + 拦截 Mob，三重保护）</li>
 *   <li>该维度死亡不掉落（keepInventory:true）</li>
 *   <li>该维度关闭随机刻（randomTickSpeed = 0，植物等不再随机更新）</li>
 *   <li>/skydeityslash enter mandarava 从固定坐标进入；/skydeityslash leave mandarava 返回进入前的坐标</li>
 *  </ul>
 */
public final class MandaravaDimension {

    public static final ResourceKey<Level> DIMENSION =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(SkydeitySlash.MODID, "mandarava"));

    /** 进入维度的固定落点坐标 */
    private static final double ENTER_X = 243.5;
    private static final double ENTER_Y = -10;
    private static final double ENTER_Z = 112.5;

    /** 从原存档唯一打包进来的 11 个区域文件（只保留地图数据） */
    private static final List<String> REGIONS = Arrays.asList(
            "r.-1.-1.mca", "r.-1.-2.mca", "r.-1.0.mca", "r.-1.1.mca",
            "r.0.-1.mca",  "r.0.-2.mca",  "r.0.0.mca",  "r.0.1.mca",
            "r.1.-1.mca",  "r.1.0.mca",   "r.1.1.mca");

    /** 记录每个玩家进入前的原世界坐标，用于 leave 时传送回去 */
    private static final Map<UUID, ReturnPoint> RETURN_POINTS = new ConcurrentHashMap<>();

    private MandaravaDimension() {
    }

    /** 世界存档启动、创建维度层级之前，释放打包的地图区域文件（已存在则跳过，保留玩家改动） */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        try {
            Path root = event.getServer().getWorldPath(LevelResource.ROOT);
            Path regionDir = root.resolve("dimensions")
                    .resolve(SkydeitySlash.MODID)
                    .resolve("mandarava")
                    .resolve("region");
            Files.createDirectories(regionDir);
            for (String name : REGIONS) {
                releaseRegionIfMissing(regionDir, name);
            }
        } catch (IOException e) {
            SkydeitySlash.LOGGER.error("Failed to release mandarava region files", e);
        }
    }

    private static void releaseRegionIfMissing(Path regionDir, String name) throws IOException {
        Path target = regionDir.resolve(name);
        if (Files.exists(target)) {
            return;
        }
        try (InputStream in = MandaravaDimension.class.getResourceAsStream("/mandarava/region/" + name)) {
            if (in == null) {
                SkydeitySlash.LOGGER.warn("mandarava packaged region missing: {}", name);
                return;
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 阻止该维度生成任何生物（玩家、掉落物、经验球不受影响） */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel().dimension() == DIMENSION && entity instanceof Mob) {
            event.setCanceled(true);
        }
    }

    /** 该维度关闭随机刻、死亡不掉落、禁止自然生成怪物（三道世界规则） */
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (serverLevel.dimension() == DIMENSION) {
            MinecraftServer server = serverLevel.getServer();
            if (server != null) {
                serverLevel.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
                serverLevel.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
                serverLevel.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("skydeityslash")
                .then(Commands.literal("enter")
                        .then(Commands.literal("mandarava")
                                .executes(ctx -> enterMandarava(ctx.getSource()))))
                .then(Commands.literal("leave")
                        .then(Commands.literal("mandarava")
                                .executes(ctx -> leaveMandarava(ctx.getSource())))));
    }

    /** /skydeityslash enter mandarava：记录原坐标并传送到固定落点 */
    private static int enterMandarava(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (teleportToMandarava(player)) {
            source.sendSuccess(() -> Component.literal("进入彼岸"), true);
            return 1;
        }
        source.sendFailure(Component.literal("彼岸维度未加载"));
        return 0;
    }

    /** /skydeityslash leave mandarava：传回进入前的原世界坐标 */
    private static int leaveMandarava(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        teleportBack(player);
        source.sendSuccess(() -> Component.literal("返回原世界"), true);
        return 1;
    }

    /** 记录玩家当前坐标并传送到 mandarava 固定落点；维度未就绪返回 false */
    public static boolean teleportToMandarava(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        ServerLevel mandarava = server.getLevel(DIMENSION);
        if (mandarava == null) {
            return false;
        }
        ServerLevel now = (ServerLevel) player.level();
        RETURN_POINTS.put(player.getUUID(),
                new ReturnPoint(now.dimension(), player.getX(), player.getY(), player.getZ(),
                        player.getYRot(), player.getXRot()));
        player.teleportTo(mandarava, ENTER_X, ENTER_Y, ENTER_Z, player.getYRot(), player.getXRot());
        return true;
    }

    /** 若在 mandarava 维度则传回进入前的原世界坐标 */
    public static void teleportBack(ServerPlayer player) {
        ReturnPoint rp = RETURN_POINTS.remove(player.getUUID());
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel target = rp != null ? server.getLevel(rp.levelKey) : null;
        if (target == null) {
            target = server.overworld();
        }
        double x = rp != null ? rp.x : player.getX();
        double y = rp != null ? rp.y : player.getY();
        double z = rp != null ? rp.z : player.getZ();
        float yaw = rp != null ? rp.yaw : player.getYRot();
        float pitch = rp != null ? rp.pitch : player.getXRot();
        player.teleportTo(target, x, y, z, yaw, pitch);
    }

    /** 进入前的原世界坐标快照 */
    private static final class ReturnPoint {
        final ResourceKey<Level> levelKey;
        final double x;
        final double y;
        final double z;
        final float yaw;
        final float pitch;

        ReturnPoint(ResourceKey<Level> levelKey, double x, double y, double z, float yaw, float pitch) {
            this.levelKey = levelKey;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}