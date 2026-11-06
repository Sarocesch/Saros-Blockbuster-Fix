package mchorse.blockbuster.recording.scene;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.recording.data.Mode;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.FMLCommonHandler;

/**
 * Gruppenaufnahme: mehrere Spieler nehmen in derselben Szene gleichzeitig je
 * einen Actor auf.
 *
 * <p>Jedes Replay kann einen "Aufnahme-Spieler" haben ({@link Replay#recordPlayer}).
 * Der Start nimmt alle anwesenden zugewiesenen Spieler mit einem gemeinsamen
 * Countdown auf, die uebrigen Actors spielen ab. Ein Stopp vom Startenden beendet
 * die Aufnahme fuer alle; ein einzelner Mitspieler kann nur seine eigene beenden.</p>
 *
 * <p>Ausgeloest ueber den Knopf im Szenen-Menue (PacketSceneRecord mit "*") oder
 * /scene recordall &lt;szene&gt;. Bestehende Einzelaufnahmen laufen unveraendert.</p>
 */
public final class GroupRecording
{
    /** Platzhalter-Replay im PacketSceneRecord fuer "alle zugewiesenen". */
    public static final String ALL = "*";

    private static final Map<String, Session> SESSIONS = new HashMap<String, Session>();

    private GroupRecording()
    {}

    private static final class Session
    {
        final String scene;
        final UUID starter;
        final Map<UUID, String> members = new LinkedHashMap<UUID, String>();
        boolean started;

        Session(String scene, UUID starter)
        {
            this.scene = scene;
            this.starter = starter;
        }
    }

    public static boolean isActive(String scene)
    {
        return SESSIONS.containsKey(scene);
    }

    /**
     * Starten, oder - laeuft fuer diese Szene schon eine - fuer alle beenden.
     */
    public static void toggle(String scene, ICommandSender sender)
    {
        if (SESSIONS.containsKey(scene))
        {
            stop(scene, sender);
        }
        else
        {
            start(scene, sender);
        }
    }

    /**
     * Aus {@link SceneManager#record}: die Aufnahme-Taste oder /action record
     * waehrend einer Gruppenaufnahme.
     *
     * @return true, wenn die Anfrage hier erledigt wurde
     */
    public static boolean handleRecordToggle(String scene, String replay, EntityPlayerMP player)
    {
        if (ALL.equals(replay))
        {
            toggle(scene, player);

            return true;
        }

        UUID id = player.getUniqueID();

        for (Session session : new ArrayList<Session>(SESSIONS.values()))
        {
            if (id.equals(session.starter))
            {
                stop(session.scene, player);

                return true;
            }

            if (session.members.containsKey(id))
            {
                Scene loaded = CommonProxy.scenes.getScenes().get(session.scene);
                Replay own = loaded == null ? null : loaded.getByFile(session.members.get(id));

                if (own != null)
                {
                    own.apply(player);
                }

                if (isRecording(player))
                {
                    CommonProxy.manager.halt(player, false, true);
                }

                tell(player, TextFormatting.YELLOW, "Deine Aufnahme ist beendet, die anderen nehmen weiter auf.");

                return true;
            }
        }

        return false;
    }

    /**
     * Fuer /scene stop und /scene toggle: laeuft eine Gruppenaufnahme, alle Aufnahmen
     * mit beenden statt nur die Szene anzuhalten.
     */
    public static boolean stopIfActive(String scene, ICommandSender sender)
    {
        if (!SESSIONS.containsKey(scene))
        {
            return false;
        }

        stop(scene, sender);

        return true;
    }

    private static void start(String sceneId, ICommandSender sender)
    {
        MinecraftServer server = sender.getServer();

        if (server == null)
        {
            return;
        }

        Scene scene = CommonProxy.scenes.get(sceneId, sender.getEntityWorld());

        if (scene == null)
        {
            tell(sender, TextFormatting.RED, "Szene " + sceneId + " gibt es nicht.");

            return;
        }

        if (scene.playing)
        {
            tell(sender, TextFormatting.RED, "Szene " + sceneId + " laeuft gerade. Erst stoppen, dann die Gruppenaufnahme starten.");

            return;
        }

        Entity starter = sender.getCommandSenderEntity();
        Session session = new Session(sceneId, starter instanceof EntityPlayerMP ? starter.getUniqueID() : null);
        List<String> skipped = new ArrayList<String>();

        for (Replay replay : scene.replays)
        {
            String name = replay.recordPlayer == null ? "" : replay.recordPlayer.trim();

            if (name.isEmpty() || replay.id.isEmpty())
            {
                continue;
            }

            EntityPlayerMP player = server.getPlayerList().getPlayerByUsername(name);

            if (player == null)
            {
                skipped.add(name + " (offline, " + replay.id + ")");
            }
            else if (session.members.containsKey(player.getUniqueID()))
            {
                skipped.add(name + " (schon " + session.members.get(player.getUniqueID()) + ", nicht auch " + replay.id + ")");
            }
            else if (isRecording(player))
            {
                skipped.add(name + " (nimmt gerade schon auf)");
            }
            else if (player.world != scene.getWorld())
            {
                skipped.add(name + " (andere Dimension)");
            }
            else
            {
                session.members.put(player.getUniqueID(), replay.id);
            }
        }

        if (session.members.isEmpty())
        {
            tell(sender, TextFormatting.RED, "Gruppenaufnahme " + sceneId + ": keinem Actor ist ein anwesender Aufnahme-Spieler zugewiesen"
                + " (Feld 'Aufnahme-Spieler' im Szenen-Menue)." + (skipped.isEmpty() ? "" : " Uebersprungen: " + join(skipped)));

            return;
        }

        SESSIONS.put(sceneId, session);

        StringBuilder who = new StringBuilder();

        for (Map.Entry<UUID, String> entry : new ArrayList<Map.Entry<UUID, String>>(session.members.entrySet()))
        {
            final EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(entry.getKey());
            final Replay replay = scene.getByFile(entry.getValue());

            boolean scheduled = player != null && replay != null && CommonProxy.manager.record(replay.id, player, Mode.ACTIONS, replay.teleportBack, true, 0, new Runnable()
            {
                @Override
                public void run()
                {
                    countdownDone(sceneId, player);
                }
            });

            if (!scheduled)
            {
                session.members.remove(entry.getKey());
                skipped.add((player == null ? "?" : player.getName()) + " (Aufnahme liess sich nicht starten)");

                continue;
            }

            who.append(who.length() == 0 ? "" : ", ").append(player.getName()).append(" -> ").append(replay.id);
            tell(player, TextFormatting.GREEN, "Gruppenaufnahme " + sceneId + ": du nimmst " + replay.id + " auf. Countdown laeuft.");
        }

        if (session.members.isEmpty())
        {
            SESSIONS.remove(sceneId);
            tell(sender, TextFormatting.RED, "Gruppenaufnahme " + sceneId + " konnte nicht starten: " + join(skipped));

            return;
        }

        tell(sender, TextFormatting.GREEN, "Gruppenaufnahme " + sceneId + " startet: " + who
            + ". Nochmal ausloesen oder deine Aufnahme-Taste beendet sie fuer alle.");

        if (!skipped.isEmpty())
        {
            tell(sender, TextFormatting.YELLOW, "Nicht dabei: " + join(skipped));
        }
    }

    /**
     * Countdown eines Mitspielers ist um. Der erste startet die Szene - ohne alle
     * Actors, die gerade aufgenommen werden. Die Countdowns laufen gleich lang und
     * enden im selben Tick.
     */
    private static void countdownDone(String sceneId, EntityPlayerMP player)
    {
        Session session = SESSIONS.get(sceneId);

        if (session == null)
        {
            return;
        }

        Scene scene = CommonProxy.scenes.get(sceneId, player.world);

        if (scene == null)
        {
            return;
        }

        if (!session.started)
        {
            session.started = true;

            List<Replay> exceptions = new ArrayList<Replay>();

            for (Replay replay : scene.replays)
            {
                if (session.members.containsValue(replay.id))
                {
                    exceptions.add(replay);
                }
            }

            scene.startPlayback(exceptions, 0);
        }

        Replay own = scene.getByFile(session.members.get(player.getUniqueID()));

        if (own != null)
        {
            own.apply(player);
        }
    }

    private static void stop(String sceneId, ICommandSender sender)
    {
        Session session = SESSIONS.remove(sceneId);

        if (session == null)
        {
            return;
        }

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        Scene scene = CommonProxy.scenes.getScenes().get(sceneId);

        if (scene != null && scene.playing)
        {
            scene.stopPlayback(true);
        }

        for (Map.Entry<UUID, String> entry : session.members.entrySet())
        {
            EntityPlayerMP player = server == null ? null : server.getPlayerList().getPlayerByUUID(entry.getKey());

            if (player == null)
            {
                continue;
            }

            boolean recording = isRecording(player);
            Replay own = scene == null ? null : scene.getByFile(entry.getValue());

            if (own != null && recording)
            {
                own.apply(player);
            }

            if (recording)
            {
                CommonProxy.manager.halt(player, false, true);
                tell(player, TextFormatting.GREEN, "Gruppenaufnahme beendet - deine Aufnahme " + entry.getValue() + " wird gespeichert.");
            }
        }

        tell(sender, TextFormatting.GREEN, "Gruppenaufnahme " + sceneId + " fuer alle beendet.");
    }

    /**
     * Jeden Server-Tick: nimmt keiner der Mitspieler mehr auf (alle einzeln
     * gestoppt, ausgeloggt oder gestorben), die Gruppenaufnahme abschliessen.
     */
    public static void tick()
    {
        if (SESSIONS.isEmpty())
        {
            return;
        }

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();

        if (server == null)
        {
            SESSIONS.clear();

            return;
        }

        for (Session session : new ArrayList<Session>(SESSIONS.values()))
        {
            boolean active = false;

            for (UUID id : session.members.keySet())
            {
                EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(id);

                if (player != null && isRecording(player))
                {
                    active = true;

                    break;
                }
            }

            if (active)
            {
                continue;
            }

            SESSIONS.remove(session.scene);

            Scene scene = CommonProxy.scenes.getScenes().get(session.scene);

            if (session.started && scene != null && scene.playing)
            {
                scene.stopPlayback(true);
            }

            EntityPlayerMP starter = session.starter == null ? null : server.getPlayerList().getPlayerByUUID(session.starter);

            if (starter != null)
            {
                tell(starter, TextFormatting.YELLOW, "Gruppenaufnahme " + session.scene + " beendet - niemand nimmt mehr auf.");
            }
        }
    }

    private static boolean isRecording(EntityPlayerMP player)
    {
        return CommonProxy.manager.recorders.containsKey(player) || CommonProxy.manager.scheduled.containsKey(player);
    }

    private static String join(List<String> parts)
    {
        StringBuilder builder = new StringBuilder();

        for (String part : parts)
        {
            builder.append(builder.length() == 0 ? "" : ", ").append(part);
        }

        return builder.toString();
    }

    private static void tell(ICommandSender sender, TextFormatting color, String text)
    {
        if (sender == null)
        {
            return;
        }

        TextComponentString message = new TextComponentString("[Blockbuster] " + text);

        message.getStyle().setColor(color);
        sender.sendMessage(message);
    }
}
