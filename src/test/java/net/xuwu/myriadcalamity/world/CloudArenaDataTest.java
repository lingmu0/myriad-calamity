package net.xuwu.myriadcalamity.world;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** SavedData/NBT checks only. No Minecraft server, client, registry bootstrap or world is started. */
public final class CloudArenaDataTest {
    private static int checks;

    private static void check(boolean valid, String description) {
        checks++;
        if (!valid) throw new AssertionError(description);
    }

    private static CloudArenaData roundTrip(CloudArenaData original) {
        return CloudArenaData.load(original.save(new CompoundTag()));
    }

    public static void main(String[] args) {
        UUID owner = new UUID(13, 17), boss = new UUID(19, 23), previousWinner = new UUID(29, 31);
        CloudArenaData original = new CloudArenaData();
        original.built = true;
        original.owner = owner;
        original.boss = boss;
        original.fighting = true;
        original.createdAt = 800_000;
        original.startsAt = 800_080;
        original.returns.put(owner, new CloudArenaData.ReturnPoint("minecraft:the_nether", -145.625, 64.5, 207.125, -137.75F, 42.5F));
        original.rewarded.add(previousWinner);
        original.pendingRewards.add(owner);

        CloudArenaData restored = roundTrip(original);
        check(restored.built, "Platform generation marker survives saving; re-entry must not rebuild blocks");
        check(owner.equals(restored.owner) && boss.equals(restored.boss), "Encounter owner and boss UUID remain paired");
        check(restored.fighting, "Running challenge survives NBT serialization");
        check(restored.createdAt == 800_000 && restored.startsAt == 800_080, "Preparation and expiry clocks survive saving");
        check(restored.returns.get(owner).equals(original.returns.get(owner)), "Return dimension, fractional coordinates, yaw and pitch round-trip exactly");
        check(restored.rewarded.equals(original.rewarded), "Already awarded players remain recorded");
        check(restored.pendingRewards.equals(original.pendingRewards), "Full-inventory reward remains pending after reload");
        check(!restored.rewarded.contains(owner), "Pending reward is not silently marked delivered");

        restored.owner = null;
        restored.boss = null;
        restored.fighting = false;
        CloudArenaData abandoned = roundTrip(restored);
        check(abandoned.owner == null && abandoned.boss == null && !abandoned.fighting, "Cleared encounter does not revive on load");
        check(abandoned.returns.containsKey(owner), "Return ticket survives logout/abandonment independently of encounter");
        check(abandoned.pendingRewards.contains(owner), "Leaving the arena does not erase a pending reward");

        CompoundTag corrupt = original.save(new CompoundTag());
        ListTag points = corrupt.getList("Returns", Tag.TAG_COMPOUND);
        for (int axis = 0; axis < 3; axis++) {
            CompoundTag point = points.getCompound(0).copy();
            point.putUUID("Player", new UUID(41, axis));
            point.putDouble(new String[] {"X", "Y", "Z"}[axis], new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}[axis]);
            points.add(point);
        }
        CloudArenaData recovered = CloudArenaData.load(corrupt);
        check(recovered.returns.size() == 1 && recovered.returns.containsKey(owner), "Non-finite X/Y/Z tickets are rejected without discarding valid players");
        CompoundTag badAngles = original.save(new CompoundTag());
        CompoundTag anglePoint = badAngles.getList("Returns", Tag.TAG_COMPOUND).getCompound(0);
        anglePoint.putFloat("Yaw", Float.NaN);
        anglePoint.putFloat("Pitch", Float.POSITIVE_INFINITY);
        CloudArenaData angleRecovery = CloudArenaData.load(badAngles);
        check(angleRecovery.returns.get(owner).yaw() == 0 && angleRecovery.returns.get(owner).pitch() == 0,
            "Invalid angles are normalized while preserving the safe return position");

        CompoundTag ownerless = original.save(new CompoundTag());
        ownerless.remove("Owner");
        check(!CloudArenaData.load(ownerless).fighting, "A malformed encounter cannot remain fighting without an owner");
        CloudArenaData empty = CloudArenaData.load(new CompoundTag());
        check(!empty.built && empty.owner == null && empty.boss == null && !empty.fighting,
            "An empty or legacy tag has safe encounter defaults");
        check(empty.returns.isEmpty() && empty.rewarded.isEmpty() && empty.pendingRewards.isEmpty(),
            "An empty tag invents no return positions or rewards");
        System.out.println("CloudArenaDataTest: " + checks + " checks passed");
    }
}
