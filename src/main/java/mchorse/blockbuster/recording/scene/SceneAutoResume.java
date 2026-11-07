package mchorse.blockbuster.recording.scene;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.recording.data.Record;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Loop-Szenen laufen nach einem Server-Neustart weiter. Beim Start einer Szene
 * mit Loops wird ihr Name gemerkt, bei /scene stop wieder entfernt. Das
 * Herunterfahren stoppt Szenen nicht (SceneManager.reset), der Eintrag bleibt.
 * Datei: blockbuster/autoplay.txt im Weltordner, ein Szenenname pro Zeile.
 */
public final class SceneAutoResume
{
    private static final Set<String> RUNNING = new LinkedHashSet<String>();
    private static boolean loaded;

    private SceneAutoResume()
    {}

    public static void started(Scene scene)
    {
        if (scene.loops && !scene.getWorld().isRemote)
        {
            load();

            if (RUNNING.add(scene.getId()))
            {
                save();
            }
        }
    }

    public static void stopped(String id)
    {
        load();

        if (RUNNING.remove(id))
        {
            save();
        }
    }

    /** Nach dem Serverstart: gemerkte Loop-Szenen wieder starten. */
    public static void resume(MinecraftServer server)
    {
        loaded = false;
        RUNNING.clear();
        load();

        World world = server.getWorld(0);

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

                if (scene.playing)
                {
                    continue;
                }

                loadStartChunks(scene, world);
                scene.startPlayback(0);
                System.out.println("[Blockbuster] Loop-Szene nach Neustart wieder gestartet: " + id);
            }
            catch (Throwable t)
            {
                System.out.println("[Blockbuster] Loop-Szene " + id + " konnte nicht wieder starten: " + t);
            }
        }
    }

    /** Actors entstehen nur in geladenen Chunks - die Startpunkte vorher laden. */
    private static void loadStartChunks(Scene scene, World world)
    {
        for (Replay replay : scene.replays)
        {
            try
            {
                Record record = CommonProxy.manager.get(replay.id);

                if (record != null && !record.frames.isEmpty())
                {
                    Frame frame = record.frames.get(0);

                    world.getChunkFromChunkCoords((int) Math.floor(frame.x) >> 4, (int) Math.floor(frame.z) >> 4);
                }
            }
            catch (Throwable t)
            {}
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

            List<String> lines = new ArrayList<String>(RUNNING);

            file.getParentFile().mkdirs();
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        }
        catch (Throwable t)
        {
            System.out.println("[Blockbuster] autoplay.txt nicht schreibbar: " + t);
        }
    }
}
