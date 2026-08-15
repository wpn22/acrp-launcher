package com.acrp.phone;

import com.acrp.phone.proxy.CommonProxy;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import org.apache.logging.log4j.Logger;

/**
 * Entry point for the Adventure City Roleplay phone.
 *
 * <p>The mod id is deliberately distinct from {@code sphone} and from the
 * {@code acphone} jar already shipped in the ACRP pack — running two phones with a
 * clashing id would break both.
 */
@Mod(
        modid = ACRPPhone.MOD_ID,
        name = ACRPPhone.NAME,
        version = ACRPPhone.VERSION,
        acceptedMinecraftVersions = "[1.12.2]"
)
public final class ACRPPhone {

    public static final String MOD_ID = "acrpphone";
    public static final String NAME = "ACRP Phone";
    public static final String VERSION = "0.1.0";

    @Mod.Instance(MOD_ID)
    public static ACRPPhone instance;

    @SidedProxy(
            clientSide = "com.acrp.phone.proxy.ClientProxy",
            serverSide = "com.acrp.phone.proxy.ServerProxy"
    )
    public static CommonProxy proxy;

    private static Logger logger;

    public static Logger log() {
        return logger;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }
}
