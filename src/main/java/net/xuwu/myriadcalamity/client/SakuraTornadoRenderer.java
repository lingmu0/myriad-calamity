package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.entity.DivineDragonMath;
import net.xuwu.myriadcalamity.entity.SakuraTornadoEntity;
import org.joml.Matrix4f;

/**
 * Volumetric misty celadon-jade storm-funnel renderer modeled closely after the user's reference artwork:
 *   1) Luminous pale-jade inner mist funnel column with deep storm-teal recessed folds
 *   2) 8 broad overlapping diagonal helical wind-sheath curtains with feathered edges & dark-teal creases
 *   3) 10 whipped outward-peeling razor gale tongues / wind-fins along the vortex silhouette
 *   4) 12-plume flaring ground-skimming spiral wind skirt (naturally marking the r=6.8 updraft zone)
 *   5) Bright pure-white diagonal specular wind-slashes + floating white debris wind-flecks
 */
public final class SakuraTornadoRenderer extends EntityRenderer<SakuraTornadoEntity> {
    private static final int COL_DEEP_TEAL = 0x195E5B;
    private static final int COL_STORM_TEAL = 0x2F8E88;
    private static final int COL_CELADON = 0x7CE4D8;
    private static final int COL_SEAFOAM = 0xC4FAF2;
    private static final int COL_MIST_WHITE = 0xF4FFFD;

    public SakuraTornadoRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    /** Sculpted hourglass-to-flaring storm profile matching the reference tornado silhouette. */
    private static double funnelRadius(double u) {
        double baseFlare = 2.5 * Math.pow(Math.max(0.0, 1.0 - u / 0.26), 2.1);
        double column = 1.65 + 2.45 * Math.pow(u, 0.82);
        return column + baseFlare;
    }

    @Override
    public void render(SakuraTornadoEntity tornado, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = tornado.age(partial);
        float life = tornado.lifeProgress(partial);
        double fade = Math.min(1.0, age / 14.0) * (life > 0.88F ? Math.max(0.0, 1.0 - (life - 0.88F) / 0.12F) : 1.0);
        if (fade <= 0.01) return;

        VertexConsumer v = CombatEffectRenderLayer.buffer(buffers);
        Matrix4f mat = pose.last().pose();
        double height = DivineDragonMath.TORNADO_HEIGHT;
        double spin = age * 0.26;

        // 1. Volumetric Inner Mist Core Funnel (solid milky seafoam-white glow inside + storm-teal shadow folds)
        int coreRings = 16;
        int coreSegs = 20;
        for (int r = 0; r < coreRings; r++) {
            double u0 = (double) r / coreRings;
            double u1 = (double) (r + 1) / coreRings;
            double y0 = u0 * height;
            double y1 = u1 * height;
            double rad0 = funnelRadius(u0) * 0.78;
            double rad1 = funnelRadius(u1) * 0.78;
            double env0 = Math.sin(Math.pow(u0, 0.72) * Math.PI);
            double env1 = Math.sin(Math.pow(u1, 0.72) * Math.PI);

            for (int s = 0; s < coreSegs; s++) {
                double a0 = (s * Math.PI * 2.0 / coreSegs) + u0 * 4.2 - spin * 1.15;
                double a1 = ((s + 1) * Math.PI * 2.0 / coreSegs) + u0 * 4.2 - spin * 1.15;
                double a0Top = (s * Math.PI * 2.0 / coreSegs) + u1 * 4.2 - spin * 1.15;
                double a1Top = ((s + 1) * Math.PI * 2.0 / coreSegs) + u1 * 4.2 - spin * 1.15;

                double fold0 = 0.5 + 0.5 * Math.sin(s * 3.0 * Math.PI * 2.0 / coreSegs - u0 * 6.5 + spin * 0.8);
                double fold1 = 0.5 + 0.5 * Math.sin((s + 1) * 3.0 * Math.PI * 2.0 / coreSegs - u0 * 6.5 + spin * 0.8);
                int c00 = YangJianVisuals.tint(fold0 > 0.55 ? COL_MIST_WHITE : COL_STORM_TEAL, fade * env0 * (0.24 + 0.24 * fold0));
                int c01 = YangJianVisuals.tint(fold1 > 0.55 ? COL_MIST_WHITE : COL_STORM_TEAL, fade * env0 * (0.24 + 0.24 * fold1));
                int c11 = YangJianVisuals.tint(fold1 > 0.55 ? COL_SEAFOAM : COL_DEEP_TEAL, fade * env1 * (0.22 + 0.22 * fold1));
                int c10 = YangJianVisuals.tint(fold0 > 0.55 ? COL_SEAFOAM : COL_DEEP_TEAL, fade * env1 * (0.22 + 0.22 * fold0));

                Vec3 p00 = new Vec3(Math.cos(a0) * rad0, y0, Math.sin(a0) * rad0);
                Vec3 p01 = new Vec3(Math.cos(a1) * rad0, y0, Math.sin(a1) * rad0);
                Vec3 p11 = new Vec3(Math.cos(a1Top) * rad1, y1, Math.sin(a1Top) * rad1);
                Vec3 p10 = new Vec3(Math.cos(a0Top) * rad1, y1, Math.sin(a0Top) * rad1);
                YangJianVisuals.quad(v, mat, p00, p01, p11, p10, c00, c01, c11, c10);
            }
        }

        // 2. Eight Broad Overlapping Diagonal Helical Wind-Sheaths (creates the layered painterly folds & dark-teal creases!)
        int sheaths = 8;
        int sheathSteps = 24;
        for (int sh = 0; sh < sheaths; sh++) {
            boolean shadowCrease = (sh % 3 == 1);
            boolean brightHighlight = (sh % 4 == 0);
            int mainRgb = brightHighlight ? COL_MIST_WHITE : (shadowCrease ? COL_DEEP_TEAL : COL_CELADON);
            int edgeRgb = shadowCrease ? COL_DEEP_TEAL : COL_STORM_TEAL;
            double basePhase = sh * (Math.PI * 2.0 / sheaths) - spin * (shadowCrease ? 1.05 : 1.32);
            double bandHeight = 3.4 + (sh % 2) * 1.1;

            for (int i = 0; i < sheathSteps; i++) {
                double u0 = (double) i / sheathSteps;
                double u1 = (double) (i + 1) / sheathSteps;
                double y0 = u0 * (height - 2.0);
                double y1 = u1 * (height - 2.0);
                double twist = 5.4;
                double a0 = basePhase + u0 * twist;
                double a1 = basePhase + u1 * twist;

                double swell0 = 1.0 + 0.12 * Math.sin(u0 * 9.0 - age * 0.35 + sh);
                double swell1 = 1.0 + 0.12 * Math.sin(u1 * 9.0 - age * 0.35 + sh);
                double r0 = funnelRadius(u0) * (shadowCrease ? 0.92 : 1.04) * swell0;
                double r1 = funnelRadius(u1) * (shadowCrease ? 0.92 : 1.04) * swell1;

                double vEnv0 = Math.sin(Math.pow(u0, 0.65) * Math.PI);
                double vEnv1 = Math.sin(Math.pow(u1, 0.65) * Math.PI);
                double peakAlpha = brightHighlight ? 0.56 : (shadowCrease ? 0.42 : 0.38);

                int midCol0 = YangJianVisuals.tint(mainRgb, fade * vEnv0 * peakAlpha);
                int midCol1 = YangJianVisuals.tint(mainRgb, fade * vEnv1 * peakAlpha);
                int zeroEdge = YangJianVisuals.tint(edgeRgb, 0.0);

                Vec3 bot0 = new Vec3(Math.cos(a0 - 0.22) * (r0 * 1.04), Math.max(0.05, y0 - bandHeight * 0.45), Math.sin(a0 - 0.22) * (r0 * 1.04));
                Vec3 bot1 = new Vec3(Math.cos(a1 - 0.22) * (r1 * 1.04), Math.max(0.05, y1 - bandHeight * 0.45), Math.sin(a1 - 0.22) * (r1 * 1.04));
                Vec3 mid0 = new Vec3(Math.cos(a0) * r0, y0, Math.sin(a0) * r0);
                Vec3 mid1 = new Vec3(Math.cos(a1) * r1, y1, Math.sin(a1) * r1);
                Vec3 top0 = new Vec3(Math.cos(a0 + 0.24) * (r0 * 0.97), y0 + bandHeight * 0.55, Math.sin(a0 + 0.24) * (r0 * 0.97));
                Vec3 top1 = new Vec3(Math.cos(a1 + 0.24) * (r1 * 0.97), y1 + bandHeight * 0.55, Math.sin(a1 + 0.24) * (r1 * 0.97));

                // Feathered two-band wind curtain (0 -> rich center fold -> 0)
                YangJianVisuals.quad(v, mat, bot0, bot1, mid1, mid0, zeroEdge, zeroEdge, midCol1, midCol0);
                YangJianVisuals.quad(v, mat, mid0, mid1, top1, top0, midCol0, midCol1, zeroEdge, zeroEdge);
            }
        }

        // 3. Ten Whipped Outward-Peeling Gale Tongues / Wind-Fins (sharp curved wisps peeling off the left/right edges!)
        for (int w = 0; w < 10; w++) {
            double uBase = ((w * 0.137 + age * 0.018) % 0.82) + 0.08;
            double startAng = w * 1.88 - spin * 1.45 + uBase * 5.0;
            int wispSegs = 6;
            for (int k = 0; k < wispSegs; k++) {
                double f0 = (double) k / wispSegs;
                double f1 = (double) (k + 1) / wispSegs;
                double u0 = uBase + f0 * 0.11;
                double u1 = uBase + f1 * 0.11;
                double r0 = funnelRadius(u0) + f0 * f0 * 2.15;
                double r1 = funnelRadius(u1) + f1 * f1 * 2.15;
                double a0 = startAng + f0 * 0.95;
                double a1 = startAng + f1 * 0.95;
                double halfH0 = (1.0 - f0) * 0.95;
                double halfH1 = (1.0 - f1) * 0.95;

                int c0 = YangJianVisuals.tint(w % 2 == 0 ? COL_SEAFOAM : COL_CELADON, fade * (1.0 - f0) * 0.48);
                int c1 = YangJianVisuals.tint(COL_CELADON, fade * (1.0 - f1) * 0.48);
                Vec3 p0B = new Vec3(Math.cos(a0) * r0, u0 * height - halfH0, Math.sin(a0) * r0);
                Vec3 p0T = new Vec3(Math.cos(a0) * r0, u0 * height + halfH0, Math.sin(a0) * r0);
                Vec3 p1T = new Vec3(Math.cos(a1) * r1, u1 * height + halfH1, Math.sin(a1) * r1);
                Vec3 p1B = new Vec3(Math.cos(a1) * r1, u1 * height - halfH1, Math.sin(a1) * r1);
                YangJianVisuals.quad(v, mat, p0B, p1B, p1T, p0T, c0, c1, c1, c0);
            }
        }

        // 4. Flaring Ground-Skimming Spiral Wind Skirt (r = 1.6 .. 6.8 updraft perimeter, sweeping low dust & mist!)
        int skirtPlumes = 12;
        int skirtSteps = 7;
        for (int p = 0; p < skirtPlumes; p++) {
            double baseA = p * (Math.PI * 2.0 / skirtPlumes) - spin * 1.25;
            for (int s = 0; s < skirtSteps; s++) {
                double f0 = (double) s / skirtSteps;
                double f1 = (double) (s + 1) / skirtSteps;
                double r0 = 1.6 + f0 * (DivineDragonMath.TORNADO_UPDRAFT_RADIUS - 1.4);
                double r1 = 1.6 + f1 * (DivineDragonMath.TORNADO_UPDRAFT_RADIUS - 1.4);
                double a0 = baseA - f0 * 1.45;
                double a1 = baseA - f1 * 1.45;
                double y0 = 2.2 * Math.pow(1.0 - f0, 1.7) + 0.06;
                double y1 = 2.2 * Math.pow(1.0 - f1, 1.7) + 0.06;
                double spread0 = 0.34 + f0 * 0.52;
                double spread1 = 0.34 + f1 * 0.52;

                int c0 = YangJianVisuals.tint(p % 3 == 0 ? COL_MIST_WHITE : COL_CELADON, fade * (1.0 - f0) * 0.44);
                int c1 = YangJianVisuals.tint(COL_STORM_TEAL, fade * (1.0 - f1) * 0.32);
                Vec3 p0L = new Vec3(Math.cos(a0 - spread0) * r0, 0.05, Math.sin(a0 - spread0) * r0);
                Vec3 p0R = new Vec3(Math.cos(a0 + spread0) * r0, y0, Math.sin(a0 + spread0) * r0);
                Vec3 p1R = new Vec3(Math.cos(a1 + spread1) * r1, y1, Math.sin(a1 + spread1) * r1);
                Vec3 p1L = new Vec3(Math.cos(a1 - spread1) * r1, 0.05, Math.sin(a1 - spread1) * r1);
                YangJianVisuals.quad(v, mat, p0L, p1L, p1R, p0R, c0, c1, c1, c0);
            }
        }

        // 5. Crisp White Diagonal Wind-Slash Highlights + 18 Orbiting White Wind-Flecks (matching reference photo details!)
        for (int h = 0; h < 5; h++) {
            double uMid = ((h * 0.19 + age * 0.025) % 0.72) + 0.12;
            double aMid = h * 2.4 - spin * 1.6 + uMid * 5.4;
            double rMid = funnelRadius(uMid) * 1.07;
            Vec3 s0 = new Vec3(Math.cos(aMid - 0.35) * rMid, (uMid - 0.05) * height, Math.sin(aMid - 0.35) * rMid);
            Vec3 s1 = new Vec3(Math.cos(aMid + 0.35) * rMid, (uMid + 0.05) * height, Math.sin(aMid + 0.35) * rMid);
            int wBright = YangJianVisuals.tint(0xFFFFFF, fade * 0.78);
            int wZero = YangJianVisuals.tint(COL_SEAFOAM, 0.0);
            YangJianVisuals.quad(v, mat, s0.add(0, -0.18, 0), s1.add(0, -0.06, 0), s1.add(0, 0.18, 0), s0.add(0, 0.06, 0),
                wZero, wBright, wZero, wBright);
        }

        int fleckCol = YangJianVisuals.tint(0xFFFFFF, fade * 0.82);
        for (int m = 0; m < 18; m++) {
            double u = ((m * 0.061 + age * 0.019) % 0.92) + 0.04;
            double r = funnelRadius(u) + 1.1 + (m % 4) * 0.75;
            double a = m * 2.39996 - spin * (1.1 + (m % 3) * 0.22);
            Vec3 c = new Vec3(Math.cos(a) * r, u * height, Math.sin(a) * r);
            Vec3 tang = new Vec3(-Math.sin(a) * 0.22, 0.16, Math.cos(a) * 0.22);
            Vec3 perp = new Vec3(Math.cos(a) * 0.07, 0.09, Math.sin(a) * 0.07);
            YangJianVisuals.quad(v, mat, c.subtract(tang), c.add(perp), c.add(tang), c.subtract(perp),
                fleckCol, fleckCol, fleckCol, fleckCol);
        }

        super.render(tornado, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SakuraTornadoEntity entity) {
        return DivineDragonRenderer.TEXTURE;
    }
}
