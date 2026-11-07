package mchorse.blockbuster.network.server;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.network.common.PacketLittleTilesClick;
import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.capturing.ActionHandler;
import mchorse.mclib.network.ServerMessageHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class ServerHandlerLittleTilesClick extends ServerMessageHandler<PacketLittleTilesClick>
{
    @Override
    public void run(EntityPlayerMP player, PacketLittleTilesClick message)
    {
        List<Action> events = CommonProxy.manager.getActions(player);

        if (events != null && player.getDistanceSq(message.x + 0.5, message.y + 0.5, message.z + 0.5) < 100)
        {
            ActionHandler.addLittleTilesClick(events, new BlockPos(message.x, message.y, message.z));
        }
    }
}
