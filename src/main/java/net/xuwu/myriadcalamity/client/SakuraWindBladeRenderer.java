package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.entity.SakuraWindBlade;
import org.joml.Matrix4f;

/** Cinematic volumetric jade-sakura crescent slash wave renderer (non-MC multi-layered plasma geometry). */
public final class SakuraWindBladeRenderer extends EntityRenderer<SakuraWindBlade> {
    public SakuraWindBladeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(SakuraWindBlade blade, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 vel = blade.getDeltaMovement();
        Vec3 dir = new Vec3(vel.x, 0, vel.z);
        if (dir.lengthSqr() < 1e-6) dir = new Vec3(0, 0, 1);
        else dir = dir.normalize();

        Vec3 right = new Vec3(-dir.z, 0, dir.x);
        Vec3 up = new Vec3(0, 1, 0);

        float life = blade.lifeProgress(partial);
        // Spawn immediately at full intensity right as the sword finishes cleaving (no grow-in ramp!)
        double fade = life > 0.82F ? Math.max(0.0, 1.0 - (life - 0.82F) / 0.18F) : 1.0;
        if (fade <= 0.01) return;

        float age = blade.tickCount + partial;
        VertexConsumer v = CombatEffectRenderLayer.buffer(buffers);
        Matrix4f mat = pose.last().pose();

        int mode = blade.mode();
        if (mode == SakuraWindBlade.MODE_SWEEP) {
            // Razor-slim, crystal-translucent low emerald crescent (<=0.84 blocks high, easily jumped in vanilla MC!)
            DivineDragonVfx.bladeCrescent(v, mat, dir, right, up, blade.bladeWidth() * 0.5, 4.1,
                0.22, fade, age, DivineDragonVfx.JADE, true);
        } else if (mode == SakuraWindBlade.MODE_VERTICAL) {
            // Razor-thin towering sky-cleaving crystal guillotine
            DivineDragonVfx.bladeCrescent(v, mat, dir, up, right, blade.bladeHeight() * 0.5, 4.8,
                blade.bladeWidth() * 0.20, fade, age, DivineDragonVfx.JADE, true);
        } else if (mode == SakuraWindBlade.MODE_CROSS) {
            double tiltRad = Math.toRadians(blade.tiltDegrees());
            Vec3 spanAxis = right.scale(Math.cos(tiltRad)).add(up.scale(Math.sin(tiltRad)));
            Vec3 thickAxis = up.scale(Math.cos(tiltRad)).subtract(right.scale(Math.sin(tiltRad)));
            DivineDragonVfx.bladeCrescent(v, mat, dir, spanAxis, thickAxis, blade.bladeWidth() * 0.5, 4.2,
                0.48, fade, age, DivineDragonVfx.JADE, true);
        } else if (mode == SakuraWindBlade.MODE_HIGH_ARC) {
            // Sleek translucent iridescent violet-cyan elevated arc (sprint underneath on the ground to dodge!)
            DivineDragonVfx.bladeCrescent(v, mat, dir, right, up, blade.bladeWidth() * 0.5, 4.5,
                0.72, fade, age, DivineDragonVfx.VIOLET_JADE, true);
        } else {
            double tiltRad = Math.toRadians(blade.tiltDegrees());
            Vec3 spanAxis = right.scale(Math.cos(tiltRad)).add(up.scale(Math.sin(tiltRad)));
            Vec3 thickAxis = up.scale(Math.cos(tiltRad)).subtract(right.scale(Math.sin(tiltRad)));
            DivineDragonVfx.bladeCrescent(v, mat, dir, spanAxis, thickAxis, blade.bladeWidth() * 0.5, 2.8,
                0.38, fade, age, DivineDragonVfx.JADE, true);
        }

        super.render(blade, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SakuraWindBlade entity) {
        return DivineDragonRenderer.TEXTURE;
    }
}
