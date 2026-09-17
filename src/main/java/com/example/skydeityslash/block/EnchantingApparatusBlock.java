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
                // 纯原版挖掘手感：不要求正确工具（i=30）→ 空手 3 秒、木镐 1.5 秒、更好的镐更快
                .strength(2.0f)
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

    /** 核心外观：两格高（32px）。底座偏圆（切角八边形）+ 四根小柱子 + 悬浮月晶圆球。
     *  0-2   第一层切角八边形，x/z 1..15，四角切 3 px
     *  2-4   第二层切角八边形，x/z 2..14，四角切 3 px
     *  4-5   符文盆，x/z 4..12，四角切 2 px（比原来小一圈）
     *  4-10  四根 2x2 px 小柱子，立在外圈四边中点
     *  10-21 空档（物品悬浮区，y≈15.5px）
     *  21-31 月晶圆球（纯装饰，悬浮在外，不参与碰撞/选取）
     */
    private static final VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.or(
            // 第一层 0-2
            box(1, 0, 4, 15, 2, 12),
            box(4, 0, 1, 12, 2, 4),
            box(4, 0, 12, 12, 2, 15),
            // 第二层 2-4
            box(2, 2, 5, 14, 4, 11),
            box(5, 2, 2, 11, 4, 5),
            box(5, 2, 11, 11, 4, 14),
            // 符文盆 4-5
            box(4, 4, 6, 12, 5, 10),
            box(6, 4, 4, 10, 5, 6),
            box(6, 4, 10, 10, 5, 12),
            // 四根 2x2 px 小柱子 4-10
            box(7, 4, 2, 9, 10, 4),
            box(7, 4, 12, 9, 10, 14),
            box(2, 4, 7, 4, 10, 9),
            box(12, 4, 7, 14, 10, 9));

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