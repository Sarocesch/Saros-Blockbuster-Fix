package com.creativemd.littletiles;

import com.creativemd.littletiles.common.structure.LittleStructure;
import com.creativemd.littletiles.common.tileentity.TileEntityLittleTiles;

/**
 * Saro-Fix: LittleTiles markiert die Eltern-Struktur einer Tuer (Schalter,
 * Eltern-Tuer) beim Umschreiben der Verbindung nicht als geaendert. Wird nur ein
 * Teil gespeichert (Chunk entladen, Absturz), passen Tuer, Animation und Schalter
 * nicht mehr zusammen. Hier wird der Hauptblock der Struktur zum Speichern
 * vorgemerkt. Nur Server, nie eine Ausnahme nach aussen.
 */
public final class SaroDirty
{
    private SaroDirty()
    {}

    /**
     * EntityAnimation.readEntityFromNBT: nach dem Setzen der Kachel-Bloecke in die
     * Fake-Welt fehlte manchmal die TileEntity -> Client-Absturz (te == null).
     * Hier wird sie notfalls angelegt.
     */
    public static net.minecraft.tileentity.TileEntity getOrCreateTe(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos)
    {
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);

        if (te == null)
        {
            te = new TileEntityLittleTiles();
            world.setTileEntity(pos, te);

            net.minecraft.tileentity.TileEntity placed = world.getTileEntity(pos);

            if (placed != null)
            {
                te = placed;
            }
        }

        return te;
    }

    public static void mark(LittleStructure structure)
    {
        try
        {
            TileEntityLittleTiles te = structure.mainBlock.getTe();

            if (te != null && te.getWorld() != null && !te.getWorld().isRemote)
            {
                te.markDirty();
            }
        }
        catch (Throwable t)
        {}
    }
}
