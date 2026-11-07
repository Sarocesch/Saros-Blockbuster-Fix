package mchorse.blockbuster_pack.client.gui;

import mchorse.blockbuster.client.render.GtwSkins;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Auswahl der GtwNpc-Skins mit Vorschau (Vorderansicht jeder Figur). Ein Klick
 * legt den Skin auf den Morph, das Modell selbst bleibt.
 */
@SideOnly(Side.CLIENT)
public class GuiNpcSkinPicker extends GuiElement
{
    private static final int CELL_W = 56;
    private static final int CELL_H = 86;
    private static final int TOP = 30;

    public Consumer<ResourceLocation> callback;
    /** Zufalls-Pool statt Einzelwahl: ein Klick nimmt einen Skin auf oder heraus. */
    public List<String> pool;
    public GuiButtonElement close;

    private final List<ResourceLocation> skins = new ArrayList<ResourceLocation>();
    private final List<Boolean> small = new ArrayList<Boolean>();
    private ResourceLocation current;
    private int scroll;

    public GuiNpcSkinPicker(Minecraft mc)
    {
        super(mc);

        this.close = new GuiButtonElement(mc, IKey.str("Schliessen"), (b) -> this.removeFromParent());
        this.close.flex().relative(this).set(0, 5, 80, 20).x(1F, -90);
        this.add(this.close);
    }

    public void fill(ResourceLocation current)
    {
        this.skins.clear();
        this.small.clear();

        for (String text : GtwSkins.all())
        {
            ResourceLocation location = new ResourceLocation(text);

            this.skins.add(location);
            this.small.add(GtwSkins.isSmall(location));
        }

        this.current = current;
        this.scroll = 0;
    }

    private int columns()
    {
        return Math.max(1, (this.area.w - 20) / CELL_W);
    }

    private int maxScroll()
    {
        int rows = (this.skins.size() + this.columns() - 1) / this.columns();

        return Math.max(0, rows * CELL_H - (this.area.h - TOP - 10));
    }

    private int indexAt(int mouseX, int mouseY)
    {
        int x = mouseX - this.area.x - 10;
        int y = mouseY - this.area.y - TOP + this.scroll;

        if (x < 0 || y < 0 || mouseY < this.area.y + TOP)
        {
            return -1;
        }

        int column = x / CELL_W;

        if (column >= this.columns())
        {
            return -1;
        }

        int index = (y / CELL_H) * this.columns() + column;

        return index < this.skins.size() ? index : -1;
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

        int index = this.indexAt(context.mouseX, context.mouseY);

        if (index >= 0 && context.mouseButton == 0)
        {
            if (this.pool != null)
            {
                String id = this.skins.get(index).toString();

                if (!this.pool.remove(id))
                {
                    this.pool.add(id);
                }
            }
            else
            {
                this.current = this.skins.get(index);

                if (this.callback != null)
                {
                    this.callback.accept(this.current);
                }
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

        this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - Integer.signum(context.mouseWheel) * CELL_H / 2));

        return true;
    }

    @Override
    public void draw(GuiContext context)
    {
        Gui.drawRect(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xee000000);

        String title = this.skins.isEmpty() ? "Keine GtwNpc-Skins gefunden"
            : this.pool != null ? "Zufalls-Pool: " + this.pool.size() + " gewaehlt - bei jedem Loop-Neustart einer davon"
            : "NPC-Skin waehlen (" + this.skins.size() + ")";

        this.font.drawStringWithShadow(title, this.area.x + 10, this.area.y + 11, 0xffffff);

        int columns = this.columns();
        int hover = this.area.isInside(context) ? this.indexAt(context.mouseX, context.mouseY) : -1;

        for (int i = 0; i < this.skins.size(); i++)
        {
            int x = this.area.x + 10 + (i % columns) * CELL_W;
            int y = this.area.y + TOP + (i / columns) * CELL_H - this.scroll;

            if (y < this.area.y + TOP || y + CELL_H > this.area.ey())
            {
                continue;
            }

            ResourceLocation skin = this.skins.get(i);

            if (this.pool != null && this.pool.contains(skin.toString()))
            {
                Gui.drawRect(x, y, x + CELL_W - 4, y + CELL_H - 4, 0xff22aa44);
            }
            else if (this.pool == null && skin.equals(this.current))
            {
                Gui.drawRect(x, y, x + CELL_W - 4, y + CELL_H - 4, 0xff0088ff);
            }
            else if (i == hover)
            {
                Gui.drawRect(x, y, x + CELL_W - 4, y + CELL_H - 4, 0x44ffffff);
            }

            this.drawFigure(skin, this.small.get(i), x + (CELL_W - 4) / 2, y + 4);

            String name = GtwSkins.shortName(skin.toString());
            int slash = name.lastIndexOf('/');

            name = name.substring(slash + 1).replace(".png", "");

            if (this.font.getStringWidth(name) > CELL_W - 6)
            {
                name = this.font.trimStringToWidth(name, CELL_W - 10) + "..";
            }

            this.font.drawStringWithShadow(name, x + (CELL_W - 4 - this.font.getStringWidth(name)) / 2, y + 71, 0xdddddd);
        }

        super.draw(context);
    }

    /** Vorderansicht, doppelte Groesse: Kopf mit Hut, Koerper, Arme, Beine. */
    private void drawFigure(ResourceLocation skin, boolean slim, int cx, int top)
    {
        int arm = slim ? 3 : 4;

        this.mc.renderEngine.bindTexture(skin);
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();

        part(8, 8, 8, 8, cx - 8, top);
        part(20, 20, 8, 12, cx - 8, top + 16);
        part(44, 20, arm, 12, cx - 8 - arm * 2, top + 16);
        part(44, 20, arm, 12, cx + 8, top + 16);
        part(4, 20, 4, 12, cx - 8, top + 40);
        part(4, 20, 4, 12, cx, top + 40);
        part(40, 8, 8, 8, cx - 8, top);

        GlStateManager.disableBlend();
    }

    private static void part(int u, int v, int w, int h, int x, int y)
    {
        Gui.drawScaledCustomSizeModalRect(x, y, u, v, w, h, w * 2, h * 2, 64, 64);
    }
}
