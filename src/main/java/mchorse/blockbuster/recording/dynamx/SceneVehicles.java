package mchorse.blockbuster.recording.dynamx;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.utils.EntityUtils;
import net.minecraft.entity.Entity;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Szenen-Fahrzeuge finden, auch in entladenen Chunks, und Geister-Kopien entfernen.
 *
 * <p>Blockbuster fand ein Fahrzeug nur unter den geladenen Entities. Lag es in einem
 * entladenen Chunk, baute die Szene eine zweite Kopie mit derselben UUID. Minecraft
 * lehnte die alte beim naechsten Chunk-Laden ab ("Keeping entity ... that already exists
 * with UUID"), liess sie aber im Chunk stehen und speicherte sie immer wieder mit. Wurde
 * die echte Kopie abgebaut, nahm Minecraft beim naechsten Laden die alte an - das
 * abgebaute Auto war wieder da.</p>
 *
 * <p>Jede neu aufgebaute Kopie bekommt einen Stempel (ForgeData), die neueste steht in
 * {@link SceneVehicleData}. Beim Chunk-Laden fliegt jede Kopie raus, die abgelehnt
 * wurde oder aelter ist als die neueste.</p>
 */
public class SceneVehicles
{
    public static final String STAMP = "BlockbusterVehicleStamp";

    /**
     * Fahrzeug per UUID suchen. Mit Startpose wird deren Chunk notfalls geladen und
     * nochmal gesucht - sonst entstuende beim Neuaufbau ein Duplikat.
     */
    public static Entity find(World world, UUID id, boolean hasStart, double x, double z)
    {
        if (world.isRemote)
        {
            return EntityUtils.entityByUUID(world, id);
        }

        Entity entity = lookup(world, id);

        if (entity == null && hasStart && ensureChunk(world, x, z))
        {
            entity = lookup(world, id);
        }

        return entity;
    }

    private static Entity lookup(World world, UUID id)
    {
        if (world instanceof WorldServer)
        {
            Entity entity = ((WorldServer) world).getEntityFromUuid(id);

            if (entity != null)
            {
                return entity;
            }
        }

        return EntityUtils.entityByUUID(world, id);
    }

    /**
     * Den Chunk an dieser Position sofort laden. Forge-Tickets laden erst im naechsten
     * Tick - so lange wartet der Szenenstart nicht, und ein Spawn in einen ungeladenen
     * Chunk scheitert still.
     *
     * @return true, wenn der Chunk erst jetzt geladen wurde
     */
    public static boolean ensureChunk(World world, double x, double z)
    {
        int cx = MathHelper.floor(x) >> 4;
        int cz = MathHelper.floor(z) >> 4;

        if (world.getChunkProvider().getLoadedChunk(cx, cz) != null)
        {
            return false;
        }

        world.getChunkFromChunkCoords(cx, cz);

        return true;
    }

    /**
     * Frisch aufgebaute Kopie stempeln, bevor sie in die Welt kommt.
     */
    public static void stamp(Entity vehicle)
    {
        long stamp = Math.max(vehicle.world.getTotalWorldTime(), SceneVehicleData.get(vehicle.world).get(vehicle.getUniqueID()) + 1);

        vehicle.getEntityData().setLong(STAMP, stamp);
    }

    /**
     * Die Kopie ist in der Welt: ab jetzt ist sie die gueltige.
     */
    public static void adopt(Entity vehicle)
    {
        long stamp = vehicle.getEntityData().getLong(STAMP);

        if (stamp > 0)
        {
            SceneVehicleData.get(vehicle.world).put(vehicle.getUniqueID(), stamp);
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event)
    {
        World world = event.getWorld();

        if (world == null || world.isRemote || !(world instanceof WorldServer) || !DynamXCompat.isAvailable())
        {
            return;
        }

        Chunk chunk = event.getChunk();
        SceneVehicleData data = null;

        for (ClassInheritanceMultiMap<Entity> list : chunk.getEntityLists())
        {
            for (Entity entity : new ArrayList<Entity>(list))
            {
                if (entity.isDead || !DynamXCompat.isVehicle(entity))
                {
                    continue;
                }

                UUID id = entity.getUniqueID();
                Entity owner = ((WorldServer) world).getEntityFromUuid(id);
                boolean refused = owner != null && owner != entity;

                if (data == null)
                {
                    data = SceneVehicleData.get(world);
                }

                long newest = data.get(id);
                long mine = entity.getEntityData().getLong(STAMP);

                if (newest > 0 && mine > newest)
                {
                    /* Die Registry hinkt hinterher (Absturz vor dem Speichern) - die
                     * neuere Kopie wird nie entfernt. */
                    data.put(id, mine);
                    newest = mine;
                }

                boolean outdated = newest > 0 && mine < newest;

                if (!refused && !outdated)
                {
                    continue;
                }

                this.purge(chunk, entity, refused);
            }
        }
    }

    private void purge(Chunk chunk, Entity vehicle, boolean refused)
    {
        for (Entity passenger : new ArrayList<Entity>(vehicle.getPassengers()))
        {
            passenger.dismountRidingEntity();

            if (passenger instanceof EntityActor)
            {
                passenger.setDead();
            }
        }

        if (refused)
        {
            /* Abgelehnte Entities bleiben sonst im Chunk und werden mitgespeichert. */
            chunk.removeEntity(vehicle);
        }

        /* Nicht setDead(): DynamX kippt dabei den Kofferraum aus (StorageModule.onSetDead). */
        vehicle.isDead = true;
        chunk.markDirty();

        System.out.println(String.format(java.util.Locale.ROOT, "[Blockbuster] Fahrzeug %s: alte Kopie bei %.1f %.1f %.1f entfernt (%s)",
            vehicle.getUniqueID(), vehicle.posX, vehicle.posY, vehicle.posZ, refused ? "abgelehnt" : "veraltet"));
    }
}
