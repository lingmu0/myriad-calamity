package net.xuwu.myriadcalamity.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

public final class WindingKeyItem extends Item {
    public WindingKeyItem(Properties properties) { super(properties); }
    @Override public InteractionResult useOn(UseOnContext context) {
        if(!context.getLevel().isClientSide() && context.getPlayer()!=null)
            context.getPlayer().sendOverlayMessage(Component.translatable("message.myriad_calamity.only_altar"));
        return InteractionResult.FAIL;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> lines,TooltipFlag flag) {
        lines.accept(Component.translatable("tooltip.myriad_calamity.winding_key").withStyle(ChatFormatting.GOLD));
        lines.accept(Component.translatable("tooltip.myriad_calamity.arena").withStyle(ChatFormatting.GRAY));
    }
}
