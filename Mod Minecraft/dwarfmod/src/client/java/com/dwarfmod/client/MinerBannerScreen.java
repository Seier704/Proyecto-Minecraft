package com.dwarfmod.client;

import com.dwarfmod.DwarfBannerActionRequest;
import com.dwarfmod.DwarfEntity;
import com.dwarfmod.MinerBannerMenu;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class MinerBannerScreen extends AbstractContainerScreen<MinerBannerMenu> {

    private List<DwarfEntity> nearbyDwarves;
    private int selectedDwarfIndex = -1;

    private final int customImageHeight = 240;

    public MinerBannerScreen(MinerBannerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.customImageHeight) / 2;

        if (this.minecraft != null && this.minecraft.level != null) {
            AABB searchBox = new AABB(this.menu.blockEntity.getBlockPos()).inflate(32);
            this.nearbyDwarves = this.minecraft.level.getEntitiesOfClass(DwarfEntity.class, searchBox);
        }

        Button btnWork = Button.builder(Component.literal("WORK"), b -> {
            if (this.selectedDwarfIndex >= 0 && this.selectedDwarfIndex < this.nearbyDwarves.size()) {
                ClientPlayNetworking.send(new DwarfBannerActionRequest(this.nearbyDwarves.get(this.selectedDwarfIndex).getId(), 1));
            }
        }).bounds(x + 12, y + 80, 70, 20).build();

        Button btnStop = Button.builder(Component.literal("STOP"), b -> {
            if (this.selectedDwarfIndex >= 0 && this.selectedDwarfIndex < this.nearbyDwarves.size()) {
                ClientPlayNetworking.send(new DwarfBannerActionRequest(this.nearbyDwarves.get(this.selectedDwarfIndex).getId(), 2));
            }
        }).bounds(x + 94, y + 80, 70, 20).build();

        this.addRenderableWidget(btnWork);
        this.addRenderableWidget(btnStop);

        // Invisible buttons for dwarf selection
        if (this.nearbyDwarves != null) {
            for (int i = 0; i < Math.min(4, this.nearbyDwarves.size()); i++) {
                final int idx = i;
                Button selectBtn = Button.builder(Component.empty(), b -> {
                    this.selectedDwarfIndex = idx;
                    ClientPlayNetworking.send(new DwarfBannerActionRequest(this.nearbyDwarves.get(idx).getId(), 0));
                }).bounds(x + 10, y + 22 + (i * 12), 156, 12).build();
                selectBtn.setAlpha(0.0f); // transparent
                this.addRenderableWidget(selectBtn);
            }
        }
    }

    protected void renderBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.customImageHeight) / 2;
        
        // Background
        graphics.fill(x, y, x + this.imageWidth, y + this.customImageHeight, 0xFF373737);
        graphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + this.customImageHeight - 1, 0xFFC6C6C6);

        // Titles
        graphics.text(this.font, "NEARBY DWARVES (32m)", x + 10, y + 8, 0xFF404040, false);
        graphics.text(this.font, "INVENTORY PREVIEW", x + 10, y + 108, 0xFF404040, false);

        // Dwarf List Background
        graphics.fill(x + 10, y + 20, x + 166, y + 72, 0xFF202020);

        if (this.nearbyDwarves != null) {
            for (int i = 0; i < this.nearbyDwarves.size(); i++) {
                if (i >= 4) break; // limit to 4 for simple UI
                
                DwarfEntity dwarf = this.nearbyDwarves.get(i);
                int entryY = y + 22 + (i * 12);
                
                if (i == this.selectedDwarfIndex) {
                    graphics.fill(x + 10, entryY - 2, x + 166, entryY + 10, 0xFF555500); // Selection highlight
                }

                String state = dwarf.isWorking() ? "Working" : "Idle";
                String name = dwarf.getName().getString() + " - " + state;
                int color = i == this.selectedDwarfIndex ? 0xFFFFFF55 : 0xFFB0B0B0;
                graphics.text(this.font, name, x + 14, entryY, color, false);
            }
        }
        
        // Slot backgrounds
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 4; col++) {
                graphics.fill(x + 11 + col * 18, y + 119 + row * 18, x + 29 + col * 18, y + 137 + row * 18, 0xFF8B8B8B);
                graphics.fill(x + 12 + col * 18, y + 120 + row * 18, x + 28 + col * 18, y + 136 + row * 18, 0xFF373737);
            }
        }
    }
}
