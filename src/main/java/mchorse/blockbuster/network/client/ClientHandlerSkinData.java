package mchorse.blockbuster.network.client;

import mchorse.blockbuster.client.textures.URLDownloadThread;
import mchorse.blockbuster.network.common.PacketSkinData;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Das Bild kommt vom Server, weil die URL nicht mehr erreichbar ist.
 */
public class ClientHandlerSkinData extends ClientMessageHandler<PacketSkinData>
{
    @Override
    @SideOnly(Side.CLIENT)
    public void run(EntityPlayerSP player, PacketSkinData message)
    {
        if (message.url == null || message.url.isEmpty() || message.data == null || message.data.length == 0)
        {
            return;
        }

        URLDownloadThread.acceptFromServer(message.url, message.data);
    }
}
