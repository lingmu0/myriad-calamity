package net.xuwu.myriadcalamity.client;

import net.minecraft.world.phys.AABB;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.entity.SakuraFeatherBlade;
import org.joml.Matrix4f;

/**
 * Ornate 3D Sacred Everblossom Spirit-Sword (常樱神锋飞剑) renderer:
 *   - Sculpted 4-bevel diamond ice-jade sword blade with pure-white fuller spine
 *   - Swept twin-crescent Sakura-Gold Blossom Cross-Guard + glowing star-core gem + twin silk tassels
 *   - Revolving orbit star-flare, pre-launch lock halo, sonic shock-cone burst on release, and crystal sword wake!
 */
public final class SakuraFeatherBladeRenderer extends CombatEntityRenderer<SakuraFeatherBlade> {
    public SakuraFeatherBladeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    protected void collect(SakuraFeatherBlade sword, float partial, PoseStack pose, CombatGeometry buffers, int light) {
        VertexConsumer v = buffers.quads();
        Matrix4f mat = pose.last().pose();

        float age = sword.effectAge(partial);
        float spawnScale = Mth.clamp(age / 5.0F, 0.1F, 1.0F);
        Vec3 dir = sword.heading(partial);
        if (dir.lengthSqr() < 1e-6) dir = new Vec3(0, -0.3, 1).normalize();
        Vec3 side = Math.abs(dir.y) > 0.92 ? new Vec3(1, 0, 0) : dir.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = side.cross(dir).normalize();

        boolean launched = sword.isLaunched();
        float fAge = sword.flightAge(partial);

        // 1. Orbiting shimmer flare / Aiming lock ring / Launch sonic shock-cone + wake trail!
        if (!launched) {
            double pulse = 0.78 + 0.22 * Math.sin(age * 0.45 + sword.slot());
            YangJianVisuals.flare(v, mat, dir.scale(-0.55), side, up,
                0.38 * pulse * spawnScale, 0.92 * pulse * spawnScale, DivineDragonVfx.SAKURA);

            // Soft shimmering orbit ribbon trailing along the revolving ring
            Vec3 orbNow = sword.orbitPosition(age);
            Vec3 orbPrev = sword.orbitPosition(Math.max(0.0F, age - 1.8F)).subtract(orbNow);
            if (orbPrev.lengthSqr() > 0.04) {
                YangJianVisuals.wake(v, mat, orbPrev, Vec3.ZERO, 0.04, 0.18 * spawnScale,
                    DivineDragonVfx.JADE, 0.0, 0.48 * spawnScale);
            }

            // Converging golden-sakura lock halo right as this wave whips its tips onto the player!
            int lockT = sword.waveLockTick();
            if (age >= lockT - 6.0F) {
                float aimU = Mth.clamp((age - (lockT - 6.0F)) / 6.0F, 0.0F, 1.0F);
                double ringR = (1.35 - 0.65 * aimU) * spawnScale;
                DivineDragonVfx.orientedRing(v, mat, dir.scale(0.35), side, up, ringR, 0.10,
                    YangJianVisuals.tint(DivineDragonVfx.GOLD.core(), 0.35 + 0.55 * aimU));
            }
        } else {
            // Crisp sonic shock-ring + Mach cone on wave launch (just like Yang Jian's flying swords!)
            if (fAge >= 0.0F && fAge < 5.5F) {
                double t = fAge / 5.5;
                double fade = (1.0 - t) * (1.0 - t);
                DivineDragonVfx.orientedRing(v, mat, dir.scale(-0.6 - t * 1.4), side, up,
                    0.55 + t * 1.85, 0.14 * fade + 0.04,
                    YangJianVisuals.tint(DivineDragonVfx.JADE.core(), fade * 0.92));
                YangJianVisuals.sonicCone(v, mat, dir.scale(1.8), dir, 2.8, 0.78, DivineDragonVfx.JADE, fade * 0.72);
            }
            // Sleek luminous dual-layer crystal sword wake
            double trailLen = Math.min(10.5, 2.0 + fAge * 2.4);
            YangJianVisuals.wake(v, mat, dir.scale(-trailLen), dir.scale(1.2),
                0.04, 0.28, DivineDragonVfx.JADE, 0.0, 0.88);
            YangJianVisuals.wake(v, mat, dir.scale(-trailLen * 1.22), dir.scale(-0.3),
                0.08, 0.52, DivineDragonVfx.SAKURA, 0.0, 0.36);
        }

        // 2. Ornate 3D Sacred Everblossom Spirit-Sword Sculpt (常樱神锋飞剑)
        double s = spawnScale;
        int whiteCore = YangJianVisuals.tint(0xFFFFFF, 0.98);
        int jadeEdge = YangJianVisuals.tint(DivineDragonVfx.JADE.body(), 0.78);
        int jadeVeil = YangJianVisuals.tint(DivineDragonVfx.JADE.glow(), 0.24);
        int sakWing = YangJianVisuals.tint(DivineDragonVfx.SAKURA.body(), 0.88);
        int sakTip = YangJianVisuals.tint(DivineDragonVfx.SAKURA.glow(), 0.45);
        int goldGuard = YangJianVisuals.tint(DivineDragonVfx.GOLD.core(), 0.94);

        // 2a. 4-bevel diamond crystalline sword blade (guard at 0.0 -> shoulder at +0.7 -> needle tip at +3.4)
        Vec3 tip = dir.scale(3.45 * s);
        Vec3 midSpineU = dir.scale(1.15 * s).add(up.scale(0.14 * s));
        Vec3 midSpineD = dir.scale(1.15 * s).subtract(up.scale(0.14 * s));
        Vec3 edgeL = dir.scale(1.05 * s).add(side.scale(0.32 * s));
        Vec3 edgeR = dir.scale(1.05 * s).subtract(side.scale(0.32 * s));
        Vec3 baseCenter = dir.scale(-0.05 * s);

        // Upper & lower diamond blade facets (white-diamond spine -> translucent ice-jade cutting edges)
        YangJianVisuals.quad(v, mat, tip, edgeL, baseCenter, midSpineU, whiteCore, jadeEdge, goldGuard, whiteCore);
        YangJianVisuals.quad(v, mat, tip, midSpineU, baseCenter, edgeR, whiteCore, whiteCore, goldGuard, jadeEdge);
        YangJianVisuals.quad(v, mat, tip, midSpineD, baseCenter, edgeL, whiteCore, whiteCore, goldGuard, jadeEdge);
        YangJianVisuals.quad(v, mat, tip, edgeR, baseCenter, midSpineD, whiteCore, jadeEdge, goldGuard, whiteCore);

        // Translucent crystal blade aura halo
        YangJianVisuals.quad(v, mat, tip.add(dir.scale(0.45 * s)), side.scale(0.55 * s).add(dir.scale(0.9 * s)),
            dir.scale(-0.3 * s), side.scale(-0.55 * s).add(dir.scale(0.9 * s)),
            jadeVeil, jadeVeil, jadeVeil, jadeVeil);

        // 2b. Swept Twin-Crescent Sakura Blossom Cross-Guard (樱瓣双翼剑格) + Star-Core Gem
        for (int sign : new int[]{-1, 1}) {
            Vec3 gRootF = dir.scale(0.22 * s);
            Vec3 gRootB = dir.scale(-0.28 * s);
            Vec3 gMid = dir.scale(-0.18 * s).add(side.scale(sign * 0.62 * s)).add(up.scale(0.10 * s));
            Vec3 gTip = dir.scale(-0.72 * s).add(side.scale(sign * 1.05 * s)).add(up.scale(0.18 * s));
            Vec3 gMidD = dir.scale(-0.18 * s).add(side.scale(sign * 0.62 * s)).subtract(up.scale(0.10 * s));
            YangJianVisuals.quad(v, mat, gRootF, gMid, gTip, gRootB, goldGuard, sakWing, sakTip, sakWing);
            YangJianVisuals.quad(v, mat, gRootF, gRootB, gTip, gMidD, goldGuard, sakWing, sakTip, sakWing);
        }

        // Radiant guard star-gem + slender hilt & fluttering sakura pommel silk
        YangJianVisuals.flare(v, mat, Vec3.ZERO, side, up, 0.24 * s, 0.42 * s, DivineDragonVfx.GOLD);
        Vec3 hiltEnd = dir.scale(-1.15 * s);
        YangJianVisuals.quad(v, mat,
            baseCenter.add(side.scale(0.08 * s)), baseCenter.subtract(side.scale(0.08 * s)),
            hiltEnd.subtract(side.scale(0.06 * s)), hiltEnd.add(side.scale(0.06 * s)),
            goldGuard, goldGuard, sakWing, sakWing);

        // Twin fluttering sakura-pink tassel streamers behind the pommel
        double flutter = Math.sin(age * 0.65 + sword.slot()) * 0.22 * s;
        Vec3 tasselTip = dir.scale(-2.05 * s).add(side.scale(flutter)).add(up.scale(flutter * 0.5));
        int zeroSak = YangJianVisuals.tint(DivineDragonVfx.SAKURA.glow(), 0.0);
        YangJianVisuals.quad(v, mat,
            hiltEnd.add(side.scale(0.14 * s)), hiltEnd.subtract(side.scale(0.14 * s)),
            tasselTip, tasselTip,
            sakWing, sakWing, zeroSak, zeroSak);

    }

    @Override protected AABB getBoundingBoxForCulling(SakuraFeatherBlade entity) {
        return entity.combatRenderBounds();
    }
}
