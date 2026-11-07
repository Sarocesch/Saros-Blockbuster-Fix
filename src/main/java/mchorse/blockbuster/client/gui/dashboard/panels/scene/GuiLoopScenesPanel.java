package mchorse.blockbuster.client.gui.dashboard.panels.scene;

import mchorse.blockbuster.client.gui.dashboard.GuiBlockbusterPanel;
import mchorse.mclib.client.gui.mclib.GuiDashboard;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Reiter "Loop-Szenen" in der linken Leiste: dieselbe Uebersicht wie im
 * Szenen-Reiter, fest eingebaut und beim Oeffnen frisch vom Server.
 */
@SideOnly(Side.CLIENT)
public class GuiLoopScenesPanel extends GuiBlockbusterPanel
{
    private final GuiLoopOverview overview;

    public GuiLoopScenesPanel(Minecraft mc, GuiDashboard dashboard)
    {
        super(mc, dashboard);

        this.overview = new GuiLoopOverview(mc, false);
        this.overview.flex().relative(this).wh(1F, 1F);
        this.add(this.overview);
    }

    @Override
    public void appear()
    {
        super.appear();
        this.overview.open();
    }
}
