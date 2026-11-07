package mchorse.blockbuster.network.client;

import mchorse.blockbuster.client.gui.dashboard.panels.scene.GuiLoopOverview;
import mchorse.blockbuster.network.common.PacketLoopSceneList;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ClientHandlerLoopSceneList extends ClientMessageHandler<PacketLoopSceneList>
{
    @Override
    @SideOnly(Side.CLIENT)
    public void run(EntityPlayerSP player, PacketLoopSceneList message)
    {
        GuiLoopOverview.receive(message.entries);
    }
}
