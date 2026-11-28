package net.xuwu.myriadcalamity.client;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.entity.DivineDragon;
import net.xuwu.myriadcalamity.entity.DivineDragonHitboxes;
import net.xuwu.myriadcalamity.entity.DivineDragonMath;
import org.joml.Matrix4f;

/**
 * Renderer for the 2.25x Sekiro Divine Dragon:
 *   - Non-mirrored 1:1 Sekiro/Blockbench orientation (Seven-Branched Sword on viewer's left)
 *   - Live swept Sword-Arc Slash Ribbons painted right off the Seven-Branched Sword during every cleave
 *   - Jaw-originating volumetric Sweeping Gale Breath cyclone cannon
 *   - Wide straight crimson-gold Grab Corridor + airborne golden celestial thunder prison
 */
public final class DivineDragonRenderer extends RiggedBossRenderer<DivineDragon> {
    public static final float MODEL_SCALE = 2.25F;
    public static final Identifier TEXTURE = MyriadCalamity.id("textures/entity/divine_dragon.png");
    private final DivineDragonModel rig;

    public DivineDragonRenderer(EntityRendererProvider.Context context) {
        super(context, 0.0F);
        rig = new DivineDragonModel(context.getResourceManager());
    }

    @Override protected void scale(State state, PoseStack pose) {
        pose.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    @Override protected float getFlipDegrees() { return 0; }

    @Override protected AABB getBoundingBoxForCulling(DivineDragon boss) {
        return boss.combatRenderBounds();
    }

    @Override public boolean shouldRender(DivineDragon boss, Frustum frustum, double x, double y, double z) {
        return boss.shouldRender(x, y, z) && frustum.isVisible(getBoundingBoxForCulling(boss));
    }

    @Override public void extractRenderState(DivineDragon boss, State state, float partial) {
        super.extractRenderState(boss, state, partial);
        state.texture = TEXTURE;
        state.effects = null;
        state.overlayMesh = null;
        state.meshType = null;
        if (boss.isIntroConcealed(partial)) {
            state.mesh = null;
            return;
        }
        state.bodyRot = Mth.rotLerp(partial, boss.yBodyRotO, boss.yBodyRot);
        boolean dissolving = boss.isDissolving();
        if (dissolving) state.meshType = RenderTypes.entityTranslucent(TEXTURE);
        rig.setupAnim(boss, partial);
        var frame = rig.snapshot();
        state.mesh = frame.body();
        if (!boss.isInvisible()) {
            state.overlayMesh = new OverlayMesh(RenderTypes.entityTranslucentCullItemTarget(TEXTURE), frame.shell());
        }
        if (!dissolving) {
            state.effects = new CombatGeometry();
            collect(boss, partial, new PoseStack(), state.effects);
        }
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.mesh != null) super.submit(state, pose, collector, camera);
    }

    private static void collect(DivineDragon boss, float partial, PoseStack pose, CombatGeometry buffers) {
        VertexConsumer v = buffers.quads();
        Matrix4f mat = pose.last().pose();
        double floorOffset = boss.isArenaBoss() ? (129.05 - boss.getY()) : -21.0;

        // 1. Sleek layered jade-white wind exclusion perimeter around the base
        int act = boss.action();
        float actAge = Math.max(0.0F, boss.actionAge(partial));
        double barrierFade = act == DivineDragon.INTRO_AWAKEN
            ? Mth.clamp((actAge - DivineDragonMath.INTRO_REVEAL_TICK) / 24.0F, 0.0F, 1.0F)
            : 1.0;
        double pulse = (0.72 + 0.28 * Math.sin((boss.tickCount + partial) * 0.08)) * barrierFade;
        Vec3 base = new Vec3(0.0, floorOffset, 0.0);
        if (pulse > 0.02) {
            YangJianVisuals.ring(v, mat, base, DivineDragonMath.EXCLUSION_RADIUS,
                0.38, YangJianVisuals.tint(DivineDragonVfx.JADE.body(), 0.26 * pulse));
            YangJianVisuals.ring(v, mat, base.add(0, 0.05, 0), DivineDragonMath.EXCLUSION_RADIUS,
                0.10, YangJianVisuals.tint(DivineDragonVfx.JADE.core(), 0.48 * pulse));
        }
        float bodyRot = Mth.rotLerp(partial, boss.yBodyRotO, boss.yBodyRot);

        // 2. Live Seven-Branched Sword slash arc ribbon during sword-swing actions (makes wind blades visibly rip off the blade!)
        if (boss.isSwordSlashAction(act) && actAge >= 8.0F) {
            renderSwordSwingTrail(v, mat, boss, act, actAge, bodyRot);
        }

        // 3. Two-Pass 120-deg Diagonal Sweeping Gale Breath (Pass 1: 24..72t, instant switch: 72..80t, Pass 2: 80..128t)
        if (act == DivineDragon.SWEEPING_GALE_BREATH && actAge >= 10.0F && actAge <= 132.0F) {
            renderGaleBreath(v, mat, boss, actAge, bodyRot, floorOffset);
        }

        // 4. Sky-Bite Thunder Throw: wide straight crimson-gold danger corridor + airborne celestial thunder prison!
        if (act == DivineDragon.SKY_BITE_THROW) {
            renderSkyBiteVisuals(v, mat, boss, actAge, floorOffset);
        }

        // 5. Stagger Collapse sacred golden head weakpoint resonance ring
        if (act == DivineDragon.STAGGER_COLLAPSE) {
            double sPulse = 0.65 + 0.35 * Math.sin(actAge * 0.35);
            Vec3 headRel = boss.headWorldOffset();
            YangJianVisuals.ring(v, mat, headRel.add(0, -1.5, 0), 7.8, 0.28,
                YangJianVisuals.tint(DivineDragonVfx.GOLD.core(), 0.65 * sPulse));
            DivineDragonVfx.drawPolygonRing(v, mat, headRel.add(0, -1.3, 0), 8.6, 6, actAge * 0.14, 0.16,
                YangJianVisuals.tint(DivineDragonVfx.GOLD.body(), 0.55 * sPulse));
        }
    }

    private static void renderSwordSwingTrail(VertexConsumer v, Matrix4f mat, DivineDragon boss,
                                              int act, float actAge, float bodyRot) {
        String clip = DivineDragon.clipNameForAction(act);
        float nowSec = actAge / 20.0F;
        double[] tmpS = new double[3];
        double[] tmpC = new double[3];
        int samples = 9;
        Vec3[] innerPts = new Vec3[samples];
        Vec3[] outerPts = new Vec3[samples];
        double totalTravel = 0.0;
        Vec3 prev = null;
        for (int i = 0; i < samples; i++) {
            float tSec = Math.max(0.0F, nowSec - 0.34F * (1.0F - (float) i / (samples - 1)));
            DivineDragonHitboxes.sampleWorld(clip, tSec, DivineDragonHitboxes.SWORD, 0, 0, 0, bodyRot, tmpS);
            DivineDragonHitboxes.sampleWorld(clip, tSec, DivineDragonHitboxes.CHEST, 0, 0, 0, bodyRot, tmpC);
            Vec3 sw = new Vec3(tmpS[0], tmpS[1], tmpS[2]);
            Vec3 ch = new Vec3(tmpC[0], tmpC[1], tmpC[2]);
            Vec3 outDir = sw.subtract(ch);
            if (outDir.lengthSqr() > 1e-4) outDir = outDir.normalize();
            else outDir = new Vec3(0, 1, 0);
            innerPts[i] = sw.subtract(outDir.scale(3.2));
            outerPts[i] = sw.add(outDir.scale(7.2));
            if (prev != null) totalTravel += sw.distanceTo(prev);
            prev = sw;
        }
        // Only flash brightly when the Seven-Branched Sword is actively whipping at high speed!
        double speedIntensity = Mth.clamp((totalTravel - 6.0) / 24.0, 0.0, 1.0);
        if (speedIntensity > 0.05) {
            DivineDragonVfx.swordSlashRibbon(v, mat, innerPts, outerPts, speedIntensity * 0.92);
        }
    }

    private static void renderGaleBreath(VertexConsumer v, Matrix4f mat, DivineDragon boss,
                                         float actAge, float bodyRot, double floorOffset) {
        double[] mouthW = new double[3];
        DivineDragonHitboxes.sampleWorld("sweeping_gale_breath", actAge / 20.0F,
            DivineDragonHitboxes.MOUTH, 0, 0, 0, bodyRot, mouthW);
        Vec3 mouthOrigin = new Vec3(mouthW[0], mouthW[1], mouthW[2]);

        double sweepYawDeg = boss.galeBreathYawDeg(actAge);
        double rad = Math.toRadians(sweepYawDeg);
        Vec3 dir = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad)).normalize();
        Vec3 endTarget = dir.scale(boss.breathTargetRadius()).add(0.0, floorOffset + 0.55, 0.0);

        // Initial charge (t=10..24) OR snappy 8-tick reversal re-inhale (t=72..80)
        if (!DivineDragonMath.isGaleBreathFiring(actAge)) {
            if (actAge > 128.0F) return;
            float charge = actAge < 24.0F
                ? Mth.clamp((actAge - 10.0F) / 14.0F, 0.0F, 1.0F)
                : Mth.clamp((actAge - 72.0F) / 8.0F, 0.25F, 1.0F);
            Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
            Vec3 up = new Vec3(0, 1, 0);
            DivineDragonVfx.orientedRing(v, mat, mouthOrigin, side, up, 4.8 * (1.15 - 0.60 * charge), 0.28,
                YangJianVisuals.tint(DivineDragonVfx.JADE.core(), 0.78 * charge));
            DivineDragonVfx.orientedRing(v, mat, mouthOrigin, side, up, 7.2 * (1.10 - 0.50 * charge), 0.20,
                YangJianVisuals.tint(DivineDragonVfx.SAKURA.body(), 0.52 * charge));
            return;
        }

        float fade;
        if (actAge <= 72.0F) {
            fade = actAge > 68.0F ? (72.0F - actAge) / 4.0F : Math.min(1.0F, (actAge - 24.0F) / 3.0F);
        } else {
            fade = actAge > 124.0F ? (128.0F - actAge) / 4.0F : Math.min(1.0F, (actAge - 80.0F) / 3.0F);
        }
        DivineDragonVfx.galeCycloneBeam(v, mat, mouthOrigin, endTarget, actAge, fade, boss.getId() + 17);
    }

    private static void renderSkyBiteVisuals(VertexConsumer v, Matrix4f mat, DivineDragon boss,
                                             float actAge, double floorOffset) {
        int gs = boss.grabState();

        // 1. Wide straight crimson-gold danger corridor (11 blocks wide, 34..74 blocks deep!)
        if (gs == DivineDragon.GRAB_TELEGRAPH && actAge <= 42.0F) {
            double rad = Math.toRadians(boss.grabCorridorYawDeg());
            Vec3 dir = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad)).normalize();
            Vec3 originFloor = new Vec3(0.0, floorOffset + 0.09, 0.0);
            DivineDragonVfx.grabDangerCorridor(v, mat, originFloor, dir,
                DivineDragonMath.GRAB_CORRIDOR_MIN_RANGE,
                DivineDragonMath.GRAB_CORRIDOR_MAX_RANGE,
                DivineDragonMath.GRAB_CORRIDOR_HALF_WIDTH,
                actAge, actAge >= 24.0F);
        }

        // 2. Airborne golden celestial thunder prison & 4 forked execution bolts (t = 56..118)
        if (gs == DivineDragon.GRAB_AIR_THUNDER && actAge >= 56.0F && actAge <= 120.0F) {
            Vec3 airRel = new Vec3(boss.grabAirX() - boss.getX(), boss.grabAirY() - boss.getY(), boss.grabAirZ() - boss.getZ());
            DivineDragonVfx.skyThunderPrison(v, mat, airRel, actAge, boss.getId() + 73);
        }
    }
}
