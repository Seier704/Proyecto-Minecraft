package com.dwarfmod;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.item.BlockItem;

public class ModItems {
	// Armor sets in unlock order; the last one is just below diamond.
	public static final Tier BRONZE = new Tier("dwarven_bronze", 13, 1, 3, 4, 2, 8, 0.0F, ItemTags.REPAIRS_COPPER_ARMOR);
	public static final Tier STEEL = new Tier("dwarven_steel", 20, 2, 5, 6, 2, 9, 0.0F, ItemTags.REPAIRS_IRON_ARMOR);
	public static final Tier DWARVEN = new Tier("dwarven", 28, 2, 5, 7, 3, 10, 1.0F, ItemTags.REPAIRS_IRON_ARMOR);
	public static final List<Tier> TIERS = List.of(BRONZE, STEEL, DWARVEN);

	public static class Tier {
		public final String name;
		public final Item helmet, chestplate, leggings, boots;

		Tier(String name, int durability, int boots, int legs, int chest, int helmet, int enchant, float toughness, TagKey<Item> repair) {
			this.name = name;
			ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, DwarfMod.id(name));
			ArmorMaterial material = new ArmorMaterial(
				durability,
				Map.of(ArmorType.BOOTS, boots, ArmorType.LEGGINGS, legs, ArmorType.CHESTPLATE, chest, ArmorType.HELMET, helmet, ArmorType.BODY, chest),
				enchant, SoundEvents.ARMOR_EQUIP_IRON, toughness, 0.0F, repair, asset
			);
			this.helmet = armor(name + "_helmet", material, ArmorType.HELMET);
			this.chestplate = armor(name + "_chestplate", material, ArmorType.CHESTPLATE);
			this.leggings = armor(name + "_leggings", material, ArmorType.LEGGINGS);
			this.boots = armor(name + "_boots", material, ArmorType.BOOTS);
		}

		public Item[] pieces() {
			return new Item[] {helmet, chestplate, leggings, boots};
		}
	}

	// Between diamond (1561 / 8.0 / +3) and netherite (2031 / 9.0 / +4).
	public static final ToolMaterial DWARVEN_TOOLS = new ToolMaterial(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1850, 8.6F, 3.5F, 12, ItemTags.DIAMOND_TOOL_MATERIALS);

	public static final Item DWARVEN_SWORD = tool("dwarven_sword", p -> p.sword(DWARVEN_TOOLS, 3.0F, -2.4F));
	public static final Item DWARVEN_SHOVEL = tool("dwarven_shovel", p -> p.shovel(DWARVEN_TOOLS, 1.5F, -3.0F));
	public static final Item DWARVEN_PICKAXE = tool("dwarven_pickaxe", p -> p.pickaxe(DWARVEN_TOOLS, 1.0F, -2.8F));
	public static final Item DWARVEN_AXE = tool("dwarven_axe", p -> p.axe(DWARVEN_TOOLS, 5.0F, -3.0F));
	public static final Item DWARVEN_HOE = tool("dwarven_hoe", p -> p.hoe(DWARVEN_TOOLS, -3.0F, 0.0F));

	public static final Item ALE = brew("dwarven_ale", new MobEffectInstance(MobEffects.RESISTANCE, 3600, 0));
	public static final Item MEAD = brew("dwarven_mead", new MobEffectInstance(MobEffects.HASTE, 3600, 1));
	public static final Item STOUT = brew("dwarven_stout", new MobEffectInstance(MobEffects.STRENGTH, 3600, 0));
	public static final Item SPIRIT = brew("dwarven_spirit",
		new MobEffectInstance(MobEffects.RESISTANCE, 2400, 1), new MobEffectInstance(MobEffects.STRENGTH, 2400, 0), new MobEffectInstance(MobEffects.HASTE, 2400, 1));

	private static Item brew(String name, MobEffectInstance... effects) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, DwarfMod.id(name));
		Item.Properties props = new Item.Properties().setId(key).stacksTo(16).component(DataComponents.CONSUMABLE,
			Consumables.defaultDrink().onConsume(new ApplyStatusEffectsConsumeEffect(List.of(effects))).build());
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(props));
	}

	private static Item tool(String name, java.util.function.UnaryOperator<Item.Properties> setup) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, DwarfMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(setup.apply(new Item.Properties().setId(key))));
	}

	private static Item armor(String name, ArmorMaterial material, ArmorType type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, DwarfMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).humanoidArmor(material, type)));
	}

	private static Block registerBlock(String name, Block block) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, DwarfMod.id(name));
		// In newer mappings, sometimes we need to set the id on properties if we were using a builder, 
		// but block constructor takes properties directly.
		return Registry.register(BuiltInRegistries.BLOCK, key, block);
	}

	private static Item registerBlockItem(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, DwarfMod.id(name));
		Item.Properties props = new Item.Properties().setId(key).useBlockDescriptionPrefix();
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, props));
	}

	public static final Block MINER_BANNER_BLOCK = registerBlock("miner_banner", new MinerBannerBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, DwarfMod.id("miner_banner"))).mapColor(MapColor.COLOR_RED).strength(1.0F).sound(SoundType.WOOD).noOcclusion()));
	public static final Item MINER_BANNER = registerBlockItem("miner_banner", MINER_BANNER_BLOCK);

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(entries -> {
			entries.accept(ALE);
			entries.accept(MEAD);
			entries.accept(STOUT);
			entries.accept(SPIRIT);
			entries.accept(DWARVEN_SWORD);
			entries.accept(DWARVEN_SHOVEL);
			entries.accept(DWARVEN_PICKAXE);
			entries.accept(DWARVEN_AXE);
			entries.accept(DWARVEN_HOE);
			entries.accept(MINER_BANNER);
			for (Tier tier : TIERS) {
				for (Item piece : tier.pieces()) {
					entries.accept(piece);
				}
			}
		});
	}

	/** Ticks a dwarf needs to break a block of hardness 1.5 (stone) with the given tool; iron = 25, diamond 30% faster. */
	public static int miningTicks(net.minecraft.world.item.ItemStack tool) {
		if (tool.isEmpty()) return 100;
		if (tool.is(DWARVEN_PICKAXE)) return 11;
		if (tool.is(net.minecraft.world.item.Items.NETHERITE_PICKAXE)) return 14;
		if (tool.is(net.minecraft.world.item.Items.DIAMOND_PICKAXE)) return 17;
		if (tool.is(net.minecraft.world.item.Items.IRON_PICKAXE)) return 25;
		if (tool.is(net.minecraft.world.item.Items.GOLDEN_PICKAXE)) return 40;
		if (tool.is(net.minecraft.world.item.Items.STONE_PICKAXE)) return 35;
		if (tool.is(net.minecraft.world.item.Items.WOODEN_PICKAXE)) return 50;
		return 100;
	}
}
