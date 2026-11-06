package mchorse.blockbuster.network.server.scene;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.capabilities.recording.Recording;
import mchorse.blockbuster.network.common.scene.PacketSceneCast;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.entity.player.EntityPlayerMP;

public class ServerHandlerSceneCast extends ServerMessageHandler<PacketSceneCast>
{
    @Override
    public void run(EntityPlayerMP player, PacketSceneCast message)
    {
        if (!OpHelper.isPlayerOp(player) || message.location.isEmpty())
        {
            return;
        }

        try
        {
            /* Nur das uebernehmen, was dieser Spieler geaendert hat - auf die
             * aktuelle Fassung, damit Aenderungen anderer Spieler bleiben. */
            if (message.merge != null && message.location.getScene() != null)
            {
                mchorse.blockbuster.recording.scene.Scene current = CommonProxy.scenes.load(message.location.getFilename());

                if (current != null)
                {
                    message.merge.apply(current, message.location.getScene());
                }
            }

            CommonProxy.scenes.save(message.location.getFilename(), message.location.getScene(), mchorse.blockbuster.Blockbuster.sceneSaveUpdate.get() && !message.live);
            Recording.get(player).setLastScene(message.location.getFilename());

            /* Alle, die die Szene offen haben, sehen sofort den neuen Stand. */
            mchorse.blockbuster.recording.scene.SceneEditors.sendScene(message.location.getFilename(), message.location.getScene());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}