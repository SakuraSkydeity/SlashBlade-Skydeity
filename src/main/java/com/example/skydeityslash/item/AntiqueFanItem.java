package com.example.skydeityslash.item;

import com.example.skydeityslash.dimension.MandaravaDimension;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 新月之扇：右键进入/返回彼岸（mandarava）维度。不需要作弊即可使用。
 */
public class AntiqueFanItem extends Item {

    public AntiqueFanItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.level().dimension() == MandaravaDimension.DIMENSION) {
                MandaravaDimension.teleportBack(serverPlayer);
            } else {
                MandaravaDimension.teleportToMandarava(serverPlayer);
            }
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("\u00a77借助新月的力量右键进入彼岸之地"));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}