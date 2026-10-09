package com.dwarfmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class MinerBannerBlockEntity extends BlockEntity implements MenuProvider {

    private int miningMode = 1; // 1, 2, 3 (1x1, 2x2, 3x3)
    private int miningDepth = 10;
    private boolean isMining = false;
    private int cursorY;
    private Direction tunnelDir = Direction.NORTH;

    /** Synced to the menu: [0]=mode, [1]=depth, [2]=mining (0|1), [3]=dir (2D data value). */
    private final ContainerData containerData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> miningMode;
                case 1 -> miningDepth;
                case 2 -> isMining ? 1 : 0;
                case 3 -> tunnelDir.get2DDataValue();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> setMiningMode(value);
                case 1 -> setMiningDepth(value);
                case 2 -> setMining(value != 0);
                case 3 -> setTunnelDir(Direction.from2DDataValue(value));
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    /** Cells being broken right now / cells a dwarf could not reach. Runtime only, not saved. */
    private final java.util.Set<Long> claimed = new java.util.HashSet<>();
    private final java.util.Set<Long> unreachable = new java.util.HashSet<>();

    public boolean claim(BlockPos pos) { return claimed.add(pos.asLong()); }
    public void release(BlockPos pos) { claimed.remove(pos.asLong()); }
    public boolean hasClaims() { return !claimed.isEmpty(); }
    public boolean isClaimed(BlockPos pos) { return claimed.contains(pos.asLong()); }
    public void markUnreachable(BlockPos pos) { unreachable.add(pos.asLong()); }
    public boolean isUnreachable(BlockPos pos) { return unreachable.contains(pos.asLong()); }

    @Override
    public void setRemoved() {
        super.setRemoved();
        claimed.clear();
        unreachable.clear();
    }

    public ContainerData getContainerData() { return containerData; }

    public Direction getTunnelDir() { return tunnelDir; }
    public void setTunnelDir(Direction dir) {
        if (dir.getAxis().isHorizontal()) { this.tunnelDir = dir; setChanged(); }
    }

    public MinerBannerBlockEntity(BlockPos pos, BlockState state) {
        super(DwarfMod.MINER_BANNER_BE, pos, state);
        this.cursorY = pos.getY() - 1;
    }

    public void tick() {
        // Here we can handle specific logic if we want the banner to do something over time.
    }

    public int getMiningMode() { return miningMode; }
    public void setMiningMode(int miningMode) { this.miningMode = Math.max(1, Math.min(3, miningMode)); setChanged(); }

    public int getMiningDepth() { return miningDepth; }
    public void setMiningDepth(int miningDepth) { this.miningDepth = Math.max(5, Math.min(64, miningDepth)); setChanged(); }

    public boolean isMining() { return isMining; }
    public void setMining(boolean mining) {
        if (mining && !this.isMining) { unreachable.clear(); }
        isMining = mining;
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);
        tag.putInt("MiningMode", this.miningMode);
        tag.putInt("MiningDepth", this.miningDepth);
        tag.putBoolean("IsMining", this.isMining);
        tag.putInt("CursorY", this.cursorY);
        tag.putInt("TunnelDir", this.tunnelDir.get2DDataValue());
    }

    @Override
    protected void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);
        this.miningMode = tag.getIntOr("MiningMode", 1);
        this.miningDepth = tag.getIntOr("MiningDepth", 10);
        this.isMining = tag.getBooleanOr("IsMining", false);
        this.cursorY = tag.getIntOr("CursorY", this.worldPosition.getY() - 1);
        this.tunnelDir = Direction.from2DDataValue(tag.getIntOr("TunnelDir", 2));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.dwarfmod.miner_banner");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new MinerBannerMenu(syncId, playerInventory, this);
    }
}
