package com.dwarfmod.client;

import com.dwarfmod.DwarfFollowRequest;
import com.dwarfmod.DwarfInfoPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/** Inventory-style panel with the dwarf's role, level, friendship and hiring status. */
public class DwarfInfoScreen extends Screen {
	private static final int W = 230;
	private static final int H = 162;
	private static final int[] STAGE_COLORS = {0xFFA03030, 0xFFB08030, 0xFF508030, 0xFF2080A0, 0xFF7040C0};
	private static final int TEXT = 0xFF404040;

	private final int[] v;

	public DwarfInfoScreen(DwarfInfoPayload payload) {
		super(Component.translatable("entity.dwarfmod.dwarf." + payload.v()[1]));
		this.v = payload.v();
	}

	@Override
	protected void init() {
		int x = (this.width - W) / 2;
		int y = (this.height - H) / 2;
		boolean following = v[13] == 1;
		Component label = Component.translatable(following ? "dwarfmod.screen.button.stop" : "dwarfmod.screen.button.follow");
		Button follow = Button.builder(label, b -> ClientPlayNetworking.send(new DwarfFollowRequest(v[0])))
			.bounds(x + 82, y + H - 30, 138, 20).build();
		follow.active = v[9] == 1;
		this.addRenderableWidget(follow);
	}

	@Override
	public boolean isPauseScreen() {

		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.minecraft.options.keyInventory.matches(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	private void bar(GuiGraphicsExtractor g, int x, int y, int w, float frac, int color) {
		g.fill(x, y, x + w, y + 7, 0xFF373737);
		g.fill(x + 1, y + 1, x + w - 1, y + 6, 0xFF8B8B8B);
		int filled = Math.round((w - 2) * Math.max(0.0F, Math.min(1.0F, frac)));
		g.fill(x + 1, y + 1, x + 1 + filled, y + 6, color);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int x = (this.width - W) / 2;
		int y = (this.height - H) / 2;
		g.fill(x, y, x + W, y + H, 0xFF000000);
		g.fill(x + 1, y + 1, x + W - 1, y + H - 1, 0xFFFFFFFF);
		g.fill(x + 3, y + 3, x + W - 1, y + H - 1, 0xFF555555);
		g.fill(x + 3, y + 3, x + W - 3, y + H - 3, 0xFFC6C6C6);

		g.centeredText(this.font, this.title, x + W / 2, y + 8, TEXT);

		int px = x + 10;
		int py = y + 22;
		g.fill(px, py, px + 62, py + 130, 0xFF373737);
		g.fill(px + 1, py + 1, px + 62, py + 130, 0xFFFFFFFF);
		g.fill(px + 1, py + 1, px + 61, py + 129, 0xFF8B8B8B);
		g.fill(px + 2, py + 2, px + 61, py + 129, 0xFF202020);
		var entity = this.minecraft.level == null ? null : this.minecraft.level.getEntity(v[0]);
		if (entity instanceof LivingEntity living) {
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, px + 2, py + 2, px + 61, py + 129, 42, 0.0625F, mouseX, mouseY, living);
		}

		int tx = x + 82;
		int ty = y + 24;
		String max = Component.translatable("dwarfmod.screen.max").getString();

		g.text(this.font, Component.translatable("dwarfmod.screen.level", v[2]), tx, ty, TEXT, false);
		String xpText = v[4] < 0 ? max : v[3] + "/" + v[4];
		g.text(this.font, "XP " + xpText, tx + 70, ty, TEXT, false);
		this.bar(g, tx, ty + 11, 138, v[4] < 0 ? 1.0F : (float) v[3] / v[4], 0xFF55C040);

		ty += 28;
		g.text(this.font, Component.translatable("dwarfmod.screen.friendship"), tx, ty, TEXT, false);
		g.text(this.font, Component.translatable("dwarfmod.friendship." + v[6]), tx + 70, ty, STAGE_COLORS[v[6]], false);
		String ptsText = v[7] < 0 ? max : v[5] + "/" + v[7];
		this.bar(g, tx, ty + 11, 138, v[7] < 0 ? 1.0F : (float) v[5] / v[7], STAGE_COLORS[v[6]]);
		g.text(this.font, ptsText, tx + 138 - this.font.width(ptsText), ty + 21, 0xFF606060, false);

		ty += 36;
		String pct = (v[8] > 0 ? "+" : "") + v[8] + "%";
		g.text(this.font, Component.translatable("dwarfmod.screen.prices", pct), tx, ty, TEXT, false);

		ty += 14;
		g.text(this.font, Component.translatable(v[13] == 1 ? "dwarfmod.screen.follow.on" : v[9] == 1 ? "dwarfmod.screen.follow.yes" : "dwarfmod.screen.follow.no"), tx, ty, TEXT, false);

		if (v[10] > 0) {
			ty += 14;
			Component hire = switch (v[10]) {
				case 3 -> Component.translatable("dwarfmod.screen.hire.active", v[11]);
				case 2 -> Component.translatable("dwarfmod.screen.hire.yes", v[12]);
				default -> Component.translatable("dwarfmod.screen.hire.no", Component.translatable("dwarfmod.friendship.3"));
			};
			g.text(this.font, hire, tx, ty, v[10] == 1 ? 0xFFA03030 : 0xFF2A7A2A, false);
		}
	}
}
