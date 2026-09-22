package mchorse.blockbuster.client.textures;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.actions.MorphAction;
import mchorse.mclib.utils.ReflectionUtils;
import mchorse.mclib.utils.resources.FilteredResourceLocation;
import mchorse.mclib.utils.resources.MultiResourceLocation;
import mchorse.mclib.utils.resources.MultiskinThread;
import mchorse.mclib.utils.resources.TextureLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;

/**
 * URL download thread
 *
 * This bad boy downloads a picture from internet and puts it into the
 * texture manager's.
 */
public class URLDownloadThread implements Runnable
{
    /**
     * Look, MA! I'm Google Chrome on OS X!!! xD
     */
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    /**
     * Wartezeit vor jedem Versuch (ms). Ein Wackler im Netz, ein kurz
     * ueberlasteter Server oder ein Rate-Limit liess den Skin vorher fuer die
     * ganze Sitzung schwarz - es gab genau einen Versuch.
     */
    private static final int[] RETRY_DELAYS = {0, 2000, 5000, 15000, 30000};

    private static final int MAX_REDIRECTS = 6;
    private static final int MAX_BYTES = 32 * 1024 * 1024;

    /**
     * URLs, die gerade heruntergeladen werden. Ohne diesen Guard spawnt jeder
     * getInputStream-Aufruf fuer dieselbe URL (z.B. Multi-Skin-Merges oder mehrere
     * Bloecke mit demselben Skin im selben Frame) einen eigenen Download-Thread.
     */
    private static final Set<ResourceLocation> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    /**
     * Fertig heruntergeladene Bilder, Schluessel = URL. Damit liefert ActorsPack
     * ein bekanntes Bild sofort, statt erst den schwarzen Platzhalter zu zeigen.
     */
    private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<String, byte[]>();

    /** In dieser Sitzung schon erfolgreich geholt - kein zweiter Download. */
    private static final Set<String> FRESH = ConcurrentHashMap.newKeySet();

    /** URLs, fuer die gerade irgendwo der schwarze Platzhalter angezeigt wird. */
    private static final Set<String> PLACEHOLDER = ConcurrentHashMap.newKeySet();

    private static File cacheDir;

    private ResourceLocation url;

    public URLDownloadThread(ResourceLocation url)
    {
        this.url = url;
    }

    /** Starts a download thread for this URL unless one is already running */
    public static void startIfAbsent(ResourceLocation url)
    {
        URLTextureUploader.ensureRegistered();

        if (!FRESH.contains(url.toString()) && IN_FLIGHT.add(url))
        {
            Thread thread = new Thread(new URLDownloadThread(url), "Blockbuster-URLSkin");
            thread.setDaemon(true);
            thread.start();
        }
    }

    /**
     * Das zuletzt geladene Bild dieser URL, aus dem Arbeitsspeicher oder von der
     * Platte (auch aus frueheren Sitzungen), oder null.
     *
     * <p>Ohne das zeigte jede URL beim ersten Binden den schwarzen Platzhalter,
     * bis der Download durch war - sichtbar als kurzes Schwarz bei jedem Skin-
     * oder Modellwechsel in einer Aufnahme. mclib setzt Multi-Skins aus Ebenen
     * zusammen, die es ueber genau diesen Weg liest; eine URL-Ebene wurde dort
     * sogar dauerhaft schwarz eingebacken.</p>
     */
    public static byte[] cached(ResourceLocation url)
    {
        String key = url.toString();
        byte[] data = CACHE.get(key);

        if (data == null)
        {
            data = readDisk(key);

            if (data != null)
            {
                CACHE.put(key, data);
            }
        }

        return data;
    }

    public static void markPlaceholder(ResourceLocation url)
    {
        PLACEHOLDER.add(url.toString());
    }

    /**
     * Sofort und blockierend laden - nur abseits des Render-Threads aufrufen.
     * Ein Versuch; scheitert er, uebernehmen die Wiederholungen im Hintergrund.
     */
    public static byte[] loadNow(ResourceLocation url)
    {
        byte[] data = cached(url);

        if (data != null)
        {
            return data;
        }

        String key = url.toString();

        try
        {
            byte[] fetched = fetch(key);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(fetched));

            if (image == null)
            {
                return null;
            }

            CACHE.put(key, fetched);
            FRESH.add(key);
            writeDisk(key, fetched);

            if (PLACEHOLDER.remove(key))
            {
                URLTextureUploader.enqueue(url, toIntArgb(image));
            }

            return fetched;
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    /**
     * Alle Skins aus den Morph-Aktionen einer Aufnahme vorladen, bevor der
     * Wechsel im Bild passiert.
     */
    public static void prefetchActions(List<List<Action>> actions)
    {
        if (actions == null)
        {
            return;
        }

        for (List<Action> tick : actions)
        {
            if (tick == null)
            {
                continue;
            }

            for (Action action : tick)
            {
                if (action instanceof MorphAction && ((MorphAction) action).morph != null)
                {
                    try
                    {
                        prefetch(((MorphAction) action).morph.toNBT());
                    }
                    catch (Throwable ignored)
                    {}
                }
            }
        }
    }

    /**
     * Jede URL in diesem NBT (Skin, Multi-Skin-Ebenen, Body Parts) vorladen,
     * sofern sie noch nicht im Zwischenspeicher liegt.
     */
    public static void prefetch(NBTBase tag)
    {
        if (tag instanceof NBTTagString)
        {
            String value = ((NBTTagString) tag).getString();

            if (value.startsWith("http://") || value.startsWith("https://"))
            {
                /* Dieselbe Klasse, die RLUtils.create fuer URLs baut (behaelt die
                 * Gross-/Kleinschreibung), damit der Schluessel exakt passt. */
                ResourceLocation url = new TextureLocation(value);

                if (url.getResourcePath().startsWith("//") && cached(url) == null)
                {
                    startIfAbsent(url);
                }
            }
        }
        else if (tag instanceof NBTTagCompound)
        {
            NBTTagCompound compound = (NBTTagCompound) tag;

            for (String key : compound.getKeySet())
            {
                prefetch(compound.getTag(key));
            }
        }
        else if (tag instanceof NBTTagList)
        {
            NBTTagList list = (NBTTagList) tag;

            for (int i = 0; i < list.tagCount(); i++)
            {
                prefetch(list.get(i));
            }
        }
    }

    public static InputStream downloadImage(final ResourceLocation url) throws IOException
    {
        return new ByteArrayInputStream(fetch(url.toString()));
    }

    /**
     * Laedt die Bytes hinter einer URL.
     *
     * <p>Drei Gruende, warum ein erreichbarer Link vorher trotzdem schwarz blieb:</p>
     * <ul>
     * <li>Umleitungen: HttpURLConnection folgt keiner Umleitung von http auf https
     * (oder zurueck). Viele Download-Links leiten genau so um - geladen wurde dann
     * die Umleitungsseite statt des Bildes. Hier wird jede Umleitung selbst verfolgt.</li>
     * <li>Content-Type: Nur "image/..." wurde angenommen. Download-Links liefern oft
     * "application/octet-stream" - derselbe Server, anderer Link, schwarz. Ob es ein
     * Bild ist, entscheidet jetzt der Bild-Decoder, nicht der Header.</li>
     * <li>Kein Zeitlimit: eine haengende Verbindung blockierte die URL fuer immer.</li>
     * </ul>
     */
    private static byte[] fetch(String address) throws IOException
    {
        for (int hop = 0; hop < MAX_REDIRECTS; hop++)
        {
            URLConnection con = new URL(address).openConnection();

            con.setConnectTimeout(10000);
            con.setReadTimeout(20000);
            con.setRequestProperty("User-Agent", USER_AGENT);
            con.setRequestProperty("Accept", "image/png,image/*;q=0.9,*/*;q=0.8");

            if (con instanceof HttpURLConnection)
            {
                HttpURLConnection http = (HttpURLConnection) con;

                http.setInstanceFollowRedirects(false);

                int code = http.getResponseCode();

                if (code >= 300 && code < 400)
                {
                    String location = http.getHeaderField("Location");

                    http.disconnect();

                    if (location == null)
                    {
                        throw new IOException("HTTP " + code + " ohne Umleitungsziel");
                    }

                    address = new URL(new URL(address), location).toString();

                    continue;
                }

                if (code < 200 || code >= 300)
                {
                    http.disconnect();

                    throw new IOException("HTTP " + code + " von " + address);
                }
            }

            InputStream stream = con.getInputStream();

            try
            {
                return readAll(stream);
            }
            finally
            {
                stream.close();
            }
        }

        throw new IOException("zu viele Umleitungen");
    }

    private static byte[] readAll(InputStream stream) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        byte[] buffer = new byte[16 * 1024];
        int read;

        while ((read = stream.read(buffer)) != -1)
        {
            out.write(buffer, 0, read);

            if (out.size() > MAX_BYTES)
            {
                throw new IOException("Datei groesser als " + (MAX_BYTES / 1024 / 1024) + " MB");
            }
        }

        return out.toByteArray();
    }

    /**
     * Was kam statt eines lesbaren Bildes? Fuer die Logzeile, damit man am Log
     * eines Kameramanns sieht, warum sein Skin schwarz ist.
     */
    private static String describe(byte[] data)
    {
        if (data.length == 0)
        {
            return "leere Antwort";
        }

        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P')
        {
            return "WebP, das Minecraft nicht lesen kann - als PNG hochladen";
        }

        String head = new String(data, 0, Math.min(data.length, 64), java.nio.charset.StandardCharsets.ISO_8859_1).trim().toLowerCase();

        if (head.startsWith("<"))
        {
            return "HTML-Seite statt Bild (Link fuehrt auf eine Webseite, nicht direkt aufs Bild)";
        }

        return data.length + " Bytes, unbekanntes Format";
    }

    /* Zwischenspeicher auf der Platte: config/blockbuster/url_cache/<sha1>.img */

    private static File cacheFile(String key) throws Exception
    {
        if (cacheDir == null)
        {
            cacheDir = new File(Minecraft.getMinecraft().mcDataDir, "config/blockbuster/url_cache");
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        StringBuilder name = new StringBuilder();

        for (byte b : digest.digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        {
            name.append(String.format("%02x", b));
        }

        return new File(cacheDir, name + ".img");
    }

    private static byte[] readDisk(String key)
    {
        try
        {
            File file = cacheFile(key);

            return file.isFile() ? Files.readAllBytes(file.toPath()) : null;
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private static void writeDisk(String key, byte[] data)
    {
        try
        {
            File file = cacheFile(key);
            File temp = new File(file.getPath() + ".tmp");

            file.getParentFile().mkdirs();
            Files.write(temp.toPath(), data);
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        catch (Throwable t)
        {
            System.out.println("[Blockbuster] Skin-Zwischenspeicher nicht beschreibbar: " + t);
        }
    }

    public static void addToManager(ResourceLocation url, InputStream is) throws IOException
    {
        uploadToManager(url, ImageIO.read(is));
    }

    /** Nur der GL-Upload (braucht GL-Kontext -> Main-Thread). Bild ist bereits dekodiert. */
    public static void uploadToManager(ResourceLocation url, BufferedImage image)
    {
        if (image == null) return;

        TextureManager manager = Minecraft.getMinecraft().renderEngine;
        Map<ResourceLocation, ITextureObject> map = ReflectionUtils.getTextures(manager);
        ITextureObject existing = map.get(url);
        boolean replaced = existing != null && existing.getClass() == SimpleTexture.class;

        if (replaced)
        {
            /* In die Textur schreiben, die schon den Platzhalter traegt, statt
             * sie in der Tabelle auszutauschen. Wer das alte Texturobjekt
             * festhielt, zeigte sonst weiter schwarz. */
            TextureUtil.uploadTextureImageAllocate(existing.getGlTextureId(), image, false, false);
        }
        else
        {
            SimpleTexture texture = new SimpleTexture(url);
            TextureUtil.uploadTextureImageAllocate(texture.getGlTextureId(), image, false, false);

            map.put(url, texture);
        }

        int refreshed = refreshMultiskins(map, url);

        System.out.println("[Blockbuster] Skin geladen: " + url + (replaced ? " (Platzhalter ersetzt)" : "")
                + (refreshed > 0 ? ", Multi-Skins neu zusammengesetzt: " + refreshed : ""));
    }

    /**
     * Multi-Skins mit dieser URL als Ebene neu zusammensetzen. Solange die URL
     * noch lud, bekam mclib fuer diese Ebene den schwarzen Platzhalter.
     */
    private static int refreshMultiskins(Map<ResourceLocation, ITextureObject> map, ResourceLocation url)
    {
        String key = url.toString();
        int count = 0;

        for (ResourceLocation location : new ArrayList<ResourceLocation>(map.keySet()))
        {
            if (!(location instanceof MultiResourceLocation))
            {
                continue;
            }

            List<FilteredResourceLocation> children = ((MultiResourceLocation) location).children;

            if (children == null)
            {
                continue;
            }

            for (FilteredResourceLocation child : children)
            {
                if (child != null && child.path != null && key.equals(child.path.toString()))
                {
                    MultiskinThread.add((MultiResourceLocation) location);
                    count++;

                    break;
                }
            }
        }

        return count;
    }

    @Override
    public void run()
    {
        try
        {
            // Der langsame Teil (HTTP-Download + Bild-Decode + Pixelformat-Konvertierung)
            // laeuft auf DIESEM Hintergrund-Thread. Der GL-Upload passiert gedrosselt
            // ueber URLTextureUploader (max. wenige Uploads pro Tick), statt dass alle
            // gleichzeitig fertigen Downloads im selben Frame hochgeladen werden.
            BufferedImage image = null;
            byte[] data = null;
            String problem = "unbekannt";

            for (int attempt = 0; attempt < RETRY_DELAYS.length && image == null; attempt++)
            {
                if (RETRY_DELAYS[attempt] > 0)
                {
                    Thread.sleep(RETRY_DELAYS[attempt]);
                }

                try
                {
                    data = fetch(this.url.toString());
                    image = ImageIO.read(new ByteArrayInputStream(data));

                    if (image == null)
                    {
                        problem = describe(data);
                    }
                }
                catch (IOException e)
                {
                    problem = e.toString();
                }
            }

            if (image == null)
            {
                System.out.println("[Blockbuster] Skin bleibt schwarz, " + RETRY_DELAYS.length + " Versuche gescheitert: " + this.url + " -> " + problem);

                return;
            }

            String key = this.url.toString();
            byte[] previous = cached(this.url);
            boolean changed = previous == null || !Arrays.equals(previous, data);

            CACHE.put(key, data);
            FRESH.add(key);

            if (changed)
            {
                writeDisk(key, data);
            }

            /* Hochladen, wenn das Bild neu/anders ist ODER irgendwo noch der
             * Platzhalter steht. Nur "geaendert" reichte nicht: lag das Bild schon
             * im Speicher, blieb eine Textur mit Platzhalter fuer immer schwarz. */
            if (changed | PLACEHOLDER.remove(key))
            {
                URLTextureUploader.enqueue(this.url, toIntArgb(image));
            }
        }
        catch (InterruptedException e)
        {}
        catch (Throwable t)
        {
            System.out.println("[Blockbuster] Skin " + this.url + " konnte nicht geladen werden: " + t);
        }
        finally
        {
            IN_FLIGHT.remove(this.url);
        }
    }

    /**
     * uploadTextureImageAllocate's getRGB() ist auf den TYPE_4BYTE_ABGR-Bildern des
     * PNG-Decoders eine teure Pro-Pixel-Farbraumkonvertierung auf dem Main-Thread —
     * auf INT_ARGB ist es ein billiger Bitmasken-Pfad. Konvertierung passiert hier
     * auf dem Download-Thread.
     */
    private static BufferedImage toIntArgb(BufferedImage image)
    {
        if (image == null || image.getType() == BufferedImage.TYPE_INT_ARGB)
        {
            return image;
        }

        try
        {
            BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = converted.createGraphics();

            try
            {
                graphics.setComposite(AlphaComposite.Src);
                graphics.drawImage(image, 0, 0, null);
            }
            finally
            {
                graphics.dispose();
            }

            return converted;
        }
        catch (Throwable e)
        {
            return image;
        }
    }
}
