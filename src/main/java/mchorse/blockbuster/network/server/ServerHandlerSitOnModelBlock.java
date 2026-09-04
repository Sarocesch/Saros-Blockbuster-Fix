package mchorse.blockbuster.network.server;

import mchorse.blockbuster.common.entity.EntitySeat;
import mchorse.blockbuster.common.tileentity.TileEntityModel;
import mchorse.blockbuster.network.common.PacketSitOnModelBlock;
import mchorse.mclib.network.ServerMessageHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

public class ServerHandlerSitOnModelBlock extends ServerMessageHandler<PacketSitOnModelBlock>
{
    /** Reichweite wie beim normalen Blockklick, mit etwas Luft. Quadriert. */
    private static final double MAX_DISTANCE = 8 * 8;

    @Override
    public void run(EntityPlayerMP player, PacketSitOnModelBlock message)
    {
        if (player.isRiding())
        {
            return;
        }

        final BlockPos pos = message.pos;

        /* Ein Paket kann alles behaupten - Reichweite gehoert deshalb hierher und
         * nicht auf den Client */
        if (player.getDistanceSq(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > MAX_DISTANCE)
        {
            return;
        }

        final TileEntity tile = this.getTE(player, pos);

        if (!(tile instanceof TileEntityModel))
        {
            return;
        }

        if (!((TileEntityModel) tile).getSettings().isSeat())
        {
            return;
        }

        EntitySeat.sit(player, pos, ((TileEntityModel) tile).getSettings());
    }
}
