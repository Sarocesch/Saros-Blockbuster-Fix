package mchorse.blockbuster.client.render;

import mchorse.blockbuster.client.model.ModelCustomRenderer;
import mchorse.blockbuster.common.entity.EntityActor;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * MWRP-Posen auf Custom-Modellen (Fred, Alex): Spieler im Morph und Actors.
 *
 * <p>MWRP setzt Posen nur an einem ModelPlayer (Obfuscate). Darum laeuft MWRPs
 * eigener Posen-Renderer hier auf einem Hilfs-ModelPlayer, und das Ergebnis
 * wandert auf die Biped-Knochen von ModelCustom zurueck. ModelPlayer ist flach,
 * bei Fred haengen Arme und Beine am Koerper - deshalb werden Winkel und
 * Drehpunkte der Koerper-Kinder in den Koerper-Raum umgerechnet. Ohne aktive
 * Pose passiert nichts.</p>
 *
 * <p>Nur per Reflection; beim ersten Fehler schaltet sich das ab.</p>
 */
@SideOnly(Side.CLIENT)
public final class MwrpCustomPose
{
    private static boolean resolved;
    private static boolean broken;
    private static Object renderer;
    private static Method before;
    private static Method after;
    private static Constructor<?> pre;
    private static Constructor<?> post;
    private static Method getPose;
    private static Method getFading;

    private static ModelPlayer scratch;
    private static ModelRenderer[] parts;
    private static ModelRenderer[] wears;
    private static final float[][] DEFAULTS = new float[6][3];
    private static final Map<EntityActor, ActorPuppet> PUPPETS = new WeakHashMap<EntityActor, ActorPuppet>();

    private MwrpCustomPose()
    {}

    /**
     * Knochen-Reihenfolge wie in ModelCustom.applyExternalPoses: Kopf, Koerper,
     * linker Arm, rechter Arm, linkes Bein, rechtes Bein.
     */
    public static void apply(Entity entity, ModelCustomRenderer[] limbs, ModelRenderer[] bones)
    {
        if (broken)
        {
            return;
        }

        EntityPlayer player = playerFor(entity);

        if (player == null || !resolve())
        {
            return;
        }

        try
        {
            UUID uuid = player.getUniqueID();

            if (getPose.invoke(null, uuid) == null && getFading.invoke(null, uuid) == null)
            {
                return;
            }

            float partial = net.minecraft.client.Minecraft.getMinecraft().getRenderPartialTicks();

            before.invoke(renderer, pre.newInstance(player, scratch, partial));
            seed(limbs, bones);
            after.invoke(renderer, post.newInstance(player, scratch, partial));
            readBack(limbs, bones);
            before.invoke(renderer, pre.newInstance(player, scratch, partial));
        }
        catch (Throwable t)
        {
            broken = true;
            System.out.println("[Blockbuster] MWRP-Pose auf Custom-Modell fehlgeschlagen, aus: " + t);
        }
    }

    private static EntityPlayer playerFor(Entity entity)
    {
        if (entity instanceof EntityPlayer)
        {
            return (EntityPlayer) entity;
        }

        if (!(entity instanceof EntityActor))
        {
            return null;
        }

        EntityActor actor = (EntityActor) entity;

        MwrpPoseBridge.sync(actor);

        ActorPuppet puppet = PUPPETS.get(actor);

        if (puppet == null || puppet.world != actor.world)
        {
            puppet = new ActorPuppet(actor, "mwrp-pose", "bb_actor");
            PUPPETS.put(actor, puppet);
        }

        return puppet;
    }

    private static boolean childOfBody(ModelCustomRenderer[] limbs, int i)
    {
        return i != 1 && limbs[1] != null && limbs[i] != null && limbs[1].limb.name.equals(limbs[i].limb.parent);
    }

    /** Hilfsmodell aus den fertig gestellten Fred-Knochen fuellen, alles im flachen Raum. */
    private static void seed(ModelCustomRenderer[] limbs, ModelRenderer[] bones)
    {
        float[] body = matrix(bones[1]);

        for (int i = 0; i < 6; i++)
        {
            float[] pos = DEFAULTS[i].clone();
            float[] rot = matrix(limbs[i] == null ? null : bones[i]);

            if (childOfBody(limbs, i))
            {
                rot = mul(body, rot);
                pos = add(DEFAULTS[1], transform(body, sub(DEFAULTS[i], DEFAULTS[1])));
            }

            set(parts[i], pos, rot);
            set(wears[i], pos, rot);
        }
    }

    /** Ergebnis zurueck: Winkel auf die Knochen, Drehpunkt-Verschiebung direkt aufs Limb. */
    private static void readBack(ModelCustomRenderer[] limbs, ModelRenderer[] bones)
    {
        float[] body = matrix(parts[1]);
        float[] bodyT = transpose(body);
        float[] bodyPos = point(parts[1]);

        for (int i = 0; i < 6; i++)
        {
            if (limbs[i] == null)
            {
                continue;
            }

            float[] rot = matrix(parts[i]);
            float[] delta;

            if (childOfBody(limbs, i))
            {
                rot = mul(bodyT, rot);
                delta = sub(transform(bodyT, sub(point(parts[i]), bodyPos)), sub(DEFAULTS[i], DEFAULTS[1]));
            }
            else
            {
                delta = sub(point(parts[i]), DEFAULTS[i]);
            }

            euler(rot, bones[i]);

            limbs[i].rotationPointX += delta[0];
            limbs[i].rotationPointY += delta[1];
            limbs[i].rotationPointZ += delta[2];
        }
    }

    private static boolean resolve()
    {
        if (resolved)
        {
            return renderer != null;
        }

        resolved = true;

        try
        {
            Class<?> impl = Class.forName("de.sarocesch.minewacheroleplaymod.animation.pose.client.PoseRendererImpl");
            Class<?> preClass = Class.forName("com.mrcrayfish.obfuscate.client.event.ModelPlayerEvent$SetupAngles$Pre");
            Class<?> postClass = Class.forName("com.mrcrayfish.obfuscate.client.event.ModelPlayerEvent$SetupAngles$Post");
            Class<?> state = Class.forName("de.sarocesch.minewacheroleplaymod.animation.pose.PoseClientState");

            before = impl.getMethod("beforeSetup", preClass);
            after = impl.getMethod("onSetupAngles", postClass);
            pre = preClass.getConstructor(EntityPlayer.class, ModelPlayer.class, float.class);
            post = postClass.getConstructor(EntityPlayer.class, ModelPlayer.class, float.class);
            getPose = state.getMethod("get", UUID.class);
            getFading = state.getMethod("getFading", UUID.class);

            scratch = new ModelPlayer(0.0F, false);
            parts = new ModelRenderer[] {scratch.bipedHead, scratch.bipedBody, scratch.bipedLeftArm, scratch.bipedRightArm, scratch.bipedLeftLeg, scratch.bipedRightLeg};
            wears = new ModelRenderer[] {scratch.bipedHeadwear, scratch.bipedBodyWear, scratch.bipedLeftArmwear, scratch.bipedRightArmwear, scratch.bipedLeftLegwear, scratch.bipedRightLegwear};

            for (int i = 0; i < 6; i++)
            {
                DEFAULTS[i] = point(parts[i]);
            }

            renderer = impl.newInstance();
        }
        catch (Throwable t)
        {
            renderer = null;
            System.out.println("[Blockbuster] MWRP-Posen fuer Custom-Modelle nicht verfuegbar: " + t);
        }

        return renderer != null;
    }

    /* Drehungen wie ModelRenderer: erst Z, dann Y, dann X, also M = Rz * Ry * Rx. */

    private static float[] matrix(ModelRenderer r)
    {
        if (r == null)
        {
            return new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1};
        }

        double ca = Math.cos(r.rotateAngleX), sa = Math.sin(r.rotateAngleX);
        double cb = Math.cos(r.rotateAngleY), sb = Math.sin(r.rotateAngleY);
        double cc = Math.cos(r.rotateAngleZ), sc = Math.sin(r.rotateAngleZ);

        return new float[] {
            (float) (cc * cb), (float) (cc * sb * sa - sc * ca), (float) (cc * sb * ca + sc * sa),
            (float) (sc * cb), (float) (sc * sb * sa + cc * ca), (float) (sc * sb * ca - cc * sa),
            (float) (-sb), (float) (cb * sa), (float) (cb * ca)
        };
    }

    private static void euler(float[] m, ModelRenderer out)
    {
        float sb = Math.max(-1F, Math.min(1F, -m[6]));
        double b = Math.asin(sb);

        if (Math.abs(sb) < 0.9999F)
        {
            out.rotateAngleX = (float) Math.atan2(m[7], m[8]);
            out.rotateAngleZ = (float) Math.atan2(m[3], m[0]);
        }
        else
        {
            out.rotateAngleX = (float) Math.atan2(-m[5], m[4]);
            out.rotateAngleZ = 0;
        }

        out.rotateAngleY = (float) b;
    }

    private static void set(ModelRenderer r, float[] pos, float[] rot)
    {
        r.rotationPointX = pos[0];
        r.rotationPointY = pos[1];
        r.rotationPointZ = pos[2];
        euler(rot, r);
    }

    private static float[] point(ModelRenderer r)
    {
        return new float[] {r.rotationPointX, r.rotationPointY, r.rotationPointZ};
    }

    private static float[] mul(float[] a, float[] b)
    {
        float[] r = new float[9];

        for (int i = 0; i < 3; i++)
        {
            for (int j = 0; j < 3; j++)
            {
                r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
            }
        }

        return r;
    }

    private static float[] transpose(float[] m)
    {
        return new float[] {m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]};
    }

    private static float[] transform(float[] m, float[] v)
    {
        return new float[] {
            m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
            m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
            m[6] * v[0] + m[7] * v[1] + m[8] * v[2]
        };
    }

    private static float[] add(float[] a, float[] b)
    {
        return new float[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static float[] sub(float[] a, float[] b)
    {
        return new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }
}
