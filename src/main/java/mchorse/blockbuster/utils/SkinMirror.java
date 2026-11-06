package mchorse.blockbuster.utils;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.Loader;

/**
 * Skin-Spiegel auf dem Server.
 *
 * <p>Die Szenen zeigen auf Skin-Links, und Discord-Links laufen ab: derselbe Link,
 * der bei einem Kameramann noch aus dem Zwischenspeicher kommt, liefert beim naechsten
 * Spieler HTTP 404, und der sieht einen schwarzen Actor. Wer das Bild noch hat, gibt es
 * dem Server; der legt es ab und reicht es an alle weiter, die es nicht mehr laden
 * koennen. Damit haengt eine Aufnahme nicht mehr am Ablaufdatum eines fremden Links.</p>
 *
 * <p>Angenommen werden Bilder nur von Operatoren - sonst koennte jeder Spieler die
 * Skins der Szene austauschen.</p>
 */
public final class SkinMirror
{
    private static final Map<String, byte[]> MEMORY = new HashMap<String, byte[]>();

    /** Wer auf welches Bild wartet. */
    private static final Map<String, Set<UUID>> WAITING = new HashMap<String, Set<UUID>>();

    private static File folder;

    private SkinMirror()
    {}

    public static byte[] get(String url)
    {
        byte[] data = MEMORY.get(url);

        if (data != null)
        {
            return data;
        }

        try
        {
            File file = file(url);

            if (file.isFile())
            {
                data = Files.readAllBytes(file.toPath());
                MEMORY.put(url, data);

                return data;
            }
        }
        catch (Throwable ignored)
        {}

        return null;
    }

    public static void put(String url, byte[] data)
    {
        MEMORY.put(url, data);

        try
        {
            File file = file(url);
            File temp = new File(file.getPath() + ".tmp");

            file.getParentFile().mkdirs();
            Files.write(temp.toPath(), data);
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);

            System.out.println("[Blockbuster] Skin-Spiegel: " + url + " gesichert (" + data.length + " Bytes)");
        }
        catch (Throwable t)
        {
            System.out.println("[Blockbuster] Skin-Spiegel nicht beschreibbar: " + t);
        }
    }

    /**
     * Diesen Spieler als Wartenden vormerken.
     *
     * @return true, wenn die uebrigen Spieler gefragt werden sollen (erste Anfrage)
     */
    public static boolean remember(String url, EntityPlayerMP player)
    {
        Set<UUID> waiting = WAITING.get(url);
        boolean first = waiting == null;

        if (first)
        {
            waiting = new HashSet<UUID>();
            WAITING.put(url, waiting);
        }

        waiting.add(player.getUniqueID());

        return first;
    }

    /**
     * Alle Wartenden zu dieser URL, und die Liste danach leeren.
     */
    public static List<EntityPlayerMP> takeWaiting(String url, MinecraftServer server)
    {
        List<EntityPlayerMP> players = new ArrayList<EntityPlayerMP>();
        Set<UUID> waiting = WAITING.remove(url);

        if (waiting == null || server == null)
        {
            return players;
        }

        for (UUID id : waiting)
        {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(id);

            if (player != null)
            {
                players.add(player);
            }
        }

        return players;
    }

    private static File file(String url) throws Exception
    {
        if (folder == null)
        {
            folder = new File(Loader.instance().getConfigDir(), "blockbuster/skin_mirror");
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        StringBuilder name = new StringBuilder();

        for (byte b : digest.digest(url.getBytes(Charset.forName("UTF-8"))))
        {
            name.append(String.format("%02x", b));
        }

        return new File(folder, name + ".img");
    }
}
