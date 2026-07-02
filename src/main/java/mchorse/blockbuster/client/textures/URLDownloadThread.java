package mchorse.blockbuster.client.textures;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import mchorse.mclib.utils.ReflectionUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
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
    public static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.110 Safari/537.36";

    /**
     * URLs, die gerade heruntergeladen werden. Ohne diesen Guard spawnt jeder
     * getInputStream-Aufruf fuer dieselbe URL (z.B. Multi-Skin-Merges oder mehrere
     * Bloecke mit demselben Skin im selben Frame) einen eigenen Download-Thread.
     */
    private static final Set<ResourceLocation> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private ResourceLocation url;

    public URLDownloadThread(ResourceLocation url)
    {
        this.url = url;
    }

    /** Starts a download thread for this URL unless one is already running */
    public static void startIfAbsent(ResourceLocation url)
    {
        URLTextureUploader.ensureRegistered();

        if (IN_FLIGHT.add(url))
        {
            Thread thread = new Thread(new URLDownloadThread(url), "Blockbuster-URLSkin");
            thread.setDaemon(true);
            thread.start();
        }
    }

    public static InputStream downloadImage(final ResourceLocation url) throws IOException
    {
        URLConnection con = new URL(url.toString()).openConnection();
        con.setRequestProperty("User-Agent", USER_AGENT);

        InputStream stream = con.getInputStream();
        String type = con.getHeaderField("Content-Type");

        if (type != null && !type.startsWith("image/"))
        {
            return null;
        }

        return stream;
    }

    public static void addToManager(ResourceLocation url, InputStream is) throws IOException
    {
        uploadToManager(url, ImageIO.read(is));
    }

    /** Nur der GL-Upload (braucht GL-Kontext -> Main-Thread). Bild ist bereits dekodiert. */
    public static void uploadToManager(ResourceLocation url, BufferedImage image)
    {
        if (image == null) return;

        SimpleTexture texture = new SimpleTexture(url);
        TextureUtil.uploadTextureImageAllocate(texture.getGlTextureId(), image, false, false);

        TextureManager manager = Minecraft.getMinecraft().renderEngine;
        Map<ResourceLocation, ITextureObject> map = ReflectionUtils.getTextures(manager);

        map.put(url, texture);
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
            InputStream stream = downloadImage(this.url);
            if (stream == null) return;
            BufferedImage image = ImageIO.read(stream);
            URLTextureUploader.enqueue(this.url, toIntArgb(image));
        }
        catch (IOException e)
        {}
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