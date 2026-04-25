package mchorse.blockbuster.recording.mwf;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
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
     * Trigger the MWF weapon fire animation on the CLIENT for a given entity.
     * Called from ClientHandlerMWFFireReplay — only ever runs on the client side.
     *
     * Uses reflection so this class stays loadable on servers without MWF.
     * Mirrors the logic of PacketOtherShooterAnimation.handleClientSide() but
     * accepts any EntityLivingBase instead of requiring an EntityPlayer.
     */
    public static void triggerClientFireAnimation(EntityLivingBase entity, String gunName, int fireTickDelay)
    {
        if (entity == null || gunName == null || gunName.isEmpty()) return;
        try
        {
            /* ModularWarfare.gunTypes is Map<String, TypeHolder> where TypeHolder.type = GunType */
            Class<?> clsMW = Class.forName("com.modularwarfare.ModularWarfare");
            Field fGunTypes = clsMW.getField("gunTypes");
            Map<?, ?> gunTypesMap = (Map<?, ?>) fGunTypes.get(null);

            Object holder = gunTypesMap.get(gunName);
            if (holder == null) return;

            Field fType = holder.getClass().getField("type");
            Object gunType = fType.get(holder);
            if (gunType == null) return;

            /* Check animation type (BASIC or ENHANCED) */
            Field fAnimType = gunType.getClass().getField("animationType");
            Object animType = fAnimType.get(gunType);
            Class<?> clsCRH = Class.forName("com.modularwarfare.client.ClientRenderHooks");

            if ("BASIC".equals(animType == null ? "" : animType.toString()))
            {
                Method mGetMachine = clsCRH.getMethod("getAnimMachine", EntityLivingBase.class);
                Object machine = mGetMachine.invoke(null, entity);

                Field fModel = gunType.getClass().getField("model");
                Object model = fModel.get(gunType);

                Class<?> clsModelGun = Class.forName("com.modularwarfare.client.model.ModelGun");
                Class<?> clsGunType = Class.forName("com.modularwarfare.common.guns.GunType");
                Method mTrigger = machine.getClass().getMethod("triggerShoot", clsModelGun, clsGunType, int.class);
                mTrigger.invoke(machine, model, gunType, fireTickDelay);
            }
            else
            {
                /* Enhanced animation */
                Method mGetMachine = clsCRH.getMethod("getEnhancedAnimMachine", EntityLivingBase.class);
                Object machine = mGetMachine.invoke(null, entity);

                Class<?> clsAnimCtrl = Class.forName("com.modularwarfare.client.fpp.enhanced.animation.AnimationController");
                Field fEnhModel = gunType.getClass().getField("enhancedModel");
                Object enhModel = fEnhModel.get(gunType);
                Field fConfig = enhModel.getClass().getField("config");
                Object config = fConfig.get(enhModel);
                Class<?> clsGunEnhCfg = Class.forName("com.modularwarfare.client.fpp.enhanced.configs.GunEnhancedRenderConfig");
                Method mGetCtrl = clsAnimCtrl.getMethod("getController", EntityLivingBase.class, clsGunEnhCfg);
                Object ctrl = mGetCtrl.invoke(null, entity, config);

                Class<?> clsMEG = Class.forName("com.modularwarfare.client.fpp.enhanced.models.ModelEnhancedGun");
                Class<?> clsGunType = Class.forName("com.modularwarfare.common.guns.GunType");
                Method mTrigger = machine.getClass().getMethod("triggerShoot",
                        clsAnimCtrl, clsMEG, clsGunType, int.class, boolean.class);
                mTrigger.invoke(machine, ctrl, enhModel, gunType, fireTickDelay, false);
            }
        }
        catch (Throwable ignored)
        {
            /* MWF absent or API changed — silently skip animation */
        }
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
