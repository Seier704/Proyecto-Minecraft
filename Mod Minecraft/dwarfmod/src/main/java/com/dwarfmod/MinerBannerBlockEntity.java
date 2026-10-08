package com.dwarfmod;

import net.minecraft.core.BlockPos;
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

    private int miningMode = 1; // 1, 2, 4 (1x1, 2x2, 4x4)
    private int miningDepth = 10;
    private boolean isMining = false;
    private int cursorY;

    public MinerBannerBlockEntity(BlockPos pos, BlockState state) {
        super(DwarfMod.MINER_BANNER_BE, pos, state);
        this.cursorY = pos.getY() - 1;
    }

    public void tick() {
        // Here we can handle specific logic if we want the banner to do something over time.
    }

    public int getMiningMode() { return miningMode; }
    public void setMiningMode(int miningMode) { this.miningMode = miningMode; setChanged(); }

    public int getMiningDepth() { return miningDepth; }
    public void setMiningDepth(int miningDepth) { this.miningDepth = miningDepth; setChanged(); }

    public boolean isMining() { return isMining; }
    public void setMining(boolean mining) { isMining = mining; setChanged(); }

    @Override
    protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);
        tag.putInt("MiningMode", this.miningMode);
        tag.putInt("MiningDepth", this.miningDepth);
        tag.putBoolean("IsMining", this.isMining);
        tag.putInt("CursorY", this.cursorY);
    }

    @Override
    protected void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);
        this.miningMode = tag.getIntOr("MiningMode", 1);
        this.miningDepth = tag.getIntOr("MiningDepth", 10);
        this.isMining = tag.getBooleanOr("IsMining", false);
        this.cursorY = tag.getIntOr("CursorY", this.worldPosition.getY() - 1);
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
