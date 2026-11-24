package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.entity.YangJian;
import net.xuwu.myriadcalamity.world.CloudArena;
import net.xuwu.myriadcalamity.world.CloudWeather;
import org.joml.Matrix4f;

/** Local weather for the Cloud Realm, driven by the tracked arena boss's phase. */
final class YangJianStorm {
    private static final int CLOUD_CELLS = 16;
    private static final double CLOUD_CELL_SIZE = 12;
    private static ClientLevel currentLevel;
    private static float coverage, previousCoverage, red, previousRed;

    static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level != currentLevel) {
            currentLevel = level;
            coverage = previousCoverage = red = previousRed = 0;
        }
        if (level == null || minecraft.player == null || !CloudArena.inCloudRealm(level)) return;
        CloudWeather weather = CloudWeather.CLEAR;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof YangJian boss && boss.isArenaBoss()) {
                CloudWeather sample = boss.weather();
                if (sample.rain() > weather.rain()) weather = sample;
            }
        }
        // Native rain and the custom clouds share the same boss transition clock.
        CloudArena.applyWeather(level, weather);
        previousCoverage = coverage;
        previousRed = red;
        coverage = Mth.approach(coverage, weather.coverage(), .042F);
        red = Mth.approach(red, weather.red(), .042F);
    }

    static boolean visible(ClientLevel level) {
        return level != null && level == currentLevel && previousCoverage + coverage > 0;
    }

    static void render(ClientLevel level, float partial, Vec3 camera, boolean suppressFlash,
                       VertexConsumer vertices, Matrix4f pose) {
        Vec3 platform = Vec3.atBottomCenterOf(CloudArena.CENTER.above()).subtract(camera);
        if (platform.horizontalDistanceSqr() > 75 * 75 || Math.abs(platform.y) > 60) return;
        double amount = Mth.lerp(partial, previousCoverage, coverage);
        double severity = Mth.lerp(partial, previousRed, red);
        double time = level.getGameTime() + (double) partial;
        Flash blue = Flash.at(time, false), crimson = Flash.at(time, true);
        double blueAmount = amount * (1 - severity), redAmount = amount * severity;
        double blueGlow = suppressFlash ? 0 : blue.light() * blueAmount;
        double redGlow = suppressFlash ? 0 : crimson.light() * redAmount;

        haze(vertices, pose, platform, amount, severity, blueGlow + redGlow);
        clouds(vertices, pose, platform, time, amount, severity, blue, crimson, blueGlow, redGlow, 1);
        clouds(vertices, pose, platform, time, amount, severity, blue, crimson, blueGlow, redGlow, 0);
        if (blue.light() * blueAmount > 0) lightning(vertices, pose, platform, blue, false, blueAmount);
        if (crimson.light() * redAmount > 0) lightning(vertices, pose, platform, crimson, true, redAmount);
    }

    private static void clouds(VertexConsumer v, Matrix4f pose, Vec3 center, double time, double amount,
                               double severity, Flash blue, Flash crimson, double blueGlow, double redGlow,
                               int layer) {
        double drift = time * (layer == 0 ? .044 : .025);
        double dx = drift % CLOUD_CELL_SIZE, dz = (drift * .49) % CLOUD_CELL_SIZE;
        int offsetX = (int) Math.floor(drift / CLOUD_CELL_SIZE);
        int offsetZ = (int) Math.floor(drift * .49 / CLOUD_CELL_SIZE);
        int stride = CLOUD_CELLS + 1;
        Vec3[] positions = new Vec3[stride * stride];
        int[] colors = new int[positions.length];
        for (int z = 0; z < stride; z++) {
            for (int x = 0; x < stride; x++) {
                double px = (x - CLOUD_CELLS / 2.0) * CLOUD_CELL_SIZE + dx;
                double pz = (z - CLOUD_CELLS / 2.0) * CLOUD_CELL_SIZE + dz;
                int cell = x - offsetX + (z - offsetZ) * 2689 + layer * 13103;
                double noise = YangJianVisuals.unit(cell + 3919);
                double fade = Mth.clamp((93 - Math.hypot(px, pz)) / 31, 0, 1);
                int dry = layer == 0 ? 0x161C28 : 0x26314B;
                int base = mix(dry, 0x3C2436, severity * .58);
                int rgb = mix(base, 0x59657D, noise * .55);
                if (blueGlow > 0) rgb = mix(rgb, 0xAFC7FF, blueGlow * blue.falloff(px, pz) * .67);
                if (redGlow > 0) rgb = mix(rgb, 0xCC425B, redGlow * crimson.falloff(px, pz) * .67);
                double density = layer == 0 ? .36 + noise * .61 : .57 + noise * .35;
                positions[z * stride + x] = center.add(px, 25 + layer * 10 + noise * 4.6, pz);
                colors[z * stride + x] = YangJianVisuals.tint(rgb, amount * density * fade);
            }
        }
        for (int z = 0; z < CLOUD_CELLS; z++) {
            for (int x = 0; x < CLOUD_CELLS; x++) {
                int a = z * stride + x, b = a + 1, c = b + stride, d = a + stride;
                if (((colors[a] | colors[b] | colors[c] | colors[d]) >>> 24) == 0) continue;
                YangJianVisuals.quad(v, pose, positions[a], positions[b], positions[c], positions[d],
                    colors[a], colors[b], colors[c], colors[d]);
            }
        }
    }

    private static void haze(VertexConsumer v, Matrix4f pose, Vec3 center, double amount, double severity, double glow) {
        int color = mix(mix(0x51596A, 0x432839, severity), 0xB07991, glow * .16);
        int bottom = YangJianVisuals.tint(color, 0);
        int middle = YangJianVisuals.tint(color, amount * (.57 + severity * .20));
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI / 24, b = (i + 1) * Math.PI / 24;
            Vec3 first = YangJianVisuals.radial(center, a, 93), second = YangJianVisuals.radial(center, b, 93);
            YangJianVisuals.quad(v, pose, first.add(0, -20, 0), second.add(0, -20, 0),
                second.add(0, 11, 0), first.add(0, 11, 0), bottom, bottom, middle, middle);
            YangJianVisuals.quad(v, pose, first.add(0, 11, 0), second.add(0, 11, 0),
                second.add(0, 49, 0), first.add(0, 49, 0), middle, middle, bottom, bottom);
        }
    }

    private static void lightning(VertexConsumer v, Matrix4f pose, Vec3 center, Flash flash, boolean red, double amount) {
        Vec3 sky = center.add(flash.x(), 25, flash.z());
        double strength = flash.light() * (.78 + .22 * flash.light()) * amount;
        Vec3 far = center.add(flash.x() * .67 - flash.z() * .28, 19.5, flash.z() * .67 + flash.x() * .28);
        YangJianVisuals.Palette palette = red ? YangJianVisuals.RED : YangJianVisuals.BLUE;
        YangJianVisuals.bolt(v, pose, sky, far, .37, 2.35, flash.age(), flash.seed(), palette, strength, true);
        if (red) {
            Vec3 ground = center.add(flash.x() * .82, -.6, flash.z() * .82);
            Vec3 leader = sky.lerp(ground, Math.min(1, (flash.age() + .7) / 3));
            YangJianVisuals.bolt(v, pose, sky, leader, .43, 2.1, flash.age(), flash.seed() + 71,
                palette, strength, true);
        }
    }

    private record Flash(double age, int seed, double x, double z, double light) {
        double falloff(double px, double pz) {
            return Math.max(0, 1 - Math.hypot(px - x, pz - z) / 52);
        }

        static Flash at(double time, boolean red) {
            int period = red ? 45 : 144;
            long turn = (long) Math.floor(time / period);
            int seed = (int) turn * 953 + (red ? 7837 : 941);
            double start = turn * (double) period + 4 + YangJianVisuals.unit(seed) * (period - (red ? 28 : 74));
            double age = time - start;
            double angle = YangJianVisuals.unit(seed + 111) * Math.PI * 2;
            double radius = 36 + YangJianVisuals.unit(seed + 507) * 14;
            double light = age < 0 || age >= 9 ? 0
                : .84 * Math.exp(-age / 2.2) + .43 * Math.exp(-Math.abs(age - 4.1) * 1.4);
            return new Flash(age, seed, Math.cos(angle) * radius, Math.sin(angle) * radius, light);
        }
    }

    private static int mix(int a, int b, double t) {
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255);
        int g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255);
        int blue = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | g << 8 | blue;
    }

    private YangJianStorm() {}
}
