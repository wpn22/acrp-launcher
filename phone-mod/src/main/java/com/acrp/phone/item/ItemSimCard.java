package com.acrp.phone.item;

import com.acrp.phone.ACRPPhone;
import com.acrp.phone.core.PhoneNbt;
import com.acrp.phone.core.PhoneNumber;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A SIM card. Carries the number and, if registered, the character it belongs to.
 *
 * <p>A card with no number is blank stock — it gets provisioned the first time it
 * is inserted into a phone. Blank cards are what shops sell.
 */
public class ItemSimCard extends Item {

    public ItemSimCard() {
        setRegistryName(ACRPPhone.MOD_ID, "sim_card");
        setUnlocalizedName(ACRPPhone.MOD_ID + ".sim_card");
        setCreativeTab(ModItems.TAB);
        setMaxStackSize(1);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world,
                               List<String> tooltip, ITooltipFlag flag) {
        PhoneNumber number = PhoneNbt.simNumber(stack);
        if (number == null) {
            tooltip.add(TextFormatting.DARK_GRAY
                    + I18n.translateToLocal("item.acrpphone.sim_card.blank"));
            return;
        }
        tooltip.add(TextFormatting.GRAY + number.formatted());
        tooltip.add((PhoneNbt.isRegistered(stack) ? TextFormatting.GREEN : TextFormatting.RED)
                + I18n.translateToLocal(PhoneNbt.isRegistered(stack)
                ? "item.acrpphone.sim_card.registered"
                : "item.acrpphone.sim_card.burner"));
    }

    /** A provisioned SIM reads as a distinct item, so it stands out in an inventory. */
    @Override
    public boolean hasEffect(ItemStack stack) {
        return PhoneNbt.simNumber(stack) != null && PhoneNbt.isRegistered(stack);
    }
}
