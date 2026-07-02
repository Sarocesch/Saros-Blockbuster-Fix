package mchorse.blockbuster.client.textures;

import java.awt.image.BufferedImage;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Drains finished URL skin downloads onto the GPU with a per-tick budget.
 *
 * Frueher wurde jeder fertige Download einzeln via addScheduledTask hochgeladen —
 * und alle Scheduled Tasks laufen im SELBEN Frame. Bei vielen Modellbloecken mit
 * URL-Skins in einem Bereich (Chunk-Load -> zig Downloads werden fast gleichzeitig
 * fertig) ergab das einen einzelnen Frame mit hunderten ms GL-Upload = extremer
 * Lagspike. Jetzt laedt jeder Client-Tick hoechstens ein paar Texturen hoch; ein
 * Burst von 50 Skins verteilt sich damit unsichtbar auf ~1-2 Sekunden.
 */
@SideOnly(Side.CLIENT)
public class URLTextureUploader
{
    private static final Queue<PendingUpload> PENDING = new ConcurrentLinkedQueue<PendingUpload>();

    private static final int MAX_UPLOADS_PER_TICK = 2;
    private static final long MAX_MILLIS_PER_TICK = 6;

    private static boolean registered;

    /**
     * Registers the tick handler on first use. Must be called from the main
     * thread (which is the case: URL skins are requested during bindTexture).
     */
    public static void ensureRegistered()
    {
        if (!registered)
        {
            registered = true;
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new URLTextureUploader());
        }
    }

    public static void enqueue(ResourceLocation url, BufferedImage image)
    {
        if (image != null)
        {
            PENDING.add(new PendingUpload(url, image));
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty())
        {
            return;
        }

        long start = System.currentTimeMillis();

        for (int i = 0; i < MAX_UPLOADS_PER_TICK; i++)
        {
            PendingUpload upload = PENDING.poll();

            if (upload == null)
            {
                return;
            }

            URLDownloadThread.uploadToManager(upload.url, upload.image);

            if (System.currentTimeMillis() - start >= MAX_MILLIS_PER_TICK)
            {
                return;
            }
        }
    }

    private static class PendingUpload
    {
        public final ResourceLocation url;
        public final BufferedImage image;

        public PendingUpload(ResourceLocation url, BufferedImage image)
        {
            this.url = url;
            this.image = image;
        }
    }
}
