package com.dwarfmod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.GolemRandomStrollInVillageGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Dwarf villager with three roles (blacksmith, brewer, miner). Like a villager it trades and levels up; on top of that
 * each player has a friendship with each dwarf that changes prices and unlocks following and (miner) hiring.
 */
public class DwarfEntity extends AbstractVillager {
	public static final int BLACKSMITH = 0;
	public static final int BREWER = 1;
	public static final int MINER = 2;
	public static final int ROLES = 3;

	// Friendship points needed for each stage: Distrustful, Acquaintance, Friend, Trusted, Ally.
	public static final int[] FRIENDSHIP_STEPS = {0, 15, 50, 110, 200};
	public static final int FRIEND = 2;
	public static final int TRUSTED = 3;
	private static final float[] PRICE_FACTOR = {0.25F, 0.0F, -0.10F, -0.20F, -0.30F};
	private static final int HIRE_EMERALDS = 8;
	private static final int HIRE_TICKS = 24000;
	private static final int MINE_INTERVAL = 400;
	private static final int GIFT_COOLDOWN = 1200;

	private static final EntityDataAccessor<Integer> DATA_ROLE = SynchedEntityData.defineId(DwarfEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_WORKING = SynchedEntityData.defineId(DwarfEntity.class, EntityDataSerializers.BOOLEAN);

	private static final int SEARCH_XZ = 24;
	private static final int SEARCH_Y = 6;
	private static final int HOME_RADIUS = 16;
	private static final int RESTOCK_INTERVAL = 6000;

	private int level = 1;
	private int xp = 0;
	private @Nullable BlockPos stationPos;
	private final Map<UUID, Integer> friendship = new HashMap<>();
	private @Nullable UUID followUuid;
	private int hiredTicks = 0;
	private int giftCooldown = 0;
	private int damageEnchantRoll = -1;
	private @Nullable BlockPos bannerPos;

	public @Nullable BlockPos getBannerPos() {
		return this.bannerPos;
	}

	public void setBannerPos(@Nullable BlockPos pos) {
		this.bannerPos = pos;
		if (pos != null) {
			this.clearHome();
			this.setHomeTo(pos, 8);
		}
	}

	public @Nullable UUID getFollowUuid() {
		return this.followUuid;
	}

	public DwarfEntity(EntityType<? extends DwarfEntity> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		this.getNavigation().setCanOpenDoors(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ROLE, BLACKSMITH);
		builder.define(DATA_WORKING, false);
	}

	public int getRole() {
		return this.entityData.get(DATA_ROLE);
	}

	public void setRole(int role) {
		this.entityData.set(DATA_ROLE, Math.floorMod(role, ROLES));
	}

	public boolean isWorking() {
		return this.entityData.get(DATA_WORKING);
	}

	public void setWorking(boolean working) {
		this.entityData.set(DATA_WORKING, working);
	}

	@Override
	protected Component getTypeName() {
		return Component.translatable("entity.dwarfmod.dwarf." + this.getRole());
	}

	// Guard-style behaviour: fight what threatens the village, otherwise patrol around the workstation.
	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(0, new TradeWithPlayerGoal(this));
		this.goalSelector.addGoal(0, new LookAtTradingPlayerGoal(this));
		this.goalSelector.addGoal(1, new DwarfMeleeGoal(this, 1.0));
		this.goalSelector.addGoal(1, new DwarfDepositGoal(this, 1.0));
		this.goalSelector.addGoal(2, new DwarfMineBlockGoal(this, 1.0));
		this.goalSelector.addGoal(3, new FollowOwnerGoal());
		this.goalSelector.addGoal(4, new SleepAtNightGoal());
		this.goalSelector.addGoal(4, new OpenDoorGoal(this, true));
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.5));
		this.goalSelector.addGoal(5, new MoveThroughVillageGoal(this, 0.5, false, 4, () -> false));
		this.goalSelector.addGoal(5, new GolemRandomStrollInVillageGoal(this, 0.5));
		this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.5));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, DwarfEntity.class).setAlertOthers(DwarfEntity.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false, (target, level) -> target instanceof Enemy));
	}

	// Closes in, and backs away a step when the enemy gets too close, like the guard villagers do.
	private static class DwarfMeleeGoal extends MeleeAttackGoal {
		private final DwarfEntity dwarf;

		DwarfMeleeGoal(DwarfEntity dwarf, double speed) {
			super(dwarf, speed, true);
			this.dwarf = dwarf;
		}

		@Override
		public void start() {
			super.start();
			this.dwarf.setAggressive(true);
		}

		@Override
		public void stop() {
			super.stop();
			this.dwarf.setAggressive(false);
		}

		@Override
		public void tick() {
			LivingEntity target = this.dwarf.getTarget();
			if (target != null) {
				if (target.distanceTo(this.dwarf) <= 2.0F) {
					this.dwarf.getMoveControl().strafe(-1.0F, 0.0F);
					this.dwarf.lookAt(target, 30.0F, 30.0F);
				}
				super.tick();
			}
		}
	}

	/** Called when another villager or dwarf is hurt by a monster: nearby dwarfs jump in. */
	public void helpAgainst(LivingEntity attacker) {
		if (this.getTarget() == null && !this.isSleeping() && attacker.isAlive() && this.followUuid == null) {
			this.setTarget(attacker);
		}
	}
	private class FollowOwnerGoal extends Goal {
		private int recalc;

		FollowOwnerGoal() {
			this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE));
		}

		private @Nullable Player owner() {
			return followUuid == null ? null : DwarfEntity.this.level().getPlayerByUUID(followUuid);
		}

		@Override
		public boolean canUse() {
			if (DwarfEntity.this.bannerPos != null) return false;
			Player p = owner();
			return p != null && DwarfEntity.this.distanceToSqr(p) > 16.0 && !DwarfEntity.this.isTrading();
		}

		@Override
		public boolean canContinueToUse() {
			if (DwarfEntity.this.bannerPos != null) return false;
			Player p = owner();
			return p != null && DwarfEntity.this.distanceToSqr(p) > 6.25 && !DwarfEntity.this.isTrading();
		}

		@Override
		public void tick() {
			Player p = owner();
			if (p == null) {
				return;
			}
			if (DwarfEntity.this.distanceToSqr(p) > 400.0) {
				DwarfEntity.this.teleportTo(p.getX(), p.getY(), p.getZ());
				DwarfEntity.this.getNavigation().stop();
			} else if (--recalc <= 0) {
				recalc = 10;
				DwarfEntity.this.getNavigation().moveTo(p, 1.0);
			}
		}
	}

	private class SleepAtNightGoal extends Goal {
		private @Nullable BlockPos bed;

		SleepAtNightGoal() {
			this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
		}

		private boolean isNight() {
			long t = DwarfEntity.this.level().getOverworldClockTime() % 24000L;
			return t >= 12600L && t < 23000L;
		}

		private boolean busy() {
			return followUuid != null || DwarfEntity.this.isTrading() || DwarfEntity.this.getTarget() != null;
		}

		@Override
		public boolean canUse() {
			if (!isNight() || busy() || DwarfEntity.this.tickCount % 20 != 0) {
				return false;
			}
			this.bed = findBed();
			return this.bed != null;
		}

		@Override
		public boolean canContinueToUse() {
			return isNight() && !busy() && this.bed != null && DwarfEntity.this.level().getBlockState(this.bed).is(BlockTags.BEDS);
		}

		@Override
		public void start() {
			DwarfEntity.this.getNavigation().moveTo(this.bed.getX() + 0.5, this.bed.getY(), this.bed.getZ() + 0.5, 0.5);
		}

		@Override
		public void tick() {
			if (DwarfEntity.this.isSleeping()) {
				return;
			}
			if (DwarfEntity.this.distanceToSqr(this.bed.getX() + 0.5, this.bed.getY(), this.bed.getZ() + 0.5) < 3.0) {
				DwarfEntity.this.startSleeping(this.bed);
				DwarfEntity.this.getNavigation().stop();
			} else if (DwarfEntity.this.getNavigation().isDone()) {
				DwarfEntity.this.getNavigation().moveTo(this.bed.getX() + 0.5, this.bed.getY(), this.bed.getZ() + 0.5, 0.5);
			}
		}

		@Override
		public void stop() {
			if (DwarfEntity.this.isSleeping()) {
				DwarfEntity.this.stopSleeping();
			}
			this.bed = null;
		}

		private @Nullable BlockPos findBed() {
			BlockPos origin = DwarfEntity.this.blockPosition();
			BlockPos best = null;
			double bestDist = Double.MAX_VALUE;
			for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-32, -6, -32), origin.offset(32, 6, 32))) {
				var state = DwarfEntity.this.level().getBlockState(pos);
				if (state.is(BlockTags.BEDS) && !state.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED)) {
					double d = pos.distSqr(origin);
					if (d < bestDist) {
						bestDist = d;
						best = pos.immutable();
					}
				}
			}
			return best;
		}
	}

	// ---- friendship ----

	public int friendshipPoints(UUID player) {
		return this.friendship.getOrDefault(player, 0);
	}

	public static int friendshipStage(int points) {
		int stage = 0;
		for (int i = 0; i < FRIENDSHIP_STEPS.length; i++) {
			if (points >= FRIENDSHIP_STEPS[i]) {
				stage = i;
			}
		}
		return stage;
	}

	public void addFriendship(Player player, int amount) {
		int before = friendshipStage(friendshipPoints(player.getUUID()));
		this.friendship.merge(player.getUUID(), amount, Integer::sum);
		int after = friendshipStage(friendshipPoints(player.getUUID()));
		if (after > before && player instanceof net.minecraft.server.level.ServerPlayer sp) {
			sp.sendSystemMessage(Component.translatable("dwarfmod.levelup", this.getDisplayName(), Component.translatable("dwarfmod.friendship." + after)));
			this.makeSound(SoundEvents.VILLAGER_CELEBRATE);
		}
	}

	public void sendInfo(net.minecraft.server.level.ServerPlayer player) {
		int pts = friendshipPoints(player.getUUID());
		int stage = friendshipStage(pts);
		int nextPts = stage >= FRIENDSHIP_STEPS.length - 1 ? -1 : FRIENDSHIP_STEPS[stage + 1];
		int nextXp = VillagerData.canLevelUp(this.level) ? VillagerData.getMaxXpPerLevel(this.level) : -1;
		int hire = 0;
		int hireMinutes = 0;
		if (this.getRole() == MINER) {
			if (this.hiredTicks > 0) {
				hire = 3;
				hireMinutes = (this.hiredTicks + 1199) / 1200;
			} else {
				hire = stage >= TRUSTED ? 2 : 1;
			}
		}
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new DwarfInfoPayload(new int[] {
			this.getId(), this.getRole(), this.level, this.xp, nextXp, pts, stage, nextPts,
			Math.round(PRICE_FACTOR[stage] * 100.0F), stage >= FRIEND ? 1 : 0, hire, hireMinutes, HIRE_EMERALDS, this.followUuid != null ? 1 : 0}));
	}
	private void showStatus(Player player) {
		int pts = friendshipPoints(player.getUUID());
		int stage = friendshipStage(pts);
		String progress = stage >= FRIENDSHIP_STEPS.length - 1 ? "MAX" : pts + "/" + FRIENDSHIP_STEPS[stage + 1];
		player.sendOverlayMessage(Component.translatable("dwarfmod.status", this.getDisplayName(), Component.translatable("dwarfmod.friendship." + stage), progress));
	}

	private static int giftValue(ItemStack s) {
		if (s.is(Items.DIAMOND)) return 8;
		if (s.is(Items.GOLDEN_APPLE)) return 6;
		if (s.is(Items.GOLD_INGOT)) return 3;
		if (s.is(ModItems.ALE) || s.is(ModItems.MEAD) || s.is(ModItems.STOUT)) return 5;
		if (s.is(ModItems.SPIRIT)) return 10;
		if (s.is(Items.COOKED_BEEF) || s.is(Items.COOKED_PORKCHOP) || s.is(Items.BREAD)) return 1;
		return 0;
	}

	private void applyFriendshipPrices(Player player) {
		int stage = friendshipStage(friendshipPoints(player.getUUID()));
		float factor = PRICE_FACTOR[stage];
		for (MerchantOffer offer : this.getOffers()) {
			offer.resetSpecialPriceDiff();
			int base = offer.getBaseCostA().getCount();
			int diff = (int) Math.ceil(base * Math.abs(factor));
			offer.addToSpecialPriceDiff(factor < 0 ? -diff : diff);
		}
	}

	// ---- interaction ----

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (held.is(Items.VILLAGER_SPAWN_EGG) || !this.isAlive() || this.isTrading() || this.isBaby()) {
			return super.mobInteract(player, hand);
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (this.level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		int stage = friendshipStage(friendshipPoints(player.getUUID()));

		if (player.isShiftKeyDown()) {
			if (held.is(Items.EMERALD) && this.getRole() == MINER) {
				return this.tryHire(player, held, stage);
			}
			int gift = giftValue(held);
			if (gift > 0) {
				if (this.giftCooldown > 0) {
					player.sendOverlayMessage(Component.translatable("dwarfmod.gift.wait", this.getDisplayName()));
				} else {
					held.shrink(1);
					this.giftCooldown = GIFT_COOLDOWN;
					this.addFriendship(player, gift);
					player.sendOverlayMessage(Component.translatable("dwarfmod.gift.ok", this.getDisplayName()));
					this.makeSound(SoundEvents.VILLAGER_YES);
				}
				return InteractionResult.SUCCESS;
			}
			if (held.isEmpty()) {
				return this.toggleFollow(player, stage);
			}
		}

		this.showStatus(player);
		if (this.getOffers().isEmpty()) {
			return InteractionResult.CONSUME;
		}
		this.applyFriendshipPrices(player);
		this.setTradingPlayer(player);
		this.openTradingScreen(player, this.getDisplayName(), this.level);
		return InteractionResult.SUCCESS;
	}

	/** Follow button of the info screen; only the player a dwarf already follows can dismiss it. */
	public void requestFollowToggle(net.minecraft.server.level.ServerPlayer player) {
		if (this.followUuid == null || this.followUuid.equals(player.getUUID())) {
			this.toggleFollow(player, friendshipStage(friendshipPoints(player.getUUID())));
		}
		this.sendInfo(player);
	}

	private InteractionResult toggleFollow(Player player, int stage) {

		if (stage < FRIEND) {
			player.sendOverlayMessage(Component.translatable("dwarfmod.follow.locked", this.getDisplayName()));
			return InteractionResult.SUCCESS;
		}
		if (this.followUuid != null) {
			this.followUuid = null;
			this.hiredTicks = 0;
			player.sendOverlayMessage(Component.translatable("dwarfmod.follow.off", this.getDisplayName()));
		} else {
			this.followUuid = player.getUUID();
			this.clearHome();
			player.sendOverlayMessage(Component.translatable("dwarfmod.follow.on", this.getDisplayName()));
		}
		return InteractionResult.SUCCESS;
	}

	private InteractionResult tryHire(Player player, ItemStack held, int stage) {
		if (stage < TRUSTED) {
			player.sendOverlayMessage(Component.translatable("dwarfmod.hire.locked", this.getDisplayName()));
		} else if (held.getCount() < HIRE_EMERALDS) {
			player.sendOverlayMessage(Component.translatable("dwarfmod.hire.cost"));
		} else {
			held.shrink(HIRE_EMERALDS);
			this.hiredTicks = HIRE_TICKS;
			this.followUuid = player.getUUID();
			this.clearHome();
			this.addFriendship(player, 2);
			player.sendOverlayMessage(Component.translatable("dwarfmod.hire.ok", this.getDisplayName()));
		}
		return InteractionResult.SUCCESS;
	}

	// ---- server tick ----

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.giftCooldown > 0) {
			this.giftCooldown--;
		}
		boolean fighting = this.getTarget() != null && this.getTarget().isAlive();
		Item wanted = fighting ? Items.IRON_AXE : this.idleItem();
		if (!this.getMainHandItem().is(wanted)) {
			this.setItemSlot(EquipmentSlot.MAINHAND, wanted == null ? ItemStack.EMPTY : new ItemStack(wanted));
		}

		if (this.followUuid == null && this.tickCount % 100 == 20) {
			this.updateStation(level);
		}
		if (this.tickCount % RESTOCK_INTERVAL == 0 && !this.isTrading() && this.offers != null) {
			this.offers.forEach(MerchantOffer::resetUses);
		}
		if (this.bannerPos != null && this.tickCount % 40 == 0) {
			if (!level.getBlockState(this.bannerPos).is(ModItems.MINER_BANNER_BLOCK)) {
				this.setBannerPos(null);
			}
		}
		if (this.hiredTicks > 0) {
			this.hiredTicks--;
			if (this.hiredTicks == 0) {
				this.followUuid = null;
				this.setBannerPos(null);
				Player owner = level.getPlayerByUUID(this.followUuid);
				if (owner != null) {
					owner.sendOverlayMessage(Component.translatable("dwarfmod.hire.end", this.getDisplayName()));
				}
			}
		}
	}

	private @Nullable Item idleItem() {
		return switch (this.getRole()) {
			case BREWER -> ModItems.ALE;
			case MINER -> Items.IRON_PICKAXE;
			default -> null;
		};
	}

	private Predicate<net.minecraft.world.level.block.state.BlockState> stationMatcher() {
		return switch (this.getRole()) {
			case BREWER -> s -> s.is(Blocks.BARREL);
			case MINER -> s -> s.is(Blocks.BLAST_FURNACE);
			default -> s -> s.is(BlockTags.ANVIL);
		};
	}

	private Block stationBlock() {
		return switch (this.getRole()) {
			case BREWER -> Blocks.BARREL;
			case MINER -> Blocks.BLAST_FURNACE;
			default -> Blocks.ANVIL;
		};
	}

	private void updateStation(ServerLevel level) {
		Predicate<net.minecraft.world.level.block.state.BlockState> matcher = this.stationMatcher();
		if (this.stationPos != null && !matcher.test(level.getBlockState(this.stationPos))) {
			this.stationPos = null;
		}
		if (this.stationPos == null) {
			this.stationPos = this.findStation(level, matcher);
			if (this.stationPos == null) {
				this.stationPos = this.placeStation(level);
			}
		}
		if (this.stationPos != null && (!this.hasHome() || !this.getHomePosition().equals(this.stationPos))) {
			this.setHomeTo(this.stationPos, HOME_RADIUS);
		}
	}

	private @Nullable BlockPos findStation(ServerLevel level, Predicate<net.minecraft.world.level.block.state.BlockState> matcher) {
		BlockPos origin = this.blockPosition();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-SEARCH_XZ, -SEARCH_Y, -SEARCH_XZ), origin.offset(SEARCH_XZ, SEARCH_Y, SEARCH_XZ))) {
			if (level.hasChunkAt(pos) && matcher.test(level.getBlockState(pos))) {
				double dist = pos.distSqr(origin);
				if (dist < bestDist) {
					bestDist = dist;
					best = pos.immutable();
				}
			}
		}
		return best;
	}

	// Villages have no spare workstation, so the dwarf sets one up next to where he stands.
	private @Nullable BlockPos placeStation(ServerLevel level) {
		BlockPos origin = this.blockPosition();
		for (int r = 2; r <= 4; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					BlockPos pos = origin.offset(dx, 0, dz);
					if (level.hasChunkAt(pos) && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
						&& level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
						&& level.getFluidState(pos.below()).isEmpty()) {
						level.setBlockAndUpdate(pos, this.stationBlock().defaultBlockState());
						DwarfMod.LOGGER.info("Dwarf (role {}) placed workstation at {}", this.getRole(), pos);
						return pos;
					}
				}
			}
		}
		return null;
	}

	// ---- trades ----

	@Override
	protected void updateTrades(ServerLevel level) {
		for (int l = 1; l <= this.level; l++) {
			this.addTradesForLevel(level, l);
		}
	}

	private void addTradesForLevel(ServerLevel serverLevel, int lvl) {
		MerchantOffers o = this.getOffers();
		switch (this.getRole()) {
			case BREWER -> this.brewerTrades(o, lvl);
			case MINER -> this.minerTrades(o, lvl);
			default -> this.blacksmithTrades(serverLevel, o, lvl);
		}
	}

	private static MerchantOffer sell(Item item, int count, int emeralds, int uses, int xp) {
		return new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), new ItemStack(item, count), uses, xp, 0.05F);
	}

	private static MerchantOffer buy(Item item, int count, int emeralds, int uses, int xp) {
		return new MerchantOffer(new ItemCost(item, count), new ItemStack(Items.EMERALD, emeralds), uses, xp, 0.05F);
	}

	private void brewerTrades(MerchantOffers o, int lvl) {
		switch (lvl) {
			case 1 -> {
				o.add(sell(ModItems.ALE, 1, 1, 12, 5));
				o.add(buy(Items.WHEAT, 20, 1, 12, 5));
			}
			case 2 -> {
				o.add(sell(ModItems.MEAD, 1, 2, 12, 8));
				o.add(buy(Items.SUGAR, 12, 1, 12, 8));
			}
			case 3 -> {
				o.add(sell(ModItems.STOUT, 1, 3, 10, 10));
				o.add(buy(Items.BREAD, 10, 1, 12, 10));
			}
			case 4 -> {
				o.add(sell(ModItems.ALE, 3, 3, 8, 15));
				o.add(sell(ModItems.MEAD, 3, 5, 8, 15));
			}
			case 5 -> o.add(new MerchantOffer(new ItemCost(Items.EMERALD, 8), Optional.of(new ItemCost(Items.GOLD_INGOT, 2)), new ItemStack(ModItems.SPIRIT), 4, 20, 0.05F));
			default -> {
			}
		}
	}

	private void minerTrades(MerchantOffers o, int lvl) {
		switch (lvl) {
			case 1 -> {
				o.add(buy(Items.COAL, 20, 1, 12, 5));
				o.add(sell(Items.RAW_COPPER, 6, 1, 12, 5));
				o.add(sell(Items.RAW_IRON, 4, 2, 12, 5));
			}
			case 2 -> {
				o.add(sell(Items.RAW_IRON, 8, 3, 12, 8));
				o.add(sell(Items.REDSTONE, 8, 2, 12, 8));
				o.add(sell(Items.LAPIS_LAZULI, 6, 2, 12, 8));
			}
			case 3 -> {
				o.add(sell(Items.RAW_GOLD, 4, 4, 10, 10));
				o.add(sell(Items.AMETHYST_SHARD, 4, 4, 10, 10));
				o.add(buy(Items.IRON_INGOT, 10, 2, 12, 10));
			}
			case 4 -> {
				o.add(sell(Items.QUARTZ, 8, 4, 10, 15));
				o.add(sell(Items.DIAMOND, 1, 14, 3, 15));
			}
			case 5 -> o.add(sell(Items.DIAMOND, 1, 10, 3, 20));
			default -> {
			}
		}
	}

	private void blacksmithTrades(ServerLevel serverLevel, MerchantOffers o, int lvl) {
		switch (lvl) {
			case 1 -> o.add(toolForIron(serverLevel, Items.IRON_SHOVEL, 3, 5, Enchantments.EFFICIENCY));
			case 2 -> {
				o.add(toolForIron(serverLevel, Items.IRON_AXE, 5, 8, Enchantments.EFFICIENCY));
				o.add(toolForIron(serverLevel, Items.IRON_PICKAXE, 6, 8, Enchantments.EFFICIENCY));
				addArmor(o, ModItems.BRONZE, null, new int[] {5, 8, 7, 4}, 10);
			}
			case 3 -> {
				o.add(toolForIron(serverLevel, Items.IRON_SWORD, 4, 8, this.randomDamageEnchant()));
				addArmor(o, ModItems.STEEL, ModItems.BRONZE, new int[] {4, 6, 5, 3}, 15);
			}
			case 4 -> addArmor(o, ModItems.DWARVEN, ModItems.STEEL, new int[] {6, 10, 8, 5}, 20);
			case 5 -> {
				o.add(toolUpgrade(Items.IRON_SHOVEL, ModItems.DWARVEN_SHOVEL, 4));
				o.add(toolUpgrade(Items.IRON_HOE, ModItems.DWARVEN_HOE, 4));
				o.add(toolUpgrade(Items.IRON_AXE, ModItems.DWARVEN_AXE, 6));
				o.add(toolUpgrade(Items.IRON_PICKAXE, ModItems.DWARVEN_PICKAXE, 6));
				o.add(toolUpgrade(Items.IRON_SWORD, ModItems.DWARVEN_SWORD, 6));
			}
			default -> {
			}
		}
	}

	// Same iron price as before; the tool comes with a level 1 enchantment when one is given.
	private static MerchantOffer toolForIron(ServerLevel level, Item tool, int ingots, int xp, ResourceKey<Enchantment> enchant) {
		ItemStack result = new ItemStack(tool);
		result.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchant), 1);
		return new MerchantOffer(new ItemCost(Items.IRON_INGOT, ingots), result, 12, xp, 0.05F);
	}

	private ResourceKey<Enchantment> randomDamageEnchant() {
		List<ResourceKey<Enchantment>> pool = List.of(Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS);
		if (this.damageEnchantRoll < 0) {
			this.damageEnchantRoll = this.random.nextInt(pool.size());
		}
		return pool.get(this.damageEnchantRoll);
	}

	// Hand over the matching iron tool plus emeralds and get the dwarven one (better than diamond, below netherite).
	private static MerchantOffer toolUpgrade(Item from, Item to, int emeralds) {
		return new MerchantOffer(new ItemCost(from, 1), Optional.of(new ItemCost(Items.EMERALD, emeralds)), new ItemStack(to), 6, 20, 0.05F);
	}

	// Tier 1 costs iron only. Later tiers upgrade the piece from the previous tier.
	private static void addArmor(MerchantOffers o, ModItems.Tier tier, ModItems.@Nullable Tier previous, int[] ingots, int xp) {
		Item[] pieces = tier.pieces();
		for (int i = 0; i < pieces.length; i++) {
			ItemStack result = new ItemStack(pieces[i]);
			if (previous == null) {
				o.add(new MerchantOffer(new ItemCost(Items.IRON_INGOT, ingots[i]), result, 8, xp, 0.05F));
			} else {
				o.add(new MerchantOffer(new ItemCost(previous.pieces()[i], 1), Optional.of(new ItemCost(Items.IRON_INGOT, ingots[i])), result, 8, xp, 0.05F));
			}
		}
	}

	@Override
	protected void rewardTradeXp(MerchantOffer offer) {
		Player trader = this.getTradingPlayer();
		if (trader != null) {
			this.addFriendship(trader, 1);
		}
		this.xp += offer.getXp();
		int popXp = 3 + this.random.nextInt(4);
		if (VillagerData.canLevelUp(this.level) && this.xp >= VillagerData.getMaxXpPerLevel(this.level)) {
			if (this.level() instanceof ServerLevel serverLevel) {
				this.level++;
				this.addTradesForLevel(serverLevel, this.level);
				this.makeSound(SoundEvents.VILLAGER_CELEBRATE);
				DwarfMod.LOGGER.info("Dwarf reached level {}", this.level);
			}
			popXp += 5;
		}
		if (offer.shouldRewardExp()) {
			this.level().addFreshEntity(new ExperienceOrb(this.level(), this.getX(), this.getY() + 0.5, this.getZ(), popXp));
		}
	}

	@Override
	public int getVillagerXp() {
		return this.xp;
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	// ---- save data ----

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("DwarfLevel", this.level);
		output.putInt("DwarfXp", this.xp);
		output.putInt("DwarfRole", this.getRole());
		output.putInt("DamageEnchant", this.damageEnchantRoll);
		output.putInt("HiredTicks", this.hiredTicks);
		if (this.followUuid != null) {
			output.putString("FollowUuid", this.followUuid.toString());
		}
		StringBuilder sb = new StringBuilder();
		this.friendship.forEach((id, pts) -> sb.append(id).append(':').append(pts).append(';'));
		output.putString("Friends", sb.toString());
		if (this.stationPos != null) {
			output.store("AnvilPos", BlockPos.CODEC, this.stationPos);
		}
		if (this.bannerPos != null) {
			output.store("BannerPos", BlockPos.CODEC, this.bannerPos);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.level = Math.max(1, input.getIntOr("DwarfLevel", 1));
		this.xp = input.getIntOr("DwarfXp", 0);
		this.setRole(input.getIntOr("DwarfRole", BLACKSMITH));
		this.damageEnchantRoll = input.getIntOr("DamageEnchant", -1);
		this.hiredTicks = input.getIntOr("HiredTicks", 0);
		String follow = input.getStringOr("FollowUuid", "");
		this.followUuid = follow.isEmpty() ? null : UUID.fromString(follow);
		this.friendship.clear();
		for (String entry : input.getStringOr("Friends", "").split(";")) {
			String[] parts = entry.split(":");
			if (parts.length == 2) {
				this.friendship.put(UUID.fromString(parts[0]), Integer.parseInt(parts[1]));
			}
		}
		this.stationPos = input.read("AnvilPos", BlockPos.CODEC).orElse(null);
		this.bannerPos = input.read("BannerPos", BlockPos.CODEC).orElse(null);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 20.0)
			.add(Attributes.MOVEMENT_SPEED, 0.5)
			.add(Attributes.ATTACK_DAMAGE, 5.0)
			.add(Attributes.FOLLOW_RANGE, 24.0);
	}
}



