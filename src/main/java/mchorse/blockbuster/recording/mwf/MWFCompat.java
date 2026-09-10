package mchorse.blockbuster.recording.mwf;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.EntityLivingBase;

import net.minecraftforge.common.capabilities.Capability;

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
            if (holder == null)
            {
                System.out.println("[Blockbuster] MWF fire: no gun type registered for '" + gunName + "'");
                return;
            }

            Field fType = holder.getClass().getField("type");
            Object gunType = fType.get(holder);
            if (gunType == null) return;

            /* Check animation type (BASIC or ENHANCED) */
            Field fAnimType = gunType.getClass().getField("animationType");
            Object animType = fAnimType.get(gunType);

            System.out.println("[Blockbuster] MWF fire: gun=" + gunName + " anim=" + animType + " entity=" + entity.getEntityId());
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
                System.out.println("[Blockbuster] MWF fire: BASIC triggerShoot OK");
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
                /* getController is declared with the BASE type EnhancedRenderConfig.
                 * Looking it up with the subclass GunEnhancedRenderConfig throws
                 * NoSuchMethodException - getMethod matches parameter types exactly. */
                Class<?> clsEnhCfg = Class.forName("com.modularwarfare.client.fpp.enhanced.configs.EnhancedRenderConfig");
                Method mGetCtrl = clsAnimCtrl.getMethod("getController", EntityLivingBase.class, clsEnhCfg);
                Object ctrl = mGetCtrl.invoke(null, entity, config);

                Class<?> clsMEG = Class.forName("com.modularwarfare.client.fpp.enhanced.models.ModelEnhancedGun");
                Class<?> clsGunType = Class.forName("com.modularwarfare.common.guns.GunType");
                Method mTrigger = machine.getClass().getMethod("triggerShoot",
                        clsAnimCtrl, clsMEG, clsGunType, int.class, boolean.class);
                mTrigger.invoke(machine, ctrl, enhModel, gunType, fireTickDelay, false);
                System.out.println("[Blockbuster] MWF fire: ENHANCED triggerShoot OK");
            }

            /* The visible bullet. MWF spawns it in FireManager, which an actor
             * never runs, so the actor fired with animation, sound and flash but
             * nothing left the barrel. Purely cosmetic and client local. */
            spawnCosmeticTrail(clsCRH, entity, gunType);
        }
        catch (Throwable t)
        {
            /* Was silent before, which made this impossible to diagnose from a log. */
            System.out.println("[Blockbuster] MWF fire animation failed: " + t);
        }
    }

    private static void spawnCosmeticTrail(Class<?> clsCRH, EntityLivingBase entity, Object gunType)
    {
        try
        {
            Class<?> clsGunType = Class.forName("com.modularwarfare.common.guns.GunType");
            Method mTrail = clsCRH.getMethod("spawnCosmeticTrail", EntityLivingBase.class, clsGunType, net.minecraft.item.ItemStack.class);

            mTrail.invoke(null, entity, gunType, entity.getHeldItemMainhand());
        }
        catch (NoSuchMethodException e)
        {
            /* Older MWF without the cosmetic entry point - animation still plays. */
        }
        catch (Throwable t)
        {
            System.out.println("[Blockbuster] MWF cosmetic trail failed: " + t);
        }
    }

    /**
     * Returns true if the given event object is a {@code WeaponFireEvent.Pre}.
     * Used by the compat handler to filter the generic event bus firehose
     * without needing a hard class reference.
     */
    /* --- Fire sound --------------------------------------------------------
     *
     * triggerShoot only drives the animation state machine. MWF plays the shot
     * itself from FireManager via GunType.playSound, which is server side - it
     * pushes a sound packet to the nearby players through ModularWarfare.NETWORK.
     * The actor never runs FireManager, so without this the replay was silent. */

    private static boolean soundResolved = false;
    private static boolean soundAvailable = false;
    private static Method methodPlaySound;   /* BaseType.playSound(EntityLivingBase, WeaponSoundType, ItemStack) */
    private static Object soundTypeFire;     /* WeaponSoundType.Fire */

    private static boolean initSound()
    {
        if (soundResolved) return soundAvailable;
        soundResolved = true;

        try
        {
            Class<?> classWeaponSoundType = Class.forName("com.modularwarfare.common.guns.WeaponSoundType");

            for (Object constant : classWeaponSoundType.getEnumConstants())
            {
                /* NOT toString() - WeaponSoundType overrides it to return the
                 * eventName, so comparing against "Fire" never matched and the
                 * whole method exited silently. */
                if ("Fire".equals(((Enum<?>) constant).name()))
                {
                    soundTypeFire = constant;
                    break;
                }
            }

            Class<?> classBaseType = Class.forName("com.modularwarfare.common.type.BaseType");
            methodPlaySound = classBaseType.getMethod("playSound",
                    EntityLivingBase.class, classWeaponSoundType, net.minecraft.item.ItemStack.class);

            soundAvailable = soundTypeFire != null;
        }
        catch (Throwable t)
        {
            soundAvailable = false;
            System.out.println("[Blockbuster] MWF fire sound unavailable: " + t);
        }

        if (!soundAvailable)
        {
            System.out.println("[Blockbuster] MWF fire sound unavailable: WeaponSoundType.Fire not resolved");
        }

        return soundAvailable;
    }

    /**
     * Server side: play a gun's fire sound at an entity, the same way MWF does
     * for a real shooter.
     */
    public static void playFireSound(EntityLivingBase entity, String gunName, net.minecraft.item.ItemStack stack)
    {
        if (entity == null || gunName == null || gunName.isEmpty()) return;
        if (!isAvailable() || !initSound()) return;

        try
        {
            Object gunType = findGunType(gunName);

            if (gunType == null)
            {
                System.out.println("[Blockbuster] MWF fire sound: no gun type for '" + gunName + "' (server side)");
                return;
            }

            methodPlaySound.invoke(gunType, entity, soundTypeFire, stack);
            System.out.println("[Blockbuster] MWF fire sound played for " + gunName);
        }
        catch (Throwable t)
        {
            /* InvocationTargetException hides the real error in its cause. */
            Throwable cause = t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null ? t.getCause() : t;

            System.out.println("[Blockbuster] MWF fire sound failed: " + cause);

            StackTraceElement[] trace = cause.getStackTrace();

            for (int i = 0; i < Math.min(6, trace.length); i++)
            {
                System.out.println("[Blockbuster]     at " + trace[i]);
            }
        }
    }

    /**
     * Resolve a GunType from its internal name via ModularWarfare.gunTypes.
     */
    private static Object findGunType(String gunName) throws Exception
    {
        Class<?> clsMW = Class.forName("com.modularwarfare.ModularWarfare");
        Map<?, ?> gunTypesMap = (Map<?, ?>) clsMW.getField("gunTypes").get(null);
        Object holder = gunTypesMap.get(gunName);

        return holder == null ? null : holder.getClass().getField("type").get(holder);
    }

    /* --- ModularMovements (lean, sit, crawl, roll) --------------------------
     *
     * ModularMovements ships inside MWF. Its pose state is a single int
     * (PlayerState.writeCode/readCode), the server keeps it in
     * ServerListener.playerStateMap and the client in
     * ClientListener.ohterPlayerStateMap - both keyed by entity id, both public.
     * ClientListener.setRotationAngles already takes a plain ModelBiped, so an
     * actor can be posed by it without changing anything in MWF. */

    private static boolean movementResolved = false;
    private static boolean movementAvailable = false;
    private static Map<Object, Object> serverStateMap;
    private static Map<Object, Object> clientStateMap;
    private static Class<?> classPlayerState;
    private static Method methodWriteCode;
    private static Method methodReadCode;
    private static Method methodMovementAngles;

    @SuppressWarnings("unchecked")
    private static boolean initMovement()
    {
        if (movementResolved) return movementAvailable;
        movementResolved = true;

        try
        {
            Class<?> classServer = Class.forName("mchhui.modularmovements.tactical.server.ServerListener");
            Class<?> classClient = Class.forName("mchhui.modularmovements.tactical.client.ClientListener");

            classPlayerState = Class.forName("mchhui.modularmovements.tactical.PlayerState");
            methodWriteCode = classPlayerState.getMethod("writeCode");
            methodReadCode = classPlayerState.getMethod("readCode", int.class);

            serverStateMap = (Map<Object, Object>) classServer.getField("playerStateMap").get(null);
            clientStateMap = (Map<Object, Object>) classClient.getField("ohterPlayerStateMap").get(null);

            methodMovementAngles = classClient.getMethod("setRotationAngles",
                    net.minecraft.client.model.ModelBiped.class,
                    float.class, float.class, float.class, float.class, float.class, float.class,
                    net.minecraft.entity.Entity.class);

            movementAvailable = serverStateMap != null && clientStateMap != null;
        }
        catch (Throwable t)
        {
            movementAvailable = false;
            System.out.println("[Blockbuster] ModularMovements unavailable: " + t);
        }

        return movementAvailable;
    }

    /**
     * Server side: the recording player's ModularMovements pose as one int.
     * Returns 0 when there is nothing to record.
     */
    public static int getMovementState(Entity entity)
    {
        if (entity == null || !initMovement()) return 0;

        try
        {
            Object state = serverStateMap.get(Integer.valueOf(entity.getEntityId()));

            if (state == null) return 0;

            Object code = methodWriteCode.invoke(state);

            return code instanceof Integer ? (Integer) code : 0;
        }
        catch (Throwable ignored) {}

        return 0;
    }

    /**
     * Client side: give an actor a ModularMovements pose so the renderer picks
     * it up. Keyed by entity id, which is what ClientListener reads.
     */
    public static void setMovementState(Entity entity, int code)
    {
        if (entity == null || !initMovement()) return;

        try
        {
            Integer id = Integer.valueOf(entity.getEntityId());
            Object state = clientStateMap.get(id);

            if (state == null)
            {
                state = classPlayerState.newInstance();
                clientStateMap.put(id, state);
            }

            methodReadCode.invoke(state, Integer.valueOf(code));
        }
        catch (Throwable t)
        {
            System.out.println("[Blockbuster] ModularMovements state failed: " + t);
        }
    }

    /**
     * Client side: let ModularMovements pose this model, exactly as it does for
     * a player through MWF's FakePlayerModel.
     */
    public static void applyMovementAngles(net.minecraft.client.model.ModelBiped model, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale, Entity entity)
    {
        if (model == null || entity == null || !initMovement()) return;
        if (!clientStateMap.containsKey(Integer.valueOf(entity.getEntityId()))) return;

        try
        {
            methodMovementAngles.invoke(null, model, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entity);
        }
        catch (Throwable ignored) {}
    }

    /**
     * Client side: drop an actor's pose when its playback ends.
     */
    public static void clearMovementState(Entity entity)
    {
        if (entity == null || !initMovement()) return;

        try
        {
            clientStateMap.remove(Integer.valueOf(entity.getEntityId()));
        }
        catch (Throwable ignored) {}
    }

    /* --- Held item classification ------------------------------------------
     *
     * Mirrors what MWF's ClientRenderHooks.renderThirdPose decides, so an actor
     * holds a weapon the same way a player does. Without this, ModelCustom's
     * own setHands() falls back to ArmPose.ITEM and the gun hangs straight down. */

    /** Not an MWF item that changes the arm pose. */
    public static final int HOLD_NONE = 0;
    /** An MWF gun (BaseType id 1). */
    public static final int HOLD_GUN = 1;
    /** Another MWF item with a model. */
    public static final int HOLD_OTHER = 2;

    private static boolean holdResolved = false;
    private static boolean holdAvailable = false;
    private static Class<?> classBaseItem;
    private static Class<?> classItemAttachment;
    private static Class<?> classItemBackpack;
    private static Field fieldBaseItemType;   /* BaseItem.baseType */
    private static Method methodTypeHasModel; /* BaseType.hasModel() */
    private static Field fieldTypeId;         /* BaseType.id */

    private static boolean initHold()
    {
        if (holdResolved) return holdAvailable;
        holdResolved = true;

        try
        {
            classBaseItem = Class.forName("com.modularwarfare.common.type.BaseItem");
            classItemAttachment = Class.forName("com.modularwarfare.common.guns.ItemAttachment");
            classItemBackpack = Class.forName("com.modularwarfare.common.backpacks.ItemBackpack");
            fieldBaseItemType = classBaseItem.getField("baseType");

            Class<?> classBaseType = Class.forName("com.modularwarfare.common.type.BaseType");
            methodTypeHasModel = classBaseType.getMethod("hasModel");
            fieldTypeId = classBaseType.getField("id");

            holdAvailable = true;
        }
        catch (Throwable t)
        {
            holdAvailable = false;
        }

        return holdAvailable;
    }

    /**
     * Classify a held stack the way MWF's third person pose does.
     */
    public static int getHoldKind(ItemStack stack)
    {
        if (stack == null || stack.isEmpty() || !initHold()) return HOLD_NONE;

        try
        {
            Object item = stack.getItem();

            if (!classBaseItem.isInstance(item)) return HOLD_NONE;
            if (classItemAttachment.isInstance(item) || classItemBackpack.isInstance(item)) return HOLD_NONE;

            Object baseType = fieldBaseItemType.get(item);

            if (baseType == null || !Boolean.TRUE.equals(methodTypeHasModel.invoke(baseType))) return HOLD_NONE;

            return fieldTypeId.getInt(baseType) == 1 ? HOLD_GUN : HOLD_OTHER;
        }
        catch (Throwable ignored) {}

        return HOLD_NONE;
    }

    /* --- Aiming ------------------------------------------------------------
     *
     * MWF broadcasts aiming with PacketAimingResponse keyed by player UUID, and
     * the client stores it in AnimationUtils.isAiming. The consumer in
     * ClientRenderHooks.renderThirdPose reads entity.getUniqueID(), so an actor
     * works there as long as something puts its UUID into that map - which
     * MWF's own packet cannot do, because it resolves players only. */

    private static boolean aimResolved = false;
    private static boolean aimAvailable = false;
    private static Map<Object, Object> serverAimMap;  /* ServerTickHandler.playerAimInstant */
    private static Map<Object, Object> clientAimMap;  /* AnimationUtils.isAiming */

    @SuppressWarnings("unchecked")
    private static boolean initAim()
    {
        if (aimResolved) return aimAvailable;
        aimResolved = true;

        try
        {
            Class<?> classServerTick = Class.forName("com.modularwarfare.common.handler.ServerTickHandler");
            serverAimMap = (Map<Object, Object>) classServerTick.getField("playerAimInstant").get(null);

            Class<?> classAnimationUtils = Class.forName("com.modularwarfare.api.AnimationUtils");
            clientAimMap = (Map<Object, Object>) classAnimationUtils.getField("isAiming").get(null);

            aimAvailable = serverAimMap != null && clientAimMap != null;
        }
        catch (Throwable t)
        {
            aimAvailable = false;
        }

        return aimAvailable;
    }

    /**
     * Server side: is this player currently aiming down sights?
     */
    public static boolean isAiming(Entity entity)
    {
        if (entity == null || !initAim()) return false;

        try
        {
            return Boolean.TRUE.equals(serverAimMap.get(entity.getUniqueID()));
        }
        catch (Throwable ignored) {}

        return false;
    }

    /**
     * Client side: is this entity currently marked as aiming?
     *
     * Needed because MWF sets the aiming arm pose from RenderLivingEvent.Pre,
     * which runs before the model's setRotationAngles - Blockbuster's
     * ModelCustom.setHands would overwrite it again on every frame.
     */
    public static boolean isAimingClient(Entity entity)
    {
        if (entity == null || !initAim()) return false;

        try
        {
            return Boolean.TRUE.equals(clientAimMap.get(entity.getUniqueID()));
        }
        catch (Throwable ignored) {}

        return false;
    }

    /**
     * Client side: mark an entity as aiming so MWF's third person pose picks it
     * up. Keyed by UUID because that is what MWF reads.
     */
    public static void setAiming(Entity entity, boolean aiming)
    {
        if (entity == null || !initAim()) return;

        try
        {
            if (aiming)
            {
                clientAimMap.put(entity.getUniqueID(), Boolean.TRUE);
            }
            else
            {
                clientAimMap.remove(entity.getUniqueID());
            }
        }
        catch (Throwable ignored) {}
    }

    /* --- Extra slots (MWF vests / body armor) ------------------------------
     *
     * MWF keeps vests in its own extra slot capability on the player, not in a
     * vanilla armor slot, which is why Blockbuster's EquipAction never saw
     * them. Resolved separately from the fire animation so one missing piece
     * does not disable the other. */

    private static boolean slotsResolved = false;
    private static boolean slotsAvailable = false;
    private static Object extraCapability;

    /** Slot index of the body vest, matching MWF's own RenderLayerBody. */
    public static final int SLOT_VEST = 1;

    private static boolean initSlots()
    {
        if (slotsResolved) return slotsAvailable;
        slotsResolved = true;

        try
        {
            Class<?> classCapabilityExtra = Class.forName("com.modularwarfare.common.capability.extraslots.CapabilityExtra");

            extraCapability = classCapabilityExtra.getField("CAPABILITY").get(null);
            slotsAvailable = extraCapability != null;
        }
        catch (Throwable t)
        {
            slotsAvailable = false;
        }

        return slotsAvailable;
    }

    /**
     * Read one of MWF's extra slots off an entity. Returns
     * {@link ItemStack#EMPTY} when MWF is absent, the entity has no extra slots
     * or the slot is empty.
     */
    public static ItemStack getExtraSlotStack(Entity entity, int slot)
    {
        if (entity == null || !initSlots()) return ItemStack.EMPTY;

        try
        {
            Capability<?> capability = (Capability<?>) extraCapability;

            if (!entity.hasCapability(capability, null)) return ItemStack.EMPTY;

            Object handler = entity.getCapability(capability, null);

            if (handler == null) return ItemStack.EMPTY;

            /* Resolved off the instance so we do not depend on which item
             * handler interface MWF's container happens to implement. */
            Method method = handler.getClass().getMethod("getStackInSlot", int.class);
            Object stack = method.invoke(handler, slot);

            return stack instanceof ItemStack ? (ItemStack) stack : ItemStack.EMPTY;
        }
        catch (Throwable ignored) {}

        return ItemStack.EMPTY;
    }

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
