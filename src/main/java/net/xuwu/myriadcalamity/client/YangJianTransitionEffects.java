package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.entity.YangJian;
import net.xuwu.myriadcalamity.entity.YangJianTransition;
import org.joml.Matrix4f;

/** Axe lightning follows its authored grip; jump cues stay anchored to the platform floor. */
final class YangJianTransitionEffects {
    static void weapon(YangJian boss,YangJianModel model,float partial,VertexConsumer v,Matrix4f m) {
        float age=boss.actionAge(partial);
        if(age<YangJianTransition.SUMMON_START || age>=YangJianTransition.DURATION)return;
        float yaw=Mth.rotLerp(partial,boss.yBodyRotO,boss.yBodyRot);
        float charge=(float)Math.min(1,YangJianTransition.weaponScale(age)/3);
        Vec3 grip=model.heldWeaponPoint(yaw,0,0,0);
        Vec3 crown=model.heldWeaponPoint(yaw,0,-47F/16,0);
        Vec3 left=model.heldWeaponPoint(yaw,-16F/16,-33F/16,0);
        Vec3 right=model.heldWeaponPoint(yaw,16F/16,-33F/16,0);
        lightning(v,m,grip,crown,age,.12*charge,alpha(200*charge,0xFFF2A9));
        lightning(v,m,left,crown,age+11,.16*charge,alpha(235*charge,0xFFF8D8));
        lightning(v,m,crown,right,age+29,.16*charge,alpha(235*charge,0xFFF8D8));
        for(int i=0;i<4;i++) {
            double turn=age*.05+i*Math.PI/2;
            Vec3 end=crown.add(Math.cos(turn)*.72*charge,.38*Math.sin(turn*2),Math.sin(turn)*.72*charge);
            lightning(v,m,crown,end,age+i*17,.10*charge,alpha(160*charge,0xFFD65B));
        }
        // Sample the same baked animation at earlier instants rather than drawing an unrelated arc.
        if(age>=YangJianTransition.SWEEP_START && age<YangJianTransition.IMPACT+4) {
            float head=Math.min(age,YangJianTransition.IMPACT);
            float tail=Math.max(YangJianTransition.SWEEP_START,head-8);
            float fade=age>YangJianTransition.IMPACT?1-(age-YangJianTransition.IMPACT)/4:1;
            for(int i=0;i<20;i++) {
                float a=Mth.lerp(i/20F,tail,head),b=Mth.lerp((i+1)/20F,tail,head);
                Vec3 offsetA=new Vec3(0,YangJianTransition.height(a)-YangJianTransition.height(age),0);
                Vec3 offsetB=new Vec3(0,YangJianTransition.height(b)-YangJianTransition.height(age),0);
                Vec3 outerA=model.transitionWeaponPoint(a,yaw,0,-47F/16,0).add(offsetA);
                Vec3 outerB=model.transitionWeaponPoint(b,yaw,0,-47F/16,0).add(offsetB);
                Vec3 innerA=model.transitionWeaponPoint(a,yaw,0,-29F/16,0).add(offsetA);
                Vec3 innerB=model.transitionWeaponPoint(b,yaw,0,-29F/16,0).add(offsetB);
                int color=alpha(fade*(i+1)/20F*125,0xFFE29A);
                point(v,m,innerA,color);point(v,m,outerA,color);point(v,m,outerB,color);point(v,m,innerB,color);
                beam(v,m,outerA,outerB,.048,alpha(fade*(i+1)/20F*235,0xFFF8DF));
            }
        }
    }

    /** Called from the world render stage, including when the airborne boss is outside the camera. */
    static void ground(YangJian boss,float partial,PoseStack pose,MultiBufferSource buffers,Vec3 camera) {
        float age=boss.actionAge(partial);
        if(age<0 || age>=YangJianTransition.DURATION)return;
        Vec3 center=boss.transitionCenter();
        double radius=boss.transitionRadius();
        if(!Double.isFinite(center.x+center.y+center.z+radius) || radius<=0 || radius>40)return;
        pose.pushPose();pose.translate(center.x-camera.x,center.y-camera.y,center.z-camera.z);
        VertexConsumer v=buffers.getBuffer(RenderType.debugQuads());
        Matrix4f m=pose.last().pose();
        if(age>=YangJianTransition.SUMMON_START && age<YangJianTransition.IMPACT) {
            float progress=Mth.clamp((age-YangJianTransition.SUMMON_START)
                /(YangJianTransition.IMPACT-YangJianTransition.SUMMON_START),0,1);
            disk(v,m,radius,.048,alpha(24+progress*36,0xD79D15));
            ring(v,m,radius,.085,.061,alpha(170+progress*75,0xFFE38B));
            for(int i=1;i<=3;i++)ring(v,m,radius*i/4,.035,.058,alpha(40+progress*55,0xFFE39A));
            // Closing rings count down the arena-wide strike without implying a safe patch.
            ring(v,m,radius*(1-progress),.095,.071,alpha(135+progress*95,0xFFF6C7));
        }
        if(age>=YangJianTransition.IMPACT && age<YangJianTransition.IMPACT+10) {
            double fade=1-(age-YangJianTransition.IMPACT)/10;
            disk(v,m,radius,.07,alpha(135*fade,0xFFF3B5));
            ring(v,m,radius,.16,.10,alpha(245*fade,0xFFFDEB));
            for(int i=0;i<24;i++) {
                double heading=i*Math.PI/12;
                Vec3 end=new Vec3(Math.cos(heading)*radius,.12,Math.sin(heading)*radius);
                lightning(v,m,new Vec3(0,.12,0),end,age+i*13,.13,alpha(205*fade,0xFFF7CF));
            }
        }
        if(age>=YangJianTransition.IMPACT+10 && age<YangJianTransition.WAVE_START) {
            double progress=(age-YangJianTransition.IMPACT-10)/(YangJianTransition.WAVE_START-YangJianTransition.IMPACT-10);
            ring(v,m,.55+progress*1.05,.055,.12,alpha(95+progress*130,0xFFE69B));
            ring(v,m,.45,.09,.08,alpha(90+progress*120,0xFFF9D1));
        }
        double wave=YangJianTransition.waveRadius(age);
        if(wave>=0) {
            double inner=Math.max(0,wave-YangJianTransition.WAVE_HALF_WIDTH);
            double outer=Math.min(radius,wave+YangJianTransition.WAVE_HALF_WIDTH);
            if(outer>inner) {
                ring(v,m,(inner+outer)/2,(outer-inner)/2,.095,alpha(115,0xF4C153));
                ring(v,m,wave,.10,.32,alpha(245,0xFFF9DA));
                ring(v,m,inner,.045,.13,alpha(150,0xFFD974));
                waveWall(v,m,inner,outer);
                for(int i=0;i<48;i++) {
                    double a=i*Math.PI/24,b=(i+1)*Math.PI/24;
                    Vec3 start=new Vec3(Math.cos(a)*wave,.25,Math.sin(a)*wave);
                    Vec3 end=new Vec3(Math.cos(b)*wave,.25,Math.sin(b)*wave);
                    lightning(v,m,start,end,age+i*7,.055,alpha(220,0xFFF8CB));
                }
            }
        }
        pose.popPose();
    }

    private static void waveWall(VertexConsumer v,Matrix4f m,double inner,double outer) {
        // Maximum visual crest .35 matches the server's .45-block jumpable ground band.
        for(int i=0;i<128;i++) {
            double a=i*Math.PI/64,b=(i+1)*Math.PI/64,crest=(inner+outer)/2;
            Vec3 lowA=new Vec3(Math.cos(a)*outer,.075,Math.sin(a)*outer);
            Vec3 lowB=new Vec3(Math.cos(b)*outer,.075,Math.sin(b)*outer);
            Vec3 highA=new Vec3(Math.cos(a)*crest,.35,Math.sin(a)*crest);
            Vec3 highB=new Vec3(Math.cos(b)*crest,.35,Math.sin(b)*crest);
            point(v,m,lowA,0x55F7C85F);point(v,m,highA,0xBFFFEAA9);
            point(v,m,highB,0xBFFFEAA9);point(v,m,lowB,0x55F7C85F);
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
        Vec3 direction=end.subtract(start).normalize();
        Vec3 side=direction.cross(Math.abs(direction.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
        Vec3 previous=start;
        double frame=Math.floor(time*.5);
        for(int i=1;i<=7;i++) {
            Vec3 next=start.lerp(end,i/7D);
            if(i<7)next=next.add(side.scale(Math.sin(frame*2.41+i*6.37)*jitter));
            beam(v,m,previous,next,.026,color);
            previous=next;
        }
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
