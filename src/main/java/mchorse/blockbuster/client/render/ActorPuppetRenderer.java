package mchorse.blockbuster.client.render;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.recording.appearance.ActorAppearance;
import mchorse.blockbuster_pack.morphs.CustomMorph;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Zeichnet einen Actor als echtes Spielermodell mit /skin und CPM-Modell des
 * Aufnehmenden. Gibt false zurueck, wenn der normale Morph gezeichnet werden soll -
 * dann bleibt alles wie bisher.
 */
@SideOnly(Side.CLIENT)
public final class ActorPuppetRenderer
{
    /** Skin-Texturen je Aussehen (Hash). null = Bild nicht lesbar. */
    private static final Map<String, ResourceLocation> SKINS = new HashMap<String, ResourceLocation>();

    /** CPM-Anmeldung je Aussehen: {Sitzung, Weltzeit, nachgeholt}. */
    private static final Map<String, long[]> REGISTERED = new HashMap<String, long[]>();

    /** Zaehlt bei jedem Betreten einer Welt hoch - CPM leert beim Verbinden seine Server-Modelle. */
    private static int session;

    private static boolean eventsRegistered;

    private static boolean cpmResolved;
    private static Method methodAccessGet;
    private static Method methodGetLoader;
    private static Method methodSetModel;

    private ActorPuppetRenderer()
    {}

    public static boolean render(RenderManager manager, EntityActor actor, double x, double y, double z, float yaw, float partialTicks)
    {
        ActorAppearance look = actor.appearance;
        boolean npc = !actor.npcSkin.isEmpty();

        if (look == null && !npc || !isHumanoid(actor.getMorph()))
        {
            return false;
        }

        /* Ohne CPM haette das Gesicht Loecher, wo die 3D-Augen sitzen sollten. */
        if (!npc && look.cpm != null && !resolveCpm())
        {
            return false;
        }

        try
        {
            ensureEvents();

            /* Ein NPC-Skin gewinnt gegen den Skin des Aufnehmenden, ohne CPM. */
            ActorPuppet puppet = npc ? puppetForNpc(actor) : puppetFor(actor, look);

            if (!npc && look.cpm != null)
            {
                registerCpm(puppet, look);
                applyGestures(puppet, look);
            }

            puppet.hideOuter = actor.appearanceHideOuter;
            puppet.mirror();

            RenderPlayer renderer = manager.getSkinMap().get(puppet.getSkinType());

            if (renderer == null)
            {
                return false;
            }

            MwrpPoseBridge.sync(actor);
            renderer.doRender(puppet, x, y, z, yaw, partialTicks);

            return true;
        }
        catch (Throwable t)
        {
            /* Einmal melden und fuer diesen Actor auf den Morph zurueckfallen. */
            System.out.println("[Blockbuster] Spielermodell fuer Actor " + actor.getEntityId() + " fehlgeschlagen, zeige Morph: " + t);
            actor.appearance = null;
            actor.appearancePuppet = null;
            actor.npcSkin = "";

            return false;
        }
    }

    /**
     * Nur menschenfoermige Morphs ersetzen. Wechselt eine Aufnahme per Morph-Aktion
     * auf ein Tier oder einen Gegenstand, bleibt dieser Morph sichtbar.
     */
    private static boolean isHumanoid(AbstractMorph morph)
    {
        if (morph == null)
        {
            return true;
        }

        if (morph instanceof CustomMorph)
        {
            CustomMorph custom = (CustomMorph) morph;

            return custom.model == null || (custom.model.limbs.containsKey("head") && custom.model.limbs.containsKey("body"));
        }

        return morph.getClass().getName().equals("mchorse.vanilla_pack.morphs.PlayerMorph");
    }

    private static ActorPuppet puppetFor(EntityActor actor, ActorAppearance look) throws Exception
    {
        Object existing = actor.appearancePuppet;

        if (existing instanceof ActorPuppet && ((ActorPuppet) existing).hash.equals(look.hash))
        {
            return (ActorPuppet) existing;
        }

        ActorPuppet puppet = new ActorPuppet(actor, look.hash, "bb_actor");

        puppet.slim = look.slim;
        puppet.skin = skinFor(look);
        actor.appearancePuppet = puppet;

        /* Metamorphs Morph-Zustand startet mit animation = 0, und 0 gilt als "Morph-
         * Animation laeuft". Erst ein Spieler-Tick setzt ihn auf -1 - der Stellvertreter
         * wird nie getickt. Beim Zeichnen setzt Metamorph dann die Farbe auf
         * alpha = 1 + partialTicks/10; CPM uebernimmt sie fuer seine Wuerfel, das Byte
         * laeuft ueber (1.05 -> 11/255), und der Alpha-Test verwirft die Augen. Nur bei
         * partialTicks ~ 0 blitzten sie kurz auf. Einmal aktualisieren = nicht animiert. */
        mchorse.metamorph.capabilities.morphing.IMorphing morphing = mchorse.metamorph.capabilities.morphing.Morphing.get(puppet);

        if (morphing != null)
        {
            morphing.update(puppet);
        }

        return puppet;
    }

    /**
     * Stellvertreter mit einem GtwNpc-Skin. Eigener Profil-Name je Skin, so findet
     * CPM dafuer nie ein Modell. Die Textur laedt GtwNpc selbst (SkinTexture).
     */
    private static ActorPuppet puppetForNpc(EntityActor actor)
    {
        String key = "npc:" + actor.npcSkin;
        Object existing = actor.appearancePuppet;

        if (existing instanceof ActorPuppet && ((ActorPuppet) existing).hash.equals(key))
        {
            return (ActorPuppet) existing;
        }

        ResourceLocation location = new ResourceLocation(actor.npcSkin);
        ActorPuppet puppet = new ActorPuppet(actor, key, "bb_actor");

        puppet.slim = GtwSkins.isSmall(location);
        /* Fehlt der Skin auf diesem Rechner: Steve statt Schachbrett. */
        puppet.skin = GtwSkins.all().contains(actor.npcSkin) ? location : null;
        actor.appearancePuppet = puppet;

        /* Wie in puppetFor: sonst verwirft der Alpha-Test Teile des Modells. */
        mchorse.metamorph.capabilities.morphing.IMorphing morphing = mchorse.metamorph.capabilities.morphing.Morphing.get(puppet);

        if (morphing != null)
        {
            morphing.update(puppet);
        }

        return puppet;
    }

    /**
     * Das rohe PNG, ohne Vanilla-Aufbereitung: die ausgestanzten Augen muessen
     * durchsichtig bleiben, dort sitzen die 3D-Augen von CPM.
     */
    private static ResourceLocation skinFor(ActorAppearance look) throws Exception
    {
        if (look.skin == null)
        {
            return null;
        }

        if (SKINS.containsKey(look.hash))
        {
            return SKINS.get(look.hash);
        }

        ResourceLocation location = null;
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(look.skin));

        if (image != null)
        {
            location = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("bb_actor_skin_" + look.hash, new DynamicTexture(image));
        }

        SKINS.put(look.hash, location);

        return location;
    }

    private static boolean resolveCpm()
    {
        if (!cpmResolved)
        {
            cpmResolved = true;

            try
            {
                Class<?> access = Class.forName("com.tom.cpm.shared.MinecraftClientAccess");

                methodAccessGet = access.getMethod("get");
                methodGetLoader = access.getMethod("getDefinitionLoader");
                methodSetModel = Class.forName("com.tom.cpm.shared.definition.ModelDefinitionLoader").getMethod("setModel", Object.class, byte[].class, boolean.class);
            }
            catch (Throwable t)
            {
                methodSetModel = null;
                System.out.println("[Blockbuster] CPM nicht gefunden, Actors mit CPM-Modell zeigen ihren Morph: " + t);
            }
        }

        return methodSetModel != null;
    }

    /**
     * Das CPM-Modell fuer das GameProfile des Stellvertreters anmelden - derselbe
     * Aufruf, den CPMs SetSkin-Paket fuer echte Spieler macht. Einmal je Sitzung,
     * plus einmal etwas spaeter: CPM leert seine Server-Modelle beim Verbinden
     * (HelloS2C), und das kann nach dem ersten Bild ankommen.
     */
    private static void registerCpm(ActorPuppet puppet, ActorAppearance look) throws Exception
    {
        long time = puppet.world.getTotalWorldTime();
        long[] state = REGISTERED.get(look.hash);

        if (state == null || state[0] != session)
        {
            setModel(puppet, look);
            REGISTERED.put(look.hash, new long[] {session, time, 0});
        }
        else if (state[2] == 0 && time - state[1] >= 100)
        {
            setModel(puppet, look);
            state[2] = 1;
        }
    }

    /** Pose 0, Geste 0, alle Ebenen auf dem Standardwert des Modells. */
    private static final byte[] DEFAULT_GESTURES = new byte[] {0, 0};

    private static Method methodLoadPlayer;
    private static java.lang.reflect.Field fieldAnimState;
    private static java.lang.reflect.Field fieldGestureData;

    /**
     * CPM-Ebenen fuer den Stellvertreter setzen.
     *
     * <p>Auf einem Server mit CPM-Gesten legt CPM die Ebenen eines Modells nur an, wenn
     * der Spieler Gesten-Daten hat (AnimationEngine, Zweig mit gestureData). Echte
     * Spieler bekommen sie vom Server, der Stellvertreter nie - dann fiel CPM in den
     * Alt-Zweig: keine Ebenen (keine Augen), und die Hautschicht-Bits wurden als
     * Gesten-Nummer gelesen (Flackern).</p>
     */
    private static void applyGestures(ActorPuppet puppet, ActorAppearance look) throws Exception
    {
        Object access = methodAccessGet.invoke(null);
        Object loader = access == null ? null : methodGetLoader.invoke(access);

        if (loader == null)
        {
            return;
        }

        if (methodLoadPlayer == null)
        {
            methodLoadPlayer = loader.getClass().getMethod("loadPlayer", Object.class, String.class);
            fieldAnimState = Class.forName("com.tom.cpm.shared.config.Player").getField("animState");
            fieldGestureData = Class.forName("com.tom.cpm.shared.animation.AnimationState").getField("gestureData");
        }

        Object player = methodLoadPlayer.invoke(loader, puppet.getGameProfile(), "player");

        if (player == null)
        {
            return;
        }

        Object state = fieldAnimState.get(player);
        byte[] wanted = look.gestures != null && look.gestures.length > 1 ? look.gestures : DEFAULT_GESTURES;

        if (state != null && fieldGestureData.get(state) != wanted)
        {
            fieldGestureData.set(state, wanted);
        }
    }

    private static void setModel(ActorPuppet puppet, ActorAppearance look) throws Exception
    {
        Object access = methodAccessGet.invoke(null);
        Object loader = access == null ? null : methodGetLoader.invoke(access);

        if (loader != null)
        {
            methodSetModel.invoke(loader, puppet.getGameProfile(), look.cpm, look.cpmForced);
        }
    }

    private static void ensureEvents()
    {
        if (!eventsRegistered)
        {
            eventsRegistered = true;
            MinecraftForge.EVENT_BUS.register(new Events());
        }
    }

    public static class Events
    {
        /** Kein Spielername ueber dem Stellvertreter - den Actor-Namen zeichnet RenderActor. */
        @SubscribeEvent
        public void onSpecials(RenderLivingEvent.Specials.Pre<?> event)
        {
            if (event.getEntity() instanceof ActorPuppet)
            {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public void onWorldLoad(WorldEvent.Load event)
        {
            if (event.getWorld().isRemote)
            {
                session++;
            }
        }
    }
}
