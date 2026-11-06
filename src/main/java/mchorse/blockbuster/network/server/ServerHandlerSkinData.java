package mchorse.blockbuster.network.server;

import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.PacketSkinData;
import mchorse.blockbuster.utils.SkinMirror;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Ein Spieler liefert ein Bild fuer den Spiegel.
 *
 * <p>Nur von Operatoren, sonst koennte jeder die Skins der Szene austauschen.</p>
 */
public class ServerHandlerSkinData extends ServerMessageHandler<PacketSkinData>
{
    @Override
    public void run(EntityPlayerMP player, PacketSkinData message)
    {
        if (!OpHelper.isPlayerOp(player) || message.url == null || message.url.isEmpty())
        {
            return;
        }

        if (message.data == null || message.data.length == 0 || message.data.length > PacketSkinData.MAX_SIZE)
        {
            return;
        }

        if (SkinMirror.get(message.url) == null)
        {
            SkinMirror.put(message.url, message.data);
        }

        for (EntityPlayerMP waiting : SkinMirror.takeWaiting(message.url, player.mcServer))
        {
            Dispatcher.sendTo(new PacketSkinData(message.url, message.data), waiting);
        }
    }
}
