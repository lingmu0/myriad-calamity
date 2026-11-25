package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.entity.TriPointedBlade;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The held three-pointed spear itself, maintaining its flight heading with a golden piercing wind-cone and wake. */
public final class TriPointedBladeRenderer extends CombatEntityRenderer<TriPointedBlade> {
    private final YangJianModel model;
    private final YangJianProjectileTrail trail = new YangJianProjectileTrail();
    public TriPointedBladeRenderer(EntityRendererProvider.Context context) {
        super(context);model=new YangJianModel(context.getResourceManager());shadowRadius=.2F;
    }
    @Override protected void collect(TriPointedBlade blade,float partial,PoseStack pose,CombatGeometry buffers,int light) {
        VertexConsumer v = buffers.quads();
        Matrix4f m = pose.last().pose();
        trail.render(blade, partial, 6.2, .24, YangJianVisuals.GOLD, v, m);
        Vec3 motion=blade.getDeltaMovement();
        if(motion.lengthSqr()<1E-7)motion=blade.getLookAngle();
        Vec3 dir = motion.normalize();
        if(dir.lengthSqr()>1E-6) {
            YangJianVisuals.sonicCone(v, m, dir.scale(1.15), dir, 2.35, .52, YangJianVisuals.GOLD, .78);
            Vec3 side = YangJianVisuals.side(dir), up = dir.cross(side);
            double phase = (blade.tickCount + partial) * .55;
            Vec3 prev = dir.scale(.95);
            for(int i=1;i<=8;i++) {
                double t=i/8.0;
                double a=phase+t*Math.PI*2.4;
                double r=.14+t*.34;
                Vec3 pt=dir.scale(.95-t*2.4).add(side.scale(Math.cos(a)*r)).add(up.scale(Math.sin(a)*r));
                YangJianVisuals.wake(v,m,pt,prev,.025*(1-t),.055*(1-t*.5),YangJianVisuals.GOLD,(1-t)*.65,(1-t*.5)*.9);
                prev=pt;
            }
        }
        pose.pushPose();
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0,-1,0),
            new Vector3f((float)dir.x,(float)dir.y,(float)dir.z)));
        float scale=YangJianRenderer.MODEL_SCALE;
        pose.scale(scale,scale,scale);
        buffers.mesh(pose,RenderTypes.entityCutout(YangJianRenderer.TEXTURE),(stack,vertices)->
            model.renderWeapon(stack,vertices,light,OverlayTexture.NO_OVERLAY));
        pose.popPose();
    }
}
