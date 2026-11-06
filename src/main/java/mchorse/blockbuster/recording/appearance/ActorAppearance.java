package mchorse.blockbuster.recording.appearance;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import java.security.MessageDigest;

/**
 * Aussehen eines aufnehmenden Spielers: MWRP-Skin (/skin) und CPM-Modell.
 *
 * <p>Wird beim Aufnahmestart in die Aufnahme geschrieben (Tag "Appearance") und
 * mit dem Actor an die Clients geschickt. Dort zeichnet ActorPuppetRenderer den
 * Actor damit als echtes Spielermodell - nur so greift CustomPlayerModels.</p>
 *
 * <p>Die CPM-Daten sind genau die Bytes, die CPM selbst an Clients schickt
 * (PlayerData.data). Bei den /skin-Augen ist das nur ein kurzer Verweis, den jeder
 * MWRP-Client aus den mitgelieferten Modellen aufloest.</p>
 */
public class ActorAppearance
{
    /** Obergrenze je Teil - ein /skin-PNG hat wenige KB, ein CPM-Verweis ~76 Bytes. */
    public static final int MAX_BYTES = 64 * 1024;

    public byte[] skin;
    public boolean slim;
    public byte[] cpm;
    public boolean cpmForced;

    /**
     * CPM-Ebenen des Aufnehmenden (PlayerData.gestureData, Pose und Geste genullt).
     * Ohne diese Daten legt CPM auf einem Server mit Gesten die Ebenen gar nicht an -
     * die /skin-Augen fehlten. Null = Standardwerte des Modells.
     */
    public byte[] gestures;

    public String hash = "";

    /* Nur zur Diagnose */
    public String owner = "";
    public String ownerName = "";
    public long captured;

    public boolean isValid()
    {
        return (this.skin != null && this.skin.length > 0) || (this.cpm != null && this.cpm.length > 0);
    }

    public void updateHash()
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");

            if (this.skin != null) digest.update(this.skin);
            digest.update((byte) (this.slim ? 1 : 0));
            if (this.cpm != null) digest.update(this.cpm);
            digest.update((byte) (this.cpmForced ? 1 : 0));

            StringBuilder builder = new StringBuilder();

            for (byte b : digest.digest())
            {
                builder.append(String.format("%02x", b));
            }

            this.hash = builder.toString();
        }
        catch (Exception e)
        {
            this.hash = Integer.toHexString(java.util.Arrays.hashCode(this.skin)) + Integer.toHexString(java.util.Arrays.hashCode(this.cpm));
        }
    }

    /** Kurzbeschreibung fuer Chat und Log. */
    public String describe()
    {
        String text = this.skin != null ? "Skin (" + (this.slim ? "slim" : "classic") + ")" : "kein /skin";

        return this.cpm != null ? text + ", CPM-Modell" : text;
    }

    public NBTTagCompound toNBT()
    {
        NBTTagCompound tag = new NBTTagCompound();

        if (this.skin != null) tag.setByteArray("Skin", this.skin);
        tag.setBoolean("Slim", this.slim);
        if (this.cpm != null) tag.setByteArray("Cpm", this.cpm);
        tag.setBoolean("CpmForced", this.cpmForced);
        if (this.gestures != null) tag.setByteArray("Gestures", this.gestures);
        tag.setString("Hash", this.hash);
        tag.setString("Owner", this.owner);
        tag.setString("OwnerName", this.ownerName);
        tag.setLong("Captured", this.captured);

        return tag;
    }

    /**
     * @return null, wenn nichts Brauchbares drinsteht
     */
    public static ActorAppearance fromNBT(NBTTagCompound tag)
    {
        ActorAppearance look = new ActorAppearance();

        look.skin = tag.hasKey("Skin", 7) ? tag.getByteArray("Skin") : null;
        look.slim = tag.getBoolean("Slim");
        look.cpm = tag.hasKey("Cpm", 7) ? tag.getByteArray("Cpm") : null;
        look.cpmForced = tag.getBoolean("CpmForced");
        look.gestures = tag.hasKey("Gestures", 7) ? tag.getByteArray("Gestures") : null;
        look.hash = tag.getString("Hash");
        look.owner = tag.getString("Owner");
        look.ownerName = tag.getString("OwnerName");
        look.captured = tag.getLong("Captured");

        if (!look.isValid()) return null;
        if (look.hash.isEmpty()) look.updateHash();

        return look;
    }

    public void toBuf(ByteBuf buf)
    {
        writeBytes(buf, this.skin);
        buf.writeBoolean(this.slim);
        writeBytes(buf, this.cpm);
        buf.writeBoolean(this.cpmForced);
        ByteBufUtils.writeUTF8String(buf, this.hash);
    }

    public static ActorAppearance fromBuf(ByteBuf buf)
    {
        ActorAppearance look = new ActorAppearance();

        look.skin = readBytes(buf);
        look.slim = buf.readBoolean();
        look.cpm = readBytes(buf);
        look.cpmForced = buf.readBoolean();
        look.hash = ByteBufUtils.readUTF8String(buf);

        if (look.hash.isEmpty()) look.updateHash();

        return look.isValid() ? look : null;
    }

    /**
     * Die Ebenen gehen getrennt hinter dem uebrigen Aussehen uebers Netz, damit ein
     * Client der vorigen Fassung die Spawn-Daten noch lesen kann.
     */
    public static void writeGestures(ByteBuf buf, byte[] gestures)
    {
        writeBytes(buf, gestures != null && gestures.length <= 256 ? gestures : null);
    }

    public static byte[] readGestures(ByteBuf buf)
    {
        return readBytes(buf);
    }

    private static void writeBytes(ByteBuf buf, byte[] bytes)
    {
        if (bytes == null)
        {
            buf.writeInt(-1);

            return;
        }

        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static byte[] readBytes(ByteBuf buf)
    {
        int length = buf.readInt();

        if (length < 0)
        {
            return null;
        }

        if (length > MAX_BYTES)
        {
            throw new IllegalStateException("Actor appearance part too large: " + length);
        }

        byte[] bytes = new byte[length];

        buf.readBytes(bytes);

        return bytes;
    }
}
