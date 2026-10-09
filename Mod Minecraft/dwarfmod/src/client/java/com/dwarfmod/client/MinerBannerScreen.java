package com.dwarfmod.client;

import com.dwarfmod.BannerConfigRequest;
import com.dwarfmod.BannerDwarvesPayload;
import com.dwarfmod.DwarfBannerActionRequest;
import com.dwarfmod.MinerBannerMenu;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Miner banner: hired miners near the banner (multi-select), mine/stop, tunnel size and depth, inventory preview.
 * The panel is painted in extractBackground; every control is a real widget ({@link Ctl}) so clicks go through
 * the vanilla widget pipeline.
 */
public class MinerBannerScreen extends AbstractContainerScreen<MinerBannerMenu> {

    private static final int ROWS = 3;
    private static final int ROW_H = 14;
    private static final int LIST_Y = 16;
    private static final int REFRESH_TICKS = 5;

    private static final int PANEL = 0xFF202020;
    private static final int PANEL_HEADER = 0xFF303030;
    private static final int TEXT_DARK = 0xFF404040;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFFB0B0B0;
    private static final int GOLD = 0xFFFFFF55;
    private static final int RED_TEXT = 0xFFFF5555;
    private static final int GREEN = 0xFF2E9E2E;
    private static final int RED = 0xFFB52424;
    private static final int GRAY = 0xFF6E6E6E;

    private static final String[] NAMES = {"Gimli", "Thorin", "Balin", "Dwalin", "Bofur", "Oin", "Gloin", "Bifur",
        "Bombur", "Dori", "Nori", "Ori", "Fili", "Kili", "Durin", "Dain"};

    private static final int[] DEPTH_BOX = {34, 103, 108, 16}; // x, y, w, h relative to the panel

    /** Latest list from the server, set by the client receiver. */
    private List<BannerDwarvesPayload.Entry> dwarves = new ArrayList<>();
    private final Set<Integer> selected = new HashSet<>();
    private final List<Ctl> controls = new ArrayList<>();
    private int page = 0;
    private int ticks = 0;

    public MinerBannerScreen(MinerBannerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 238);
    }

    /** Called from the network receiver on the client thread. */
    public void setDwarves(List<BannerDwarvesPayload.Entry> list) {
        this.dwarves = new ArrayList<>(list);
        this.selected.retainAll(this.dwarves.stream().map(BannerDwarvesPayload.Entry::id).toList());
        this.page = Math.min(this.page, pageCount() - 1);
    }

    private int pageCount() {
        return Math.max(1, (this.dwarves.size() + ROWS - 1) / ROWS);
    }

    /** How a control paints itself (inside its own rectangle). */
    private interface Paint {
        void paint(GuiGraphicsExtractor g, Ctl c);
    }

    /** A flat widget with its own look; {@code enabled} decides whether it is clickable. */
    private final class Ctl extends AbstractButton {
        private final Runnable action;
        private final BooleanSupplier enabled;
        private final Paint paint;

        Ctl(int rx, int ry, int w, int h, Runnable action, BooleanSupplier enabled, Paint paint) {
            super(MinerBannerScreen.this.leftPos + rx, MinerBannerScreen.this.topPos + ry, w, h, Component.empty());
            this.action = action;
            this.enabled = enabled;
            this.paint = paint;
        }

        void sync() {
            this.active = this.enabled.getAsBoolean();
        }

        @Override
        public void onPress(InputWithModifiers input) {
            if (this.active) {
                this.action.run();
            }
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            this.paint.paint(g, this);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
        }
    }

    private void add(int rx, int ry, int w, int h, Runnable action, BooleanSupplier enabled, Paint paint) {
        Ctl c = new Ctl(rx, ry, w, h, action, enabled, paint);
        c.sync();
        this.controls.add(c);
        this.addRenderableWidget(c);
    }

    private void button(int rx, int ry, int w, int h, int color, Supplier<String> label, Runnable action, BooleanSupplier enabled) {
        add(rx, ry, w, h, action, enabled, (g, c) -> paintButton(g, c, color, label.get()));
    }

    @Override
    protected void init() {
        super.init();
        this.controls.clear();
        for (int i = 0; i < ROWS; i++) {
            final int row = i;
            add(10, LIST_Y + 2 + row * ROW_H, 156, ROW_H, () -> toggle(this.page * ROWS + row),
                () -> this.page * ROWS + row < this.dwarves.size(), (g, c) -> paintRow(g, c, this.page * ROWS + row));
        }
        button(132, 2, 16, 12, GRAY, () -> "<", () -> this.page = Math.max(0, this.page - 1), () -> this.page > 0);
        button(150, 2, 16, 12, GRAY, () -> ">", () -> this.page = Math.min(pageCount() - 1, this.page + 1),
            () -> this.page < pageCount() - 1);

        button(10, 65, 76, 16, GREEN, () -> Component.translatable("dwarfmod.banner.mine").getString(), () -> sendToSelected(1),
            () -> !this.selected.isEmpty());
        button(90, 65, 76, 16, RED, () -> Component.translatable("dwarfmod.banner.stop").getString(), () -> sendToSelected(2),
            () -> !this.selected.isEmpty());
        button(10, 84, 156, 16, GRAY, () -> {
            int mode = Math.max(1, Math.min(3, this.menu.getMode()));
            return Component.translatable("dwarfmod.banner.size", mode + "x" + mode).getString();
        }, () -> sendConfig(this.menu.getMode() % 3 + 1, this.menu.getDepth()), () -> true);
        button(10, 103, 20, 16, GRAY, () -> "-", () -> sendConfig(this.menu.getMode(), this.menu.getDepth() - 5), () -> this.menu.getDepth() > 5);
        button(146, 103, 20, 16, GRAY, () -> "+", () -> sendConfig(this.menu.getMode(), this.menu.getDepth() + 5), () -> this.menu.getDepth() < 64);

        ClientPlayNetworking.send(new DwarfBannerActionRequest(-1, 3));
    }

    private void sendConfig(int mode, int depth) {
        ClientPlayNetworking.send(new BannerConfigRequest(Math.max(1, Math.min(3, mode)), Math.max(5, Math.min(64, depth))));
    }

    private void toggle(int index) {
        if (index < 0 || index >= this.dwarves.size()) {
            return;
        }
        int id = this.dwarves.get(index).id();
        if (!this.selected.remove(id)) {
            this.selected.add(id);
            // The inventory preview follows the last miner that was selected.
            ClientPlayNetworking.send(new DwarfBannerActionRequest(id, 0));
        }
    }

    private void sendToSelected(int action) {
        for (int id : this.selected) {
            ClientPlayNetworking.send(new DwarfBannerActionRequest(id, action));
        }
        // Packets arrive in order, so this refresh already reflects the action just sent.
        ClientPlayNetworking.send(new DwarfBannerActionRequest(-1, 3));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (++this.ticks % REFRESH_TICKS == 0) {
            ClientPlayNetworking.send(new DwarfBannerActionRequest(-1, 3));
        }
    }

    private String nameOf(Entity entity, int id) {
        return entity == null ? "#" + id : NAMES[Math.floorMod(entity.getUUID().hashCode(), NAMES.length)];
    }

    private String stateOf(BannerDwarvesPayload.Entry e) {
        if (e.full()) {
            return Component.translatable("dwarfmod.banner.state.full").getString();
        }
        return Component.translatable(e.working() ? "dwarfmod.banner.state.mining" : "dwarfmod.banner.state.free").getString();
    }

    /** Beveled flat button: light top/left edge, dark bottom/right edge. */
    private void paintButton(GuiGraphicsExtractor g, Ctl c, int color, String label) {
        int x = c.getX();
        int y = c.getY();
        int w = c.getWidth();
        int h = c.getHeight();
        int base = !c.active ? 0xFF4A4A4A : c.isHovered() ? brighten(color) : color;
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, base);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x55FFFFFF);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0x55FFFFFF);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0x66000000);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, 0x66000000);
        g.text(this.font, label, x + (w - this.font.width(label)) / 2, y + (h - 8) / 2 + 1, c.active ? TEXT_WHITE : 0xFF909090, true);
    }

    private void paintRow(GuiGraphicsExtractor g, Ctl c, int idx) {
        if (idx >= this.dwarves.size()) {
            return;
        }
        BannerDwarvesPayload.Entry e = this.dwarves.get(idx);
        int x = c.getX();
        int ey = c.getY();
        boolean sel = this.selected.contains(e.id());
        if (sel) {
            g.fill(x + 1, ey, x + 155, ey + ROW_H, 0xFF4A4A00);
            g.fill(x, ey - 1, x + 156, ey, GOLD);
            g.fill(x, ey + ROW_H, x + 156, ey + ROW_H + 1, GOLD);
        } else if (c.isHovered()) {
            g.fill(x + 1, ey, x + 155, ey + ROW_H, 0xFF303030);
        }
        Entity entity = this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.getEntity(e.id()) : null;
        int stateColor = e.full() ? RED_TEXT : e.working() ? GOLD : TEXT_GRAY;
        String name = nameOf(entity, e.id()) + " - ";
        g.text(this.font, name, x + 4, ey + 3, sel ? GOLD : TEXT_WHITE, false);
        g.text(this.font, stateOf(e), x + 4 + this.font.width(name), ey + 3, stateColor, false);
        g.item(e.tool(), x + 138, ey - 1);
    }

    private static int brighten(int c) {
        int r = Math.min(255, ((c >> 16) & 0xFF) + 28);
        int gr = Math.min(255, ((c >> 8) & 0xFF) + 28);
        int b = Math.min(255, (c & 0xFF) + 28);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        for (Ctl c : this.controls) {
            c.sync();
        }
        paintPanel(graphics);
    }

    /** All text is painted by the panel itself; skip the default title / "Inventory" labels. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    }

    private void paintPanel(GuiGraphicsExtractor graphics) {
        int x = this.leftPos;
        int y = this.topPos;

        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF373737);
        graphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, 0xFFC6C6C6);

        // Header + dwarf list backdrop (rows are widgets drawn on top)
        graphics.text(this.font, Component.translatable("dwarfmod.banner.title").getString(), x + 10, y + 4, TEXT_DARK, false);
        String pageText = (this.page + 1) + "/" + pageCount();
        graphics.text(this.font, pageText, x + 132 - 4 - this.font.width(pageText), y + 4, TEXT_DARK, false);

        graphics.fill(x + 10, y + LIST_Y, x + 166, y + LIST_Y + 4 + ROWS * ROW_H, PANEL);
        if (this.dwarves.isEmpty()) {
            graphics.text(this.font, Component.translatable("dwarfmod.banner.none").getString(), x + 14, y + LIST_Y + 6, 0xFF909090, false);
        }

        // Depth readout between the - and + buttons
        int bx = x + DEPTH_BOX[0];
        int by = y + DEPTH_BOX[1];
        graphics.fill(bx, by, bx + DEPTH_BOX[2], by + DEPTH_BOX[3], PANEL);
        String depth = Component.translatable("dwarfmod.banner.depth", this.menu.getDepth()).getString();
        graphics.text(this.font, depth, bx + (DEPTH_BOX[2] - this.font.width(depth)) / 2, by + 4, TEXT_WHITE, false);

        // Inventory preview: dark header bar + dark panel around the 8 slots (one row)
        graphics.fill(x + 10, y + 123, x + 166, y + 133, PANEL_HEADER);
        String inv = Component.translatable("dwarfmod.banner.inventory").getString();
        graphics.text(this.font, inv, x + 10 + (156 - this.font.width(inv)) / 2, y + 124, TEXT_WHITE, false);
        graphics.fill(x + 10, y + 133, x + 166, y + 153, PANEL);
        for (int col = 0; col < 8; col++) {
            slotBg(graphics, x + 12 + col * 18, y + 135);
        }

        // Player inventory
        for (int l = 0; l < 3; l++) {
            for (int c = 0; c < 9; c++) {
                slotBg(graphics, x + 8 + c * 18, y + 158 + l * 18);
            }
        }
        for (int c = 0; c < 9; c++) {
            slotBg(graphics, x + 8 + c * 18, y + 216);
        }
    }

    private static void slotBg(GuiGraphicsExtractor graphics, int sx, int sy) {
        graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF8B8B8B);
        graphics.fill(sx, sy, sx + 16, sy + 16, 0xFF373737);
    }
}
