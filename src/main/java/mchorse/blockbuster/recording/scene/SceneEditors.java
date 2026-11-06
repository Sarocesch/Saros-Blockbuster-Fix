package mchorse.blockbuster.recording.scene;

import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.scene.PacketSceneEditors;
import mchorse.blockbuster.network.common.scene.PacketSceneLive;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Wer gerade welche Szene im Szenen-Menue offen hat (Server).
 *
 * <p>Vorher wusste niemand vom anderen: zwei Leute bearbeiteten dieselbe Szene,
 * sahen die Aenderungen des anderen erst nach erneutem Oeffnen und ueberschrieben
 * sich gegenseitig. Das Menue meldet sich jetzt jede Sekunde, und jede gespeicherte
 * Fassung geht sofort an alle, die dieselbe Szene offen haben.</p>
 */
public class SceneEditors
{
    /** Ohne Meldung so lange (ms) gilt ein Spieler als weg - Absturz, Logout, Menue zu. */
    private static final long TIMEOUT = 4000;

    private static final Map<UUID, Editor> editors = new HashMap<UUID, Editor>();
    private static int ticks;

    private static class Editor
    {
        public String scene;
        public String replay;
        public String name;
        public long seen;
    }

    public static void update(EntityPlayerMP player, String scene, String replay)
    {
        UUID uuid = player.getUniqueID();
        Editor editor = editors.get(uuid);
        Set<String> changed = new HashSet<String>();

        if (scene.isEmpty())
        {
            if (editor != null)
            {
                editors.remove(uuid);
                changed.add(editor.scene);
            }
        }
        else
        {
            if (editor == null)
            {
                editor = new Editor();
                editor.scene = "";
                editor.replay = "";
                editors.put(uuid, editor);
            }

            if (!editor.scene.equals(scene) || !editor.replay.equals(replay))
            {
                changed.add(editor.scene);
                changed.add(scene);
            }

            editor.scene = scene;
            editor.replay = replay;
            editor.name = player.getName();
            editor.seen = System.currentTimeMillis();
        }

        changed.remove("");

        for (String name : changed)
        {
            broadcast(name);
        }
    }

    /**
     * Haengengebliebene Eintraege entfernen (aus SceneManager.tick).
     */
    public static void tick()
    {
        if (++ticks % 20 != 0 || editors.isEmpty())
        {
            return;
        }

        long now = System.currentTimeMillis();
        Set<String> changed = new HashSet<String>();
        Iterator<Map.Entry<UUID, Editor>> it = editors.entrySet().iterator();

        while (it.hasNext())
        {
            Map.Entry<UUID, Editor> entry = it.next();

            if (now - entry.getValue().seen > TIMEOUT || player(entry.getKey()) == null)
            {
                changed.add(entry.getValue().scene);
                it.remove();
            }
        }

        for (String name : changed)
        {
            broadcast(name);
        }
    }

    public static void reset()
    {
        editors.clear();
    }

    /**
     * Jedem, der diese Szene offen hat, die anderen Bearbeiter schicken.
     */
    private static void broadcast(String scene)
    {
        for (Map.Entry<UUID, Editor> entry : editors.entrySet())
        {
            if (!entry.getValue().scene.equals(scene))
            {
                continue;
            }

            EntityPlayerMP player = player(entry.getKey());

            if (player == null)
            {
                continue;
            }

            List<String> names = new ArrayList<String>();
            List<String> replays = new ArrayList<String>();

            for (Map.Entry<UUID, Editor> other : editors.entrySet())
            {
                if (other.getKey().equals(entry.getKey()) || !other.getValue().scene.equals(scene))
                {
                    continue;
                }

                names.add(other.getValue().name);
                replays.add(other.getValue().replay);
            }

            Dispatcher.sendTo(new PacketSceneEditors(scene, names, replays), player);
        }
    }

    /**
     * Die gerade gespeicherte Fassung an alle, die diese Szene offen haben -
     * auch an den, der gespeichert hat: bei ihm fehlen sonst die Aenderungen
     * der anderen, die der Server eben eingemischt hat.
     */
    public static void sendScene(String filename, Scene scene)
    {
        if (editors.isEmpty())
        {
            return;
        }

        PacketSceneLive packet = null;

        for (Map.Entry<UUID, Editor> entry : editors.entrySet())
        {
            if (!entry.getValue().scene.equals(filename))
            {
                continue;
            }

            EntityPlayerMP player = player(entry.getKey());

            if (player != null)
            {
                if (packet == null)
                {
                    packet = new PacketSceneLive(new SceneLocation(scene));
                }

                Dispatcher.sendTo(packet, player);
            }
        }
    }

    private static EntityPlayerMP player(UUID uuid)
    {
        if (FMLCommonHandler.instance().getMinecraftServerInstance() == null)
        {
            return null;
        }

        return FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUUID(uuid);
    }
}
