package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.entity.DivineFlyingSword;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Visible revolving jian blades align with their flight direction, with launch bursts and luminous wakes. */
public final class DivineFlyingSwordRenderer extends CombatEntityRenderer<DivineFlyingSword> {
    private final YangJianWeapons weapons;
    private final YangJianProjectileTrail trail = new YangJianProjectileTrail();
    public DivineFlyingSwordRenderer(EntityRendererProvider.Context context) {
        super(context);weapons=new YangJianWeapons(context.getResourceManager());shadowRadius=.12F;
    }
    @Override protected AABB getBoundingBoxForCulling(DivineFlyingSword entity) { return entity.combatRenderBounds(); }

    @Override protected void collect(DivineFlyingSword sword,float partial,PoseStack pose,CombatGeometry buffers,int light) {
        if(!sword.hasPlan())return;
        YangJianVisuals.Palette palette = sword.myriadSword() ? YangJianVisuals.RED : YangJianVisuals.SILVER;
        VertexConsumer v = buffers.quads();
        Matrix4f m = pose.last().pose();
        Vec3 heading = sword.facing(partial);

        if (!sword.stepThrow() || sword.flying(partial)) {
            trail.render(sword, partial, sword.myriadSword() ? 6.8 : 5.4, sword.stepThrow() ? .13 : .22,
                palette, v, m);
        }
        if (!sword.stepThrow()) {
            float age = sword.effectAge(partial);
            float flightAge = age - sword.releaseTick();
            Vec3 side = YangJianVisuals.side(heading);
            Vec3 up = heading.cross(side).normalize();
            if (flightAge < 0) {
                double pulse = .75 + .25 * Math.sin(age * .55 + sword.getId());
                YangJianVisuals.flare(v, m, heading.scale(-.35), side, up, .28 * pulse, .62 * pulse, palette);
            } else if (flightAge < 4.5F) {
                double t = flightAge / 4.5;
                double fade = (1 - t) * (1 - t);
                YangJianVisuals.orientedRing(v, m, heading.scale(-.45 - t * .5), side, up,
                    .22 + t * .72, .055 * fade + .015, YangJianVisuals.tint(palette.core(), fade * .9));
                YangJianVisuals.sonicCone(v, m, heading.scale(.65), heading, 1.35, .36, palette, fade * .75);
            }
        }
        pose.pushPose();
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0,-1,0),new Vector3f((float)heading.x,(float)heading.y,(float)heading.z)));
        float scale=sword.stepThrow()?.4F:.65F;
        pose.scale(scale,scale,scale);
        buffers.mesh(pose,RenderTypes.entityCutout(YangJianRenderer.TEXTURE),(stack,vertices)->
            weapons.renderSword(stack,vertices,15728880,OverlayTexture.NO_OVERLAY,0xFFFFFFFF));
        pose.popPose();
    }
}
