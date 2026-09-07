package com.example.skydeityslash.block;

import com.example.skydeityslash.blockentity.ArcanePedestalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 奥术基座：可放进 1 个耗材物品，多方块合成的耗材存放点。
 */
public class ArcanePedestalBlock extends Block implements EntityBlock {

    /** 基座形状（4 段台阶：底座收腰 0-1 / 1-2 / 细柱 2-11 / 小顶 11-12），与模型逐段一致，仅用于碰撞/选取 */
    private static final VoxelShape SHAPE = Shapes.or(
            box(4, 0, 4, 12, 1, 12),
            box(5, 1, 5, 11, 2, 11),
            box(6, 2, 6, 10, 11, 10),
            box(5, 11, 5, 11, 12, 11));

    public ArcanePedestalBlock() {
        super(Properties.of()
                .instabreak()
                .noOcclusion());
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ArcanePedestalBlockEntity tile)) return InteractionResult.PASS;

        ItemStack held = player.getItemInHand(hand);
        if (tile.getStack().isEmpty() && !held.isEmpty()) {
            // 放入 1 个（手持多个则只取 1 个）
            ItemStack put = held.split(1);
            tile.setStack(put);
        } else if (!tile.getStack().isEmpty() && held.isEmpty()) {
            // 空手取出
            player.getInventory().placeItemBackInInventory(tile.getStack());
            tile.setStack(ItemStack.EMPTY);
        } else if (!tile.getStack().isEmpty() && !held.isEmpty()) {
            // 已有物品时再放=替换（旧物品弹出）
            level.addFreshEntity(new ItemEntity(level,
                    player.getX(), player.getY() + 0.3, player.getZ(), tile.getStack()));
            ItemStack put = held.split(1);
            tile.setStack(put);
        }
        // 把最新数据推送给客户端，放上去的物品立即可见
        tile.syncToClients();
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArcanePedestalBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ArcanePedestalBlockEntity tile && !tile.getStack().isEmpty()) {
                level.addFreshEntity(new ItemEntity(level,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, tile.getStack()));
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}