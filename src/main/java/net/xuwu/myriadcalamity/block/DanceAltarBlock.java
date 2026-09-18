package net.xuwu.myriadcalamity.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.entity.CogworkDancer;
import net.xuwu.myriadcalamity.world.DanceEncounters;
import net.xuwu.myriadcalamity.world.TheatreLayout;

public final class DanceAltarBlock extends Block {
    public DanceAltarBlock(Properties properties) { super(properties); }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        ItemStack stack=player.getItemInHand(hand);
        if(!stack.is(MyriadCalamity.KEY.get())) {
            if(!level.isClientSide)player.displayClientMessage(Component.translatable("message.myriad_calamity.need_key"),true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if(level.isClientSide)return InteractionResult.SUCCESS;
        ServerLevel server=(ServerLevel)level;
        if(level.getDifficulty()==Difficulty.PEACEFUL)return fail(player,"peaceful");
        if(DanceEncounters.get(server).active(pos) || !level.getEntitiesOfClass(CogworkDancer.class,new AABB(pos).inflate(48),CogworkDancer::isAlive).isEmpty())return fail(player,"already_dancing");
        for(int[] seal:TheatreLayout.SEALS) {
            BlockPos at=pos.offset(seal[0],0,seal[1]);
            if(!level.hasChunkAt(at) || !level.getBlockState(at).is(Blocks.LODESTONE))return fail(player,"incomplete_theatre");
        }
        // Transaction: both entities are accepted by the server before consuming any item.
        if(!CogworkDancer.summonAtAltar(server,pos))return fail(player,"no_room");
        stack.shrink(1);
        player.getCooldowns().addCooldown(MyriadCalamity.KEY.get(),100);
        level.playSound(null,pos,SoundEvents.BEACON_ACTIVATE,SoundSource.BLOCKS,1.4F,.8F);
        player.displayClientMessage(Component.translatable("message.myriad_calamity.summoned"),false);
        return InteractionResult.CONSUME;
    }
    private InteractionResult fail(Player player,String key) {
        player.displayClientMessage(Component.translatable("message.myriad_calamity."+key),true);return InteractionResult.FAIL;
    }
}
