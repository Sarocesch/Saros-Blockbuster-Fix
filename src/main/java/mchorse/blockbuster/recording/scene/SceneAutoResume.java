package mchorse.blockbuster.recording.scene;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.recording.data.Record;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dauerhafte Loop-Szenen, die nur laufen, wenn ein Spieler in der Naehe ist.
 *
 * <p>Startet jemand eine Szene mit Loops, wird ihr Name gemerkt
 * (blockbuster/autoplay.txt im Weltordner); /scene stop nimmt sie wieder heraus.
 * Der Server haelt sie aber nur aktiv, solange ein Spieler naeher als
 * {@link #START} Bloecke an einem Startpunkt ist. Ist laenger als
 * {@link #IDLE_TICKS} keiner mehr im Umkreis von {@link #STOP} Bloecken um
 * Startpunkte oder Actors, verschwinden die Actors und die Chunks werden frei.
 * START liegt ueber der Server-Sichtweite (9 Chunks), keiner sieht das Auftauchen.
 * Das gilt auch nach einem Neustart.</p>
 */
public final class SceneAutoResume
{
    private static final double START = 160;
    private static final double STOP = 200;
    private static final int IDLE_TICKS = 200;
    private static final int CHECK_EVERY = 20;

    private static final Set<String> RUNNING = new LinkedHashSet<String>();
    private static final Map<String, Long> LAST_NEAR = new HashMap<String, Long>();
    private static final Map<String, List<double[]>> ANCHORS = new HashMap<String, List<double[]>>();
    private static final Set<String> BROKEN = new LinkedHashSet<String>();
    private static boolean loaded;
    private static boolean suspending;

    private SceneAutoResume()
    {}

    public static void started(Scene scene)
    {
        if (scene.loops && !scene.getWorld().isRemote)
        {
            load();
            ANCHORS.remove(scene.getId());
            BROKEN.remove(scene.getId());
            LAST_NEAR.put(scene.getId(), scene.getWorld().getTotalWorldTime());

            if (RUNNING.add(scene.getId()))
            {
                save();
            }
        }
    }

    public static void stopped(String id)
    {
        /* Pause mangels Spieler: bleibt dauerhaft an. */
        if (suspending)
        {
            return;
        }

        load();
        LAST_NEAR.remove(id);

        if (RUNNING.remove(id))
        {
            save();
        }
    }

    public static boolean isActive(String id)
    {
        load();

        return RUNNING.contains(id);
    }

    /** Uebersicht "An": dauerhaft an, der Tick startet sie bei Spielern in der Naehe. */
    public static void activate(Scene scene)
    {
        load();
        ANCHORS.remove(scene.getId());

        if (RUNNING.add(scene.getId()))
        {
            save();
        }
    }

    /**
     * Eine Aufnahme startet: jede Loop-Szene mit dieser Aufnahme beenden, sonst
     * spielt die alte Fassung mit (oder startet, sobald man in der Naehe ist).
     * Danach in der Loop-Uebersicht wieder anschalten.
     */
    public static void endForRecording(String recordId, EntityPlayer player)
    {
        if (player.world.isRemote || player.getServer() == null)
        {
            return;
        }

        load();

        World world = player.getServer().getWorld(0);
        Set<String> ids = new LinkedHashSet<String>(RUNNING);

        for (Map.Entry<String, Scene> entry : CommonProxy.scenes.getScenes().entrySet())
        {
            if (entry.getValue().playing && entry.getValue().loops)
            {
                ids.add(entry.getKey());
            }
        }

        for (String id : ids)
        {
            Scene scene = CommonProxy.scenes.get(id, world);

            if (scene == null || scene.getByFile(recordId) == null)
            {
                continue;
            }

            if (scene.playing)
            {
                scene.stopPlayback(true, true);
            }

            stopped(id);
            player.sendMessage(new net.minecraft.util.text.TextComponentString(net.minecraft.util.text.TextFormatting.YELLOW
                + "Loop-Szene " + id + " fuer die Aufnahme beendet. Danach in der Loop-Uebersicht wieder anschalten."));
        }
    }

    /** Uebersicht "Neu": Actors weg, bleibt an - der Tick startet sie gleich wieder frisch. */
    public static void restart(Scene scene)
    {
        if (scene.playing)
        {
            suspending = true;

            try
            {
                scene.stopPlayback(true, true);
            }
            finally
            {
                suspending = false;
            }
        }

        activate(scene);
    }

    /** Server neu gestartet oder Welt neu geladen: Liste frisch von der Platte. */
    public static void reset()
    {
        loaded = false;
        RUNNING.clear();
        LAST_NEAR.clear();
        ANCHORS.clear();
        BROKEN.clear();
    }

    /** Aus dem Oberwelt-Tick: jede Sekunde pruefen, wo Spieler sind. */
    public static void tick(World world)
    {
        if (world.getTotalWorldTime() % CHECK_EVERY != 0)
        {
            return;
        }

        load();

        long now = world.getTotalWorldTime();

        for (String id : new ArrayList<String>(RUNNING))
        {
            try
            {
                Scene scene = CommonProxy.scenes.get(id, world);

                if (scene == null || !scene.loops)
                {
                    stopped(id);

                    continue;
                }

                List<double[]> anchors = anchors(scene);

                if (scene.playing)
                {
                    if (playerNear(scene.getWorld(), anchors, scene, STOP))
                    {
                        LAST_NEAR.put(id, now);
                    }
                    else
                    {
                        Long last = LAST_NEAR.get(id);

                        if (last == null)
                        {
                            LAST_NEAR.put(id, now);
                        }
                        else if (now - last >= IDLE_TICKS)
                        {
                            suspending = true;

                            try
                            {
                                scene.stopPlayback(true, true);
                            }
                            finally
                            {
                                suspending = false;
                            }

                            System.out.println("[Blockbuster] Loop-Szene " + id + " pausiert, kein Spieler in der Naehe.");
                        }
                    }
                }
                else if (playerNear(scene.getWorld(), anchors, null, START))
                {
                    if (anchors.size() < enabledReplays(scene))
                    {
                        /* Eine Aufnahme fehlt (geloescht?): nicht jede Sekunde neu scheitern. */
                        ANCHORS.remove(id);

                        if (BROKEN.add(id))
                        {
                            System.out.println("[Blockbuster] Loop-Szene " + id + " startet nicht, eine Aufnahme fehlt.");
                        }

                        continue;
                    }

                    BROKEN.remove(id);
                    loadStartChunks(scene.getWorld(), anchors);
                    scene.startPlayback(0);
                    LAST_NEAR.put(id, now);
                    System.out.println("[Blockbuster] Loop-Szene " + id + " gestartet, Spieler in der Naehe.");
                }
            }
            catch (Throwable t)
            {
                System.out.println("[Blockbuster] Loop-Szene " + id + ": " + t);
            }
        }
    }

    private static boolean playerNear(World world, List<double[]> anchors, Scene scene, double radius)
    {
        double r2 = radius * radius;

        for (EntityPlayer player : world.playerEntities)
        {
            for (double[] anchor : anchors)
            {
                double dx = player.posX - anchor[0];
                double dz = player.posZ - anchor[1];

                if (dx * dx + dz * dz <= r2)
                {
                    return true;
                }
            }

            if (scene != null)
            {
                for (RecordPlayer actor : scene.actors.values())
                {
                    Entity entity = actor.actor;

                    if (entity != null && !entity.isDead && entity.getDistanceSq(player.posX, entity.posY, player.posZ) <= r2)
                    {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static int enabledReplays(Scene scene)
    {
        int count = 0;

        for (Replay replay : scene.replays)
        {
            if (replay.enabled)
            {
                count++;
            }
        }

        return count;
    }

    /** Erstes Bild jeder Aufnahme der Szene: x, z, y. */
    public static List<double[]> anchors(Scene scene)
    {
        List<double[]> anchors = ANCHORS.get(scene.getId());

        if (anchors != null)
        {
            return anchors;
        }

        anchors = new ArrayList<double[]>();

        for (Replay replay : scene.replays)
        {
            if (!replay.enabled)
            {
                continue;
            }

            try
            {
                Record record = CommonProxy.manager.get(replay.id);

                if (record != null && !record.frames.isEmpty())
                {
                    Frame frame = record.frames.get(0);

                    anchors.add(new double[] {frame.x, frame.z, frame.y});
                }
            }
            catch (Throwable t)
            {}
        }

        ANCHORS.put(scene.getId(), anchors);

        return anchors;
    }

    /** Actors entstehen nur in geladenen Chunks - die Startpunkte vorher laden. */
    private static void loadStartChunks(World world, List<double[]> anchors)
    {
        for (double[] anchor : anchors)
        {
            world.getChunkFromChunkCoords((int) Math.floor(anchor[0]) >> 4, (int) Math.floor(anchor[1]) >> 4);
        }
    }

    private static File file()
    {
        File scenes = SceneManager.sceneFile("x");

        return scenes == null ? null : new File(scenes.getParentFile().getParentFile(), "autoplay.txt");
    }

    private static void load()
    {
        if (loaded)
        {
            return;
        }

        loaded = true;

        try
        {
            File file = file();

            if (file != null && file.isFile())
            {
                for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8))
                {
                    if (!line.trim().isEmpty())
                    {
                        RUNNING.add(line.trim());
                    }
                }
            }
        }
        catch (Throwable t)
        {
            System.out.println("[Blockbuster] autoplay.txt nicht lesbar: " + t);
        }
    }

    private static void save()
    {
        try
        {
            File file = file();

            if (file == null)
            {
                return;
            }

            file.getParentFile().mkdirs();
            Files.write(file.toPath(), new ArrayList<String>(RUNNING), StandardCharsets.UTF_8);
        }
        catch (Throwable t)
        {
            System.out.println("[Blockbuster] autoplay.txt nicht schreibbar: " + t);
        }
    }
}
