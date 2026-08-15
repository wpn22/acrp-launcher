package com.acrp.phone.proxy;

import com.acrp.phone.ACRPPhone;
import com.acrp.phone.core.PhoneNbt;
import com.acrp.phone.core.PhoneNumber;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

/**
 * Client-side lifecycle and UI entry point.
 */
public class ClientProxy extends CommonProxy {

    @Override
    public void openPhone(ItemStack stack, EnumHand hand) {
        // Phase 3 replaces this with the real screen. Until the UI engine lands,
        // log what the shell will need so the item can be exercised in-game.
        PhoneNumber number = PhoneNbt.activeNumber(stack);
        ACRPPhone.log().info("phone opened: device={} number={} setUp={}",
                PhoneNbt.deviceId(stack),
                number == null ? "<no sim>" : number.formatted(),
                PhoneNbt.isSetUp(stack));
    }
}
