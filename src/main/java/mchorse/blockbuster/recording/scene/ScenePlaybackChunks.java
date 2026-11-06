package mchorse.blockbuster.recording.scene;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.recording.data.Record;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.LoadingCallback;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Haelt waehrend einer Szene die Chunks geladen, in denen ihre Actors gerade sind.
 *
 * <p>Ohne Spieler in der Naehe entlaedt Minecraft einen Chunk beim naechsten
 * Autosave. Faehrt dort gerade ein Szenen-Auto, wird es mitsamt Actor gespeichert
 * und entfernt - die Szene spielt den Actor weiter, das Auto bleibt stehen.</p>
 *
 * <p>Frueher wurde die ganze Strecke beim Start festgehalten. Das brach bei langen
 * Fahrten an der Obergrenze ab, kostete pro Szene bis zu 41 Forge-Tickets und fehlte
 * ganz, wenn die Szene ueber den Kamera-Editor gespawnt wurde. Jetzt wandert ein
 * Fenster mit: 5x5 Chunks um jeden Actor und die Bahn der naechsten Sekunden voraus.
 * Es wird jeden Tick aus der Szene nachgefuehrt, egal wie sie gestartet wurde.</p>
 */
public final class ScenePlaybackChunks
{
    /** Notbremse gegen kaputte Aufnahmen. */
    private static final int MAX_CHUNKS = 2048;

    /** Radius um die aktuelle Position, in Chunks. */
    private static final int AROUND = 2;

    /** Wie weit die Bahn vorausgeladen wird, in Ticks. */
    private static final int AHEAD = 100;
    private static final int AHEAD_STEP = 5;

    /** So lange bleibt ein Chunk nach dem letzten Bedarf noch gehalten, in Ticks. */
    private static final int KEEP = 200;

    private static final Map<String, Window> HELD = new HashMap<String, Window>();

    private static boolean prepared;

    private ScenePlaybackChunks()
    {}

    private static final class Window
    {
        final World world;
        final List<Ticket> tickets = new ArrayList<Ticket>();
        final Map<Ticket, Integer> used = new HashMap<Ticket, Integer>();
        final Map<Long, Ticket> forced = new HashMap<Long, Ticket>();
        final Map<Long, Long> lastWanted = new HashMap<Long, Long>();
        boolean warned;

        Window(World world)
        {
            this.world = world;
        }

        Ticket ticketWithRoom()
        {
            for (Ticket ticket : this.tickets)
            {
                Integer count = this.used.get(ticket);

                if (count == null || count.intValue() < ticket.getMaxChunkListDepth())
                {
                    return ticket;
                }
            }

            Ticket ticket = ForgeChunkManager.requestTicket(Blockbuster.instance, this.world, Type.NORMAL);

            if (ticket != null)
            {
                this.tickets.add(ticket);
                this.used.put(ticket, 0);
            }

            return ticket;
        }
    }

    /**
     * Neu ansetzen: alte Tickets der Szene weg, Fenster um die jetzigen Actors.
     */
    public static void hold(Scene scene)
    {
        if (scene == null || scene.getId() == null || scene.getId().isEmpty())
        {
            return;
        }

        release(scene.getId(), false);
        update(scene);
    }

    /**
     * Fenster nachfuehren. Jeden Tick aus {@link Scene#tick()}.
     */
    public static void update(Scene scene)
    {
        String id = scene.getId();
        World world = scene.getWorld();

        if (id == null || id.isEmpty() || world == null || world.isRemote || !scene.playing)
        {
            return;
        }

        try
        {
            Window window = HELD.get(id);

            if (window != null && window.world != world)
            {
                release(id, false);
                window = null;
            }

            if (window == null)
            {
                prepare();

                window = new Window(world);
                HELD.put(id, window);
            }

            long now = world.getTotalWorldTime();
            Set<Long> wanted = new HashSet<Long>();

            for (RecordPlayer player : scene.actors.values())
            {
                if (player == null || player.realPlayer || player.actor == null || player.actor.world != world)
                {
                    continue;
                }

                EntityLivingBase actor = player.actor;
                int cx = MathHelper.floor(actor.posX) >> 4;
                int cz = MathHelper.floor(actor.posZ) >> 4;

                area(wanted, cx, cz, AROUND);
                loadNow(world, cx, cz);

                Record record = player.record;

                if (record == null || record.frames == null || record.frames.isEmpty())
                {
                    continue;
                }

                int size = record.frames.size();
                int base = player.tick - record.preDelay;

                for (int i = AHEAD_STEP; i <= AHEAD; i += AHEAD_STEP)
                {
                    int t = Math.min(Math.max(base + i, 0), size - 1);
                    Frame frame = record.frames.get(t);

                    area(wanted, MathHelper.floor(frame.x) >> 4, MathHelper.floor(frame.z) >> 4, 1);

                    if (t == size - 1)
                    {
                        break;
                    }
                }
            }

            for (Long key : wanted)
            {
                window.lastWanted.put(key, now);

                if (window.forced.containsKey(key))
                {
                    continue;
                }

                Ticket ticket = window.forced.size() >= MAX_CHUNKS ? null : window.ticketWithRoom();

                if (ticket == null)
                {
                    if (!window.warned)
                    {
                        window.warned = true;
                        System.out.println("[Blockbuster] Szene " + id + ": keine Chunk-Tickets mehr frei, "
                            + window.forced.size() + " Chunks gehalten");
                    }

                    break;
                }

                ForgeChunkManager.forceChunk(ticket, new ChunkPos(chunkX(key), chunkZ(key)));
                window.forced.put(key, ticket);
                window.used.put(ticket, window.used.get(ticket) + 1);
            }

            if (now % 20 == 0)
            {
                trim(window, now);
            }
        }
        catch (Throwable t)
        {
            /* Einmal je Szene melden - das hier laeuft jeden Tick. */
            if (FAILED.add(id))
            {
                System.out.println("[Blockbuster] Szene " + id + ": Chunks konnten nicht festgehalten werden: " + t);
            }
        }
    }

    private static final Set<String> FAILED = new HashSet<String>();

    /**
     * Chunks freigeben, die laenger nicht mehr gebraucht wurden.
     */
    private static void trim(Window window, long now)
    {
        Iterator<Map.Entry<Long, Ticket>> it = window.forced.entrySet().iterator();

        while (it.hasNext())
        {
            Map.Entry<Long, Ticket> entry = it.next();
            Long last = window.lastWanted.get(entry.getKey());

            if (last != null && now - last.longValue() < KEEP)
            {
                continue;
            }

            Long key = entry.getKey();
            Ticket ticket = entry.getValue();

            ForgeChunkManager.unforceChunk(ticket, new ChunkPos(chunkX(key), chunkZ(key)));
            it.remove();
            window.lastWanted.remove(key);
            window.used.put(ticket, Math.max(0, window.used.get(ticket) - 1));
        }

        Iterator<Ticket> tickets = window.tickets.iterator();

        while (tickets.hasNext())
        {
            Ticket ticket = tickets.next();

            if (window.used.get(ticket) == 0)
            {
                ForgeChunkManager.releaseTicket(ticket);
                window.used.remove(ticket);
                tickets.remove();
                window.warned = false;
            }
        }
    }

    /**
     * Der Chunk unter dem Actor muss JETZT geladen sein, nicht erst im naechsten
     * Tick - sonst scheitert das Spawnen oder der Actor faellt aus der Welt.
     */
    private static void loadNow(World world, int cx, int cz)
    {
        if (world instanceof WorldServer && ((WorldServer) world).getChunkProvider().getLoadedChunk(cx, cz) == null)
        {
            ((WorldServer) world).getChunkProvider().provideChunk(cx, cz);
        }
    }

    private static void area(Set<Long> set, int cx, int cz, int radius)
    {
        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dz = -radius; dz <= radius; dz++)
            {
                set.add(Long.valueOf(ChunkPos.asLong(cx + dx, cz + dz)));
            }
        }
    }

    private static int chunkX(Long key)
    {
        return (int) (key.longValue() & 0xFFFFFFFFL);
    }

    private static int chunkZ(Long key)
    {
        return (int) (key.longValue() >>> 32 & 0xFFFFFFFFL);
    }

    /**
     * Chunks wieder der normalen Verwaltung ueberlassen.
     */
    public static void release(String scene)
    {
        release(scene, true);
    }

    private static void release(String scene, boolean log)
    {
        Window window = HELD.remove(scene);

        if (window == null)
        {
            return;
        }

        for (Ticket ticket : window.tickets)
        {
            try
            {
                ForgeChunkManager.releaseTicket(ticket);
            }
            catch (Throwable ignored)
            {}
        }

        if (log && !window.forced.isEmpty())
        {
            System.out.println("[Blockbuster] Szene " + scene + ": Chunks freigegeben");
        }
    }

    /**
     * Forge vergibt Tickets nur, wenn der Mod vorher einen Rueckruf angemeldet hat.
     * Unsere Tickets sollen einen Neustart NICHT ueberleben - beim Laden der Welt
     * werden wiederhergestellte Tickets deshalb einfach nicht weiterverwendet.
     */
    private static void prepare()
    {
        if (prepared)
        {
            return;
        }

        prepared = true;

        ForgeChunkManager.setForcedChunkLoadingCallback(Blockbuster.instance, new LoadingCallback()
        {
            @Override
            public void ticketsLoaded(List<Ticket> tickets, World world)
            {}
        });

        MinecraftForge.EVENT_BUS.register(new ScenePlaybackChunks());
    }

    /**
     * Wird die Welt entladen, sind unsere Tickets hinfaellig.
     */
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event)
    {
        for (String scene : new ArrayList<String>(HELD.keySet()))
        {
            Window window = HELD.get(scene);

            if (window != null && window.world == event.getWorld())
            {
                release(scene, false);
            }
        }
    }
}
