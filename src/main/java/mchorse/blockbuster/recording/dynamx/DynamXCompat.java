package mchorse.blockbuster.recording.dynamx;

import net.minecraft.entity.Entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflection-based compatibility layer for DynamX vehicles.
 *
 * Blockbuster must not hard-depend on DynamX — the mod may be absent at runtime.
 * This class resolves DynamX classes/methods once on first use and caches them.
 * All public methods return false/null/no-op if DynamX is not present.
 *
 * Control bitmask (BasicEngineModule):
 *   Bit 0 (1)  = Engine ON/OFF
 *   Bit 1 (2)  = Accelerate (forward)
 *   Bit 2 (4)  = Reverse
 *   Bit 3 (8)  = Turn left
 *   Bit 4 (16) = Turn right
 *   Bit 5 (32) = Handbrake
 */
public final class DynamXCompat
{
    private static boolean resolved = false;
    private static boolean available = false;

    /* DynamX class references */
    private static Class<?> classBaseVehicleEntity;
    private static Class<?> classBasicEngineModule;
    private static Class<?> classSeatsModule;
    private static Class<?> classBasePartSeat;
    private static Class<?> classVehicleEntityEventControllerUpdate;

    /* Cached methods */
    private static Method methodGetModuleByType;
    private static Method methodGetControls;
    private static Method methodSetControls;
    private static Method methodGetSeats;
    private static Method methodMountEntity;
    private static Method methodGetRidingEntity;
    private static Method methodGetSeatToPassengerMap;
    private static Method methodGetRidingSeat;
    private static Method methodEventGetEntity;

    private DynamXCompat() {}

    /**
     * Resolve DynamX classes and methods. Idempotent — safe to call multiple times.
     * Returns true if DynamX is available.
     */
    public static boolean init()
    {
        if (resolved) return available;
        resolved = true;

        try
        {
            classBaseVehicleEntity = Class.forName("fr.dynamx.common.entities.BaseVehicleEntity");
            classBasicEngineModule = Class.forName("fr.dynamx.common.entities.modules.engines.BasicEngineModule");
            classSeatsModule = Class.forName("fr.dynamx.common.entities.modules.SeatsModule");
            classBasePartSeat = Class.forName("fr.dynamx.common.contentpack.parts.BasePartSeat");
            classVehicleEntityEventControllerUpdate = Class.forName("fr.dynamx.api.events.VehicleEntityEvent$ControllerUpdate");

            /* BaseVehicleEntity is actually on ModularPhysicsEntity — walk up */
            Class<?> modularPhysicsEntity = Class.forName("fr.dynamx.common.entities.ModularPhysicsEntity");
            methodGetModuleByType = modularPhysicsEntity.getMethod("getModuleByType", Class.class);

            methodGetControls = classBasicEngineModule.getMethod("getControls");
            methodSetControls = classBasicEngineModule.getMethod("setControls", int.class);

            methodGetSeats = classSeatsModule.getMethod("getSeats");
            methodMountEntity = classBasePartSeat.getMethod("mountEntity",
                    classBaseVehicleEntity, classSeatsModule, Entity.class);

            methodGetSeatToPassengerMap = classSeatsModule.getMethod("getSeatToPassengerMap");
            methodGetRidingSeat = classSeatsModule.getMethod("getRidingSeat", Entity.class);

            /* Event.getEntity() is inherited from VehicleEntityEvent parent */
            Class<?> vehicleEntityEvent = Class.forName("fr.dynamx.api.events.VehicleEntityEvent");
            methodEventGetEntity = vehicleEntityEvent.getMethod("getEntity");

            available = true;
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

    public static boolean isVehicle(Entity entity)
    {
        if (!isAvailable() || entity == null) return false;
        return classBaseVehicleEntity.isInstance(entity);
    }

    public static Class<?> getControllerUpdateEventClass()
    {
        if (!isAvailable()) return null;
        return classVehicleEntityEventControllerUpdate;
    }

    /**
     * Get the vehicle entity from a VehicleEntityEvent.
     */
    public static Entity getEventVehicle(Object event)
    {
        if (!isAvailable() || event == null) return null;
        try
        {
            Object result = methodEventGetEntity.invoke(event);
            if (result instanceof Entity) return (Entity) result;
        }
        catch (Throwable ignored) {}
        return null;
    }

    /**
     * Get the current control bitmask from a vehicle's engine module.
     * Returns -1 if vehicle has no engine or DynamX is missing.
     */
    public static int getVehicleControls(Entity vehicle)
    {
        if (!isAvailable() || vehicle == null) return -1;
        try
        {
            Object engine = methodGetModuleByType.invoke(vehicle, classBasicEngineModule);
            if (engine == null) return -1;
            Object result = methodGetControls.invoke(engine);
            if (result instanceof Integer) return (Integer) result;
        }
        catch (Throwable ignored) {}
        return -1;
    }

    /**
     * Set the control bitmask on a vehicle's engine module.
     * No-op if vehicle has no engine or DynamX is missing.
     */
    public static boolean setVehicleControls(Entity vehicle, int controls)
    {
        if (!isAvailable() || vehicle == null) return false;
        try
        {
            Object engine = methodGetModuleByType.invoke(vehicle, classBasicEngineModule);
            if (engine == null) return false;
            methodSetControls.invoke(engine, controls);
            return true;
        }
        catch (Throwable ignored) {}
        return false;
    }

    /**
     * Mount an entity onto a specific seat of a vehicle (by index).
     * Returns true on success.
     */
    public static boolean mountSeat(Entity vehicle, Entity rider, int seatIndex)
    {
        if (!isAvailable() || vehicle == null || rider == null) return false;
        try
        {
            Object seatsModule = methodGetModuleByType.invoke(vehicle, classSeatsModule);
            if (seatsModule == null) return false;

            Object seatsObj = methodGetSeats.invoke(seatsModule);
            if (!(seatsObj instanceof java.util.List)) return false;

            java.util.List<?> seats = (java.util.List<?>) seatsObj;
            if (seatIndex < 0 || seatIndex >= seats.size()) return false;

            Object seat = seats.get(seatIndex);
            Object result = methodMountEntity.invoke(seat, vehicle, seatsModule, rider);
            return result instanceof Boolean && (Boolean) result;
        }
        catch (Throwable ignored) {}
        return false;
    }

    /**
     * Get the seat index that an entity is riding on, or -1 if not riding or not found.
     */
    public static int getSeatIndex(Entity vehicle, Entity rider)
    {
        if (!isAvailable() || vehicle == null || rider == null) return -1;
        try
        {
            Object seatsModule = methodGetModuleByType.invoke(vehicle, classSeatsModule);
            if (seatsModule == null) return -1;

            Object seat = methodGetRidingSeat.invoke(seatsModule, rider);
            if (seat == null) return -1;

            Object seatsObj = methodGetSeats.invoke(seatsModule);
            if (!(seatsObj instanceof java.util.List)) return -1;

            java.util.List<?> seats = (java.util.List<?>) seatsObj;
            for (int i = 0; i < seats.size(); i++)
            {
                if (seats.get(i) == seat) return i;
            }
        }
        catch (Throwable ignored) {}
        return -1;
    }

    /**
     * Returns true if the entity is currently riding a DynamX vehicle (in the driver's seat).
     */
    public static boolean isDriving(Entity rider)
    {
        if (!isAvailable() || rider == null) return false;
        Entity ridden = rider.getRidingEntity();
        return isVehicle(ridden);
    }
}
