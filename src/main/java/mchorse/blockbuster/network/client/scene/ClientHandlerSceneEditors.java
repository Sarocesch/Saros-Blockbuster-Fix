package mchorse.blockbuster.network.client.scene;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.network.common.scene.PacketSceneEditors;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ClientHandlerSceneEditors extends ClientMessageHandler<PacketSceneEditors>
{
    @Override
    @SideOnly(Side.CLIENT)
    public void run(EntityPlayerSP player, PacketSceneEditors message)
    {
        ClientProxy.panels.scenePanel.setEditors(message.scene, message.names, message.replays);
    }
}
