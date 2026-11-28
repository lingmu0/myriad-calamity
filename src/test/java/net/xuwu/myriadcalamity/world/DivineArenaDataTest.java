package net.xuwu.myriadcalamity.world;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** SavedData/NBT checks only for the Divine Realm arena. No Minecraft runtime is started. */
public final class DivineArenaDataTest {
    private static int checks;

    private static void check(boolean valid, String description) {
        checks++;
        if (!valid) throw new AssertionError(description);
    }

    private static DivineArenaData roundTrip(DivineArenaData original) {
        return DivineArenaData.load(original.save(new CompoundTag(), null), null);
    }

    public static void main(String[] args) {
        UUID owner = new UUID(51, 53), boss = new UUID(59, 61), previousWinner = new UUID(67, 71);
        DivineArenaData original = new DivineArenaData();
        original.built = true;
        original.treeVersion = 2;
        original.owner = owner;
        original.boss = boss;
        original.fighting = true;
        original.createdAt = 420_000;
        original.startsAt = 420_080;
        original.returns.put(owner, new DivineArenaData.ReturnPoint("minecraft:overworld", 256.25, 72.0, -512.75, 90.0F, -12.5F));
        original.rewarded.add(previousWinner);
        original.pendingRewards.add(owner);

        DivineArenaData restored = roundTrip(original);
        check(restored.built && restored.treeVersion == 2, "Giant sakura tree version & generation marker survive saving");
        check(owner.equals(restored.owner) && boss.equals(restored.boss), "Encounter owner and Divine Dragon UUID remain paired");
        check(restored.fighting, "Active Divine Dragon challenge survives NBT serialization");
        check(restored.createdAt == 420_000 && restored.startsAt == 420_080, "Preparation and expiry clocks survive saving");
        check(restored.returns.get(owner).equals(original.returns.get(owner)), "Return coordinates and facing round-trip cleanly");
        check(restored.rewarded.equals(original.rewarded), "Previous Divine Dragon's Tears winners remain recorded");
        check(restored.pendingRewards.equals(original.pendingRewards), "Pending Divine Dragon's Tears reward survives reload");

        CompoundTag corrupt = original.save(new CompoundTag(), null);
        ListTag points = corrupt.getList("Returns", Tag.TAG_COMPOUND);
        for (int axis = 0; axis < 3; axis++) {
            CompoundTag point = points.getCompound(0).copy();
            point.putUUID("Player", new UUID(73, axis));
            point.putDouble(new String[] {"X", "Y", "Z"}[axis], new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}[axis]);
            points.add(point);
        }
        DivineArenaData recovered = DivineArenaData.load(corrupt, null);
        check(recovered.returns.size() == 1 && recovered.returns.containsKey(owner),
            "Non-finite return coordinates are rejected without discarding valid players");

        CompoundTag badAngles = original.save(new CompoundTag(), null);
        CompoundTag anglePoint = badAngles.getList("Returns", Tag.TAG_COMPOUND).getCompound(0);
        anglePoint.putFloat("Yaw", Float.NaN);
        anglePoint.putFloat("Pitch", Float.NEGATIVE_INFINITY);
        DivineArenaData angleRecovery = DivineArenaData.load(badAngles, null);
        check(angleRecovery.returns.get(owner).yaw() == 0 && angleRecovery.returns.get(owner).pitch() == 0,
            "Invalid return angles reset cleanly while preserving coordinates");

        CompoundTag ownerless = original.save(new CompoundTag(), null);
        ownerless.remove("Owner");
        check(!DivineArenaData.load(ownerless, null).fighting, "Ownerless encounter resets fighting flag safely");

        DivineArenaData empty = DivineArenaData.load(new CompoundTag(), null);
        check(!empty.built && empty.treeVersion == 0 && empty.owner == null && empty.boss == null && !empty.fighting,
            "Empty tag uses clean defaults");
        System.out.println("DivineArenaDataTest: " + checks + " checks passed");
    }
}
