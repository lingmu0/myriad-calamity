package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.config.MyriadConfig;
import net.xuwu.myriadcalamity.entity.LightningScar;
import net.xuwu.myriadcalamity.entity.LightningTrail;
import org.joml.Matrix4f;

/** Repeated strikes are staggered within the server-defined thunder field. */
public final class LightningTrailRenderer extends CombatEntityRenderer<LightningTrail> {
    private static final int GLOW_RGB=0x2456D6,CORE_RGB=0xDEF0FF;
    private static final int ARC_RGB=0x3C78EB;

    public LightningTrailRenderer(EntityRendererProvider.Context context) { super(context);shadowRadius=0; }

    @Override protected AABB getBoundingBoxForCulling(LightningTrail entity) { return entity.combatRenderBounds(); }

    @Override protected void collect(LightningTrail trail,float partial,PoseStack pose,CombatGeometry buffers,int light) {
        if(!trail.live(partial))return;
        Vec3 origin=trail.position();
        Vec3 anchor=trail.anchor().subtract(origin),end=trail.scarEnd().subtract(origin),center=trail.scarCenter().subtract(origin);
        int shape=trail.shape();
        double radius=trail.scarRadius(),arc=trail.scarAngle();
        if(!(radius>0) || radius>40)return;
        float age=trail.effectAge(partial);
        float fade=trail.persistent()?1F:Math.min(1,Math.max(.15F,(trail.lifetime()-age)/9F));
        double breathe=.5+.5*Math.sin(age*.32+trail.getId()*.7);
        VertexConsumer v=buffers.quads();
        double headingX=end.x-anchor.x,headingZ=end.z-anchor.z;
        if(MyriadConfig.showYangJianAttackIndicators()) {
            double[][] outline=LightningScar.outline(shape,anchor.x,anchor.z,headingX,headingZ,center.x,center.z,radius,arc);
            double washY=.03,rimY=.055;
            int wash=color((16+14*breathe)*fade,GLOW_RGB);
            for(int i=1;i<outline.length-1;i++) {
                Vec3 a=point(outline[0],washY),b=point(outline[i],washY),c=point(outline[i+1],washY);
                quad(v,pose,a,b,c,c,wash);
            }
            int frame=(int)(age/2.5);
            for(int i=0;i<outline.length-1;i++) {
                double jitter=.10*Math.sin(i*12.9898+trail.getId()*3.1+frame*2.6*fade);
                Vec3 a=point(outline[i],rimY).add(jitter,0,jitter*.6),b=point(outline[i+1],rimY).add(jitter,0,jitter*.6);
                ribbon(v,pose,a,b,.062,color((120+45*breathe)*fade,ARC_RGB));
                ribbon(v,pose,a,b,.024,color((190+45*breathe)*fade,CORE_RGB));
            }
        }
        double[][] strikes=LightningScar.pillars(shape,anchor.x,anchor.z,headingX,headingZ,center.x,center.z,
            radius,arc,LightningScar.MAX_PILLARS);
        int interval=trail.persistent()?Math.max(13,strikes.length*3):0;
        double spread=Math.min(radius*.54,1.90);
        Matrix4f matrix=pose.last().pose();
        for(int i=0;i<strikes.length;i++) {
            YangJianLightningEffects.strike(v,matrix,point(strikes[i],.044),LightningScar.PILLAR_HEIGHT,
                spread,.192,age-i*3,interval,trail.getId()+i*37,YangJianVisuals.BLUE,fade);
        }
    }

    private static Vec3 point(double[] flat,double y) { return new Vec3(flat[0],y,flat[1]); }
    static void ribbon(VertexConsumer v,PoseStack pose,Vec3 a,Vec3 b,double halfWidth,int color) {
        Vec3 d=b.subtract(a);double horizontal=d.horizontalDistance();
        Vec3 normal=horizontal>1E-8?new Vec3(-d.z/horizontal*halfWidth,0,d.x/horizontal*halfWidth):new Vec3(halfWidth,0,0);
        quad(v,pose,a.add(normal),b.add(normal),b.subtract(normal),a.subtract(normal),color);
    }
    static void quad(VertexConsumer v,PoseStack pose,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color) {
        vertex(v,pose,a,color);vertex(v,pose,b,color);vertex(v,pose,c,color);vertex(v,pose,d,color);
    }
    private static void vertex(VertexConsumer v,PoseStack pose,Vec3 p,int color) {
        v.addVertex(pose.last().pose(),(float)p.x,(float)p.y,(float)p.z).setColor(color);
    }
    private static int color(double alpha,int rgb) { return YangJianVisuals.tint(rgb,alpha/255); }
}
