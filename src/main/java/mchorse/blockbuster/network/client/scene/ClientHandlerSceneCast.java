package mchorse.blockbuster.network.client.scene;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.client.gui.dashboard.GuiBlockbusterPanels;
import mchorse.blockbuster.network.common.scene.PacketSceneCast;
import mchorse.mclib.client.gui.mclib.GuiDashboard;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client handler director cast
 *
 * This client handler is responsible for transferring requested director block
 * cast to current director panel.
 */
public class ClientHandlerSceneCast extends ClientMessageHandler<PacketSceneCast>
{
    @Override
    @SideOnly(Side.CLIENT)
    public void run(EntityPlayerSP player, PacketSceneCast message)
    {
        GuiDashboard dashboard = GuiDashboard.get();
        GuiBlockbusterPanels panels = ClientProxy.panels;
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;

        boolean opened = message.open && screen == null;

        /* Skins der Replays vorladen, damit ein im Editor gewechselter Skin
         * nicht erst beim Actor schwarz aufblitzt. */
        if (message.location != null && message.location.getScene() != null)
        {
            for (mchorse.blockbuster.recording.scene.Replay replay : message.location.getScene().replays)
            {
                if (replay.morph != null)
                {
                    mchorse.blockbuster.client.textures.URLDownloadThread.prefetch(replay.morph.toNBT());
                }
            }
        }

        if (opened)
        {
            panels.scenePanel.openScene(message.location);
        }
        else if (screen == dashboard && !message.open && message.location != null && message.location.isScene()
            && message.location.getFilename().equals(panels.scenePanel.getLocation().getFilename()))
        {
            /* Antwort auf das Holen beim Oeffnen: nicht ungefragt ueber das legen,
             * was der Spieler inzwischen schon eingegeben hat. */
            panels.scenePanel.liveUpdate(message.location);
        }
        else if (screen == dashboard)
        {
            panels.scenePanel.setScene(message.location);
        }
        else
        {
            panels.scenePanel.set(message.location);
        }

        if (opened)
        {
            dashboard.panels.setPanel(panels.scenePanel);
            Minecraft.getMinecraft().displayGuiScreen(GuiDashboard.get());
        }
    }
}