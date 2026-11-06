package mchorse.blockbuster.recording.scene;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;

/**
 * Was ein Spieler im Szenen-Menue tatsaechlich geaendert hat.
 *
 * <p>Das Menue schickte beim Schliessen immer die ganze Szene, so wie dieser Client
 * sie kannte. Hatte inzwischen ein anderer Spieler etwas geaendert (Actor
 * hinzugefuegt, Auto-Einstellungen), war das damit wieder weg - wer zuletzt
 * schloss, gewann. Jetzt geht zusaetzlich mit, welche Teile sich gegenueber dem
 * Stand beim Oeffnen geaendert haben, und der Server uebernimmt nur diese auf
 * seine aktuelle Fassung.</p>
 */
public class SceneMerge
{
    public static final byte SAME = 0;
    public static final byte CHANGED = 1;
    public static final byte NEW = 2;

    public static final int TITLE = 1;
    public static final int START = 2;
    public static final int STOP = 4;
    public static final int LOOPS = 8;
    public static final int AUDIO = 16;
    public static final int REORDER = 32;

    public int fields;

    /** Je Replay der gesendeten Szene, in derselben Reihenfolge. */
    public List<Byte> states = new ArrayList<Byte>();

    /** Id, unter der dieses Replay beim Oeffnen bekannt war ("" bei neuen). */
    public List<String> origins = new ArrayList<String>();

    /** Ids der Replays, die der Spieler entfernt hat. */
    public List<String> removed = new ArrayList<String>();

    /**
     * Je Replay die NBT-Schluessel, die der Spieler daran geaendert hat (nur bei
     * CHANGED). Der Server uebernimmt nur diese - aendern zwei Leute denselben
     * Actor an verschiedenen Stellen (einer den Morph, einer den Namen), bleibt
     * beides erhalten.
     */
    public List<List<String>> keys = new ArrayList<List<String>>();

    /**
     * Stand beim Oeffnen des Menues festhalten (Client).
     */
    public static class Baseline
    {
        public final Scene scene;
        public final NBTTagCompound fields;
        public final Map<Replay, NBTTagCompound> replays = new IdentityHashMap<Replay, NBTTagCompound>();
        public final List<Replay> order = new ArrayList<Replay>();

        public Baseline(Scene scene)
        {
            this.scene = scene;
            this.fields = fieldsOf(scene);

            for (Replay replay : scene.replays)
            {
                this.replays.put(replay, replayTag(replay));
                this.order.add(replay);
            }
        }

        /**
         * Aenderungen gegenueber diesem Stand, oder null, wenn sich nichts geaendert hat.
         */
        public SceneMerge diff()
        {
            return this.diff(false);
        }

        /**
         * @param always auch ohne Aenderung einen (leeren) Abgleich liefern
         */
        public SceneMerge diff(boolean always)
        {
            SceneMerge merge = new SceneMerge();
            NBTTagCompound now = fieldsOf(this.scene);
            boolean changed = false;

            merge.fields |= differs(now, this.fields, "Title") ? TITLE : 0;
            merge.fields |= differs(now, this.fields, "StartCommand") ? START : 0;
            merge.fields |= differs(now, this.fields, "StopCommand") ? STOP : 0;
            merge.fields |= differs(now, this.fields, "Loops") ? LOOPS : 0;

            NBTTagCompound audioNow = now.copy();
            NBTTagCompound audioBefore = this.fields.copy();

            for (String key : new String[] {"Title", "StartCommand", "StopCommand", "Loops"})
            {
                audioNow.removeTag(key);
                audioBefore.removeTag(key);
            }

            merge.fields |= audioNow.equals(audioBefore) ? 0 : AUDIO;
            changed = merge.fields != 0;

            List<Replay> kept = new ArrayList<Replay>();

            for (Replay replay : this.scene.replays)
            {
                NBTTagCompound before = this.replays.get(replay);

                if (before == null)
                {
                    merge.states.add(NEW);
                    merge.origins.add("");
                    merge.keys.add(new ArrayList<String>());
                    changed = true;

                    continue;
                }

                NBTTagCompound tagNow = replayTag(replay);
                boolean edited = !tagNow.equals(before);
                List<String> keys = new ArrayList<String>();

                if (edited)
                {
                    Set<String> all = new HashSet<String>(tagNow.getKeySet());

                    all.addAll(before.getKeySet());

                    for (String key : all)
                    {
                        if (differs(tagNow, before, key))
                        {
                            keys.add(key);
                        }
                    }
                }

                merge.states.add(edited ? CHANGED : SAME);
                merge.origins.add(before.getString("Id"));
                merge.keys.add(keys);
                changed |= edited;
                kept.add(replay);
            }

            for (Replay replay : this.order)
            {
                if (!containsIdentity(this.scene.replays, replay))
                {
                    merge.removed.add(this.replays.get(replay).getString("Id"));
                    changed = true;
                }
            }

            /* Nur die Reihenfolge der schon bekannten Replays zaehlt. */
            List<Replay> before = new ArrayList<Replay>();

            for (Replay replay : this.order)
            {
                if (containsIdentity(kept, replay))
                {
                    before.add(replay);
                }
            }

            for (int i = 0; i < kept.size(); i++)
            {
                if (kept.get(i) != before.get(i))
                {
                    merge.fields |= REORDER;
                    changed = true;

                    break;
                }
            }

            return changed || always ? merge : null;
        }
    }

    private static NBTTagCompound fieldsOf(Scene scene)
    {
        NBTTagCompound tag = new NBTTagCompound();

        scene.toNBT(tag);
        tag.removeTag("Actors");

        return tag;
    }

    private static NBTTagCompound replayTag(Replay replay)
    {
        NBTTagCompound tag = new NBTTagCompound();

        replay.toNBT(tag);

        return tag;
    }

    private static boolean differs(NBTTagCompound a, NBTTagCompound b, String key)
    {
        Object x = a.getTag(key);
        Object y = b.getTag(key);

        return x == null ? y != null : !x.equals(y);
    }

    private static boolean containsIdentity(List<Replay> list, Replay replay)
    {
        for (Replay other : list)
        {
            if (other == replay)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Server: die Aenderungen aus {@code incoming} auf {@code current} (die aktuelle
     * Fassung auf dem Server) anwenden. Das Ergebnis steht danach in {@code incoming}.
     */
    public void apply(Scene current, Scene incoming)
    {
        if (this.states.size() != incoming.replays.size() || this.origins.size() != incoming.replays.size())
        {
            /* Passt nicht zusammen - wie frueher ganz uebernehmen. */
            return;
        }

        if ((this.fields & TITLE) == 0) incoming.title = current.title;
        if ((this.fields & START) == 0) incoming.startCommand = current.startCommand;
        if ((this.fields & STOP) == 0) incoming.stopCommand = current.stopCommand;
        if ((this.fields & LOOPS) == 0) incoming.loops = current.loops;
        if ((this.fields & AUDIO) == 0) incoming.copyAudio(current);

        Set<String> removed = new HashSet<String>(this.removed);
        boolean[] usedServer = new boolean[current.replays.size()];
        List<Replay> result = new ArrayList<Replay>();

        if ((this.fields & REORDER) != 0)
        {
            /* Reihenfolge des Spielers, Fremdes hinten an. */
            for (int i = 0; i < incoming.replays.size(); i++)
            {
                byte state = this.states.get(i);

                if (state == NEW)
                {
                    result.add(incoming.replays.get(i));

                    continue;
                }

                int match = find(current, usedServer, this.origins.get(i), removed);

                if (match < 0)
                {
                    if (state == CHANGED)
                    {
                        result.add(incoming.replays.get(i));
                    }

                    continue;
                }

                usedServer[match] = true;
                result.add(state == CHANGED ? this.merged(current.replays.get(match), incoming.replays.get(i), i) : current.replays.get(match));
            }

            for (int j = 0; j < current.replays.size(); j++)
            {
                if (!usedServer[j] && !removed.contains(current.replays.get(j).id))
                {
                    result.add(current.replays.get(j));
                }
            }
        }
        else
        {
            /* Reihenfolge des Servers, Neues hinten an. */
            boolean[] usedIncoming = new boolean[incoming.replays.size()];

            for (int j = 0; j < current.replays.size(); j++)
            {
                Replay server = current.replays.get(j);

                if (removed.contains(server.id))
                {
                    continue;
                }

                int match = -1;

                for (int i = 0; i < incoming.replays.size(); i++)
                {
                    if (!usedIncoming[i] && this.states.get(i) != NEW && this.origins.get(i).equals(server.id))
                    {
                        match = i;

                        break;
                    }
                }

                if (match < 0)
                {
                    result.add(server);

                    continue;
                }

                usedIncoming[match] = true;
                result.add(this.states.get(match) == CHANGED ? this.merged(server, incoming.replays.get(match), match) : server);
            }

            for (int i = 0; i < incoming.replays.size(); i++)
            {
                byte state = this.states.get(i);

                if (!usedIncoming[i] && (state == NEW || state == CHANGED))
                {
                    result.add(incoming.replays.get(i));
                }
            }
        }

        incoming.replays.clear();
        incoming.replays.addAll(result);
    }

    /**
     * Server-Fassung eines Actors mit nur den Feldern, die der Spieler geaendert hat.
     */
    private Replay merged(Replay server, Replay mine, int index)
    {
        List<String> keys = index < this.keys.size() ? this.keys.get(index) : null;

        if (keys == null || keys.isEmpty())
        {
            return mine;
        }

        NBTTagCompound tag = replayTag(server);
        NBTTagCompound changed = replayTag(mine);

        for (String key : keys)
        {
            if (changed.hasKey(key))
            {
                tag.setTag(key, changed.getTag(key).copy());
            }
            else
            {
                tag.removeTag(key);
            }
        }

        Replay replay = new Replay();

        replay.fromNBT(tag);

        return replay;
    }

    private static int find(Scene current, boolean[] used, String id, Set<String> removed)
    {
        if (removed.contains(id))
        {
            return -1;
        }

        for (int j = 0; j < current.replays.size(); j++)
        {
            if (!used[j] && current.replays.get(j).id.equals(id))
            {
                return j;
            }
        }

        return -1;
    }

    /* Netz */

    public void toBuf(ByteBuf buf)
    {
        buf.writeInt(this.fields);
        buf.writeInt(this.states.size());

        for (int i = 0; i < this.states.size(); i++)
        {
            buf.writeByte(this.states.get(i));
            ByteBufUtils.writeUTF8String(buf, this.origins.get(i));
        }

        buf.writeInt(this.removed.size());

        for (String id : this.removed)
        {
            ByteBufUtils.writeUTF8String(buf, id);
        }

        buf.writeInt(this.keys.size());

        for (List<String> list : this.keys)
        {
            buf.writeInt(list.size());

            for (String key : list)
            {
                ByteBufUtils.writeUTF8String(buf, key);
            }
        }
    }

    public static SceneMerge fromBuf(ByteBuf buf)
    {
        SceneMerge merge = new SceneMerge();

        merge.fields = buf.readInt();

        for (int i = 0, c = buf.readInt(); i < c; i++)
        {
            merge.states.add(buf.readByte());
            merge.origins.add(ByteBufUtils.readUTF8String(buf));
        }

        for (int i = 0, c = buf.readInt(); i < c; i++)
        {
            merge.removed.add(ByteBufUtils.readUTF8String(buf));
        }

        for (int i = 0, c = buf.readInt(); i < c; i++)
        {
            List<String> list = new ArrayList<String>();

            for (int j = 0, d = buf.readInt(); j < d; j++)
            {
                list.add(ByteBufUtils.readUTF8String(buf));
            }

            merge.keys.add(list);
        }

        return merge;
    }
}
