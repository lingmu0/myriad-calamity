package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * High-impact non-MC cinematic volumetric VFX geometry kit for the Sekiro Divine Dragon:
 * layered HDR-feel energy crescents, sword-cleave ribbons, braided cyclone breath beams,
 * crimson-gold straight grab corridors, rotating sacred thunder seals, and golden lightning prisons.
 */
final class DivineDragonVfx {
    static final YangJianVisuals.Palette JADE = new YangJianVisuals.Palette(0x28F2C4, 0x9CFFF0, 0xFFFFFF);
    static final YangJianVisuals.Palette SAKURA = new YangJianVisuals.Palette(0xFF70B6, 0xFFC6E4, 0xFFFFFF);
    static final YangJianVisuals.Palette VIOLET_JADE = new YangJianVisuals.Palette(0x8C7EFF, 0xC6FAFF, 0xFFFFFF);
    static final YangJianVisuals.Palette GOLD = YangJianVisuals.GOLD;
    static final YangJianVisuals.Palette CRIMSON = new YangJianVisuals.Palette(0xB80E28, 0xFF3A52, 0xFFE8A0);
    static final YangJianVisuals.Palette STORM_WHITE = new YangJianVisuals.Palette(0x74C8FF, 0xD8F2FF, 0xFFFFFF);
    static final YangJianVisuals.Palette MEGA_GOLD = new YangJianVisuals.Palette(0xB8E8FF, 0xFFF4C8, 0xFFFFFF);

    private DivineDragonVfx() {}

    /** Renders a razor-slim, crystal-translucent 32-segment crescent blade with bright core gleam and delicate sakura veil. */
    static void bladeCrescent(VertexConsumer v, Matrix4f mat, Vec3 forward, Vec3 spanAxis, Vec3 thickAxis,
                              double halfSpan, double depthBow, double halfThick, double fade,
                              float age, YangJianVisuals.Palette mainPal, boolean sakuraEdge) {
        int segs = 32;
        int outerGlow = YangJianVisuals.tint(mainPal.glow(), fade * 0.16);
        int zeroGlow = YangJianVisuals.tint(mainPal.glow(), 0.0);
        int bodyCol = YangJianVisuals.tint(mainPal.body(), fade * 0.44);
        int finCol = YangJianVisuals.tint(mainPal.body(), fade * 0.28);
        int coreCol = YangJianVisuals.tint(mainPal.core(), fade * 0.92);
        int sakBody = YangJianVisuals.tint(SAKURA.body(), fade * 0.34);
        int sakZero = YangJianVisuals.tint(SAKURA.glow(), 0.0);

        for (int i = 0; i < segs; i++) {
            double u0 = -1.0 + 2.0 * i / segs;
            double u1 = -1.0 + 2.0 * (i + 1) / segs;
            double bow0 = Math.pow(Math.max(0.0, 1.0 - u0 * u0), 0.85);
            double bow1 = Math.pow(Math.max(0.0, 1.0 - u1 * u1), 0.85);

            Vec3 lead0 = spanAxis.scale(u0 * halfSpan).add(forward.scale(depthBow * bow0));
            Vec3 lead1 = spanAxis.scale(u1 * halfSpan).add(forward.scale(depthBow * bow1));

            // Razor-slim pure-white specular filament -> translucent crystal-jade blade -> gossamer fading tail
            Vec3 coreBack0 = lead0.subtract(forward.scale(depthBow * 0.11 * bow0 + 0.10));
            Vec3 coreBack1 = lead1.subtract(forward.scale(depthBow * 0.11 * bow1 + 0.10));

            Vec3 mid0 = lead0.subtract(forward.scale(depthBow * 0.30 * bow0 + 0.28));
            Vec3 mid1 = lead1.subtract(forward.scale(depthBow * 0.30 * bow1 + 0.28));

            Vec3 tail0 = lead0.subtract(forward.scale(depthBow * 0.78 * bow0 + 0.75));
            Vec3 tail1 = lead1.subtract(forward.scale(depthBow * 0.78 * bow1 + 0.75));

            // 1. Blinding thin edge -> crystal translucent emerald mantle -> clear wake
            YangJianVisuals.quad(v, mat, lead0, lead1, coreBack1, coreBack0, coreCol, coreCol, coreCol, coreCol);
            YangJianVisuals.quad(v, mat, coreBack0, coreBack1, mid1, mid0, coreCol, coreCol, bodyCol, bodyCol);
            YangJianVisuals.quad(v, mat, mid0, mid1, tail1, tail0, bodyCol, bodyCol, zeroGlow, zeroGlow);

            // 2. Slim glassy upper/lower bevel fins (translucent light-catching edge without boxy bulk)
            Vec3 top0 = coreBack0.add(thickAxis.scale(halfThick * (0.20 + 0.80 * bow0)));
            Vec3 top1 = coreBack1.add(thickAxis.scale(halfThick * (0.20 + 0.80 * bow1)));
            Vec3 bot0 = coreBack0.subtract(thickAxis.scale(halfThick * (0.20 + 0.80 * bow0)));
            Vec3 bot1 = coreBack1.subtract(thickAxis.scale(halfThick * (0.20 + 0.80 * bow1)));
            YangJianVisuals.quad(v, mat, lead0, lead1, top1, top0, coreCol, coreCol, finCol, finCol);
            YangJianVisuals.quad(v, mat, lead0, lead1, bot1, bot0, coreCol, coreCol, finCol, finCol);
            YangJianVisuals.quad(v, mat, top0, top1, tail1, tail0, outerGlow, outerGlow, zeroGlow, zeroGlow);
            YangJianVisuals.quad(v, mat, bot0, bot1, tail1, tail0, outerGlow, outerGlow, zeroGlow, zeroGlow);

            // 3. Delicate fluttering translucent sakura light-silk behind the crescent
            if (sakuraEdge) {
                double wave0 = Math.sin(u0 * 9.0 - age * 0.55) * halfThick * 0.55 * bow0;
                double wave1 = Math.sin(u1 * 9.0 - age * 0.55) * halfThick * 0.55 * bow1;
                Vec3 rLead0 = mid0.add(thickAxis.scale(wave0));
                Vec3 rLead1 = mid1.add(thickAxis.scale(wave1));
                Vec3 rTail0 = tail0.subtract(forward.scale(1.2 * bow0)).add(thickAxis.scale(wave0 * 1.25));
                Vec3 rTail1 = tail1.subtract(forward.scale(1.2 * bow1)).add(thickAxis.scale(wave1 * 1.25));
                YangJianVisuals.quad(v, mat, rLead0, rLead1, rTail1, rTail0, sakBody, sakBody, sakZero, sakZero);
            }
        }
    }

    /** Renders a sleek, translucent crystal slash arc painted directly off the Seven-Branched Sword! */
    static void swordSlashRibbon(VertexConsumer v, Matrix4f mat, Vec3[] innerPts, Vec3[] outerPts, double opacity) {
        int n = Math.min(innerPts.length, outerPts.length);
        if (n < 2 || opacity <= 0.02) return;
        for (int i = 0; i < n - 1; i++) {
            double f0 = (double) i / (n - 1);
            double f1 = (double) (i + 1) / (n - 1);
            double a0 = Math.pow(f0, 1.5) * opacity;
            double a1 = Math.pow(f1, 1.5) * opacity;
            Vec3 mid0 = innerPts[i].lerp(outerPts[i], 0.68);
            Vec3 mid1 = innerPts[i + 1].lerp(outerPts[i + 1], 0.68);

            int in0 = YangJianVisuals.tint(SAKURA.body(), a0 * 0.12);
            int in1 = YangJianVisuals.tint(SAKURA.body(), a1 * 0.12);
            int md0 = YangJianVisuals.tint(JADE.body(), a0 * 0.44);
            int md1 = YangJianVisuals.tint(JADE.body(), a1 * 0.44);
            int out0 = YangJianVisuals.tint(JADE.core(), a0 * 0.92);
            int out1 = YangJianVisuals.tint(JADE.core(), a1 * 0.92);

            YangJianVisuals.quad(v, mat, innerPts[i], innerPts[i + 1], mid1, mid0, in0, in1, md1, md0);
            YangJianVisuals.quad(v, mat, mid0, mid1, outerPts[i + 1], outerPts[i], md0, md1, out1, out0);
        }
    }

    /**
     * Renders a clean, un-bent, straight diagonal roaring cyclone beam erupting directly from the dragon's
     * animated mouth straight down onto the ground impact point (endTarget), with a crisp ground-contact swirl!
     */
    static void galeCycloneBeam(VertexConsumer v, Matrix4f mat, Vec3 mouthOrigin, Vec3 endTarget,
                                float actAge, double fade, int seed) {
        Vec3 axis = endTarget.subtract(mouthOrigin);
        double len = axis.length();
        if (len < 1.0 || fade <= 0.02) return;
        Vec3 dir = axis.scale(1.0 / len);
        Vec3 side = Math.abs(dir.y) > 0.92 ? new Vec3(1, 0, 0) : dir.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = side.cross(dir).normalize();

        // 1. Mouth focusing plasma halos right in front of the roaring jaws
        for (int k = 0; k < 3; k++) {
            double off = 1.2 + k * 2.2;
            double r = (2.4 + k * 0.95) * (0.9 + 0.12 * Math.sin(actAge * 1.1 + k));
            orientedRing(v, mat, mouthOrigin.add(dir.scale(off)), side, up, r, 0.24,
                YangJianVisuals.tint(k == 0 ? JADE.core() : (k == 1 ? JADE.body() : SAKURA.body()), fade * (0.80 - k * 0.16)));
        }

        // 2. Three concentric straight diagonal energy jet shells (matches 3.5-radius 3D beam hitbox 1:1!)
        YangJianVisuals.wake(v, mat, mouthOrigin, endTarget, 0.85, 1.85, JADE, 0.92 * fade, 0.72 * fade);
        YangJianVisuals.wake(v, mat, mouthOrigin, endTarget, 1.80, 3.25, JADE, 0.46 * fade, 0.34 * fade);
        YangJianVisuals.wake(v, mat, mouthOrigin, endTarget, 2.75, 4.10, SAKURA, 0.20 * fade, 0.14 * fade);

        // 3. Four counter-rotating high-speed braided helical cyclone ribbons along the straight diagonal beam
        int steps = 26;
        for (int strand = 0; strand < 4; strand++) {
            double dirSign = (strand % 2 == 0) ? 1.0 : -1.0;
            YangJianVisuals.Palette pal = (strand == 3) ? SAKURA : JADE;
            Vec3 prev = mouthOrigin;
            for (int i = 1; i <= steps; i++) {
                double u = (double) i / steps;
                double phase = strand * (Math.PI * 0.5) + dirSign * (actAge * 0.90 - u * 16.0);
                double rad = (1.3 + u * 2.1) * Math.sin(Math.pow(u, 0.42) * Math.PI * 0.94);
                Vec3 pt = mouthOrigin.lerp(endTarget, u)
                    .add(side.scale(Math.cos(phase) * rad))
                    .add(up.scale(Math.sin(phase) * rad));
                double w = 0.20 + u * 0.16;
                int c = YangJianVisuals.tint(strand == 0 ? pal.core() : pal.body(), fade * (0.76 - u * 0.22));
                ribbonSegment(v, mat, prev, pt, side, up, w, c);
                prev = pt;
            }
        }

        // 4. Supersonic shockwave rings rushing straight down the diagonal beam + crisp ground-impact ring!
        for (int i = 0; i < 6; i++) {
            double u = ((actAge * 0.10 + i / 6.0) % 1.0);
            Vec3 c = mouthOrigin.lerp(endTarget, u);
            double r = 1.9 + u * 1.9;
            orientedRing(v, mat, c, side, up, r, 0.24,
                YangJianVisuals.tint(i % 2 == 0 ? JADE.core() : SAKURA.body(), fade * (1.0 - u * 0.45) * 0.72));
        }

        double splashPulse = 0.85 + 0.15 * Math.sin(actAge * 1.4);
        Vec3 floorSpot = new Vec3(endTarget.x, endTarget.y - 0.50, endTarget.z);
        YangJianVisuals.ring(v, mat, floorSpot, 4.4 * splashPulse, 0.42,
            YangJianVisuals.tint(JADE.core(), fade * 0.82));
        YangJianVisuals.ring(v, mat, floorSpot.add(0, 0.04, 0), 2.5 * splashPulse, 1.6,
            YangJianVisuals.tint(JADE.body(), fade * 0.28));

        YangJianVisuals.bolt(v, mat, mouthOrigin.add(dir.scale(2.0)), endTarget, 0.28, 1.9,
            actAge * 1.4, seed, JADE, 0.72 * fade, false);
    }

    /** Renders the wide straight crimson-gold grab danger corridor (11 blocks wide, 40 blocks long) with rushing chevrons! */
    static void grabDangerCorridor(VertexConsumer v, Matrix4f mat, Vec3 originFloor, Vec3 dir,
                                   double minRange, double maxRange, double halfWidth,
                                   float actAge, boolean locked) {
        Vec3 right = new Vec3(-dir.z, 0.0, dir.x);
        double flash = locked ? (0.82 + 0.18 * Math.sin(actAge * 1.25)) : 0.70;
        double charge = Mth.clamp(actAge / 38.0, 0.0, 1.0);

        Vec3 sL = originFloor.add(dir.scale(minRange)).subtract(right.scale(halfWidth));
        Vec3 sR = originFloor.add(dir.scale(minRange)).add(right.scale(halfWidth));
        Vec3 eL = originFloor.add(dir.scale(maxRange)).subtract(right.scale(halfWidth));
        Vec3 eR = originFloor.add(dir.scale(maxRange)).add(right.scale(halfWidth));

        // 1. Translucent crimson danger floor carpet (brightens toward lock/bite impact)
        int carpetNear = YangJianVisuals.tint(CRIMSON.glow(), (0.24 + 0.24 * charge) * flash);
        int carpetFar = YangJianVisuals.tint(CRIMSON.glow(), (0.14 + 0.22 * charge) * flash);
        YangJianVisuals.quad(v, mat, sL, sR, eR, eL, carpetNear, carpetNear, carpetFar, carpetFar);

        // 2. Blazing double-layered crimson-gold edge rails + rising holographic energy curb walls
        int railCore = YangJianVisuals.tint(CRIMSON.core(), 0.95 * flash);
        int railBody = YangJianVisuals.tint(CRIMSON.body(), 0.85 * flash);
        int railZero = YangJianVisuals.tint(CRIMSON.glow(), 0.0);
        double railW = 0.55;
        double wallH = 1.4 + 1.2 * charge;
        for (int sideSign : new int[]{-1, 1}) {
            Vec3 a = originFloor.add(dir.scale(minRange)).add(right.scale(sideSign * halfWidth));
            Vec3 b = originFloor.add(dir.scale(maxRange)).add(right.scale(sideSign * halfWidth));
            Vec3 aIn = a.subtract(right.scale(sideSign * railW));
            Vec3 bIn = b.subtract(right.scale(sideSign * railW));
            YangJianVisuals.quad(v, mat, a, aIn, bIn, b, railCore, railBody, railBody, railCore);
            // Vertical energy barrier curb along the lane edges
            YangJianVisuals.quad(v, mat, a, b, b.add(0, wallH, 0), a.add(0, wallH, 0), railBody, railBody, railZero, railZero);
        }
        // End-cap glowing bar
        YangJianVisuals.quad(v, mat, eL, eR, eR.subtract(dir.scale(0.6)), eL.subtract(dir.scale(0.6)),
            railCore, railCore, railBody, railBody);

        // 3. Forward-rushing holographic chevron waves (>>>) sweeping down the straight corridor!
        int chevrons = 6;
        for (int i = 0; i < chevrons; i++) {
            double u = ((actAge * 0.075 + i / (double) chevrons) % 1.0);
            double dist = Mth.lerp(u, minRange + 1.5, maxRange - 2.5);
            double cAlpha = Math.sin(u * Math.PI) * (0.72 + 0.25 * charge) * flash;
            int chCore = YangJianVisuals.tint(CRIMSON.core(), cAlpha);
            int chBody = YangJianVisuals.tint(CRIMSON.body(), cAlpha * 0.55);
            Vec3 apex = originFloor.add(dir.scale(dist + 2.4)).add(0, 0.10, 0);
            Vec3 apexBack = originFloor.add(dir.scale(dist + 1.3)).add(0, 0.10, 0);
            Vec3 wingL = originFloor.add(dir.scale(dist - 1.2)).subtract(right.scale(halfWidth * 0.88)).add(0, 0.10, 0);
            Vec3 wingLBack = originFloor.add(dir.scale(dist - 2.2)).subtract(right.scale(halfWidth * 0.88)).add(0, 0.10, 0);
            Vec3 wingR = originFloor.add(dir.scale(dist - 1.2)).add(right.scale(halfWidth * 0.88)).add(0, 0.10, 0);
            Vec3 wingRBack = originFloor.add(dir.scale(dist - 2.2)).add(right.scale(halfWidth * 0.88)).add(0, 0.10, 0);
            YangJianVisuals.quad(v, mat, apex, wingL, wingLBack, apexBack, chCore, chBody, chBody, chCore);
            YangJianVisuals.quad(v, mat, apex, apexBack, wingRBack, wingR, chCore, chCore, chBody, chBody);
        }
    }

    /** Renders a rotating sacred golden magic seal + converging light pillar (non-MC cinematic lightning telegraph!). */
    static void sacredThunderSeal(VertexConsumer v, Matrix4f mat, Vec3 center, double radius,
                                  float charge, float age, boolean locked) {
        double c = Mth.clamp(charge, 0.0, 1.0);
        double pulse = 0.80 + 0.20 * Math.sin(age * 0.65);
        int coreCol = YangJianVisuals.tint(GOLD.core(), (0.55 + 0.45 * c) * pulse);
        int bodyCol = YangJianVisuals.tint(GOLD.body(), (0.40 + 0.50 * c) * pulse);
        int glowCol = YangJianVisuals.tint(GOLD.glow(), (0.18 + 0.30 * c) * pulse);

        // Outer luminous ring + converging inner charge ring
        YangJianVisuals.ring(v, mat, center.add(0, 0.07, 0), radius, 0.24, bodyCol);
        YangJianVisuals.ring(v, mat, center.add(0, 0.09, 0), radius * (1.05 - 0.85 * c), 0.16, coreCol);

        // Counter-rotating sacred 8-point geometric rune star inside the seal
        double rotA = age * 0.09;
        double rotB = -age * 0.13;
        drawPolygonRing(v, mat, center.add(0, 0.08, 0), radius * 0.88, 4, rotA, 0.16, coreCol);
        drawPolygonRing(v, mat, center.add(0, 0.08, 0), radius * 0.88, 4, rotA + Math.PI * 0.25, 0.16, bodyCol);
        if (locked) {
            drawPolygonRing(v, mat, center.add(0, 0.11, 0), radius * 1.15, 6, rotB, 0.14, coreCol);
            // Sky-piercing golden lock beacon column
            YangJianVisuals.wake(v, mat, center.add(0, 74.0, 0), center.add(0, 0.1, 0),
                0.08, 0.24 + c * 0.42, GOLD, 0.14, 0.62 + 0.35 * c);
        } else if (c > 0.45) {
            double tf = (c - 0.45) / 0.55;
            YangJianVisuals.wake(v, mat, center.add(0, 62.0, 0), center.add(0, 0.1, 0),
                0.05, 0.18 * tf, GOLD, 0.08, 0.48 * tf);
        }
        // Soft radial energy disc glow at floor
        YangJianVisuals.ring(v, mat, center.add(0, 0.05, 0), radius * 0.55, radius * 0.45, glowCol);
    }

    /** Renders an expanding translucent volumetric golden shockwave hemisphere dome upon lightning impact. */
    static void shockwaveDome(VertexConsumer v, Matrix4f mat, Vec3 center, double radius, double height,
                              YangJianVisuals.Palette pal, double fade) {
        if (fade <= 0.02 || radius <= 0.1) return;
        int segs = 24;
        int rings = 5;
        for (int r = 0; r < rings; r++) {
            double phi0 = (r / (double) rings) * (Math.PI * 0.5);
            double phi1 = ((r + 1) / (double) rings) * (Math.PI * 0.5);
            double rad0 = Math.cos(phi0) * radius;
            double rad1 = Math.cos(phi1) * radius;
            double y0 = Math.sin(phi0) * height;
            double y1 = Math.sin(phi1) * height;
            int col0 = YangJianVisuals.tint(r == 0 ? pal.core() : pal.body(), fade * (0.65 - r * 0.11));
            int col1 = YangJianVisuals.tint(pal.glow(), fade * Math.max(0.0, 0.50 - (r + 1) * 0.11));
            for (int i = 0; i < segs; i++) {
                double a0 = i * (Math.PI * 2.0 / segs);
                double a1 = (i + 1) * (Math.PI * 2.0 / segs);
                Vec3 p00 = center.add(Math.cos(a0) * rad0, y0, Math.sin(a0) * rad0);
                Vec3 p01 = center.add(Math.cos(a1) * rad0, y0, Math.sin(a1) * rad0);
                Vec3 p11 = center.add(Math.cos(a1) * rad1, y1, Math.sin(a1) * rad1);
                Vec3 p10 = center.add(Math.cos(a0) * rad1, y1, Math.sin(a0) * rad1);
                YangJianVisuals.quad(v, mat, p00, p01, p11, p10, col0, col0, col1, col1);
            }
        }
    }

    /** Renders the airborne golden celestial prison + forked execution bolts for Sky-Bite Thunder Throw. */
    static void skyThunderPrison(VertexConsumer v, Matrix4f mat, Vec3 airCenter, float actAge, int seed) {
        double lockFade = actAge > 114.0F ? Math.max(0.0, 1.0 - (actAge - 114.0F) / 6.0)
            : Math.min(1.0, (actAge - 56.0F) / 10.0);
        if (lockFade > 0.02) {
            // Three counter-rotating golden magic halos encircling the immobilized airborne victim
            YangJianVisuals.ring(v, mat, airCenter.add(0, 0.2, 0), 2.4, 0.24,
                YangJianVisuals.tint(GOLD.core(), 0.92 * lockFade));
            YangJianVisuals.ring(v, mat, airCenter.add(0, 1.6, 0), 2.9, 0.22,
                YangJianVisuals.tint(GOLD.body(), 0.85 * lockFade));
            drawPolygonRing(v, mat, airCenter.add(0, 0.9, 0), 2.7, 6, actAge * 0.15, 0.18,
                YangJianVisuals.tint(GOLD.core(), 0.88 * lockFade));
            drawPolygonRing(v, mat, airCenter.add(0, 2.3, 0), 2.2, 4, -actAge * 0.20, 0.15,
                YangJianVisuals.tint(CRIMSON.core(), 0.82 * lockFade));

            // 6 crackling golden sky-chains descending from the storm cloud canopy
            for (int i = 0; i < 6; i++) {
                double a = i * (Math.PI / 3.0) + actAge * 0.07;
                Vec3 pt = airCenter.add(Math.cos(a) * 2.3, 1.0, Math.sin(a) * 2.3);
                YangJianVisuals.wake(v, mat, pt.add(0, 48.0, 0), pt.add(0, -2.0, 0),
                    0.06, 0.18, GOLD, 0.12 * lockFade, 0.78 * lockFade);
            }
        }

        // 4 blazing forked golden execution thunderbolts + volumetric shockwave domes at t = 76, 87, 98, 109!
        int[] strikeTicks = {76, 87, 98, 109};
        for (int idx = 0; idx < strikeTicks.length; idx++) {
            float landed = actAge - strikeTicks[idx];
            if (landed >= 0.0F && landed < 11.0F) {
                YangJianLightningEffects.strike(v, mat, airCenter, 76.0, 3.4, 0.48,
                    landed, idx, seed + idx * 37, GOLD, 1.0);
                YangJianVisuals.bolt(v, mat, airCenter.add(0, 52.0, 0), airCenter, 0.55, 4.2,
                    landed * 1.8, seed + idx * 91, GOLD, Math.max(0.0, 1.0 - landed / 9.0), true);
                double shockFade = Math.max(0.0, 1.0 - landed / 8.0);
                shockwaveDome(v, mat, airCenter.add(0, -0.5, 0), 1.4 + landed * 1.1, 1.2 + landed * 0.75, GOLD, shockFade * 0.85);
            }
        }
    }

    static void orientedRing(VertexConsumer v, Matrix4f mat, Vec3 center, Vec3 side, Vec3 up,
                             double radius, double thickness, int col) {
        int segs = 20;
        double rIn = Math.max(0.05, radius - thickness * 0.5);
        double rOut = radius + thickness * 0.5;
        for (int i = 0; i < segs; i++) {
            double a0 = i * (Math.PI * 2.0 / segs);
            double a1 = (i + 1) * (Math.PI * 2.0 / segs);
            Vec3 d0 = side.scale(Math.cos(a0)).add(up.scale(Math.sin(a0)));
            Vec3 d1 = side.scale(Math.cos(a1)).add(up.scale(Math.sin(a1)));
            YangJianVisuals.quad(v, mat,
                center.add(d0.scale(rIn)), center.add(d1.scale(rIn)),
                center.add(d1.scale(rOut)), center.add(d0.scale(rOut)),
                col, col, col, col);
        }
    }

    static void drawPolygonRing(VertexConsumer v, Matrix4f mat, Vec3 center, double radius,
                                int sides, double angleOffset, double thickness, int col) {
        double rIn = Math.max(0.05, radius - thickness * 0.5);
        double rOut = radius + thickness * 0.5;
        for (int i = 0; i < sides; i++) {
            double a0 = angleOffset + i * (Math.PI * 2.0 / sides);
            double a1 = angleOffset + (i + 1) * (Math.PI * 2.0 / sides);
            Vec3 p0In = center.add(Math.cos(a0) * rIn, 0, Math.sin(a0) * rIn);
            Vec3 p1In = center.add(Math.cos(a1) * rIn, 0, Math.sin(a1) * rIn);
            Vec3 p1Out = center.add(Math.cos(a1) * rOut, 0, Math.sin(a1) * rOut);
            Vec3 p0Out = center.add(Math.cos(a0) * rOut, 0, Math.sin(a0) * rOut);
            YangJianVisuals.quad(v, mat, p0In, p1In, p1Out, p0Out, col, col, col, col);
        }
    }

    private static void ribbonSegment(VertexConsumer v, Matrix4f mat, Vec3 a, Vec3 b, Vec3 side, Vec3 up, double halfW, int col) {
        Vec3 s = side.scale(halfW);
        Vec3 u = up.scale(halfW);
        YangJianVisuals.quad(v, mat, a.subtract(s), b.subtract(s), b.add(s), a.add(s), col, col, col, col);
        YangJianVisuals.quad(v, mat, a.subtract(u), b.subtract(u), b.add(u), a.add(u), col, col, col, col);
    }

    /**
     * Cinematic world-space Divine Dragon arena entrance:
     *   1) Billowing thunderhead storm ceiling & swirling cloud-mist curtains around the ancient Sakura Tree
     *   2) Continuous sky-to-cloud-sea forked lightning bolts accelerating toward the center
     *   3) Gathering storm arcs snapping into the Sakura Tree crown right before impact
     *   4) Colossal multi-layered Mega-Lightning Pillar splitting the center Sakura Tree -> blinding starburst & shockwave dome
     *   5) Expanding celadon-jade wind rings as the revealed Divine Dragon roars skyward and clears the mist!
     */
    static void introStormAndMegaBolt(VertexConsumer v, Matrix4f mat, Vec3 floorCenter, float introAge,
                                      float mist, float skyFlash, int seed) {
        Vec3 treeCrown = floorCenter.add(0.0, 21.0, 0.0);

        // 1. Storm cloud canopy & swirling mist curtains enveloping the Sakura Tree (云雾缭绕)
        if (mist > 0.015F) {
            renderIntroThunderheads(v, mat, floorCenter, introAge, mist, skyFlash);
            renderIntroMistCurtains(v, mat, floorCenter, introAge, mist, skyFlash);
        }

        // 2. Continuous crashing sky lightning bolts across Act 1 (不断有闪电劈下)
        int[] boltTicks = net.xuwu.myriadcalamity.entity.DivineDragonMath.INTRO_AMBIENT_BOLT_TICKS;
        for (int idx = 0; idx < boltTicks.length; idx++) {
            float dt = introAge - boltTicks[idx];
            if (dt < 0.0F || dt >= 11.0F) continue;
            double angle = idx * 2.399963229728653 + 0.55;
            double radius = (idx >= boltTicks.length - 3)
                ? (18.0 + (boltTicks.length - 1 - idx) * 6.0)
                : (34.0 + (idx % 4) * 12.0);
            Vec3 strikeGround = floorCenter.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            Vec3 strikeSky = strikeGround.add(Math.cos(angle + 1.2) * 12.0, 108.0, Math.sin(angle + 1.2) * 12.0);
            double fade = Math.pow(Math.max(0.0, 1.0 - dt / 11.0), 1.35);
            YangJianVisuals.Palette pal = (idx % 3 == 0) ? GOLD : STORM_WHITE;
            YangJianLightningEffects.strike(v, mat, strikeGround, 108.0, 5.2, 0.52, dt, idx, seed + idx * 43, pal, fade);
            YangJianVisuals.bolt(v, mat, strikeSky, strikeGround, 0.55, 6.2, dt * 1.9, seed + idx * 71, pal, fade * 0.92, true);
            if (dt < 7.0F) {
                double imp = Math.pow(1.0 - dt / 7.0, 1.4);
                shockwaveDome(v, mat, strikeGround, 1.6 + dt * 2.2, 1.2 + dt * 1.1, pal, imp * 0.58);
            }
        }

        // 3. Gathering storm arcs converging onto the ancient Sakura Tree crown (t = 58..90)
        if (introAge >= 58.0F && introAge < 91.5F) {
            float charge = Mth.clamp((introAge - 58.0F) / 32.0F, 0.0F, 1.0F);
            int arcCount = 4 + (int) (charge * 5.0F);
            for (int i = 0; i < arcCount; i++) {
                double a = i * (Math.PI * 2.0 / arcCount) + introAge * 0.09;
                double rOut = 48.0 - charge * 20.0;
                Vec3 skyPt = treeCrown.add(Math.cos(a) * rOut, 74.0 + Math.sin(introAge * 0.25 + i) * 8.0, Math.sin(a) * rOut);
                YangJianVisuals.bolt(v, mat, skyPt, treeCrown.add(0, 2.0, 0),
                    0.22 + charge * 0.34, 4.5, introAge * 1.7 + i * 7, seed + i * 59,
                    (i % 2 == 0) ? MEGA_GOLD : STORM_WHITE, (0.35 + charge * 0.62), true);
            }
            // Pulsing crown condensation glow atop the ancient cherry tree
            YangJianVisuals.ring(v, mat, treeCrown.add(0, 1.0, 0), 16.0 * (1.15 - charge * 0.72), 0.7 + charge * 0.8,
                YangJianVisuals.tint(MEGA_GOLD.core(), (0.30 + charge * 0.65)));
            YangJianVisuals.flare(v, mat, treeCrown.add(0, 3.0, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0),
                4.0 + charge * 15.0, 0.25 + charge * 0.70, MEGA_GOLD);
            YangJianVisuals.flare(v, mat, treeCrown.add(0, 3.0, 0), new Vec3(0, 0, 1), new Vec3(0, 1, 0),
                4.0 + charge * 15.0, 0.25 + charge * 0.70, MEGA_GOLD);
        }

        // 4. COLOSSAL MEGA-LIGHTNING PILLAR splitting the center Sakura Tree (t = 89..115)
        float megaAge = introAge - 89.0F;
        if (megaAge >= 0.0F && megaAge < 26.0F) {
            double slamIn = Mth.clamp(megaAge / 2.2, 0.0, 1.0);
            double fade = megaAge < 5.0F ? 1.0 : Math.pow(Math.max(0.0, 1.0 - (megaAge - 5.0) / 21.0), 1.45);
            Vec3 pillarTop = treeCrown.add(0.0, 165.0, 0.0);
            Vec3 pillarHit = treeCrown.add(0.0, -2.0, 0.0);
            Vec3 currentTip = pillarTop.lerp(pillarHit, slamIn);

            // Multi-layered colossal vertical plasma column (inner pure-white core + gold-cyan jacket + wide outer radiance)
            double expand = 1.0 + Math.min(1.0, megaAge / 6.0) * 0.45;
            verticalPlasmaCylinder(v, mat, currentTip, pillarTop, 4.8 * expand, 5.8 * expand,
                YangJianVisuals.tint(MEGA_GOLD.core(), 0.96 * fade));
            verticalPlasmaCylinder(v, mat, currentTip, pillarTop, 9.6 * expand, 11.5 * expand,
                YangJianVisuals.tint(MEGA_GOLD.body(), 0.62 * fade));
            verticalPlasmaCylinder(v, mat, currentTip, pillarTop, 16.5 * expand, 21.0 * expand,
                YangJianVisuals.tint(STORM_WHITE.glow(), 0.28 * fade));

            // Six massive forked lightning arcs tearing violently down the mega-pillar
            for (int i = 0; i < 6; i++) {
                double a = i * (Math.PI / 3.0) + megaAge * 0.18;
                Vec3 from = pillarTop.add(Math.cos(a) * 14.0, 0.0, Math.sin(a) * 14.0);
                Vec3 to = pillarHit.add(Math.cos(a + 0.8) * 6.5, 0.0, Math.sin(a + 0.8) * 6.5);
                YangJianVisuals.bolt(v, mat, from, to, 1.15 + (i % 2) * 0.40, 8.8,
                    megaAge * 2.4 + i * 11, seed + 900 + i * 67,
                    (i % 2 == 0) ? MEGA_GOLD : GOLD, fade * 0.96, true);
            }

            // Blinding twin-plane starburst flare right where the colossal bolt strikes the Sakura Tree!
            double flareR = 26.0 + Math.sin(Math.min(1.0, megaAge / 7.0) * Math.PI * 0.5) * 24.0;
            YangJianVisuals.flare(v, mat, treeCrown.add(0, 4.0, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0), flareR, fade, MEGA_GOLD);
            YangJianVisuals.flare(v, mat, treeCrown.add(0, 4.0, 0), new Vec3(0, 0, 1), new Vec3(0, 1, 0), flareR, fade, MEGA_GOLD);

            // Expanding colossal sky-splitting shockwave dome & cloud-sea blast rings!
            double shockR = 6.0 + megaAge * 4.2;
            double shockH = 5.0 + megaAge * 2.4;
            shockwaveDome(v, mat, floorCenter.add(0, 1.0, 0), shockR, shockH, MEGA_GOLD, fade * 0.72);
            YangJianVisuals.ring(v, mat, floorCenter.add(0, 0.4, 0), shockR * 0.96, 2.6 + megaAge * 0.18,
                YangJianVisuals.tint(MEGA_GOLD.core(), fade * 0.88));
            YangJianVisuals.ring(v, mat, floorCenter.add(0, 0.6, 0), shockR * 0.72, 1.8,
                YangJianVisuals.tint(JADE.body(), fade * 0.65));
        }

        // 5. Skyward Roar Gale Rings blowing the remaining mist apart (t = 106..152)
        float roarAge = introAge - net.xuwu.myriadcalamity.entity.DivineDragonMath.INTRO_ROAR_PEAK_TICK;
        if (roarAge >= 0.0F && roarAge < 46.0F) {
            for (int w = 0; w < 3; w++) {
                float wt = roarAge - w * 10.0F;
                if (wt < 0.0F || wt >= 26.0F) continue;
                double wf = Math.pow(1.0 - wt / 26.0, 1.3);
                double r = 14.0 + wt * 3.1;
                YangJianVisuals.ring(v, mat, floorCenter.add(0, 1.2 + wt * 0.45, 0), r, 1.4 + wt * 0.08,
                    YangJianVisuals.tint(w % 2 == 0 ? JADE.core() : SAKURA.body(), wf * 0.62));
                YangJianVisuals.ring(v, mat, treeCrown.add(0, 56.0 + wt * 0.35, 0), 8.0 + wt * 2.2, 0.85,
                    YangJianVisuals.tint(JADE.body(), wf * 0.48));
            }
        }
    }

    private static void renderIntroThunderheads(VertexConsumer v, Matrix4f mat, Vec3 floorCenter,
                                                float introAge, float mist, float skyFlash) {
        int cells = 12;
        double cellSize = 24.0;
        double drift = introAge * 0.22;
        int stride = cells + 1;
        Vec3[] pts = new Vec3[stride * stride];
        int[] cols = new int[pts.length];
        for (int z = 0; z < stride; z++) {
            for (int x = 0; x < stride; x++) {
                double px = (x - cells * 0.5) * cellSize;
                double pz = (z - cells * 0.5) * cellSize;
                double dist = Math.hypot(px, pz);
                double edgeFade = Mth.clamp((142.0 - dist) / 42.0, 0.0, 1.0);
                double wave = Math.sin((px + drift) * 0.045) * Math.cos((pz + drift * 0.6) * 0.045);
                double y = 76.0 + wave * 6.5;
                int baseRgb = 0x1E2638;
                int litRgb = 0xC8E4FF;
                double lit = Mth.clamp(skyFlash * (0.45 + 0.55 * Math.max(0.0, 1.0 - dist / 115.0)), 0.0, 1.0);
                int r = (int) Mth.lerp(lit, (baseRgb >> 16) & 255, (litRgb >> 16) & 255);
                int g = (int) Mth.lerp(lit, (baseRgb >> 8) & 255, (litRgb >> 8) & 255);
                int b = (int) Mth.lerp(lit, baseRgb & 255, litRgb & 255);
                int rgb = (r << 16) | (g << 8) | b;
                double alpha = mist * edgeFade * (0.52 + 0.22 * (wave * 0.5 + 0.5) + skyFlash * 0.16);
                pts[z * stride + x] = floorCenter.add(px, y, pz);
                cols[z * stride + x] = YangJianVisuals.tint(rgb, Math.min(0.88, alpha));
            }
        }
        for (int z = 0; z < cells; z++) {
            for (int x = 0; x < cells; x++) {
                int a = z * stride + x, b = a + 1, c = b + stride, d = a + stride;
                if (((cols[a] | cols[b] | cols[c] | cols[d]) >>> 24) == 0) continue;
                YangJianVisuals.quad(v, mat, pts[a], pts[b], pts[c], pts[d], cols[a], cols[b], cols[c], cols[d]);
            }
        }
    }

    private static void renderIntroMistCurtains(VertexConsumer v, Matrix4f mat, Vec3 floorCenter,
                                                float introAge, float mist, float skyFlash) {
        double expand = (1.0 - mist) * 32.0;
        double[] radii = {24.0 + expand * 0.7, 42.0 + expand, 64.0 + expand * 1.15};
        double[] speeds = {0.014, -0.011, 0.008};
        double[] heights = {34.0, 28.0, 22.0};
        int segs = 32;
        int mistRgb = skyFlash > 0.25F ? 0xD8EAFF : 0x9FB6CC;
        for (int ring = 0; ring < radii.length; ring++) {
            double rad = radii[ring];
            double h = heights[ring];
            double spin = introAge * speeds[ring];
            int midCol = YangJianVisuals.tint(mistRgb, mist * (0.28 - ring * 0.04 + skyFlash * 0.14));
            int zeroCol = YangJianVisuals.tint(mistRgb, 0.0);
            for (int i = 0; i < segs; i++) {
                double a0 = spin + i * (Math.PI * 2.0 / segs);
                double a1 = spin + (i + 1) * (Math.PI * 2.0 / segs);
                double bob0 = Math.sin(a0 * 3.0 + introAge * 0.06 + ring) * 3.2;
                double bob1 = Math.sin(a1 * 3.0 + introAge * 0.06 + ring) * 3.2;
                Vec3 b0 = floorCenter.add(Math.cos(a0) * rad, -1.0, Math.sin(a0) * rad);
                Vec3 b1 = floorCenter.add(Math.cos(a1) * rad, -1.0, Math.sin(a1) * rad);
                Vec3 m0 = floorCenter.add(Math.cos(a0) * rad, h * 0.42 + bob0, Math.sin(a0) * rad);
                Vec3 m1 = floorCenter.add(Math.cos(a1) * rad, h * 0.42 + bob1, Math.sin(a1) * rad);
                Vec3 t0 = floorCenter.add(Math.cos(a0) * (rad + 4.0), h + bob0, Math.sin(a0) * (rad + 4.0));
                Vec3 t1 = floorCenter.add(Math.cos(a1) * (rad + 4.0), h + bob1, Math.sin(a1) * (rad + 4.0));
                YangJianVisuals.quad(v, mat, b0, b1, m1, m0, zeroCol, zeroCol, midCol, midCol);
                YangJianVisuals.quad(v, mat, m0, m1, t1, t0, midCol, midCol, zeroCol, zeroCol);
            }
        }
    }

    private static void verticalPlasmaCylinder(VertexConsumer v, Matrix4f mat, Vec3 bottom, Vec3 top,
                                               double bottomRadius, double topRadius, int col) {
        if ((col >>> 24) == 0) return;
        int segs = 16;
        for (int i = 0; i < segs; i++) {
            double a0 = i * (Math.PI * 2.0 / segs);
            double a1 = (i + 1) * (Math.PI * 2.0 / segs);
            double c0 = Math.cos(a0), s0 = Math.sin(a0);
            double c1 = Math.cos(a1), s1 = Math.sin(a1);
            Vec3 p0 = bottom.add(c0 * bottomRadius, 0.0, s0 * bottomRadius);
            Vec3 p1 = bottom.add(c1 * bottomRadius, 0.0, s1 * bottomRadius);
            Vec3 p2 = top.add(c1 * topRadius, 0.0, s1 * topRadius);
            Vec3 p3 = top.add(c0 * topRadius, 0.0, s0 * topRadius);
            YangJianVisuals.quad(v, mat, p0, p1, p2, p3, col, col, col, col);
        }
    }
}
