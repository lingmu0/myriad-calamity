package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.config.MyriadConfig;
import net.xuwu.myriadcalamity.entity.YangJian;
import net.xuwu.myriadcalamity.entity.YangJianHazard;
import net.xuwu.myriadcalamity.entity.YangJianSkill;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Small immediate weapon effects; persistent damaging trails have their own server entities. */
final class YangJianCombatEffects {
    static void render(YangJian boss,YangJianModel model,float partial,PoseStack pose,CombatGeometry buffers) {
        int strokeDuration = strokeDuration(boss);
        if(strokeDuration==0 && boss.phase()<2 && boss.action()!=YangJian.TRANSITION)return;
        VertexConsumer v=buffers.quads();
        Matrix4f m=pose.last().pose();
        float age=boss.actionAge(partial);
        eyeCharge(boss,model,partial,v,m);
        if(boss.phase()==3 || boss.action()==YangJian.THIRD_EYE_OPEN)divine(boss,model,partial,age,v,m);
        if(boss.action()==YangJian.SWEEP_BEAM && boss.hasSweepClone()) {
            sweepCloneVeil(boss,partial,pose,v);
            sweepCloneModel(boss,model,partial,pose,buffers);
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
        float active=boss.stepAge(partial)-boss.stepWindup();
        if(strokeDuration>0 && boss.weapon()!=3 && !boss.weaponThrown() && !boss.isInvisible()
                && active>=0 && active<strokeDuration+4) {
            weaponStroke(boss,model,partial,age,active,strokeDuration,v,m);
        }
        if((boss.action()==YangJian.AXE_SLAM || boss.action()==YangJian.AXE_COMBO && boss.comboStep()==3)
                && active>=0 && active<7) {
            Vec3 position=new Vec3(Mth.lerp(partial,boss.xOld,boss.getX()),Mth.lerp(partial,boss.yOld,boss.getY()),Mth.lerp(partial,boss.zOld,boss.getZ()));
            Vec3 center=boss.attackPoint().subtract(position);
            double radius=boss.telegraphRadius();
            YangJianLightningEffects.strike(v,m,center,6.5,Math.min(2.3,radius*.39),.21,active,0,
                boss.getId(),YangJianVisuals.BLUE,1);
            if(MyriadConfig.showYangJianAttackIndicators()) {
                ring(v,m,center,radius,.068,((int)((1-active/7)*146)<<24)|0xA8E8FF);
            }
        }
    }

    private static int strokeDuration(YangJian boss) {
        YangJianSkill skill = YangJianSkill.forAction(boss.action());
        if (skill == null) return 0;
        return switch (skill) {
            case COMBO, FOUR_COMBO, SIX_COMBO, COORDINATED, COUNTER, THRUST, LIGHTNING_THRUST,
                AXE_SLAM, AXE_COMBO, DRAW_SLASH, DELAYED_COMBO, DIVINE_SWEEP -> skill.stepActive(boss.comboStep());
            case AERIAL_COMBO, DIVINE_JUDGEMENT -> boss.comboStep()==2 && boss.telegraphShape()==2 ? 6 : 0;
            case INVISIBLE_DASH -> boss.comboStep()==1 ? skill.stepActive(1) : 0;
            default -> 0;
        };
    }

    private static void weaponStroke(YangJian boss,YangJianModel model,float partial,float age,float active,
                                     int duration,VertexConsumer v,Matrix4f m) {
        boolean divine=boss.action()==YangJian.DIVINE_SWEEP;
        float yaw=Mth.rotLerp(partial,boss.yBodyRotO,boss.yBodyRot);
        YangJianVisuals.Palette palette = divine ? YangJianVisuals.RED
            : boss.weapon()==1 || boss.action()==YangJian.LIGHTNING_THRUST ? YangJianVisuals.BLUE
            : boss.weapon()==2 ? YangJianVisuals.SILVER : YangJianVisuals.GOLD;
        if (divine && active<duration) {
            Vec3 tip = model.bladeEdge(yaw,0,YangJianSkill.divineSweepWeaponLength(age)).tip();
            double tipRadius = tip.horizontalDistance();
            if (tipRadius>.1 && tipRadius<boss.telegraphRadius()) {
                double reach = boss.telegraphRadius()/tipRadius;
                Vec3 end = new Vec3(tip.x*reach,YangJianSkill.DIVINE_SWEEP_HEIGHT,tip.z*reach);
                YangJianVisuals.bolt(v,m,tip,end,.103,.34,age,boss.getId(),palette,.95,true);
                YangJianVisuals.bolt(v,m,tip,end,.047,.56,age+4,boss.getId()+51,palette,.65,false);
            }
        }

        // Sample only this stroke, including its last frames while the glow fades in recovery.
        float newest = Math.max(0,active-duration+.01F), oldest = Math.min(active+.7F,4.7F);
        if (newest>=oldest) return;
        YangJianModel.BladeEdge previous = null;
        double previousOpacity = 0;
        for (int i=0;i<=12;i++) {
            float back = Mth.lerp(i/12F,oldest,newest);
            float sampleYaw = Mth.rotLerp(Math.max(0,partial-back),boss.yBodyRotO,boss.yBodyRot);
            YangJianModel.BladeEdge edge = model.bladeEdge(sampleYaw,back,
                divine ? YangJianSkill.divineSweepWeaponLength(age-back) : 1);
            double opacity = (1-back/4.7) * (i/12.0);
            if (previous!=null) {
                YangJianVisuals.bladeArc(v,m,previous.heel(),previous.tip(),edge.heel(),edge.tip(),
                    palette,previousOpacity,opacity);
            }
            previous = edge;
            previousOpacity = opacity;
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
    }

    private static void eyeCharge(YangJian boss,YangJianModel model,float partial,VertexConsumer v,Matrix4f m) {
        YangJianHazard hazard=boss.currentBeam();
        if(hazard==null || !hazard.warning(partial))return;
        float charge=Mth.clamp(hazard.age(partial)/Math.max(1,hazard.windup()),0,1);
        float bodyYaw=Mth.rotLerp(partial,boss.yBodyRotO,boss.yBodyRot);
        Vec3 eye=model.eyePoint(bodyYaw);
        Vec3 direction=hazard.visualDirection(partial).normalize();
        if (direction.lengthSqr()<1E-8) return;
        Vec3 side=YangJianVisuals.side(direction);
        Vec3 up=direction.cross(side).normalize();
        double clock=boss.tickCount+partial;
        double pulse=.85+.15*Math.sin(clock*1.2);
        double radius=.065+charge*.15;
        Vec3 center=eye.add(direction.scale(.025));
        YangJianVisuals.flare(v,m,center,side,up,radius,(.45+charge*.55)*pulse,YangJianVisuals.GOLD);
        for (int i=0;i<4;i++) {
            double angle=clock*.16+i*Math.PI/2;
            Vec3 axis=side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            YangJianVisuals.bolt(v,m,center.add(axis.scale(radius*(1.4-.56*charge))),
                center.add(axis.scale(radius*.43)),.012,.025,clock,boss.getId()+i*33,
                YangJianVisuals.GOLD,charge*.8,false);
        }
    }

    private static void sweepCloneModel(YangJian boss,YangJianModel model,float partial,PoseStack pose,CombatGeometry buffers) {
        Vec3 now=new Vec3(Mth.lerp(partial,boss.xOld,boss.getX()),Mth.lerp(partial,boss.yOld,boss.getY()),Mth.lerp(partial,boss.zOld,boss.getZ()));
        Vec3 clone=boss.sweepClone();
        pose.pushPose();pose.translate(clone.x-now.x,clone.y-now.y,clone.z-now.z);
        pose.mulPose(new Quaternionf().rotationY((180-boss.sweepCloneYaw())*Mth.DEG_TO_RAD));
        // LivingEntityRenderer applies this mirror and baseline translation
        // before rendering the real model. Repeating it keeps the apparition
        // upright instead of turning the whole rig upside down.
        pose.scale(-1F,-1F,1F);pose.scale(YangJianRenderer.MODEL_SCALE,YangJianRenderer.MODEL_SCALE,YangJianRenderer.MODEL_SCALE);
        pose.translate(0,-1.501,0);
        RiggedBossRenderer.Mesh cloneMesh=model.beamClone(boss.sweepCloneYaw(),boss.cloneBeam(),partial);
        buffers.mesh(pose,RenderTypes.entityTranslucent(YangJianRenderer.TEXTURE),(stack,vertices)->
            cloneMesh.draw(stack,vertices,15728880,OverlayTexture.NO_OVERLAY,0x35FFFFFF));
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
        if(charged) {
            beam(v,m,from,to,.09,0x344E91FF);
            beam(v,m,from,to,.023,0xEEE1FAFF);
        }
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
