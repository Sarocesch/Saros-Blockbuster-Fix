package mchorse.blockbuster.network.client;

import mchorse.blockbuster.client.textures.URLDownloadThread;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.PacketSkinData;
import mchorse.blockbuster.network.common.PacketSkinRequest;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Der Server sucht ein Bild, das jemand anders nicht mehr laden kann.
 * Liegt es hier im Zwischenspeicher, geht es hin.
 */
public class ClientHandlerSkinRequest extends ClientMessageHandler<PacketSkinRequest>
{
    @Override
    @SideOnly(Side.CLIENT)
    public void run(EntityPlayerSP player, PacketSkinRequest message)
    {
        if (message.url == null || message.url.isEmpty())
        {
            return;
        }

        byte[] data = URLDownloadThread.cachedBytes(message.url);

        if (data != null && data.length > 0 && data.length <= PacketSkinData.MAX_SIZE)
        {
            Dispatcher.sendToServer(new PacketSkinData(message.url, data));
        }
    }
}
