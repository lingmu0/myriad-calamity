package net.xuwu.myriadcalamity.client;

import net.minecraft.world.phys.AABB;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.entity.DivineThunderHazard;
import org.joml.Matrix4f;

/** Cinematic non-MC sacred golden thunder seal telegraph, forked sky lightning, and shockwave dome renderer. */
public final class DivineThunderHazardRenderer extends CombatEntityRenderer<DivineThunderHazard> {
    private static final double BOLT_HEIGHT = 96.0;

    public DivineThunderHazardRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    protected void collect(DivineThunderHazard hazard, float partial, PoseStack pose, CombatGeometry buffers, int light) {
        float age = hazard.age(partial);
        int windup = hazard.windup();
        if (age < 0 || age >= windup + hazard.duration()) return;

        VertexConsumer v = buffers.quads();
        Matrix4f mat = pose.last().pose();
        Vec3 center = Vec3.ZERO;

        if (hazard.isWarning(partial)) {
            float charge = Mth.clamp(age / Math.max(1, windup), 0.0F, 1.0F);
            DivineDragonVfx.sacredThunderSeal(v, mat, center, hazard.radius(), charge, age, hazard.isLocked());
        } else {
            float landed = age - windup;
            YangJianLightningEffects.strike(v, mat, center, BOLT_HEIGHT,
                Math.min(3.6, hazard.radius() * 0.98), hazard.isLocked() ? 0.44 : 0.34,
                landed, 0, hazard.getId(), DivineDragonVfx.GOLD, 1.0);
            if (landed < 8.0F) {
                double impFade = Math.pow(1.0 - landed / 8.0, 1.25);
                YangJianVisuals.bolt(v, mat, center.add(0, 62.0, 0), center, 0.42, 4.6,
                    landed * 1.6, hazard.getId() * 19, DivineDragonVfx.GOLD, impFade * 0.85, true);
                DivineDragonVfx.shockwaveDome(v, mat, center,
                    0.8 + landed * (hazard.radius() * 0.36), 0.7 + landed * 0.65,
                    DivineDragonVfx.GOLD, impFade * 0.78);
            }
        }
    }

    @Override protected AABB getBoundingBoxForCulling(DivineThunderHazard entity) {
        return entity.combatRenderBounds();
    }
}
