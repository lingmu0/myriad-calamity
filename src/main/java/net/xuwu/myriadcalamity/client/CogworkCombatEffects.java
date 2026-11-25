package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.entity.CogworkDancer;
import net.xuwu.myriadcalamity.entity.CombatMath;
import org.joml.Matrix4f;

/**
 * World-space combat VFX for the Cogwork Dancers: twin-color dash ribbons, kinetic afterimages,
 * volumetric jumpable slam shockwaves, duet clockwork mandalas, phase rewind rings, and the
 * phase-four phantom echo during a failed solo duet.
 */
final class CogworkCombatEffects {
    private record Theme(int glow, int body, int core, int accent) {}

    private static final Theme GOLD = new Theme(0xD97D1E, 0xFFD266, 0xFFFCE6, 0x52EFE4);
    private static final Theme SILVER = new Theme(0x3B8AE8, 0xA2E2FF, 0xF5FDFF, 0xFFE39E);
    private static final Vec3 X_AXIS = new Vec3(1, 0, 0);
    private static final Vec3 Z_AXIS = new Vec3(0, 0, 1);

    private CogworkCombatEffects() {}

    static void render(CogworkDancer dancer, float partial, PoseStack pose, MultiBufferSource buffers, Vec3 camera) {
        if (ShaderPackSupport.pass() == ShaderPackSupport.Pass.SHADOW) return;
        int action = dancer.action();
        if (action == CogworkDancer.IDLE) return;
        float age = dancer.scheduledActionAge(partial);
        if (age < 0) return;

        Theme theme = dancer.follower() ? SILVER : GOLD;
        Vec3 pos = new Vec3(
            Mth.lerp(partial, dancer.xOld, dancer.getX()),
            Mth.lerp(partial, dancer.yOld, dancer.getY()),
            Mth.lerp(partial, dancer.zOld, dancer.getZ()));
        int windup = dancer.attackWindup();
        float active = age - windup;

        VertexConsumer v = CombatEffectRenderLayer.buffer(buffers);
        pose.pushPose();
        switch (action) {
            case CogworkDancer.DASH -> renderDash(dancer, pos, age, windup, active, theme, pose, v, camera);
            case CogworkDancer.BARRAGE -> renderBarrage(dancer, pos, age, windup, active, theme, pose, v, camera);
            case CogworkDancer.SLAM -> renderSlam(dancer, pos, age, windup, active, theme, pose, v, camera);
            case CogworkDancer.SPIN -> renderSpin(dancer, pos, age, windup, active, theme, pose, v, camera);
            case CogworkDancer.REWIND -> renderRewind(dancer, pos, age, theme, pose, v, camera);
            case CogworkDancer.FAILED -> renderFailedDuet(dancer, pos, age, windup, active, theme, pose, v, camera);
            default -> { }
        }
        pose.popPose();
    }

    private static void renderDash(CogworkDancer dancer, Vec3 pos, float age, int windup, float active,
                                   Theme theme, PoseStack pose, VertexConsumer v, Vec3 camera) {
        Vec3 start = dancer.attackStart();
        Vec3 end = dancer.attackEnd();
        pose.translate(start.x - camera.x, start.y - camera.y, start.z - camera.z);
        Matrix4f m = pose.last().pose();
        Vec3 route = end.subtract(start);
        double length = route.horizontalDistance();
        if (length < 0.05) return;
        Vec3 dir = new Vec3(route.x / length, 0, route.z / length);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);

        if (active < 0) {
            // Gathering windup charge rings around the coiled dancer at the lane origin.
            float charge = Mth.clamp(age / Math.max(1F, windup), 0F, 1F);
            double r = 1.65 - charge * 0.95;
            double pulse = 0.78 + 0.22 * Math.sin(age * 0.65);
            ring(v, m, new Vec3(0, 0.08, 0), r, 0.055, tint(theme.body, (0.28 + 0.45 * charge) * pulse));
            ring(v, m, new Vec3(0, 0.11, 0), r * 0.65, 0.028, tint(theme.accent, 0.35 + 0.45 * charge));
            return;
        }
        if (active >= CombatMath.DASH_TRAVEL_TICKS + 6) return;

        // Takeoff shock-burst at launch point
        if (active < 7) {
            float t = active / 7F;
            double fade = (1 - t) * (1 - t);
            ring(v, m, new Vec3(0, 0.14, 0), 0.7 + t * 2.4, 0.09 * (1 - t * 0.4), tint(theme.body, fade * 0.8));
            ring(v, m, new Vec3(0, 0.16, 0), 0.55 + t * 2.0, 0.035, tint(theme.core, fade));
            verticalShockRing(v, m, dir.scale(0.6 + t * 1.4).add(0, 1.35, 0), dir, side, 0.55 + t * 1.35, 0.075,
                tint(theme.core, fade * 0.85), tint(theme.glow, fade * 0.35));
        }

        // High-speed twin-scythe energy ribbons + velocity slipstream
        double progress = Mth.clamp(active / (double) CombatMath.DASH_TRAVEL_TICKS, 0, 1);
        double fade = active <= CombatMath.DASH_TRAVEL_TICKS ? 1.0 : Math.max(0, 1 - (active - CombatMath.DASH_TRAVEL_TICKS) / 6.0);
        Vec3 head = pos.subtract(start);
        double headDist = Mth.clamp(head.dot(dir), 0, length);
        double tailDist = Math.max(0, headDist - 8.5);
        if (headDist - tailDist > 0.2) {
            dashStream(v, m, dir, side, tailDist, headDist, 1.35, 1.25, theme, fade, age);
        }
        // Kinetic air-piercing rings along the dash
        if (active < CombatMath.DASH_TRAVEL_TICKS) {
            for (int i = 0; i < 3; i++) {
                double ringDist = headDist - 0.9 - i * 2.1;
                if (ringDist < 0.2) continue;
                double rf = fade * (1 - i * 0.28);
                verticalShockRing(v, m, dir.scale(ringDist).add(0, 1.35, 0), dir, side,
                    0.82 + i * 0.22, 0.055, tint(theme.core, rf * 0.72), tint(theme.glow, rf * 0.24));
            }
            // Translucent kinetic chevron afterimages trailing behind the moving dancer
            for (int g = 1; g <= 3; g++) {
                double gd = headDist - g * 1.75;
                if (gd < 0) continue;
                double gf = (1 - g / 4.0) * 0.55 * fade;
                Vec3 at = dir.scale(gd);
                afterimageChevron(v, m, at, dir, side, theme, gf);
            }
        }
        // Brake deceleration flash at lane end
        if (progress > 0.85) {
            float bt = Mth.clamp((active - CombatMath.DASH_TRAVEL_TICKS * 0.85F) / 6F, 0F, 1F);
            double bf = (1 - bt) * fade;
            ring(v, m, dir.scale(headDist).add(0, 0.12, 0), 0.6 + bt * 1.9, 0.07, tint(theme.body, bf * 0.65));
        }
    }

    private static void renderBarrage(CogworkDancer dancer, Vec3 pos, float age, int windup, float active,
                                      Theme theme, PoseStack pose, VertexConsumer v, Vec3 camera) {
        var lanes = dancer.telegraphLanes();
        if (lanes.isEmpty()) return;
        Vec3 anchor = dancer.arenaPosition();
        pose.translate(anchor.x - camera.x, anchor.y - camera.y, anchor.z - camera.z);
        Matrix4f m = pose.last().pose();

        if (active < 0) {
            // Subtle synchronized musical pulse gliding along each lane during initial windup.
            float charge = Mth.clamp(age / Math.max(1F, windup), 0F, 1F);
            for (int pass = 0; pass < lanes.size(); pass++) {
                var lane = lanes.get(pass);
                Vec3 s = lane.start().subtract(anchor), e = lane.end().subtract(anchor);
                double sweepT = (charge * 1.4 - pass * 0.12);
                if (sweepT < 0 || sweepT > 1) continue;
                Vec3 dir = e.subtract(s);
                double len = dir.horizontalDistance();
                if (len < 0.1) continue;
                Vec3 u = new Vec3(dir.x / len, 0, dir.z / len);
                Vec3 side = new Vec3(-u.z, 0, u.x);
                Vec3 p = s.lerp(e, sweepT).add(0, 0.06, 0);
                double op = Math.sin(sweepT * Math.PI) * 0.52;
                ribbonQuad(v, m, p.subtract(u.scale(1.6)), p, side, 0.25, 0.95, tint(theme.glow, 0), tint(theme.body, op));
                ribbonQuad(v, m, p, p.add(u.scale(0.5)), side, 0.95, 0.1, tint(theme.core, op * 1.15), tint(theme.core, 0));
            }
            return;
        }

        int pass = (int) (active / CombatMath.BARRAGE_PASS_TICKS);
        float local = active - pass * CombatMath.BARRAGE_PASS_TICKS;
        if (pass < 0 || pass >= lanes.size()) return;
        var lane = lanes.get(pass);
        Vec3 s = lane.start().subtract(anchor), e = lane.end().subtract(anchor);
        Vec3 diff = e.subtract(s);
        double len = diff.horizontalDistance();
        if (len < 0.1) return;
        Vec3 dir = new Vec3(diff.x / len, 0, diff.z / len);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);

        if (local < CombatMath.BARRAGE_WARNING_TICKS) {
            // Staging flash at the upcoming lane origin
            float t = local / (float) CombatMath.BARRAGE_WARNING_TICKS;
            ring(v, m, s.add(0, 0.1, 0), 1.5 - t * 0.9, 0.06, tint(theme.accent, 0.35 + t * 0.55));
            Vec3 bodyLocal = pos.subtract(anchor).add(0, 1.2, 0);
            YangJianVisuals.wake(v, m, bodyLocal.lerp(s.add(0, 1.2, 0), 0.35), bodyLocal,
                0.04, 0.18, toPalette(theme), 0.15, 0.65);
        } else if (local < CombatMath.BARRAGE_WARNING_TICKS + CombatMath.BARRAGE_DASH_TICKS + 2) {
            // Blazing high-speed meteor slash across the active lane, disappearing right as the pass ends
            float dashAge = local - CombatMath.BARRAGE_WARNING_TICKS;
            double progress = Mth.clamp(dashAge / (double) CombatMath.BARRAGE_DASH_TICKS, 0, 1);
            double fade = dashAge <= CombatMath.BARRAGE_DASH_TICKS ? 1.0
                : Math.max(0, 1 - (dashAge - CombatMath.BARRAGE_DASH_TICKS) / 2.0);
            double headDist = progress * len;
            double tailDist = Math.max(0, headDist - Math.min(len, 11.5));
            pose.pushPose();
            pose.translate(s.x, s.y, s.z);
            Matrix4f lm = pose.last().pose();
            dashStream(v, lm, dir, side, tailDist, headDist, 1.32, 1.38, theme, fade * 1.05, age + pass * 19);
            if (dashAge < 4) {
                float lf = 1 - dashAge / 4F;
                verticalShockRing(v, lm, dir.scale(0.5 + dashAge * 0.5).add(0, 1.3, 0), dir, side,
                    0.6 + dashAge * 0.45, 0.08, tint(theme.core, lf * 0.9), tint(theme.glow, lf * 0.35));
            }
            for (int g = 1; g <= 2; g++) {
                double gd = headDist - g * 2.2;
                if (gd > 0) afterimageChevron(v, lm, dir.scale(gd), dir, side, theme, (1 - g * 0.35) * 0.6 * fade);
            }
            pose.popPose();
        }
    }

    private static void renderSlam(CogworkDancer dancer, Vec3 pos, float age, int windup, float active,
                                   Theme theme, PoseStack pose, VertexConsumer v, Vec3 camera) {
        Vec3 impact = dancer.attackEnd();
        pose.translate(impact.x - camera.x, impact.y - camera.y, impact.z - camera.z);
        Matrix4f m = pose.last().pose();
        Vec3 localBoss = pos.subtract(impact);

        if (active < 0) {
            // Subtle aerial lock-on ring + converging focus halo under the suspended dancer
            float charge = Mth.clamp(age / Math.max(1F, windup), 0F, 1F);
            double r = CombatMath.SLAM_WARNING_RADIUS * (1.0 - charge * 0.68);
            ring(v, m, new Vec3(0, 0.06, 0), r, 0.055, tint(theme.body, 0.25 + charge * 0.45));
            ring(v, m, new Vec3(0, localBoss.y + 0.4, 0), 1.35 - charge * 0.55, 0.045, tint(theme.accent, 0.3 + charge * 0.5));
            return;
        }

        if (active < 8) {
            // Plunge meteor descent streak toward the impact point
            float t = active / 8F;
            Vec3 top = new Vec3(localBoss.x, localBoss.y + 3.4, localBoss.z);
            Vec3 tip = new Vec3(localBoss.x, Math.max(0.08, localBoss.y), localBoss.z);
            YangJianVisuals.wake(v, m, top, tip, 0.06, 0.48, toPalette(theme), 0.1, 0.95);
            // Twin descending energy spirals
            for (int s = 0; s < 2; s++) {
                double a = t * Math.PI * 3 + s * Math.PI;
                Vec3 a0 = new Vec3(Math.cos(a) * 0.85, localBoss.y + 2.2, Math.sin(a) * 0.85);
                Vec3 a1 = new Vec3(Math.cos(a + 1.1) * 0.35, Math.max(0.1, localBoss.y), Math.sin(a + 1.1) * 0.35);
                YangJianVisuals.wake(v, m, a0, a1, 0.03, 0.12, toPalette(theme), 0.15, 0.85);
            }
            return;
        }

        float waveAge = active - 8F;
        // Impact flash, radial fissures, and brief kinetic light column
        if (waveAge < 11) {
            double fade = Math.pow(1 - waveAge / 11.0, 1.35);
            double spread = 0.8 + waveAge * 0.34;
            YangJianVisuals.flare(v, m, new Vec3(0, 0.12, 0), X_AXIS, Z_AXIS, spread * 1.15, fade, toPalette(theme));
            if (waveAge < 5) {
                double colFade = 1 - waveAge / 5.0;
                double colR = 0.55 + waveAge * 0.16;
                cylinder(v, m, Vec3.ZERO, colR, 6.2 * (0.45 + 0.55 * colFade), 16,
                    tint(theme.body, colFade * 0.42), tint(theme.glow, 0));
                cylinder(v, m, Vec3.ZERO, colR * 0.4, 7.2, 12,
                    tint(theme.core, colFade * 0.85), tint(theme.core, 0));
            }
            // Radial impact ground cracks / shock spokes
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4 + (dancer.follower() ? 0.39 : 0.12);
                double len = (1.6 + (i % 2) * 1.1) * (0.45 + 0.55 * Math.min(1, waveAge / 3.5));
                Vec3 near = new Vec3(Math.cos(a) * 0.3, 0.06, Math.sin(a) * 0.3);
                Vec3 far = new Vec3(Math.cos(a) * len, 0.05, Math.sin(a) * len);
                Vec3 side = new Vec3(-Math.sin(a), 0, Math.cos(a));
                ribbonQuad(v, m, near, far, side, 0.16 * fade, 0.01, tint(theme.core, fade * 0.9), tint(theme.glow, 0));
            }
        }

        // Volumetric jumpable expanding shockwave ring (matches CombatMath.SLAM_RADIUS = 8.5, WAVE_HEIGHT = 0.4)
        if (waveAge <= 20) {
            double radius = Math.min(CombatMath.SLAM_RADIUS, waveAge * 0.45);
            double waveFade = waveAge > 17 ? Math.max(0, 1 - (waveAge - 17) / 3.0) : 1.0;
            if (radius > 0.25 && waveFade > 0) {
                double halfWidth = 0.58;
                double inner = Math.max(0.05, radius - halfWidth);
                double outer = radius + halfWidth * 0.72;
                double crestY = 0.36; // Matches the server's 0.4-block jumpable wave height cleanly
                int segs = 80;
                int lowCol = tint(theme.glow, 0.14 * waveFade);
                int midCol = tint(theme.body, 0.62 * waveFade);
                int topCol = tint(theme.core, 0.94 * waveFade);
                int floorInner = tint(theme.glow, 0.04 * waveFade);
                int floorOuter = tint(theme.body, 0.36 * waveFade);
                for (int i = 0; i < segs; i++) {
                    double a = i * Math.PI * 2 / segs, b = (i + 1) * Math.PI * 2 / segs;
                    double ca = Math.cos(a), sa = Math.sin(a), cb = Math.cos(b), sb = Math.sin(b);
                    Vec3 outA = new Vec3(ca * outer, 0.055, sa * outer), outB = new Vec3(cb * outer, 0.055, sb * outer);
                    Vec3 crestA = new Vec3(ca * radius, crestY, sa * radius), crestB = new Vec3(cb * radius, crestY, sb * radius);
                    Vec3 inA = new Vec3(ca * inner, 0.055, sa * inner), inB = new Vec3(cb * inner, 0.055, sb * inner);
                    // Leading upward energy ramp
                    YangJianVisuals.quad(v, m, outA, crestA, crestB, outB, lowCol, topCol, topCol, lowCol);
                    // Trailing descending energy slope
                    YangJianVisuals.quad(v, m, crestA, inA, inB, crestB, midCol, lowCol, lowCol, midCol);
                    // Glowing ground ribbon under the wave
                    YangJianVisuals.quad(v, m,
                        new Vec3(ca * inner, 0.045, sa * inner), new Vec3(ca * outer, 0.045, sa * outer),
                        new Vec3(cb * outer, 0.045, sb * outer), new Vec3(cb * inner, 0.045, sb * inner),
                        floorInner, floorOuter, floorOuter, floorInner);
                }
                // Bright crest crown ring + crackling clockwork harmonic sparks along the rim
                ring(v, m, new Vec3(0, crestY + 0.015, 0), radius, 0.055, topCol);
                ring(v, m, new Vec3(0, 0.08, 0), outer, 0.04, tint(theme.accent, 0.55 * waveFade));
                int arcCount = 12;
                for (int i = 0; i < arcCount; i++) {
                    double a0 = i * Math.PI * 2 / arcCount + waveAge * 0.07;
                    double a1 = a0 + 0.24;
                    Vec3 p0 = new Vec3(Math.cos(a0) * radius, crestY * 0.85, Math.sin(a0) * radius);
                    Vec3 p1 = new Vec3(Math.cos(a1) * radius, crestY * 0.95, Math.sin(a1) * radius);
                    YangJianVisuals.bolt(v, m, p0, p1, 0.028, 0.14, age + i * 3,
                        dancer.getId() + i * 29, toPalette(theme), waveFade * 0.72, false);
                }
            }
        }
    }

    private static void renderSpin(CogworkDancer dancer, Vec3 pos, float age, int windup, float active,
                                   Theme theme, PoseStack pose, VertexConsumer v, Vec3 camera) {
        Vec3 center = dancer.attackEnd();
        pose.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
        Matrix4f m = pose.last().pose();

        // Lead dancer renders the shared floor clockwork mandala & volley pulses once for the pair.
        if (!dancer.follower()) {
            double intensity = active < 0 ? Mth.clamp(age / Math.max(1F, windup), 0F, 1F) * 0.45
                : active < 52 ? 1.0 : Math.max(0, 1 - (active - 52) / 22.0);
            if (intensity > 0.01) {
                clockworkMandala(v, m, CombatMath.DUET_RADIUS, age * 0.028, intensity, GOLD, SILVER);
            }
            // Radiant shock-ring synchronized with every 7-tick blade wave emission (active 16..52)
            if (active >= 16 && active < 58) {
                float sinceStart = active - 16;
                int waveIndex = (int) (sinceStart / 7F);
                if (waveIndex <= 5) {
                    float waveAge = sinceStart - waveIndex * 7F;
                    if (waveAge >= 0 && waveAge < 6.5F) {
                        double wf = Math.pow(1 - waveAge / 6.5, 1.3);
                        double r = 1.4 + waveAge * 1.45;
                        Theme wt = (waveIndex & 1) == 0 ? GOLD : SILVER;
                        ring(v, m, new Vec3(0, 1.18, 0), r, 0.08 * wf + 0.02, tint(wt.body, wf * 0.65));
                        ring(v, m, new Vec3(0, 1.2, 0), r, 0.028, tint(wt.core, wf * 0.92));
                    }
                }
            }
        }

        if (active < 0) return;

        // Flowing orbital dance ribbon trailing each dancer's scythe sweep during the waltz!
        if (active < 58) {
            double fade = active < 52 ? Math.min(1, active / 8.0) : Math.max(0, 1 - (active - 52) / 6.0);
            double baseAngle = active * 0.15 + (dancer.follower() ? Math.PI : 0);
            int samples = 18;
            double orbitR = 2.65;
            Vec3 prevIn = null, prevOut = null;
            double prevAlpha = 0;
            for (int i = 0; i <= samples; i++) {
                double frac = i / (double) samples;
                double backAngle = baseAngle - (1 - frac) * 2.1;
                double h = 1.25 + Math.sin(backAngle * 2.0) * 0.38;
                Vec3 inPt = new Vec3(Math.cos(backAngle) * (orbitR - 0.95), h - 0.12, Math.sin(backAngle) * (orbitR - 0.95));
                Vec3 outPt = new Vec3(Math.cos(backAngle) * (orbitR + 0.85), h + 0.18, Math.sin(backAngle) * (orbitR + 0.85));
                double a = frac * frac * fade;
                if (prevIn != null) {
                    YangJianVisuals.quad(v, m, prevIn, prevOut, outPt, inPt,
                        tint(theme.glow, 0), tint(theme.body, prevAlpha * 0.58),
                        tint(theme.body, a * 0.58), tint(theme.glow, 0));
                    Vec3 prevEdge = prevOut.lerp(prevIn, 0.18), curEdge = outPt.lerp(inPt, 0.18);
                    YangJianVisuals.quad(v, m, prevEdge, prevOut, outPt, curEdge,
                        tint(theme.body, prevAlpha * 0.45), tint(theme.core, prevAlpha * 0.92),
                        tint(theme.core, a * 0.92), tint(theme.body, a * 0.45));
                }
                prevIn = inPt;
                prevOut = outPt;
                prevAlpha = a;
            }
        }

        // Harmonic resonance tether between the twin dancers, blossoming during their embrace (active 52..76)
        if (!dancer.follower() && active >= 12 && active < 76) {
            double tetherFade = active < 52 ? Math.min(1, (active - 12) / 10.0) * 0.72
                : Math.sin(Mth.clamp((active - 52) / 24.0, 0, 1) * Math.PI);
            double angle = active * 0.15;
            double dist = active < 52 ? 1.5 : 0.7;
            Vec3 pA = new Vec3(Math.cos(angle) * dist, 1.45, Math.sin(angle) * dist);
            Vec3 pB = new Vec3(-Math.cos(angle) * dist, 1.45, -Math.sin(angle) * dist);
            YangJianVisuals.bolt(v, m, pA, pB, 0.04, 0.22, age, dancer.getId() + 17, toPalette(GOLD), tetherFade * 0.75, false);
            YangJianVisuals.bolt(v, m, pB, pA, 0.036, 0.22, age + 5, dancer.getId() + 91, toPalette(SILVER), tetherFade * 0.75, false);
            if (active >= 52) {
                double bloom = Math.sin(Mth.clamp((active - 52) / 24.0, 0, 1) * Math.PI);
                YangJianVisuals.flare(v, m, new Vec3(0, 1.55, 0), X_AXIS, Z_AXIS, 0.9 + bloom * 1.1, bloom * 0.85, toPalette(GOLD));
                ring(v, m, new Vec3(0, 0.12, 0), 1.2 + (active - 52) * 0.14, 0.05, tint(GOLD.core, bloom * 0.65));
            }
        }
    }

    private static void renderRewind(CogworkDancer dancer, Vec3 pos, float age,
                                     Theme theme, PoseStack pose, VertexConsumer v, Vec3 camera) {
        if (age > CombatMath.TRANSITION_TICKS) return;
        pose.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z);
        Matrix4f m = pose.last().pose();
        double progress = Mth.clamp(age / (double) CombatMath.TRANSITION_TICKS, 0, 1);
        double envelope = Math.sin(Math.min(1, progress * 1.15) * Math.PI);

        // Two counter-rotating holographic clockwork gear rings around the winding dancer
        double rOuter = 1.65 - progress * 0.25;
        clockworkRing(v, m, new Vec3(0, 1.45, 0), rOuter, 12, age * 0.14, theme.body, theme.core, envelope * 0.78);
        clockworkRing(v, m, new Vec3(0, 0.12, 0), rOuter * 1.32, 16, -age * 0.09, theme.accent, theme.core, envelope * 0.55);

        // Converging tension arcs snapping inward toward the clockwork core
        Vec3 heart = new Vec3(0, 1.55, 0);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + age * 0.18;
            double dist = 1.95 * (1 - (progress * 1.8 + i * 0.17) % 1.0) + 0.35;
            Vec3 outer = new Vec3(Math.cos(a) * dist, 1.55 + Math.sin(age * 0.2 + i) * 0.55, Math.sin(a) * dist);
            YangJianVisuals.bolt(v, m, outer, heart, 0.025, 0.18, age + i * 4,
                dancer.getId() + i * 43, toPalette(theme), envelope * 0.72, false);
        }

        // Chime-lock release burst at the end of the 3-second rewind
        if (age >= 48) {
            float bt = Mth.clamp((age - 48F) / 12F, 0F, 1F);
            double bf = (1 - bt) * (1 - bt);
            ring(v, m, new Vec3(0, 0.16, 0), 0.7 + bt * 4.8, 0.11, tint(theme.body, bf * 0.8));
            ring(v, m, new Vec3(0, 1.45, 0), 0.6 + bt * 3.6, 0.06, tint(theme.core, bf * 0.95));
        }
    }

    private static void renderFailedDuet(CogworkDancer dancer, Vec3 pos, float age, int windup, float active,
                                         Theme theme, PoseStack pose, VertexConsumer v, Vec3 camera) {
        if (active < 0 || active > 64) return;
        Vec3 center = dancer.attackEnd();
        pose.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
        Matrix4f m = pose.last().pose();
        // The missing partner's complementary color!
        Theme phantom = dancer.follower() ? GOLD : SILVER;

        if (active < 26) {
            // Fragile flickering phantom echo where the missing partner would have joined hands
            double fade = Math.min(1, active / 8.0) * (0.65 + 0.35 * Math.sin(active * 0.75));
            Vec3 partnerSpot = new Vec3(dancer.follower() ? -1.4 : 1.4, 1.55, 0);
            ring(v, m, new Vec3(partnerSpot.x, 0.12, partnerSpot.z), 1.05, 0.04, tint(phantom.body, fade * 0.45));
            // Delicate ghostly diamond silhouette echo
            diamondEcho(v, m, partnerSpot, 0.55, 1.2, phantom, fade * 0.55);
            // Half-formed broken mandala ring
            brokenArcRing(v, m, new Vec3(0, 0.08, 0), CombatMath.DUET_RADIUS * 0.55, age * 0.02, phantom, fade * 0.42);
        } else {
            // At tick 26, the phantom shatters gently into drifting ascending light motes
            float shatter = (active - 26F) / 38F;
            double fade = Math.max(0, 1 - shatter);
            fade *= fade;
            Vec3 origin = new Vec3(dancer.follower() ? -1.4 : 1.4, 1.55, 0);
            ring(v, m, new Vec3(origin.x, 0.12, origin.z), 1.05 + shatter * 2.2, 0.035, tint(phantom.body, fade * 0.38));
            for (int i = 0; i < 14; i++) {
                double a = i * Math.PI * 2 / 14 + 0.25 * i;
                double r = 0.25 + shatter * (1.4 + (i % 3) * 0.65);
                double y = origin.y - 0.5 + shatter * (1.8 + (i % 4) * 0.45);
                Vec3 mote = new Vec3(origin.x + Math.cos(a) * r, y, origin.z + Math.sin(a) * r);
                double s = 0.075 * fade;
                YangJianVisuals.strip(v, m, mote.add(0, -s, 0), mote.add(0, s, 0), X_AXIS, s, s,
                    tint(phantom.core, fade * 0.85), tint(phantom.body, fade * 0.4));
                YangJianVisuals.strip(v, m, mote.add(0, -s, 0), mote.add(0, s, 0), Z_AXIS, s, s,
                    tint(phantom.core, fade * 0.85), tint(phantom.body, fade * 0.4));
            }
        }
    }

    // =========================================================================
    // Shared Geometric Primitives for Cogwork Dancer Effects
    // =========================================================================

    private static void dashStream(VertexConsumer v, Matrix4f m, Vec3 dir, Vec3 side,
                                   double tailDist, double headDist, double yCenter, double halfSpan,
                                   Theme theme, double fade, float age) {
        Vec3 tail = dir.scale(tailDist);
        Vec3 head = dir.scale(headDist);
        // Central velocity lance beam (3-tier bloom -> body -> core)
        Vec3 cTail = tail.add(0, yCenter, 0), cHead = head.add(0, yCenter, 0);
        ribbonQuad(v, m, cTail, cHead, side, 0.14, 0.72, tint(theme.glow, 0), tint(theme.glow, 0.32 * fade));
        ribbonQuad(v, m, cTail, cHead, side, 0.06, 0.34, tint(theme.body, 0), tint(theme.body, 0.78 * fade));
        ribbonQuad(v, m, cTail, cHead, side, 0.02, 0.11, tint(theme.core, 0), tint(theme.core, 0.96 * fade));
        // Twin flanking scythe-blade slash ribbons (left and right of the dancer)
        for (int sign : new int[]{-1, 1}) {
            double hOff = sign * 0.14 * Math.sin(age * 0.55);
            Vec3 sTail = tail.add(side.scale(sign * halfSpan * 0.78)).add(0, yCenter + hOff, 0);
            Vec3 sHead = head.add(side.scale(sign * halfSpan)).add(0, yCenter + 0.18 + hOff, 0);
            Vec3 up = new Vec3(0, 1, 0);
            ribbonQuad(v, m, sTail, sHead, up, 0.05, 0.32, tint(theme.body, 0), tint(theme.body, 0.72 * fade));
            ribbonQuad(v, m, sTail, sHead, up, 0.02, 0.11, tint(theme.core, 0), tint(theme.core, 0.95 * fade));
            ribbonQuad(v, m, sTail, sHead, side, 0.03, 0.16, tint(theme.accent, 0), tint(theme.accent, 0.65 * fade));
        }
    }

    private static void afterimageChevron(VertexConsumer v, Matrix4f m, Vec3 base, Vec3 dir, Vec3 side,
                                          Theme theme, double opacity) {
        // Stylized angular automaton wing-silhouette chevron
        Vec3 head = base.add(dir.scale(0.65)).add(0, 1.45, 0);
        Vec3 back = base.subtract(dir.scale(0.45)).add(0, 1.45, 0);
        int colFront = tint(theme.core, opacity * 0.9);
        int colMid = tint(theme.body, opacity * 0.55);
        int colTail = tint(theme.glow, 0);
        for (int sign : new int[]{-1, 1}) {
            Vec3 wingTip = back.add(side.scale(sign * 1.15)).add(0, 0.55, 0);
            Vec3 lowTip = back.add(side.scale(sign * 0.75)).add(0, -0.75, 0);
            YangJianVisuals.quad(v, m, head, wingTip, back, lowTip, colFront, colMid, colTail, colMid);
        }
    }

    private static void clockworkMandala(VertexConsumer v, Matrix4f m, double radius, double angle,
                                         double opacity, Theme primary, Theme secondary) {
        Vec3 floor = new Vec3(0, 0.055, 0);
        // Outer 16-tooth rotating gilded gear ring
        clockworkRing(v, m, floor, radius * 0.96, 16, angle, primary.body, primary.core, opacity * 0.42);
        // Inner counter-rotating silver astrolabe ring
        clockworkRing(v, m, floor.add(0, 0.01, 0), radius * 0.58, 12, -angle * 1.45, secondary.body, secondary.core, opacity * 0.48);
        // 8 delicate radial harmonic spokes connecting inner & outer rings
        int spokeCol = tint(primary.core, opacity * 0.22);
        int spokeOut = tint(secondary.body, opacity * 0.16);
        for (int i = 0; i < 8; i++) {
            double a = angle + i * Math.PI / 4;
            Vec3 p0 = new Vec3(Math.cos(a) * radius * 0.22, 0.06, Math.sin(a) * radius * 0.22);
            Vec3 p1 = new Vec3(Math.cos(a) * radius * 0.95, 0.06, Math.sin(a) * radius * 0.95);
            Vec3 side = new Vec3(-Math.sin(a), 0, Math.cos(a));
            ribbonQuad(v, m, p0, p1, side, 0.055, 0.035, spokeCol, spokeOut);
        }
    }

    private static void clockworkRing(VertexConsumer v, Matrix4f m, Vec3 center, double radius,
                                      int teeth, double angle, int bodyRgb, int coreRgb, double opacity) {
        ring(v, m, center, radius, 0.065, tint(bodyRgb, opacity));
        ring(v, m, center.add(0, 0.008, 0), radius * 0.92, 0.025, tint(coreRgb, opacity * 0.85));
        int toothCol = tint(coreRgb, opacity * 0.95);
        for (int i = 0; i < teeth; i++) {
            double a = angle + i * Math.PI * 2 / teeth;
            double da = 0.048;
            double r0 = radius + 0.04, r1 = radius + 0.32;
            YangJianVisuals.quad(v, m,
                center.add(Math.cos(a - da) * r0, 0, Math.sin(a - da) * r0),
                center.add(Math.cos(a - da * 0.7) * r1, 0, Math.sin(a - da * 0.7) * r1),
                center.add(Math.cos(a + da * 0.7) * r1, 0, Math.sin(a + da * 0.7) * r1),
                center.add(Math.cos(a + da) * r0, 0, Math.sin(a + da) * r0),
                toothCol);
        }
    }

    private static void brokenArcRing(VertexConsumer v, Matrix4f m, Vec3 center, double radius,
                                      double angle, Theme theme, double opacity) {
        int arcs = 5;
        int col = tint(theme.body, opacity);
        for (int k = 0; k < arcs; k++) {
            double start = angle + k * Math.PI * 2 / arcs;
            double span = Math.PI * 2 / arcs * 0.58;
            for (int i = 0; i < 8; i++) {
                double a = start + span * i / 8, b = start + span * (i + 1) / 8;
                YangJianVisuals.quad(v, m,
                    center.add(Math.cos(a) * (radius - 0.045), 0, Math.sin(a) * (radius - 0.045)),
                    center.add(Math.cos(a) * (radius + 0.045), 0, Math.sin(a) * (radius + 0.045)),
                    center.add(Math.cos(b) * (radius + 0.045), 0, Math.sin(b) * (radius + 0.045)),
                    center.add(Math.cos(b) * (radius - 0.045), 0, Math.sin(b) * (radius - 0.045)),
                    col);
            }
        }
    }

    private static void diamondEcho(VertexConsumer v, Matrix4f m, Vec3 center, double w, double h,
                                    Theme theme, double opacity) {
        Vec3 top = center.add(0, h, 0), bot = center.add(0, -h * 0.85, 0);
        int edge = tint(theme.body, opacity * 0.35), core = tint(theme.core, opacity * 0.85);
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2, b = (i + 1) * Math.PI / 2;
            Vec3 pA = center.add(Math.cos(a) * w, 0, Math.sin(a) * w);
            Vec3 pB = center.add(Math.cos(b) * w, 0, Math.sin(b) * w);
            YangJianVisuals.quad(v, m, top, pA, pB, pB, core, edge, edge, edge);
            YangJianVisuals.quad(v, m, bot, pB, pA, pA, edge, edge, edge, edge);
        }
    }

    private static void verticalShockRing(VertexConsumer v, Matrix4f m, Vec3 center, Vec3 axis, Vec3 side,
                                          double radius, double thickness, int coreCol, int glowCol) {
        Vec3 up = new Vec3(0, 1, 0);
        int segs = 28;
        for (int i = 0; i < segs; i++) {
            double a = i * Math.PI * 2 / segs, b = (i + 1) * Math.PI * 2 / segs;
            Vec3 dirA = side.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            Vec3 dirB = side.scale(Math.cos(b)).add(up.scale(Math.sin(b)));
            Vec3 inA = center.add(dirA.scale(Math.max(0.05, radius - thickness)));
            Vec3 outA = center.add(dirA.scale(radius + thickness));
            Vec3 outB = center.add(dirB.scale(radius + thickness));
            Vec3 inB = center.add(dirB.scale(Math.max(0.05, radius - thickness)));
            YangJianVisuals.quad(v, m, inA, outA, outB, inB, coreCol, glowCol, glowCol, coreCol);
        }
    }

    private static void cylinder(VertexConsumer v, Matrix4f m, Vec3 base, double radius, double height,
                                 int sides, int bottomCol, int topCol) {
        for (int i = 0; i < sides; i++) {
            double a = i * Math.PI * 2 / sides, b = (i + 1) * Math.PI * 2 / sides;
            Vec3 a0 = base.add(Math.cos(a) * radius, 0.05, Math.sin(a) * radius);
            Vec3 b0 = base.add(Math.cos(b) * radius, 0.05, Math.sin(b) * radius);
            YangJianVisuals.quad(v, m, a0, b0, b0.add(0, height, 0), a0.add(0, height, 0),
                bottomCol, bottomCol, topCol, topCol);
        }
    }

    private static void ribbonQuad(VertexConsumer v, Matrix4f m, Vec3 from, Vec3 to, Vec3 lateral,
                                   double fromW, double toW, int fromCol, int toCol) {
        YangJianVisuals.strip(v, m, from, to, lateral, fromW, toW, fromCol, toCol);
    }

    private static void ring(VertexConsumer v, Matrix4f m, Vec3 center, double radius, double width, int color) {
        YangJianVisuals.ring(v, m, center, radius, width, color);
    }

    private static YangJianVisuals.Palette toPalette(Theme t) {
        return new YangJianVisuals.Palette(t.glow, t.body, t.core);
    }

    private static int tint(int rgb, double alpha) {
        return YangJianVisuals.tint(rgb, alpha);
    }
}
