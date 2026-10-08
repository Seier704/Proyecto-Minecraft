package com.dwarfmod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class MinerBannerBlock extends Block implements EntityBlock {

    public MinerBannerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide() && level instanceof ServerLevel serverLevel && placer instanceof Player player) {
            int assignedCount = 0;
            AABB bounds = new AABB(pos).inflate(32.0D);

            for (DwarfEntity dwarf : serverLevel.getEntitiesOfClass(DwarfEntity.class, bounds)) {
                if (dwarf.getRole() == DwarfEntity.MINER && dwarf.getFollowUuid() != null && dwarf.getFollowUuid().equals(player.getUUID())) {
                    dwarf.setBannerPos(pos);
                    assignedCount++;
                }
            }

            if (assignedCount > 0) {
                player.sendSystemMessage(Component.translatable("dwarfmod.banner.assigned", assignedCount));
            }
        }
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MinerBannerBlockEntity banner) {
                player.openMenu(banner);
            }
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MinerBannerBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : (lvl, pos, st, be) -> {
            if (be instanceof MinerBannerBlockEntity banner) banner.tick();
        };
    }
}
