package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Shared geometry for Yang Jian's emissive effects. Coordinates are local to the current pose. */
final class YangJianVisuals {
    record Palette(int glow, int body, int core) {}

    static final Palette BLUE = new Palette(0x3268F0, 0x70BAFF, 0xECFAFF);
    static final Palette RED = new Palette(0xD1194B, 0xFF5271, 0xFFF2EB);
    static final Palette GOLD = new Palette(0xED8624, 0xFFD16A, 0xFFF8D9);
    static final Palette SILVER = new Palette(0x749FDB, 0xBDE8FF, 0xF7FFFF);

    static void bolt(VertexConsumer v, Matrix4f pose, Vec3 from, Vec3 to, double width, double scatter,
                     double age, int seed, Palette palette, double opacity, boolean forks) {
        Vec3 route = to.subtract(from);
        double length = route.length();
        if (length < .025 || opacity <= 0) return;
        int pieces = Mth.clamp((int) Math.ceil(length * 1.7), 3, 19);
        Vec3 direction = route.scale(1 / length), side = side(direction), up = direction.cross(side);
        double clock = age / 2.3;
        Vec3 previous = from;
        for (int i = 1; i <= pieces; i++) {
            double t = (double) i / pieces;
            double jitter = scatter * Math.sin(Math.PI * t);
            Vec3 point = i == pieces ? to : from.lerp(to, t)
                .add(side.scale(noise(seed, i, clock) * jitter))
                .add(up.scale(noise(seed + 451, i, clock) * jitter));
            double weight = width * (1 - t * .48) * (.86 + .14 * noise(seed + 12, i, clock));
            electricSegment(v, pose, previous, point, side, up, weight, palette, opacity);
            if (forks && pieces >= 6 && (i == pieces / 3 || i == pieces * 2 / 3)) {
                double fork = Math.min(length * .21, 5);
                Vec3 tip = point.add(direction.scale(fork * .6))
                    .add(side.scale(fork * (i == pieces / 3 ? 1 : -1) * .65))
                    .add(up.scale(fork * noise(seed + 27, i, clock) * .58));
                bolt(v, pose, point, tip, width * .51, scatter * .35, age + 3, seed + i * 31,
                    palette, opacity * .62, false);
            }
            previous = point;
        }
    }

    private static void electricSegment(VertexConsumer v, Matrix4f pose, Vec3 from, Vec3 to, Vec3 side, Vec3 up,
                                        double width, Palette palette, double opacity) {
        cross(v, pose, from, to, side, up, width * 2.65, tint(palette.glow(), opacity * .24));
        cross(v, pose, from, to, side, up, width, tint(palette.body(), opacity * .88));
        cross(v, pose, from, to, side, up, width * .29, tint(palette.core(), opacity));
    }

    static void eyeBeam(VertexConsumer v, Matrix4f pose, Vec3 from, Vec3 to, double radius, double age, int seed) {
        Vec3 axis = to.subtract(from);
        double length = axis.length();
        if (length < .025 || radius <= 0) return;
        Vec3 direction = axis.scale(1 / length), side = side(direction), up = direction.cross(side);
        int pieces = Mth.clamp((int) Math.ceil(length / 2.2), 5, 18);
        // All passes use the current clipped ray. The bright veins move inside its volume.
        beamShell(v, pose, from, to, side, up, radius, age, pieces, 1, GOLD.glow(), .16);
        beamShell(v, pose, from, to, side, up, radius, age, pieces, .69, GOLD.body(), .43);
        beamShell(v, pose, from, to, side, up, radius, age, pieces, .29, GOLD.core(), .98);
        for (int strand = 0; strand < 3; strand++) {
            Vec3 previous = from;
            for (int i = 1; i <= pieces; i++) {
                double t = (double) i / pieces;
                double phase = strand * Math.PI * 2 / 3 + age * .62 - t * length * .76 + seed;
                double spread = radius * .72 * Math.pow(Math.sin(Math.PI * t), .45);
                Vec3 point = i == pieces ? to : from.lerp(to, t)
                    .add(side.scale(Math.cos(phase) * spread))
                    .add(up.scale(Math.sin(phase) * spread));
                cross(v, pose, previous, point, side, up, radius * .055, tint(GOLD.core(), .67));
                previous = point;
            }
        }
        double pulse = .87 + .13 * Math.sin(age * 1.25);
        flare(v, pose, from.add(direction.scale(.008)), side, up, radius * .7, pulse, GOLD);
        flare(v, pose, to.subtract(direction.scale(.008)), side, up, radius, pulse, GOLD);
    }

    private static void beamShell(VertexConsumer v, Matrix4f pose, Vec3 from, Vec3 to, Vec3 side, Vec3 up,
                                  double radius, double age, int pieces, double scale, int rgb, double opacity) {
        Vec3 previous = from;
        double lastWidth = radius * scale * .22;
        for (int i = 1; i <= pieces; i++) {
            double t = (double) i / pieces;
            double band = .5 + .5 * Math.sin(age * 1.8 - t * pieces * 2.05);
            double width = radius * scale * (.82 + .18 * band);
            Vec3 next = from.lerp(to, t);
            int color = tint(rgb, opacity * (.72 + band * .28));
            for (int face = 0; face < 8; face++) {
                double a = face * Math.PI / 4, b = (face + 1) * Math.PI / 4;
                Vec3 first = side.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
                Vec3 second = side.scale(Math.cos(b)).add(up.scale(Math.sin(b)));
                quad(v, pose, previous.add(first.scale(lastWidth)), next.add(first.scale(width)),
                    next.add(second.scale(width)), previous.add(second.scale(lastWidth)), color);
            }
            previous = next;
            lastWidth = width;
        }
    }

    static void flare(VertexConsumer v, Matrix4f pose, Vec3 center, Vec3 side, Vec3 up, double radius,
                      double opacity, Palette palette) {
        int filled = tint(palette.body(), opacity * .57), edge = tint(palette.glow(), 0);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4, b = (i + 1) * Math.PI / 4;
            Vec3 first = center.add(side.scale(radius * Math.cos(a))).add(up.scale(radius * Math.sin(a)));
            Vec3 second = center.add(side.scale(radius * Math.cos(b))).add(up.scale(radius * Math.sin(b)));
            quad(v, pose, center, first, second, second, filled, edge, edge, edge);
        }
        int core = tint(palette.core(), opacity);
        strip(v, pose, center.subtract(side.scale(radius * .92)), center.add(side.scale(radius * .92)),
            up, radius * .08, radius * .08, core, core);
        strip(v, pose, center.subtract(up.scale(radius * .76)), center.add(up.scale(radius * .76)),
            side, radius * .07, radius * .07, core, core);
    }

    static void warningRing(VertexConsumer v, Matrix4f pose, Vec3 center, double radius, double progress,
                            Palette palette) {
        double charge = Mth.clamp(progress, 0, 1);
        int fill = tint(palette.glow(), .09 + .12 * charge);
        Vec3 floor = center.add(0, .032, 0);
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI / 32, b = (i + 1) * Math.PI / 32;
            Vec3 first = radial(floor, a, radius), second = radial(floor, b, radius);
            quad(v, pose, floor, first, second, second, fill);
        }
        // The dim perimeter always marks the complete hit area. Only the countdown moves.
        ring(v, pose, center.add(0, .044, 0), radius, .04, tint(palette.body(), .55));
        double closing = radius * (1 - charge);
        ring(v, pose, center.add(0, .063, 0), closing, .10, tint(palette.glow(), .58));
        ring(v, pose, center.add(0, .068, 0), closing, .036, tint(palette.core(), .85 + charge * .15));
    }

    static void ring(VertexConsumer v, Matrix4f pose, Vec3 center, double radius, double width, int color) {
        if (radius <= 0) return;
        double inner = Math.max(0, radius - width), outer = radius + width;
        int sides = Mth.clamp((int) (radius * 18), 24, 96);
        for (int i = 0; i < sides; i++) {
            double a = i * Math.PI * 2 / sides, b = (i + 1) * Math.PI * 2 / sides;
            quad(v, pose, radial(center, a, inner), radial(center, a, outer),
                radial(center, b, outer), radial(center, b, inner), color);
        }
    }

    static void bladeArc(VertexConsumer v, Matrix4f pose, Vec3 oldHeel, Vec3 oldTip, Vec3 heel, Vec3 tip,
                         Palette palette, double previous, double current) {
        if (oldTip.distanceToSqr(tip) + oldHeel.distanceToSqr(heel) < .00004) return;
        int inner = tint(palette.glow(), 0);
        int oldColor = tint(palette.body(), previous * .7), newColor = tint(palette.body(), current * .7);
        quad(v, pose, oldHeel, oldTip, tip, heel, inner, oldColor, newColor, inner);
        double oldWidth = .09 * previous, width = .09 * current;
        Vec3 oldEdge = oldTip.lerp(oldHeel, oldWidth), edge = tip.lerp(heel, width);
        int oldCore = tint(palette.core(), previous), core = tint(palette.core(), current);
        quad(v, pose, oldEdge, oldTip, tip, edge, oldColor, oldCore, core, newColor);
    }

    static void wake(VertexConsumer v, Matrix4f pose, Vec3 tail, Vec3 head, double tailWidth, double headWidth,
                     Palette palette, double tailOpacity, double headOpacity) {
        Vec3 direction = head.subtract(tail).normalize();
        if (direction.lengthSqr() < 1E-8) return;
        Vec3 side = side(direction), up = direction.cross(side);
        for (Vec3 lateral : new Vec3[]{side, up}) {
            strip(v, pose, tail, head, lateral, tailWidth, headWidth,
                tint(palette.glow(), tailOpacity * .40), tint(palette.glow(), headOpacity * .40));
            strip(v, pose, tail, head, lateral, tailWidth * .55, headWidth * .55,
                tint(palette.body(), tailOpacity * .78), tint(palette.body(), headOpacity * .78));
            strip(v, pose, tail, head, lateral, tailWidth * .16, headWidth * .16,
                tint(palette.core(), tailOpacity), tint(palette.core(), headOpacity));
        }
    }

    private static void cross(VertexConsumer v, Matrix4f pose, Vec3 from, Vec3 to, Vec3 side, Vec3 up,
                              double width, int color) {
        strip(v, pose, from, to, side, width, width, color, color);
        strip(v, pose, from, to, up, width, width, color, color);
    }

    static void strip(VertexConsumer v, Matrix4f pose, Vec3 from, Vec3 to, Vec3 side, double fromWidth,
                      double toWidth, int fromColor, int toColor) {
        Vec3 a = side.scale(fromWidth), b = side.scale(toWidth);
        quad(v, pose, from.subtract(a), from.add(a), to.add(b), to.subtract(b),
            fromColor, fromColor, toColor, toColor);
    }

    static Vec3 side(Vec3 direction) {
        return direction.cross(Math.abs(direction.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
    }

    static Vec3 radial(Vec3 center, double angle, double radius) {
        return center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
    }

    static double unit(int seed) {
        seed = (seed ^ (seed >>> 16)) * 0x7FEB352D;
        seed = (seed ^ (seed >>> 15)) * 0x846CA68B;
        return ((seed ^ (seed >>> 16)) >>> 8) / 16777216.0;
    }

    private static double noise(int seed, int point, double clock) {
        int frame = (int) Math.floor(clock);
        double t = clock - frame;
        t = t * t * (3 - 2 * t);
        int base = seed * 193 + point * 631;
        double a = unit(base + frame * 15731), b = unit(base + (frame + 1) * 15731);
        return (a + (b - a) * t) * 2 - 1;
    }

    static int tint(int rgb, double alpha) {
        return ((int) (Mth.clamp(alpha, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    static void quad(VertexConsumer v, Matrix4f pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        quad(v, pose, a, b, c, d, color, color, color, color);
    }

    static void quad(VertexConsumer v, Matrix4f pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                     int colorA, int colorB, int colorC, int colorD) {
        point(v, pose, a, colorA);
        point(v, pose, b, colorB);
        point(v, pose, c, colorC);
        point(v, pose, d, colorD);
    }

    private static void point(VertexConsumer v, Matrix4f pose, Vec3 p, int color) {
        v.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(color);
    }

    private YangJianVisuals() {}
}
