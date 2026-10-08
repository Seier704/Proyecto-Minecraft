package com.dwarfmod.client;

import com.dwarfmod.DwarfEntity;
import com.dwarfmod.DwarfMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

public class DwarfRenderer extends HumanoidMobRenderer<DwarfEntity, HumanoidRenderState, DwarfModel> {
	private static final Identifier TEXTURE = DwarfMod.id("textures/entity/dwarf.png");

	public DwarfRenderer(EntityRendererProvider.Context context) {
		super(context, new DwarfModel(context.bakeLayer(DwarfModClient.DWARF_LAYER)), 0.5F);
	}

	@Override
	protected void scale(HumanoidRenderState state, PoseStack poseStack) {
		poseStack.scale(1.2F, 0.8F, 1.2F);
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return TEXTURE;
	}
}

