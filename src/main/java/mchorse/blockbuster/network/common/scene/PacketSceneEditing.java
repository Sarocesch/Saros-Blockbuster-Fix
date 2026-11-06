package mchorse.blockbuster.network.common.scene;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * Client an Server: "ich habe diese Szene offen und diesen Actor ausgewaehlt".
 * Leere Szene = Menue verlassen.
 */
public class PacketSceneEditing implements IMessage
{
    public String scene = "";
    public String replay = "";

    public PacketSceneEditing()
    {}

    public PacketSceneEditing(String scene, String replay)
    {
        this.scene = scene == null ? "" : scene;
        this.replay = replay == null ? "" : replay;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.scene = ByteBufUtils.readUTF8String(buf);
        this.replay = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        ByteBufUtils.writeUTF8String(buf, this.scene);
        ByteBufUtils.writeUTF8String(buf, this.replay);
    }
}
