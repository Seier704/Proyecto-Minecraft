package com.dwarfmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;

import java.util.EnumSet;

/**
 * Digs a straight tunnel (1x1, 2x2 or 3x3) out from the miner banner, layer by layer up to the banner's depth.
 * When a tunnel is finished or blocked (hole, liquid, unreachable cell) the next front is tried: the other three
 * horizontal directions, then the same four shifted sideways.
 */
public class DwarfMineBlockGoal extends Goal {

    private static final int MAX_SHIFT = 4;
    private static final int STUCK_LIMIT = 200;

    private final DwarfEntity dwarf;
    private final double speedModifier;
    private MinerBannerBlockEntity banner;
    private BlockPos targetBlock;
    private BlockPos standPos;
    private int breakingTime;
    private int maxBreakingTime;
    private int stuckTicks;

    public DwarfMineBlockGoal(DwarfEntity dwarf, double speedModifier) {
        this.dwarf = dwarf;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Level level = this.dwarf.level();
        if (level.isClientSide()) return false;
        boolean log = this.dwarf.tickCount % 100 == 0;
        if (!this.dwarf.isWorking() || this.dwarf.getBannerPos() == null) {
            if (log && this.dwarf.getRole() == DwarfEntity.MINER) DwarfMod.LOGGER.info("[mine] skip: working={} bannerPos={}", this.dwarf.isWorking(), this.dwarf.getBannerPos());
            return false;
        }
        // Inventory full and nowhere to put things: stop, do not litter the floor.
        if (this.dwarf.isInventoryFull()) {
            if (log) DwarfMod.LOGGER.info("[mine] skip: inventory full");
            return false;
        }

        BlockEntity be = level.getBlockEntity(this.dwarf.getBannerPos());
        if (!(be instanceof MinerBannerBlockEntity b) || !b.isMining()) {
            if (log) DwarfMod.LOGGER.info("[mine] skip: banner entity={} mining={}", be, be instanceof MinerBannerBlockEntity bb && bb.isMining());
            return false;
        }

        Target t = findTarget(b);
        if (t == null) {
            if (log) DwarfMod.LOGGER.info("[mine] no target (mode={} depth={} dir={} sky={})", b.getMiningMode(), b.getMiningDepth(), b.getTunnelDir(), level.canSeeSky(b.getBlockPos()));
            finishIfNobodyLeft(b);
            return false;
        }
        if (!b.claim(t.cell)) return false;
        DwarfMod.LOGGER.info("[mine] target {} stand {}", t.cell, t.stand);
        this.banner = b;
        this.targetBlock = t.cell;
        this.standPos = t.stand;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.targetBlock != null && this.banner != null && this.banner.isMining()
            && this.dwarf.isWorking() && !this.dwarf.isInventoryFull();
    }

    @Override
    public void start() {
        this.breakingTime = 0;
        this.stuckTicks = 0;
        ServerLevel level = (ServerLevel) this.dwarf.level();
        BlockState state = level.getBlockState(this.targetBlock);
        float hardness = Math.max(0.6F, Math.min(4.0F, state.getDestroySpeed(level, this.targetBlock) / 1.5F));
        this.maxBreakingTime = Math.round(ModItems.miningTicks(this.dwarf.getWorkTool()) * hardness);
        this.moveToStand();
    }

    @Override
    public void stop() {
        if (this.banner != null && this.targetBlock != null) {
            this.banner.release(this.targetBlock);
        }
        this.targetBlock = null;
        this.banner = null;
    }

    private void moveToStand() {
        this.dwarf.getNavigation().moveTo(this.standPos.getX() + 0.5, this.standPos.getY(), this.standPos.getZ() + 0.5, this.speedModifier);
    }

    @Override
    public void tick() {
        if (this.targetBlock == null) return;

        double dist = this.dwarf.distanceToSqr(this.targetBlock.getX() + 0.5, this.targetBlock.getY() + 0.5, this.targetBlock.getZ() + 0.5);
        if (dist > 9.0) {
            if (this.dwarf.getNavigation().isDone()) moveToStand();
            if (++this.stuckTicks > STUCK_LIMIT) {
                // Cannot get there: treat this cell as a wall for this front.
                this.banner.markUnreachable(this.targetBlock);
                this.banner.release(this.targetBlock);
                this.targetBlock = null;
            }
            return;
        }

        this.stuckTicks = 0;
        this.dwarf.getNavigation().stop();
        this.dwarf.getLookControl().setLookAt(this.targetBlock.getX() + 0.5, this.targetBlock.getY() + 0.5, this.targetBlock.getZ() + 0.5, 10.0F, this.dwarf.getMaxHeadXRot());

        this.breakingTime++;
        if (this.breakingTime % 10 == 0) {
            this.dwarf.level().playSound(null, this.targetBlock, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        if (this.breakingTime >= this.maxBreakingTime) {
            breakBlock();
        }
    }

    private void breakBlock() {
        ServerLevel level = (ServerLevel) this.dwarf.level();
        BlockState state = level.getBlockState(this.targetBlock);

        if (!state.isAir() && state.getDestroySpeed(level, this.targetBlock) >= 0) {
            ItemStack tool = this.dwarf.getMainHandItem();
            LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(this.targetBlock))
                .withParameter(LootContextParams.TOOL, tool)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, this.dwarf);

            for (ItemStack drop : state.getDrops(builder)) {
                ItemStack remainder = this.dwarf.getInventory().addItem(drop);
                if (!remainder.isEmpty()) {
                    // Inventory filled up mid-drop: keep it by dropping only this overflow right here.
                    Vec3 c = Vec3.atCenterOf(this.targetBlock);
                    level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, c.x, c.y, c.z, remainder));
                }
            }
            level.destroyBlock(this.targetBlock, false, this.dwarf);

            if (!tool.isEmpty()) {
                tool.hurtAndBreak(1, this.dwarf, EquipmentSlot.MAINHAND);
                if (tool.isEmpty()) this.dwarf.setWorkTool(ItemStack.EMPTY);
            }
        }

        this.banner.release(this.targetBlock);
        this.targetBlock = null;
    }

    // ---- tunnel geometry ----

    private record Target(BlockPos cell, BlockPos stand) { }

    /** First breakable cell, scanning fronts in order and each front layer by layer; null when everything is done. */
    private Target findTarget(MinerBannerBlockEntity b) {
        Level level = b.getLevel();
        BlockPos origin = b.getBlockPos();
        int size = b.getMiningMode();
        int depth = b.getMiningDepth();
        Direction first = b.getTunnelDir();

        Direction dir = first;
        for (int shift = 0; shift <= MAX_SHIFT; shift++) {
            dir = first;
            for (int i = 0; i < 4; i++) {
                BlockPos base = origin.relative(dir.getClockWise(), shift * (size + 1));
                Target t = scanFront(b, level, base, dir, size, depth);
                if (t != null) return t;
                dir = dir.getClockWise();
            }
        }
        return null;
    }

    private Target scanFront(MinerBannerBlockEntity b, Level level, BlockPos base, Direction dir, int size, int depth) {
        Direction right = dir.getClockWise();
        // Banner in the open: dig the trench one block into the ground instead of through thin air.
        int drop = level.canSeeSky(b.getBlockPos()) ? 1 : 0;
        for (int k = 1; k <= depth; k++) {
            BlockPos stand = k == 1 ? base : base.relative(dir, k - 1).below(drop);
            // The ground the dwarf walks on must exist (also protects against natural pits).
            BlockState floor = level.getBlockState(stand.below());
            if (floor.isAir() || !floor.getFluidState().isEmpty()) return null;

            Target found = null;
            for (int a = 0; a < size && found == null; a++) {
                for (int h = 0; h < size; h++) {
                    BlockPos cell = base.relative(dir, k).below(drop).relative(right, a - (size - 1) / 2).above(h);
                    if (b.isUnreachable(cell)) return null;
                    BlockState st = level.getBlockState(cell);
                    if (!st.getFluidState().isEmpty()) return null;
                    if (st.isAir()) continue;
                    // Never dig up chests, furnaces and other containers (nor the banner itself): they are left alone.
                    if (level.getBlockEntity(cell) != null || st.is(ModItems.MINER_BANNER_BLOCK)) continue;
                    if (st.getDestroySpeed(level, cell) < 0) return null;
                    if (touchesLiquid(level, cell)) return null;
                    if (b.isClaimed(cell)) continue;
                    found = new Target(cell, stand);
                    break;
                }
            }
            if (found != null) return found;
            // Layer cleared (or all cells being worked on by others): move on only if nothing is still claimed here.
            if (layerHasClaims(b, base.below(drop), dir, right, size, k)) return null;
        }
        return null;
    }

    private boolean layerHasClaims(MinerBannerBlockEntity b, BlockPos base, Direction dir, Direction right, int size, int k) {
        for (int a = 0; a < size; a++) {
            for (int h = 0; h < size; h++) {
                if (b.isClaimed(base.relative(dir, k).relative(right, a - (size - 1) / 2).above(h))) return true;
            }
        }
        return false;
    }

    private boolean touchesLiquid(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (!level.getBlockState(pos.relative(d)).getFluidState().isEmpty()) return true;
        }
        return false;
    }

    /** No front left: switch the banner off, release its miners and tell the nearest player. */
    private void finishIfNobodyLeft(MinerBannerBlockEntity b) {
        // Other dwarves may still be breaking claimed cells; only finish once nothing is claimed.
        BlockPos origin = b.getBlockPos();
        Level level = b.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;
        AABB box = new AABB(origin).inflate(32.0D);
        if (b.hasClaims()) return;
        b.setMining(false);
        for (DwarfEntity d : serverLevel.getEntitiesOfClass(DwarfEntity.class, box)) {
            if (origin.equals(d.getBannerPos())) d.setWorking(false);
        }
        Player p = serverLevel.getNearestPlayer(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 48.0D, false);
        if (p != null) p.sendSystemMessage(Component.translatable("dwarfmod.banner.done"));
    }
}
