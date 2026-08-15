package com.acrp.phone.client;

import com.acrp.phone.ACRPPhone;
import com.acrp.phone.item.ModItems;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Client-only registrations. Scoped to {@link Side#CLIENT} so none of these types
 * are ever loaded on a dedicated server.
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = ACRPPhone.MOD_ID)
public final class ClientRegistryHandler {

    private ClientRegistryHandler() {
    }

    @SubscribeEvent
    public static void onRegisterModels(ModelRegistryEvent event) {
        bindInventoryModel(ModItems.PHONE);
        bindInventoryModel(ModItems.SIM_CARD);
    }

    private static void bindInventoryModel(Item item) {
        ModelLoader.setCustomModelResourceLocation(item, 0,
                new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }
}
