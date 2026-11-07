package mchorse.blockbuster.client.gui.dashboard.panels.scene;

import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.PacketLoopSceneAction;
import mchorse.blockbuster.network.common.PacketLoopSceneList;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * Uebersicht aller Szenen mit Loops: laeuft / wartet auf Spieler / aus, wie
 * viele Actors leben, An/Aus und Teleport. Nur fuer OPs, der Server prueft.
 */
@SideOnly(Side.CLIENT)
public class GuiLoopOverview extends GuiElement
{
    private static final int ROW = 22;
    private static final int TOP = 32;
    private static final int BUTTON_W = 40;

    private static GuiLoopOverview current;

    private final List<PacketLoopSceneList.Entry> entries = new ArrayList<PacketLoopSceneList.Entry>();
    private boolean loading;
    private int scroll;

    public GuiLoopOverview(Minecraft mc)
    {
        this(mc, true);
    }

    /** closable = false: fest als eigener Reiter, ohne Schliessen-Knopf. */
    public GuiLoopOverview(Minecraft mc, boolean closable)
    {
        super(mc);

        GuiButtonElement refresh = new GuiButtonElement(mc, IKey.str("Aktualisieren"), (b) -> this.request(PacketLoopSceneAction.LIST, ""));

        refresh.flex().relative(this).set(0, 6, 80, 20).x(1F, closable ? -175 : -90);
        this.add(refresh);

        if (closable)
        {
            GuiButtonElement close = new GuiButtonElement(mc, IKey.str("Schliessen"), (b) -> this.removeFromParent());

            close.flex().relative(this).set(0, 6, 80, 20).x(1F, -90);
            this.add(close);
        }
    }

    public static void receive(List<PacketLoopSceneList.Entry> entries)
    {
        if (current != null)
        {
            current.entries.clear();
            current.entries.addAll(entries);
            current.loading = false;
            current.scroll = Math.min(current.scroll, current.maxScroll());
        }
    }

    public void open()
    {
        current = this;
        this.scroll = 0;
        this.request(PacketLoopSceneAction.LIST, "");
    }

    @Override
    public void removeFromParent()
    {
        super.removeFromParent();

        if (current == this)
        {
            current = null;
        }
    }

    private void request(byte action, String id)
    {
        this.loading = true;
        Dispatcher.sendToServer(new PacketLoopSceneAction(action, id));
    }

    private int maxScroll()
    {
        return Math.max(0, this.entries.size() * ROW - (this.area.h - TOP - 26));
    }

    /* Knopf-Spalten von rechts: TP, An/Aus, Neu */
    private int buttonX(int column)
    {
        return this.area.ex() - 10 - (column + 1) * (BUTTON_W + 5);
    }

    @Override
    public boolean mouseClicked(GuiContext context)
    {
        if (super.mouseClicked(context))
        {
            return true;
        }

        if (!this.area.isInside(context))
        {
            return false;
        }

        int row = (context.mouseY - this.area.y - TOP + this.scroll) / ROW;

        if (context.mouseY >= this.area.y + TOP && row >= 0 && row < this.entries.size() && context.mouseButton == 0)
        {
            PacketLoopSceneList.Entry entry = this.entries.get(row);
            int x = context.mouseX;

            if (x >= this.buttonX(0) && x < this.buttonX(0) + BUTTON_W && entry.hasPos)
            {
                this.request(PacketLoopSceneAction.TELEPORT, entry.id);
            }
            else if (x >= this.buttonX(1) && x < this.buttonX(1) + BUTTON_W)
            {
                this.request(entry.active ? PacketLoopSceneAction.OFF : PacketLoopSceneAction.ON, entry.id);
            }
            else if (x >= this.buttonX(5) && x < this.buttonX(5) + BUTTON_W)
            {
                this.request(PacketLoopSceneAction.TOGETHER, entry.id);
            }
            else if (x >= this.buttonX(4) && x < this.buttonX(4) + BUTTON_W)
            {
                this.request(PacketLoopSceneAction.PAUSE, entry.id);
            }
            else if (x >= this.buttonX(3) && x < this.buttonX(3) + BUTTON_W)
            {
                this.request(PacketLoopSceneAction.SCHEDULE, entry.id);
            }
            else if (x >= this.buttonX(2) && x < this.buttonX(2) + BUTTON_W && entry.active)
            {
                this.request(PacketLoopSceneAction.RESTART, entry.id);
            }
        }

        return true;
    }

    @Override
    public boolean mouseScrolled(GuiContext context)
    {
        if (!this.area.isInside(context))
        {
            return super.mouseScrolled(context);
        }

        this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - Integer.signum(context.mouseWheel) * ROW));

        return true;
    }

    @Override
    public void draw(GuiContext context)
    {
        Gui.drawRect(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xee000000);

        int running = 0;
        int waiting = 0;

        for (PacketLoopSceneList.Entry entry : this.entries)
        {
            if (entry.playing) running++;
            else if (entry.active) waiting++;
        }

        this.font.drawStringWithShadow("Loop-Szenen (" + this.entries.size() + ")  laeuft: " + running + "  wartet: " + waiting
            + (this.loading ? "  ..." : ""), this.area.x + 10, this.area.y + 12, 0xffffff);

        int y0 = this.area.y + TOP;

        for (int i = 0; i < this.entries.size(); i++)
        {
            int y = y0 + i * ROW - this.scroll;

            if (y < y0 || y + ROW > this.area.ey() - 24)
            {
                continue;
            }

            PacketLoopSceneList.Entry entry = this.entries.get(i);

            if (i % 2 == 0)
            {
                Gui.drawRect(this.area.x + 5, y, this.area.ex() - 5, y + ROW - 2, 0x22ffffff);
            }

            String name = entry.title == null || entry.title.isEmpty() ? entry.id : entry.title + " (" + entry.id + ")";
            String status;
            int color;

            if (entry.broken)
            {
                status = "Aufnahme fehlt - startet nicht";
                color = 0xff5555;
            }
            else if (entry.playing)
            {
                boolean broken = entry.alive < entry.total;

                status = "laeuft, Actors " + entry.alive + "/" + entry.total;
                color = broken ? 0xffaa00 : 0x55ff55;
            }
            else if (entry.active && !entry.timeOk)
            {
                status = entry.schedule == 2 ? "wartet auf die Nacht" : "wartet auf den Tag";
                color = 0x8888ff;
            }
            else if (entry.active)
            {
                status = "wartet auf Spieler in der Naehe";
                color = 0xffff55;
            }
            else
            {
                status = "aus";
                color = 0x888888;
            }

            int textW = this.buttonX(5) - this.area.x - 20;

            this.font.drawStringWithShadow(this.font.trimStringToWidth(name, textW), this.area.x + 10, y + 2, 0xffffff);
            this.font.drawStringWithShadow(this.font.trimStringToWidth(status
                + (entry.hasPos ? "  @ " + (int) entry.x + " " + (int) entry.y + " " + (int) entry.z : ""), textW), this.area.x + 10, y + 11, color);

            this.button(this.buttonX(1), y, entry.active ? "Aus" : "An", entry.active ? 0xffcc3333 : 0xff33aa33, context);

            if (entry.active)
            {
                this.button(this.buttonX(2), y, "Neu", 0xff886622, context);
            }

            {
                String[] times = {"Immer", "Tag", "Nacht"};
                int[] colors = {0xff555555, 0xffaa8822, 0xff333388};
                int schedule = Math.max(0, Math.min(2, entry.schedule));

                this.button(this.buttonX(3), y, times[schedule], colors[schedule], context);
                this.button(this.buttonX(4), y, entry.pause <= 0 ? "0s" : entry.pause + "s", entry.pause <= 0 ? 0xff555555 : 0xff227777, context);
                this.button(this.buttonX(5), y, entry.together ? "Gem." : "Einz.", entry.together ? 0xff7744aa : 0xff555555, context);
            }

            if (entry.hasPos)
            {
                this.button(this.buttonX(0), y, "TP", 0xff3366cc, context);
            }
        }

        if (this.entries.isEmpty() && !this.loading)
        {
            this.font.drawStringWithShadow("Keine Szene mit Loops gefunden.", this.area.x + 10, y0 + 4, 0xaaaaaa);
        }

        this.font.drawStringWithShadow("An/Aus = dauerhaft an/beenden. Neu = Actors frisch. Immer/Tag/Nacht = Tageszeit. 0s-120s = Pause je Runde. Einz./Gem. = einzeln oder alle zugleich neu.",
            this.area.x + 10, this.area.ey() - 16, 0x888888);

        super.draw(context);
    }

    private void button(int x, int y, String label, int color, GuiContext context)
    {
        boolean hover = context.mouseX >= x && context.mouseX < x + BUTTON_W && context.mouseY >= y + 1 && context.mouseY < y + ROW - 3;

        Gui.drawRect(x, y + 1, x + BUTTON_W, y + ROW - 3, hover ? color | 0x00222222 : color);
        this.font.drawStringWithShadow(label, x + (BUTTON_W - this.font.getStringWidth(label)) / 2, y + 6, 0xffffff);
    }
}
