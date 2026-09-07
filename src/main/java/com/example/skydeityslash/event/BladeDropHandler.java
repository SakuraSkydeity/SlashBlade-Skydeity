package com.example.skydeityslash.event;

import com.example.skydeityslash.SkydeitySlash;
import com.example.skydeityslash.entity.ColumbinaNpcEntity;
import com.example.skydeityslash.entity.FurinaNpcEntity;
import com.example.skydeityslash.entity.HutaoNpcEntity;
import com.example.skydeityslash.entity.IroiNpcEntity;
import com.example.skydeityslash.entity.LinneaNpcEntity;
import com.example.skydeityslash.entity.OdetteNpcEntity;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/**
 * 丢刀变人形：furina 刀被丢下时，把掉落的物品实体替换为芙宁娜人形 NPC。
 * 右键（主人）NPC 回收原刀。NPC 贴图固定为 furina_npc.png，不占用玩家自身皮肤。
 * 通过 NBT 持久化主人与刀，无静态 Map，避免内存泄漏。
 * 由 SkydeitySlash 主类显式注册到 Forge 事件总线。
 */
public class BladeDropHandler {
    // SlashBladeDefinition.getTranslationKey() 返回 Util.makeDescriptionId("item", name)，
    // 即 "item.skydeityslash.slash_furina" / "item.skydeityslash.hutao"，而非 "skydeityslash:xxx"
    private static final String FURINA_KEY = "item.skydeityslash.slash_furina";
    private static final String HUTAO_KEY = "item.skydeityslash.hutao";
    private static final String ODETTE_KEY = "item.skydeityslash.odette";
    private static final String LINNEA_KEY = "item.skydeityslash.linnea";
    private static final String COLUMBINA_KEY = "item.skydeityslash.columbina";
    private static final String IROI_KEY = "item.skydeityslash.iroi";

    /** 玩家按 Q 丢刀时，Player.drop 不会调用 setThrower，导致 ItemEntity.getOwner() 为 null。
     *  这里在物品实体加入世界前把主人写进 thrower，保证后续生成 NPC 时能确定归属。 */
    @SubscribeEvent
    public static void onItemToss(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (isNpcBlade(event.getEntity().getItem())) {
            event.getEntity().setThrower(event.getPlayer().getUUID());
        }
    }

    @SubscribeEvent
    public static void onItemEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof ItemEntity item)) return;
        ItemStack stack = item.getItem();
        if (!isNpcBlade(stack)) return;

        UUID ownerId = null;
        if (item.getOwner() instanceof ServerPlayer sp) {
            ownerId = sp.getUUID();
        } else {
            Player nearest = item.level().getNearestPlayer(item, 8.0);
            if (nearest instanceof ServerPlayer sp2) ownerId = sp2.getUUID();
        }
        if (ownerId == null) return;

        event.setCanceled(true);
        item.discard();
        String key = bladeKey(stack);
        if (FURINA_KEY.equals(key)) {
            FurinaNpcEntity.spawn(item.level(), ownerId, stack, item.position());
        } else if (HUTAO_KEY.equals(key)) {
            HutaoNpcEntity.spawn(item.level(), ownerId, stack, item.position());
        } else if (ODETTE_KEY.equals(key)) {
            OdetteNpcEntity.spawn(item.level(), ownerId, stack, item.position());
        } else if (COLUMBINA_KEY.equals(key)) {
            ColumbinaNpcEntity.spawn(item.level(), ownerId, stack, item.position());
        } else if (IROI_KEY.equals(key)) {
            IroiNpcEntity.spawn(item.level(), ownerId, stack, item.position());
        } else {
            LinneaNpcEntity.spawn(item.level(), ownerId, stack, item.position());
        }
    }

    @SubscribeEvent
    public static void onInteractNpc(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getTarget() instanceof FurinaNpcEntity npc)) return;
        Player player = event.getEntity();
        if (!npc.isOwner(player)) return;

        event.setCanceled(true);
        double x = npc.getX(), y = npc.getY() + 1.0, z = npc.getZ();
        ItemStack blade = npc.getBladeStack();
        if (!blade.isEmpty()) {
            if (!player.addItem(blade)) {
                player.drop(blade, false);
            }
        }
        npc.discard();
        player.level().playSound(null, x, y, z,
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8F, 1.2F);

        // 回收特效：爱心粒子（少一点）+ 附魔粒子（更少）
        if (player.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HEART, x, y, z, 3, 0.4, 0.5, 0.4, 0.0);
            server.sendParticles(ParticleTypes.ENCHANT, x, y, z, 15, 0.5, 0.8, 0.5, 0.35);
        }
    }

    private static boolean isNpcBlade(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemSlashBlade)) return false;
        ISlashBladeState state = stack.getCapability(CapabilitySlashBlade.BLADESTATE).orElse(null);
        if (state == null) return false;
        String key = state.getTranslationKey();
        return FURINA_KEY.equals(key) || HUTAO_KEY.equals(key)
                || ODETTE_KEY.equals(key) || LINNEA_KEY.equals(key)
                || COLUMBINA_KEY.equals(key) || IROI_KEY.equals(key);
    }

    private static String bladeKey(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemSlashBlade)) return "";
        ISlashBladeState state = stack.getCapability(CapabilitySlashBlade.BLADESTATE).orElse(null);
        return state != null ? state.getTranslationKey() : "";
    }
}
