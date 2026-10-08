package com.dwarfmod.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

public class DwarfModel extends HumanoidModel<HumanoidRenderState> {
	public DwarfModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createLayer() {
		return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64);
	}
}
