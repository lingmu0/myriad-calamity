package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.entity.YangJian;
import net.xuwu.myriadcalamity.entity.YangJianSkill;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Small immediate weapon effects; persistent damaging trails have their own server entities. */
final class YangJianCombatEffects {
    static void render(YangJian boss,YangJianModel model,float partial,PoseStack pose,MultiBufferSource buffers) {
        VertexConsumer v=buffers.getBuffer(RenderType.debugQuads());
        Matrix4f m=pose.last().pose();
        float age=boss.actionAge(partial);
        if(boss.phase()==3 || boss.action()==YangJian.THIRD_EYE_OPEN)divine(boss,model,partial,age,v,m);
        if(boss.action()==YangJian.SWEEP_BEAM && boss.hasSweepClone()) {
            sweepCloneVeil(boss,partial,pose,v);
            // Switching from debug quads to the translucent model ends the
            // current BufferBuilder. Reacquire debug quads before any later
            // effect writes to it.
            sweepCloneModel(boss,model,partial,pose,buffers);
            v=buffers.getBuffer(RenderType.debugQuads());
        }
        if(boss.phase()==2 && boss.axeTrailCharges()>0)axeAftershock(boss,partial,v,m);
        if(boss.weapon()==3 && !boss.weaponThrown())whip(boss,model,partial,v,m);
        if(boss.action()==YangJian.INVISIBLE_DASH && (age>=6 && age<11 || age>=18 && age<24)) {
            float t=age<11?(age-6)/5:(age-18)/6;
            ring(v,m,Vec3.ZERO,.55+t*.95,.035,((int)((1-t)*190)<<24)|0xB3EFFF);
            for(int i=0;i<6;i++) {
                double a=i*Math.PI/3;
                Vec3 start=new Vec3(Math.cos(a)*.68,.08,Math.sin(a)*.68);
                beam(v,m,start,start.add(0,3.2*(1-t),0),.025,((int)((1-t)*145)<<24)|0xD5F8FF);
            }
        }
        if(boss.action()==YangJian.TRANSITION)YangJianTransitionEffects.weapon(boss,model,partial,v,m);
        YangJianSkill skill=YangJianSkill.forAction(boss.action());
        if(skill==null)return;
        float active=boss.stepAge(partial)-boss.stepWindup();
        if(boss.action()==YangJian.DRAW_SLASH && active>=0 && active<4) {
            double heading=Math.atan2(boss.dashEnd().z-boss.dashStart().z,boss.dashEnd().x-boss.dashStart().x);
            double radius=boss.telegraphRadius();
            int color=((int)((1-active/4)*205)<<24)|0xDEF8FF;
            for(int i=0;i<28;i++) {
                double a=heading-Math.toRadians(80)+Math.toRadians(160)*i/28;
                double b=heading-Math.toRadians(80)+Math.toRadians(160)*(i+1)/28;
                beam(v,m,new Vec3(Math.cos(a)*radius,1.05,Math.sin(a)*radius),
                    new Vec3(Math.cos(b)*radius,1.05,Math.sin(b)*radius),.045,color);
            }
        }
        if((boss.action()==YangJian.AXE_SLAM || boss.action()==YangJian.AXE_COMBO && boss.comboStep()==3)
                && active>=0 && active<7) {
            Vec3 position=new Vec3(Mth.lerp(partial,boss.xOld,boss.getX()),Mth.lerp(partial,boss.yOld,boss.getY()),Mth.lerp(partial,boss.zOld,boss.getZ()));
            Vec3 center=boss.attackPoint().subtract(position);
            float fade=1-active/7;
            ring(v,m,center,Math.max(.4,boss.telegraphRadius()*active/7),.075,((int)(fade*220)<<24)|0xCDF6FF);
            for(int i=0;i<8;i++) {
                double a=i*Math.PI/4;
                Vec3 end=center.add(Math.cos(a)*boss.telegraphRadius(),.12,Math.sin(a)*boss.telegraphRadius());
                Vec3 mid=center.lerp(end,.55).add(0,.4*fade,0);
                beam(v,m,center.add(0,.2,0),mid,.025,((int)(fade*210)<<24)|0xA8E8FF);
                beam(v,m,mid,end,.018,((int)(fade*170)<<24)|0xD6F8FF);
            }
        }
    }

    private static void divine(YangJian boss,YangJianModel model,float partial,float age,VertexConsumer v,Matrix4f m) {
        float glow=boss.action()==YangJian.THIRD_EYE_OPEN?Mth.clamp((age-18)/35,0,1):1;
        glow*=1-Mth.clamp((boss.deathTime+partial)/20,0,1);
        double time=boss.tickCount+partial;
        for(int i=0;i<6;i++) {
            double angle=i*Math.PI/3+time*.013,height=.3+(time*.025+i*.47)%2.9;
            Vec3 spark=new Vec3(Math.cos(angle)*.82,height,Math.sin(angle)*.82);
            beam(v,m,spark,spark.add(0,.15,0),.018,((int)(glow*125)<<24)|0xF9A8CD);
        }
        if(boss.action()==YangJian.THIRD_EYE_OPEN && age>=22 && age<68) {
            float envelope=Mth.sin((age-22)/46*Mth.PI);
            ring(v,m,Vec3.ZERO,1+(age-22)*.06,.055,((int)(envelope*170)<<24)|0xF8A4BC);
        }
        if(boss.action()==YangJian.DIVINE_SWEEP && age>=20 && age<33) {
            double heading=Math.atan2(boss.dashEnd().z-boss.dashStart().z,boss.dashEnd().x-boss.dashStart().x);
            double arc=Math.toRadians(boss.telegraphAngle()),reach=boss.telegraphRadius();
            double progress=Mth.clamp((age-20)/10,0,1);
            float fade=age>30?1-(age-30)/3:1;
            double swept=Math.min(arc*progress,Math.toRadians(75));
            double height=YangJianSkill.DIVINE_SWEEP_HEIGHT;
            // The spear is part of the animated rig. Sample its actual tip so
            // the lightning begins at the blade instead of at the caster's
            // centre. The hand-height plane remains shared with the server
            // hitbox; only the horizontal tip offset is taken from the model.
            float bodyYaw=Mth.rotLerp(partial,boss.yBodyRotO,boss.yBodyRot);
            Vec3 sampledGrip=model.heldWeaponPoint(bodyYaw,0,-10.8F/16F,0);
            Vec3 sampledTip=model.heldWeaponPoint(bodyYaw,0,-70.135F/16F,0);
            double expectedFront=heading+arc*.5-swept;
            Vec3 weaponAxis=sampledTip.subtract(sampledGrip).multiply(1,0,1);
            boolean finiteAxis=Double.isFinite(weaponAxis.x) && Double.isFinite(weaponAxis.z)
                && weaponAxis.horizontalDistanceSqr()>.0001;
            double front=expectedFront;
            if(finiteAxis) {
                // The authored weapon forward axis is a quarter-turn clockwise
                // from the in-game horizontal sweep axis. Rotate it back
                // counter-clockwise so the bolt leaves the visible spear tip.
                weaponAxis=new Vec3(weaponAxis.z,0,-weaponAxis.x);
                front=Math.atan2(weaponAxis.z,weaponAxis.x);
            } else weaponAxis=new Vec3(Math.cos(expectedFront),0,Math.sin(expectedFront));
            boolean finiteTip=Double.isFinite(sampledTip.x) && Double.isFinite(sampledTip.z);
            double tipRadius=finiteTip?Math.hypot(sampledTip.x,sampledTip.z):Double.NaN;
            if(!Double.isFinite(tipRadius) || tipRadius<.7 || tipRadius>=reach-.35) {
                tipRadius=Math.clamp(reach-2.4,.7,Math.max(.7,reach-.35));
                front=expectedFront;
                weaponAxis=new Vec3(Math.cos(front),0,Math.sin(front));
                sampledTip=new Vec3(Math.cos(front)*tipRadius,0,Math.sin(front)*tipRadius);
            }
            double lightningLength=Math.max(1.6,reach-tipRadius);
            double outerReach=tipRadius+lightningLength;
            // Keep the trailing arc tied to the current tip direction. This
            // compensates for the model's local forward axis and mirror, which
            // otherwise produces the same quarter-turn laser deflection bug.
            double visualHeading=front-arc*.5+swept;
            for(int i=0;i<18;i++) {
                double a=visualHeading+arc*.5-swept*i/18,b=visualHeading+arc*.5-swept*(i+1)/18;
                int alpha=(int)(fade*(.62F+.38F*Mth.sin((float)(time*.92+i*1.73)))*225);
                double middle=(a+b)*.5;
                Vec3 from=new Vec3(Math.cos(middle)*tipRadius,height,Math.sin(middle)*tipRadius);
                Vec3 to=new Vec3(Math.cos(middle)*outerReach,height,Math.sin(middle)*outerReach);
                redLightning(v,m,from,to,.105,(alpha<<24)|0xD20F2D,(Math.min(255,alpha+34)<<24)|0xFFE1E7,time*.72+i*2.3);
                // Short branches make the sweep read as a moving red lightning
                // arc rather than a static ribbon around the arena.
                if((i&2)==0) {
                    Vec3 fork=from.lerp(to,.58);
                    double forkAngle=middle+.22*Math.sin(time*.81+i*2.1);
                    Vec3 forkEnd=fork.add(Math.cos(forkAngle)*(.45+.14*Math.sin(time+i)),
                        .18*Math.sin(time*1.13+i*.9),Math.sin(forkAngle)*(.45+.14*Math.sin(time+i)));
                    redLightning(v,m,fork,forkEnd,.045,(Math.min(220,alpha+8)<<24)|0xB50A27,
                        (Math.min(255,alpha+26)<<24)|0xFFD0D8,time*1.08+i*3.7);
                }
            }
            Vec3 tip=new Vec3(sampledTip.x,height,sampledTip.z);
            Vec3 tipEnd=tip.add(weaponAxis.normalize().scale(lightningLength));
            redLightning(v,m,tip,tipEnd,
                .06,((int)(fade*205)<<24)|0xD51030,(int)(fade*235)<<24|0xFFE2E7,time*1.2+9.0);
        }
    }

    /** Draws a branching, time-varying bolt between two sweep points. */
    private static void redLightning(VertexConsumer v,Matrix4f m,Vec3 from,Vec3 to,double width,
        int outerColor,int coreColor,double seed) {
        Vec3 delta=to.subtract(from);double length=delta.length();
        if(length<1E-4)return;
        Vec3 axis=delta.scale(1/length);
        Vec3 reference=Math.abs(axis.y)>.92?new Vec3(1,0,0):new Vec3(0,1,0);
        Vec3 side=axis.cross(reference).normalize();
        Vec3 vertical=side.cross(axis).normalize();
        Vec3 previous=from;
        for(int i=1;i<=5;i++) {
            double t=i/5D;
            Vec3 point=i==5?to:from.lerp(to,t)
                .add(side.scale(Math.sin(seed+i*2.17)*.18*(1-t)))
                .add(vertical.scale(Math.cos(seed*.83+i*1.61)*.12*(1-t)));
            beam(v,m,previous,point,width*(.82+.18*Math.sin(seed+i)),outerColor);
            beam(v,m,previous,point,width*.28,coreColor);
            previous=point;
        }
    }

    private static void sweepCloneModel(YangJian boss,YangJianModel model,float partial,PoseStack pose,MultiBufferSource buffers) {
        Vec3 now=new Vec3(Mth.lerp(partial,boss.xOld,boss.getX()),Mth.lerp(partial,boss.yOld,boss.getY()),Mth.lerp(partial,boss.zOld,boss.getZ()));
        Vec3 clone=boss.sweepClone();
        pose.pushPose();pose.translate(clone.x-now.x,clone.y-now.y,clone.z-now.z);
        pose.mulPose(new Quaternionf().rotationY((180-boss.sweepCloneYaw())*Mth.DEG_TO_RAD));
        // LivingEntityRenderer applies this mirror and baseline translation
        // before rendering the real model. Repeating it keeps the apparition
        // upright instead of turning the whole rig upside down.
        pose.scale(-1F,-1F,1F);pose.scale(YangJianRenderer.MODEL_SCALE,YangJianRenderer.MODEL_SCALE,YangJianRenderer.MODEL_SCALE);
        pose.translate(0,-1.501,0);
        VertexConsumer ghost=buffers.getBuffer(RenderType.entityTranslucent(YangJianRenderer.TEXTURE));
        model.renderToBuffer(pose,ghost,15728880,OverlayTexture.NO_OVERLAY,0x35FFFFFF);
        pose.popPose();
    }

    private static void sweepCloneVeil(YangJian boss,float partial,PoseStack pose,VertexConsumer v) {
        Vec3 now=new Vec3(Mth.lerp(partial,boss.xOld,boss.getX()),Mth.lerp(partial,boss.yOld,boss.getY()),Mth.lerp(partial,boss.zOld,boss.getZ()));
        Vec3 clone=boss.sweepClone();float pulse=.72F+.28F*Mth.sin((boss.tickCount+partial)*.42F);
        pose.pushPose();pose.translate(clone.x-now.x,clone.y-now.y,clone.z-now.z);
        Matrix4f m=pose.last().pose();
        int alpha=(int)(42+28*pulse);
        ring(v,m,Vec3.ZERO,.55+.08*pulse,.035,(alpha<<24)|0xB3EFFF);
        for(int i=0;i<6;i++) {
            double angle=i*Math.PI/3+(boss.tickCount+partial)*.06;
            Vec3 start=new Vec3(Math.cos(angle)*.68,.08,Math.sin(angle)*.68);
            beam(v,m,start,start.add(0,3.2*pulse,0),.025,((int)(55*pulse)<<24)|0xD5F8FF);
        }
        pose.popPose();
    }

    private static void axeAftershock(YangJian boss,float partial,VertexConsumer v,Matrix4f m) {
        int charges=boss.axeTrailCharges();
        float pulse=.82F+.18F*Mth.sin((boss.tickCount+partial)*.28F);
        ring(v,m,new Vec3(0,1.05,0),.62+.08*pulse,.035,((int)(190*pulse)<<24)|0xD8F5FF);
        double phase=(boss.tickCount+partial)*.12;
        for(int i=0;i<charges;i++) {
            double a=phase+i*Math.PI*2/charges;
            Vec3 from=new Vec3(Math.cos(a)*.45,1.02,Math.sin(a)*.45);
            Vec3 to=new Vec3(Math.cos(a+.42)*.9,1.02,Math.sin(a+.42)*.9);
            beam(v,m,from,to,.025,((int)(210*pulse)<<24)|0xB4E8FF);
        }
    }

    private static void whip(YangJian boss,YangJianModel model,float partial,VertexConsumer v,Matrix4f m) {
        float bodyYaw=Mth.rotLerp(partial,boss.yBodyRotO,boss.yBodyRot);
        Vec3 grip=model.heldWeaponPoint(bodyYaw,0,-10.8F/16F,0);
        boolean sweep=boss.action()==YangJian.WHIP_SWEEP || boss.action()==YangJian.WHIP_SPIN;
        float localAge=boss.stepAge(partial),active=localAge-boss.stepWindup();
        YangJianSkill skill=YangJianSkill.forAction(boss.action());
        int duration=sweep && skill!=null?skill.stepActive(0):1;
        boolean extended=sweep && active>=0 && active<duration+6;
        Vec3 previous=grip;
        if(extended) {
            float progress=Mth.clamp(active/duration,0,1);
            double arc=Math.toRadians(boss.telegraphAngle());
            double heading=Math.atan2(boss.dashEnd().z-boss.dashStart().z,boss.dashEnd().x-boss.dashStart().x);
            double direction=YangJianSkill.sweepAngle(heading,arc,progress);
            double reach=boss.telegraphRadius();
            // The returning whip now shares the regular sweep's full-height hit band.
            double tipY=1.2;
            float retract=active>duration?Mth.clamp((active-duration)/6,0,1):0;
            Vec3 end=new Vec3(Math.cos(direction)*reach,tipY,Math.sin(direction)*reach).lerp(grip.add(0,-1.4,0),retract);
            for(int i=1;i<=24;i++) {
                double t=i/24D;
                // The long striking portion stays on the server's radial attack segment.
                // A slight bend near the wrist gives the conducting links flexibility.
                double bend=Math.sin(t*Math.PI)*.13*(1-progress);
                Vec3 point=grip.lerp(end,t).add(-Math.sin(direction)*bend,0,Math.cos(direction)*bend);
                chainLink(v,m,previous,point,i,active<duration);
                previous=point;
            }
        } else {
            float charge=sweep?Mth.clamp(localAge/Math.max(1,boss.stepWindup()),0,1):0;
            double heading=Math.toRadians(-bodyYaw);
            for(int i=1;i<=16;i++) {
                double t=i/16D,angle=t*Math.PI*2.2;
                double radius=.27*(1-t*.45);
                Vec3 point=grip.add(Math.cos(angle+heading)*radius*t,-t*1.45,Math.sin(angle+heading)*radius*t);
                chainLink(v,m,previous,point,i,charge>.4);
                previous=point;
            }
        }
    }

    private static void chainLink(VertexConsumer v,Matrix4f m,Vec3 from,Vec3 to,int index,boolean charged) {
        beam(v,m,from,to,.052,index%2==0?0xFFB8A37A:0xFF586570);
        if(charged)beam(v,m,from,to,.023,0xEEE1FAFF);
    }
    private static void ring(VertexConsumer v,Matrix4f m,Vec3 center,double radius,double width,int color) {
        for(int i=0;i<64;i++) {
            double a=i*Math.PI/32,b=(i+1)*Math.PI/32;
            point(v,m,center.add(Math.cos(a)*(radius-width),.045,Math.sin(a)*(radius-width)),color);
            point(v,m,center.add(Math.cos(a)*(radius+width),.045,Math.sin(a)*(radius+width)),color);
            point(v,m,center.add(Math.cos(b)*(radius+width),.045,Math.sin(b)*(radius+width)),color);
            point(v,m,center.add(Math.cos(b)*(radius-width),.045,Math.sin(b)*(radius-width)),color);
        }
    }
    private static void beam(VertexConsumer v,Matrix4f m,Vec3 from,Vec3 to,double width,int color) {
        Vec3 direction=to.subtract(from).normalize();
        Vec3 side=direction.cross(Math.abs(direction.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize().scale(width);
        Vec3 up=direction.cross(side).normalize().scale(width);
        for(Vec3 axis:new Vec3[]{side,up}) {
            point(v,m,from.subtract(axis),color);point(v,m,from.add(axis),color);
            point(v,m,to.add(axis),color);point(v,m,to.subtract(axis),color);
        }
    }
    private static void point(VertexConsumer v,Matrix4f m,Vec3 p,int color) {
        v.addVertex(m,(float)p.x,(float)p.y,(float)p.z).setColor(color);
    }
    private YangJianCombatEffects() {}
}
