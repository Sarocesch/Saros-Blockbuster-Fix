package mchorse.blockbuster.recording.actions;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;

/**
 * Merkzeichen, das der Aufnehmende per Taste setzt. Tut beim Abspielen nichts,
 * steht nur in der Zeitleiste des Aktions-Editors, damit man weiss, was wo passiert.
 */
public class MarkerAction extends Action
{
    public String label = "";

    public MarkerAction()
    {}

    public MarkerAction(String label)
    {
        this.label = label == null ? "" : label;
    }

    @Override
    public void fromBuf(ByteBuf buf)
    {
        super.fromBuf(buf);
        this.label = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBuf(ByteBuf buf)
    {
        super.toBuf(buf);
        ByteBufUtils.writeUTF8String(buf, this.label);
    }

    @Override
    public void fromNBT(NBTTagCompound tag)
    {
        this.label = tag.getString("Label");
    }

    @Override
    public void toNBT(NBTTagCompound tag)
    {
        tag.setString("Label", this.label);
    }

    @Override
    public boolean isSafe()
    {
        return true;
    }
}
