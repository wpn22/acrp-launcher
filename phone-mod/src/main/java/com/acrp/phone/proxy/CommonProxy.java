package com.acrp.phone.proxy;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Side-agnostic lifecycle hooks. Anything that touches rendering, fonts or GUIs
 * lives in {@link ClientProxy} — loading those classes on a dedicated server
 * crashes it.
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
    }

    public void init(FMLInitializationEvent event) {
    }

    public void postInit(FMLPostInitializationEvent event) {
    }

    /**
     * Opens the phone UI. No-op on the server, where there is nothing to draw.
     *
     * @param stack the phone being held
     * @param hand  the hand holding it
     */
    public void openPhone(ItemStack stack, EnumHand hand) {
    }
}
