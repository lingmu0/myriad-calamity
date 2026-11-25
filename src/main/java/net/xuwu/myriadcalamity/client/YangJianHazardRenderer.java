package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.config.MyriadConfig;
import net.xuwu.myriadcalamity.entity.YangJianHazard;
import net.xuwu.myriadcalamity.entity.YangJianHazardMath;
import org.joml.Matrix4f;

/** Geometry warnings and bounded effects share the server's exact segment or impact circle. */
public final class YangJianHazardRenderer extends CombatEntityRenderer<YangJianHazard> {
    private static final Vec3 X_AXIS = new Vec3(1, 0, 0);
    private static final Vec3 Z_AXIS = new Vec3(0, 0, 1);
    private final YangJianWeapons weapons;
    public YangJianHazardRenderer(EntityRendererProvider.Context context) { super(context);weapons=new YangJianWeapons(context.getResourceManager());shadowRadius=0; }

    static void collectBeam(YangJianHazard h,float partial,PoseStack pose,CombatGeometry buffers,Vec3 camera) {
        if(h.kind()!=1 || !h.ready() || !h.active(partial) || h.warning(partial))return;
        YangJianVisuals.eyeBeam(buffers.quads(),pose.last().pose(),h.visualStart(partial).subtract(camera),h.visualEnd(partial).subtract(camera),
            h.radius(),h.age(partial)-h.windup(),h.getId());
    }

    @Override protected AABB getBoundingBoxForCulling(YangJianHazard entity) { return entity.combatRenderBounds(); }

    @Override protected void collect(YangJianHazard h,float partial,PoseStack pose,CombatGeometry buffers,int light) {
        if(h.kind()==1 || !h.ready() || h.age(partial)<0 || (!h.persistent() && h.age(partial)>=h.windup()+h.duration()))return;
        Vec3 from=h.visualStart(partial).subtract(h.position());
        VertexConsumer v=buffers.quads();
        Matrix4f matrix=pose.last().pose();
        float charge=h.windup()==0?1:Mth.clamp(h.age(partial)/h.windup(),0,1);
        boolean warning=h.warning(partial);
        boolean showRange=MyriadConfig.showYangJianAttackIndicators();
        if(warning && showRange) {
            if(h.kind()==3) {
                YangJianVisuals.warningRing(v,matrix,from,h.radius(),charge,YangJianVisuals.RED);
            } else {
                circle(v,pose,from,h.radius(),0xEDC7E8FF,(int)(30+charge*35));
            }
        }
        float landed=h.age(partial)-h.windup();
        if(!warning && h.kind()==2) {
            double size=showRange?h.radius():Math.min(.92,h.radius()*.46);
            bladeScar(v,pose,from,size,landed,h.getId(),showRange);
        }
        if(h.kind()==2) {
            float drop=Mth.clamp((h.age(partial)-(h.windup()-5))/5,0,1);
            Vec3 tip=from.add(0,(1-drop)*8,0);
            if(warning) {
                Vec3 rift=from.add(0,8.1,0);
                double riftFade=Math.min(1,charge*2.2)*(1-drop*.5);
                YangJianVisuals.flare(v,matrix,rift,X_AXIS,Z_AXIS,.42+charge*.35,riftFade*.72,YangJianVisuals.SILVER);
                YangJianVisuals.ring(v,matrix,rift,.28+charge*.38,.045,YangJianVisuals.tint(YangJianVisuals.SILVER.core(),riftFade*.75));
            }
            if(drop>0 && warning) {
                double length=Math.min(drop*8,4.4);
                YangJianVisuals.wake(v,matrix,tip.add(0,length,0),tip,.015,.26,
                    YangJianVisuals.SILVER,0,Math.min(1,drop*3));
            }
            pose.pushPose();pose.translate(tip.x,tip.y+39.6/16*.68,tip.z);pose.scale(.68F,.68F,.68F);
            buffers.mesh(pose,RenderTypes.entityCutout(YangJianRenderer.TEXTURE),(stack,vertices)->
                weapons.renderSword(stack,vertices,15728880,OverlayTexture.NO_OVERLAY,0xFFFFFFFF));
            pose.popPose();
        } else if(!warning) {
            if(showRange) {
                jaggedRim(v,pose,from,h.radius(),landed,h.getId(),0x9AE01A2E,0xCAFF97A9,.105,.040);
            }
            int interval=h.persistent()?15+(int)(YangJianVisuals.unit(h.getId())*7):0;
            YangJianLightningEffects.strike(v,matrix,from,YangJianHazardMath.RED_THUNDER_HEIGHT,
                Math.min(2.4,h.radius()*.68),.27,landed,interval,h.getId(),YangJianVisuals.RED,1);
        }
    }

    private static void bladeScar(VertexConsumer v,PoseStack pose,Vec3 center,double radius,float age,int seed,boolean showRange) {
        double fade=Math.max(0,1-age/18.0);
        if(fade<=0)return;
        Matrix4f m=pose.last().pose();
        if(age<6) {
            double impFade=Math.pow(1-age/6.0,1.3);
            YangJianVisuals.flare(v,m,center.add(0,.12,0),X_AXIS,Z_AXIS,.45+age*.26,impFade*.9,YangJianVisuals.SILVER);
            YangJianVisuals.impactShockRing(v,m,center,.25+age*(radius*.22),.32*impFade,.26,YangJianVisuals.SILVER,impFade*.85);
            if(age<3.5) {
                double beamFade=1-age/3.5;
                YangJianVisuals.wake(v,m,center.add(0,3.8,0),center.add(0,.05,0),.02,.28*beamFade,
                    YangJianVisuals.SILVER,0,beamFade*.85);
            }
        }
        int glow=((int)(fade*78)<<24)|0xA8C8EE;
        int body=((int)(fade*172)<<24)|0xDCEBFF;
        int core=((int)(fade*245)<<24)|0xFFFFFF;
        for(int cut=0;cut<4;cut++) {
            double angle=cut*Math.PI/4+seed*.37,c=Math.cos(angle),s=Math.sin(angle);
            Vec3 axis=new Vec3(c,0,s),side=new Vec3(-s,0,c);
            double reach=radius*(cut%2==0?1.08:.78),half=(cut%2==0?.24:.18)*fade;
            for(int sign=-1;sign<=1;sign+=2) {
                Vec3 far=center.add(axis.scale(reach*sign)).add(0,.025,0);
                Vec3 near=center.add(axis.scale(.14*sign)).add(0,.12*fade,0);
                LightningTrailRenderer.quad(v,pose,near.add(side.scale(half)),far,near.subtract(side.scale(half)),near.subtract(side.scale(half)),body);
                LightningTrailRenderer.quad(v,pose,near.add(side.scale(half*.42)),far,near.subtract(side.scale(half*.42)),near.subtract(side.scale(half*.42)),core);
            }
        }
        if(showRange)jaggedRim(v,pose,center,radius*.97,age,seed,glow,core,.10,.05);
    }

    private static void jaggedRim(VertexConsumer v,PoseStack pose,Vec3 center,double radius,float age,int seed,
                                  int outer,int core,double outerWidth,double coreWidth) {
        int segments=64;
        double clock=age/2.5;int frame=(int)Math.floor(clock);
        double blend=clock-frame;blend=blend*blend*(3-2*blend);
        for(int i=0;i<segments;i++) {
            double a=i*Math.PI*2/segments,b=(i+1)*Math.PI*2/segments;
            double ra=radius*(1+.07*arcNoise(seed,i,41,frame,blend,0));
            double rb=radius*(1+.07*arcNoise(seed,(i+1)%segments,41,frame,blend,0));
            Vec3 x=center.add(Math.cos(a)*ra,.045,Math.sin(a)*ra);
            Vec3 y=center.add(Math.cos(b)*rb,.045,Math.sin(b)*rb);
            LightningTrailRenderer.ribbon(v,pose,x,y,outerWidth,outer);
            LightningTrailRenderer.ribbon(v,pose,x,y,coreWidth,core);
        }
    }
    private static double arcNoise(int seed,int strand,int point,int frame,double blend,int axis) {
        double base=seed*13.13+strand*37.31+point*19.73+axis*71.91;
        double a=Math.sin(base+frame*11.73)*43758.5453;
        double b=Math.sin(base+(frame+1)*11.73)*43758.5453;
        a=(a-Math.floor(a))*2-1;b=(b-Math.floor(b))*2-1;
        return a+(b-a)*blend;
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
}
