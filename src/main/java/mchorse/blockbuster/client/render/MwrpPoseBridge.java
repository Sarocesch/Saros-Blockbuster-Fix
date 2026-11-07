package mchorse.blockbuster.client.render;

import mchorse.blockbuster.common.entity.EntityActor;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reicht die Pose eines Actors an MWRPs Posen-Speicher auf dem Client weiter
 * (PoseClientState, nach UUID). MWRP zeichnet sie dann am Spielermodell des
 * Actors (ActorPuppet traegt die UUID des Actors), genau wie bei einem Spieler.
 *
 * Nur per Reflection und ohne Client-Klassen, damit nichts haengt, wenn MWRP
 * fehlt oder sich aendert: beim ersten Fehler schaltet sich die Bruecke ab.
 */
public final class MwrpPoseBridge
{
    private static boolean resolved;
    private static Method methodSet;
    private static Method methodClearFading;

    private MwrpPoseBridge()
    {}

    /**
     * Vor dem Zeichnen: nur bei einer Aenderung setzen, jeder Aufruf startet bei
     * MWRP den Uebergang neu.
     */
    public static void sync(EntityActor actor)
    {
        if (actor.mwrpPose.equals(actor.appliedPose) || !resolve())
        {
            return;
        }

        try
        {
            methodSet.invoke(null, actor.getUniqueID(), actor.mwrpPose, actor.world.getTotalWorldTime());
            actor.appliedPose = actor.mwrpPose;
        }
        catch (Throwable t)
        {
            disable(t);
        }
    }

    /** Actor verschwindet vom Client: Eintrag bei MWRP entfernen, sonst bleiben Leichen. */
    public static void clear(EntityActor actor)
    {
        if (actor.appliedPose.isEmpty() || methodSet == null)
        {
            actor.appliedPose = "";

            return;
        }

        try
        {
            UUID uuid = actor.getUniqueID();

            methodSet.invoke(null, uuid, "", 0L);
            methodClearFading.invoke(null, uuid);
        }
        catch (Throwable t)
        {
            disable(t);
        }

        actor.appliedPose = "";
    }

    private static boolean resolve()
    {
        if (!resolved)
        {
            resolved = true;

            try
            {
                Class<?> state = Class.forName("de.sarocesch.minewacheroleplaymod.animation.pose.PoseClientState");

                methodSet = state.getMethod("set", UUID.class, String.class, long.class);
                methodClearFading = state.getMethod("clearFading", UUID.class);
            }
            catch (Throwable t)
            {
                methodSet = null;
                System.out.println("[Blockbuster] MWRP-Posen nicht gefunden, Actors zeigen keine Posen: " + t);
            }
        }

        return methodSet != null;
    }

    private static void disable(Throwable t)
    {
        methodSet = null;
        System.out.println("[Blockbuster] MWRP-Pose am Actor fehlgeschlagen, Posen fuer Actors aus: " + t);
    }
}
