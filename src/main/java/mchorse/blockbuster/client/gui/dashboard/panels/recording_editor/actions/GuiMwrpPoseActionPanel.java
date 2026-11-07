package mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.actions;

import mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.GuiRecordingEditorPanel;
import mchorse.blockbuster.recording.actions.MwrpPoseAction;
import mchorse.mclib.client.gui.framework.elements.input.GuiTextElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import net.minecraft.client.Minecraft;

/**
 * MWRP-Pose aus der Aufnahme: ID ansehen und nachtraeglich aendern (leer = keine Pose).
 */
public class GuiMwrpPoseActionPanel extends GuiActionPanel<MwrpPoseAction>
{
    public GuiTextElement pose;

    public GuiMwrpPoseActionPanel(Minecraft mc, GuiRecordingEditorPanel panel)
    {
        super(mc, panel);

        this.pose = new GuiTextElement(mc, 200, (str) -> this.action.pose = str.trim());
        this.pose.flex().relative(this.area).set(10, 0, 0, 20).y(1, -30).w(1, -20);

        this.add(this.pose);
    }

    @Override
    public void setKey(String key)
    {}

    @Override
    public void fill(MwrpPoseAction action)
    {
        super.fill(action);

        this.pose.setText(action.pose);
    }

    @Override
    public void draw(GuiContext context)
    {
        this.font.drawStringWithShadow("MWRP-Pose", this.area.x + 10, this.area.y + 10, 0xffffff);
        this.font.drawStringWithShadow("Pose-ID wie bei /pose, leer = Pose endet. Sichtbar mit \"Skin des Aufnehmenden\".", this.area.x + 10, this.area.y + 30, 0xcccccc);

        super.draw(context);
    }
}
