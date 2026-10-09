package com.dwarfmod;

import java.util.ArrayDeque;
import java.util.Queue;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DwarfMod implements ModInitializer {
	public static final String MOD_ID = "dwarfmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final String CHECKED_TAG = "dwarfmod_checked_v4";
	private static final int MAX_DWARFS = 3;
	private static final double NEARBY_RADIUS = 64.0;

	public static final ResourceKey<EntityType<?>> DWARF_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("dwarf"));
	public static final EntityType<DwarfEntity> DWARF = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		DWARF_KEY,
		EntityType.Builder.of(DwarfEntity::new, MobCategory.CREATURE).sized(0.7F, 1.4F).clientTrackingRange(10).build(DWARF_KEY)
	);

	public static final BlockEntityType<MinerBannerBlockEntity> MINER_BANNER_BE = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		id("miner_banner"),
		net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(MinerBannerBlockEntity::new, ModItems.MINER_BANNER_BLOCK).build()
	);

	public static final MenuType<MinerBannerMenu> MINER_BANNER_MENU = Registry.register(
		BuiltInRegistries.MENU,
		id("miner_banner"),
		new MenuType<>(MinerBannerMenu::new, FeatureFlags.DEFAULT_FLAGS)
	);

	private final Queue<Villager> pending = new ArrayDeque<>();

	@Override
	public void onInitialize() {
		ModItems.register();
		FabricDefaultAttributeRegistry.register(DWARF, DwarfEntity.createAttributes());

		// Villagers are placed when a village generates; each new adult villager may bring a dwarf along.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof net.minecraft.world.entity.monster.zombie.Zombie || entity instanceof net.minecraft.world.entity.monster.illager.AbstractIllager) {
				net.minecraft.world.entity.Mob mob = (net.minecraft.world.entity.Mob) entity;
				mob.targetSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(mob, DwarfEntity.class, false));
			}
			if (entity instanceof Villager villager && !villager.isBaby() && !villager.entityTags().contains(CHECKED_TAG)) {
				villager.addTag(CHECKED_TAG);
				pending.add(villager);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Villager villager;
			while ((villager = pending.poll()) != null) {
				if (villager.isRemoved() || !(villager.level() instanceof ServerLevel level)) {
					continue;
				}
				trySpawnDwarf(level, villager);
			}
		});
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(DwarfInfoRequest.TYPE, DwarfInfoRequest.CODEC);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(DwarfFollowRequest.TYPE, DwarfFollowRequest.CODEC);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(DwarfBannerActionRequest.TYPE, DwarfBannerActionRequest.CODEC);

		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(DwarfFollowRequest.TYPE, (payload, context) -> {
			var player = context.player();
			if (player.level().getEntity(payload.entityId()) instanceof DwarfEntity dwarf && dwarf.distanceToSqr(player) < 100.0) {
				dwarf.requestFollowToggle(player);
			}
		});
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(DwarfInfoPayload.TYPE, DwarfInfoPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(DwarfInfoRequest.TYPE, (payload, context) -> {
			var player = context.player();
			if (player.level().getEntity(payload.entityId()) instanceof DwarfEntity dwarf && dwarf.distanceToSqr(player) < 100.0) {
				dwarf.sendInfo(player);
			}
		});

		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(DwarfBannerActionRequest.TYPE, (payload, context) -> {
			var player = context.player();
			if (!(player.containerMenu instanceof MinerBannerMenu menu) || menu.getBannerPos() == null) {
				return;
			}
			BlockPos bannerPos = menu.getBannerPos();
			if (payload.action() == 3) { // Refresh the list of hired miners
				sendBannerDwarves(player, bannerPos);
				return;
			}
			if (player.level().getEntity(payload.dwarfId()) instanceof DwarfEntity dwarf && bannerPos.equals(dwarf.getBannerPos())) {
				if (payload.action() == 0) { // Select
					menu.selectedDwarfId = dwarf.getId();
					menu.dwarfInventory.delegate = dwarf.getInventory();
					menu.broadcastChanges(); // Sync slots
				} else if (payload.action() == 1) { // Mine
					dwarf.setWorking(true);
					menu.blockEntity.setMining(true);
				} else if (payload.action() == 2) { // Stop
					dwarf.setWorking(false);
				}
			}
		});
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(BannerConfigRequest.TYPE, BannerConfigRequest.CODEC);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(BannerDwarvesPayload.TYPE, BannerDwarvesPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(BannerConfigRequest.TYPE, (payload, context) -> {
			var player = context.player();
			if (player.containerMenu instanceof MinerBannerMenu menu && menu.blockEntity != null
				&& player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(menu.getBannerPos())) <= 64.0) {
				menu.blockEntity.setMiningMode(payload.mode());
				menu.blockEntity.setMiningDepth(payload.depth());
			}
		});

		// A villager or dwarf hurt by a monster calls the nearby dwarfs.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (!(victim instanceof net.minecraft.world.entity.npc.villager.AbstractVillager) || !(source.getEntity() instanceof net.minecraft.world.entity.monster.Enemy)
				|| !(source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) || !(victim.level() instanceof ServerLevel lvl)) {
				return;
			}
			for (DwarfEntity dwarf : lvl.getEntitiesOfClass(DwarfEntity.class, victim.getBoundingBox().inflate(16.0, 6.0, 16.0))) {
				if (dwarf != victim) {
					dwarf.helpAgainst(attacker);
				}
			}
		});		// Killing a monster that was fighting a dwarf earns that dwarf's trust.
		ServerLivingEntityEvents.AFTER_DEATH.register((dead, source) -> {
			if (!(source.getEntity() instanceof net.minecraft.world.entity.player.Player player) || !(dead instanceof net.minecraft.world.entity.monster.Enemy)
				|| !(dead.level() instanceof ServerLevel lvl)) {
				return;
			}
			for (DwarfEntity dwarf : lvl.getEntitiesOfClass(DwarfEntity.class, dead.getBoundingBox().inflate(10.0))) {
				if (dwarf.getTarget() == dead || dwarf.getLastHurtByMob() == dead) {
					dwarf.addFriendship(player, 4);
				}
			}
		});
		// /dwarf <blacksmith|brewer|miner> spawns a dwarf of that role at the player.
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
			String[] names = {"blacksmith", "brewer", "miner"};
			var root = net.minecraft.commands.Commands.literal("dwarf").requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS));
			for (int r = 0; r < names.length; r++) {
				final int role = r;
				root.then(net.minecraft.commands.Commands.literal(names[r]).executes(ctx -> {
					var src = ctx.getSource();
					DwarfEntity dwarf = DWARF.create(src.getLevel(), EntitySpawnReason.COMMAND);
					if (dwarf == null) {
						return 0;
					}
					dwarf.setRole(role);
					dwarf.snapTo(src.getPosition().x, src.getPosition().y, src.getPosition().z, 0.0F, 0.0F);
					src.getLevel().addFreshEntity(dwarf);
					return 1;
				}));
			}
			dispatcher.register(root);
		});
		LOGGER.info("Dwarf NPC loaded");
	}

	/** Sends the player the hired miners assigned to the banner at {@code bannerPos} (radius 32). */
	private static void sendBannerDwarves(net.minecraft.server.level.ServerPlayer player, BlockPos bannerPos) {
		var list = new java.util.ArrayList<BannerDwarvesPayload.Entry>();
		for (DwarfEntity dwarf : player.level().getEntitiesOfClass(DwarfEntity.class, new AABB(bannerPos).inflate(32.0))) {
			// A miner hired by this player after the banner was placed has no banner yet: adopt it when the menu opens.
			if (dwarf.getRole() == DwarfEntity.MINER && dwarf.getBannerPos() == null
				&& player.getUUID().equals(dwarf.getFollowUuid())) {
				dwarf.setBannerPos(bannerPos);
			}
			if (dwarf.getRole() == DwarfEntity.MINER && bannerPos.equals(dwarf.getBannerPos())) {
				list.add(new BannerDwarvesPayload.Entry(dwarf.getId(), dwarf.isWorking(), dwarf.isInventoryFull(), dwarf.getWorkTool().copy()));
			}
		}
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new BannerDwarvesPayload(list));
	}

	private static boolean isDwarfBiome(Holder<Biome> biome) {
		return biome.is(BiomeTags.IS_MOUNTAIN)
			|| biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.ICE_SPIKES) || biome.is(Biomes.SNOWY_TAIGA)
			|| biome.is(Biomes.SNOWY_BEACH) || biome.is(Biomes.FROZEN_RIVER)
			|| biome.is(Biomes.FROZEN_OCEAN) || biome.is(Biomes.DEEP_FROZEN_OCEAN);
	}

	private void trySpawnDwarf(ServerLevel level, Villager villager) {
		if (!isDwarfBiome(level.getBiome(villager.blockPosition()))) {
			return;
		}
		StructureStart village = level.structureManager().getStructureWithPieceAt(villager.blockPosition(), StructureTags.VILLAGE);
		if (!village.isValid()) {
			return;
		}
		// 1 to 3 dwarfs per village, fixed by the world seed and the village start chunk.
		long hash = level.getSeed() ^ (village.getChunkPos().x() * 341873128712L) ^ (village.getChunkPos().z() * 132897987541L);
		int target = 1 + new java.util.Random(hash).nextInt(MAX_DWARFS);
		int existing = level.getEntitiesOfClass(DwarfEntity.class, AABB.of(village.getBoundingBox()).inflate(NEARBY_RADIUS)).size();
		BlockPos pos = villager.blockPosition();
		for (int i = existing; i < target; i++) {
			DwarfEntity dwarf = DWARF.create(level, EntitySpawnReason.STRUCTURE);
			if (dwarf == null) {
				return;
			}
			dwarf.setRole(i);
			dwarf.snapTo(pos.getX() + 0.5 + (i - existing), pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
			level.addFreshEntity(dwarf);
			LOGGER.info("Dwarf spawned in village at {}", pos);
		}
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}











