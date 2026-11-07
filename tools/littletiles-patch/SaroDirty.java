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
