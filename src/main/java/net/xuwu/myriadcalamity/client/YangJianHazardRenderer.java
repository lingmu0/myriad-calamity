package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.config.MyriadConfig;
import net.xuwu.myriadcalamity.entity.YangJianHazard;
import net.xuwu.myriadcalamity.entity.YangJianHazardMath;

/** Geometry warnings and bounded effects share the server's exact segment or impact circle. */
public final class YangJianHazardRenderer extends EntityRenderer<YangJianHazard> {
    private final YangJianWeapons weapons;
    public YangJianHazardRenderer(EntityRendererProvider.Context context) { super(context);weapons=new YangJianWeapons(context.getResourceManager());shadowRadius=0; }
    @Override public void render(YangJianHazard h,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        if(!h.ready() || h.age(partial)<0 || (!h.persistent() && h.age(partial)>=h.windup()+h.duration()))return;
        Vec3 from=h.visualStart(partial).subtract(h.position()),to=h.visualEnd(partial).subtract(h.position());
        VertexConsumer v=buffers.getBuffer(RenderType.debugQuads());
        float charge=h.windup()==0?1:Mth.clamp(h.age(partial)/h.windup(),0,1);
        if(h.kind()==1) {
            // Every eye beam charges on the eye itself. No route is drawn
            // until release, including fixed shots and ordinary aerial sweeps.
            if(!h.warning(partial))eyeBeam(v,pose,from,to,h.radius(),h.age(partial)-h.windup(),h.getId());
        } else {
            boolean warning=h.warning(partial);
            float pulse=.72F+.28F*Mth.sin(h.age(partial)*.73F+h.getId());
            if(warning) {
                // Painted warning circles are the opt-in attack indicator. With it off, the
                // falling blade and the thunder column are the only advance notice.
                if(MyriadConfig.showYangJianAttackIndicators())
                    circle(v,pose,from,h.radius(),h.kind()==3?0xEDFF5256:0xEDC7E8FF,(int)(30+charge*35));
            } else {
                // A landed strike always shows its area, but drawn as the weapon that made
                // it: blade cuts for the falling swords, a scorched arc for the red thunder.
                float landed=h.age(partial)-h.windup();
                if(h.kind()==2)bladeScar(v,pose,from,h.radius(),landed,h.getId());
                else jaggedRim(v,pose,from,h.radius(),landed,h.getId(),
                    ((int)(95+55*pulse)<<24)|0xE01A2E,((int)(190+65*pulse)<<24)|0xFF97A9,.055,.11);
            }
            if(h.kind()==2) {
                float drop=Mth.clamp((h.age(partial)-(h.windup()-5))/5,0,1);
                // The approved blade tip is at -39.6 pixels; land that tip exactly on the warning floor.
                pose.pushPose();pose.translate(from.x,from.y+39.6/16*.68+(1-drop)*8,from.z);pose.scale(.68F,.68F,.68F);
                weapons.renderSword(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(YangJianRenderer.TEXTURE)),15728880,OverlayTexture.NO_OVERLAY,0xFFFFFFFF);
                pose.popPose();
            } else if(!warning)redThunder(v,pose,from,h.radius(),h.age(partial)-h.windup(),h.getId());
        }
        super.render(h,yaw,partial,pose,buffers,light);
    }

    /** Blade impact: crossed cuts and a cracked rim, so a landed sword reads as a sword. */
    private static void bladeScar(VertexConsumer v,PoseStack pose,Vec3 center,double radius,float age,int seed) {
        double fade=Math.max(0,1-age/18.0);
        if(fade<=0)return;
        int glow=((int)(fade*70)<<24)|0xBFD4E8;
        int body=((int)(fade*150)<<24)|0xDCE9F7;
        int core=((int)(fade*235)<<24)|0xFFFFFF;
        for(int cut=0;cut<4;cut++) {
            double angle=cut*Math.PI/4+seed*.37,c=Math.cos(angle),s=Math.sin(angle);
            Vec3 axis=new Vec3(c,0,s),side=new Vec3(-s,0,c);
            // Alternating long and short cuts keep the mark from reading as a plain cross.
            double reach=radius*(cut%2==0?1:.74),half=(cut%2==0?.21:.16)*fade;
            for(int sign=-1;sign<=1;sign+=2) {
                Vec3 far=center.add(axis.scale(reach*sign)).add(0,.02,0);
                Vec3 near=center.add(axis.scale(.16*sign)).add(0,.1*fade,0);
                LightningTrailRenderer.quad(v,pose,near.add(side.scale(half)),far,near.subtract(side.scale(half)),near.subtract(side.scale(half)),body);
                LightningTrailRenderer.quad(v,pose,near.add(side.scale(half*.4)),far,near.subtract(side.scale(half*.4)),near.subtract(side.scale(half*.4)),core);
            }
        }
        jaggedRim(v,pose,center,radius*.97,age,seed,glow,core,.05,.10);
    }

    /** A cracked, jittering outline that replaces the plain warning circle after a strike lands. */
    private static void jaggedRim(VertexConsumer v,PoseStack pose,Vec3 center,double radius,float age,int seed,
                                  int outer,int core,double outerWidth,double coreWidth) {
        int segments=64;
        double clock=age/2.5;int frame=(int)Math.floor(clock);
        double blend=clock-frame;blend=blend*blend*(3-2*blend);
        for(int i=0;i<segments;i++) {
            double a=i*Math.PI*2/segments,b=(i+1)*Math.PI*2/segments;
            double ra=radius*(1+.07*arcNoise(seed,i,41,frame,blend,0));
            double rb=radius*(1+.07*arcNoise(seed,i+1,41,frame,blend,1));
            Vec3 x=center.add(Math.cos(a)*ra,.045,Math.sin(a)*ra);
            Vec3 y=center.add(Math.cos(b)*rb,.045,Math.sin(b)*rb);
            LightningTrailRenderer.ribbon(v,pose,x,y,outerWidth,outer);
            LightningTrailRenderer.ribbon(v,pose,x,y,coreWidth,core);
        }
    }
    private static void eyeBeam(VertexConsumer v,PoseStack pose,Vec3 from,Vec3 to,double radius,float age,int seed) {
        // One current segment owns all layers. A fast sweep never leaves a
        // fan of old full-length rays suspended around the caster.
        double pulse=.91+.09*Math.sin(age*1.8+seed);
        tube(v,pose,from,to,radius,0x24FFB719);
        tube(v,pose,from,to,radius*.70*pulse,0x51FFD334);
        tube(v,pose,from,to,radius*.33*pulse,0xD5FFE56F);
        tube(v,pose,from,to,radius*.10,0xFFFFFCE7);
        beamLightning(v,pose,from,to,radius,age,seed);
        beamEndFlare(v,pose,from,to,radius,pulse);
    }

    private static void beamEndFlare(VertexConsumer v,PoseStack pose,Vec3 from,Vec3 to,double radius,double pulse) {
        Vec3 direction=to.subtract(from).normalize();if(direction.lengthSqr()<1E-8)return;
        Vec3 side=direction.cross(Math.abs(direction.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
        Vec3 up=direction.cross(side).normalize();
        // Keep the flare on the hit plane; it must not shine through the
        // wall that clipped the beam. No stored or previous endpoints render.
        Vec3 center=to.subtract(direction.scale(.006));
        for(int i=0;i<16;i++) {
            double a=i*Math.PI/8,b=(i+1)*Math.PI/8;
            Vec3 x=center.add(side.scale(Math.cos(a)*radius)).add(up.scale(Math.sin(a)*radius));
            Vec3 y=center.add(side.scale(Math.cos(b)*radius)).add(up.scale(Math.sin(b)*radius));
            LightningTrailRenderer.quad(v,pose,center,x,y,y,0x4FFFD45C);
        }
        double size=radius*.85*pulse,width=radius*.065;
        Vec3 a=side.scale(size),b=up.scale(width);
        LightningTrailRenderer.quad(v,pose,center.subtract(a).subtract(b),center.add(a).subtract(b),
            center.add(a).add(b),center.subtract(a).add(b),0xE8FFF6BD);
        a=up.scale(size);b=side.scale(width);
        LightningTrailRenderer.quad(v,pose,center.subtract(a).subtract(b),center.add(a).subtract(b),
            center.add(a).add(b),center.subtract(a).add(b),0xD3FFF8D3);
    }

    private static void redThunder(VertexConsumer v,PoseStack pose,Vec3 ground,double radius,float age,int seed) {
        // The pillar keeps its full height both while it descends and after it lands, so the
        // strike stays a landmark instead of collapsing into the ground band.
        double height=YangJianHazardMath.RED_THUNDER_HEIGHT;
        double clock=age/2.0;int frame=(int)Math.floor(clock);
        double blend=clock-frame;blend=blend*blend*(3-2*blend);
        double pulse=.78+.22*Math.sin(age*1.12+seed*.71);
        for(int strand=0;strand<3;strand++) {
            Vec3 top=ground.add(arcNoise(seed,strand,17,frame,blend,0)*.44,
                height*(strand==0?1:.7),arcNoise(seed,strand,17,frame,blend,1)*.44);
            Vec3 landing=ground.add(arcNoise(seed,strand,0,frame,blend,0)*radius*.23,.055,
                arcNoise(seed,strand,0,frame,blend,1)*radius*.23);
            lightning(v,pose,landing,top,strand==0?.060:.037,seed+strand*53,age,1,
                ((int)(120+45*pulse)<<24)|0xE50831,0xFFFEE4EC);
        }
        for(int i=0;i<7;i++) {
            // Stable radial sectors keep the landing point readable; only
            // their electrical branches flicker, rather than rotating a disk.
            double angle=i*Math.PI*2/7+seed*.31;
            double length=radius*(.76+.16*arcNoise(seed,i,8,frame,blend,0));
            Vec3 end=ground.add(Math.cos(angle)*length,.09+Math.abs(arcNoise(seed,i,9,frame,blend,1))*.18,Math.sin(angle)*length);
            Vec3 root=ground.add(0,.11,0);
            lightning(v,pose,root,end,.032,seed+i*29,age,1,0xBAEB113A,0xFFFFCDD9);
            if((i&1)==0) {
                Vec3 fork=root.lerp(end,.6);
                double forkAngle=angle+.65*arcNoise(seed,i,3,frame,blend,0);
                Vec3 forkEnd=fork.add(Math.cos(forkAngle)*radius*.26,.16,Math.sin(forkAngle)*radius*.26);
                lightning(v,pose,fork,forkEnd,.020,seed+i*71,age,1,0xAEBD0C32,0xF4FFA8C1);
            }
        }
    }

    /** Time-blended jagged paths with fixed endpoints and a red corona. */
    private static void lightning(VertexConsumer v,PoseStack pose,Vec3 from,Vec3 to,double width,int seed,float age,int strand,int outer,int core) {
        Vec3 route=to.subtract(from);double length=route.length();if(length<1E-6)return;
        Vec3 direction=route.scale(1/length);
        Vec3 side=direction.cross(Math.abs(direction.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
        Vec3 up=direction.cross(side).normalize();
        int pieces=Mth.clamp((int)Math.ceil(length*3),4,18);
        double clock=age/1.7;int frame=(int)Math.floor(clock);
        double blend=clock-frame;blend=blend*blend*(3-2*blend);
        double jitter=Math.min(.25,length*.13);
        Vec3 previous=from;
        for(int i=1;i<=pieces;i++) {
            double t=(double)i/pieces,taper=Math.sin(Math.PI*t);
            Vec3 next=i==pieces?to:from.add(route.scale(t))
                .add(side.scale(arcNoise(seed,strand,i,frame,blend,0)*jitter*taper))
                .add(up.scale(arcNoise(seed,strand,i,frame,blend,1)*jitter*taper));
            crossRibbon(v,pose,previous,next,side,up,width*2.4,(outer&0xFFFFFF)|0x28000000);
            crossRibbon(v,pose,previous,next,side,up,width,outer);
            crossRibbon(v,pose,previous,next,side,up,width*.26,core);
            previous=next;
        }
    }

    private static void crossRibbon(VertexConsumer v,PoseStack pose,Vec3 from,Vec3 to,Vec3 side,Vec3 up,double width,int color) {
        Vec3 a=side.scale(width),b=up.scale(width);
        LightningTrailRenderer.quad(v,pose,from.add(a),to.add(a),to.subtract(a),from.subtract(a),color);
        LightningTrailRenderer.quad(v,pose,from.add(b),to.add(b),to.subtract(b),from.subtract(b),color);
    }
    private static void beamLightning(VertexConsumer v,PoseStack pose,Vec3 from,Vec3 to,double radius,float age,int seed) {
        Vec3 route=to.subtract(from);double length=route.length();if(length<1E-6)return;
        Vec3 direction=route.scale(1/length);
        Vec3 side=direction.cross(Math.abs(direction.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
        Vec3 up=direction.cross(side).normalize();
        // Bounded geometry, with noise blended over three ticks instead of jumping every rendered frame.
        int pieces=Mth.clamp((int)Math.ceil(length*1.25),4,24);
        double clock=age/3.0;int frame=(int)Math.floor(clock);
        double blend=clock-frame;blend=blend*blend*(3-2*blend);
        double width=Math.min(.035,radius*.065);
        for(int strand=0;strand<3;strand++) {
            double angle=strand*Math.PI*2/3+seed*.71;
            int alpha=(int)(210+25*Math.sin(age*.65+strand*2.1));
            int color=(alpha<<24)|0xFFF3A6;
            Vec3 previous=from;
            for(int i=1;i<=pieces;i++) {
                double t=(double)i/pieces;
                // Taper onto the published endpoints; offsets stay inside the actual beam radius.
                double taper=Math.sin(Math.PI*t);
                double x=arcNoise(seed,strand,i,frame,blend,0),y=arcNoise(seed,strand,i,frame,blend,1);
                Vec3 next=i==pieces?to:from.add(route.scale(t))
                    .add(side.scale((Math.cos(angle)*.6+x*.2)*radius*taper))
                    .add(up.scale((Math.sin(angle)*.6+y*.2)*radius*taper));
                // Both ribbon offsets are perpendicular to the full beam, so they cannot pass its clipped end plane.
                Vec3 a=side.scale(width),b=up.scale(width);
                LightningTrailRenderer.quad(v,pose,previous.add(a),next.add(a),next.subtract(a),previous.subtract(a),color);
                LightningTrailRenderer.quad(v,pose,previous.add(b),next.add(b),next.subtract(b),previous.subtract(b),color);
                previous=next;
            }
        }
    }
    private static double arcNoise(int seed,int strand,int point,int frame,double blend,int axis) {
        double base=seed*13.13+strand*37.31+point*19.73+axis*71.91;
        double a=Math.sin(base+frame*11.73)*43758.5453;
        double b=Math.sin(base+(frame+1)*11.73)*43758.5453;
        a=(a-Math.floor(a))*2-1;b=(b-Math.floor(b))*2-1;
        return a+(b-a)*blend;
    }
    static void tube(VertexConsumer v,PoseStack pose,Vec3 from,Vec3 to,double radius,int color) {
        Vec3 d=to.subtract(from).normalize();if(d.lengthSqr()<1E-8)return;
        Vec3 u=d.cross(Math.abs(d.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize(),w=d.cross(u).normalize();
        for(int i=0;i<12;i++) {
            double a=i*Math.PI/6,b=(i+1)*Math.PI/6;
            Vec3 x=u.scale(Math.cos(a)*radius).add(w.scale(Math.sin(a)*radius)),y=u.scale(Math.cos(b)*radius).add(w.scale(Math.sin(b)*radius));
            LightningTrailRenderer.quad(v,pose,from.add(x),to.add(x),to.add(y),from.add(y),color);
        }
    }
    private static void circle(VertexConsumer v,PoseStack pose,Vec3 center,double radius,int edge,int fillAlpha) {
        center=center.add(0,.018,0);
        for(int i=0;i<64;i++) {
            double a=i*Math.PI/32,b=(i+1)*Math.PI/32;
            Vec3 x=center.add(Math.cos(a)*radius,0,Math.sin(a)*radius),y=center.add(Math.cos(b)*radius,0,Math.sin(b)*radius);
            LightningTrailRenderer.quad(v,pose,center,x,y,y,(fillAlpha<<24)|(edge&0xFFFFFF));
            LightningTrailRenderer.ribbon(v,pose,x,y,.04,edge);
        }
    }
    @Override public ResourceLocation getTextureLocation(YangJianHazard h) { return YangJianRenderer.TEXTURE; }
}
