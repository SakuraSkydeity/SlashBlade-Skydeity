package com.example.skydeityslash.block;

import com.example.skydeityslash.blockentity.EnchantingApparatusBlockEntity;
import com.example.skydeityslash.registry.ModBlockEntities;
import com.example.skydeityslash.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * 新月台 (Enchanting Apparatus): 多方块合成的中央方块。
 * 使用方式：
 *   1. ±3 格范围内放置若干 {@link ArcanePedestalBlock 奥术基座} 并放入耗材
 *   2. 手持核心物(reagent)右键装置 → 匹配配方 → 10.5秒后出产物
 * 产物放在装置内，右键取出产物。
 */
public class EnchantingApparatusBlock extends Block implements EntityBlock {

    public EnchantingApparatusBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(3.0f)
                .noOcclusion());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnchantingApparatusBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof EnchantingApparatusBlockEntity tile)) {
            return InteractionResult.SUCCESS;
        }
        if (tile.isCrafting()) {
            return InteractionResult.SUCCESS;
        }

        ItemStack held = player.getItemInHand(hand);
        if (tile.getStack().isEmpty() && held.isEmpty()) {
            // 空着，没东西放
            return InteractionResult.SUCCESS;
        }

        if (tile.getStack().isEmpty()) {
            // 第一次放核心物 → 尝试开始合成
            if (tile.attemptCraft(held, player)) {
                tile.setStack(held.split(1));
            }
        } else {
            // 已有产物 → 直接放入玩家背包，满了才掉落在地上
            ItemStack product = tile.getStack();
            tile.setStack(ItemStack.EMPTY);
            if (!player.getInventory().add(product)) {
                level.addFreshEntity(new ItemEntity(level,
                        player.getX(), player.getY(), player.getZ(), product));
            }
            // 手中若有核心物且匹配 → 直接继续开始下一炉
            if (!held.isEmpty() && tile.attemptCraft(held, player)) {
                tile.setStack(held.split(1));
            }
        }
        // 把最新数据推送给客户端，装置内物品立即可见
        tile.syncToClients();
        return InteractionResult.SUCCESS;
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable net.minecraft.world.level.block.entity.BlockEntity blockEntity,
                              net.minecraft.world.item.ItemStack stack) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof EnchantingApparatusBlockEntity tile && !tile.getStack().isEmpty()) {
            level.addFreshEntity(new ItemEntity(level,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, tile.getStack()));
        }
        super.playerDestroy(level, player, pos, state, blockEntity, stack);
    }

    /** 核心外观：更大的底盘 + 四角立柱 + 悬浮在空中的月晶核心（底座与核心之间留空，形成悬浮感） */
    private static final VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.or(
            box(1, 0, 1, 15, 3, 15),
            box(2, 3, 2, 14, 4, 14),
            box(3, 4, 3, 13, 5, 13),
            box(2, 5, 2, 4, 7, 4),
            box(12, 5, 2, 14, 7, 4),
            box(2, 5, 12, 4, 7, 14),
            box(12, 5, 12, 14, 7, 14),
            box(6, 9, 6, 10, 10, 10),
            box(5, 10, 5, 11, 14, 11),
            box(6, 14, 6, 10, 15, 10));

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (type == ModBlockEntities.ENCHANTING_APPARATUS.get()) {
            return (net.minecraft.world.level.block.entity.BlockEntityTicker<T>)
                    (net.minecraft.world.level.block.entity.BlockEntityTicker<EnchantingApparatusBlockEntity>)
                            EnchantingApparatusBlockEntity::tick;
        }
        return null;
    }
}