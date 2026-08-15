package com.acrp.phone.item;

import com.acrp.phone.ACRPPhone;
import com.acrp.phone.core.PhoneNbt;
import com.acrp.phone.core.PhoneNumber;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The handset.
 *
 * <p>Right-click opens the UI. Holding a SIM in the other hand and right-clicking
 * inserts it; sneaking with an empty other hand ejects whatever is in the tray.
 */
public class ItemPhone extends Item {

    public ItemPhone() {
        setRegistryName(ACRPPhone.MOD_ID, "phone");
        setUnlocalizedName(ACRPPhone.MOD_ID + ".phone");
        setCreativeTab(ModItems.TAB);
        setMaxStackSize(1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player,
                                                    EnumHand hand) {
        ItemStack phone = player.getHeldItem(hand);
        EnumHand other = hand == EnumHand.MAIN_HAND ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
        ItemStack inOtherHand = player.getHeldItem(other);

        // Tray handling runs on the server so the NBT change is authoritative.
        if (inOtherHand.getItem() instanceof ItemSimCard) {
            if (!world.isRemote) {
                insertSim(player, phone, inOtherHand, other);
            }
            return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, phone);
        }

        if (player.isSneaking()) {
            if (!world.isRemote) {
                ejectSim(player, phone);
            }
            return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, phone);
        }

        if (world.isRemote) {
            ACRPPhone.proxy.openPhone(phone, hand);
        }
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, phone);
    }

    /** Swaps a SIM into the tray, handing any previous card back to the player. */
    private void insertSim(EntityPlayer player, ItemStack phone, ItemStack sim,
                           EnumHand simHand) {
        ItemStack previous = PhoneNbt.insertedSim(phone);

        ItemStack toInsert = sim.copy();
        toInsert.setCount(1);
        PhoneNbt.setInsertedSim(phone, toInsert);
        player.setHeldItem(simHand, ItemStack.EMPTY);

        if (!previous.isEmpty() && !player.inventory.addItemStackToInventory(previous)) {
            player.dropItem(previous, false);
        }
    }

    /** Pops the SIM out; losing service is immediate and intentional. */
    private void ejectSim(EntityPlayer player, ItemStack phone) {
        ItemStack sim = PhoneNbt.insertedSim(phone);
        if (sim.isEmpty()) {
            return;
        }
        PhoneNbt.setInsertedSim(phone, ItemStack.EMPTY);
        if (!player.inventory.addItemStackToInventory(sim)) {
            player.dropItem(sim, false);
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world,
                               List<String> tooltip, ITooltipFlag flag) {
        PhoneNumber number = PhoneNbt.activeNumber(stack);
        if (number == null) {
            tooltip.add(TextFormatting.RED
                    + I18n.translateToLocal("item.acrpphone.phone.no_sim"));
        } else {
            tooltip.add(TextFormatting.GRAY + number.formatted());
        }
    }
}
