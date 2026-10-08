package com.dwarfmod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;

import net.minecraft.world.inventory.Slot;

public class MinerBannerMenu extends AbstractContainerMenu {

    public final MinerBannerBlockEntity blockEntity;
    public final DelegatingContainer dwarfInventory = new DelegatingContainer();
    public int selectedDwarfId = -1;

    public MinerBannerMenu(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, (MinerBannerBlockEntity) null);
    }

    public MinerBannerMenu(int syncId, Inventory playerInventory, MinerBannerBlockEntity blockEntity) {
        super(DwarfMod.MINER_BANNER_MENU, syncId);
        this.blockEntity = blockEntity;

        // 8 slots for Dwarf Inventory Preview (4x2 grid)
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 4; col++) {
                this.addSlot(new Slot(dwarfInventory, col + row * 4, 12 + col * 18, 120 + row * 18) {
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
                this.addSlot(new Slot(playerInventory, j1 + l * 9 + 9, 8 + j1 * 18, 160 + l * 18));
            }
        }
        for (int i1 = 0; i1 < 9; ++i1) {
            this.addSlot(new Slot(playerInventory, i1, 8 + i1 * 18, 218));
        }
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
