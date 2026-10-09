package com.dwarfmod.test;

import com.dwarfmod.DwarfEntity;
import com.dwarfmod.DwarfMod;
import com.dwarfmod.MinerBannerBlockEntity;
import com.dwarfmod.ModItems;
import com.dwarfmod.client.MinerBannerScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;

/** Opens the miner banner screen in a real world, clicks its controls and checks the server state. */
public class BannerGameTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> mc.options.guiScale().set(2));
		ctx.getInput().resizeWindow(1280, 800);
		BlockPos[] banner = new BlockPos[1];
		try (var sp = ctx.worldBuilder().create()) {
			ctx.waitTicks(20);
			sp.getServer().runOnServer(server -> {
				var player = server.getPlayerList().getPlayers().get(0);
				var level = player.level();
				BlockPos pos = player.blockPosition().offset(2, 0, 0);
				banner[0] = pos;
				// Solid rock in front of the banner (tunnels run north by default) so the miners have something to dig.
				for (int dx = -3; dx <= 3; dx++) {
					for (int dy = -4; dy <= 1; dy++) {
						for (int dz = -1; dz >= -20; dz--) {
							level.setBlockAndUpdate(pos.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
						}
					}
				}
				level.setBlockAndUpdate(pos, ModItems.MINER_BANNER_BLOCK.defaultBlockState());
				for (int i = 0; i < 2; i++) {
					DwarfEntity dwarf = DwarfMod.DWARF.create(level, EntitySpawnReason.COMMAND);
					dwarf.setRole(DwarfEntity.MINER);
					dwarf.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5 + i, 0.0F, 0.0F);
					level.addFreshEntity(dwarf);
					dwarf.setBannerPos(pos);
				}
				player.openMenu((MinerBannerBlockEntity) level.getBlockEntity(pos));
			});
			ctx.waitForScreen(MinerBannerScreen.class);
			ctx.waitTicks(40);
			ctx.takeScreenshot("banner_open");

			// Select the first miner (row 0) and press Mine; the server must flag it as working.
			click(ctx, 80, 25);
			ctx.waitTicks(5);
			ctx.takeScreenshot("banner_selected");
			click(ctx, 48, 73); // Mine
			// The server may switch it off again within a few ticks (nothing to dig on flat ground), so sample every tick.
			boolean working = false;
			for (int i = 0; i < 10 && !working; i++) {
				ctx.waitTick();
				working = sp.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().get(0).level()
					.getEntitiesOfClass(DwarfEntity.class, server.getPlayerList().getPlayers().get(0).getBoundingBox().inflate(64.0)).stream()
					.anyMatch(DwarfEntity::isWorking));
			}
			if (!working) {
				throw new AssertionError("No dwarf became working after pressing Mine");
			}
			ctx.waitTicks(25); // the list refreshes every 20 ticks
			ctx.takeScreenshot("banner_mining");

			// Size button cycles 1x1 -> 2x2, depth + goes 10 -> 15.
			click(ctx, 88, 92);
			click(ctx, 156, 111);
			ctx.waitTicks(20);
			int[] cfg = sp.getServer().computeOnServer(server -> {
				var player = server.getPlayerList().getPlayers().get(0);
				var be = (MinerBannerBlockEntity) player.level().getBlockEntity(banner[0]);
				return new int[] {be.getMiningMode(), be.getMiningDepth()};
			});
			if (cfg[0] != 2 || cfg[1] != 15) {
				throw new AssertionError("Expected mode 2 / depth 15, got " + cfg[0] + " / " + cfg[1]);
			}
			ctx.takeScreenshot("banner_config");
		}
	}

	/** Clicks at a position relative to the top-left corner of the banner panel (176x238, centered). */
	private static void click(ClientGameTestContext ctx, int relX, int relY) {
		int[] origin = ctx.computeOnClient(mc -> {
			var screen = mc.gui.screen();
			return new int[] {(screen.width - 176) / 2, (screen.height - 238) / 2, (int) mc.getWindow().getGuiScale()};
		});
		ctx.getInput().setCursorPos((origin[0] + relX) * origin[2], (origin[1] + relY) * origin[2]);
		ctx.waitTick();
		ctx.getInput().pressMouse(0);
		ctx.waitTick();
	}
}
