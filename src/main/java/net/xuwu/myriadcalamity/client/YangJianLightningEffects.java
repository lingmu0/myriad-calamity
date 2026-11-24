package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** A brief descending bolt and the ground bloom it leaves at the point of contact. */
final class YangJianLightningEffects {
    private static final Vec3 X_AXIS = new Vec3(1, 0, 0);
    private static final Vec3 Z_AXIS = new Vec3(0, 0, 1);

    static void strike(VertexConsumer vertices, Matrix4f pose, Vec3 ground, double height, double spread,
                       double width, double age, int interval, int seed, YangJianVisuals.Palette colors, double fade) {
        if (age < 0 || fade <= 0) return;
        int cycle = interval > 0 ? (int) (age / interval) : 0;
        double time = age - (double) cycle * interval;
        double contactTime = cycle == 0 ? 0 : 1.55;
        double impactAge = time - contactTime;
        if (impactAge >= 8) return;
        int shape = seed + 137 * cycle;
        Vec3 landing = ground.add(0, .087, 0);
        if (impactAge < 4.8) {
            Vec3 sky = ground.add((YangJianVisuals.unit(shape + 10) - .5) * 1.55, height,
                (YangJianVisuals.unit(shape + 25) - .5) * 1.55);
            double descent = contactTime == 0 ? 1 : Math.min(1, (time + .35) / (contactTime + .35));
            Vec3 leader = sky.lerp(landing, descent);
            double flash = impactAge < 0 ? .65 : Math.pow(1 - impactAge / 4.8, .7);
            YangJianVisuals.bolt(vertices, pose, sky, leader, width * (.72 + .3 * flash), .86,
                age, shape, colors, fade * flash, true);
            if (impactAge >= 0) {
                Vec3 fork = sky.lerp(landing, .26).add(.85, 0, -.60);
                YangJianVisuals.bolt(vertices, pose, fork, landing, width * .32, .45,
                    age + 2, shape + 37, colors, fade * flash * .68, false);
            }
        }
        if (impactAge < 0) return;
        double pulse = Math.pow(1 - impactAge / 8, 1.55) * fade;
        double reach = spread * (.36 + .64 * Math.min(1, impactAge / 3.5));
        YangJianVisuals.flare(vertices, pose, landing, X_AXIS, Z_AXIS, .21 + reach * .42, pulse, colors);
        for (int i = 0; i < 8; i++) {
            double heading = i * Math.PI / 4 + YangJianVisuals.unit(shape) * Math.PI;
            double extent = reach * (.54 + .46 * YangJianVisuals.unit(shape + i * 19));
            Vec3 end = YangJianVisuals.radial(landing, heading, extent)
                .add(0, .06 + .17 * YangJianVisuals.unit(shape - i * 71), 0);
            YangJianVisuals.bolt(vertices, pose, landing, end, .061, .15, age + i,
                shape + i * 47, colors, pulse, false);
            if (i % 2 == 0) {
                Vec3 root = landing.lerp(end, .61);
                Vec3 fork = YangJianVisuals.radial(root, heading + .94, extent * .36).add(0, .23, 0);
                YangJianVisuals.bolt(vertices, pose, root, fork, .029, .074, age, shape - i * 17,
                    colors, pulse * .71, false);
            }
        }
    }

    private YangJianLightningEffects() {}
}
