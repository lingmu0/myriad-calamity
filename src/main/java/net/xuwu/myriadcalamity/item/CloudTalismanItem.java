package net.xuwu.myriadcalamity.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.xuwu.myriadcalamity.world.CloudArena;

public final class CloudTalismanItem extends Item {
    public CloudTalismanItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) CloudArena.useTalisman(serverPlayer);
        return level.isClientSide()?InteractionResult.SUCCESS:InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) CloudArena.useTalisman(player);
        return context.getLevel().isClientSide()?InteractionResult.SUCCESS:InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable("tooltip.myriad_calamity.cloud_talisman").withStyle(ChatFormatting.AQUA));
        lines.accept(Component.translatable("tooltip.myriad_calamity.cloud_talisman_return").withStyle(ChatFormatting.GRAY));
    }
}
