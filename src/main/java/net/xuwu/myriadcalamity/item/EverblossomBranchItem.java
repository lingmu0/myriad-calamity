package net.xuwu.myriadcalamity.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.xuwu.myriadcalamity.world.DivineArena;

/** Entry talisman that transports the challenger into the Divine Dragon's Sakura Cloud Sea realm. */
public final class EverblossomBranchItem extends Item {
    public EverblossomBranchItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) DivineArena.useBranch(serverPlayer);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) DivineArena.useBranch(player);
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.myriad_calamity.everblossom_branch").withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(Component.translatable("tooltip.myriad_calamity.everblossom_branch_return").withStyle(ChatFormatting.GRAY));
    }
}
