package mchorse.blockbuster.network.server.scene;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.scene.PacketSceneBackup;
import mchorse.blockbuster.network.common.scene.PacketSceneManage;
import mchorse.blockbuster.network.common.scene.PacketScenes;
import mchorse.blockbuster.recording.RecordUtils;
import mchorse.blockbuster.recording.scene.Replay;
import mchorse.blockbuster.recording.scene.Scene;
import mchorse.blockbuster.recording.scene.SceneManager;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.io.File;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ServerHandlerSceneManage extends ServerMessageHandler<PacketSceneManage>
{
    /** Unter dem 1-MB-Limit eines Server-Pakets. */
    private static final int CHUNK = 256 * 1024;

    @Override
    public void run(EntityPlayerMP player, PacketSceneManage message)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        if (message.action == PacketSceneManage.RENAME && CommonProxy.scenes.rename(message.source, message.destination))
        {
            Dispatcher.sendTo(message, player);
        }
        else if (message.action == PacketSceneManage.REMOVE && CommonProxy.scenes.remove(message.source))
        {
            Dispatcher.sendTo(message, player);
        }
        else if (message.action == PacketSceneManage.DUPE || message.action == PacketSceneManage.DUPE_SCENE)
        {
            this.dupe(player, message.source, message.destination, message.action == PacketSceneManage.DUPE);
        }
        else if (message.action == PacketSceneManage.BACKUP)
        {
            this.backup(player, message.source);
        }
    }

    /**
     * Szene auf dem Server kopieren, auf Wunsch mit allen Aufnahmen.
     *
     * Frueher legte der Client die Kopie an und verliess sich darauf, dass sie
     * beim Schliessen des Menues mitgeschickt wird. Seit beim Schliessen nur noch
     * Aenderungen gehen, kam die Kopie nie an. Jetzt kopiert der Server aus seiner
     * gespeicherten Fassung, und nichts Bestehendes wird je ueberschrieben.
     */
    private void dupe(EntityPlayerMP player, String sourceName, String destination, boolean recordings)
    {
        try
        {
            if (!SceneManager.isValidFilename(destination) || SceneManager.sceneFile(destination).exists())
            {
                error(player, "Szene '" + destination + "' gibt es schon oder der Name ist ungueltig - nichts kopiert.");

                return;
            }

            Scene source = CommonProxy.scenes.load(sourceName);

            if (source == null)
            {
                error(player, "Szene '" + sourceName + "' ist auf dem Server nicht gespeichert - nichts kopiert.");

                return;
            }

            Scene copy = new Scene();

            copy.copy(source);
            copy.setId(destination);
            copy.setupIds();
            copy.renamePrefix(source.getId(), copy.getId(), (id) -> id + "_copy");

            Map<String, String> copied = new HashMap<String, String>();
            Set<String> taken = new HashSet<String>();
            int files = 0;

            for (int i = 0; i < copy.replays.size(); i++)
            {
                Replay from = source.replays.get(i);
                Replay to = copy.replays.get(i);

                /* Zwei Actors auf derselben Aufnahme teilen sich auch die Kopie. */
                if (!from.id.isEmpty() && copied.containsKey(from.id))
                {
                    to.id = copied.get(from.id);

                    continue;
                }

                if (to.id.isEmpty())
                {
                    continue;
                }

                /* Nie auf eine bestehende Aufnahme zeigen: sonst wuerde eine neue
                 * Aufnahme in der Kopie die eines anderen Actors ueberschreiben. */
                String target = to.id;

                for (int n = 1; RecordUtils.isReplayExists(target) || taken.contains(target); n++)
                {
                    target = to.id + "_" + n;
                }

                File file = from.id.isEmpty() ? null : RecordUtils.replayFile(from.id);

                if (recordings && file != null && file.isFile())
                {
                    Files.copy(file.toPath(), RecordUtils.replayFile(target).toPath());
                    files++;
                }

                to.id = target;
                taken.add(target);

                if (!from.id.isEmpty())
                {
                    copied.put(from.id, target);
                }
            }

            CommonProxy.scenes.save(destination, copy, false);
            Dispatcher.sendTo(new PacketScenes(CommonProxy.scenes.sceneFiles()), player);

            player.sendMessage(new TextComponentString(TextFormatting.GREEN + "[Blockbuster] Szene '" + sourceName + "' kopiert nach '" + destination + "'"
                + (recordings ? " mit " + files + " Aufnahmen." : " (ohne Aufnahmen).")));
        }
        catch (Exception e)
        {
            e.printStackTrace();
            error(player, "Kopieren fehlgeschlagen: " + e);
        }
    }

    /**
     * Szene und alle ihre Aufnahmen als Dateien an den Client schicken, der sie
     * bei sich speichert.
     */
    private void backup(EntityPlayerMP player, String name)
    {
        try
        {
            Scene scene = CommonProxy.scenes.load(name);

            if (scene == null)
            {
                error(player, "Szene '" + name + "' ist auf dem Server nicht gespeichert - keine Sicherung.");

                return;
            }

            List<File> files = new ArrayList<File>();
            List<String> paths = new ArrayList<String>();
            Set<String> records = new HashSet<String>();

            files.add(SceneManager.sceneFile(name));
            paths.add("scenes/" + name + ".dat");

            for (Replay replay : scene.replays)
            {
                for (String id : Arrays.asList(replay.id, replay.idleRecord))
                {
                    if (id == null || id.isEmpty() || !records.add(id))
                    {
                        continue;
                    }

                    File file = RecordUtils.replayFile(id);

                    if (file.isFile())
                    {
                        files.add(file);
                        paths.add("records/" + id + ".dat");
                    }
                }
            }

            String folder = name + "_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            long bytes = 0;

            for (File file : files)
            {
                bytes += file.length();
            }

            player.sendMessage(new TextComponentString(TextFormatting.GRAY + "[Blockbuster] Sicherung von '" + name + "' wird uebertragen: "
                + files.size() + " Dateien, " + String.format("%.1f", bytes / 1048576D) + " MB."));

            for (int i = 0; i < files.size(); i++)
            {
                byte[] data = Files.readAllBytes(files.get(i).toPath());
                int chunks = Math.max(1, (data.length + CHUNK - 1) / CHUNK);

                for (int c = 0; c < chunks; c++)
                {
                    byte[] part = Arrays.copyOfRange(data, c * CHUNK, Math.min(data.length, (c + 1) * CHUNK));

                    Dispatcher.sendTo(new PacketSceneBackup(folder, paths.get(i), i, files.size(), c, chunks, part), player);
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            error(player, "Sicherung fehlgeschlagen: " + e);
        }
    }

    private static void error(EntityPlayerMP player, String text)
    {
        player.sendMessage(new TextComponentString(TextFormatting.RED + "[Blockbuster] " + text));
    }
}
