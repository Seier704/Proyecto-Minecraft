package com.dwarfmod;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class DelegatingContainer implements Container {
    public Container delegate = new SimpleContainer(8);

    @Override
    public int getContainerSize() {
        return delegate.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot >= delegate.getContainerSize()) return ItemStack.EMPTY;
        return delegate.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot >= delegate.getContainerSize()) return ItemStack.EMPTY;
        return delegate.removeItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot >= delegate.getContainerSize()) return ItemStack.EMPTY;
        return delegate.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= delegate.getContainerSize()) return;
        delegate.setItem(slot, stack);
    }

    @Override
    public void setChanged() {
        delegate.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return delegate.stillValid(player);
    }

    @Override
    public void clearContent() {
        delegate.clearContent();
    }
}
