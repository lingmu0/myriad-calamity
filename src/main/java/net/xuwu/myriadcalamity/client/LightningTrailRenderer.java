package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.entity.LightningTrail;
import net.xuwu.myriadcalamity.entity.YangJianEffects;

/** A low, finite cyan route and restrained jagged arcs show precisely where ground contact is dangerous. */
public final class LightningTrailRenderer extends EntityRenderer<LightningTrail> {
    public LightningTrailRenderer(EntityRendererProvider.Context context) { super(context);shadowRadius=0; }
    @Override public void render(LightningTrail trail,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        if(!trail.hasRoute() || trail.effectAge(partial)>=trail.lifetime())return;
        Vec3 start=trail.routeStart().subtract(trail.position()).add(0,.018,0),end=trail.routeEnd().subtract(trail.position()).add(0,.018,0);
        Vec3 route=end.subtract(start);double length=route.horizontalDistance();if(length<1E-6)return;
        Vec3 side=new Vec3(-route.z/length,0,route.x/length);
        float fade=Math.min(1,Math.max(.15F,(trail.lifetime()-trail.effectAge(partial))/9F));
        VertexConsumer v=buffers.getBuffer(RenderType.debugQuads());
        ribbon(v,pose,start,end,YangJianEffects.TRAIL_RADIUS,color(30*fade,55,138,224));
        for(int sideSign:new int[]{-1,1}) {
            Vec3 offset=side.scale(YangJianEffects.TRAIL_RADIUS*sideSign);
            ribbon(v,pose,start.add(offset),end.add(offset),.028,color(150*fade,95,203,255));
        }
        double heading=Math.atan2(route.z,route.x);
        endCap(v,pose,start,heading+Math.PI/2,fade);
        endCap(v,pose,end,heading-Math.PI/2,fade);
        int pieces=Math.max(3,(int)Math.ceil(length*3));
        int frame=(int)(trail.effectAge(partial)/3);
        for(int strand=0;strand<3;strand++) {
            Vec3 previous=start.add(side.scale((strand-1)*.5));
            for(int i=1;i<=pieces;i++) {
                double t=(double)i/pieces;
                double wobble=Math.sin(i*12.9898+strand*7.31+trail.getId()*3.1+frame*2.6)*.16;
                Vec3 next=start.add(route.scale(t)).add(side.scale((strand-1)*.5+(i==pieces?0:wobble)))
                    .add(0,.04+Math.abs(wobble)*.5,0);
                ribbon(v,pose,previous,next,.035,color((strand==1?245:170)*fade,159,229,255));
                previous=next;
            }
        }
        super.render(trail,yaw,partial,pose,buffers,light);
    }
    private static void endCap(VertexConsumer v,PoseStack pose,Vec3 center,double angle,float fade) {
        for(int i=0;i<16;i++) {
            double a=angle+i*Math.PI/16,b=angle+(i+1)*Math.PI/16,radius=YangJianEffects.TRAIL_RADIUS;
            Vec3 from=center.add(Math.cos(a)*radius,0,Math.sin(a)*radius),to=center.add(Math.cos(b)*radius,0,Math.sin(b)*radius);
            quad(v,pose,center,from,to,to,color(30*fade,55,138,224));
            ribbon(v,pose,from,to,.028,color(150*fade,95,203,255));
        }
    }
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
    private static int color(float alpha,int r,int g,int b) { return ((int)Math.clamp(alpha,0,255)<<24)|(r<<16)|(g<<8)|b; }
    @Override public ResourceLocation getTextureLocation(LightningTrail trail) { return YangJianRenderer.TEXTURE; }
}
