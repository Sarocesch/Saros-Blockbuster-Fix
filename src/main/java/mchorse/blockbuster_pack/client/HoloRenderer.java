package mchorse.blockbuster_pack.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Der gesamte Kontakt zu DynamX fuer den Holo-Morph.
 *
 * <p>Alles, was DynamX-Klassen anfasst, steckt hier drin und wird nur ueber
 * {@link #available()} betreten. Ohne DynamX wird die Klasse damit nie geladen und
 * Blockbuster laeuft unveraendert weiter - so wie es die BetterLights-Anbindung auch
 * haelt.</p>
 *
 * <p>Gerendert wird eine echte, aber nie getickte Fahrzeug-Entity ueber den normalen
 * Entity-Pfad. Das ist bewusst so und nicht der rohe Modell-Renderer: nur dieser Weg
 * loest {@code DynamXEntityRenderEvent} aus, wodurch Raeder ihre Seiten-Spiegelung
 * bekommen und Glas korrekt gezeichnet wird. Dasselbe Vorgehen wie beim
 * Hologramm-Block der Roleplay-Mod, der seit laengerem so laeuft.</p>
 */
@SideOnly(Side.CLIENT)
public class HoloRenderer
{
    public static final String DYNAMX = "dynamxmod";

    private static Boolean loaded;

    /** Gebaute Render-Entities, je Fahrzeug und Textur-Variante. */
    private static final Map<String, Object> CACHE = new HashMap<String, Object>();

    /** Fahrzeuge, die sich nicht bauen liessen - nicht jeden Frame erneut versuchen. */
    private static final Map<String, Boolean> FAILED = new HashMap<String, Boolean>();

    /**
     * {@code PackPhysicsEntity.entityTextureId} steht bis zum ersten Tick auf -1 und
     * DynamX zeichnet solange nichts. Die Holo-Entity wird nie getickt, also wird das
     * Feld direkt gesetzt. DynamX liefert unobfuskierte Feldnamen, die Reflection
     * funktioniert also im Dev wie im fertigen Spiel.
     */
    private static java.lang.reflect.Field texIdField;

    private HoloRenderer()
    {}

    public static boolean available()
    {
        if (loaded == null)
        {
            loaded = Loader.isModLoaded(DYNAMX);
        }

        return loaded.booleanValue();
    }

    /**
     * Alle Fahrzeuge, die DynamX kennt, als "Registrierungsname@Variante". Wird beim
     * Oeffnen des Morph-Menues abgefragt, nicht beim Modstart - die Inhaltspakete sind
     * zu dem Zeitpunkt noch gar nicht geladen.
     */
    public static List<String> vehicles()
    {
        final List<String> out = new ArrayList<String>();

        if (!available())
        {
            return out;
        }

        try
        {
            for (Item item : ForgeRegistries.ITEMS)
            {
                if (!(item instanceof fr.dynamx.common.items.ItemModularEntity) || item.getRegistryName() == null)
                {
                    continue;
                }

                final fr.dynamx.common.items.ItemModularEntity spawner = (fr.dynamx.common.items.ItemModularEntity) item;
                final int max = Math.max(0, spawner.getMaxMeta());

                for (int meta = 0; meta <= max; meta++)
                {
                    out.add(item.getRegistryName().toString() + "@" + meta);
                }
            }
        }
        catch (Throwable t)
        {
            /* Ein kaputtes Pack darf nicht das ganze Morph-Menue mitnehmen */
        }

        return out;
    }

    /** Lesbarer Name fuer die Liste im Morph-Menue. */
    public static String label(String id, int meta)
    {
        if (!available())
        {
            return id;
        }

        try
        {
            final Item item = ForgeRegistries.ITEMS.getValue(new net.minecraft.util.ResourceLocation(id));

            if (item instanceof fr.dynamx.common.items.ItemModularEntity)
            {
                final Object info = ((fr.dynamx.common.items.ItemModularEntity) item).getInfo();

                if (info instanceof fr.dynamx.api.contentpack.object.INamedObject)
                {
                    final String name = ((fr.dynamx.api.contentpack.object.INamedObject) info).getName();

                    return meta > 0 ? name + " " + (meta + 1) : name;
                }
            }
        }
        catch (Throwable ignored)
        {}

        return id;
    }

    /**
     * Zeichnet das Fahrzeug am Ursprung des aktuellen Matrix-Zustands.
     *
     * <p>Zwei Durchgaenge wie in der Welt: DynamX zeichnet Undurchsichtiges nur in
     * Durchgang 0 und Durchsichtiges nur in Durchgang 1. Ein einzelner Aufruf liesse
     * also Reifen und Scheiben weg.</p>
     */
    public static void render(String id, int meta, float partialTicks)
    {
        if (!available())
        {
            return;
        }

        final Object entity = entity(id, meta);

        if (entity == null)
        {
            return;
        }

        final RenderManager manager = Minecraft.getMinecraft().getRenderManager();
        final int savedPass = net.minecraftforge.client.MinecraftForgeClient.getRenderPass();
        final boolean hadLighting = GlStateManager.glGetInteger(2896) != 0;

        try
        {
            manager.setRenderShadow(false);
            net.minecraftforge.client.ForgeHooksClient.setRenderPass(0);
            manager.renderEntity((net.minecraft.entity.Entity) entity, 0, 0, 0, 0F, partialTicks, false);
            net.minecraftforge.client.ForgeHooksClient.setRenderPass(1);
            manager.renderEntity((net.minecraft.entity.Entity) entity, 0, 0, 0, 0F, partialTicks, false);
        }
        catch (Throwable t)
        {
            FAILED.put(key(id, meta), Boolean.TRUE);
            CACHE.remove(key(id, meta));
        }
        finally
        {
            /* Der Rest des Frames erbt sonst, was DynamX hier hinterlaesst */
            manager.setRenderShadow(true);
            net.minecraftforge.client.ForgeHooksClient.setRenderPass(savedPass);

            if (hadLighting)
            {
                GlStateManager.enableLighting();
            }

            GlStateManager.color(1F, 1F, 1F, 1F);
        }
    }

    /** Fuer das Vorschaubild im Menue reicht das statische Modell. */
    public static void renderStatic(String id, int meta)
    {
        if (!available())
        {
            return;
        }

        try
        {
            final Item item = ForgeRegistries.ITEMS.getValue(new net.minecraft.util.ResourceLocation(id));

            if (!(item instanceof fr.dynamx.common.items.ItemModularEntity))
            {
                return;
            }

            RenderHelper.enableStandardItemLighting();
            fr.dynamx.utils.client.DynamXRenderUtils.renderCar(
                ((fr.dynamx.common.items.ItemModularEntity) item).getInfo(), (byte) meta);
            RenderHelper.disableStandardItemLighting();
        }
        catch (Throwable ignored)
        {}
    }

    private static String key(String id, int meta)
    {
        return id + "@" + meta;
    }

    /**
     * Baut die Render-Entity einmalig. Sie wird nie getickt und bekommt nie eine
     * Physik - nur ihre Module, damit Raeder und Aufbauten da sind.
     */
    private static Object entity(String id, int meta)
    {
        final String key = key(id, meta);

        if (FAILED.containsKey(key))
        {
            return null;
        }

        Object cached = CACHE.get(key);

        if (cached != null)
        {
            return cached;
        }

        try
        {
            final net.minecraft.world.World world = Minecraft.getMinecraft().world;
            final Item item = ForgeRegistries.ITEMS.getValue(new net.minecraft.util.ResourceLocation(id));

            if (world == null || !(item instanceof fr.dynamx.common.items.ItemModularEntity))
            {
                return null;
            }

            final net.minecraft.entity.Entity spawned = ((fr.dynamx.common.items.ItemModularEntity) item)
                .getSpawnEntity(world, null, new com.jme3.math.Vector3f(0F, 0F, 0F), 0F, meta);

            if (!(spawned instanceof fr.dynamx.common.entities.BaseVehicleEntity))
            {
                FAILED.put(key, Boolean.TRUE);

                return null;
            }

            final fr.dynamx.common.entities.BaseVehicleEntity<?> vehicle =
                (fr.dynamx.common.entities.BaseVehicleEntity<?>) spawned;

            vehicle.initEntityProperties();
            /* DynamX zeichnet nur bei ALL - gesetzt, aber nie getickt, also entsteht
             * auch keine Physik */
            vehicle.initialized = fr.dynamx.common.entities.PhysicsEntity.EnumEntityInitState.ALL;

            if (texIdField == null)
            {
                texIdField = fr.dynamx.common.entities.PackPhysicsEntity.class.getDeclaredField("entityTextureId");
                texIdField.setAccessible(true);
            }

            texIdField.setByte(vehicle, (byte) meta);

            if (vehicle.getPackInfo() == null)
            {
                FAILED.put(key, Boolean.TRUE);

                return null;
            }

            CACHE.put(key, vehicle);

            return vehicle;
        }
        catch (Throwable t)
        {
            FAILED.put(key, Boolean.TRUE);

            return null;
        }
    }

    /** Beim Weltwechsel wegwerfen - die Entities haengen an ihrer Welt. */
    public static void clear()
    {
        CACHE.clear();
        FAILED.clear();
    }
}
