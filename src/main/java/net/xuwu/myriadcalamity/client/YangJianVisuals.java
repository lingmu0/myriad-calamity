package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Shared geometry for Yang Jian's and shared boss emissive effects. Coordinates are local to the current pose. */
final class YangJianVisuals {
    record Palette(int glow, int body, int core) {}

    static final Palette BLUE = new Palette(0x2862F5, 0x72C2FF, 0xF0FCFF);
    static final Palette RED = new Palette(0xD61646, 0xFF4E6E, 0xFFF4EC);
    static final Palette GOLD = new Palette(0xE87C18, 0xFFD264, 0xFFFCE2);
    static final Palette SILVER = new Palette(0x6496DE, 0xBFEAFF, 0xFAFFFF);

    static void bolt(VertexConsumer v, Matrix4f pose, Vec3 from, Vec3 to, double width, double scatter,
                     double age, int seed, Palette palette, double opacity, boolean forks) {
        Vec3 route = to.subtract(from);
        double length = route.length();
        if (length < .025 || opacity <= 0) return;
        int pieces = Mth.clamp((int) Math.ceil(length * 1.85), 3, 22);
        Vec3 direction = route.scale(1 / length), side = side(direction), up = direction.cross(side);
        double clock = age / 2.1;
        Vec3 previous = from;
        for (int i = 1; i <= pieces; i++) {
            double t = (double) i / pieces;
            double jitter = scatter * Math.sin(Math.PI * t);
            Vec3 point = i == pieces ? to : from.lerp(to, t)
                .add(side.scale(noise(seed, i, clock) * jitter))
                .add(up.scale(noise(seed + 451, i, clock) * jitter));
            double weight = width * (1 - t * .44) * (.85 + .15 * noise(seed + 12, i, clock));
            electricSegment(v, pose, previous, point, side, up, weight, palette, opacity);
            if (forks && pieces >= 6 && (i == pieces / 4 || i == pieces / 2 || i == pieces * 3 / 4)) {
                double fork = Math.min(length * .24, 5.2);
                double sgn = (i % 2 == 0) ? 1 : -1;
                Vec3 tip = point.add(direction.scale(fork * .58))
                    .add(side.scale(fork * sgn * (.52 + .28 * unit(seed + i * 13))))
                    .add(up.scale(fork * noise(seed + 27, i, clock) * .6));
                bolt(v, pose, point, tip, width * .48, scatter * .38, age + 3, seed + i * 31,
                    palette, opacity * .66, false);
            }
            previous = point;
        }
    }

    private static void electricSegment(VertexConsumer v, Matrix4f pose, Vec3 from, Vec3 to, Vec3 side, Vec3 up,
                                        double width, Palette palette, double opacity) {
        cross(v, pose, from, to, side, up, width * 2.95, tint(palette.glow(), opacity * .26));
        cross(v, pose, from, to, side, up, width * 1.08, tint(palette.body(), opacity * .90));
        cross(v, pose, from, to, side, up, width * .32, tint(palette.core(), opacity));
    }

    static void eyeBeam(VertexConsumer v, Matrix4f pose, Vec3 from, Vec3 to, double radius, double age, int seed) {
        Vec3 axis = to.subtract(from);
        double length = axis.length();
        if (length < .025 || radius <= 0) return;
        Vec3 direction = axis.scale(1 / length), side = side(direction), up = direction.cross(side);
        int pieces = Mth.clamp((int) Math.ceil(length / 2.0), 6, 20);
        double pulse = .86 + .14 * Math.sin(age * 1.35);

        // 1. Muzzle focusing rings near the forehead emission point
        Vec3 muzzle = from.add(direction.scale(.18));
        orientedRing(v, pose, muzzle, side, up, radius * (1.28 + .16 * pulse), radius * .14, tint(GOLD.body(), .72));
        orientedRing(v, pose, from.add(direction.scale(.45)), side, up, radius * (1.65 - .14 * pulse), radius * .07, tint(RED.body(), .48));

        // 2. Layered pulsing beam shells with crimson-gold outer bloom and incandescent core
        beamShell(v, pose, from, to, side, up, radius, age, pieces, 1.18, GOLD.glow(), .18);
        beamShell(v, pose, from, to, side, up, radius, age, pieces, .74, GOLD.body(), .48);
        beamShell(v, pose, from, to, side, up, radius, age, pieces, .31, GOLD.core(), 1.0);

        // 3. Three braided luminous helical energy veins + outer crackling plasma strand
        for (int strand = 0; strand < 3; strand++) {
            Vec3 previous = from;
            for (int i = 1; i <= pieces; i++) {
                double t = (double) i / pieces;
                double phase = strand * Math.PI * 2 / 3 + age * .68 - t * length * .82 + seed;
                double spread = radius * .86 * Math.pow(Math.sin(Math.PI * t), .42);
                Vec3 point = i == pieces ? to : from.lerp(to, t)
                    .add(side.scale(Math.cos(phase) * spread))
                    .add(up.scale(Math.sin(phase) * spread));
                cross(v, pose, previous, point, side, up, radius * .12, tint(GOLD.body(), .38));
                cross(v, pose, previous, point, side, up, radius * .05, tint(GOLD.core(), .82));
                previous = point;
            }
        }
        // Crackling lightning filament racing along the beam exterior
        bolt(v, pose, from.add(direction.scale(.2)), to, Math.max(.025, radius * .07),
            Math.max(.12, radius * .62), age * 1.4, seed + 89, GOLD, .72, false);

        // 4. Radiant origin starburst + fiery impact endpoint eruption
        flare(v, pose, from.add(direction.scale(.02)), side, up, radius * .92, pulse, GOLD);
        Vec3 hit = to.subtract(direction.scale(.03));
        flare(v, pose, hit, side, up, radius * 1.45, pulse, GOLD);
        orientedRing(v, pose, hit, side, up, radius * (1.2 + (age * .35 % 1.4)), radius * .10,
            tint(GOLD.body(), Math.max(0, .75 - (age * .35 % 1.4) * .5)));
        // Upward/outward splashing plasma sparks at beam impact
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3 + age * .35 + seed;
            double reach = radius * (1.1 + .85 * unit(seed + k * 29 + (int) age));
            Vec3 radialDir = side.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            Vec3 sparkEnd = hit.subtract(direction.scale(reach * .55)).add(radialDir.scale(reach));
            strip(v, pose, hit, sparkEnd, direction, radius * .09, .01, tint(GOLD.core(), .9), tint(GOLD.glow(), 0));
        }
    }

    private static void beamShell(VertexConsumer v, Matrix4f pose, Vec3 from, Vec3 to, Vec3 side, Vec3 up,
                                  double radius, double age, int pieces, double scale, int rgb, double opacity) {
        Vec3 previous = from;
        double lastWidth = radius * scale * .24;
        for (int i = 1; i <= pieces; i++) {
            double t = (double) i / pieces;
            double band = .5 + .5 * Math.sin(age * 1.95 - t * pieces * 2.15);
            double width = radius * scale * (.80 + .22 * band);
            Vec3 next = from.lerp(to, t);
            int color = tint(rgb, opacity * (.70 + band * .30));
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
        if (radius <= 0 || opacity <= 0) return;
        // 12-segment radial soft glow disc
        int filled = tint(palette.body(), opacity * .62), edge = tint(palette.glow(), 0);
        int hot = tint(palette.core(), opacity * .92);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6, b = (i + 1) * Math.PI / 6;
            Vec3 first = center.add(side.scale(radius * Math.cos(a))).add(up.scale(radius * Math.sin(a)));
            Vec3 second = center.add(side.scale(radius * Math.cos(b))).add(up.scale(radius * Math.sin(b)));
            quad(v, pose, center, first, second, second, filled, edge, edge, edge);
            // Bright inner core disc
            Vec3 inA = center.add(side.scale(radius * .38 * Math.cos(a))).add(up.scale(radius * .38 * Math.sin(a)));
            Vec3 inB = center.add(side.scale(radius * .38 * Math.cos(b))).add(up.scale(radius * .38 * Math.sin(b)));
            quad(v, pose, center, inA, inB, inB, hot, filled, filled, filled);
        }
        // 4 crisp long primary starburst spikes + 4 diagonal secondary spikes
        int core = tint(palette.core(), opacity);
        int coreZero = tint(palette.body(), 0);
        starRay(v, pose, center, side, up, radius * 1.18, radius * .095, core, coreZero);
        starRay(v, pose, center, up, side, radius * 1.02, radius * .085, core, coreZero);
        Vec3 diagA = side.add(up).normalize(), diagB = side.subtract(up).normalize();
        starRay(v, pose, center, diagA, diagB, radius * .72, radius * .055, tint(palette.core(), opacity * .85), coreZero);
        starRay(v, pose, center, diagB, diagA, radius * .72, radius * .055, tint(palette.core(), opacity * .85), coreZero);
    }

    private static void starRay(VertexConsumer v, Matrix4f pose, Vec3 center, Vec3 axis, Vec3 perp,
                                double len, double halfW, int centerCol, int tipCol) {
        Vec3 pA = center.add(axis.scale(len)), pB = center.subtract(axis.scale(len));
        Vec3 w = perp.scale(halfW);
        quad(v, pose, center.subtract(w), center.add(w), pA, pA, centerCol, centerCol, tipCol, tipCol);
        quad(v, pose, center.add(w), center.subtract(w), pB, pB, centerCol, centerCol, tipCol, tipCol);
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
        ring(v, pose, center.add(0, .063, 0), closing, .11, tint(palette.glow(), .60));
        ring(v, pose, center.add(0, .068, 0), closing, .038, tint(palette.core(), .85 + charge * .15));
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

    static void orientedRing(VertexConsumer v, Matrix4f pose, Vec3 center, Vec3 side, Vec3 up,
                             double radius, double width, int color) {
        if (radius <= 0) return;
        double inner = Math.max(0, radius - width), outer = radius + width;
        int sides = 24;
        for (int i = 0; i < sides; i++) {
            double a = i * Math.PI * 2 / sides, b = (i + 1) * Math.PI * 2 / sides;
            Vec3 dirA = side.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            Vec3 dirB = side.scale(Math.cos(b)).add(up.scale(Math.sin(b)));
            quad(v, pose, center.add(dirA.scale(inner)), center.add(dirA.scale(outer)),
                center.add(dirB.scale(outer)), center.add(dirB.scale(inner)), color);
        }
    }

    static void impactShockRing(VertexConsumer v, Matrix4f pose, Vec3 center, double radius, double height,
                                double width, Palette palette, double opacity) {
        if (radius <= 0.05 || opacity <= 0) return;
        double inner = Math.max(0.02, radius - width), outer = radius + width * 0.6;
        int sides = Mth.clamp((int) (radius * 14), 28, 72);
        int zero = tint(palette.glow(), 0);
        int body = tint(palette.body(), opacity * .62);
        int core = tint(palette.core(), opacity * .94);
        for (int i = 0; i < sides; i++) {
            double a = i * Math.PI * 2 / sides, b = (i + 1) * Math.PI * 2 / sides;
            Vec3 outA = radial(center, a, outer).add(0, .045, 0), outB = radial(center, b, outer).add(0, .045, 0);
            Vec3 topA = radial(center, a, radius).add(0, height, 0), topB = radial(center, b, radius).add(0, height, 0);
            Vec3 inA = radial(center, a, inner).add(0, .045, 0), inB = radial(center, b, inner).add(0, .045, 0);
            quad(v, pose, outA, topA, topB, outB, zero, core, core, zero);
            quad(v, pose, topA, inA, inB, topB, body, zero, zero, body);
        }
        ring(v, pose, center.add(0, height, 0), radius, width * .12, core);
    }

    /** Piercing aerodynamic energy lance cone for high-speed thrusts, counters, and aerial dives. */
    static void sonicCone(VertexConsumer v, Matrix4f pose, Vec3 tip, Vec3 direction, double length, double radius,
                          Palette palette, double opacity) {
        if (length < .05 || radius <= 0 || opacity <= 0) return;
        Vec3 dir = direction.normalize();
        if (dir.lengthSqr() < 1E-8) return;
        Vec3 side = side(dir), up = dir.cross(side);
        Vec3 base = tip.subtract(dir.scale(length));
        int sides = 16;
        int tipCol = tint(palette.core(), opacity * .95);
        int midCol = tint(palette.body(), opacity * .52);
        int tailCol = tint(palette.glow(), 0);
        Vec3 midCenter = tip.subtract(dir.scale(length * .38));
        for (int i = 0; i < sides; i++) {
            double a = i * Math.PI * 2 / sides, b = (i + 1) * Math.PI * 2 / sides;
            Vec3 dA = side.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            Vec3 dB = side.scale(Math.cos(b)).add(up.scale(Math.sin(b)));
            Vec3 mA = midCenter.add(dA.scale(radius * .52)), mB = midCenter.add(dB.scale(radius * .52));
            Vec3 bA = base.add(dA.scale(radius)), bB = base.add(dB.scale(radius));
            quad(v, pose, tip, mA, mB, tip, tipCol, midCol, midCol, tipCol);
            quad(v, pose, mA, bA, bB, mB, midCol, tailCol, tailCol, midCol);
        }
        orientedRing(v, pose, midCenter, side, up, radius * .68, radius * .06, tint(palette.core(), opacity * .8));
        orientedRing(v, pose, tip.subtract(dir.scale(length * .72)), side, up, radius * .92, radius * .045,
            tint(palette.body(), opacity * .5));
    }

    /** Octagonal golden divine parry ward rendered in front of Yang Jian during GUARD. */
    static void divineWard(VertexConsumer v, Matrix4f pose, Vec3 center, Vec3 forward, double radius,
                           double age, Palette palette, double opacity) {
        if (opacity <= 0) return;
        Vec3 dir = forward.normalize();
        if (dir.lengthSqr() < 1E-8) return;
        Vec3 side = side(dir), up = dir.cross(side);
        double spin = age * .08;
        int oct = 8;
        int fill = tint(palette.glow(), opacity * .20);
        int rim = tint(palette.body(), opacity * .78);
        int core = tint(palette.core(), opacity * .96);
        for (int i = 0; i < oct; i++) {
            double a = spin + i * Math.PI / 4, b = spin + (i + 1) * Math.PI / 4;
            Vec3 pA = center.add(side.scale(Math.cos(a) * radius)).add(up.scale(Math.sin(a) * radius));
            Vec3 pB = center.add(side.scale(Math.cos(b) * radius)).add(up.scale(Math.sin(b) * radius));
            quad(v, pose, center, pA, pB, pB, tint(palette.body(), opacity * .32), fill, fill, fill);
            // Outer octagonal golden frame bar
            Vec3 inA = center.add(side.scale(Math.cos(a) * radius * .90)).add(up.scale(Math.sin(a) * radius * .90));
            Vec3 inB = center.add(side.scale(Math.cos(b) * radius * .90)).add(up.scale(Math.sin(b) * radius * .90));
            quad(v, pose, inA, pA, pB, inB, rim, core, core, rim);
        }
        // Counter-rotating inner inscribed diamond ward
        for (int i = 0; i < 4; i++) {
            double a = -spin * 1.4 + i * Math.PI / 2, b = -spin * 1.4 + (i + 1) * Math.PI / 2;
            Vec3 pA = center.add(side.scale(Math.cos(a) * radius * .64)).add(up.scale(Math.sin(a) * radius * .64));
            Vec3 pB = center.add(side.scale(Math.cos(b) * radius * .64)).add(up.scale(Math.sin(b) * radius * .64));
            Vec3 inA = center.add(side.scale(Math.cos(a) * radius * .56)).add(up.scale(Math.sin(a) * radius * .56));
            Vec3 inB = center.add(side.scale(Math.cos(b) * radius * .56)).add(up.scale(Math.sin(b) * radius * .56));
            quad(v, pose, inA, pA, pB, inB, core);
        }
    }

    /** 3-tier volumetric weapon slash trail: outer glow aura + vibrant crescent body + razor incandescent core. */
    static void bladeArc(VertexConsumer v, Matrix4f pose, Vec3 oldHeel, Vec3 oldTip, Vec3 heel, Vec3 tip,
                         Palette palette, double previous, double current) {
        if (oldTip.distanceToSqr(tip) + oldHeel.distanceToSqr(heel) < .00004) return;
        // 1. Wide soft bloom extending slightly beyond the blade tip for kinetic punch
        Vec3 oldAura = oldTip.add(oldTip.subtract(oldHeel).scale(.18));
        Vec3 newAura = tip.add(tip.subtract(heel).scale(.18));
        int zero = tint(palette.glow(), 0);
        int oldBloom = tint(palette.glow(), previous * .38), newBloom = tint(palette.glow(), current * .38);
        quad(v, pose, oldTip, oldAura, newAura, tip, oldBloom, zero, zero, newBloom);

        // 2. Main vibrant energy swath spanning heel -> tip
        int oldColor = tint(palette.body(), previous * .78), newColor = tint(palette.body(), current * .78);
        quad(v, pose, oldHeel, oldTip, tip, heel, zero, oldColor, newColor, zero);

        // 3. Razor-bright incandescent leading blade edge
        double oldWidth = .14 * previous + .03, width = .14 * current + .03;
        Vec3 oldEdge = oldTip.lerp(oldHeel, Math.min(.35, oldWidth));
        Vec3 edge = tip.lerp(heel, Math.min(.35, width));
        int oldCore = tint(palette.core(), previous), core = tint(palette.core(), current);
        quad(v, pose, oldEdge, oldTip, tip, edge, oldColor, oldCore, core, newColor);
    }

    static void wake(VertexConsumer v, Matrix4f pose, Vec3 tail, Vec3 head, double tailWidth, double headWidth,
                     Palette palette, double tailOpacity, double headOpacity) {
        Vec3 direction = head.subtract(tail).normalize();
        if (direction.lengthSqr() < 1E-8) return;
        Vec3 side = side(direction), up = direction.cross(side);
        for (Vec3 lateral : new Vec3[]{side, up}) {
            strip(v, pose, tail, head, lateral, tailWidth * 1.25, headWidth * 1.25,
                tint(palette.glow(), tailOpacity * .42), tint(palette.glow(), headOpacity * .42));
            strip(v, pose, tail, head, lateral, tailWidth * .60, headWidth * .60,
                tint(palette.body(), tailOpacity * .82), tint(palette.body(), headOpacity * .82));
            strip(v, pose, tail, head, lateral, tailWidth * .18, headWidth * .18,
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
        v.vertex(pose, (float) p.x, (float) p.y, (float) p.z).color(color).endVertex();
    }

    private YangJianVisuals() {}
}
