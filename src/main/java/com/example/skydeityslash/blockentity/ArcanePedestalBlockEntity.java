package com.example.skydeityslash.blockentity;

import com.example.skydeityslash.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * 恒月基座方块实体：只存 1 个耗材物品。
 */
public class ArcanePedestalBlockEntity extends BlockEntity {

    private ItemStack stack = ItemStack.EMPTY;

    public ArcanePedestalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCANE_PEDESTAL.get(), pos, state);
    }

    public ItemStack getStack() {
        return stack;
    }

    public void setStack(ItemStack stack) {
        this.stack = stack;
        this.setChanged();
    }

    /** 把当前数据包推送给所有在线玩家，让放上去的物品在客户端立即可见。 */
    public void syncToClients() {
        if (this.level == null || this.level.isClientSide) return;
        this.setChanged();
        this.level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        if (this.level.getServer() != null) {
            this.level.getServer().getPlayerList().broadcastAll(ClientboundBlockEntityDataPacket.create(this));
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithFullMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stack = ItemStack.of(tag.getCompound("stack"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("stack", stack.save(new CompoundTag()));
    }
}