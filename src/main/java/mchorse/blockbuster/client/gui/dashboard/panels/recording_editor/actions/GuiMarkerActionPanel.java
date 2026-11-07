package mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.actions;

import mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.GuiRecordingEditorPanel;
import mchorse.blockbuster.recording.actions.MarkerAction;
import mchorse.mclib.client.gui.framework.elements.input.GuiTextElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import net.minecraft.client.Minecraft;

/**
 * Marker aus der Marker-Taste: Name ansehen und umbenennen ("Tuer auf", "Wechsel").
 */
public class GuiMarkerActionPanel extends GuiActionPanel<MarkerAction>
{
    public GuiTextElement label;

    public GuiMarkerActionPanel(Minecraft mc, GuiRecordingEditorPanel panel)
    {
        super(mc, panel);

        this.label = new GuiTextElement(mc, 200, (str) -> this.action.label = str);
        this.label.flex().relative(this.area).set(10, 0, 0, 20).y(1, -30).w(1, -20);

        this.add(this.label);
    }

    @Override
    public void setKey(String key)
    {}

    @Override
    public void fill(MarkerAction action)
    {
        super.fill(action);

        this.label.setText(action.label);
    }

    @Override
    public void draw(GuiContext context)
    {
        this.font.drawStringWithShadow("Marker", this.area.x + 10, this.area.y + 10, 0xffffff);
        this.font.drawStringWithShadow("Per Taste waehrend der Aufnahme gesetzt. Tut beim Abspielen nichts.", this.area.x + 10, this.area.y + 30, 0xcccccc);

        super.draw(context);
    }
}
