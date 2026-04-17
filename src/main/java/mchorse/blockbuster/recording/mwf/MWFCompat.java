package mchorse.blockbuster.recording.mwf;

import net.minecraft.entity.Entity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reflection-based compatibility layer for ModularWarfare (MWF / MWF-SHINING).
 *
 * Blockbuster must not hard-depend on MWF — the mod may be absent at runtime.
 * This class resolves MWF classes on first use and caches them. All public
 * methods become no-ops if MWF is not present.
 *
 * Purpose: Cosmetic-only weapon fire replay.
 * - Record: capture weapon internal name + fire tick delay when WeaponFireEvent fires
 * - Replay: broadcast PacketOtherShooterAnimation so all clients see the actor's
 *   fire animation + sound. Does NOT trigger damage/ammo consumption.
 */
public final class MWFCompat
{
    private static boolean resolved = false;
    private static boolean available = false;

    /* MWF class references */
    private static Class<?> classModularWarfare;
    private static Class<?> classNetworkHandler;
    private static Class<?> classPacketOtherShooterAnimation;
    private static Class<?> classAnimationType;
    private static Object animationTypeFire; /* enum constant */

    /* Cached members */
    private static Field fieldNetwork;           /* ModularWarfare.NETWORK */
    private static Method methodSendToAll;       /* NetworkHandler.sendToAll(PacketBase) */
    private static Constructor<?> ctorPacketFire; /* PacketOtherShooterAnimation(UUID, AnimationType, String, int, boolean) */

    private MWFCompat() {}

    public static boolean init()
    {
        if (resolved) return available;
        resolved = true;

        try
        {
            classModularWarfare = Class.forName("com.modularwarfare.ModularWarfare");
            classNetworkHandler = Class.forName("com.modularwarfare.common.network.NetworkHandler");
            classPacketOtherShooterAnimation = Class.forName("com.modularwarfare.common.network.PacketOtherShooterAnimation");
            classAnimationType = Class.forName("com.modularwarfare.common.network.PacketOtherShooterAnimation$AnimationType");
            Class<?> classPacketBase = Class.forName("com.modularwarfare.common.network.PacketBase");

            fieldNetwork = classModularWarfare.getField("NETWORK");
            methodSendToAll = classNetworkHandler.getMethod("sendToAll", classPacketBase);

            /* AnimationType.FIRE enum constant */
            Object[] consts = classAnimationType.getEnumConstants();
            if (consts != null && consts.length > 0)
            {
                animationTypeFire = consts[0]; /* only value is FIRE */
            }

            ctorPacketFire = classPacketOtherShooterAnimation.getConstructor(
                    UUID.class, classAnimationType, String.class, int.class, boolean.class);

            available = animationTypeFire != null;
        }
        catch (Throwable t)
        {
            available = false;
        }

        return available;
    }

    public static boolean isAvailable()
    {
        return resolved ? available : init();
    }

    /**
     * Returns true if the given event object is a {@code WeaponFireEvent.Pre}.
     * Used by the compat handler to filter the generic event bus firehose
     * without needing a hard class reference.
     */
    public static boolean isFirePreEvent(Object event)
    {
        if (event == null) return false;
        try
        {
            Class<?> cls = Class.forName("com.modularwarfare.api.WeaponFireEvent$Pre");
            return cls.isInstance(event);
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    /**
     * Broadcast a cosmetic fire animation packet to all clients for the given
     * player UUID. Called at playback time so the actor's fire animation plays
     * on every nearby client without triggering damage/ammo on the server.
     */
    public static boolean sendFireAnimation(Entity source, String internalName, int fireTickDelay)
    {
        if (!isAvailable() || source == null || internalName == null) return false;
        try
        {
            Object packet = ctorPacketFire.newInstance(
                    source.getUniqueID(),
                    animationTypeFire,
                    internalName,
                    fireTickDelay,
                    false);

            Object network = fieldNetwork.get(null);
            if (network == null) return false;

            methodSendToAll.invoke(network, packet);
            return true;
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }
}
