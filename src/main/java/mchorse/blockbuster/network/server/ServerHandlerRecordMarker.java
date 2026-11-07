package mchorse.blockbuster.network.server;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.network.common.PacketRecordMarker;
import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.actions.MarkerAction;
import mchorse.mclib.network.ServerMessageHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class ServerHandlerRecordMarker extends ServerMessageHandler<PacketRecordMarker>
{
    @Override
    public void run(EntityPlayerMP player, PacketRecordMarker message)
    {
        List<Action> actions = CommonProxy.manager.getActions(player);

        if (actions == null)
        {
            player.sendStatusMessage(new TextComponentString(TextFormatting.GRAY + "Marker: keine Aufnahme aktiv"), true);

            return;
        }

        int number = CommonProxy.manager.recorders.get(player).nextMarker();

        actions.add(new MarkerAction("M" + number));
        player.sendStatusMessage(new TextComponentString(TextFormatting.YELLOW + "Marker " + number + " gesetzt"), true);
    }
}
