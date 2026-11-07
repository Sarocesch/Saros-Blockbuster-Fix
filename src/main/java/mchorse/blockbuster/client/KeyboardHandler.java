package mchorse.blockbuster.client;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.client.gui.GuiGun;
import mchorse.blockbuster_pack.morphs.StructureMorph;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/**
 * Separate event handler for keyboard events
 */
@SideOnly(Side.CLIENT)
public class KeyboardHandler
{
    /* Misc. */
    private KeyBinding plause;
    private KeyBinding record;
    private KeyBinding pause;
    private KeyBinding openGun;
    private KeyBinding marker;
    public static KeyBinding zoom;
    public static KeyBinding gunReload;
    public static KeyBinding gunShoot;

    /**
     * Create and register key bindings for mod
     */
    public KeyboardHandler()
    {
        /* Key categories */
        String category = "key.blockbuster.category";

        /* Misc */
        this.plause = new KeyBinding("key.blockbuster.plause_director", Keyboard.KEY_RCONTROL, category);
        this.record = new KeyBinding("key.blockbuster.record_director", Keyboard.KEY_RMENU, category);
        this.pause = new KeyBinding("key.blockbuster.pause_director", Keyboard.KEY_RSHIFT, category);
        this.openGun = new KeyBinding("key.blockbuster.open_gun", Keyboard.KEY_END, category);
        this.zoom = new KeyBinding("key.blockbuster.zoom", 0, category);
        this.gunReload = new KeyBinding("key.blockbuster.gun_reload", 19, category);
        this.gunShoot = new KeyBinding("key.blockbuster.gun_shoot", -100, category);
        /* Komma: M ist im Minewache-Pack schon Karte und MWF-Aufsaetze. */
        this.marker = new KeyBinding("Aufnahme-Marker setzen", Keyboard.KEY_COMMA, category);

        ClientRegistry.registerKeyBinding(this.plause);
        ClientRegistry.registerKeyBinding(this.record);
        ClientRegistry.registerKeyBinding(this.pause);
        ClientRegistry.registerKeyBinding(this.openGun);
        ClientRegistry.registerKeyBinding(this.zoom);
        ClientRegistry.registerKeyBinding(this.gunReload);
        ClientRegistry.registerKeyBinding(this.gunShoot);
        ClientRegistry.registerKeyBinding(this.marker);
    }

    @SubscribeEvent
    public void onUserLogOut(ClientDisconnectionFromServerEvent event)
    {
        ClientProxy.manager.reset();
        ClientProxy.recordingOverlay.setVisible(false);
        RenderingHandler.resetEmitters();

        Minecraft.getMinecraft().addScheduledTask(StructureMorph::cleanUp);
    }


    /** Editor-Felder fuer Marker und MWRP-Posen, ohne den Aktions-Editor selbst anzufassen. */
    @SubscribeEvent
    public void onActionPanels(mchorse.blockbuster.events.ActionPanelRegisterEvent event)
    {
        event.register(mchorse.blockbuster.recording.actions.MarkerAction.class, new mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.actions.GuiMarkerActionPanel(Minecraft.getMinecraft(), event.panel));
        event.register(mchorse.blockbuster.recording.actions.MwrpPoseAction.class, new mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.actions.GuiMwrpPoseActionPanel(Minecraft.getMinecraft(), event.panel));
    }

    private Class<?> littleTilesAnimation;
    private boolean littleTilesResolved;

    /**
     * Klick auf eine laufende LittleTiles-Tuer-Animation (z. B. offene Tuer
     * schliessen): LittleTiles faengt ihn auf dem Client ab und schickt nur ein
     * eigenes Paket - der Server sieht keinen Klick. Hier wird er erkannt und
     * gemeldet, der Server nimmt ihn nur waehrend einer Aufnahme auf.
     */
    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
    public void onLittleTilesClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event)
    {
        this.checkLittleTilesAnimationClick(event);
    }

    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
    public void onLittleTilesClickEmpty(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickEmpty event)
    {
        this.checkLittleTilesAnimationClick(event);
    }

    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
    public void onLittleTilesClickItem(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event)
    {
        this.checkLittleTilesAnimationClick(event);
    }

    private void checkLittleTilesAnimationClick(net.minecraftforge.event.entity.player.PlayerInteractEvent event)
    {
        Minecraft mc = Minecraft.getMinecraft();

        if (!event.getWorld().isRemote || event.getEntityPlayer() != mc.player || event.getHand() != net.minecraft.util.EnumHand.MAIN_HAND)
        {
            return;
        }

        if (!this.littleTilesResolved)
        {
            this.littleTilesResolved = true;

            try
            {
                this.littleTilesAnimation = Class.forName("com.creativemd.littletiles.common.entity.EntityAnimation");
            }
            catch (Throwable t)
            {}
        }

        if (this.littleTilesAnimation == null)
        {
            return;
        }

        net.minecraft.util.math.Vec3d eyes = mc.player.getPositionEyes(1F);
        net.minecraft.util.math.Vec3d end = eyes.add(mc.player.getLook(1F).scale(mc.playerController.getBlockReachDistance()));
        double best = mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.BLOCK
            ? eyes.distanceTo(mc.objectMouseOver.hitVec) : Double.MAX_VALUE;
        net.minecraft.util.math.Vec3d hit = null;

        for (net.minecraft.entity.Entity entity : mc.world.getEntitiesWithinAABB(net.minecraft.entity.Entity.class,
            new net.minecraft.util.math.AxisAlignedBB(eyes.x, eyes.y, eyes.z, end.x, end.y, end.z).grow(1), (e) -> this.littleTilesAnimation.isInstance(e)))
        {
            net.minecraft.util.math.RayTraceResult result = entity.getEntityBoundingBox().calculateIntercept(eyes, end);

            if (result != null && eyes.distanceTo(result.hitVec) < best)
            {
                best = eyes.distanceTo(result.hitVec);
                hit = result.hitVec;
            }
        }

        if (hit != null)
        {
            net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(hit);

            mchorse.blockbuster.network.Dispatcher.sendToServer(new mchorse.blockbuster.network.common.PacketLittleTilesClick(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    /** Eigener Reiter "Loop-Szenen" in der linken Leiste, nach den Blockbuster-Reitern. */
    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOW)
    public void onDashboardPanels(mchorse.mclib.events.RegisterDashboardPanels event)
    {
        if (event.dashboard instanceof mchorse.mclib.client.gui.mclib.GuiDashboard)
        {
            mchorse.mclib.client.gui.mclib.GuiDashboard dashboard = (mchorse.mclib.client.gui.mclib.GuiDashboard) event.dashboard;

            dashboard.panels.registerPanel(new mchorse.blockbuster.client.gui.dashboard.panels.scene.GuiLoopScenesPanel(Minecraft.getMinecraft(), dashboard),
                mchorse.mclib.client.gui.utils.keys.IKey.str("Loop-Szenen"), mchorse.mclib.client.gui.utils.Icons.SERVER);
        }
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event)
    {
        if (this.plause.isPressed())
        {
            if (ClientProxy.panels.scenePanel != null)
            {
                ClientProxy.panels.scenePanel.plause();
            }
        }

        if (this.record.isPressed())
        {
            if (ClientProxy.panels.scenePanel != null)
            {
                ClientProxy.panels.scenePanel.record();
            }
        }

        if (this.pause.isPressed())
        {
            if (ClientProxy.panels.scenePanel != null)
            {
                ClientProxy.panels.scenePanel.pause();
            }
        }

        if (this.marker.isPressed())
        {
            mchorse.blockbuster.network.Dispatcher.sendToServer(new mchorse.blockbuster.network.common.PacketRecordMarker());
        }

        Minecraft mc = Minecraft.getMinecraft();

        if (this.openGun.isPressed() && mc.player.capabilities.isCreativeMode && OpHelper.isPlayerOp())
        {
            ItemStack stack = mc.player.getHeldItemMainhand();

            if (stack.getItem() == Blockbuster.gunItem)
            {
                mc.displayGuiScreen(new GuiGun(stack));
            }
        }
    }
}