package net.xuwu.myriadcalamity.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class WindingKeyItem extends Item {
    public WindingKeyItem(Properties properties) { super(properties); }
    @Override public InteractionResult useOn(UseOnContext context) {
        if(!context.getLevel().isClientSide && context.getPlayer()!=null)
            context.getPlayer().displayClientMessage(Component.translatable("message.myriad_calamity.only_altar"),true);
        return InteractionResult.FAIL;
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.myriad_calamity.winding_key").withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("tooltip.myriad_calamity.arena").withStyle(ChatFormatting.GRAY));
    }
}