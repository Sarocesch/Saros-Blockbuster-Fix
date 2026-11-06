package mchorse.blockbuster.recording.appearance;

import net.minecraft.entity.player.EntityPlayerMP;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Liest auf dem Server das Aussehen eines Spielers aus: den MWRP-Skin und das
 * CPM-Modell. Beides nur per Reflection - fehlt eine der Mods, faellt der Teil weg.
 */
public final class AppearanceCapture
{
    private AppearanceCapture()
    {}

    /**
     * @return null, wenn der Spieler weder /skin noch CPM-Modell hat
     */
    public static ActorAppearance capture(EntityPlayerMP player)
    {
        if (player == null)
        {
            return null;
        }

        ActorAppearance look = new ActorAppearance();

        readSkin(player.getUniqueID(), look);
        readCpm(player, look);

        if (!look.isValid())
        {
            return null;
        }

        look.owner = player.getUniqueID().toString();
        look.ownerName = player.getName();
        look.captured = System.currentTimeMillis();
        look.updateHash();

        return look;
    }

    /**
     * MWRP /skin: das fuer CPM vorbereitete PNG (Augenflaeche ausgestanzt), wie es
     * auch der lebende Spieler traegt.
     */
    private static void readSkin(UUID id, ActorAppearance look)
    {
        try
        {
            byte[] png = null;
            String model = null;
            Object data = Class.forName("de.sarocesch.minewacheroleplaymod.skin.SkinManager").getMethod("get", UUID.class).invoke(null, id);

            if (data != null)
            {
                png = (byte[]) data.getClass().getField("pngBytes").get(data);
                model = (String) data.getClass().getField("model").get(data);
            }

            if (png == null)
            {
                /* Ohne MySQL ist SkinManager nach einem Neustart leer, die Datei liegt aber noch da. */
                Path path = (Path) Class.forName("de.sarocesch.minewacheroleplaymod.skin.SkinLoader").getMethod("cacheFileFor", UUID.class).invoke(null, id);

                if (path != null && Files.isRegularFile(path))
                {
                    png = Files.readAllBytes(path);
                }
            }

            if (png == null || png.length == 0)
            {
                return;
            }

            if (png.length > ActorAppearance.MAX_BYTES)
            {
                System.out.println("[Blockbuster] Aussehen: /skin von " + id + " ist zu gross (" + png.length + " Bytes)");

                return;
            }

            if (model == null)
            {
                model = detectModel(png);
            }

            look.skin = png;
            look.slim = "slim".equals(model);
        }
        catch (ClassNotFoundException e)
        {
            /* MWRP nicht installiert */
        }
        catch (Throwable t)
        {
            System.out.println("[Blockbuster] Aussehen: /skin nicht lesbar: " + t);
        }
    }

    private static String detectModel(byte[] png)
    {
        try
        {
            Method validate = Class.forName("de.sarocesch.minewacheroleplaymod.skin.SkinValidator").getMethod("validate", byte[].class, String.class);
            Object result = validate.invoke(null, png, null);

            return (String) result.getClass().getField("model").get(result);
        }
        catch (Throwable t)
        {
            return "default";
        }
    }

    /**
     * CPM: das Modell, das CPM selbst an alle Clients schickt (NetH$ServerNetH,
     * per ASM an NetHandlerPlayServer gehaengt).
     */
    private static void readCpm(EntityPlayerMP player, ActorAppearance look)
    {
        try
        {
            Object connection = player.connection;

            if (connection == null)
            {
                return;
            }

            Object data = connection.getClass().getMethod("cpm$getEncodedModelData").invoke(connection);

            if (data == null)
            {
                return;
            }

            byte[] model = (byte[]) data.getClass().getField("data").get(data);

            if (model == null || model.length == 0)
            {
                return;
            }

            if (model.length > ActorAppearance.MAX_BYTES)
            {
                System.out.println("[Blockbuster] Aussehen: CPM-Modell von " + player.getName() + " ist zu gross (" + model.length + " Bytes), wird weggelassen");

                return;
            }

            look.cpm = model.clone();
            look.cpmForced = data.getClass().getField("forced").getBoolean(data);

            /* Ebenen-Zustaende (z.B. Augen). [0] = Pose, [1] = Geste - die sollen nicht
             * die ganze Aufnahme ueber festhaengen, also genullt. */
            byte[] gestures = (byte[]) data.getClass().getField("gestureData").get(data);

            if (gestures != null && gestures.length > 2 && gestures.length <= 256)
            {
                look.gestures = gestures.clone();
                look.gestures[0] = 0;
                look.gestures[1] = 0;
            }
        }
        catch (NoSuchMethodException e)
        {
            /* CPM nicht installiert */
        }
        catch (Throwable t)
        {
            System.out.println("[Blockbuster] Aussehen: CPM-Modell nicht lesbar: " + t);
        }
    }
}
