package mchorse.blockbuster.client.render;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Die Skins von GtwNpc (Ordner GtwNpc/skins im Spielverzeichnis), per Reflection
 * auf fr.aym.gtwnpc.client.skin.SkinRepository. Ohne GtwNpc einfach leer.
 */
@SideOnly(Side.CLIENT)
public final class GtwSkins
{
    public static final String PREFIX = "gtwnpcmod:skin/";

    private static boolean resolved;
    private static Method methodGetSkins;
    private static Method methodIsSmall;

    private GtwSkins()
    {}

    public static boolean available()
    {
        return resolve();
    }

    /** Alle Skins als ResourceLocation-Text, sortiert. */
    public static List<String> all()
    {
        List<String> list = new ArrayList<String>();

        if (!resolve())
        {
            return list;
        }

        try
        {
            Map<?, ?> skins = (Map<?, ?>) methodGetSkins.invoke(null);

            for (Object value : skins.values())
            {
                if (value instanceof Collection)
                {
                    for (Object location : (Collection<?>) value)
                    {
                        String text = String.valueOf(location);

                        if (!list.contains(text))
                        {
                            list.add(text);
                        }
                    }
                }
            }
        }
        catch (Throwable t)
        {
            System.out.println("[Blockbuster] GtwNpc-Skins nicht lesbar: " + t);
        }

        Collections.sort(list);

        return list;
    }

    public static boolean isSmall(ResourceLocation location)
    {
        if (!resolve())
        {
            return false;
        }

        try
        {
            return (Boolean) methodIsSmall.invoke(null, location);
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    /** Kurzer Name fuer die Liste: npc_1/skin_5.png */
    public static String shortName(String location)
    {
        return location.startsWith(PREFIX) ? location.substring(PREFIX.length()) : location;
    }

    private static boolean resolve()
    {
        if (!resolved)
        {
            resolved = true;

            try
            {
                Class<?> repository = Class.forName("fr.aym.gtwnpc.client.skin.SkinRepository");

                methodGetSkins = repository.getMethod("getSkins");
                methodIsSmall = repository.getMethod("isSmallSkin", ResourceLocation.class);
            }
            catch (Throwable t)
            {
                methodGetSkins = null;
                System.out.println("[Blockbuster] GtwNpc nicht gefunden, keine NPC-Skins fuer Actors: " + t);
            }
        }

        return methodGetSkins != null;
    }
}
