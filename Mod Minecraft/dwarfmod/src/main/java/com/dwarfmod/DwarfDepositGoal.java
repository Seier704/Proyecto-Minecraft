package com.dwarfmod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.EnumSet;

public class DwarfDepositGoal extends Goal {

    private final DwarfEntity dwarf;
    private final double speedModifier;
    private BlockPos targetChest;

    public DwarfDepositGoal(DwarfEntity dwarf, double speedModifier) {
        this.dwarf = dwarf;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.dwarf.getBannerPos() == null) return false;
        if (this.dwarf.getInventory().isEmpty()) return false;
        
        // Deposit if it's night OR if the inventory is full
        long time = this.dwarf.level().getOverworldClockTime() % 24000L;
        boolean isNight = time >= 13000L && time < 23000L;
        
        boolean isFull = true;
        for (int i = 0; i < this.dwarf.getInventory().getContainerSize(); i++) {
            if (this.dwarf.getInventory().getItem(i).isEmpty()) {
                isFull = false;
                break;
            }
        }
        
        if (!isNight && !isFull) return false;

        this.targetChest = findChestNearBanner();
        return this.targetChest != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.targetChest != null && !this.dwarf.getInventory().isEmpty();
    }

    @Override
    public void start() {
        this.dwarf.getNavigation().moveTo(this.targetChest.getX() + 0.5, this.targetChest.getY(), this.targetChest.getZ() + 0.5, this.speedModifier);
    }

    @Override
    public void tick() {
        if (this.targetChest == null) return;

        double dist = this.dwarf.distanceToSqr(this.targetChest.getX() + 0.5, this.targetChest.getY(), this.targetChest.getZ() + 0.5);
        if (dist > 4.0) {
            this.dwarf.getNavigation().moveTo(this.targetChest.getX() + 0.5, this.targetChest.getY(), this.targetChest.getZ() + 0.5, this.speedModifier);
        } else {
            this.dwarf.getNavigation().stop();
            depositItems();
            this.targetChest = null;
        }
    }

    private void depositItems() {
        if (!(this.dwarf.level() instanceof ServerLevel level)) return;
        
        BlockEntity be = level.getBlockEntity(this.targetChest);
        if (be instanceof Container chest) {
            for (int i = 0; i < this.dwarf.getInventory().getContainerSize(); i++) {
                ItemStack stack = this.dwarf.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    // Try to insert into chest
                    for (int j = 0; j < chest.getContainerSize(); j++) {
                        ItemStack chestStack = chest.getItem(j);
                        if (chestStack.isEmpty()) {
                            chest.setItem(j, stack.copy());
                            this.dwarf.getInventory().setItem(i, ItemStack.EMPTY);
                            break;
                        } else if (ItemStack.isSameItemSameComponents(chestStack, stack) && chestStack.getCount() < chestStack.getMaxStackSize()) {
                            int space = chestStack.getMaxStackSize() - chestStack.getCount();
                            if (stack.getCount() <= space) {
                                chestStack.grow(stack.getCount());
                                this.dwarf.getInventory().setItem(i, ItemStack.EMPTY);
                                break;
                            } else {
                                chestStack.grow(space);
                                stack.shrink(space);
                            }
                        }
                    }
                }
            }
        }
    }

    private BlockPos findChestNearBanner() {
        BlockPos banner = this.dwarf.getBannerPos();
        if (banner == null) return null;

        for (int y = -2; y <= 2; y++) {
            for (int x = -5; x <= 5; x++) {
                for (int z = -5; z <= 5; z++) {
                    BlockPos pos = banner.offset(x, y, z);
                    BlockEntity be = this.dwarf.level().getBlockEntity(pos);
                    if (be instanceof Container) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }
}
