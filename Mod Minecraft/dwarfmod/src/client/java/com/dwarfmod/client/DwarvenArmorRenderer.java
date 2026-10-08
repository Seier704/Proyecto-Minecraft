package com.dwarfmod.client;

import com.dwarfmod.DwarfMod;
import com.dwarfmod.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class DwarvenArmorRenderer implements ArmorRenderer {
		private static final Map<EquipmentSlot, ModelLayerLocation> LAYERS = new EnumMap<>(EquipmentSlot.class);

	static {
		LAYERS.put(EquipmentSlot.HEAD, new ModelLayerLocation(DwarfMod.id("dwarven_gear"), "head"));
		LAYERS.put(EquipmentSlot.CHEST, new ModelLayerLocation(DwarfMod.id("dwarven_gear"), "chest"));
		LAYERS.put(EquipmentSlot.LEGS, new ModelLayerLocation(DwarfMod.id("dwarven_gear"), "legs"));
		LAYERS.put(EquipmentSlot.FEET, new ModelLayerLocation(DwarfMod.id("dwarven_gear"), "feet"));
	}

	private final Map<EquipmentSlot, DwarvenGearModel> models = new EnumMap<>(EquipmentSlot.class);

	private final Identifier texture;

	public DwarvenArmorRenderer(EntityRendererProvider.Context context, ModItems.Tier tier) {
		this.texture = DwarfMod.id("textures/entity/" + tier.name + "_gear.png");
		LAYERS.forEach((slot, loc) -> models.put(slot, new DwarvenGearModel(context.bakeLayer(loc))));
	}

	public static void init() {
		ModelLayerRegistry.registerModelLayer(LAYERS.get(EquipmentSlot.HEAD), DwarvenGearModel::head);
		ModelLayerRegistry.registerModelLayer(LAYERS.get(EquipmentSlot.CHEST), DwarvenGearModel::chest);
		ModelLayerRegistry.registerModelLayer(LAYERS.get(EquipmentSlot.LEGS), DwarvenGearModel::legs);
		ModelLayerRegistry.registerModelLayer(LAYERS.get(EquipmentSlot.FEET), DwarvenGearModel::feet);
		for (ModItems.Tier tier : ModItems.TIERS) {
			ArmorRenderer.register(context -> new DwarvenArmorRenderer(context, tier), tier.pieces());
		}
	}

	@Override
	public void render(PoseStack poseStack, SubmitNodeCollector collector, ItemStack stack, HumanoidRenderState state,
			EquipmentSlot slot, int light, HumanoidModel<HumanoidRenderState> contextModel) {
		DwarvenGearModel model = models.get(slot);
		if (model == null) {
			return;
		}
		ArmorRenderer.submitTransformCopyingModel(contextModel, state, model, state, false, collector, poseStack,
			RenderTypes.armorCutoutNoCull(texture), light, OverlayTexture.NO_OVERLAY, 0);
	}
}

