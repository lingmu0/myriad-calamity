package net.xuwu.myriadcalamity.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Rare divine trophy harvested when the Divine Dragon dissolves into sakura ash. */
public final class DivineDragonTearsItem extends Item {
    public DivineDragonTearsItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.myriad_calamity.divine_dragon_tears").withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("tooltip.myriad_calamity.divine_dragon_tears_lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
