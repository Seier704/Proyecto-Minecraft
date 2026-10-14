package com.dwarfmod.client;

import com.dwarfmod.DwarfMod;
import com.dwarfmod.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class DwarvenArmorRenderer implements ArmorRenderer {
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final Map<EquipmentSlot, ModelLayerLocation> LAYERS = layers("dwarven_gear");

	private final Map<EquipmentSlot, DwarvenGearModel> models = new EnumMap<>(EquipmentSlot.class);

	private final Identifier texture;

	public DwarvenArmorRenderer(EntityRendererProvider.Context context, ModItems.Tier tier, Map<EquipmentSlot, ModelLayerLocation> layers) {
		this.texture = DwarfMod.id("textures/entity/" + tier.name + "_gear.png");
		layers.forEach((slot, loc) -> models.put(slot, new DwarvenGearModel(context.bakeLayer(loc))));
	}

	private static Map<EquipmentSlot, ModelLayerLocation> layers(String model) {
		Map<EquipmentSlot, ModelLayerLocation> map = new EnumMap<>(EquipmentSlot.class);
		for (EquipmentSlot slot : SLOTS) {
			map.put(slot, new ModelLayerLocation(DwarfMod.id(model), slot.getName()));
		}
		return map;
	}

	// One geometry per set, in HEAD, CHEST, LEGS, FEET order; each tier of the set only changes the texture.
	@SafeVarargs
	private static void registerSet(Map<EquipmentSlot, ModelLayerLocation> layers, Iterable<ModItems.Tier> tiers, Supplier<LayerDefinition>... slotLayers) {
		for (int i = 0; i < SLOTS.length; i++) {
			ModelLayerRegistry.registerModelLayer(layers.get(SLOTS[i]), slotLayers[i]::get);
		}
		for (ModItems.Tier tier : tiers) {
			ArmorRenderer.register(context -> new DwarvenArmorRenderer(context, tier, layers), tier.pieces());
		}
	}

	public static void init() {
		registerSet(LAYERS, ModItems.TIERS,
			DwarvenGearModel::head, DwarvenGearModel::chest, DwarvenGearModel::legs, DwarvenGearModel::feet);
		registerSet(layers("warrior_copper_gear"), java.util.List.of(ModItems.WARRIOR_COPPER),
			WarriorGearModel::copperHead, WarriorGearModel::copperChest, WarriorGearModel::copperLegs, WarriorGearModel::copperFeet);
		registerSet(layers("warrior_steel_gear"), java.util.List.of(ModItems.WARRIOR_STEEL),
			WarriorGearModel::steelHead, WarriorGearModel::steelChest, WarriorGearModel::steelLegs, WarriorGearModel::steelFeet);
		registerSet(layers("warrior_veteran_gear"), java.util.List.of(ModItems.WARRIOR_VETERAN),
			WarriorGearModel::veteranHead, WarriorGearModel::veteranChest, WarriorGearModel::veteranLegs, WarriorGearModel::veteranFeet);
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
