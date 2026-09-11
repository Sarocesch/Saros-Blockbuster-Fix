package mchorse.blockbuster.recording.dynamx;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import fr.dynamx.api.physics.IPhysicsWorld;
import fr.dynamx.common.DynamXContext;
import fr.dynamx.common.entities.PhysicsEntity;
import fr.dynamx.common.physics.entities.AbstractEntityPhysicsHandler;
import net.minecraft.entity.Entity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Fuehrt ein DynamX-Fahrzeug beim Abspielen kinematisch auf der aufgenommenen Bahn.
 *
 * <p>Bisher wurden nur die Steuertasten wiedergegeben, und die Physik fuhr daraus ihre
 * eigene Linie - je schneller, desto weiter daneben. Ein vanilla setPosition hilft nicht:
 * DynamX ueberschreibt die Minecraft-Position jeden Tick aus physicsPosition
 * (PhysicsEntity.updateMinecraftPos). Deshalb wird der Koerper hier kinematisch gemacht und
 * die aufgenommene Transform direkt ueber die Physik-API gesetzt, genau so, wie DynamX selbst
 * ein gegriffenes Objekt fuehrt (MoveObjects.preUpdatePhysics).</p>
 *
 * <p>Referenziert DynamX- und jme3-Typen direkt. Nur nach DynamXCompat.isAvailable()
 * aufrufen - ohne DynamX wird die Klasse dann nie geladen.</p>
 *
 * <p>Alle Tabellen gibt es je Seite doppelt: Entity.equals vergleicht nur die Entity-ID, und
 * im Einzelspieler teilen sich Server- und Client-Fahrzeug dieselbe ID.</p>
 */
public final class DynamXVehiclePin
{
    /** Letzte gepinnte Position je Fahrzeug, fuer die Geschwindigkeit. [0] = Server, [1] = Client */
    @SuppressWarnings("unchecked")
    private static final Map<Entity, float[]>[] LAST = new Map[] {new WeakHashMap<Entity, float[]>(), new WeakHashMap<Entity, float[]>()};

    /** Welcher Actor gerade welches Fahrzeug fuehrt. */
    @SuppressWarnings("unchecked")
    private static final Map<Entity, Entity>[] DRIVEN = new Map[] {new WeakHashMap<Entity, Entity>(), new WeakHashMap<Entity, Entity>()};

    /** Zaehler fuer die Abweichungs-Diagnose (nur Server). */
    private static final Map<Entity, int[]> REPORT = new WeakHashMap<Entity, int[]>();

    private static boolean conventionChecked;
    private static boolean warned;

    private DynamXVehiclePin()
    {}

    private static int side(Entity entity)
    {
        return entity.world.isRemote ? 1 : 0;
    }

    /**
     * Aufnahme: Transform des Fahrzeugs als {x, y, z, qx, qy, qz, qw}, oder null.
     *
     * <p>Gelesen werden die Werte, die DynamX im Minecraft-Thread gerade gesetzt hat
     * (posX/Y/Z und renderRotation, beide aus updateMinecraftPos), damit nichts halb vom
     * Physik-Thread geschrieben ist.</p>
     */
    public static float[] readTransform(Entity vehicle)
    {
        if (!(vehicle instanceof PhysicsEntity))
        {
            return null;
        }

        PhysicsEntity<?> entity = (PhysicsEntity<?>) vehicle;
        Quaternion rotation = entity.renderRotation;

        checkConvention(entity, rotation);

        return new float[] {(float) entity.posX, (float) entity.posY, (float) entity.posZ,
            rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW()};
    }

    /**
     * Abspielen: Fahrzeug kinematisch auf die aufgenommene Transform setzen.
     */
    public static void pin(Entity actor, Entity vehicle, float x, float y, float z, float qx, float qy, float qz, float qw)
    {
        if (!(vehicle instanceof PhysicsEntity))
        {
            return;
        }

        PhysicsEntity<?> entity = (PhysicsEntity<?>) vehicle;
        int side = side(vehicle);
        float[] previous = LAST[side].get(vehicle);
        Vector3f velocity = new Vector3f();

        if (previous != null)
        {
            float dx = x - previous[0];
            float dy = y - previous[1];
            float dz = z - previous[2];

            /* Unter zehn Bloecken ist es Fahrt, darueber ein Sprung im Zeitstrahl.
             * Die Geschwindigkeit braucht DynamX fuer Tacho, Motorklang und Raddrehung. */
            if (dx * dx + dy * dy + dz * dz < 100F)
            {
                velocity.set(dx * 20F, dy * 20F, dz * 20F);
            }

            if (side == 0)
            {
                report(entity, previous, velocity);
            }
        }

        LAST[side].put(vehicle, new float[] {x, y, z});
        DRIVEN[side].put(actor, vehicle);

        apply(entity, new Vector3f(x, y, z), new Quaternion(qx, qy, qz, qw), velocity, true);
    }

    /**
     * Abspielen alter Aufnahmen ohne Fahrzeug-Transform: Rotation aus Gier und Neigung
     * (dieselbe Formel wie DynamXGeometry.eulerToQuaternion, nur ohne Objekt-Pool).
     */
    public static void pinFromYawPitch(Entity actor, Entity vehicle, float x, float y, float z, float yaw, float pitch)
    {
        Quaternion rotation = new Quaternion().fromAngles((float) Math.toRadians(-pitch), (float) Math.toRadians(-yaw), 0F);

        pin(actor, vehicle, x, y, z, rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW());
    }

    /**
     * Der Actor faehrt nicht mehr: sein zuletzt gefuehrtes Fahrzeug freigeben.
     */
    public static void release(Entity actor)
    {
        Entity vehicle = DRIVEN[side(actor)].remove(actor);

        if (vehicle != null)
        {
            unpin(vehicle);
        }
    }

    /**
     * Fahrzeug wieder der Physik ueberlassen - sonst bleibt es fuer immer steif.
     */
    public static void unpin(Entity vehicle)
    {
        if (LAST[side(vehicle)].remove(vehicle) == null || !(vehicle instanceof PhysicsEntity))
        {
            return;
        }

        apply((PhysicsEntity<?>) vehicle, null, null, new Vector3f(), false);
    }

    public static boolean isPinned(Entity vehicle)
    {
        return vehicle != null && LAST[side(vehicle)].containsKey(vehicle);
    }

    private static void apply(final PhysicsEntity<?> entity, final Vector3f position, final Quaternion rotation, final Vector3f velocity, final boolean freeze)
    {
        Runnable task = new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    AbstractEntityPhysicsHandler<?, ?> handler = entity.physicsHandler;

                    if (handler == null)
                    {
                        return;
                    }

                    handler.setFreezePhysics(freeze);

                    if (position != null)
                    {
                        handler.setPhysicsPosition(position);
                    }

                    if (rotation != null)
                    {
                        handler.setPhysicsRotation(rotation);
                    }

                    handler.setLinearVelocity(velocity);
                    handler.setAngularVelocity(new Vector3f());
                }
                catch (Throwable t)
                {
                    warnOnce(t);
                }
            }
        };

        if (entity.usesPhysicsWorld())
        {
            IPhysicsWorld world = DynamXContext.getPhysicsWorld(entity.world);

            if (world != null)
            {
                /* Vor dem naechsten Physikschritt, im Physik-Thread. Im Einzelspieler
                 * rechnet die Physik in einem eigenen Thread - direkt setzen wuerde mit
                 * dem Schritt kollidieren. */
                world.schedule(task);

                return;
            }
        }

        /* Keine Physikwelt auf dieser Seite (Server im Einzelspieler, Zuschauer-Client):
         * direkt die Felder setzen, aus denen DynamX Position und Darstellung liest. */
        if (position != null)
        {
            entity.physicsPosition.set(position);
        }

        if (rotation != null)
        {
            entity.physicsRotation.set(rotation);
        }
    }

    /**
     * Alle 20 Pins: wie weit hat die Physik das Fahrzeug seit dem letzten Pin bewegt?
     * Bei kinematischer Fuehrung ~0. Das ist der objektive Beweis fuer "exakte Bahn".
     */
    private static void report(PhysicsEntity<?> entity, float[] previous, Vector3f velocity)
    {
        int[] counter = REPORT.get(entity);

        if (counter == null)
        {
            counter = new int[1];
            REPORT.put(entity, counter);
        }

        if (counter[0]++ % 20 != 0)
        {
            return;
        }

        float dx = entity.physicsPosition.x - previous[0];
        float dy = entity.physicsPosition.y - previous[1];
        float dz = entity.physicsPosition.z - previous[2];

        System.out.println(String.format(java.util.Locale.ROOT,
            "[Blockbuster] Fahrzeug-Pin %d: Abweichung von der Bahn %.3f Bloecke, Tempo %.1f m/s",
            entity.getEntityId(), Math.sqrt(dx * dx + dy * dy + dz * dz), velocity.length()));
    }

    /**
     * Einmal pro Sitzung pruefen, ob die Formel fuer alte Aufnahmen stimmt: echte Rotation
     * gegen die aus Gier und Neigung rekonstruierte.
     */
    private static void checkConvention(PhysicsEntity<?> entity, Quaternion actual)
    {
        if (conventionChecked)
        {
            return;
        }

        conventionChecked = true;

        Quaternion rebuilt = new Quaternion().fromAngles((float) Math.toRadians(-entity.rotationPitch), (float) Math.toRadians(-entity.rotationYaw), 0F);

        System.out.println(String.format(java.util.Locale.ROOT,
            "[Blockbuster] Fahrzeug-Rotation echt=(%.3f %.3f %.3f %.3f) aus Gier/Neigung=(%.3f %.3f %.3f %.3f)",
            actual.getX(), actual.getY(), actual.getZ(), actual.getW(),
            rebuilt.getX(), rebuilt.getY(), rebuilt.getZ(), rebuilt.getW()));
    }

    private static void warnOnce(Throwable t)
    {
        if (!warned)
        {
            warned = true;
            System.out.println("[Blockbuster] Fahrzeug-Pin fehlgeschlagen: " + t);
        }
    }
}
