package mchorse.blockbuster.network.client.scene;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.network.common.scene.PacketSceneLive;
import mchorse.blockbuster.recording.scene.Replay;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ClientHandlerSceneLive extends ClientMessageHandler<PacketSceneLive>
{
    @Override
    @SideOnly(Side.CLIENT)
    public void run(EntityPlayerSP player, PacketSceneLive message)
    {
        if (message.location == null || message.location.getScene() == null)
        {
            return;
        }

        for (Replay replay : message.location.getScene().replays)
        {
            if (replay.morph != null)
            {
                mchorse.blockbuster.client.textures.URLDownloadThread.prefetch(replay.morph.toNBT());
            }
        }

        ClientProxy.panels.scenePanel.liveUpdate(message.location);
    }
}
