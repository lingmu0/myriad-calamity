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
import net.xuwu.myriadcalamity.entity.YangJianHazard;

/** Geometry warnings and bounded effects share the server's exact segment or impact circle. */
public final class YangJianHazardRenderer extends EntityRenderer<YangJianHazard> {
    private final YangJianWeapons weapons;
    public YangJianHazardRenderer(EntityRendererProvider.Context context) { super(context);weapons=new YangJianWeapons(context.getResourceManager());shadowRadius=0; }
    @Override public void render(YangJianHazard h,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        if(!h.ready() || h.age(partial)<0 || (!h.persistent() && h.age(partial)>=h.windup()+h.duration()))return;
        Vec3 from=h.start().subtract(h.position()),to=h.end().subtract(h.position());
        VertexConsumer v=buffers.getBuffer(RenderType.debugQuads());
        float charge=h.windup()==0?1:Mth.clamp(h.age(partial)/h.windup(),0,1);
        if(h.kind()==1) {
            if(h.warning(partial)) {
                // The clone sweep has an authored windup but deliberately no
                // visible warning line; the beam appears only on release.
                if(h.mode()!=3) {
                    tube(v,pose,from,to,.025+charge*.02,0xD9FFD54F);
                    tube(v,pose,from,to,h.radius(),0x18FFCC32);
                }
            } else {
                tube(v,pose,from,to,h.radius(),0x59FFC229);
                tube(v,pose,from,to,h.radius()*.52,0xBEFFE36B);
                tube(v,pose,from,to,h.radius()*.15,0xFFFFF8D6);
                // Clone passes use the same gold lightning treatment as the
                // ordinary eye beams; each published hazard is one real sweep.
                beamLightning(v,pose,from,to,h.radius(),h.age(partial),h.getId());
            }
        } else {
            boolean warning=h.warning(partial);
            int edge=h.kind()==3?0xEDFF5256:0xEDC7E8FF;
            circle(v,pose,from,h.radius(),warning?edge:0xDDE9CFFF,warning?(int)(30+charge*35):75);
            if(h.kind()==2) {
                float drop=Mth.clamp((h.age(partial)-(h.windup()-5))/5,0,1);
                // The approved blade tip is at -39.6 pixels; land that tip exactly on the warning floor.
                pose.pushPose();pose.translate(from.x,from.y+39.6/16*.68+(1-drop)*8,from.z);pose.scale(.68F,.68F,.68F);
                weapons.renderSword(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(YangJianRenderer.TEXTURE)),15728880,OverlayTexture.NO_OVERLAY,0xFFFFFFFF);
                pose.popPose();
            } else if(!warning) {
                for(int strand=0;strand<3;strand++) {
                    Vec3 last=from;
                    for(int i=1;i<=12;i++) {
                        double wobble=Math.sin(h.getId()*7.13+i*4.21+strand*1.9)*.38;
                        Vec3 next=from.add(wobble,i*.72,Math.cos(i*3.51+strand)*.3);
                        tube(v,pose,last,next,strand==0?.10:.045,strand==0?0xFFFFDFE4:0xE9FF3757);last=next;
                    }
                }
            }
        }
        super.render(h,yaw,partial,pose,buffers,light);
    }
    private static void beamLightning(VertexConsumer v,PoseStack pose,Vec3 from,Vec3 to,double radius,float age,int seed) {
        Vec3 route=to.subtract(from);double length=route.length();if(length<1E-6)return;
        Vec3 direction=route.scale(1/length);
        Vec3 side=direction.cross(Math.abs(direction.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
        Vec3 up=direction.cross(side).normalize();
        // Bounded geometry, with noise blended over three ticks instead of jumping every rendered frame.
        int pieces=Math.clamp((int)Math.ceil(length*1.25),4,24);
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
