package net.xuwu.myriadcalamity.client;

import net.minecraft.world.phys.AABB;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.entity.SakuraRingWave;
import org.joml.Matrix4f;

/** High-poly 96-segment cinematic 360-deg jumpable shockwave ring with jade plasma crest and sakura aurora trail. */
public final class SakuraRingWaveRenderer extends CombatEntityRenderer<SakuraRingWave> {
    public SakuraRingWaveRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    protected void collect(SakuraRingWave wave, float partial, PoseStack pose, CombatGeometry buffers, int light) {
        float life = wave.lifeProgress(partial);
        double fade = life > 0.80F ? Math.max(0.0, 1.0 - (life - 0.80F) / 0.20F) : Math.min(1.0, life * 14.0);
        if (fade <= 0.01) return;

        float age = wave.age(partial);
        double r = wave.currentRadius(partial);
        double h = wave.ringHeight(); // 0.85 blocks (crisp vanilla MC 1-block jump clearance!)
        double trail = 3.2;
        int segs = 96;

        VertexConsumer v = buffers.quads();
        Matrix4f mat = pose.last().pose();

        int coreCol = YangJianVisuals.tint(DivineDragonVfx.JADE.core(), fade * 0.90);
        int bodyCol = YangJianVisuals.tint(DivineDragonVfx.JADE.body(), fade * 0.46);
        int glowCol = YangJianVisuals.tint(DivineDragonVfx.JADE.glow(), fade * 0.16);
        int zeroCol = YangJianVisuals.tint(DivineDragonVfx.JADE.glow(), 0.0);
        int sakCol = YangJianVisuals.tint(DivineDragonVfx.SAKURA.body(), fade * 0.34);
        int sakZero = YangJianVisuals.tint(DivineDragonVfx.SAKURA.glow(), 0.0);

        for (int i = 0; i < segs; i++) {
            double a0 = i * (Math.PI * 2.0 / segs);
            double a1 = (i + 1) * (Math.PI * 2.0 / segs);
            double c0 = Math.cos(a0), s0 = Math.sin(a0);
            double c1 = Math.cos(a1), s1 = Math.sin(a1);
            double ripple0 = 0.06 * Math.sin(a0 * 12.0 - age * 0.5);
            double ripple1 = 0.06 * Math.sin(a1 * 12.0 - age * 0.5);

            Vec3 leadBot0 = new Vec3(c0 * r, 0.05, s0 * r);
            Vec3 leadBot1 = new Vec3(c1 * r, 0.05, s1 * r);
            Vec3 leadTop0 = new Vec3(c0 * (r - 0.14), h * 0.85 + ripple0, s0 * (r - 0.14));
            Vec3 leadTop1 = new Vec3(c1 * (r - 0.14), h * 0.85 + ripple1, s1 * (r - 0.14));

            double midR = Math.max(1.0, r - trail * 0.38);
            double tailR = Math.max(0.5, r - trail);
            double sakR = Math.max(0.5, r - trail * 1.28);
            Vec3 midBot0 = new Vec3(c0 * midR, 0.06, s0 * midR);
            Vec3 midBot1 = new Vec3(c1 * midR, 0.06, s1 * midR);
            Vec3 midTop0 = new Vec3(c0 * midR, h * 0.45, s0 * midR);
            Vec3 midTop1 = new Vec3(c1 * midR, h * 0.45, s1 * midR);
            Vec3 tailBot0 = new Vec3(c0 * tailR, 0.04, s0 * tailR);
            Vec3 tailBot1 = new Vec3(c1 * tailR, 0.04, s1 * tailR);
            Vec3 sakTail0 = new Vec3(c0 * sakR, 0.12 + Math.abs(ripple0) * 1.5, s0 * sakR);
            Vec3 sakTail1 = new Vec3(c1 * sakR, 0.12 + Math.abs(ripple1) * 1.5, s1 * sakR);

            // Blinding aerodynamic wave ramp + glowing jade floor glide + trailing sakura aurora band
            YangJianVisuals.quad(v, mat, leadBot0, leadBot1, leadTop1, leadTop0, coreCol, coreCol, coreCol, coreCol);
            YangJianVisuals.quad(v, mat, leadTop0, leadTop1, midTop1, midTop0, coreCol, coreCol, bodyCol, bodyCol);
            YangJianVisuals.quad(v, mat, midTop0, midTop1, tailBot1, tailBot0, glowCol, glowCol, zeroCol, zeroCol);
            YangJianVisuals.quad(v, mat, leadBot0, leadBot1, midBot1, midBot0, coreCol, coreCol, bodyCol, bodyCol);
            YangJianVisuals.quad(v, mat, midBot0, midBot1, sakTail1, sakTail0, sakCol, sakCol, sakZero, sakZero);
        }
    }

    @Override protected AABB getBoundingBoxForCulling(SakuraRingWave entity) {
        return entity.combatRenderBounds();
    }
}
