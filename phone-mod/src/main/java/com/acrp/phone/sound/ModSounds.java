package com.acrp.phone.sound;

import com.acrp.phone.ACRPPhone;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * The phone's interface sounds.
 *
 * <p>Short feedback only — locking, unlocking, key taps, notifications, the ring.
 * There is no music player and no media playback; the phone makes the noises a
 * handset makes and nothing else.
 *
 * <p>The audio is synthesised by {@code SoundGenerator} in the tools source set
 * rather than sampled from a real device, so it can be regenerated and carries no
 * one else's licence. All ten files together come to under 90 KB.
 */
@Mod.EventBusSubscriber(modid = ACRPPhone.MOD_ID)
public final class ModSounds {

    public static SoundEvent UNLOCK;
    public static SoundEvent LOCK;
    public static SoundEvent KEY_PRESS;
    public static SoundEvent NOTIFICATION;
    public static SoundEvent MESSAGE_SENT;
    public static SoundEvent FACE_OK;
    public static SoundEvent FACE_FAIL;
    public static SoundEvent SHUTTER;
    public static SoundEvent CALL_END;
    public static SoundEvent RING;

    private ModSounds() {
    }

    @SubscribeEvent
    public static void onRegisterSounds(RegistryEvent.Register<SoundEvent> event) {
        UNLOCK = register(event, "unlock");
        LOCK = register(event, "lock");
        KEY_PRESS = register(event, "key_press");
        NOTIFICATION = register(event, "notification");
        MESSAGE_SENT = register(event, "message_sent");
        FACE_OK = register(event, "face_ok");
        FACE_FAIL = register(event, "face_fail");
        SHUTTER = register(event, "shutter");
        CALL_END = register(event, "call_end");
        RING = register(event, "ring");
    }

    private static SoundEvent register(RegistryEvent.Register<SoundEvent> event, String name) {
        ResourceLocation id = new ResourceLocation(ACRPPhone.MOD_ID, name);
        SoundEvent sound = new SoundEvent(id).setRegistryName(id);
        event.getRegistry().register(sound);
        return sound;
    }
}
