package com.dwarfmod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class DwarfMineBlockGoal extends Goal {

    private final DwarfEntity dwarf;
    private final double speedModifier;
    private BlockPos targetBlock;
    private int breakingTime;
    private int maxBreakingTime;

    public DwarfMineBlockGoal(DwarfEntity dwarf, double speedModifier) {
        this.dwarf = dwarf;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.dwarf.isWorking()) return false;
        if (this.dwarf.getBannerPos() == null) return false;
        
        Level level = this.dwarf.level();
        if (level.isClientSide()) return false;
        
        BlockEntity be = level.getBlockEntity(this.dwarf.getBannerPos());
        if (!(be instanceof MinerBannerBlockEntity banner) || !banner.isMining()) {
            return false;
        }

        // Find a block to mine
        this.targetBlock = findBlockToMine(banner);
        return this.targetBlock != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.targetBlock != null && this.dwarf.getBannerPos() != null && this.breakingTime < this.maxBreakingTime;
    }

    @Override
    public void start() {
        this.dwarf.getNavigation().moveTo(this.targetBlock.getX(), this.targetBlock.getY() + 1, this.targetBlock.getZ(), this.speedModifier);
        this.breakingTime = 0;
        
        // Calculate max breaking time based on tool
        ItemStack tool = this.dwarf.getMainHandItem();
        if (tool.is(ModItems.DWARVEN_PICKAXE)) {
            this.maxBreakingTime = 40;
        } else if (tool.is(net.minecraft.world.item.Items.DIAMOND_PICKAXE)) {
            this.maxBreakingTime = 60;
        } else {
            this.maxBreakingTime = 100;
        }
    }

    @Override
    public void tick() {
        if (this.targetBlock == null) return;

        double dist = this.dwarf.distanceToSqr(this.targetBlock.getX() + 0.5, this.targetBlock.getY(), this.targetBlock.getZ() + 0.5);
        if (dist > 4.0) {
            this.dwarf.getNavigation().moveTo(this.targetBlock.getX(), this.targetBlock.getY() + 1, this.targetBlock.getZ(), this.speedModifier);
        } else {
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
    }

    private void breakBlock() {
        ServerLevel level = (ServerLevel) this.dwarf.level();
        BlockState state = level.getBlockState(this.targetBlock);
        
        if (!state.isAir() && state.getDestroySpeed(level, this.targetBlock) >= 0) {
            LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(this.targetBlock))
                .withParameter(LootContextParams.TOOL, this.dwarf.getMainHandItem())
                .withOptionalParameter(LootContextParams.THIS_ENTITY, this.dwarf);
                
            for (ItemStack drop : state.getDrops(builder)) {
                ItemStack remainder = this.dwarf.getInventory().addItem(drop);
                if (!remainder.isEmpty()) {
                    level.addFreshEntity(new ItemEntity(level, this.targetBlock.getX() + 0.5, this.targetBlock.getY() + 0.5, this.targetBlock.getZ() + 0.5, remainder));
                }
            }
            level.destroyBlock(this.targetBlock, false, this.dwarf);
        }
        
        this.targetBlock = null;
    }

    private BlockPos findBlockToMine(MinerBannerBlockEntity banner) {
        BlockPos center = banner.getBlockPos();
        int size = banner.getMiningMode(); // 1, 2, 4
        int radius = size == 1 ? 0 : size == 2 ? 1 : 2;
        int depth = banner.getMiningDepth();
        
        Level level = banner.getLevel();
        
        // Scan downwards
        for (int y = center.getY() - 1; y >= center.getY() - depth; y--) {
            for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0 && !state.is(Blocks.BEDROCK)) {
                        // Check if we can safely stand above it or next to it without falling into lava/void
                        if (isSafeToMine(level, pos)) {
                            return pos;
                        }
                    }
                }
            }
        }
        
        // If we reach here, we mined everything or hit an obstacle
        banner.setMining(false);
        return null;
    }
    
    private boolean isSafeToMine(Level level, BlockPos pos) {
        // Just a basic check to prevent mining if the block below is lava or air (to prevent falling)
        // A more advanced check would use pathfinding, but this works for a top-down quarry.
        BlockState below = level.getBlockState(pos.below());
        if (below.isAir() || below.liquid()) {
            return false; // Dangerous
        }
        return true;
    }
}
