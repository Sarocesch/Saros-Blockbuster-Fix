package mchorse.blockbuster.recording.scene;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.capabilities.recording.IRecording;
import mchorse.blockbuster.capabilities.recording.Recording;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.actions.VehicleMountAction;
import mchorse.blockbuster.recording.data.Record;
import mchorse.blockbuster.recording.dynamx.DynamXCompat;
import mchorse.blockbuster.recording.dynamx.DynamXVehiclePin;
import mchorse.blockbuster.recording.dynamx.HydraulicCompat;
import mchorse.blockbuster.recording.dynamx.SceneVehicleData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Raeumt Szenen weg: laufende Szenen, die am Szenenende stehen gelassenen Actors
 * (Freeze/Idle), Leichen, die noch im Fahrzeugsitz haengen, und die Fahrzeuge, in denen
 * sie sassen. Nur freigegeben fiel ein Flugzeug vom Himmel und blieb liegen; geloescht
 * baut resetVehicles es beim naechsten Abspielen aus dem Snapshot an der Startpose neu.
 *
 * Wird per Reflection aus MWRP (/fixall) aufgerufen, Signaturen stabil halten.
 */
public final class SceneCleaner
{
    private SceneCleaner()
    {}

    /**
     * @param sceneId Szenenname oder null fuer alle
     * @param world Mittelpunkt-Welt, nur mit radius >= 0 noetig
     * @param radius Umkreis um x/y/z, kleiner 0 = ueberall
     * @return Anzahl entfernter Actors
     */
    public static int clear(MinecraftServer server, String sceneId, World world, double x, double y, double z, double radius)
    {
        Area area = new Area(sceneId, world, x, y, z, radius);
        Set<Entity> removed = Collections.newSetFromMap(new IdentityHashMap<Entity, Boolean>());
        Set<Entity> vehicles = Collections.newSetFromMap(new IdentityHashMap<Entity, Boolean>());

        /* Laufende Szenen hart stoppen, ohne Freeze. Bei einem Namen gilt kein Umkreis. */
        for (Scene scene : CommonProxy.scenes.getScenes().values())
        {
            if (!scene.playing || scene.getWorld() == null || scene.getWorld().isRemote)
            {
                continue;
            }

            if (sceneId != null ? sceneId.equals(scene.getId()) : area.containsAny(scene))
            {
                for (RecordPlayer player : scene.actors.values())
                {
                    if (!player.realPlayer && player.actor != null)
                    {
                        removed.add(player.actor);
                        release(player.actor, vehicles);
                    }
                }

                scene.stopPlayback(true, true);
            }
        }

        /* Idle-Schleifen und Waisen, die keine Szene mehr kennt. */
        for (RecordPlayer player : new ArrayList<RecordPlayer>(CommonProxy.manager.players.values()))
        {
            if (!player.realPlayer && player.actor != null && area.matches(player.actor))
            {
                player.kill = true;
                release(player.actor, vehicles);
                CommonProxy.manager.stop(player);
                removed.add(player.actor);
            }
        }

        for (WorldServer worldServer : server.worlds)
        {
            for (Entity entity : new ArrayList<Entity>(worldServer.loadedEntityList))
            {
                if (entity instanceof EntityActor && area.matches(entity) && isSceneActor((EntityActor) entity))
                {
                    remove(entity, vehicles);
                    removed.add(entity);
                }
                else if (entity instanceof EntityPlayerMP && isFakePlayer((EntityPlayerMP) entity) && area.matches(entity))
                {
                    /* Schon abgemeldete (Stopp oben) nur noch aus der Liste nehmen. */
                    if (!entity.isDead)
                    {
                        release(entity, vehicles);
                        entity.dismountRidingEntity();
                        server.getPlayerList().playerLoggedOut((EntityPlayerMP) entity);
                    }
                    else
                    {
                        remove(entity, vehicles);
                    }

                    removed.add(entity);
                }
            }
        }

        for (Entity vehicle : vehicles)
        {
            removeVehicle(vehicle);
        }

        Scene.markCleared(sceneId, server.worlds[0].getTotalWorldTime());

        return removed.size();
    }

    /**
     * Stehen gelassene Szenen-Actors in allen geladenen Welten (fuer /fixall status).
     */
    public static int count(MinecraftServer server)
    {
        int count = 0;

        for (WorldServer worldServer : server.worlds)
        {
            for (Entity entity : new ArrayList<Entity>(worldServer.loadedEntityList))
            {
                /* Laufende Szenen zaehlen nicht, Freeze/Idle-Reste und Leichen schon. */
                if (entity instanceof EntityActor && isSceneActor((EntityActor) entity)
                        && (entity.isDead || ((EntityActor) entity).playback == null || !((EntityActor) entity).leftoverScene.isEmpty()))
                {
                    count++;
                }
            }
        }

        return count;
    }

    /** Von Hand gesetzte Actors (ohne Szene) bleiben stehen. Leichen werden immer mitgenommen. */
    private static boolean isSceneActor(EntityActor actor)
    {
        return actor.isDead || actor.wasAttached || !actor.leftoverScene.isEmpty();
    }

    private static boolean isFakePlayer(EntityPlayerMP player)
    {
        IRecording recording = Recording.get(player);

        return recording != null && recording.isFakePlayer();
    }

    /**
     * Erst aus dem Sitz, dann sofort aus der Welt: ein toter Mitfahrer wird von
     * World.updateEntities nie ausgetragen und bleibt beim Client sichtbar.
     */
    private static void remove(Entity entity, Set<Entity> vehicles)
    {
        release(entity, vehicles);
        entity.dismountRidingEntity();
        entity.world.removeEntityDangerously(entity);
    }

    /** Das gefahrene Fahrzeug zum Loeschen vormerken und seine Wracks wegraeumen. */
    private static void release(Entity entity, Set<Entity> vehicles)
    {
        Entity vehicle = entity.getRidingEntity();

        if (vehicle != null && DynamXCompat.isVehicle(vehicle))
        {
            DynamXVehiclePin.release(entity);
            HydraulicCompat.clearWrecks(vehicle);
            vehicles.add(vehicle);
        }
    }

    /**
     * /scene clear &lt;name|all&gt; cars: zusaetzlich alle Fahrzeuge der Szene(n) entfernen,
     * auch wenn gerade kein Actor drinsitzt. Gemeint sind nur Szenen-Fahrzeuge (in einer
     * Aufnahme der Szene eingestiegen oder von einer Szene neu aufgebaut) - Autos von
     * Spielern bleiben stehen. Beim naechsten Abspielen baut die Szene sie neu auf.
     *
     * @return Anzahl entfernter Fahrzeuge
     */
    public static int clearVehicles(MinecraftServer server, String sceneId)
    {
        Set<UUID> targets = new HashSet<UUID>();
        List<String> sceneIds = sceneId != null ? Collections.singletonList(sceneId) : CommonProxy.scenes.sceneFiles();

        for (String id : sceneIds)
        {
            Scene scene = CommonProxy.scenes.getScenes().get(id);

            if (scene == null)
            {
                try
                {
                    scene = CommonProxy.scenes.load(id);
                }
                catch (Exception ignored)
                {}
            }

            if (scene == null)
            {
                continue;
            }

            for (Replay replay : scene.replays)
            {
                collectVehicles(replay.id, targets);
                collectVehicles(replay.idleRecord, targets);
            }
        }

        if (sceneId == null)
        {
            for (WorldServer worldServer : server.worlds)
            {
                targets.addAll(SceneVehicleData.get(worldServer).stamps.keySet());
            }
        }

        int count = 0;

        for (WorldServer worldServer : server.worlds)
        {
            for (Entity entity : new ArrayList<Entity>(worldServer.loadedEntityList))
            {
                if (!entity.isDead && targets.contains(entity.getUniqueID()) && DynamXCompat.isVehicle(entity) && removeVehicle(entity))
                {
                    count++;
                }
            }
        }

        return count;
    }

    private static void collectVehicles(String recordId, Set<UUID> targets)
    {
        if (recordId == null || recordId.isEmpty())
        {
            return;
        }

        try
        {
            Record record = CommonProxy.manager.get(recordId);

            if (record == null || record.actions == null)
            {
                return;
            }

            for (List<Action> tick : record.actions)
            {
                if (tick == null) continue;

                for (Action action : tick)
                {
                    if (action instanceof VehicleMountAction && ((VehicleMountAction) action).isMounting)
                    {
                        targets.add(((VehicleMountAction) action).target);
                    }
                }
            }
        }
        catch (Exception ignored)
        {}
    }

    /**
     * Ohne Abbild liesse sich das Fahrzeug nicht neu aufbauen - dann bleibt es stehen und
     * wird nur der Physik zurueckgegeben, statt fuer immer zu verschwinden.
     *
     * @return true, wenn es entfernt wurde
     */
    private static boolean removeVehicle(Entity vehicle)
    {
        if (vehicle.isDead)
        {
            return false;
        }

        DynamXVehiclePin.unpin(vehicle);

        if (!VehicleMountAction.preserveForRebuild(vehicle))
        {
            return false;
        }

        for (Entity passenger : new ArrayList<Entity>(vehicle.getPassengers()))
        {
            passenger.dismountRidingEntity();
        }

        /* removeEntityDangerously ruft selbst setDead - ein zweites setDead liess DynamX
         * den Kofferraum doppelt auskippen. */
        vehicle.world.removeEntityDangerously(vehicle);

        return true;
    }

    private static class Area
    {
        private final String sceneId;
        private final World world;
        private final double x;
        private final double y;
        private final double z;
        private final double radiusSq;

        Area(String sceneId, World world, double x, double y, double z, double radius)
        {
            this.sceneId = sceneId;
            this.world = radius >= 0 ? world : null;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radiusSq = radius * radius;
        }

        boolean matches(Entity entity)
        {
            if (this.sceneId != null)
            {
                return entity instanceof EntityActor && this.sceneId.equals(((EntityActor) entity).leftoverScene);
            }

            return this.world == null || entity.world == this.world && entity.getDistanceSq(this.x, this.y, this.z) <= this.radiusSq;
        }

        boolean containsAny(Scene scene)
        {
            for (Map.Entry<Replay, RecordPlayer> entry : scene.actors.entrySet())
            {
                EntityLivingBase actor = entry.getValue().actor;

                if (actor != null && (this.world == null || actor.world == this.world && actor.getDistanceSq(this.x, this.y, this.z) <= this.radiusSq))
                {
                    return true;
                }
            }

            return false;
        }
    }
}
