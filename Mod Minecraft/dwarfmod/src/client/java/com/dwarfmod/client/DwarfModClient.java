package com.dwarfmod.client;

import com.dwarfmod.DwarfMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class DwarfModClient implements ClientModInitializer {
	public static final ModelLayerLocation DWARF_LAYER = new ModelLayerLocation(DwarfMod.id("dwarf"), "main");

	@Override
	public void onInitializeClient() {
		ModelLayerRegistry.registerModelLayer(DWARF_LAYER, DwarfModel::createLayer);
		EntityRendererRegistry.register(DwarfMod.DWARF, DwarfRenderer::new);
		DwarvenArmorRenderer.init();
		DwarfInfoClient.init();
		net.minecraft.client.gui.screens.MenuScreens.register(DwarfMod.MINER_BANNER_MENU, MinerBannerScreen::new);
	}
}


