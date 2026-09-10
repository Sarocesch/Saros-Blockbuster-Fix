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
    private static Method methodGetPackInfo;            /* BaseVehicleEntity.getPackInfo() */
    private static Method methodGetPartsByType;         /* IModelPackObject.getPartsByType(Class) */
    private static Method methodMountEntity;            /* BasePartSeat.mountEntity(vehicle, seatsModule, rider) */
    private static Method methodGetSeatToPassengerMap;  /* SeatsModule.getSeatToPassengerMap() */
    private static Method methodGetRidingSeat;          /* SeatsModule.getRidingSeat(Entity) */
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

            /* getModuleByType is on ModularPhysicsEntity */
            Class<?> modularPhysicsEntity = Class.forName("fr.dynamx.common.entities.ModularPhysicsEntity");
            methodGetModuleByType = modularPhysicsEntity.getMethod("getModuleByType", Class.class);

            methodGetControls = classBasicEngineModule.getMethod("getControls");
            methodSetControls = classBasicEngineModule.getMethod("setControls", int.class);

            /* Seats are stored on PackInfo.getPartsByType(BasePartSeat.class).
             * getPartsByType is a default method on IPartContainer — look it up
             * on that interface directly (can't rely on getPackInfo().getReturnType()
             * due to generic erasure to IPhysicsPackInfo). */
            methodGetPackInfo = classBaseVehicleEntity.getMethod("getPackInfo");
            Class<?> classIPartContainer = Class.forName("fr.dynamx.api.contentpack.object.IPartContainer");
            methodGetPartsByType = classIPartContainer.getMethod("getPartsByType", Class.class);

            /* mountEntity is declared as mountEntity(A, SeatsModule, Entity) where
             * A extends IDynamXObject — after type erasure the bytecode parameter
             * is IDynamXObject, not BaseVehicleEntity.  Using the wrong class here
             * caused NoSuchMethodException, silently disabling all DynamX compat. */
            Class<?> classIDynamXObject = Class.forName("fr.dynamx.common.entities.IDynamXObject");
            methodMountEntity = classBasePartSeat.getMethod("mountEntity",
                    classIDynamXObject, classSeatsModule, Entity.class);

            methodGetSeatToPassengerMap = classSeatsModule.getMethod("getSeatToPassengerMap");
            methodGetRidingSeat = classSeatsModule.getMethod("getRidingSeat", Entity.class);

            /* Event.getEntity() is inherited from VehicleEntityEvent parent */
            Class<?> vehicleEntityEvent = Class.forName("fr.dynamx.api.events.VehicleEntityEvent");
            methodEventGetEntity = vehicleEntityEvent.getMethod("getEntity");

            available = true;
            System.out.println("[Blockbuster] DynamXCompat initialized OK");
        }
        catch (Throwable t)
        {
            available = false;
            System.out.println("[Blockbuster] DynamXCompat init failed: " + t);
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

    /* --- Simulation holder --------------------------------------------------
     *
     * When a PLAYER mounts a DynamX vehicle, DynamX hands physics authority to
     * that player's client (SimulationHolder.DRIVER / DRIVER_SP) and calls
     * BasicEngineModule.resetControls(), which wipes everything except the
     * engine and handbrake bits. A Blockbuster fake player has no client to
     * send controls, so the car kept its engine running and never moved.
     *
     * Forcing the holder back to SERVER_SP keeps the physics on the server,
     * where the replayed VehicleControlAction actually reaches it. */

    private static boolean simResolved = false;
    private static boolean simAvailable = false;
    private static Method methodGetSynchronizer;
    private static Method methodSetSimulationHolder;
    private static Object simulationHolderServerSP;

    private static boolean initSimulation()
    {
        if (simResolved) return simAvailable;
        simResolved = true;

        try
        {
            Class<?> classPhysicsEntity = Class.forName("fr.dynamx.common.entities.PhysicsEntity");
            methodGetSynchronizer = classPhysicsEntity.getMethod("getSynchronizer");

            Class<?> classSynchronizer = Class.forName("fr.dynamx.common.network.sync.PhysicsEntitySynchronizer");
            Class<?> classSimulationHolder = Class.forName("fr.dynamx.api.network.sync.SimulationHolder");

            methodSetSimulationHolder = classSynchronizer.getMethod("setSimulationHolder",
                    classSimulationHolder, net.minecraft.entity.player.EntityPlayer.class);

            for (Object constant : classSimulationHolder.getEnumConstants())
            {
                if ("SERVER_SP".equals(((Enum<?>) constant).name()))
                {
                    simulationHolderServerSP = constant;
                    break;
                }
            }

            simAvailable = simulationHolderServerSP != null;
        }
        catch (Throwable t)
        {
            simAvailable = false;
            System.out.println("[Blockbuster] DynamX simulation holder unavailable: " + t);
        }

        return simAvailable;
    }

    /**
     * Keep a vehicle's physics on the server while a replay drives it.
     *
     * Only meaningful for fake players - a real player driving must keep
     * authority on their own client, or they lose control of the car.
     */
    public static boolean forceServerSimulation(Entity vehicle)
    {
        if (!isAvailable() || vehicle == null || !isVehicle(vehicle)) return false;
        if (!initSimulation()) return false;

        try
        {
            Object synchronizer = methodGetSynchronizer.invoke(vehicle);

            if (synchronizer == null) return false;

            methodSetSimulationHolder.invoke(synchronizer, simulationHolderServerSP, null);
            System.out.println("[Blockbuster] DynamX: vehicle physics forced to SERVER_SP for replay");

            return true;
        }
        catch (Throwable t)
        {
            System.out.println("[Blockbuster] DynamX: couldn't force server simulation: " + t);
        }

        return false;
    }

    /* --- BasicsAddon (siren, beacons, head lights, turn signals) -------------
     *
     * Resolved separately from DynamX itself: the addon is optional, and a
     * missing addon must not disable vehicle recording as a whole. */

    private static boolean basicsResolved = false;
    private static boolean basicsAvailable = false;
    private static Class<?> classBasicsAddonModule;
    private static Field fieldBasicsState;
    private static Method methodVariableGet;
    private static Method methodVariableSet;

    private static boolean initBasics()
    {
        if (basicsResolved) return basicsAvailable;
        basicsResolved = true;

        try
        {
            classBasicsAddonModule = Class.forName("fr.dynamx.addons.basics.common.modules.BasicsAddonModule");

            /* The complete light/siren state is a single synchronized int.
             * BasicsAddonModule's public setters (setSirenOn, setBeaconsOn,
             * setHeadLightsOn, the turn signals, ...) all rewrite that same
             * field, so reading and writing it directly captures every one of
             * them in one value. serializeState() would be nicer but is
             * private. */
            fieldBasicsState = classBasicsAddonModule.getDeclaredField("state");
            fieldBasicsState.setAccessible(true);

            Class<?> classEntityVariable = Class.forName("fr.dynamx.api.network.sync.EntityVariable");
            methodVariableGet = classEntityVariable.getMethod("get");
            /* set(T) erases to set(Object) in the bytecode */
            methodVariableSet = classEntityVariable.getMethod("set", Object.class);

            basicsAvailable = true;
        }
        catch (Throwable t)
        {
            basicsAvailable = false;
        }

        return basicsAvailable;
    }

    private static Object getBasicsStateVariable(Entity vehicle) throws Exception
    {
        Object module = methodGetModuleByType.invoke(vehicle, classBasicsAddonModule);

        return module == null ? null : fieldBasicsState.get(module);
    }

    /**
     * Read a vehicle's BasicsAddon state bitmask (siren, beacons, head lights,
     * DRL, turn signals, lock). Returns -1 when the addon or the module is
     * absent.
     */
    public static int getVehicleBasicsState(Entity vehicle)
    {
        if (!isAvailable() || !initBasics() || vehicle == null) return -1;

        try
        {
            Object variable = getBasicsStateVariable(vehicle);
            if (variable == null) return -1;

            Object value = methodVariableGet.invoke(variable);
            if (value instanceof Integer) return (Integer) value;
        }
        catch (Throwable ignored) {}

        return -1;
    }

    /**
     * Restore a vehicle's BasicsAddon state bitmask. The variable is
     * synchronized to spectators by DynamX, so writing it server side is what
     * makes every client see the siren and the lights.
     */
    public static boolean setVehicleBasicsState(Entity vehicle, int state)
    {
        if (!isAvailable() || !initBasics() || vehicle == null) return false;

        try
        {
            Object variable = getBasicsStateVariable(vehicle);
            if (variable == null) return false;

            methodVariableSet.invoke(variable, Integer.valueOf(state));

            return true;
        }
        catch (Throwable ignored) {}

        return false;
    }

    /**
     * Get the list of BasePartSeat instances for a vehicle via PackInfo.
     * Returns null if vehicle has no pack info or is not a DynamX vehicle.
     */
    private static java.util.List<?> getSeatList(Entity vehicle) throws Exception
    {
        Object packInfo = methodGetPackInfo.invoke(vehicle);
        if (packInfo == null) return null;
        Object seats = methodGetPartsByType.invoke(packInfo, classBasePartSeat);
        return seats instanceof java.util.List ? (java.util.List<?>) seats : null;
    }

    /**
     * Mount an entity onto a specific seat of a vehicle (by index in the
     * pack-info seat list). Returns true on success.
     */
    public static boolean mountSeat(Entity vehicle, Entity rider, int seatIndex)
    {
        if (!isAvailable())
        {
            System.out.println("[Blockbuster] mountSeat: DynamXCompat not available");
            return false;
        }
        if (vehicle == null || rider == null) return false;
        try
        {
            Object seatsModule = methodGetModuleByType.invoke(vehicle, classSeatsModule);
            if (seatsModule == null)
            {
                System.out.println("[Blockbuster] mountSeat: no SeatsModule on " + vehicle);
                return false;
            }

            java.util.List<?> seats = getSeatList(vehicle);
            if (seats == null || seats.isEmpty())
            {
                System.out.println("[Blockbuster] mountSeat: no seats on " + vehicle);
                return false;
            }
            if (seatIndex < 0 || seatIndex >= seats.size())
            {
                /* Fall back to first seat if recorded index is out of range */
                System.out.println("[Blockbuster] mountSeat: seatIndex " + seatIndex + " out of range (" + seats.size() + "), using 0");
                seatIndex = 0;
            }

            Object seat = seats.get(seatIndex);
            Object result = methodMountEntity.invoke(seat, vehicle, seatsModule, rider);
            boolean ok = result instanceof Boolean && (Boolean) result;
            if (!ok)
            {
                System.out.println("[Blockbuster] mountSeat: mountEntity returned false");
            }
            return ok;
        }
        catch (Throwable t)
        {
            System.out.println("[Blockbuster] mountSeat threw: " + t);
            return false;
        }
    }

    /**
     * Get the seat index that an entity is riding on, or -1 if not found.
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

            java.util.List<?> seats = getSeatList(vehicle);
            if (seats == null) return -1;
            for (int i = 0; i < seats.size(); i++)
            {
                if (seats.get(i) == seat) return i;
            }
        }
        catch (Throwable ignored) {}
        return -1;
    }

    /* --- Haende am Lenkrad --------------------------------------------------
     *
     * MWRP macht das fuer Spieler in ImmersiveHandAnimClientHandler, holt den
     * Lenkwinkel aber ueber Obfuscates SyncedPlayerData - also nur fuer
     * EntityPlayer. Fuer einen Actor lesen wir denselben Wert direkt am
     * Fahrzeug: WheelsModule.visualProperties[STEER_ANGLE des lenkenden Rads].
     * Damit braucht es weder MWRP noch Obfuscate noch ein eigenes Paket, und
     * der Winkel stimmt pro Bild statt nur pro Tick.
     */

    private static boolean drivingResolved;
    private static boolean drivingAvailable;
    private static Class<?> classWheelsModule;
    private static Method methodGetControllingPassenger;
    private static Method methodHasModuleOfType;
    private static Field fieldVisualProperties;
    private static Method methodWheelVariableGet;
    private static Method methodGetDirectingWheel;
    private static int steerAngleOrdinal = -1;
    private static int visualPropertyCount = -1;

    private static boolean initDriving()
    {
        if (drivingResolved) return drivingAvailable;
        drivingResolved = true;

        try
        {
            if (!isAvailable()) return false;

            classWheelsModule = Class.forName("fr.dynamx.common.entities.modules.WheelsModule");

            methodGetControllingPassenger = classSeatsModule.getMethod("getControllingPassenger");
            methodHasModuleOfType = classBaseVehicleEntity.getMethod("hasModuleOfType", Class.class);

            fieldVisualProperties = classWheelsModule.getField("visualProperties");
            methodWheelVariableGet = fieldVisualProperties.getType().getMethod("get");

            /* getDirectingWheel() is generated by Lombok on ModularVehicleInfo,
             * so look it up on the pack info instance's own class later. */
            Class<?> classInfo = Class.forName("fr.dynamx.common.contentpack.type.vehicle.ModularVehicleInfo");
            methodGetDirectingWheel = classInfo.getMethod("getDirectingWheel");

            Class<?> classProps = Class.forName("fr.dynamx.api.entities.VehicleEntityProperties$EnumVisualProperties");
            Object[] values = (Object[]) classProps.getMethod("values").invoke(null);
            visualPropertyCount = values.length;

            for (int i = 0; i < values.length; i++)
            {
                if ("STEER_ANGLE".equals(((Enum<?>) values[i]).name()))
                {
                    steerAngleOrdinal = i;
                    break;
                }
            }

            drivingAvailable = steerAngleOrdinal >= 0;

            if (!drivingAvailable)
            {
                System.out.println("[Blockbuster] DynamX steering: no STEER_ANGLE property found");
            }
        }
        catch (Throwable t)
        {
            drivingAvailable = false;
            System.out.println("[Blockbuster] DynamX steering unavailable: " + t);
        }

        return drivingAvailable;
    }

    /**
     * Puts both arms on the steering wheel while the entity drives, the same
     * pose MWRP gives a player. No-op when the entity is not the driver.
     */
    public static void applyDrivingArms(net.minecraft.client.model.ModelBiped model, Entity rider)
    {
        if (model == null || rider == null || !initDriving()) return;

        try
        {
            Entity vehicle = rider.getRidingEntity();

            if (!isVehicle(vehicle)) return;

            Object seats = methodGetModuleByType.invoke(vehicle, classSeatsModule);

            if (seats == null || methodGetControllingPassenger.invoke(seats) != rider) return;

            if (!Boolean.TRUE.equals(methodHasModuleOfType.invoke(vehicle, classWheelsModule))) return;

            Object wheels = methodGetModuleByType.invoke(vehicle, classWheelsModule);

            if (wheels == null) return;

            Object variable = fieldVisualProperties.get(wheels);
            Object raw = variable == null ? null : methodWheelVariableGet.invoke(variable);

            if (!(raw instanceof float[])) return;

            float[] properties = (float[]) raw;
            Object info = methodGetPackInfo.invoke(vehicle);

            if (info == null) return;

            int index = visualPropertyCount * ((Integer) methodGetDirectingWheel.invoke(info)).intValue() + steerAngleOrdinal;

            if (index < 0 || index >= properties.length) return;

            /* Halber Winkel, wie MWRP ihn auch nutzt - das volle Lenkrad waere
             * fuer die Arme zu viel. */
            float steer = properties[index] / 2F;

            if (!isFinite(steer)) return;

            model.bipedLeftArm.rotateAngleX = (float) Math.toRadians(-90F + steer);
            model.bipedRightArm.rotateAngleX = (float) Math.toRadians(-90F - steer);
            model.bipedLeftArm.rotateAngleY = 0F;
            model.bipedRightArm.rotateAngleY = 0F;
            model.bipedLeftArm.rotateAngleZ = 0F;
            model.bipedRightArm.rotateAngleZ = 0F;
        }
        catch (Throwable t)
        {
            System.out.println("[Blockbuster] DynamX steering pose failed: " + t);
        }
    }

    private static boolean isFinite(float value)
    {
        return !Float.isNaN(value) && !Float.isInfinite(value);
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
