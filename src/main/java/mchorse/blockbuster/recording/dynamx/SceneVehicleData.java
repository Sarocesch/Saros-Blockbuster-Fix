package mchorse.blockbuster.recording.dynamx;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Welche Kopie eines Szenen-Fahrzeugs die gueltige ist.
 *
 * <p>Eine Szene baut ihr Fahrzeug bei jedem Abspielen neu auf, immer mit derselben
 * UUID. Lag die alte Kopie dabei in einem entladenen Chunk, blieb sie dort liegen und
 * kam spaeter zurueck. Hier steht je UUID der Stempel der neuesten Kopie - alles
 * Aeltere ist ein Geist. Liegt in data/blockbuster_scene_vehicles.dat.</p>
 */
public class SceneVehicleData extends WorldSavedData
{
    public static final String KEY = "blockbuster_scene_vehicles";

    public final Map<UUID, Long> stamps = new HashMap<UUID, Long>();

    public SceneVehicleData(String name)
    {
        super(name);
    }

    public static SceneVehicleData get(World world)
    {
        MapStorage storage = world.getPerWorldStorage();
        SceneVehicleData data = (SceneVehicleData) storage.getOrLoadData(SceneVehicleData.class, KEY);

        if (data == null)
        {
            data = new SceneVehicleData(KEY);
            storage.setData(KEY, data);
        }

        return data;
    }

    public long get(UUID id)
    {
        Long stamp = this.stamps.get(id);

        return stamp == null ? 0L : stamp.longValue();
    }

    public void put(UUID id, long stamp)
    {
        this.stamps.put(id, Long.valueOf(stamp));
        this.markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound tag)
    {
        this.stamps.clear();

        NBTTagList list = tag.getTagList("Vehicles", 10);

        for (int i = 0; i < list.tagCount(); i++)
        {
            NBTTagCompound entry = list.getCompoundTagAt(i);

            this.stamps.put(new UUID(entry.getLong("Most"), entry.getLong("Least")), Long.valueOf(entry.getLong("Stamp")));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag)
    {
        NBTTagList list = new NBTTagList();

        for (Map.Entry<UUID, Long> entry : this.stamps.entrySet())
        {
            NBTTagCompound item = new NBTTagCompound();

            item.setLong("Most", entry.getKey().getMostSignificantBits());
            item.setLong("Least", entry.getKey().getLeastSignificantBits());
            item.setLong("Stamp", entry.getValue().longValue());
            list.appendTag(item);
        }

        tag.setTag("Vehicles", list);

        return tag;
    }
}
