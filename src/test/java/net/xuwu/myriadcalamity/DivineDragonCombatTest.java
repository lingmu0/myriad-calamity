package net.xuwu.myriadcalamity;

import java.util.List;
import java.util.Random;
import net.xuwu.myriadcalamity.entity.DivineDragonHitboxes;
import net.xuwu.myriadcalamity.entity.DivineDragonMath;

/** Pure-Java geometry and scheduling checks for the Sekiro Divine Dragon ranged boss encounter. */
public final class DivineDragonCombatTest {
    private static int checks;

    private static void check(boolean valid, String description) {
        checks++;
        if (!valid) throw new AssertionError(description);
    }

    public static void main(String[] args) {
        // 1. Absolute wind exclusion barrier (long-range artillery duel enforcement, scaled for 2.25x Sekiro boss)
        DivineDragonMath.PushResult far = DivineDragonMath.computeExclusionPush(0.5, 0.5, 0.5, 52.5);
        check(!far.active() && !far.hardRepel(), "Players beyond the 38.5-block soft edge feel no exclusion push");

        DivineDragonMath.PushResult soft = DivineDragonMath.computeExclusionPush(0.5, 0.5, 0.5, 36.5);
        check(soft.active() && !soft.hardRepel() && soft.vz() > 0.3,
            "Approaching inside 38.5..33.5 applies outward gale pushback");

        DivineDragonMath.PushResult core = DivineDragonMath.computeExclusionPush(0.5, 0.5, 0.5, 18.5);
        check(core.active() && core.hardRepel(), "Breaching the inner core triggers hard wind repel");
        double repelDist = Math.hypot(core.safeX() - 0.5, core.safeZ() - 0.5);
        check(Math.abs(repelDist - DivineDragonMath.EXCLUSION_REPEL_RADIUS) < 1e-4,
            "Hard repel relocates intruder safely outside the 38-block exclusion zone (r = 41.0)");

        DivineDragonMath.PushResult exactCenter = DivineDragonMath.computeExclusionPush(0.5, 0.5, 0.5, 0.5);
        check(exactCenter.active() && exactCenter.hardRepel() && Double.isFinite(exactCenter.safeX()) && Double.isFinite(exactCenter.safeZ()),
            "Exact-center singularity produces finite outward repel coordinates");

        // 2. Targeted + random sky-lightning barrage (one bolt guaranteed locked onto target)
        Random rng = new Random(20261127L);
        double tx = 22.5, tz = 58.5;
        List<DivineDragonMath.LightningPoint> barrage = DivineDragonMath.planLightningBarrage(
            0.5, 0.5, tx, tz,
            DivineDragonMath.ARENA_MIN_STRIKE_RADIUS,
            DivineDragonMath.ARENA_MAX_STRIKE_RADIUS,
            13, 8.5, rng);
        check(barrage.size() >= 10, "Lightning barrage spawns dense coverage across the combat ring");
        check(barrage.get(0).locked(), "First bolt of every barrage is locked onto the active target");
        check(Math.hypot(barrage.get(0).x() - tx, barrage.get(0).z() - tz) < 1e-4,
            "Locked bolt lands directly on target when target stands in the combat ring");
        long lockedCount = barrage.stream().filter(DivineDragonMath.LightningPoint::locked).count();
        check(lockedCount == 1, "Exactly one bolt is marked target-locked per barrage wave");
        for (int i = 0; i < barrage.size(); i++) {
            DivineDragonMath.LightningPoint p = barrage.get(i);
            double r = Math.hypot(p.x() - 0.5, p.z() - 0.5);
            check(r >= DivineDragonMath.ARENA_MIN_STRIKE_RADIUS - 1e-4 && r <= DivineDragonMath.ARENA_MAX_STRIKE_RADIUS + 1e-4,
                "All lightning bolts fall within the combat ring [39.0, 83.5]");
            for (int j = i + 1; j < barrage.size(); j++) {
                double sep = Math.hypot(p.x() - barrage.get(j).x(), p.z() - barrage.get(j).z());
                check(sep >= 8.5 - 1e-4, "Barrage bolts respect minimum spacing to avoid unfair instant overlap");
            }
        }

        // 3. Phase 2 sky-filling thunder cataclysm (whole-arena blanketing + target lock)
        List<DivineDragonMath.LightningPoint> cataclysm = DivineDragonMath.planSkyCataclysmWave(
            0.5, 0.5, -26.0, 52.0,
            DivineDragonMath.ARENA_MIN_STRIKE_RADIUS,
            DivineDragonMath.ARENA_MAX_STRIKE_RADIUS,
            26, 8.0, 5.4, new Random(9911L));
        check(cataclysm.size() >= 20, "Phase 2 cataclysm wave blankets the sky with dense lightning");
        check(cataclysm.get(0).locked(), "Cataclysm wave includes a target-locked bolt");
        int quadrantsHit = 0;
        for (int q = 0; q < 4; q++) {
            final int fq = q;
            boolean any = cataclysm.stream().anyMatch(p -> {
                double dx = p.x() - 0.5, dz = p.z() - 0.5;
                return (fq == 0 && dx >= 0 && dz >= 0) || (fq == 1 && dx < 0 && dz >= 0)
                    || (fq == 2 && dx < 0 && dz < 0) || (fq == 3 && dx >= 0 && dz < 0);
            });
            if (any) quadrantsHit++;
        }
        check(quadrantsHit == 4, "Sky cataclysm spans all four quadrants of the arena ring");

        // 4. Wind-blade oriented-box hit detection (vanilla 1-block MC jump clears low sweep; grounded walks under high arc)
        double sweepHalfH = DivineDragonMath.JUMPABLE_SWEEP_HEIGHT * 0.5;
        check(DivineDragonMath.JUMPABLE_SWEEP_HEIGHT <= 0.85F,
            "Low horizontal sweep height stays <= 0.85 blocks for reliable vanilla MC 1-block jump clearance");
        check(DivineDragonMath.windBladeHits(0, 129.0 + sweepHalfH, 30.0, 0, 1, 28.0, sweepHalfH, 1.5, 5.0, 129.0, 30.2, 0.3, 1.8),
            "Low horizontal sweep strikes a grounded player anywhere across its 56-block span");
        check(!DivineDragonMath.windBladeHits(0, 129.0 + sweepHalfH, 30.0, 0, 1, 28.0, sweepHalfH, 1.5, 5.0, 130.0, 30.2, 0.3, 1.8),
            "A standard vanilla MC 1-block jump (+1.0 above floor) cleanly vaults over the low sweep wave");
        check(!DivineDragonMath.windBladeHits(0, 133.18, 30.0, 0, 1, 28.0, 2.025, 1.5, 5.0, 129.0, 30.2, 0.3, 1.8),
            "Standing grounded cleanly passes underneath the Twin-Moon high arc");
        check(DivineDragonMath.windBladeHits(0, 133.18, 30.0, 0, 1, 28.0, 2.025, 1.5, 5.0, 130.2, 30.2, 0.3, 1.8),
            "Recklessly jumping into the Twin-Moon high arc catches the player");
        check(DivineDragonMath.windBladeHits(0, 140.5, 30.0, 0, 1, 2.3, 12.0, 1.5, 0.8, 131.0, 30.0, 0.3, 1.8),
            "Towering vertical cleave hits an airborne or grounded player in its lane");
        check(!DivineDragonMath.windBladeHits(0, 140.5, 30.0, 0, 1, 2.3, 12.0, 1.5, 3.2, 129.0, 30.0, 0.3, 1.8),
            "Sidestepping out of the vertical cleave lane avoids damage");

        // 5. Solemn neck-droop & dust-dissolve death curves (no instant despawn)
        check(DivineDragonMath.droopProgress(0) == 0.0F, "Droop starts at 0");
        check(DivineDragonMath.droopProgress(33) > 0.4F && DivineDragonMath.droopProgress(33) < 0.6F,
            "Droop eases smoothly at midpoint");
        check(DivineDragonMath.droopProgress(66) == 1.0F && DivineDragonMath.droopProgress(120) == 1.0F,
            "Droop completes gently and holds bowed posture");
        check(DivineDragonMath.dissolveAlpha(0) == 1.0F && DivineDragonMath.dissolveAlpha(DivineDragonMath.DISSOLVE_FADE_START_TICK) == 1.0F,
            "Model remains fully opaque while bowing its neck before dust dissolve begins");
        float midAlpha = DivineDragonMath.dissolveAlpha(84);
        check(midAlpha > 0.25F && midAlpha < 0.75F, "Dust dissolve fades smoothly between tick 52 and 116");
        check(DivineDragonMath.dissolveAlpha(DivineDragonMath.DISSOLVE_DURATION_TICKS) == 0.0F,
            "Model dissolves completely to alpha 0 by the end of the 6-second farewell");

        // 6. Cross-Gale tilted X-slash collision (+/-42 deg)
        check(DivineDragonMath.tiltedWindBladeHits(0, 133.2, 48.0, 0, 1, 24.0, 1.1, 1.5, 42.0, 0.0, 132.0, 48.0, 0.3, 1.8),
            "Center of diagonal X-slash hits a target in the crossing zone");
        check(!DivineDragonMath.tiltedWindBladeHits(0, 133.2, 48.0, 0, 1, 24.0, 1.1, 1.5, 42.0, 12.0, 129.0, 48.0, 0.3, 1.8),
            "Stepping under the raised wing of a +42 deg diagonal slash cleanly dodges it");

        // 7. 360-deg Shockwave Gale Ring hit / vanilla 1-block jump clearance
        check(DivineDragonMath.JUMPABLE_RING_HEIGHT <= 0.85F,
            "360-deg shockwave ring height stays <= 0.85 blocks for vanilla MC 1-block jump clearance");
        check(DivineDragonMath.ringWaveHits(0.5, 0.5, 129.0, 52.0, 1.4, DivineDragonMath.JUMPABLE_RING_HEIGHT, 0.5, 129.0, 52.5, 0.3, 1.8),
            "Grounded player on the expanding shockwave perimeter is hit");
        check(!DivineDragonMath.ringWaveHits(0.5, 0.5, 129.0, 52.0, 1.4, DivineDragonMath.JUMPABLE_RING_HEIGHT, 0.5, 130.0, 52.5, 0.3, 1.8),
            "A standard vanilla MC 1-block jump (+1.0 above floor) cleanly clears the 360-deg shockwave ring");

        // 8. Sweeping Gale Breath oriented beam
        check(DivineDragonMath.galeBreathHits(0.5, 0.5, 129.0, 0.0, 1.0, 18.0, 88.0, 3.5, 7.5, 1.5, 129.0, 56.0, 0.3, 1.8),
            "Standing inside the sweeping cyclone beam takes damage");
        check(!DivineDragonMath.galeBreathHits(0.5, 0.5, 129.0, 0.0, 1.0, 18.0, 88.0, 3.5, 7.5, 9.0, 129.0, 56.0, 0.3, 1.8),
            "Sprinting outside the 3.5-block beam half-width evades the breath");

        // 9. Cloud-Sea Tornado core hazard vs benign outer updraft ring
        DivineDragonMath.TornadoEffect torCore = DivineDragonMath.tornadoEffect(20.0, 55.0, 129.0, 21.5, 129.0, 55.0);
        check(torCore.inCore() && !torCore.inUpdraft(), "Inside r<=3.5 hits violent wind-shear core");
        DivineDragonMath.TornadoEffect torUp = DivineDragonMath.tornadoEffect(20.0, 55.0, 129.0, 25.0, 129.0, 55.0);
        check(!torUp.inCore() && torUp.inUpdraft(), "Outer ring 3.5<r<=6.8 grants benign updraft lift");
        DivineDragonMath.TornadoEffect torFar = DivineDragonMath.tornadoEffect(20.0, 55.0, 129.0, 30.0, 129.0, 55.0);
        check(!torFar.inCore() && !torFar.inUpdraft(), "Outside r>6.8 feels no tornado force");
        List<double[]> torSpawns = DivineDragonMath.planTornadoSpawns(0.5, 0.5, 18.0, 58.0, 44.0, 76.0,
            DivineDragonMath.TORNADO_COUNT_NORMAL, new Random(77L));
        check(torSpawns.size() == DivineDragonMath.TORNADO_COUNT_NORMAL && DivineDragonMath.TORNADO_COUNT_NORMAL >= 7,
            "Tornado planner spawns expanded storm count (>=7)");
        double nearDist = Math.hypot(torSpawns.get(0)[0] - 18.0, torSpawns.get(0)[1] - 58.0);
        check(nearDist >= 4.0 && nearDist <= 9.5,
            "First tornado is guaranteed to spawn right near the active target's position");

        // 10. Sky-Bite Thunder Throw wide straight grab corridor (34..74 range, 11-block width) & skyward arc
        check(DivineDragonMath.biteCorridorHits(0.5, 0.5, 129.0, 0.0, 1.0,
                DivineDragonMath.GRAB_CORRIDOR_MIN_RANGE, DivineDragonMath.GRAB_CORRIDOR_MAX_RANGE,
                DivineDragonMath.GRAB_CORRIDOR_HALF_WIDTH, DivineDragonMath.GRAB_CORRIDOR_MAX_HEIGHT,
                0.5, 129.0, 36.5, 0.3, 1.8),
            "Near end of the wide straight grab corridor catches a player");
        check(DivineDragonMath.biteCorridorHits(0.5, 0.5, 129.0, 0.0, 1.0,
                DivineDragonMath.GRAB_CORRIDOR_MIN_RANGE, DivineDragonMath.GRAB_CORRIDOR_MAX_RANGE,
                DivineDragonMath.GRAB_CORRIDOR_HALF_WIDTH, DivineDragonMath.GRAB_CORRIDOR_MAX_HEIGHT,
                5.0, 129.0, 56.5, 0.3, 1.8),
            "Mid-corridor player within halfWidth=5.5 gets bitten");
        check(DivineDragonMath.biteCorridorHits(0.5, 0.5, 129.0, 0.0, 1.0,
                DivineDragonMath.GRAB_CORRIDOR_MIN_RANGE, DivineDragonMath.GRAB_CORRIDOR_MAX_RANGE,
                DivineDragonMath.GRAB_CORRIDOR_HALF_WIDTH, DivineDragonMath.GRAB_CORRIDOR_MAX_HEIGHT,
                0.5, 129.0, 72.5, 0.3, 1.8),
            "Far end of the wide straight grab corridor still catches a player");
        check(!DivineDragonMath.biteCorridorHits(0.5, 0.5, 129.0, 0.0, 1.0,
                DivineDragonMath.GRAB_CORRIDOR_MIN_RANGE, DivineDragonMath.GRAB_CORRIDOR_MAX_RANGE,
                DivineDragonMath.GRAB_CORRIDOR_HALF_WIDTH, DivineDragonMath.GRAB_CORRIDOR_MAX_HEIGHT,
                7.0, 129.0, 56.5, 0.3, 1.8),
            "Sidestepping laterally outside the 11-block wide grab corridor cleanly dodges the grab");
        check(!DivineDragonMath.biteCorridorHits(0.5, 0.5, 129.0, 0.0, 1.0,
                DivineDragonMath.GRAB_CORRIDOR_MIN_RANGE, DivineDragonMath.GRAB_CORRIDOR_MAX_RANGE,
                DivineDragonMath.GRAB_CORRIDOR_HALF_WIDTH, DivineDragonMath.GRAB_CORRIDOR_MAX_HEIGHT,
                0.5, 129.0, 78.0, 0.3, 1.8),
            "Standing beyond corridor max range (74) escapes the bite");
        double[] arcMid = DivineDragonMath.computeGrabArcPosition(0, 144, 48, 0, 159, 54, 0.5);
        check(arcMid[1] > 158.0, "Skyward grab throw arches high above the midpoint");

        // 11. Phase 2 threshold at 50% HP
        check(Math.abs(DivineDragonMath.PHASE2_HEALTH_THRESHOLD - 0.50F) < 1e-5F,
            "Phase 2 triggers cleanly at 50% max HP");

        // 12. O(1) baked animation-synced hitboxes, non-mirrored chirality, world-yaw transform, and custom breath sweep
        check(DivineDragonHitboxes.clipCount() == 22,
            "Baked hitbox tracks cover all 22 Divine Dragon animation clips");
        for (String req : new String[]{"idle", "slash_sweep", "feather_barrage", "sweeping_gale_breath", "sky_bite_throw", "finale_judgment"}) {
            check(DivineDragonHitboxes.hasClip(req), "Baked hitbox resource contains clip: " + req);
        }
        double[] chest = new double[3];
        double[] sword = new double[3];
        double[] rot90 = new double[3];
        DivineDragonHitboxes.sampleLocal("idle", 0.0F, DivineDragonHitboxes.CHEST, chest);
        DivineDragonHitboxes.sampleLocal("idle", 0.0F, DivineDragonHitboxes.SWORD, sword);
        check(Math.abs(chest[0]) < 1.5 && chest[2] > 5.0,
            "Idle chest faces dead-center forward (|X| < 1.5, +Z > 5)");
        check(sword[0] < -10.0,
            "Non-mirrored Sekiro/Blockbench chirality places Seven-Branched Sword on viewer's LEFT (X < -10)");
        DivineDragonHitboxes.sampleWorld("idle", 0.0F, DivineDragonHitboxes.CHEST, 100.0, 150.0, 200.0, 90.0F, rot90);
        check(Math.abs(rot90[0] - (100.0 - chest[2])) < 0.05 && Math.abs(rot90[2] - (200.0 + chest[0])) < 0.05,
            "sampleWorld rotates local (+Z forward, -X left) cleanly by yBodyRot = 90 deg");

        double[] mouthP1Start = new double[3];
        double[] mouthP1End = new double[3];
        double[] mouthP2Start = new double[3];
        double[] mouthP2End = new double[3];
        DivineDragonHitboxes.sampleLocal("sweeping_gale_breath", 1.2F, DivineDragonHitboxes.MOUTH, mouthP1Start);
        DivineDragonHitboxes.sampleLocal("sweeping_gale_breath", 3.6F, DivineDragonHitboxes.MOUTH, mouthP1End);
        DivineDragonHitboxes.sampleLocal("sweeping_gale_breath", 4.0F, DivineDragonHitboxes.MOUTH, mouthP2Start);
        DivineDragonHitboxes.sampleLocal("sweeping_gale_breath", 6.4F, DivineDragonHitboxes.MOUTH, mouthP2End);
        check(mouthP1Start[0] < -10.0 && mouthP1End[0] > 10.0,
            "Pass 1 of Sweeping Gale Breath sweeps 1/3 circle (+60 -> -60 deg, LEFT to RIGHT)");
        check(mouthP2Start[0] > 10.0 && mouthP2End[0] < -10.0,
            "Pass 2 immediately reverses direction (-60 -> +60 deg, RIGHT to LEFT)");
        check(Math.abs(DivineDragonMath.galeBreathSweepOffsetDeg(24.0F) - 60.0) < 1e-4
                && Math.abs(DivineDragonMath.galeBreathSweepOffsetDeg(72.0F) - (-60.0)) < 1e-4
                && Math.abs(DivineDragonMath.galeBreathSweepOffsetDeg(80.0F) - (-60.0)) < 1e-4
                && Math.abs(DivineDragonMath.galeBreathSweepOffsetDeg(128.0F) - 60.0) < 1e-4,
            "galeBreathSweepOffsetDeg covers exact 120-deg (1/3 circle) double-sweep in opposite directions");

        // Verify 1:1 straight 3D diagonal breath capsule hitbox (mouth at Y=153, Z=44 -> ground impact at Y=129.55, Z=66)
        check(!DivineDragonMath.straightDiagonalBreathHits(0.5, 153.0, 44.0, 0.5, 129.55, 66.0, 129.0,
                3.5, 4.6, 3.4, 0.5, 129.0, 48.0, 0.3, 1.8),
            "Grounded player closer to boss underneath the elevated diagonal beam (Z=48, beam at Y~148) is NOT hit invisibly");
        check(!DivineDragonMath.straightDiagonalBreathHits(0.5, 153.0, 44.0, 0.5, 129.55, 66.0, 129.0,
                3.5, 4.6, 3.4, 0.5, 129.0, 76.0, 0.3, 1.8),
            "Grounded player standing beyond the diagonal beam's floor impact point (Z=76 > 66) is NOT hit");
        check(DivineDragonMath.straightDiagonalBreathHits(0.5, 153.0, 44.0, 0.5, 129.55, 66.0, 129.0,
                3.5, 4.6, 3.4, 0.5, 129.0, 65.5, 0.3, 1.8),
            "Grounded player at the diagonal beam's floor impact zone (Z=65.5) takes breath damage");
        check(DivineDragonMath.straightDiagonalBreathHits(0.5, 153.0, 44.0, 0.5, 129.55, 66.0, 129.0,
                3.5, 4.6, 3.4, 0.5, 141.0, 55.0, 0.3, 1.8),
            "Airborne player directly inside the 3D slanted diagonal beam mid-air (Y=141, Z=55) is accurately hit");

        // 13. 3-Wave Orbiting Everblossom Spirit-Swords (3x spawn at once, 3 sequential wave locks)
        check(DivineDragonMath.FEATHER_WAVES == 3
                && DivineDragonMath.FEATHER_COUNT_P1 == 36
                && DivineDragonMath.FEATHER_COUNT_P2 == 48,
            "Feather Barrage spawns 3x spirit-swords at once across 3 orbiting waves (36 P1 / 48 P2)");
        check(DivineDragonMath.featherWaveLockTick(0) < DivineDragonMath.featherWaveLockTick(1)
                && DivineDragonMath.featherWaveLockTick(1) < DivineDragonMath.featherWaveLockTick(2),
            "Orbiting spirit-sword waves lock & fire in 3 distinct sequential volleys");
        double[] orb0 = DivineDragonMath.featherOrbitOffset(0.0, 0, 0, 12);
        double[] orb2 = DivineDragonMath.featherOrbitOffset(10.0, 2, 3, 12);
        check(Math.hypot(orb0[0], orb0[2]) > 15.0 && orb2[1] > orb0[1],
            "Three stacked spirit-sword rings revolve cleanly at ascending heights around the Divine Dragon");

        // 14. Sword-to-Wind-Blade Alignment across ALL 11 Sword Skills + Horizontal SLASH_FAN + Centered Bite
        Object[][] strikeChecks = {
            {"slash_sweep",       30},
            {"slash_vertical",    34},
            {"slash_fan",         24},
            {"slash_slam",        28},
            {"slash_wide_arc",    42},
            {"cross_gale_cleave", 21},
            {"cross_gale_cleave", 41},
            {"shockwave_ring",    25},
            {"shockwave_ring",    43},
            {"slash_combo",       27},
            {"slash_combo",       46},
            {"slash_combo",       70},
            {"twin_moon_slash",   31},
            {"twin_moon_slash",   55},
            {"tempest_barrage",   23},
            {"tempest_barrage",   46},
            {"tempest_barrage",   64},
            {"tempest_barrage",   90},
            {"tempest_barrage",   113},
            {"finale_judgment",   51},
            {"finale_judgment",   73},
            {"finale_judgment",   94},
            {"finale_judgment",   118},
            {"finale_judgment",   172},
        };
        double[] swStrike = new double[3];
        for (Object[] sc : strikeChecks) {
            String clip = (String) sc[0];
            int tick = (Integer) sc[1];
            DivineDragonHitboxes.sampleLocal(clip, tick / 20.0F, DivineDragonHitboxes.SWORD, swStrike);
            check(Math.abs(swStrike[0]) < 15.0 && swStrike[2] > 16.0,
                "Sword mid-blade aligns dead-center forward (|X| < 15, Z > 16) at " + clip + " tick " + tick
                    + " (got X=" + swStrike[0] + ", Z=" + swStrike[2] + ")");
        }

        // Verify SLASH_FAN uses a crisp horizontal slash (|dX| >> |dY| across t=22..26) instead of a vertical chop!
        double[] fanPre = new double[3];
        double[] fanPost = new double[3];
        DivineDragonHitboxes.sampleLocal("slash_fan", 22.0F / 20.0F, DivineDragonHitboxes.SWORD, fanPre);
        DivineDragonHitboxes.sampleLocal("slash_fan", 26.0F / 20.0F, DivineDragonHitboxes.SWORD, fanPost);
        double fanDx = Math.abs(fanPost[0] - fanPre[0]);
        double fanDy = Math.abs(fanPost[1] - fanPre[1]);
        check(fanPre[0] > 15.0 && fanPost[0] < -20.0 && fanDx > fanDy * 2.2,
            "slash_fan executes a wide horizontal sweep across center (|dX|=" + fanDx + " >> |dY|=" + fanDy + ")");

        // Verify Twin-Moon Hit 2 (High Arc) cleaves higher than Hit 1 (Low Floor Sweep)
        double[] tmLow = new double[3];
        double[] tmHigh = new double[3];
        DivineDragonHitboxes.sampleLocal("twin_moon_slash", 31.0F / 20.0F, DivineDragonHitboxes.SWORD, tmLow);
        DivineDragonHitboxes.sampleLocal("twin_moon_slash", 55.0F / 20.0F, DivineDragonHitboxes.SWORD, tmHigh);
        check(tmHigh[1] > tmLow[1] + 5.0,
            "Twin-Moon Hit 2 cleaves visibly higher than Hit 1 to match low-sweep vs high-arc heights");

        // Verify Sky-Bite lunge jaws aim straight down the grab corridor (|X| < 3.0, Z > 60)
        double[] biteHead = new double[3];
        DivineDragonHitboxes.sampleLocal("sky_bite_throw", 40.0F / 20.0F, DivineDragonHitboxes.HEAD, biteHead);
        check(Math.abs(biteHead[0]) < 3.0 && biteHead[2] > 60.0,
            "Sky-Bite lunge head strikes dead-center down the grab corridor");

        // 15. Cinematic Arena Entrance (Concealed Storm & Mist -> Mega-Bolt + White Flash -> Skyward Roar -> Instant Combat)
        check(DivineDragonMath.introConcealed(0.0F)
                && DivineDragonMath.introConcealed(DivineDragonMath.INTRO_MEGA_BOLT_TICK)
                && !DivineDragonMath.introConcealed(DivineDragonMath.INTRO_REVEAL_TICK),
            "Boss remains concealed during storm buildup & mega-bolt start, then materializes inside peak white flash");
        check(DivineDragonMath.introWhiteFlashAlpha(50.0F) == 0.0F
                && DivineDragonMath.introWhiteFlashAlpha(DivineDragonMath.INTRO_REVEAL_TICK) > 0.90F
                && DivineDragonMath.introWhiteFlashAlpha(130.0F) == 0.0F,
            "Full-screen white flash stays 0 pre-impact, peaks > 0.90 at boss reveal, and fades cleanly to 0");
        check(DivineDragonMath.introMistDensity(20.0F) > 0.95F
                && DivineDragonMath.introMistDensity(120.0F) > 0.10F
                && DivineDragonMath.introMistDensity(160.0F) == 0.0F,
            "Thick swirling mist shrouds the arena on entry and clears cleanly during the dragon's skyward roar");
        check(DivineDragonMath.introCameraShake(DivineDragonMath.INTRO_MEGA_BOLT_TICK) > 0.85F
                && DivineDragonMath.introCameraShake(DivineDragonMath.INTRO_ROAR_PEAK_TICK + 10) > 0.35F
                && DivineDragonMath.introCameraShake(DivineDragonMath.INTRO_TOTAL_DURATION) == 0.0F,
            "Camera shake spikes on the colossal Sakura Tree strike and reverberates during the skyward roar");
        check(DivineDragonMath.introRoarAnimSeconds(80.0F) == 0.0F
                && Math.abs(DivineDragonMath.introRoarAnimSeconds(DivineDragonMath.INTRO_TOTAL_DURATION) - 4.5F) < 1e-4F,
            "Skyward roar clip starts at 0.0s upon reveal and completes its full 4.5s length before instant combat");
        double[] introRoarHead = new double[3];
        DivineDragonHitboxes.sampleLocal("intro_awaken", 2.0F, DivineDragonHitboxes.HEAD, introRoarHead);
        check(introRoarHead[1] > 75.0,
            "intro_awaken uses the towering skyward roar posture (head_Y=" + introRoarHead[1] + " > 75)");

        System.out.println("DivineDragonCombatTest: " + checks + " checks passed");
    }
}
