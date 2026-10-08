package com.example.skydeityslash.event;

import com.example.skydeityslash.item.BladeCharge;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/**
 * 拔刀剑充能的**服务端**逻辑：右键充能 + 三种清零时机。
 *
 * <p>清零规则（由易到难，三条都实现）：
 * <ol>
 *   <li><b>丢下</b>：{@link ItemTossEvent} —— 立刻清。furina 这类刀随后会被 {@code BladeDropHandler}
 *       变成人形 NPC，包里带的就是已经清过的那把。</li>
 *   <li><b>放进容器</b>：{@link PlayerContainerEvent.Close} —— 关容器时扫一遍**非玩家背包**的槽位，
 *       命中就清。（shift 丢进箱子也算，因为只看刀最后在不在容器里。）</li>
 *   <li><b>交给别人 / 离开过背包</b>：认主机制 —— 第一次右键时如果 {@code owner} 不是自己就清空重来，
 *       所以刀无论怎么流出去，回到谁手里都从 0 开始。</li>
 * </ol>
 * 数值同步不需要额外发包：改的是物品自己的 NBT，{@code ServerPlayer.tick()} 每 tick 的
 * {@code containerMenu.broadcastChanges()} 比对 {@code ItemStack.matches}（含 NBT）后会自动下发。
 */
public final class BladeChargeHandler {
    private BladeChargeHandler() {}

    /** 右键（主手）：充能 +1；换过主人的刀先清零再重新开始。 */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (BladeCharge.configOf(stack) == null) return;

        UUID owner = BladeCharge.owner(stack);
        if (owner == null || !owner.equals(player.getUUID())) {
            BladeCharge.clear(stack);
            BladeCharge.setOwner(stack, player.getUUID());
        }
        if (!BladeCharge.isFull(stack)) {
            BladeCharge.add(stack, 1);
        }
    }

    /** 丢下即清零。 */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        ItemStack stack = event.getEntity().getItem();
        if (BladeCharge.configOf(stack) != null) {
            BladeCharge.clear(stack);
        }
    }

    /** 关容器时，把留在容器（非玩家背包）里的刀清零。 */
    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        for (Slot slot : event.getContainer().slots) {
            if (slot.container == player.getInventory()) continue;
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && BladeCharge.configOf(stack) != null) {
                BladeCharge.clear(stack);
            }
        }
    }
}
