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
        SCHEDULE.clear();
        scheduleLoaded = false;
        PAUSE.clear();
        pauseLoaded = false;
        TOGETHER.clear();
        togetherLoaded = false;
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

                boolean timeOk = scheduleAllows(id, scene.getWorld());

                if (scene.playing)
                {
                    if (timeOk && playerNear(scene.getWorld(), anchors, scene, STOP))
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
                        else if (now - last >= IDLE_TICKS && (doorMovingNear(scene) || clickNear(scene)))
                        {
                            /* Erst pausieren, wenn keine LittleTiles-Tuer mehr in Bewegung ist. */
                            LAST_NEAR.put(id, now - IDLE_TICKS + CHECK_EVERY);
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

                            System.out.println("[Blockbuster] Loop-Szene " + id + " pausiert, " + (timeOk ? "kein Spieler in der Naehe." : "ausserhalb ihrer Tageszeit."));
                        }
                    }
                }
                else if (timeOk && playerNear(scene.getWorld(), anchors, null, START))
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

    /* Tageszeit pro Loop-Szene: 0 immer, 1 nur tags, 2 nur nachts.
     * Eigene Datei, nicht in der Szene - so kann der Szenen-Abgleich sie nicht verlieren. */
    public static final int ALWAYS = 0;
    public static final int DAY = 1;
    public static final int NIGHT = 2;

    private static final Map<String, Integer> SCHEDULE = new HashMap<String, Integer>();
    private static boolean scheduleLoaded;

    public static int getSchedule(String id)
    {
        loadSchedule();

        Integer value = SCHEDULE.get(id);

        return value == null ? ALWAYS : value;
    }

    /** Menue: Immer -> Tag -> Nacht -> Immer. */
    public static int cycleSchedule(String id)
    {
        int next = (getSchedule(id) + 1) % 3;

        if (next == ALWAYS)
        {
            SCHEDULE.remove(id);
        }
        else
        {
            SCHEDULE.put(id, next);
        }

        saveSchedule();

        return next;
    }

    /* Pause zwischen zwei Loop-Runden in Sekunden, pro Szene. Eigene Datei wie die Tageszeit. */
    private static final int[] PAUSES = {0, 10, 30, 60, 120};
    private static final Map<String, Integer> PAUSE = new HashMap<String, Integer>();
    private static boolean pauseLoaded;

    public static int getLoopPause(String id)
    {
        if (!pauseLoaded)
        {
            pauseLoaded = true;
            readMap(pauseFile(), PAUSE);
        }

        Integer value = PAUSE.get(id);

        return value == null ? 0 : value;
    }

    /** Menue: 0 -> 10 -> 30 -> 60 -> 120 -> 0 Sekunden. */
    public static int cycleLoopPause(String id)
    {
        int current = getLoopPause(id);
        int next = 0;

        for (int i = 0; i < PAUSES.length; i++)
        {
            if (PAUSES[i] == current)
            {
                next = PAUSES[(i + 1) % PAUSES.length];
            }
        }

        if (next == 0)
        {
            PAUSE.remove(id);
        }
        else
        {
            PAUSE.put(id, next);
        }

        writeMap(pauseFile(), PAUSE);

        return next;
    }

    /* Gemeinsam: alle Actors einer Loop-Szene starten zugleich neu. Standard: einzeln. */
    private static final Map<String, Integer> TOGETHER = new HashMap<String, Integer>();
    private static boolean togetherLoaded;

    public static boolean isLoopTogether(String id)
    {
        if (!togetherLoaded)
        {
            togetherLoaded = true;
            readMap(togetherFile(), TOGETHER);
        }

        return TOGETHER.containsKey(id);
    }

    public static boolean toggleLoopTogether(String id)
    {
        boolean next = !isLoopTogether(id);

        if (next)
        {
            TOGETHER.put(id, 1);
        }
        else
        {
            TOGETHER.remove(id);
        }

        writeMap(togetherFile(), TOGETHER);

        return next;
    }

    private static File togetherFile()
    {
        File scenes = SceneManager.sceneFile("x");

        return scenes == null ? null : new File(scenes.getParentFile().getParentFile(), "loop_gemeinsam.txt");
    }

    private static File pauseFile()
    {
        File scenes = SceneManager.sceneFile("x");

        return scenes == null ? null : new File(scenes.getParentFile().getParentFile(), "loop_pause.txt");
    }

    private static void readMap(File file, Map<String, Integer> map)
    {
        try
        {
            if (file != null && file.isFile())
            {
                for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8))
                {
                    int split = line.lastIndexOf('=');

                    if (split > 0)
                    {
                        map.put(line.substring(0, split).trim(), Integer.parseInt(line.substring(split + 1).trim()));
                    }
                }
            }
        }
        catch (Throwable t)
        {
            System.out.println("[Blockbuster] " + file + " nicht lesbar: " + t);
        }
    }

    private static void writeMap(File file, Map<String, Integer> map)
    {
        try
        {
            if (file == null)
            {
                return;
            }

            List<String> lines = new ArrayList<String>();

            for (Map.Entry<String, Integer> entry : map.entrySet())
            {
                lines.add(entry.getKey() + "=" + entry.getValue());
            }

            file.getParentFile().mkdirs();
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        }
        catch (Throwable t)
        {
            System.out.println("[Blockbuster] " + file + " nicht schreibbar: " + t);
        }
    }

    public static boolean isNight(World world)
    {
        long time = world.getWorldTime() % 24000L;

        return time >= 13000L && time < 23000L;
    }

    public static boolean scheduleAllows(String id, World world)
    {
        int schedule = getSchedule(id);

        return schedule == ALWAYS || (schedule == NIGHT) == isNight(world);
    }

    private static File scheduleFile()
    {
        File scenes = SceneManager.sceneFile("x");

        return scenes == null ? null : new File(scenes.getParentFile().getParentFile(), "loop_tageszeit.txt");
    }

    private static void loadSchedule()
    {
        if (scheduleLoaded)
        {
            return;
        }

        scheduleLoaded = true;

        try
        {
            File file = scheduleFile();

            if (file != null && file.isFile())
            {
                for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8))
                {
                    int split = line.lastIndexOf('=');

                    if (split > 0)
                    {
                        SCHEDULE.put(line.substring(0, split).trim(), Integer.parseInt(line.substring(split + 1).trim()));
                    }
                }
            }
        }
        catch (Throwable t)
        {
            System.out.println("[Blockbuster] loop_tageszeit.txt nicht lesbar: " + t);
        }
    }

    private static void saveSchedule()
    {
        try
        {
            File file = scheduleFile();

            if (file == null)
            {
                return;
            }

            List<String> lines = new ArrayList<String>();

            for (Map.Entry<String, Integer> entry : SCHEDULE.entrySet())
            {
                lines.add(entry.getKey() + "=" + entry.getValue());
            }

            file.getParentFile().mkdirs();
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        }
        catch (Throwable t)
        {
            System.out.println("[Blockbuster] loop_tageszeit.txt nicht schreibbar: " + t);
        }
    }

    /** Ein Klick in den letzten oder naechsten 10 s: Tuer koennte noch offen sein. */
    private static final int CLICK_WINDOW = 200;

    /**
     * Steckt ein Actor gerade in einer Tuer-Aktion (oeffnen, durchgehen,
     * schliessen)? Dann nicht pausieren - sonst bleibt die Tuer offen stehen und
     * der erste Klick der naechsten Runde schliesst sie, statt sie zu oeffnen.
     */
    private static boolean clickNear(Scene scene)
    {
        for (RecordPlayer actor : scene.actors.values())
        {
            if (actor.record == null || actor.record.actions == null)
            {
                continue;
            }

            List<List<mchorse.blockbuster.recording.actions.Action>> actions = actor.record.actions;
            int from = Math.max(0, actor.tick - CLICK_WINDOW);
            int to = Math.min(actions.size() - 1, actor.tick + CLICK_WINDOW);

            for (int i = from; i <= to; i++)
            {
                List<mchorse.blockbuster.recording.actions.Action> list = actions.get(i);

                if (list == null)
                {
                    continue;
                }

                for (mchorse.blockbuster.recording.actions.Action action : list)
                {
                    if (action.getClass() == mchorse.blockbuster.recording.actions.InteractBlockAction.class)
                    {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean doorMovingNear(Scene scene)
    {
        for (RecordPlayer actor : scene.actors.values())
        {
            Entity entity = actor.actor;

            if (entity != null && mchorse.blockbuster.recording.LTHelper.doorMoving(scene.getWorld(), entity.posX, entity.posZ, 32))
            {
                return true;
            }
        }

        return false;
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
