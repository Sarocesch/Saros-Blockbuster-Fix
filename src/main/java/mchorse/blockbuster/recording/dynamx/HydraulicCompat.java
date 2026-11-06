package mchorse.blockbuster.recording.dynamx;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;

import java.lang.reflect.Method;

/**
 * Reflection-Anbindung an Saros Hydraulic Addon (Flugzeug-Fahrwerk, Klappen,
 * Ruder, Schub, Hydraulic-Teile, abgerissene Triebwerke). Fehlt das Addon, tut
 * jede Methode nichts.
 */
public final class HydraulicCompat
{
    private static boolean resolved = false;
    private static boolean available = false;

    private static Method methodCapture;
    private static Method methodApply;
    private static Method methodRelease;
    private static Method methodClearWrecks;

    private HydraulicCompat()
    {}

    private static boolean init()
    {
        if (resolved) return available;
        resolved = true;

        try
        {
            Class<?> api = Class.forName("de.sarocesch.saroshydraulic.compat.RecordingApi");

            methodCapture = api.getMethod("capture", Entity.class);
            methodApply = api.getMethod("apply", Entity.class, NBTTagCompound.class);
            methodRelease = api.getMethod("release", Entity.class);
            methodClearWrecks = api.getMethod("clearWrecks", Entity.class);
            available = true;
        }
        catch (Throwable t)
        {
            available = false;
        }

        return available;
    }

    /**
     * Zustand des Fahrzeugs, oder null wenn es nichts davon hat.
     */
    public static NBTTagCompound capture(Entity vehicle)
    {
        if (vehicle == null || !init()) return null;

        try
        {
            return (NBTTagCompound) methodCapture.invoke(null, vehicle);
        }
        catch (Throwable ignored) {}

        return null;
    }

    public static void apply(Entity vehicle, NBTTagCompound state)
    {
        if (vehicle == null || state == null || !init()) return;

        try
        {
            methodApply.invoke(null, vehicle, state);
        }
        catch (Throwable ignored) {}
    }

    /**
     * Die Wiedergabe steuert das Fahrzeug nicht mehr: Knueppel und Schub auf 0.
     */
    public static void release(Entity vehicle)
    {
        if (vehicle == null || !init()) return;

        try
        {
            methodRelease.invoke(null, vehicle);
        }
        catch (Throwable ignored) {}
    }

    /**
     * Abgerissene Teile dieses Fahrzeugs (liegende Wracks) entfernen.
     */
    public static void clearWrecks(Entity vehicle)
    {
        if (vehicle == null || !init()) return;

        try
        {
            methodClearWrecks.invoke(null, vehicle);
        }
        catch (Throwable ignored) {}
    }
}
