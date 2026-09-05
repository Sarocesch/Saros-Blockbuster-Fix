package mchorse.blockbuster_pack.morphs;

import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Objects;

/**
 * Fahrzeug-Hologramm als Morph.
 *
 * <p>Zeigt ein DynamX-Fahrzeug, ohne dass man es vorher als Struktur einscannen muss.
 * Der Umweg ueber den Struktur-Scan hat das Fahrzeug in Bloecke zerlegt - hier wird
 * das echte Modell gezeichnet, mit Raedern, Glas und Lackierung.</p>
 *
 * <p>Ohne DynamX bleibt der Morph leer statt zu krachen: der gesamte Kontakt zur
 * Fremdmod liegt in {@code HoloRenderer} und wird nur betreten, wenn die Mod da ist.</p>
 */
public class HoloMorph extends AbstractMorph
{
    /** Registrierungsname des Fahrzeug-Items, z. B. {@code dynamxmod:vehicle_bus}. */
    public String vehicle = "";

    /** Textur-Variante des Fahrzeugs. */
    public int meta;

    public float scale = 1F;

    public HoloMorph()
    {
        this.name = "holo";
    }

    public HoloMorph(String vehicle, int meta)
    {
        this();

        this.vehicle = vehicle;
        this.meta = meta;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderOnScreen(EntityPlayer player, int x, int y, float scale, float alpha)
    {
        if (this.vehicle.isEmpty())
        {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        /* Y gespiegelt, weil das Menue in Bildschirmkoordinaten zeichnet */
        GlStateManager.scale(-scale * 0.16F, -scale * 0.16F, scale * 0.16F);
        GlStateManager.rotate(160F, 0F, 1F, 0F);
        GlStateManager.rotate(-20F, 1F, 0F, 0F);
        GlStateManager.enableDepth();

        mchorse.blockbuster_pack.client.HoloRenderer.renderStatic(this.vehicle, this.meta);

        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.popMatrix();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void render(EntityLivingBase target, double x, double y, double z, float entityYaw, float partialTicks)
    {
        if (this.vehicle.isEmpty())
        {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(180F - entityYaw, 0F, 1F, 0F);

        if (this.scale != 1F)
        {
            GlStateManager.scale(this.scale, this.scale, this.scale);
        }

        mchorse.blockbuster_pack.client.HoloRenderer.render(this.vehicle, this.meta, partialTicks);

        GlStateManager.popMatrix();
    }

    @Override
    public AbstractMorph create()
    {
        return new HoloMorph();
    }

    @Override
    public void copy(AbstractMorph from)
    {
        super.copy(from);

        if (from instanceof HoloMorph)
        {
            final HoloMorph morph = (HoloMorph) from;

            this.vehicle = morph.vehicle;
            this.meta = morph.meta;
            this.scale = morph.scale;
        }
    }

    @Override
    public boolean equals(Object object)
    {
        boolean result = super.equals(object);

        if (object instanceof HoloMorph)
        {
            final HoloMorph morph = (HoloMorph) object;

            result = result && Objects.equals(this.vehicle, morph.vehicle);
            result = result && this.meta == morph.meta;
            result = result && this.scale == morph.scale;
        }

        return result;
    }

    @Override
    public float getWidth(EntityLivingBase target)
    {
        return 0;
    }

    @Override
    public float getHeight(EntityLivingBase target)
    {
        return 0;
    }

    @Override
    public void fromNBT(NBTTagCompound tag)
    {
        super.fromNBT(tag);

        if (tag.hasKey("Vehicle"))
        {
            this.vehicle = tag.getString("Vehicle");
        }

        if (tag.hasKey("Meta"))
        {
            this.meta = tag.getInteger("Meta");
        }

        if (tag.hasKey("Scale"))
        {
            this.scale = tag.getFloat("Scale");
        }
    }

    @Override
    public void toNBT(NBTTagCompound tag)
    {
        super.toNBT(tag);

        if (!this.vehicle.isEmpty())
        {
            tag.setString("Vehicle", this.vehicle);
        }

        if (this.meta != 0)
        {
            tag.setInteger("Meta", this.meta);
        }

        if (this.scale != 1F)
        {
            tag.setFloat("Scale", this.scale);
        }
    }
}
