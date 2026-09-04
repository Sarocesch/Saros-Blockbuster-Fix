package mchorse.blockbuster.network.common;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * C→S: "Ich moechte mich auf diesen Modellblock setzen."
 *
 * <p>Es braucht diesen Umweg, weil der Server nicht sehen kann, ob Strg gedrueckt war.
 * Strg + Rechtsklick oeffnet das Kontrollfeld, ein normaler Rechtsklick setzt hin - die
 * Unterscheidung faellt also zwingend auf dem Client. Ein clientseitig abgebrochenes
 * Interaktions-Event hilft nicht: Forge schickt das Paket an den Server trotzdem
 * ("Give the server a chance to fire event as well"), der Server wuerde also bei
 * Strg + Rechtsklick zusaetzlich hinsetzen.</p>
 */
public class PacketSitOnModelBlock implements IMessage
{
    public BlockPos pos = BlockPos.ORIGIN;

    public PacketSitOnModelBlock()
    {}

    public PacketSitOnModelBlock(BlockPos pos)
    {
        this.pos = pos;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.pos.getX());
        buf.writeInt(this.pos.getY());
        buf.writeInt(this.pos.getZ());
    }
}
