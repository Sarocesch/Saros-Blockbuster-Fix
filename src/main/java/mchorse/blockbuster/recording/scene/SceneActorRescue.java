package mchorse.blockbuster.recording.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.recording.PacketPlayback;
import mchorse.blockbuster.network.common.recording.PacketSyncTick;
import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.recording.data.Record;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Faengt Actors auf, deren Chunk waehrend der Wiedergabe entladen wurde.
 *
 * <p>Minecraft speichert einen Actor mit seinem Chunk und entfernt ihn aus der Welt.
 * Die Szene spielte danach ein Exemplar weiter, das in keiner Welt mehr stand, und
 * versuchte jeden Tick, es neu zu spawnen. Kam der Chunk zurueck, entstand aus dem
 * Speicherstand eine zweite Kopie: ohne Wiedergabe, also verwaist, und das Auto, in
 * dem sie sass, blieb einfach stehen. Hier wird diese Kopie stattdessen an die
 * laufende Wiedergabe gehaengt - Actor und Auto fahren dort weiter, wo die Szene
 * gerade ist.</p>
 *
 * <p>Das mitwandernde Chunk-Fenster ({@link ScenePlaybackChunks}) verhindert das
 * Entladen normalerweise. Das hier ist das Netz darunter.</p>
 */
public class SceneActorRescue
{
    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event)
    {
        World world = event.getWorld();

        if (world == null || world.isRemote || !(world instanceof WorldServer))
        {
            return;
        }

        try
        {
            Chunk chunk = event.getChunk();
            List<EntityActor> actors = new ArrayList<EntityActor>();

            for (ClassInheritanceMultiMap<Entity> list : chunk.getEntityLists())
            {
                for (EntityActor actor : list.getByClass(EntityActor.class))
                {
                    actors.add(actor);
                }
            }

            for (EntityActor actor : actors)
            {
                if (actor.isDead)
                {
                    continue;
                }

                Entity registered = ((WorldServer) world).getEntityFromUuid(actor.getUniqueID());

                if (registered != null && registered != actor)
                {
                    /* Von Vanilla abgelehnt ("Keeping entity"), weil das Exemplar mit
                     * dieser UUID noch in der Welt steht. Die Kopie bleibt sonst
                     * unsichtbar im Chunk liegen und wird bei jedem Speichern mitgeschrieben. */
                    actor.dismountRidingEntity();
                    chunk.removeEntity(actor);
                    actor.isDead = true;
                    chunk.markDirty();

                    continue;
                }

                if (registered == actor)
                {
                    RecordPlayer player = findPlayer(actor.getUniqueID(), actor);

                    if (player != null)
                    {
                        adopt(player, actor);
                    }
                }
            }
        }
        catch (Throwable t)
        {
            System.out.println("[Blockbuster] Actor-Ruecknahme beim Chunk-Laden fehlgeschlagen: " + t);
        }
    }

    /**
     * Fuer {@link RecordPlayer#checkAndSpawn()}: steht der Actor nicht mehr in der
     * Welt, aber eine Kopie mit seiner UUID, wird die Kopie uebernommen.
     *
     * @return true, wenn uebernommen wurde - dann nicht mehr spawnen
     */
    public static boolean adoptReloaded(RecordPlayer player)
    {
        if (!(player.actor instanceof EntityActor) || !(player.actor.world instanceof WorldServer))
        {
            return false;
        }

        Entity other = ((WorldServer) player.actor.world).getEntityFromUuid(player.actor.getUniqueID());

        if (!(other instanceof EntityActor) || other == player.actor || other.isDead)
        {
            return false;
        }

        adopt(player, (EntityActor) other);

        return true;
    }

    private static RecordPlayer findPlayer(UUID uuid, Entity except)
    {
        for (RecordPlayer player : new ArrayList<RecordPlayer>(CommonProxy.manager.players.values()))
        {
            EntityLivingBase actor = player.actor;

            if (actor instanceof EntityActor && actor != except && !actor.isDead && uuid.equals(actor.getUniqueID()))
            {
                return player;
            }
        }

        return null;
    }

    private static void adopt(RecordPlayer player, EntityActor copy)
    {
        EntityLivingBase old = player.actor;
        Entity oldMount = old.getRidingEntity();

        CommonProxy.manager.players.remove(old);
        player.actor = copy;
        CommonProxy.manager.players.put(copy, player);
        copy.adoptPlayback(player);
        player.unsentPackets.clear();

        Replay replay = player.getReplay();

        if (replay != null)
        {
            replay.apply(copy);

            if (player.record != null)
            {
                player.record.applyPreviousMorph(copy, replay, player.tick, Record.MorphType.FORCE);
            }
        }

        /* Sass das alte Exemplar in einem Auto, die Kopie aber nicht (der Sitz wurde
         * nicht mitgespeichert), wieder einsteigen - sonst faehrt das Auto nicht mit. */
        if (copy.getRidingEntity() == null && oldMount != null)
        {
            Entity vehicle = ((WorldServer) copy.world).getEntityFromUuid(oldMount.getUniqueID());

            if (vehicle != null && !vehicle.isDead)
            {
                copy.startRiding(vehicle, true);
            }
        }

        /* Das alte Exemplar ist aus der Welt; als tot markiert raeumt sich auch sein
         * Hilfsspieler aus der Entity-Liste. */
        old.isDead = true;

        if (player.record != null)
        {
            Dispatcher.sendToTracked(copy, new PacketPlayback(copy.getEntityId(), true, player.realPlayer, player.record.filename, replay));
            Dispatcher.sendToTracked(copy, new PacketSyncTick(copy.getEntityId(), player.tick));
        }

        System.out.println("[Blockbuster] Actor " + copy.getUniqueID() + " nach Chunk-Neuladen wieder an die Wiedergabe gehaengt (Tick "
            + player.tick + (copy.getRidingEntity() != null ? ", sitzt im Fahrzeug" : "") + ")");
    }
}
