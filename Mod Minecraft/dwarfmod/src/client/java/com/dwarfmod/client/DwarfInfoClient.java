package com.dwarfmod.client;

import com.dwarfmod.DwarfEntity;
import com.dwarfmod.DwarfInfoPayload;
import com.dwarfmod.DwarfInfoRequest;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;

/** Alt + middle click (the pick-block key) on a dwarf asks the server for its info and opens the info screen. */
public final class DwarfInfoClient {
	private static boolean wasDown = false;

	private DwarfInfoClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(DwarfInfoPayload.TYPE, (payload, context) ->
			Minecraft.getInstance().setScreenAndShow(new DwarfInfoScreen(payload)));

		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			boolean alt = InputConstants.isKeyDown(InputConstants.KEY_LALT) || InputConstants.isKeyDown(InputConstants.KEY_RALT);
			boolean down = mc.gui.screen() == null && mc.player != null && alt && mc.options.keyPickItem.isDown();
			if (down && !wasDown && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof DwarfEntity) {
				ClientPlayNetworking.send(new DwarfInfoRequest(hit.getEntity().getId()));
			}
			wasDown = down;
		});
	}
}
