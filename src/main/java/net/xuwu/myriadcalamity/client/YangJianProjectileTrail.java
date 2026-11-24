package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Short, fading wakes built from observed flight, including orbit and homing turns. */
final class YangJianProjectileTrail {
    private static final int TRAIL_TICKS = 6;
    private record Sample(int tick, Vec3 position) {}
    private record History(UUID id, Deque<Sample> samples) {}
    private final WeakHashMap<Entity, History> histories = new WeakHashMap<>();

    void render(Entity blade, float partial, double reach, double width, YangJianVisuals.Palette palette,
                VertexConsumer v, Matrix4f pose) {
        History history = histories.get(blade);
        if (history == null || !history.id().equals(blade.getUUID())) {
            history = new History(blade.getUUID(), new ArrayDeque<>(TRAIL_TICKS));
            histories.put(blade, history);
        }
        Deque<Sample> samples = history.samples();
        int tick = blade.tickCount;
        Vec3 old = new Vec3(blade.xOld, blade.yOld, blade.zOld), current = old.lerp(blade.position(), partial);
        Sample latest = samples.peekLast();
        if (latest != null && (tick - latest.tick() > 2 || tick < latest.tick()
                || old.distanceToSqr(latest.position()) > 25)) {
            samples.clear();
            latest = null;
        }
        if (old.distanceToSqr(blade.position()) > 25) {
            // Do not leave a streak reaching back to a spawn or teleport position.
            samples.clear();
            return;
        }
        if (latest == null || latest.tick() < tick) samples.addLast(new Sample(tick, old));
        while (!samples.isEmpty() && tick - samples.getFirst().tick() >= TRAIL_TICKS) samples.removeFirst();

        Vec3 head = current;
        double traveled = 0, brightness = 1;
        for (var iterator = samples.descendingIterator(); iterator.hasNext() && traveled < reach;) {
            Sample sample = iterator.next();
            Vec3 past = sample.position();
            double distance = head.distanceTo(past);
            if (distance < .002) continue;
            if (distance > reach - traveled) {
                past = head.lerp(past, (reach - traveled) / distance);
                distance = reach - traveled;
            }
            traveled += distance;
            double fade = Math.max(0, Math.min(1 - traveled / reach,
                1 - (tick - sample.tick() + partial) / TRAIL_TICKS));
            YangJianVisuals.wake(v, pose, past.subtract(current), head.subtract(current),
                width * fade, width * brightness, palette, fade, brightness);
            head = past;
            brightness = fade;
        }
    }
}
