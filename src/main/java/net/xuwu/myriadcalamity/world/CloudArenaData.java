package net.xuwu.myriadcalamity.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Stored in the overworld so return tickets survive death, logout and dimension unloading. */
public final class CloudArenaData extends SavedData {
    boolean built;
    UUID owner;
    UUID boss;
    boolean fighting;
    long startsAt;
    long createdAt;
    final Map<UUID, ReturnPoint> returns = new HashMap<>();
    final Set<UUID> rewarded = new HashSet<>();
    final Set<UUID> pendingRewards = new HashSet<>();

    public record ReturnPoint(String dimension, double x, double y, double z, float yaw, float pitch) {}

    public static CloudArenaData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(CloudArenaData::new, CloudArenaData::load), "myriad_calamity_cloud_arena");
    }

    public static CloudArenaData load(CompoundTag tag, HolderLookup.Provider registries) {
        CloudArenaData data = new CloudArenaData();
        data.built = tag.getBoolean("Built");
        data.owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        data.boss = tag.hasUUID("Boss") ? tag.getUUID("Boss") : null;
        data.fighting = tag.getBoolean("Fighting") && data.owner != null;
        data.startsAt = tag.getLong("StartsAt");
        data.createdAt = tag.getLong("CreatedAt");
        for (Tag entry : tag.getList("Returns", Tag.TAG_COMPOUND)) {
            CompoundTag point = (CompoundTag) entry;
            if (!point.hasUUID("Player")) continue;
            double x = point.getDouble("X"), y = point.getDouble("Y"), z = point.getDouble("Z");
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) continue;
            float yaw = point.getFloat("Yaw"), pitch = point.getFloat("Pitch");
            data.returns.put(point.getUUID("Player"), new ReturnPoint(point.getString("Dimension"),
                x, y, z, Float.isFinite(yaw) ? yaw : 0, Float.isFinite(pitch) ? pitch : 0));
        }
        readIds(tag, "Rewarded", data.rewarded);
        readIds(tag, "PendingRewards", data.pendingRewards);
        return data;
    }

    private static void readIds(CompoundTag tag, String name, Set<UUID> target) {
        for (Tag entry : tag.getList(name, Tag.TAG_COMPOUND)) {
            CompoundTag player = (CompoundTag) entry;
            if (player.hasUUID("Player")) target.add(player.getUUID("Player"));
        }
    }

    private static ListTag writeIds(Set<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Player", id);
            list.add(tag);
        }
        return list;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("Built", built);
        if (owner != null) tag.putUUID("Owner", owner);
        if (boss != null) tag.putUUID("Boss", boss);
        tag.putBoolean("Fighting", fighting);
        tag.putLong("StartsAt", startsAt);
        tag.putLong("CreatedAt", createdAt);
        ListTag list = new ListTag();
        returns.forEach((id, point) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            entry.putString("Dimension", point.dimension());
            entry.putDouble("X", point.x());
            entry.putDouble("Y", point.y());
            entry.putDouble("Z", point.z());
            entry.putFloat("Yaw", point.yaw());
            entry.putFloat("Pitch", point.pitch());
            list.add(entry);
        });
        tag.put("Returns", list);
        tag.put("Rewarded", writeIds(rewarded));
        tag.put("PendingRewards", writeIds(pendingRewards));
        return tag;
    }
}
