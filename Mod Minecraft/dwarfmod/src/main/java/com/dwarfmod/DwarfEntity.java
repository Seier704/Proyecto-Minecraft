package com.dwarfmod;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
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
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
 * Dwarf villager with four roles (blacksmith, brewer, miner, warrior). Like a villager it trades and levels up; on top of that
 * each player has a friendship with each dwarf that changes prices and unlocks following and (miner) hiring.
 * The warrior guards the village bell, keeps watch at night and fights with a two-handed greataxe. Warriors spawn
 * as tier 1 (copper, 60%), 2 (steel, 30%) or 3 (veteran, 10%); the tier sets their gear, health, armor and damage.
 * Rendered with GeckoLib: model and animations come from art/make_dwarf_geo.py.
 */
public class DwarfEntity extends AbstractVillager implements GeoEntity {
	public static final int BLACKSMITH = 0;
	public static final int BREWER = 1;
	public static final int MINER = 2;
	public static final int WARRIOR = 3;
	public static final int ROLES = 4;
	private static final Identifier WARRIOR_HEALTH = DwarfMod.id("warrior_health");
	private static final Identifier WARRIOR_DAMAGE = DwarfMod.id("warrior_damage");
	private static final Identifier WARRIOR_ARMOR = DwarfMod.id("warrior_armor");

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
	// Synced so the client shows the warrior's gear for his tier.
	private static final EntityDataAccessor<Integer> DATA_TIER = SynchedEntityData.defineId(DwarfEntity.class, EntityDataSerializers.INT);
	// Warrior tiers 1..3: bonus health, armor and attack damage (base 20 / 0 / 5); the axe hits harder each tier.
	private static final double[] TIER_HEALTH = {10.0, 20.0, 30.0};
	private static final double[] TIER_ARMOR = {4.0, 8.0, 12.0};
	private static final double[] TIER_DAMAGE = {2.0, 4.0, 7.0};
	private static final int[] TIER_SPAWN_WEIGHT = {60, 30, 10};

	public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.dwarf.idle");
	public static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.dwarf.walk");
	public static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.dwarf.attack");
	public static final RawAnimation IDLE_AXE = RawAnimation.begin().thenLoop("animation.dwarf.idle_axe");
	public static final RawAnimation WALK_AXE = RawAnimation.begin().thenLoop("animation.dwarf.walk_axe");
	public static final RawAnimation ATTACK_AXE = RawAnimation.begin().thenPlay("animation.dwarf.attack_axe");
	/** Ticks from the start of attack_axe to the moment the blade lands (0.55 s in the animation). */
	private static final int AXE_IMPACT_TICKS = 11;
	private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

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
	private ItemStack workTool = ItemStack.EMPTY;

	public ItemStack getWorkTool() {
		return this.workTool;
	}

	public void setWorkTool(ItemStack tool) {
		this.workTool = tool;
	}

	public boolean isInventoryFull() {
		var inv = this.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).isEmpty()) {
				return false;
			}
		}
		return true;
	}

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
		builder.define(DATA_TIER, 1);
	}

	public int getRole() {
		return this.entityData.get(DATA_ROLE);
	}

	public void setRole(int role) {
		this.entityData.set(DATA_ROLE, Math.floorMod(role, ROLES));
		this.applyWarriorStats();
	}

	public int getWarriorTier() {
		return this.entityData.get(DATA_TIER);
	}

	public void setWarriorTier(int tier) {
		this.entityData.set(DATA_TIER, Math.max(1, Math.min(3, tier)));
		this.applyWarriorStats();
	}

	/** Village spawn roll for a new warrior: 60% tier 1, 30% tier 2, 10% tier 3. */
	public static int rollWarriorTier(net.minecraft.util.RandomSource random) {
		int roll = random.nextInt(100);
		for (int i = 0; i < TIER_SPAWN_WEIGHT.length; i++) {
			roll -= TIER_SPAWN_WEIGHT[i];
			if (roll < 0) {
				return i + 1;
			}
		}
		return 1;
	}

	private void applyWarriorStats() {
		boolean warrior = this.getRole() == WARRIOR;
		int t = this.getWarriorTier() - 1;
		this.setRoleBonus(Attributes.MAX_HEALTH, WARRIOR_HEALTH, warrior ? TIER_HEALTH[t] : 0.0);
		this.setRoleBonus(Attributes.ATTACK_DAMAGE, WARRIOR_DAMAGE, warrior ? TIER_DAMAGE[t] : 0.0);
		this.setRoleBonus(Attributes.ARMOR, WARRIOR_ARMOR, warrior ? TIER_ARMOR[t] : 0.0);
	}

	// Permanent modifiers are saved with the attributes, so health above 20 survives a reload.
	private void setRoleBonus(Holder<Attribute> attribute, Identifier id, double amount) {
		AttributeInstance instance = this.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (amount == 0.0) {
			instance.removeModifier(id);
		} else {
			instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	public boolean isWorking() {
		return this.entityData.get(DATA_WORKING);
	}

	public void setWorking(boolean working) {
		this.entityData.set(DATA_WORKING, working);
	}

	// ---- GeckoLib ----

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<DwarfEntity>("base", 5, test -> {
			boolean axe = this.getRole() == WARRIOR;
			return test.setAndContinue(test.isMoving() ? (axe ? WALK_AXE : WALK) : (axe ? IDLE_AXE : IDLE));
		}));
		controllers.add(new AnimationController<DwarfEntity>("action", 2, test -> PlayState.STOP)
			.triggerableAnim("attack", ATTACK)
			.triggerableAnim("attack_axe", ATTACK_AXE));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.geoCache;
	}

	/** One-handed swing (workers hitting or mining); synced to watching clients by GeckoLib. */
	public void playSwingAnimation() {
		this.triggerAnim("action", "attack");
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
			this.axeSwing = 0;
		}

		// Ticks left until a started two-handed swing lands (warriors only).
		private int axeSwing;

		@Override
		protected void checkAndPerformAttack(LivingEntity target) {
			if (this.dwarf.getRole() != WARRIOR) {
				if (this.canPerformAttack(target)) {
					this.dwarf.playSwingAnimation();
				}
				super.checkAndPerformAttack(target);
			} else if (this.axeSwing == 0 && this.canPerformAttack(target)) {
				this.resetAttackCooldown();
				this.axeSwing = AXE_IMPACT_TICKS;
				this.dwarf.triggerAnim("action", "attack_axe");
			}
		}

		@Override
		protected int getAttackInterval() {
			return this.dwarf.getRole() == WARRIOR ? this.adjustedTickDelay(24) : super.getAttackInterval();
		}

		private void landAxeSwing() {
			if (this.axeSwing > 0 && --this.axeSwing == 0) {
				LivingEntity target = this.dwarf.getTarget();
				if (target != null && target.isAlive() && this.dwarf.isWithinMeleeAttackRange(target)
					&& this.dwarf.level() instanceof ServerLevel serverLevel) {
					this.dwarf.doHurtTarget(serverLevel, target);
				}
			}
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
			this.landAxeSwing();
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
			// Warriors keep watch at night instead of going to bed.
			if (!isNight() || busy() || DwarfEntity.this.getRole() == WARRIOR || DwarfEntity.this.tickCount % 20 != 0) {
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

		if (!player.isShiftKeyDown() && this.getRole() == MINER && this.followUuid != null && isPickaxe(held)) {
			ItemStack old = this.workTool;
			this.workTool = held.copyWithCount(1);
			held.shrink(1);
			if (!old.isEmpty() && !player.getInventory().add(old)) {
				this.spawnAtLocation((ServerLevel) this.level(), old);
			}
			this.playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.0F, 1.0F);
			player.sendOverlayMessage(Component.translatable("dwarfmod.tool.given", this.getDisplayName(), this.workTool.getHoverName()));
			return InteractionResult.SUCCESS;
		}

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

	public static boolean isPickaxe(ItemStack stack) {
		return stack.is(net.minecraft.tags.ItemTags.PICKAXES) || stack.is(ModItems.DWARVEN_PICKAXE);
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
		if (!fighting && this.isWorking()) {
			if (this.getMainHandItem() != this.workTool) {
				this.setItemSlot(EquipmentSlot.MAINHAND, this.workTool);
			}
		} else {
			// Warriors carry the greataxe that is part of their model, so they hold no item.
			Item wanted = this.getRole() == WARRIOR ? null : fighting ? Items.IRON_AXE : this.idleItem();
			if (!this.getMainHandItem().is(wanted)) {
				this.setItemSlot(EquipmentSlot.MAINHAND, wanted == null ? ItemStack.EMPTY : new ItemStack(wanted));
			}
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
				Player owner = this.followUuid == null ? null : level.getPlayerByUUID(this.followUuid);
				this.followUuid = null;
				this.setBannerPos(null);
				this.setWorking(false);
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
			case WARRIOR -> s -> s.is(Blocks.BELL);
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
			// The warrior guards the village bell; he never builds one.
			if (this.stationPos == null && this.getRole() != WARRIOR) {
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
			case WARRIOR -> this.warriorTrades(o, lvl);
			default -> this.blacksmithTrades(serverLevel, o, lvl);
		}
	}

	// Buys monster trophies and sells what a fighter needs. His armor is only sold by the blacksmith.
	private void warriorTrades(MerchantOffers o, int lvl) {
		switch (lvl) {
			case 1 -> {
				o.add(buy(Items.ROTTEN_FLESH, 24, 1, 16, 5));
				o.add(sell(Items.SHIELD, 1, 5, 8, 5));
			}
			case 2 -> {
				o.add(buy(Items.BONE, 16, 1, 16, 8));
				o.add(sell(Items.IRON_AXE, 1, 3, 8, 8));
			}
			case 3 -> {
				o.add(buy(Items.GUNPOWDER, 8, 1, 12, 10));
				o.add(sell(ModItems.STOUT, 2, 3, 10, 10));
			}
			case 4 -> {
				o.add(buy(Items.ENDER_PEARL, 2, 3, 8, 15));
				o.add(sell(Items.GOLDEN_APPLE, 1, 10, 4, 15));
			}
			case 5 -> o.add(new MerchantOffer(new ItemCost(Items.EMERALD, 20), Optional.of(new ItemCost(Items.IRON_SWORD, 1)), new ItemStack(ModItems.DWARVEN_AXE), 3, 20, 0.05F));
			default -> {
			}
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
				addWarriorArmor(o, ModItems.WARRIOR_COPPER, null, new int[] {3, 5, 4, 2}, Items.COPPER_INGOT, new int[] {5, 8, 7, 4}, 10);
			}
			case 3 -> {
				o.add(toolForIron(serverLevel, Items.IRON_SWORD, 4, 8, this.randomDamageEnchant()));
				addWarriorArmor(o, ModItems.WARRIOR_STEEL, ModItems.WARRIOR_COPPER, null, Items.EMERALD, new int[] {4, 7, 6, 3}, 15);
			}
			case 5 -> {
				o.add(toolUpgrade(Items.IRON_SHOVEL, ModItems.DWARVEN_SHOVEL, 4));
				o.add(toolUpgrade(Items.IRON_HOE, ModItems.DWARVEN_HOE, 4));
				o.add(toolUpgrade(Items.IRON_AXE, ModItems.DWARVEN_AXE, 6));
				o.add(toolUpgrade(Items.IRON_PICKAXE, ModItems.DWARVEN_PICKAXE, 6));
				o.add(toolUpgrade(Items.IRON_SWORD, ModItems.DWARVEN_SWORD, 6));
				addWarriorArmor(o, ModItems.WARRIOR_VETERAN, ModItems.WARRIOR_STEEL, null, Items.DIAMOND, new int[] {1, 2, 2, 1}, 25);
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

	// The dwarf warrior's sets (copper, steel, veteran): the first costs emeralds + copper, the next ones take
	// the piece of the previous set plus `extra`.
	private static void addWarriorArmor(MerchantOffers o, ModItems.Tier tier, ModItems.@Nullable Tier previous,
			int @Nullable [] emeralds, Item extra, int[] extraCount, int xp) {
		Item[] pieces = tier.pieces();
		for (int i = 0; i < pieces.length; i++) {
			ItemCost first = previous == null ? new ItemCost(Items.EMERALD, emeralds[i]) : new ItemCost(previous.pieces()[i], 1);
			o.add(new MerchantOffer(first, Optional.of(new ItemCost(extra, extraCount[i])), new ItemStack(pieces[i]), 6, xp, 0.05F));
		}
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
		output.putInt("WarriorTier", this.getWarriorTier());
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
		if (!this.workTool.isEmpty()) {
			output.store("WorkTool", ItemStack.CODEC, this.workTool);
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
		this.entityData.set(DATA_TIER, Math.max(1, Math.min(3, input.getIntOr("WarriorTier", 1))));
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
		this.workTool = input.read("WorkTool", ItemStack.CODEC).orElse(ItemStack.EMPTY);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 20.0)
			.add(Attributes.MOVEMENT_SPEED, 0.5)
			.add(Attributes.ATTACK_DAMAGE, 5.0)
			.add(Attributes.FOLLOW_RANGE, 24.0);
	}
}



