package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Multi-branch descending celestial thunderbolt with sky-breach flare, shock-ripples, and ground arcs. */
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
        if (impactAge >= 8.5) return;
        int shape = seed + 137 * cycle;
        Vec3 landing = ground.add(0, .087, 0);
        if (impactAge < 5.2) {
            Vec3 sky = ground.add((YangJianVisuals.unit(shape + 10) - .5) * 1.65, height,
                (YangJianVisuals.unit(shape + 25) - .5) * 1.65);
            double descent = contactTime == 0 ? 1 : Math.min(1, (time + .35) / (contactTime + .35));
            Vec3 leader = sky.lerp(landing, descent);
            double flash = impactAge < 0 ? .68 : Math.pow(1 - impactAge / 5.2, .68);
            // Sky-breach radiant flash at bolt origin
            YangJianVisuals.flare(vertices, pose, sky, X_AXIS, Z_AXIS, width * 3.8 * flash, fade * flash * .78, colors);
            // Primary jagged lightning trunk
            YangJianVisuals.bolt(vertices, pose, sky, leader, width * (.76 + .32 * flash), .90,
                age, shape, colors, fade * flash, true);
            if (impactAge >= 0) {
                // Secondary intertwined plasma trunk + off-axis downward branch
                Vec3 forkA = sky.lerp(landing, .24).add(.95, 0, -.65);
                YangJianVisuals.bolt(vertices, pose, forkA, landing, width * .36, .52,
                    age + 2, shape + 37, colors, fade * flash * .74, false);
                Vec3 forkB = sky.lerp(landing, .42).add(-.82, 0, .74);
                Vec3 forkBEnd = landing.add(-.65, .04, .58);
                YangJianVisuals.bolt(vertices, pose, forkB, forkBEnd, width * .26, .42,
                    age + 4, shape + 91, colors, fade * flash * .58, false);
            }
        }
        if (impactAge < 0) return;
        double pulse = Math.pow(1 - impactAge / 8.5, 1.48) * fade;
        double reach = spread * (.38 + .62 * Math.min(1, impactAge / 3.4));
        // Blazing ground impact starburst + concentric expanding electric shock-ripples
        YangJianVisuals.flare(vertices, pose, landing, X_AXIS, Z_AXIS, .28 + reach * .48, pulse, colors);
        if (impactAge < 6.5) {
            double ringT = impactAge / 6.5;
            double ringFade = (1 - ringT) * (1 - ringT) * fade;
            YangJianVisuals.ring(vertices, pose, landing.add(0, .02, 0),
                .25 + spread * (.25 + .95 * ringT), .09 * (1 - ringT * .4), YangJianVisuals.tint(colors.body(), ringFade * .72));
            YangJianVisuals.ring(vertices, pose, landing.add(0, .035, 0),
                .20 + spread * (.22 + .85 * ringT), .032, YangJianVisuals.tint(colors.core(), ringFade * .95));
        }
        // Radiating ground spider-arcs + branching upward tendrils
        for (int i = 0; i < 8; i++) {
            double heading = i * Math.PI / 4 + YangJianVisuals.unit(shape) * Math.PI;
            double extent = reach * (.56 + .44 * YangJianVisuals.unit(shape + i * 19));
            Vec3 end = YangJianVisuals.radial(landing, heading, extent)
                .add(0, .07 + .20 * YangJianVisuals.unit(shape - i * 71), 0);
            YangJianVisuals.bolt(vertices, pose, landing, end, .064, .17, age + i,
                shape + i * 47, colors, pulse, false);
            if (i % 2 == 0) {
                Vec3 root = landing.lerp(end, .58);
                Vec3 fork = YangJianVisuals.radial(root, heading + .92, extent * .40).add(0, .28, 0);
                YangJianVisuals.bolt(vertices, pose, root, fork, .031, .085, age, shape - i * 17,
                    colors, pulse * .75, false);
            }
        }
    }

    private YangJianLightningEffects() {}
}
