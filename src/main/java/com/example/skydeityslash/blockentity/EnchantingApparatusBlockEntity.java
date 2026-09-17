package com.example.skydeityslash.blockentity;

import com.example.skydeityslash.recipe.EnchantingApparatusRecipe;
import com.example.skydeityslash.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 附魔装置方块实体：承担多方块合成逻辑。
 * 流程（与 ars_nouveau 一致）：
 *   1. 校准配方：核心物 + 基座耗材 无序匹配
 *   2. 施法阶段 210 tick（10.5 秒）
 *   3. 完成：清空基座耗材 → 产物进入装置 → 完成音效
 */
public class EnchantingApparatusBlockEntity extends BlockEntity implements Container {

    public static final int CRAFT_LENGTH = 60;

    private ItemStack stack = ItemStack.EMPTY;
    private boolean crafting = false;
    private int counter = 0;

    public EnchantingApparatusBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENCHANTING_APPARATUS.get(), pos, state);
    }

    // ---------- 多方块扫描 ----------

    /** 扫描以装置为中心的 ±offset 立方体内所有奥术基座。 */
    public List<BlockPos> pedestalList(int offset) {
        List<BlockPos> out = new ArrayList<>();
        if (this.level == null) return out;
        BlockPos center = getBlockPos();
        for (BlockPos b : BlockPos.betweenClosed(
                center.offset(offset, -offset, offset),
                center.offset(-offset, offset, -offset))) {
            if (level.getBlockEntity(b) instanceof ArcanePedestalBlockEntity) {
                out.add(new BlockPos(b));
            }
        }
        return out;
    }

    /** 收集基座上的所有耗材。 */
    public List<ItemStack> getPedestalItems() {
        List<ItemStack> items = new ArrayList<>();
        if (this.level == null) return items;
        for (BlockPos p : pedestalList(3)) {
            if (level.getBlockEntity(p) instanceof ArcanePedestalBlockEntity tile
                    && !tile.getStack().isEmpty()) {
                items.add(tile.getStack());
            }
        }
        return items;
    }

    /** 在配方表中找一条匹配的附魔装置配方。 */
    @Nullable
    public EnchantingApparatusRecipe getRecipe(ItemStack catalyst, @Nullable Player player) {
        if (this.level == null) return null;
        for (EnchantingApparatusRecipe r
                : this.level.getRecipeManager().getAllRecipesFor(EnchantingApparatusRecipe.TYPE)) {
            if (r.isMatch(catalyst, getPedestalItems())) return r;
        }
        return null;
    }

    /** 尝试开始合成。 */
    public boolean attemptCraft(ItemStack catalyst, @Nullable Player player) {
        if (crafting) return false;
        EnchantingApparatusRecipe recipe = getRecipe(catalyst, player);
        if (recipe == null) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable("skydeityslash.enchanting_apparatus.no_recipe"));
            }
            return false;
        }
        this.crafting = true;
        this.setChanged();
        if (player != null && level != null) {
            level.playSound(null, worldPosition, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        return true;
    }

    /** 每 tick 分发：服务端走计时，客户端出粒子。 */
    public static void tick(Level level, BlockPos pos, BlockState state,
                            EnchantingApparatusBlockEntity be) {
        if (level.isClientSide) {
            be.tickClient();
        } else {
            be.tickServer();
        }
    }

    /** 每 tick：服务端走计时，客户端出粒子。 */
    public void tickServer() {
        if (this.level == null || this.level.isClientSide) return;

        if (crafting) {
            // 配方失效（耗材被移走）则中断
            if (getRecipe(stack, null) == null) {
                crafting = false;
                counter = 0;
                setChanged();
                return;
            }
            counter++;
        }

        if (counter >= CRAFT_LENGTH) {
            counter = 0;
            if (crafting) {
                EnchantingApparatusRecipe recipe = getRecipe(stack, null);
                if (recipe != null) {
                    stack = recipe.getResult(getPedestalItems(), stack, this.level.registryAccess());
                    consumePedestalItems();
                    this.crafting = false;
                    this.setChanged();
                    if (level != null) {
                        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
                        this.syncToClients();
                    }
                } else {
                    crafting = false;
                }
            }
        }
    }

    /** 客户端：施法期间的视觉粒子（仅用原版粒子）。 */
    public void tickClient() {
        if (this.level == null || !this.level.isClientSide) return;
        if (!crafting) return;
        // 圆球顶上的星点（方块已加高到两格，圆球顶约 1.94）
        level.addParticle(ParticleTypes.END_ROD,
                worldPosition.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5,
                worldPosition.getY() + 2.05,
                worldPosition.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5,
                0, 0.02, 0);
        // 基座连线光点
        for (BlockPos p : pedestalList(3)) {
            level.addParticle(ParticleTypes.ENCHANT,
                    worldPosition.getX() + 0.5,
                    worldPosition.getY() + 1.0,
                    worldPosition.getZ() + 0.5,
                    (p.getX() - worldPosition.getX()),
                    (p.getY() - worldPosition.getY()) + 1.0,
                    (p.getZ() - worldPosition.getZ()));
        }
    }

    /** 清空基座耗材（消耗 1 个，桶等保留剩余物），并同步客户端。 */
    private void consumePedestalItems() {
        if (this.level == null) return;
        for (BlockPos p : pedestalList(3)) {
            if (level.getBlockEntity(p) instanceof ArcanePedestalBlockEntity tile) {
                ItemStack s = tile.getStack();
                if (!s.isEmpty()) {
                    ItemStack remnant = s.getCraftingRemainingItem();
                    tile.setStack(remnant == null ? ItemStack.EMPTY : remnant);
                    tile.setChanged();
                }
                tile.syncToClients();
            }
        }
    }

    public boolean isCrafting() {
        return crafting;
    }

    public ItemStack getStack() {
        return stack;
    }

    public void setStack(ItemStack stack) {
        this.stack = stack;
        this.setChanged();
    }

    // ---------- Container 接口（让装置自身可当容器） ----------

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return stack.isEmpty();
    }

    @Override
    public ItemStack getItem(int index) {
        return index == 0 ? stack : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        if (index == 0 && !stack.isEmpty() && !crafting) {
            if (count >= stack.getCount()) {
                ItemStack out = stack;
                stack = ItemStack.EMPTY;
                return out;
            }
            ItemStack out = stack.split(count);
            this.setChanged();
            return out;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        if (index == 0) {
            ItemStack out = stack;
            stack = ItemStack.EMPTY;
            return out;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int index, ItemStack itemStack) {
        if (index == 0) {
            this.stack = itemStack;
            this.setChanged();
        }
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack itemStack) {
        return index == 0 && !crafting;
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.level == null) return false;
        return this.level.getBlockEntity(this.worldPosition) == this
                && player.distanceToSqr(this.worldPosition.getX() + 0.5,
                this.worldPosition.getY() + 0.5,
                this.worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        this.stack = ItemStack.EMPTY;
        this.setChanged();
    }

    // ---------- 数据持久化 ----------

    /** 把最新数据推送给所有在线玩家，让装置内物品/进度在客户端立即可见。 */
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
        this.stack = ItemStack.of(tag.getCompound("stack"));
        this.crafting = tag.getBoolean("crafting");
        this.counter = tag.getInt("counter");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("stack", stack.save(new CompoundTag()));
        tag.putBoolean("crafting", crafting);
        tag.putInt("counter", counter);
    }
}