package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.config.MyriadConfig;
import net.xuwu.myriadcalamity.entity.YangJian;
import net.xuwu.myriadcalamity.entity.YangJianTransition;
import org.joml.Matrix4f;

/** Giant Thunder Axe transition VFX: ascent updraft, converging axe lightning, 360° golden swath, and dual jumpable waves. */
final class YangJianTransitionEffects {
    private static final Vec3 X_AXIS = new Vec3(1, 0, 0);
    private static final Vec3 Z_AXIS = new Vec3(0, 0, 1);

    static void weapon(YangJian boss,YangJianModel model,float partial,VertexConsumer v,Matrix4f m) {
        float age=boss.actionAge(partial);
        if(age<0 || age>=YangJianTransition.DURATION)return;

        // 1. Celestial updraft helix while Yang Jian ascends into the cloud sky (0..28)
        if(age<YangJianTransition.SUMMON_START+6) {
            float asc=Mth.clamp(age/YangJianTransition.ASCEND_END,0,1);
            double fade=age<YangJianTransition.ASCEND_END?Math.sin(asc*Math.PI*.85+.15):Math.max(0,1-(age-YangJianTransition.ASCEND_END)/6.0);
            for(int strand=0;strand<3;strand++) {
                Vec3 prev=null;
                for(int i=0;i<=10;i++) {
                    double t=i/10.0;
                    double ang=strand*Math.PI*2/3+age*.28+t*Math.PI*1.8;
                    double rad=1.35*(1-t*.48);
                    Vec3 p=new Vec3(Math.cos(ang)*rad,t*3.6-.3,Math.sin(ang)*rad);
                    if(prev!=null) {
                        YangJianVisuals.wake(v,m,prev,p,.03,.075,YangJianVisuals.GOLD,fade*t*.6,fade*t);
                    }
                    prev=p;
                }
            }
            YangJianVisuals.ring(v,m,new Vec3(0,.15,0),.8+asc*1.6,.065,alpha(165*fade,0xFFE58C));
        }

        if(age<YangJianTransition.SUMMON_START)return;
        float yaw=Mth.rotLerp(partial,boss.yBodyRotO,boss.yBodyRot);
        float charge=(float)Math.min(1,YangJianTransition.weaponScale(age)/3);
        Vec3 grip=model.heldWeaponPoint(yaw,0,0,0);
        Vec3 crown=model.heldWeaponPoint(yaw,0,-47F/16,0);
        Vec3 left=model.heldWeaponPoint(yaw,-16F/16,-33F/16,0);
        Vec3 right=model.heldWeaponPoint(yaw,16F/16,-33F/16,0);

        // 2. Converging sky-to-axe materialization lightning & halo burst (24..42)
        if(age<YangJianTransition.SUMMON_END+4) {
            float st=Mth.clamp((age-YangJianTransition.SUMMON_START)/(float)(YangJianTransition.SUMMON_END-YangJianTransition.SUMMON_START),0,1);
            double sf=age<=YangJianTransition.SUMMON_END?1:Math.max(0,1-(age-YangJianTransition.SUMMON_END)/4.0);
            YangJianVisuals.flare(v,m,crown,X_AXIS,Z_AXIS,.45+st*1.35,sf*.95,YangJianVisuals.GOLD);
            YangJianVisuals.ring(v,m,crown,2.4*(1-st*.65),.08,alpha(200*sf,0xFFF0A6));
            for(int i=0;i<4;i++) {
                double a=i*Math.PI/2+age*.14;
                Vec3 outer=crown.add(Math.cos(a)*(2.8-st*1.5),1.8-st*.9,Math.sin(a)*(2.8-st*1.5));
                YangJianVisuals.bolt(v,m,outer,crown,.055,.32,age+i*5,boss.getId()+i*23,YangJianVisuals.GOLD,sf*.85,false);
            }
        }

        // Crackling giant thunder-axe weapon aura
        lightning(v,m,grip,crown,age,.14*charge,alpha(215*charge,0xFFF2A9));
        lightning(v,m,left,crown,age+11,.18*charge,alpha(245*charge,0xFFF8D8));
        lightning(v,m,crown,right,age+29,.18*charge,alpha(245*charge,0xFFF8D8));
        lightning(v,m,left,right,age+41,.20*charge,alpha(190*charge,0xFFDA75));
        for(int i=0;i<5;i++) {
            double turn=age*.07+i*Math.PI*2/5;
            Vec3 end=crown.add(Math.cos(turn)*.92*charge,.45*Math.sin(turn*2),Math.sin(turn)*.92*charge);
            lightning(v,m,crown,end,age+i*17,.12*charge,alpha(180*charge,0xFFD65B));
        }

        // 3. Colossal multi-layered 360° giant axe sweep trail (40..76)
        if(age>=YangJianTransition.SWEEP_START && age<YangJianTransition.IMPACT+5) {
            float head=Math.min(age,YangJianTransition.IMPACT);
            float tail=Math.max(YangJianTransition.SWEEP_START,head-10);
            float fade=age>YangJianTransition.IMPACT?1-(age-YangJianTransition.IMPACT)/5F:1;
            int samples=24;
            for(int i=0;i<samples;i++) {
                float fA=i/(float)samples, fB=(i+1)/(float)samples;
                float a=Mth.lerp(fA,tail,head), b=Mth.lerp(fB,tail,head);
                Vec3 offsetA=new Vec3(0,YangJianTransition.height(a)-YangJianTransition.height(age),0);
                Vec3 offsetB=new Vec3(0,YangJianTransition.height(b)-YangJianTransition.height(age),0);
                Vec3 outerA=model.transitionWeaponPoint(a,yaw,0,-47F/16,0).add(offsetA);
                Vec3 outerB=model.transitionWeaponPoint(b,yaw,0,-47F/16,0).add(offsetB);
                Vec3 innerA=model.transitionWeaponPoint(a,yaw,0,-26F/16,0).add(offsetA);
                Vec3 innerB=model.transitionWeaponPoint(b,yaw,0,-26F/16,0).add(offsetB);
                YangJianVisuals.bladeArc(v,m,innerA,outerA,innerB,outerB,
                    YangJianVisuals.GOLD,fade*fA*1.15,fade*fB*1.15);
                beam(v,m,outerA,outerB,.055,alpha(fade*fB*245,0xFFFCE6));
                if(i%6==4 && fade>.4) {
                    Vec3 sparkTip=outerB.add(outerB.subtract(innerB).normalize().scale(.85));
                    YangJianVisuals.bolt(v,m,outerB,sparkTip,.038,.22,age+i,boss.getId()+i*19,
                        YangJianVisuals.GOLD,fade*fB*.85,false);
                }
            }
        }
    }

    /** Called from the world render stage, including when the airborne boss is outside the camera. */
    static void ground(YangJian boss,float partial,PoseStack pose,CombatGeometry buffers,Vec3 camera) {
        float age=boss.actionAge(partial);
        if(age<0 || age>=YangJianTransition.DURATION)return;
        Vec3 center=boss.transitionCenter();
        double radius=boss.transitionRadius();
        if(!Double.isFinite(center.x+center.y+center.z+radius) || radius<=0 || radius>40)return;
        pose.pushPose();pose.translate(center.x-camera.x,center.y-camera.y,center.z-camera.z);
        VertexConsumer v=buffers.quads();
        Matrix4f m=pose.last().pose();
        boolean showRange=MyriadConfig.showYangJianAttackIndicators();

        // Subtle golden takeoff ring under the boss at center while ascending
        if(age<YangJianTransition.ASCEND_END) {
            float t=age/YangJianTransition.ASCEND_END;
            double f=(1-t)*.85;
            YangJianVisuals.impactShockRing(v,m,Vec3.ZERO,1.2+t*5.5,.28*(1-t),.35,YangJianVisuals.GOLD,f);
        }

        if(showRange && age>=YangJianTransition.SUMMON_START && age<YangJianTransition.IMPACT) {
            float progress=Mth.clamp((age-YangJianTransition.SUMMON_START)
                /(YangJianTransition.IMPACT-YangJianTransition.SUMMON_START),0,1);
            disk(v,m,radius,.048,alpha(24+progress*36,0xD79D15));
            ring(v,m,radius,.085,.061,alpha(170+progress*75,0xFFE38B));
            for(int i=1;i<=3;i++)ring(v,m,radius*i/4,.035,.058,alpha(40+progress*55,0xFFE39A));
            ring(v,m,radius*(1-progress),.095,.071,alpha(135+progress*95,0xFFF6C7));
        }

        // Arena-wide golden thunder impact detonation (72..84)
        float impactAge=age-YangJianTransition.IMPACT;
        if(impactAge>=0 && impactAge<12) {
            YangJianLightningEffects.strike(v,m,Vec3.ZERO,16,3.8,.36,impactAge,0,boss.getId(),
                YangJianVisuals.GOLD,1);
            double burstFade=Math.pow(1-impactAge/12.0,1.25);
            // Secondary thunder bolts flanking the colossal axe head
            for(int i=0;i<4;i++) {
                double a=i*Math.PI/2+.4;
                Vec3 pt=new Vec3(Math.cos(a)*2.6,0,Math.sin(a)*2.6);
                YangJianLightningEffects.strike(v,m,pt,13,1.9,.18,impactAge,0,boss.getId()+i*53,
                    YangJianVisuals.GOLD,burstFade*.78);
            }
            // Expanding golden blast ripple at center
            YangJianVisuals.impactShockRing(v,m,Vec3.ZERO,1.0+impactAge*.92,.45*burstFade,.55,
                YangJianVisuals.GOLD,burstFade);
            if(showRange && impactAge<10) {
                double fade=1-impactAge/10;
                disk(v,m,radius,.07,alpha(105*fade,0xFFF3B5));
                ring(v,m,radius,.16,.10,alpha(230*fade,0xFFFDEB));
            }
        }

        // Dual jumpable molten-gold seismic shockwaves (exact .35 visual crest height)
        double wave=YangJianTransition.waveRadius(age);
        if(wave>=0) {
            double inner=Math.max(0,wave-YangJianTransition.WAVE_HALF_WIDTH);
            double outer=Math.min(radius,wave+YangJianTransition.WAVE_HALF_WIDTH);
            if(outer>inner) {
                ring(v,m,(inner+outer)/2,(outer-inner)/2,.085,alpha(128,0xF4C153));
                ring(v,m,wave,.11,.35,alpha(250,0xFFFBE2));
                ring(v,m,inner,.05,.12,alpha(155,0xFFD974));
                ring(v,m,outer,.05,.09,alpha(130,0xFFB940));
                waveWall(v,m,inner,outer);
                for(int i=0;i<48;i++) {
                    double a=i*Math.PI/24,b=(i+1)*Math.PI/24;
                    Vec3 start=new Vec3(Math.cos(a)*wave,.28,Math.sin(a)*wave);
                    Vec3 end=new Vec3(Math.cos(b)*wave,.28,Math.sin(b)*wave);
                    lightning(v,m,start,end,age+i*7,.065,alpha(235,0xFFF9D2));
                }
            }
        }
        pose.popPose();
    }

    private static void waveWall(VertexConsumer v,Matrix4f m,double inner,double outer) {
        // Dual-sloped wave crest: front ramp + rear tail, peaking at .35 (matching .45 jumpable ground band).
        double crest=(inner+outer)/2;
        for(int i=0;i<128;i++) {
            double a=i*Math.PI/64,b=(i+1)*Math.PI/64;
            double ca=Math.cos(a),sa=Math.sin(a),cb=Math.cos(b),sb=Math.sin(b);
            Vec3 outA=new Vec3(ca*outer,.065,sa*outer), outB=new Vec3(cb*outer,.065,sb*outer);
            Vec3 highA=new Vec3(ca*crest,.35,sa*crest), highB=new Vec3(cb*crest,.35,sb*crest);
            Vec3 inA=new Vec3(ca*inner,.065,sa*inner), inB=new Vec3(cb*inner,.065,sb*inner);
            // Front rising molten crest
            point(v,m,outA,0x66F7B844);point(v,m,highA,0xE8FFF6C8);
            point(v,m,highB,0xE8FFF6C8);point(v,m,outB,0x66F7B844);
            // Trailing sloped energy tail
            point(v,m,highA,0xC8FFE28A);point(v,m,inA,0x22E89B2A);
            point(v,m,inB,0x22E89B2A);point(v,m,highB,0xC8FFE28A);
        }
    }
    private static void disk(VertexConsumer v,Matrix4f m,double radius,double y,int color) {
        for(int i=0;i<128;i++) {
            double a=i*Math.PI/64,b=(i+1)*Math.PI/64;
            point(v,m,new Vec3(0,y,0),color);
            point(v,m,new Vec3(Math.cos(a)*radius,y,Math.sin(a)*radius),color);
            Vec3 end=new Vec3(Math.cos(b)*radius,y,Math.sin(b)*radius);
            point(v,m,end,color);point(v,m,end,color);
        }
    }
    private static void ring(VertexConsumer v,Matrix4f m,double radius,double width,double y,int color) {
        double inner=Math.max(0,radius-width),outer=radius+width;
        for(int i=0;i<128;i++) {
            double a=i*Math.PI/64,b=(i+1)*Math.PI/64;
            point(v,m,new Vec3(Math.cos(a)*inner,y,Math.sin(a)*inner),color);
            point(v,m,new Vec3(Math.cos(a)*outer,y,Math.sin(a)*outer),color);
            point(v,m,new Vec3(Math.cos(b)*outer,y,Math.sin(b)*outer),color);
            point(v,m,new Vec3(Math.cos(b)*inner,y,Math.sin(b)*inner),color);
        }
    }
    private static void lightning(VertexConsumer v,Matrix4f m,Vec3 start,Vec3 end,double time,double jitter,int color) {
        int seed=(int)(start.x*113+start.y*37+start.z*81+end.x*179+end.z*23);
        YangJianVisuals.bolt(v,m,start,end,.050,jitter*2,time,seed,
            YangJianVisuals.GOLD,(color>>>24)/255.0,false);
    }
    private static void beam(VertexConsumer v,Matrix4f m,Vec3 from,Vec3 to,double width,int color) {
        Vec3 direction=to.subtract(from).normalize();
        if(direction.lengthSqr()<1E-10)return;
        Vec3 side=direction.cross(Math.abs(direction.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize().scale(width);
        Vec3 up=direction.cross(side).normalize().scale(width);
        for(Vec3 axis:new Vec3[]{side,up}) {
            point(v,m,from.subtract(axis),color);point(v,m,from.add(axis),color);
            point(v,m,to.add(axis),color);point(v,m,to.subtract(axis),color);
        }
    }
    private static int alpha(double value,int rgb) {return (Mth.clamp((int)value,0,255)<<24)|rgb;}
    private static void point(VertexConsumer v,Matrix4f m,Vec3 p,int color) {
        v.addVertex(m,(float)p.x,(float)p.y,(float)p.z).setColor(color);
    }
    private YangJianTransitionEffects() {}
}
