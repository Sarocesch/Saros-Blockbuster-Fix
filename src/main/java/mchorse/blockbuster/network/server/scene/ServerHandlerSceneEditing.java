package mchorse.blockbuster.network.server.scene;

import mchorse.blockbuster.network.common.scene.PacketSceneEditing;
import mchorse.blockbuster.recording.scene.SceneEditors;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.entity.player.EntityPlayerMP;

public class ServerHandlerSceneEditing extends ServerMessageHandler<PacketSceneEditing>
{
    @Override
    public void run(EntityPlayerMP player, PacketSceneEditing message)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        SceneEditors.update(player, message.scene, message.replay);
    }
}
