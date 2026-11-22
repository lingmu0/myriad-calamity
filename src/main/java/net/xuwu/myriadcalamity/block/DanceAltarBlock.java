package net.xuwu.myriadcalamity.block;

import com.mojang.serialization.MapCodec;
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
    public static final MapCodec<DanceAltarBlock> CODEC=simpleCodec(DanceAltarBlock::new);
    public DanceAltarBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(!stack.is(MyriadCalamity.KEY.get()))return InteractionResult.TRY_WITH_EMPTY_HAND;
        if(level.isClientSide())return InteractionResult.SUCCESS;
        ServerLevel server=(ServerLevel)level;
        if(level.getDifficulty()==Difficulty.PEACEFUL)return fail(player,"peaceful");
        if(DanceEncounters.get(server).active(pos) || !level.getEntitiesOfClass(CogworkDancer.class,new AABB(pos).inflate(48),CogworkDancer::isAlive).isEmpty())return fail(player,"already_dancing");
        for(int[] seal:TheatreLayout.SEALS) {
            BlockPos at=pos.offset(seal[0],0,seal[1]);
            if(!level.hasChunkAt(at) || !level.getBlockState(at).is(Blocks.LODESTONE))return fail(player,"incomplete_theatre");
        }
        // Transaction: both entities are accepted by the server before consuming any item.
        if(!CogworkDancer.summonAtAltar(server,pos))return fail(player,"no_room");
        player.getCooldowns().addCooldown(stack,100);
        stack.shrink(1);
        level.playSound(null,pos,SoundEvents.VAULT_ACTIVATE,SoundSource.BLOCKS,1.4F,.8F);
        player.sendSystemMessage(Component.translatable("message.myriad_calamity.summoned"));
        return InteractionResult.CONSUME;
    }
    private InteractionResult fail(Player player,String key) {
        player.sendOverlayMessage(Component.translatable("message.myriad_calamity."+key));return InteractionResult.FAIL;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(!level.isClientSide())player.sendOverlayMessage(Component.translatable("message.myriad_calamity.need_key"));
        return level.isClientSide()?InteractionResult.SUCCESS:InteractionResult.CONSUME;
    }
}
