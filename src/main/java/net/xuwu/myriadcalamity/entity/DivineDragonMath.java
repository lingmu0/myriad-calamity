package net.xuwu.myriadcalamity.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pure-Java combat geometry and scheduling math for the Sekiro Divine Dragon boss.
 * Verified directly by DivineDragonCombatTest without launching Minecraft.
 */
public final class DivineDragonMath {
    public static final double EXCLUSION_RADIUS = 38.0;
    public static final double EXCLUSION_SOFT_EDGE = 38.5;
    public static final double EXCLUSION_CORE_RADIUS = 33.5;
    public static final double EXCLUSION_REPEL_RADIUS = 41.0;
    public static final double ARENA_MIN_STRIKE_RADIUS = 39.0;
    public static final double ARENA_MAX_STRIKE_RADIUS = 83.5;
    public static final int DISSOLVE_DURATION_TICKS = 120;
    public static final int DISSOLVE_FADE_START_TICK = 52;
    public static final float PHASE2_HEALTH_THRESHOLD = 0.50F;
    public static final float FINALE_HEALTH_THRESHOLD = 0.20F;

    // Vanilla MC player jump clears ~1.25 blocks; keep jump-over wave hitboxes strictly <= 0.85 blocks!
    public static final float JUMPABLE_SWEEP_HEIGHT = 0.84F;
    public static final float JUMPABLE_RING_HEIGHT = 0.85F;

    // Grab / Throw skill (Sky-Bite Thunder Throw) wide straight corridor geometry
    public static final double GRAB_CORRIDOR_MIN_RANGE = 34.0;
    public static final double GRAB_CORRIDOR_MAX_RANGE = 74.0;
    public static final double GRAB_CORRIDOR_HALF_WIDTH = 5.5;
    public static final double GRAB_CORRIDOR_MAX_HEIGHT = 6.5;
    public static final double GRAB_MIN_RADIUS = 40.0;
    public static final double GRAB_MAX_RADIUS = 62.0;
    public static final double GRAB_BITE_RADIUS = 5.2;
    public static final double GRAB_BITE_MAX_HEIGHT = 5.5;
    public static final double GRAB_THROW_HEIGHT = 30.0;
    public static final double GRAB_THROW_RADIUS = 54.0;

    // Stagger Collapse weakpoint meter
    public static final float STAGGER_THRESHOLD_FRACTION = 0.12F;
    public static final int STAGGER_DURATION_TICKS = 90;
    public static final int STAGGER_COOLDOWN_TICKS = 700;

    // Wandering Cloud-Sea Tornado geometry (core hazard + benign outer updraft ring)
    public static final double TORNADO_CORE_RADIUS = 3.5;
    public static final double TORNADO_UPDRAFT_RADIUS = 6.8;
    public static final double TORNADO_HEIGHT = 28.0;

    // Curved / Orbiting 3-Wave Sakura Feather Spirit-Sword counts (all 3 waves spawn at once!)
    public static final int FEATHER_WAVES = 3;
    public static final int FEATHER_PER_WAVE_P1 = 12;
    public static final int FEATHER_PER_WAVE_P2 = 16;
    public static final int FEATHER_PER_WAVE_FINALE = 12;
    public static final int FEATHER_COUNT_P1 = FEATHER_WAVES * FEATHER_PER_WAVE_P1;         // 36
    public static final int FEATHER_COUNT_P2 = FEATHER_WAVES * FEATHER_PER_WAVE_P2;         // 48
    public static final int FEATHER_COUNT_FINALE = FEATHER_WAVES * FEATHER_PER_WAVE_FINALE; // 36

    // Two-pass 120-deg (1/3 circle) Straight Diagonal Sweeping Gale Breath geometry (exact 3D beam capsule!)
    public static final double GALE_BREATH_MIN_TARGET_RADIUS = 44.0;
    public static final double GALE_BREATH_MAX_TARGET_RADIUS = 80.0;
    public static final double GALE_BREATH_BEAM_RADIUS = 3.5;
    public static final double GALE_BREATH_SPLASH_RADIUS = 4.6;
    public static final double GALE_BREATH_SPLASH_HEIGHT = 3.4;

    // Expanded Cloud-Sea Tornado counts (with 1 guaranteed near target!)
    public static final int TORNADO_COUNT_NORMAL = 7;
    public static final int TORNADO_COUNT_LOW_HP = 9;
    public static final int TORNADO_COUNT_FINALE = 5;

    // Cinematic Arena Entrance Timeline (No countdown: Storm & Mist -> Colossal Tree Bolt + White Flash -> Skyward Roar -> Instant Combat)
    public static final int INTRO_STORM_DURATION = 90;     // 4.50s: boss concealed, mist swirling, continuous sky lightning
    public static final int INTRO_MEGA_BOLT_TICK = 90;     // 4.50s: colossal lightning pillar strikes the center Sakura Tree
    public static final int INTRO_REVEAL_TICK = 93;        // 4.65s: boss materializes inside peak blinding white flash
    public static final int INTRO_ROAR_PEAK_TICK = 106;    // 5.30s: boss bellows skyward, dispersing the storm mist
    public static final int INTRO_ROAR_DURATION = 90;      // 4.50s: full skyward roar clip (intro_awaken)
    public static final int INTRO_TOTAL_DURATION = INTRO_REVEAL_TICK + INTRO_ROAR_DURATION; // 183 ticks (9.15s) -> instant combat!
    public static final int[] INTRO_AMBIENT_BOLT_TICKS = {
        6, 15, 24, 32, 40, 47, 54, 60, 66, 71, 76, 80, 84, 87
    };

    private DivineDragonMath() {}

    public record PushResult(boolean active, boolean hardRepel, double vx, double vy, double vz, double safeX, double safeZ) {
        public static final PushResult NONE = new PushResult(false, false, 0, 0, 0, 0, 0);
    }

    public record LightningPoint(double x, double z, boolean locked) {}

    public record TornadoEffect(boolean inCore, boolean inUpdraft, double outX, double outZ) {
        public static final TornadoEffect NONE = new TornadoEffect(false, false, 0, 0);
    }

    /**
     * Computes radial outward wind repulsion for any living entity approaching the Divine Dragon.
     * Enforces long-range artillery dueling by keeping creatures outside EXCLUSION_RADIUS.
     */
    public static PushResult computeExclusionPush(double centerX, double centerZ, double entityX, double entityZ) {
        double dx = entityX - centerX;
        double dz = entityZ - centerZ;
        double dist = Math.hypot(dx, dz);
        if (dist >= EXCLUSION_SOFT_EDGE) return PushResult.NONE;

        double nx, nz;
        if (dist < 1e-4) {
            nx = 0.0;
            nz = 1.0;
        } else {
            nx = dx / dist;
            nz = dz / dist;
        }

        double safeX = centerX + nx * EXCLUSION_REPEL_RADIUS;
        double safeZ = centerZ + nz * EXCLUSION_REPEL_RADIUS;

        if (dist < EXCLUSION_CORE_RADIUS) {
            return new PushResult(true, true, nx * 1.15, 0.24, nz * 1.15, safeX, safeZ);
        }

        double penetration = (EXCLUSION_SOFT_EDGE - dist) / (EXCLUSION_SOFT_EDGE - EXCLUSION_CORE_RADIUS);
        penetration = Math.max(0.0, Math.min(1.0, penetration));
        double speed = 0.34 + 0.68 * penetration;
        return new PushResult(true, false, nx * speed, 0.08 + 0.08 * penetration, nz * speed, safeX, safeZ);
    }

    /**
     * Plans a sky-lightning barrage covering the arena with random bolts plus one bolt guaranteed
     * to lock directly onto the current target's position.
     */
    public static List<LightningPoint> planLightningBarrage(
            double centerX, double centerZ,
            double targetX, double targetZ,
            double minRadius, double maxRadius,
            int totalCount, double minSpacing, Random random) {
        List<LightningPoint> points = new ArrayList<>();
        if (totalCount <= 0) return points;

        double[] clampedTarget = clampIntoRing(centerX, centerZ, targetX, targetZ, minRadius, maxRadius);
        points.add(new LightningPoint(clampedTarget[0], clampedTarget[1], true));

        int attempts = 0;
        int maxAttempts = Math.max(60, totalCount * 28);
        while (points.size() < totalCount && attempts++ < maxAttempts) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(minRadius * minRadius + random.nextDouble() * (maxRadius * maxRadius - minRadius * minRadius));
            double px = centerX + Math.cos(angle) * r;
            double pz = centerZ + Math.sin(angle) * r;
            if (isWellSpaced(points, px, pz, minSpacing)) {
                points.add(new LightningPoint(px, pz, false));
            }
        }
        return points;
    }

    /**
     * Plans one dense sky-filling cataclysm thunder wave covering the entire arena ring.
     * Guarantees:
     *   1) At least one bolt locks onto the current target position.
     *   2) A navigable safe pocket ~6.5 blocks from the target stays clear of non-locked strikes
     *      so the dense storm remains thrilling yet fair to dodge.
     */
    public static List<LightningPoint> planSkyCataclysmWave(
            double centerX, double centerZ,
            double targetX, double targetZ,
            double minRadius, double maxRadius,
            int totalCount, double minSpacing, double safePocketRadius, Random random) {
        List<LightningPoint> points = new ArrayList<>();
        if (totalCount <= 0) return points;

        double[] lockedPos = clampIntoRing(centerX, centerZ, targetX, targetZ, minRadius, maxRadius);
        points.add(new LightningPoint(lockedPos[0], lockedPos[1], true));

        // Pick a fair escape pocket ~6.8 blocks tangentially from the player inside the combat ring
        double baseAng = Math.atan2(lockedPos[1] - centerZ, lockedPos[0] - centerX);
        double targetR = Math.hypot(lockedPos[0] - centerX, lockedPos[1] - centerZ);
        double sideSign = random.nextBoolean() ? 1.0 : -1.0;
        double safeAng = baseAng + sideSign * (6.8 / Math.max(20.0, targetR));
        double safeR = Math.max(minRadius + 2.0, Math.min(maxRadius - 2.0, targetR + (random.nextDouble() - 0.5) * 3.0));
        double safeX = centerX + Math.cos(safeAng) * safeR;
        double safeZ = centerZ + Math.sin(safeAng) * safeR;

        // First seed evenly distributed angular sectors so lightning blankets the whole sky
        int sectorCount = Math.max(8, totalCount - 1);
        double startPhase = random.nextDouble() * Math.PI * 2.0;
        for (int s = 0; s < sectorCount && points.size() < totalCount; s++) {
            for (int tries = 0; tries < 10; tries++) {
                double a = startPhase + (s + 0.15 + random.nextDouble() * 0.7) * (Math.PI * 2.0 / sectorCount);
                double rFrac = (s % 3 == 0 ? 0.15 : (s % 3 == 1 ? 0.52 : 0.84)) + (random.nextDouble() - 0.5) * 0.24;
                rFrac = Math.max(0.02, Math.min(0.98, rFrac));
                double r = minRadius + rFrac * (maxRadius - minRadius);
                double px = centerX + Math.cos(a) * r;
                double pz = centerZ + Math.sin(a) * r;
                if (Math.hypot(px - safeX, pz - safeZ) < safePocketRadius) continue;
                if (isWellSpaced(points, px, pz, minSpacing)) {
                    points.add(new LightningPoint(px, pz, false));
                    break;
                }
            }
        }

        int attempts = 0;
        while (points.size() < totalCount && attempts++ < totalCount * 20) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(minRadius * minRadius + random.nextDouble() * (maxRadius * maxRadius - minRadius * minRadius));
            double px = centerX + Math.cos(a) * r;
            double pz = centerZ + Math.sin(a) * r;
            if (Math.hypot(px - safeX, pz - safeZ) < safePocketRadius) continue;
            if (isWellSpaced(points, px, pz, minSpacing * 0.85)) {
                points.add(new LightningPoint(px, pz, false));
            }
        }
        return points;
    }

    public static double[] clampIntoRing(double cx, double cz, double x, double z, double minR, double maxR) {
        double dx = x - cx, dz = z - cz;
        double d = Math.hypot(dx, dz);
        if (d < 1e-4) return new double[]{cx, cz + (minR + maxR) * 0.5};
        double clamped = Math.max(minR, Math.min(maxR, d));
        return new double[]{cx + (dx / d) * clamped, cz + (dz / d) * clamped};
    }

    private static boolean isWellSpaced(List<LightningPoint> existing, double x, double z, double minSpacing) {
        double minSq = minSpacing * minSpacing;
        for (LightningPoint p : existing) {
            double dx = p.x() - x, dz = p.z() - z;
            if (dx * dx + dz * dz < minSq) return false;
        }
        return true;
    }

    /** Normalized [0..1] progress of the solemn neck droop during the death sequence. */
    public static float droopProgress(float dissolveTick) {
        float t = Math.max(0.0F, Math.min(1.0F, dissolveTick / 66.0F));
        return t * t * (3.0F - 2.0F * t);
    }

    /** Model opacity [1..0] during the dust-dissolve finale; stays 1.0 during the droop, then fades to 0. */
    public static float dissolveAlpha(float dissolveTick) {
        if (dissolveTick <= DISSOLVE_FADE_START_TICK) return 1.0F;
        float span = Math.max(1.0F, (DISSOLVE_DURATION_TICKS - 4) - DISSOLVE_FADE_START_TICK);
        float u = Math.max(0.0F, Math.min(1.0F, (dissolveTick - DISSOLVE_FADE_START_TICK) / span));
        float smooth = u * u * (3.0F - 2.0F * u);
        return 1.0F - smooth;
    }

    /** True while the Divine Dragon is still concealed in the pre-strike storm mist before the mega-bolt white flash. */
    public static boolean introConcealed(float introTick) {
        return introTick < INTRO_REVEAL_TICK;
    }

    /** Playback time (seconds) for the skyward roar clip (`intro_awaken`), starting cleanly at 0.0s upon reveal. */
    public static float introRoarAnimSeconds(float introTick) {
        return Math.max(0.0F, (introTick - INTRO_REVEAL_TICK) / 20.0F);
    }

    /** Full-screen blinding white flash alpha [0..1] peaking as the mega-bolt splits the Sakura Tree and reveals the boss. */
    public static float introWhiteFlashAlpha(float introTick) {
        if (introTick < 89.0F || introTick >= 122.0F) return 0.0F;
        if (introTick < INTRO_REVEAL_TICK) {
            float u = (introTick - 89.0F) / (INTRO_REVEAL_TICK - 89.0F);
            return 0.98F * u * u;
        }
        if (introTick < 100.0F) {
            float u = (introTick - INTRO_REVEAL_TICK) / (100.0F - INTRO_REVEAL_TICK);
            return 0.98F - 0.08F * u;
        }
        float u = (introTick - 100.0F) / (122.0F - 100.0F);
        float inv = Math.max(0.0F, 1.0F - u);
        return 0.90F * inv * inv;
    }

    /** Thick cloud-mist shroud density [0..1] enveloping the arena on entry and blown away by the dragon's skyward roar. */
    public static float introMistDensity(float introTick) {
        if (introTick < 0.0F || introTick >= 152.0F) return 0.0F;
        if (introTick < INTRO_MEGA_BOLT_TICK) {
            float ramp = Math.min(1.0F, introTick / 16.0F);
            return 0.78F + 0.22F * ramp;
        }
        float u = Math.max(0.0F, Math.min(1.0F, (introTick - INTRO_MEGA_BOLT_TICK) / (152.0F - INTRO_MEGA_BOLT_TICK)));
        float smooth = u * u * (3.0F - 2.0F * u);
        return 1.0F - smooth;
    }

    /** Camera tremor intensity [0..1] during ambient thunderbolts, mega-lightning impact, and skyward dragon roar. */
    public static float introCameraShake(float introTick) {
        if (introTick < 0.0F || introTick >= INTRO_TOTAL_DURATION) return 0.0F;
        float shake = 0.0F;
        // Brief distant thunder tremors for ambient bolts
        for (int bt : INTRO_AMBIENT_BOLT_TICKS) {
            float dt = introTick - bt;
            if (dt >= 0.0F && dt < 7.0F) {
                shake = Math.max(shake, 0.18F * (1.0F - dt / 7.0F));
            }
        }
        // Rising low vibration right before the colossal strike
        if (introTick >= 68.0F && introTick < INTRO_MEGA_BOLT_TICK) {
            shake = Math.max(shake, 0.22F * ((introTick - 68.0F) / (INTRO_MEGA_BOLT_TICK - 68.0F)));
        }
        // Colossal mega-bolt detonation tremor
        if (introTick >= INTRO_MEGA_BOLT_TICK && introTick < 114.0F) {
            float u = (introTick - INTRO_MEGA_BOLT_TICK) / 24.0F;
            shake = Math.max(shake, (1.0F - u) * (1.0F - u));
        }
        // Resonant dragon skyward roar vibration
        if (introTick >= INTRO_ROAR_PEAK_TICK && introTick < 156.0F) {
            float u = (introTick - INTRO_ROAR_PEAK_TICK) / 50.0F;
            float env = u < 0.15F ? (u / 0.15F) : Math.max(0.0F, 1.0F - (u - 0.15F) / 0.85F);
            shake = Math.max(shake, 0.48F * env);
        }
        return Math.min(1.0F, shake);
    }

    /** Ambient sky sheet-lightning brightness [0..1] pulsing on every storm bolt during Act 1. */
    public static float introSkyFlash(float introTick) {
        if (introTick < 0.0F || introTick >= 122.0F) return 0.0F;
        float best = introWhiteFlashAlpha(introTick);
        for (int bt : INTRO_AMBIENT_BOLT_TICKS) {
            float dt = introTick - bt;
            if (dt >= 0.0F && dt < 8.0F) {
                float pulse = (float) (0.72 * Math.exp(-dt / 2.2) + 0.34 * Math.exp(-Math.abs(dt - 3.6) * 1.4));
                best = Math.max(best, Math.min(0.85F, pulse));
            }
        }
        return best;
    }

    /** Computes oriented-box collision for a sweeping jade wind-blade wave against a target point. */
    public static boolean windBladeHits(
            double bladeX, double bladeY, double bladeZ,
            double dirX, double dirZ,
            double halfWidth, double halfHeight, double halfDepth,
            double targetX, double targetY, double targetZ, double targetRadius, double targetHeight) {
        double len = Math.hypot(dirX, dirZ);
        if (len < 1e-6) return false;
        double fx = dirX / len, fz = dirZ / len;
        double rx = -fz, rz = fx;

        double dx = targetX - bladeX;
        double dz = targetZ - bladeZ;
        double along = dx * fx + dz * fz;
        double lateral = dx * rx + dz * rz;
        if (Math.abs(along) > halfDepth + targetRadius) return false;
        if (Math.abs(lateral) > halfWidth + targetRadius) return false;

        double bladeMinY = bladeY - halfHeight;
        double bladeMaxY = bladeY + halfHeight;
        double targetMaxY = targetY + targetHeight;
        return targetMaxY >= bladeMinY && targetY <= bladeMaxY;
    }

    /** Oriented tilted-plane collision for diagonal Cross-Gale X-slashes (+/-42 deg) so players can weave through the gaps. */
    public static boolean tiltedWindBladeHits(
            double bladeX, double bladeY, double bladeZ,
            double dirX, double dirZ,
            double halfSpan, double halfThick, double halfDepth, double tiltDegrees,
            double targetX, double targetY, double targetZ, double targetRadius, double targetHeight) {
        double len = Math.hypot(dirX, dirZ);
        if (len < 1e-6) return false;
        double fx = dirX / len, fz = dirZ / len;
        double rx = -fz, rz = fx;

        double dx = targetX - bladeX;
        double dz = targetZ - bladeZ;
        double along = dx * fx + dz * fz;
        if (Math.abs(along) > halfDepth + targetRadius) return false;

        double lateral = dx * rx + dz * rz;
        double targetCenterY = targetY + targetHeight * 0.5;
        double dy = targetCenterY - bladeY;
        double rad = Math.toRadians(tiltDegrees);
        double c = Math.cos(rad), s = Math.sin(rad);
        double projSpan = lateral * c + dy * s;
        double projThick = -lateral * s + dy * c;
        double targetMargin = Math.max(targetRadius, targetHeight * 0.35);
        return Math.abs(projSpan) <= halfSpan + targetRadius
            && Math.abs(projThick) <= halfThick + targetMargin;
    }

    /** 360-degree expanding ground shockwave ring hit check (jump over ringHeight to dodge cleanly). */
    public static boolean ringWaveHits(
            double centerX, double centerZ, double floorY,
            double ringRadius, double halfThickness, double ringHeight,
            double targetX, double targetY, double targetZ, double targetRadius, double targetHeight) {
        double dist = Math.hypot(targetX - centerX, targetZ - centerZ);
        if (Math.abs(dist - ringRadius) > halfThickness + targetRadius) return false;
        double targetMaxY = targetY + targetHeight;
        return targetMaxY >= floorY - 0.3 && targetY <= floorY + ringHeight;
    }

    /** Returns true during Pass 1 (t=24..72) or Pass 2 (t=80..128) of the 2-pass 120-deg diagonal Gale Breath. */
    public static boolean isGaleBreathFiring(float actAge) {
        return (actAge >= 24.0F && actAge <= 72.0F) || (actAge >= 80.0F && actAge <= 128.0F);
    }

    /**
     * Exact 1/3-circle (120-deg) double-sweep yaw offset relative to locked center yaw:
     *   - Pass 1 (t = 24..72): sweeps +60 deg -> -60 deg (viewer's LEFT -> RIGHT)
     *   - Instant switch (t = 72..80): holds -60 deg while re-inhaling
     *   - Pass 2 (t = 80..128): immediately sweeps back -60 deg -> +60 deg (viewer's RIGHT -> LEFT)
     */
    public static double galeBreathSweepOffsetDeg(float actAge) {
        if (actAge <= 72.0F) {
            double u = Math.max(0.0, Math.min(1.0, (actAge - 24.0) / 48.0));
            double s = u * u * (3.0 - 2.0 * u);
            return 60.0 - 120.0 * s;
        }
        if (actAge <= 80.0F) {
            return -60.0;
        }
        double u = Math.max(0.0, Math.min(1.0, (actAge - 80.0) / 48.0));
        double s = u * u * (3.0 - 2.0 * u);
        return -60.0 + 120.0 * s;
    }

    /**
     * Exact 3D collision for a straight un-bent diagonal cyclone beam shooting from the dragon's animated
     * mouth (mouthX, mouthY, mouthZ) straight down to the ground impact point (endX, endY, endZ):
     *   - Tests true 3D distance between the target's vertical body segment and the 3D beam line segment!
     *   - Also checks a compact ground-impact splash ring right where the diagonal beam strikes the floor.
     *   - Players standing closer to the boss (underneath the elevated beam) or further past the impact point
     *     are NEVER hit invisibly — actual attack range matches the straight diagonal beam 1:1!
     */
    public static boolean straightDiagonalBreathHits(
            double mouthX, double mouthY, double mouthZ,
            double endX, double endY, double endZ, double floorY,
            double beamRadius, double splashRadius, double splashHeight,
            double targetX, double targetY, double targetZ, double targetRadius, double targetHeight) {
        // 1. Ground impact splash right at (endX, floorY, endZ)
        double dSplash = Math.hypot(targetX - endX, targetZ - endZ);
        if (dSplash <= splashRadius + targetRadius
                && (targetY + targetHeight) >= floorY - 0.4
                && targetY <= floorY + splashHeight) {
            return true;
        }

        // 2. Exact 3D capsule check along the straight diagonal segment (mouth -> end)
        double sx = endX - mouthX;
        double sy = endY - mouthY;
        double sz = endZ - mouthZ;
        double lenSq = sx * sx + sy * sy + sz * sz;
        if (lenSq < 1e-6) return false;

        // Sample 3 vertical points on the target's body (feet, waist, head) against the 3D beam segment
        for (int k = 0; k <= 2; k++) {
            double py = targetY + targetHeight * (k * 0.5);
            double u = ((targetX - mouthX) * sx + (py - mouthY) * sy + (targetZ - mouthZ) * sz) / lenSq;
            u = Math.max(0.0, Math.min(1.0, u));
            double bx = mouthX + sx * u;
            double by = mouthY + sy * u;
            double bz = mouthZ + sz * u;
            double dx = targetX - bx;
            double dy = py - by;
            double dz = targetZ - bz;
            double rAllow = beamRadius + targetRadius;
            if (dx * dx + dy * dy + dz * dz <= rAllow * rAllow) {
                return true;
            }
        }
        return false;
    }

    /** Computes the 3D revolving ring offset around the Divine Dragon for 3-wave orbiting spirit-swords. */
    public static double[] featherOrbitOffset(double age, int wave, int slot, int waveSize) {
        int w = Math.max(0, Math.min(FEATHER_WAVES - 1, wave));
        int n = Math.max(1, waveSize);
        double dirSign = (w == 1) ? -1.0 : 1.0;
        double baseAngle = (slot * Math.PI * 2.0 / n) + w * 0.52;
        double angle = baseAngle + dirSign * Math.max(0.0, age) * (0.115 - w * 0.012);
        double radius = 17.5 + w * 4.8;
        double yBase = 24.0 + w * 6.5;
        double tiltY = Math.sin(angle * 2.0 + w * 1.1) * 2.2;
        return new double[]{
            Math.cos(angle) * radius,
            yBase + tiltY,
            Math.sin(angle) * radius
        };
    }

    /** Returns the exact tick when a blade in (wave 0..2, slot) locks & launches from its revolving ring! */
    public static int featherWaveLockTick(int wave) {
        int w = Math.max(0, Math.min(FEATHER_WAVES - 1, wave));
        return 20 + w * 26; // Wave 0 -> t=20, Wave 1 -> t=46, Wave 2 -> t=72
    }

    public static int featherBladeReleaseTick(int wave, int slot, int waveSize) {
        int lock = featherWaveLockTick(wave);
        return lock + (slot % 4); // Crisp 0..3 tick ripple across the ring right after wave lock!
    }

    /** Oriented sweeping cyclone beam check for Sweeping Gale Breath (legacy helper). */
    public static boolean galeBreathHits(
            double originX, double originZ, double floorY,
            double beamDirX, double beamDirZ,
            double minRange, double maxRange, double halfWidth, double beamHeight,
            double targetX, double targetY, double targetZ, double targetRadius, double targetHeight) {
        double len = Math.hypot(beamDirX, beamDirZ);
        if (len < 1e-6) return false;
        double fx = beamDirX / len, fz = beamDirZ / len;
        double rx = -fz, rz = fx;
        double dx = targetX - originX;
        double dz = targetZ - originZ;
        double along = dx * fx + dz * fz;
        if (along < minRange - targetRadius || along > maxRange + targetRadius) return false;
        double lateral = Math.abs(dx * rx + dz * rz);
        if (lateral > halfWidth + targetRadius) return false;
        return (targetY + targetHeight) >= floorY - 0.5 && targetY <= floorY + beamHeight;
    }

    /** Classifies a target inside a wandering Cloud-Sea Tornado (violent wind-shear core vs helpful outer updraft). */
    public static TornadoEffect tornadoEffect(
            double tornadoX, double tornadoZ, double floorY,
            double targetX, double targetY, double targetZ) {
        if (targetY < floorY - 1.5 || targetY > floorY + TORNADO_HEIGHT) return TornadoEffect.NONE;
        double dx = targetX - tornadoX;
        double dz = targetZ - tornadoZ;
        double d = Math.hypot(dx, dz);
        if (d > TORNADO_UPDRAFT_RADIUS) return TornadoEffect.NONE;
        double outX = d < 1e-4 ? 1.0 : dx / d;
        double outZ = d < 1e-4 ? 0.0 : dz / d;
        return new TornadoEffect(d <= TORNADO_CORE_RADIUS, d > TORNADO_CORE_RADIUS, outX, outZ);
    }

    /** Exact wide straight-corridor bite grab check (sidestep laterally out of the 11-block lane to dodge). */
    public static boolean biteCorridorHits(
            double originX, double originZ, double floorY,
            double dirX, double dirZ,
            double minRange, double maxRange, double halfWidth, double maxHeight,
            double targetX, double targetY, double targetZ, double targetRadius, double targetHeight) {
        double len = Math.hypot(dirX, dirZ);
        if (len < 1e-6) return false;
        double fx = dirX / len, fz = dirZ / len;
        double rx = -fz, rz = fx;
        double dx = targetX - originX;
        double dz = targetZ - originZ;
        double along = dx * fx + dz * fz;
        if (along < minRange - targetRadius || along > maxRange + targetRadius) return false;
        double lateral = Math.abs(dx * rx + dz * rz);
        if (lateral > halfWidth + targetRadius) return false;
        return (targetY + targetHeight) >= floorY - 0.5 && targetY <= floorY + maxHeight;
    }

    /** Legacy radial bite-zone helper kept for compatibility. */
    public static boolean biteGrabHits(
            double biteX, double biteZ, double floorY,
            double targetX, double targetY, double targetZ, double targetRadius) {
        double dist = Math.hypot(targetX - biteX, targetZ - biteZ);
        return dist <= GRAB_BITE_RADIUS + targetRadius && targetY <= floorY + GRAB_BITE_MAX_HEIGHT;
    }

    /** Smooth high-arc skyward trajectory carrying a grabbed victim from the jaws to the sky-thunder lock point. */
    public static double[] computeGrabArcPosition(
            double startX, double startY, double startZ,
            double endX, double endY, double endZ, double progress) {
        double t = Math.max(0.0, Math.min(1.0, progress));
        double ease = t * t * (3.0 - 2.0 * t);
        double x = startX + (endX - startX) * ease;
        double z = startZ + (endZ - startZ) * ease;
        double arcLift = Math.sin(t * Math.PI) * 7.5;
        double y = startY + (endY - startY) * ease + arcLift;
        return new double[]{x, y, z};
    }

    /** Plans evenly spaced starting positions for wandering tornadoes, with Tornado #0 guaranteed near the target! */
    public static List<double[]> planTornadoSpawns(
            double centerX, double centerZ, double minRadius, double maxRadius, int count, Random random) {
        return planTornadoSpawns(centerX, centerZ, centerX, centerZ + (minRadius + maxRadius) * 0.5,
            minRadius, maxRadius, count, random);
    }

    public static List<double[]> planTornadoSpawns(
            double centerX, double centerZ, double targetX, double targetZ,
            double minRadius, double maxRadius, int count, Random random) {
        List<double[]> out = new ArrayList<>();
        if (count <= 0) return out;

        // Tornado #0: guaranteed right near the target (5.2..7.0 blocks offset so it pressures without instant core telefrag!)
        double[] clampedT = clampIntoRing(centerX, centerZ, targetX, targetZ, minRadius + 1.5, maxRadius - 1.5);
        double offAng = random.nextDouble() * Math.PI * 2.0;
        double offDist = 5.2 + random.nextDouble() * 1.8;
        double nearX = clampedT[0] + Math.cos(offAng) * offDist;
        double nearZ = clampedT[1] + Math.sin(offAng) * offDist;
        double[] nearClamped = clampIntoRing(centerX, centerZ, nearX, nearZ, minRadius, maxRadius);
        double targetAng = Math.atan2(nearClamped[1] - centerZ, nearClamped[0] - centerX);
        out.add(new double[]{nearClamped[0], nearClamped[1], targetAng});

        // Remaining (count - 1) tornadoes distributed across the rest of the combat ring
        for (int i = 1; i < count; i++) {
            double ang = targetAng + i * (Math.PI * 2.0 / count) + (random.nextDouble() - 0.5) * 0.28;
            double r = minRadius + (0.18 + random.nextDouble() * 0.68) * (maxRadius - minRadius);
            out.add(new double[]{centerX + Math.cos(ang) * r, centerZ + Math.sin(ang) * r, ang});
        }
        return out;
    }

    /**
     * Evaluates an outward-flaring Cubic Bezier star-petal arc:
     *   P0 = spawn origin
     *   P1 = P0 + flare                                        (initial burst strictly outward!)
     *   P2 = lerp(P0, P3, 0.58) + flare * 0.55 + (0, 6.0, 0)   (graceful wide sweeping bend)
     *   P3 = locked target position
     * Returns {x, y, z, tx, ty, tz} (position and tangent velocity). For u > 1, extends C1-smoothly along P3's tangent.
     */
    public static double[] evaluateFeatherArc(
            double p0x, double p0y, double p0z,
            double flareX, double flareY, double flareZ,
            double p3x, double p3y, double p3z,
            double u) {
        double p1x = p0x + flareX;
        double p1y = p0y + flareY;
        double p1z = p0z + flareZ;

        double p2x = p0x * 0.42 + p3x * 0.58 + flareX * 0.55;
        double p2y = p0y * 0.42 + p3y * 0.58 + flareY * 0.55 + 6.0;
        double p2z = p0z * 0.42 + p3z * 0.58 + flareZ * 0.55;

        if (u <= 0.0) {
            return new double[]{p0x, p0y, p0z, 3.0 * flareX, 3.0 * flareY, 3.0 * flareZ};
        }
        if (u > 1.0) {
            double tx = 3.0 * (p3x - p2x);
            double ty = 3.0 * (p3y - p2y);
            double tz = 3.0 * (p3z - p2z);
            double extra = u - 1.0;
            return new double[]{p3x + tx * extra, p3y + ty * extra, p3z + tz * extra, tx, ty, tz};
        }

        double m = 1.0 - u;
        double m2 = m * m;
        double u2 = u * u;
        double b0 = m2 * m;
        double b1 = 3.0 * m2 * u;
        double b2 = 3.0 * m * u2;
        double b3 = u2 * u;

        double x = b0 * p0x + b1 * p1x + b2 * p2x + b3 * p3x;
        double y = b0 * p0y + b1 * p1y + b2 * p2y + b3 * p3y;
        double z = b0 * p0z + b1 * p1z + b2 * p2z + b3 * p3z;

        double d0 = 3.0 * m2;
        double d1 = 6.0 * m * u;
        double d2 = 3.0 * u2;
        double tx = d0 * (p1x - p0x) + d1 * (p2x - p1x) + d2 * (p3x - p2x);
        double ty = d0 * (p1y - p0y) + d1 * (p2y - p1y) + d2 * (p3y - p2y);
        double tz = d0 * (p1z - p0z) + d1 * (p2z - p1z) + d2 * (p3z - p2z);
        return new double[]{x, y, z, tx, ty, tz};
    }

    /** Estimates smooth flight duration (ticks) for a curved outward-flaring feather arc. */
    public static int computeFeatherFlightTicks(
            double p0x, double p0y, double p0z,
            double flareX, double flareY, double flareZ,
            double p3x, double p3y, double p3z) {
        double prevX = p0x, prevY = p0y, prevZ = p0z;
        double arcLen = 0.0;
        for (int i = 1; i <= 8; i++) {
            double[] pt = evaluateFeatherArc(p0x, p0y, p0z, flareX, flareY, flareZ, p3x, p3y, p3z, i / 8.0);
            double dx = pt[0] - prevX, dy = pt[1] - prevY, dz = pt[2] - prevZ;
            arcLen += Math.sqrt(dx * dx + dy * dy + dz * dz);
            prevX = pt[0];
            prevY = pt[1];
            prevZ = pt[2];
        }
        return Math.max(26, Math.min(44, (int) Math.round(arcLen / 2.35)));
    }
}
