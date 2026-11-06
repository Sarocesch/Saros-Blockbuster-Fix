package mchorse.blockbuster.network.server;

import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.PacketSkinData;
import mchorse.blockbuster.network.common.PacketSkinRequest;
import mchorse.blockbuster.utils.SkinMirror;
import mchorse.mclib.network.ServerMessageHandler;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Ein Spieler kommt an einen Skin nicht heran.
 *
 * <p>Liegt das Bild im Spiegel, geht es sofort zurueck. Sonst werden die uebrigen
 * Spieler gefragt - wer es noch im Zwischenspeicher hat, schickt es.</p>
 */
public class ServerHandlerSkinRequest extends ServerMessageHandler<PacketSkinRequest>
{
    @Override
    public void run(EntityPlayerMP player, PacketSkinRequest message)
    {
        if (message.url == null || message.url.isEmpty())
        {
            return;
        }

        byte[] data = SkinMirror.get(message.url);

        if (data != null)
        {
            Dispatcher.sendTo(new PacketSkinData(message.url, data), player);

            return;
        }

        if (!SkinMirror.remember(message.url, player) || player.mcServer == null)
        {
            return;
        }

        for (EntityPlayerMP other : player.mcServer.getPlayerList().getPlayers())
        {
            if (other != player)
            {
                Dispatcher.sendTo(new PacketSkinRequest(message.url), other);
            }
        }
    }
}
