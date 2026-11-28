package net.xuwu.myriadcalamity.world;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.minecraft.world.level.saveddata.SavedDataType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Saved state for the Divine Dragon's dedicated Fountainhead Cloud Sea dimension. */
public final class DivineArenaData extends SavedData {
    private static final SavedDataType<DivineArenaData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(MyriadCalamity.ID, "divine_arena"),
        level -> LegacySavedData.load(level, "myriad_calamity_divine_arena", DivineArenaData::load, DivineArenaData::new),
        level -> CompoundTag.CODEC.xmap(DivineArenaData::load, DivineArenaData::save));
    boolean built;
    int treeVersion;
    UUID owner;
    UUID boss;
    boolean fighting;
    long startsAt;
    long createdAt;
    final Map<UUID, ReturnPoint> returns = new HashMap<>();
    final Set<UUID> rewarded = new HashSet<>();
    final Set<UUID> pendingRewards = new HashSet<>();

    public record ReturnPoint(String dimension, double x, double y, double z, float yaw, float pitch) {}

    public static DivineArenaData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public static DivineArenaData load(CompoundTag tag) {
        DivineArenaData data = new DivineArenaData();
        data.built = tag.getBooleanOr("Built", false);
        data.treeVersion = tag.getIntOr("TreeVersion", 0);
        data.owner = tag.read("Owner", UUIDUtil.CODEC).orElse(null);
        data.boss = tag.read("Boss", UUIDUtil.CODEC).orElse(null);
        data.fighting = tag.getBooleanOr("Fighting", false) && data.owner != null;
        data.startsAt = tag.getLongOr("StartsAt", 0L);
        data.createdAt = tag.getLongOr("CreatedAt", 0L);
        for (Tag entry : tag.getListOrEmpty("Returns")) {
            if (!(entry instanceof CompoundTag point)) continue;
            UUID playerId = point.read("Player", UUIDUtil.CODEC).orElse(null);
            if (playerId == null) continue;
            double x = point.getDoubleOr("X", 0.0), y = point.getDoubleOr("Y", 0.0), z = point.getDoubleOr("Z", 0.0);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) continue;
            float yaw = point.getFloatOr("Yaw", 0F), pitch = point.getFloatOr("Pitch", 0F);
            data.returns.put(playerId, new ReturnPoint(
                point.getStringOr("Dimension", ""), x, y, z,
                Float.isFinite(yaw) ? yaw : 0,
                Float.isFinite(pitch) ? pitch : 0));
        }
        readIds(tag, "Rewarded", data.rewarded);
        readIds(tag, "PendingRewards", data.pendingRewards);
        return data;
    }

    private static void readIds(CompoundTag tag, String name, Set<UUID> target) {
        for (Tag entry : tag.getListOrEmpty(name)) {
            if (entry instanceof CompoundTag player) player.read("Player", UUIDUtil.CODEC).ifPresent(target::add);
        }
    }

    private static ListTag writeIds(Set<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) {
            CompoundTag tag = new CompoundTag();
            tag.store("Player", UUIDUtil.CODEC, id);
            list.add(tag);
        }
        return list;
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putBoolean("Built", built);
        tag.putInt("TreeVersion", treeVersion);
        if (owner != null) tag.store("Owner", UUIDUtil.CODEC, owner);
        if (boss != null) tag.store("Boss", UUIDUtil.CODEC, boss);
        tag.putBoolean("Fighting", fighting);
        tag.putLong("StartsAt", startsAt);
        tag.putLong("CreatedAt", createdAt);
        ListTag list = new ListTag();
        returns.forEach((id, point) -> {
            CompoundTag entry = new CompoundTag();
            entry.store("Player", UUIDUtil.CODEC, id);
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
