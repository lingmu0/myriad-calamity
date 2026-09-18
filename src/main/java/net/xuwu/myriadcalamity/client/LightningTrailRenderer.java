package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.entity.LightningScar;
import net.xuwu.myriadcalamity.entity.LightningTrail;

/**
 * The blue thunder field a P2 aftershock leaves behind: the triggering move's whole footprint is
 * washed and rimmed on the floor, then covered in tall blue pillars and crawling ground arcs.
 * It reads exactly like the P3 red-thunder field, only colder, and it stays until the next axe
 * summon. Every layer uses the published footprint, so the art matches the damage area.
 */
public final class LightningTrailRenderer extends EntityRenderer<LightningTrail> {
    private static final int GLOW_RGB=0x2456D6,BODY_RGB=0x5696FF,CORE_RGB=0xDEF0FF;
    private static final int ARC_RGB=0x3C78EB;

    public LightningTrailRenderer(EntityRendererProvider.Context context) { super(context);shadowRadius=0; }

    @Override public void render(LightningTrail trail,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        if(!trail.live(partial))return;
        Vec3 origin=trail.position();
        Vec3 anchor=trail.anchor().subtract(origin),end=trail.scarEnd().subtract(origin),center=trail.scarCenter().subtract(origin);
        int shape=trail.shape();
        double radius=trail.scarRadius(),arc=trail.scarAngle();
        if(!(radius>0) || radius>40)return;
        float age=trail.effectAge(partial);
        float fade=trail.persistent()?1F:Math.min(1,Math.max(.15F,(trail.lifetime()-age)/9F));
        double breathe=.5+.5*Math.sin(age*.32+trail.getId()*.7);
        VertexConsumer v=buffers.getBuffer(RenderType.debugQuads());
        double headingX=end.x-anchor.x,headingZ=end.z-anchor.z;
        // 1. The footprint itself: a breathing wash with a cracked, jittering rim.
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
        // 2. Blue pillars spread over the footprint, each with its own ground arcs.
        double[][] pillars=LightningScar.pillars(shape,anchor.x,anchor.z,headingX,headingZ,center.x,center.z,
            radius,arc,LightningScar.MAX_PILLARS);
        double spread=Math.min(radius*.5,2.2);
        for(int i=0;i<pillars.length;i++)
            pillar(v,pose,point(pillars[i],.04),spread,age,trail.getId()+i*37,fade);
        super.render(trail,yaw,partial,pose,buffers,light);
    }

    /** One full-height blue column plus the arcs it throws across its own patch of ground. */
    private static void pillar(VertexConsumer v,PoseStack pose,Vec3 ground,double spread,float age,int seed,float fade) {
        double clock=age/2.0;int frame=(int)Math.floor(clock);
        double blend=clock-frame;blend=blend*blend*(3-2*blend);
        double pulse=.78+.22*Math.sin(age*1.12+seed*.71);
        int glow=color((105+45*pulse)*fade,GLOW_RGB);
        int body=color((185+45*pulse)*fade,BODY_RGB);
        int core=color(245*fade,CORE_RGB);
        for(int strand=0;strand<3;strand++) {
            Vec3 top=ground.add(noise(seed,strand,17,frame,blend,0)*.44,
                LightningScar.PILLAR_HEIGHT*(strand==0?1:.72),noise(seed,strand,17,frame,blend,1)*.44);
            Vec3 landing=ground.add(noise(seed,strand,0,frame,blend,0)*spread*.35,.055,
                noise(seed,strand,0,frame,blend,1)*spread*.35);
            bolt(v,pose,landing,top,strand==0?.060:.038,seed+strand*53,age,glow,body,core);
        }
        for(int i=0;i<5;i++) {
            double angle=i*Math.PI*2/5+seed*.31;
            double length=spread*(.68+.24*noise(seed,i,8,frame,blend,0));
            Vec3 end=ground.add(Math.cos(angle)*length,.09+Math.abs(noise(seed,i,9,frame,blend,1))*.18,Math.sin(angle)*length);
            Vec3 root=ground.add(0,.11,0);
            bolt(v,pose,root,end,.032,seed+i*29,age,glow,body,core);
            if((i&1)==0) {
                Vec3 fork=root.lerp(end,.6);
                double forkAngle=angle+.65*noise(seed,i,3,frame,blend,0);
                bolt(v,pose,fork,fork.add(Math.cos(forkAngle)*spread*.26,.16,Math.sin(forkAngle)*spread*.26),
                    .020,seed+i*71,age,glow,body,core);
            }
        }
    }

    /** Time-blended jagged path drawn as three nested crossed ribbons. */
    private static void bolt(VertexConsumer v,PoseStack pose,Vec3 from,Vec3 to,double width,int seed,float age,
                             int glow,int body,int core) {
        Vec3 route=to.subtract(from);double length=route.length();if(length<1E-6)return;
        Vec3 direction=route.scale(1/length);
        Vec3 side=direction.cross(Math.abs(direction.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
        Vec3 up=direction.cross(side).normalize();
        int pieces=Mth.clamp((int)Math.ceil(length*3),4,14);
        double clock=age/1.7;int frame=(int)Math.floor(clock);
        double blend=clock-frame;blend=blend*blend*(3-2*blend);
        double jitter=Math.min(.25,length*.13);
        Vec3 previous=from;
        for(int i=1;i<=pieces;i++) {
            double t=(double)i/pieces,taper=Math.sin(Math.PI*t);
            Vec3 next=i==pieces?to:from.add(route.scale(t))
                .add(side.scale(noise(seed,1,i,frame,blend,0)*jitter*taper))
                .add(up.scale(noise(seed,2,i,frame,blend,1)*jitter*taper));
            crossRibbon(v,pose,previous,next,side,up,width*2.2,glow);
            crossRibbon(v,pose,previous,next,side,up,width,body);
            crossRibbon(v,pose,previous,next,side,up,width*.26,core);
            previous=next;
        }
    }

    /** Two perpendicular ribbons, so a bolt keeps its volume from every camera angle. */
    private static void crossRibbon(VertexConsumer v,PoseStack pose,Vec3 from,Vec3 to,Vec3 side,Vec3 up,double width,int color) {
        Vec3 a=side.scale(width),b=up.scale(width);
        quad(v,pose,from.add(a),to.add(a),to.subtract(a),from.subtract(a),color);
        quad(v,pose,from.add(b),to.add(b),to.subtract(b),from.subtract(b),color);
    }

    private static double noise(int seed,int strand,int point,int frame,double blend,int axis) {
        double base=seed*13.13+strand*37.31+point*19.73+axis*71.91;
        double a=Math.sin(base+frame*11.73)*43758.5453,b=Math.sin(base+(frame+1)*11.73)*43758.5453;
        a=(a-Math.floor(a))*2-1;b=(b-Math.floor(b))*2-1;
        return a+(b-a)*blend;
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
    private static int color(double alpha,int rgb) { return ((int)Math.clamp(alpha,0,255)<<24)|rgb; }
    @Override public ResourceLocation getTextureLocation(LightningTrail trail) { return YangJianRenderer.TEXTURE; }
}
