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
    private int cooldown;

    public DwarfDepositGoal(DwarfEntity dwarf, double speedModifier) {
        this.dwarf = dwarf;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.dwarf.getBannerPos() == null) return false;
        if (this.dwarf.getInventory().isEmpty()) return false;
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        
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
            this.cooldown = 200;
        }
    }

    private void depositItems() {
        if (!(this.dwarf.level() instanceof ServerLevel level)) return;

        BlockEntity be = level.getBlockEntity(this.targetChest);
        if (!(be instanceof Container chest)) return;
        var inv = this.dwarf.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            // Merge into matching stacks first, then fill empty slots; whatever does not fit stays with the dwarf.
            for (int j = 0; j < chest.getContainerSize() && !stack.isEmpty(); j++) {
                ItemStack target = chest.getItem(j);
                if (!target.isEmpty() && ItemStack.isSameItemSameComponents(target, stack)) {
                    int move = Math.min(stack.getCount(), target.getMaxStackSize() - target.getCount());
                    if (move > 0) {
                        target.grow(move);
                        stack.shrink(move);
                    }
                }
            }
            for (int j = 0; j < chest.getContainerSize() && !stack.isEmpty(); j++) {
                if (chest.getItem(j).isEmpty()) {
                    chest.setItem(j, stack.copy());
                    stack.setCount(0);
                }
            }
            if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
        }
        chest.setChanged();
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
