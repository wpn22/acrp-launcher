package com.acrp.phone.item;

import com.acrp.phone.ACRPPhone;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Item registration. Kept free of client-only types so it is safe to load on a
 * dedicated server — model binding lives in the client-side handler instead.
 */
@Mod.EventBusSubscriber(modid = ACRPPhone.MOD_ID)
public final class ModItems {

    public static Item PHONE;
    public static Item SIM_CARD;

    public static final CreativeTabs TAB = new CreativeTabs(ACRPPhone.MOD_ID) {
        @Override
        public ItemStack getTabIconItem() {
            return PHONE == null ? ItemStack.EMPTY : new ItemStack(PHONE);
        }
    };

    private ModItems() {
    }

    @SubscribeEvent
    public static void onRegisterItems(RegistryEvent.Register<Item> event) {
        PHONE = new ItemPhone();
        SIM_CARD = new ItemSimCard();
        event.getRegistry().registerAll(PHONE, SIM_CARD);
    }
}
