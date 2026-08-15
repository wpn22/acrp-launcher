package com.acrp.phone.core;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

/**
 * Central place for every NBT key the phone and SIM use, plus typed accessors.
 *
 * <p>The split matters: SIM tags carry the <em>identity</em> (the number, who it is
 * registered to), while phone tags carry the <em>device</em> (passcode, wallpaper,
 * setup state). Messages and contacts hang off the number server-side, so moving a
 * SIM to another handset moves the identity with it and leaves the device's own
 * data — photos, settings — behind on the stolen phone.
 */
public final class PhoneNbt {

    // --- SIM keys -----------------------------------------------------------
    public static final String SIM_NUMBER = "Number";
    public static final String SIM_OWNER = "Owner";
    public static final String SIM_REGISTERED = "Registered";
    public static final String SIM_CARRIER = "Carrier";

    // --- phone keys ---------------------------------------------------------
    public static final String PHONE_DEVICE_ID = "DeviceId";
    public static final String PHONE_SIM = "Sim";
    public static final String PHONE_PASSCODE = "Passcode";
    public static final String PHONE_WALLPAPER = "Wallpaper";
    public static final String PHONE_SETUP_DONE = "SetupDone";
    public static final String PHONE_LANGUAGE = "Language";
    public static final String PHONE_DARK_THEME = "DarkTheme";

    private PhoneNbt() {
    }

    /** Returns the stack's tag, creating and attaching one if absent. */
    public static NBTTagCompound orCreate(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        return tag;
    }

    /** Returns the stack's tag, or an empty throwaway one. Never writes. */
    public static NBTTagCompound orEmpty(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null ? new NBTTagCompound() : tag;
    }

    // --- SIM ----------------------------------------------------------------

    /** The number on a SIM stack, or {@code null} if it was never provisioned. */
    public static PhoneNumber simNumber(ItemStack sim) {
        return PhoneNumber.parseOrNull(orEmpty(sim).getString(SIM_NUMBER));
    }

    public static void setSimNumber(ItemStack sim, PhoneNumber number) {
        orCreate(sim).setString(SIM_NUMBER, number.digits());
    }

    /**
     * Whether this SIM is tied to a character. Registered SIMs are traceable;
     * unregistered ones are burners, which is the point.
     */
    public static boolean isRegistered(ItemStack sim) {
        return orEmpty(sim).getBoolean(SIM_REGISTERED);
    }

    /** Binds a SIM to its owner, making it traceable. */
    public static void register(ItemStack sim, UUID owner) {
        NBTTagCompound tag = orCreate(sim);
        tag.setBoolean(SIM_REGISTERED, true);
        tag.setString(SIM_OWNER, owner.toString());
    }

    /** The registered owner, or {@code null} for a burner. */
    public static UUID simOwner(ItemStack sim) {
        String raw = orEmpty(sim).getString(SIM_OWNER);
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // --- phone --------------------------------------------------------------

    /**
     * A stable id for this handset, minted on first access so a phone keeps its
     * own data across SIM swaps.
     */
    public static UUID deviceId(ItemStack phone) {
        NBTTagCompound tag = orCreate(phone);
        String raw = tag.getString(PHONE_DEVICE_ID);
        if (!raw.isEmpty()) {
            try {
                return UUID.fromString(raw);
            } catch (IllegalArgumentException ignored) {
                // fall through and re-mint
            }
        }
        UUID minted = UUID.randomUUID();
        tag.setString(PHONE_DEVICE_ID, minted.toString());
        return minted;
    }

    /** The SIM currently in the tray, or {@link ItemStack#EMPTY}. */
    public static ItemStack insertedSim(ItemStack phone) {
        NBTTagCompound tag = orEmpty(phone);
        if (!tag.hasKey(PHONE_SIM, 10)) { // 10 == compound
            return ItemStack.EMPTY;
        }
        return new ItemStack(tag.getCompoundTag(PHONE_SIM));
    }

    public static void setInsertedSim(ItemStack phone, ItemStack sim) {
        NBTTagCompound tag = orCreate(phone);
        if (sim.isEmpty()) {
            tag.removeTag(PHONE_SIM);
        } else {
            tag.setTag(PHONE_SIM, sim.writeToNBT(new NBTTagCompound()));
        }
    }

    /** True once the owner has finished the first-run setup wizard. */
    public static boolean isSetUp(ItemStack phone) {
        return orEmpty(phone).getBoolean(PHONE_SETUP_DONE);
    }

    public static void markSetUp(ItemStack phone) {
        orCreate(phone).setBoolean(PHONE_SETUP_DONE, true);
    }

    /** Convenience: the number this handset is currently reachable on. */
    public static PhoneNumber activeNumber(ItemStack phone) {
        ItemStack sim = insertedSim(phone);
        return sim.isEmpty() ? null : simNumber(sim);
    }
}
