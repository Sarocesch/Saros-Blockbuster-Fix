package mchorse.blockbuster.network.common;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * Client -> Server: der Spieler hat eine laufende LittleTiles-Tuer-Animation
 * angeklickt (z. B. eine offene Tuer zum Schliessen). Dabei kommt auf dem
 * Server weder ein RightClickBlock noch eine neue Animation an - nur der Client
 * sieht den Klick. Waehrend einer Aufnahme wird daraus ein Tuer-Klick.
 */
public class PacketLittleTilesClick implements IMessage
{
    public int x;
    public int y;
    public int z;

    public PacketLittleTilesClick()
    {}

    public PacketLittleTilesClick(int x, int y, int z)
    {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
    }
}
