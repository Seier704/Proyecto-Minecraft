package com.dwarfmod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class MinerBannerMenu extends AbstractContainerMenu {

    /** Server side only; the client menu has no block entity. */
    public final MinerBannerBlockEntity blockEntity;
    /** [0]=mode (1..3), [1]=depth (5..64), [2]=mining (0|1), [3]=direction. Synced to the client. */
    public final ContainerData data;
    public final DelegatingContainer dwarfInventory = new DelegatingContainer();
    public int selectedDwarfId = -1;

    public MinerBannerMenu(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, null, new SimpleContainerData(4));
    }

    public MinerBannerMenu(int syncId, Inventory playerInventory, MinerBannerBlockEntity blockEntity) {
        this(syncId, playerInventory, blockEntity, blockEntity.getContainerData());
    }

    private MinerBannerMenu(int syncId, Inventory playerInventory, MinerBannerBlockEntity blockEntity, ContainerData data) {
        super(DwarfMod.MINER_BANNER_MENU, syncId);
        this.blockEntity = blockEntity;
        this.data = data;
        this.addDataSlots(data);

        // 8 slots for Dwarf Inventory Preview (one row)
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 4; col++) {
                this.addSlot(new Slot(dwarfInventory, col + row * 4, 12 + (col + row * 4) * 18, 135) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false; // read-only preview
                    }
                    @Override
                    public boolean mayPickup(Player playerIn) {
                        return false; // read-only preview
                    }
                });
            }
        }

        // Player Inventory
        for (int l = 0; l < 3; ++l) {
            for (int j1 = 0; j1 < 9; ++j1) {
                this.addSlot(new Slot(playerInventory, j1 + l * 9 + 9, 8 + j1 * 18, 158 + l * 18));
            }
        }
        for (int i1 = 0; i1 < 9; ++i1) {
            this.addSlot(new Slot(playerInventory, i1, 8 + i1 * 18, 216));
        }
    }

    public int getMode() { return this.data.get(0); }
    public int getDepth() { return this.data.get(1); }
    public boolean isMining() { return this.data.get(2) != 0; }

    /** Server side: position of the banner this menu is open on, null on the client. */
    public BlockPos getBannerPos() {
        return this.blockEntity == null ? null : this.blockEntity.getBlockPos();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.blockEntity == null) return true;
        return player.distanceToSqr(this.blockEntity.getBlockPos().getX() + 0.5,
                this.blockEntity.getBlockPos().getY() + 0.5,
                this.blockEntity.getBlockPos().getZ() + 0.5) <= 64.0;
    }
}
