package mchorse.blockbuster_pack;

import mchorse.blockbuster.api.ModelHandler;
import mchorse.blockbuster_pack.client.gui.*;
import mchorse.blockbuster_pack.morphs.*;
import mchorse.metamorph.api.IMorphFactory;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;

/**
 * Blockbuster morph factory
 *
 * This factory is responsible for adding all custom modeled morphs provided by
 * a user (in his config folder), the server (in world save's blockbuster
 * folder) or added by API (steve, alex and fred).
 */
public class BlockbusterFactory implements IMorphFactory
{
    public ModelHandler models;
    public BlockbusterSection section;

    @Override
    public void register(MorphManager manager)
    {
        manager.list.register(this.section = new BlockbusterSection("blockbuster"));
    }

    @Override
    public void registerMorphEditors(Minecraft mc, List<GuiAbstractMorph> editors)
    {
        editors.add(new GuiCustomMorph(mc));
        editors.add(new GuiImageMorph(mc));
        editors.add(new GuiSequencerMorph(mc));
        editors.add(new GuiRecordMorph(mc));
        editors.add(new GuiStructureMorph(mc));
        editors.add(new GuiParticleMorph(mc));
        editors.add(new GuiSnowstormMorph(mc));
        editors.add(new GuiTrackerMorph(mc));
        editors.add(new GuiLightMorph(mc));
        editors.add(new GuiBetterLightsMorph(mc));
    }

    @Override
    public AbstractMorph getMorphFromNBT(NBTTagCompound tag)
    {
        String name = tag.getString("Name");
        AbstractMorph morph;
        name = name.substring(name.indexOf(".") + 1);

        /* Utility */
        if (name.equals("image"))
        {
            morph = new ImageMorph();
        }
        else if (name.equals("sequencer"))
        {
            morph = new SequencerMorph();
        }
        else if (name.equals("record"))
        {
            morph = new RecordMorph();
        }
        else if (name.equals("structure"))
        {
            morph = new StructureMorph();
        }
        else if (name.equals("particle"))
        {
            morph = new ParticleMorph();
        }
        else if (name.equals("snowstorm"))
        {
            morph = new SnowstormMorph();
        }
        else if (name.equals("tracker"))
        {
            morph = new TrackerMorph();
        }
        else if (name.equals("light"))
        {
            morph = new LightMorph();
        }
        else if (name.equals("betterLights"))
        {
            morph = new BetterLightsMorph();
        }
        else if (name.equals("holo"))
        {
            morph = new HoloMorph();
        }
        else
        {
            /* Custom model morphs */
            CustomMorph custom = new CustomMorph();

            custom.model = this.models.models.get(name);
            morph = custom;
        }

        morph.fromNBT(tag);

        return morph;
    }

    @Override
    public boolean hasMorph(String morph)
    {
        return morph.startsWith("blockbuster.") || morph.equals("sequencer") || morph.equals("structure") || morph.equals("particle") || morph.equals("snowstorm") || morph.equals("tracker") || morph.equals("light") || morph.equals("betterLights") || morph.equals("holo");
    }
}