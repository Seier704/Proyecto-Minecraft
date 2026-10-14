package com.dwarfmod.test;

import com.dwarfmod.DwarfEntity;
import com.dwarfmod.DwarfMod;
import com.dwarfmod.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Lines up every dwarf look (GeckoLib model), plays the two-handed axe attack and the worn veteran armor; screenshots only. */
public class DwarfLookGameTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			mc.options.guiScale().set(2);
		});
		ctx.getInput().resizeWindow(1280, 800);
		try (var sp = ctx.worldBuilder().create()) {
			ctx.waitTicks(20);
			List<Integer> warriors = new ArrayList<>();
			sp.getServer().runOnServer(server -> {
				var player = server.getPlayerList().getPlayers().get(0);
				var level = player.level();
				BlockPos base = player.blockPosition();
				for (int dx = -8; dx <= 8; dx++) {
					for (int dz = -4; dz <= 10; dz++) {
						level.setBlockAndUpdate(base.offset(dx, -1, dz), Blocks.POLISHED_ANDESITE.defaultBlockState());
						for (int dy = 0; dy <= 4; dy++) {
							level.setBlockAndUpdate(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
						}
					}
				}
				var cmd = server.getCommands();
				var src = player.createCommandSourceStack().withSuppressedOutput();
				cmd.performPrefixedCommand(src, "time set 6000");
				cmd.performPrefixedCommand(src, "weather clear");
				cmd.performPrefixedCommand(src, "gamerule doDaylightCycle false");
				cmd.performPrefixedCommand(src, "tp @s " + (base.getX() + 0.5) + " " + base.getY() + " " + (base.getZ() + 0.5) + " 0 12");
				// blacksmith, miner, warrior 1, 2, 3 facing the player
				int[][] line = {{DwarfEntity.BLACKSMITH, 0}, {DwarfEntity.MINER, 0}, {DwarfEntity.WARRIOR, 1}, {DwarfEntity.WARRIOR, 2}, {DwarfEntity.WARRIOR, 3}};
				for (int i = 0; i < line.length; i++) {
					DwarfEntity dwarf = DwarfMod.DWARF.create(level, EntitySpawnReason.COMMAND);
					dwarf.setRole(line[i][0]);
					if (line[i][1] > 0) {
						dwarf.setWarriorTier(line[i][1]);
						warriors.add(dwarf.getId());
					}
					dwarf.setNoAi(true);
					dwarf.snapTo(base.getX() + 0.5 + (2 - i) * 1.6, base.getY(), base.getZ() + 4.5, 180.0F, 0.0F);
					dwarf.setYHeadRot(180.0F);
					dwarf.setYBodyRot(180.0F);
					if (line[i][0] == DwarfEntity.MINER) {
						dwarf.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
					}
					level.addFreshEntity(dwarf);
				}
			});
			ctx.waitTicks(60);
			ctx.takeScreenshot("dwarf_lineup");

			// Two-handed attack of every warrior: wind-up (~0.38 s) and impact (~0.55 s).
			sp.getServer().runOnServer(server -> {
				var level = server.getPlayerList().getPlayers().get(0).level();
				for (int id : warriors) {
					((DwarfEntity) level.getEntity(id)).triggerAnim("action", "attack_axe");
				}
			});
			ctx.waitTicks(7);
			ctx.takeScreenshot("dwarf_attack_windup");
			ctx.waitTicks(4);
			ctx.takeScreenshot("dwarf_attack_impact");
			ctx.waitTicks(30);

			// Side view of the line
			sp.getServer().runOnServer(server -> {
				var player = server.getPlayerList().getPlayers().get(0);
				var src = player.createCommandSourceStack().withSuppressedOutput();
				server.getCommands().performPrefixedCommand(src, "tp @s ~-5 ~ ~4.5 -90 12");
				var level = player.level();
				for (int id : warriors) {
					((DwarfEntity) level.getEntity(id)).triggerAnim("action", "attack_axe");
				}
			});
			ctx.waitTicks(8);
			ctx.takeScreenshot("dwarf_attack_side");
			ctx.waitTicks(30);

			// The player in the veteran set, third person front view
			sp.getServer().runOnServer(server -> {
				var player = server.getPlayerList().getPlayers().get(0);
				Object[] pieces = ModItems.WARRIOR_VETERAN.pieces();
				EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
				for (int i = 0; i < 4; i++) {
					player.setItemSlot(slots[i], new ItemStack((net.minecraft.world.item.Item) pieces[i]));
				}
				var src = player.createCommandSourceStack().withSuppressedOutput();
				server.getCommands().performPrefixedCommand(src, "tp @s ~5 ~ ~-4.5 0 0");
			});
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.waitTicks(20);
			ctx.takeScreenshot("player_veteran_armor");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		}
	}
}
