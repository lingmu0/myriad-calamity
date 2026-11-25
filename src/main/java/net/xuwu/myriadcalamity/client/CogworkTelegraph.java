package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.entity.CogworkDancer;
import net.xuwu.myriadcalamity.entity.CombatMath;
import org.joml.Matrix4f;

/** Frozen, server-planned ground telegraphs paired with world-space dancer combat VFX. */
@EventBusSubscriber(modid=MyriadCalamity.ID, value=Dist.CLIENT)
public final class CogworkTelegraph {
    private static final ContextKey<CombatGeometry> GEOMETRY=new ContextKey<>(MyriadCalamity.id("cogwork_warnings"));
    private static final double GROUND_OFFSET=0.035;
    private static final float FILL_ALPHA=.12F, FILL_CHARGE_ALPHA=.09F,
        BOUNDARY_ALPHA=.44F, BOUNDARY_PULSE=.08F, WALL_ALPHA=.13F, WALL_PULSE=.03F;

    @SubscribeEvent
    public static void extract(ExtractLevelRenderStateEvent event) {
        float partial=event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera=event.getCamera().position();
        PoseStack pose=new PoseStack();
        CombatGeometry buffers=new CombatGeometry();
        for(Entity entity:event.getLevel().entitiesForRendering()) {
            if(entity instanceof CogworkDancer dancer && dancer.isAlive() && dancer.distanceToSqr(camera)<96*96) {
                if(!dancer.follower() || dancer.phase()==4)renderBoundary(dancer,partial,pose,buffers,camera);
                render(dancer,partial,pose,buffers,camera);
                CogworkCombatEffects.render(dancer,partial,pose,buffers,camera);
            }
        }
        event.getRenderState().setRenderData(GEOMETRY,buffers);
    }

    @SubscribeEvent
    public static void submit(SubmitCustomGeometryEvent event) {
        var geometry=event.getLevelRenderState().getRenderData(GEOMETRY);
        if(geometry!=null)geometry.submit(event.getPoseStack(),event.getSubmitNodeCollector());
    }

    private static void renderBoundary(CogworkDancer dancer,float partial,PoseStack pose,CombatGeometry buffers,Vec3 camera) {
        Vec3 center=dancer.arenaPosition();
        pose.pushPose();
        pose.translate(center.x-camera.x,center.y-camera.y,center.z-camera.z);
        VertexConsumer vertices=buffers.quads();
        Matrix4f matrix=pose.last().pose();
        int steps=128;
        float time=dancer.tickCount+partial;
        double radius=CombatMath.FIGHT_BOUNDARY_RADIUS, inner=radius-0.12, outer=radius+0.12;
        int ground=color(BOUNDARY_ALPHA+BOUNDARY_PULSE*(float)Math.sin(time*.16F),255,152,96);
        int coreBand=color((BOUNDARY_ALPHA+0.12F)+BOUNDARY_PULSE*(float)Math.sin(time*.16F),255,214,158);
        int wallBot=color(WALL_ALPHA+WALL_PULSE*(float)Math.sin(time*.12F),255,126,72);
        int wallMid=color((WALL_ALPHA*0.42F),255,108,58);
        int wallTop=color(0F,255,108,58);
        for(int i=0;i<steps;i++) {
            double a=i*Math.PI*2/steps,b=(i+1)*Math.PI*2/steps;
            double ca=Math.cos(a),sa=Math.sin(a),cb=Math.cos(b),sb=Math.sin(b);
            vertex(vertices,matrix,ca*inner,GROUND_OFFSET,sa*inner,ground);
            vertex(vertices,matrix,ca*outer,GROUND_OFFSET,sa*outer,ground);
            vertex(vertices,matrix,cb*outer,GROUND_OFFSET,sb*outer,ground);
            vertex(vertices,matrix,cb*inner,GROUND_OFFSET,sb*inner,ground);
            double cIn=radius-0.032,cOut=radius+0.032;
            vertex(vertices,matrix,ca*cIn,GROUND_OFFSET+0.008,sa*cIn,coreBand);
            vertex(vertices,matrix,ca*cOut,GROUND_OFFSET+0.008,sa*cOut,coreBand);
            vertex(vertices,matrix,cb*cOut,GROUND_OFFSET+0.008,sb*cOut,coreBand);
            vertex(vertices,matrix,cb*cIn,GROUND_OFFSET+0.008,sb*cIn,coreBand);
            vertex(vertices,matrix,ca*inner,0.03,sa*inner,wallBot);
            vertex(vertices,matrix,ca*inner,1.15,sa*inner,wallMid);
            vertex(vertices,matrix,cb*inner,1.15,sb*inner,wallMid);
            vertex(vertices,matrix,cb*inner,0.03,sb*inner,wallBot);

            vertex(vertices,matrix,ca*inner,1.15,sa*inner,wallMid);
            vertex(vertices,matrix,ca*inner,3.05,sa*inner,wallTop);
            vertex(vertices,matrix,cb*inner,3.05,sb*inner,wallTop);
            vertex(vertices,matrix,cb*inner,1.15,sb*inner,wallMid);
        }
        int notches=24;
        double spin=time*0.006;
        int notchCol=color(0.32F+0.08F*(float)Math.sin(time*0.2F),255,212,148);
        for(int i=0;i<notches;i++) {
            double a=spin+i*Math.PI*2/notches,da=0.018;
            double r0=outer+0.02,r1=outer+0.34;
            vertex(vertices,matrix,Math.cos(a-da)*r0,GROUND_OFFSET+0.005,Math.sin(a-da)*r0,notchCol);
            vertex(vertices,matrix,Math.cos(a-da*0.6)*r1,GROUND_OFFSET+0.005,Math.sin(a-da*0.6)*r1,notchCol);
            vertex(vertices,matrix,Math.cos(a+da*0.6)*r1,GROUND_OFFSET+0.005,Math.sin(a+da*0.6)*r1,notchCol);
            vertex(vertices,matrix,Math.cos(a+da)*r0,GROUND_OFFSET+0.005,Math.sin(a+da)*r0,notchCol);
        }
        pose.popPose();
    }

    private static void render(CogworkDancer dancer,float partial,PoseStack pose,CombatGeometry buffers,Vec3 camera) {
        int action=dancer.action();
        if(action!=CogworkDancer.DASH && action!=CogworkDancer.SLAM && action!=CogworkDancer.SPIN && action!=CogworkDancer.FAILED && action!=CogworkDancer.BARRAGE)return;
        float age=dancer.scheduledActionAge(partial);
        if(age<0)return;
        int windup=dancer.attackWindup();
        Vec3 anchor=dancer.attackEnd();
        boolean silver=dancer.follower();
        pose.pushPose();
        pose.translate(anchor.x-camera.x,anchor.y-camera.y,anchor.z-camera.z);
        VertexConsumer vertices=buffers.quads();
        Matrix4f matrix=pose.last().pose();
        if(action==CogworkDancer.BARRAGE) {
            var lanes=dancer.telegraphLanes();
            for(int pass=0;pass<lanes.size();pass++) {
                float launch=windup+pass*CombatMath.BARRAGE_PASS_TICKS+CombatMath.BARRAGE_WARNING_TICKS;
                float completed=launch+CombatMath.BARRAGE_DASH_TICKS;
                if(age>=completed)continue;
                float charge=Mth.clamp(1-(launch-age)/(float)windup,0,1);
                var lane=lanes.get(pass);
                lane(vertices,matrix,lane.start().subtract(anchor),lane.end().subtract(anchor),CombatMath.BARRAGE_LANE_RADIUS,charge,age,silver);
            }
        } else if(action==CogworkDancer.DASH && age<windup) {
            lane(vertices,matrix,dancer.attackStart().subtract(anchor),Vec3.ZERO,1.35,Mth.clamp(age/windup,0,1),age,silver);
        } else if(action==CogworkDancer.SLAM && age<windup+8) {
            disk(vertices,matrix,Vec3.ZERO,CombatMath.SLAM_WARNING_RADIUS,Mth.clamp(age/(windup+8),0,1),age,silver);
        } else if(action==CogworkDancer.SPIN && age<windup+52) {
            disk(vertices,matrix,Vec3.ZERO,CombatMath.DUET_RADIUS,Mth.clamp(age/(float)windup,0,1),age,silver);
        } else if(action==CogworkDancer.FAILED && dancer.phase()==4 && age<windup+52) {
            disk(vertices,matrix,Vec3.ZERO,CombatMath.DUET_RADIUS,Mth.clamp(age/(float)windup,0,1),age,silver);
        }
        pose.popPose();
        renderQueued(dancer,partial,pose,buffers,camera);
    }

    private static void renderQueued(CogworkDancer dancer,float partial,PoseStack pose,CombatGeometry buffers,Vec3 camera) {
        int action=dancer.queuedAction();
        if(action!=CogworkDancer.DASH && action!=CogworkDancer.SLAM && action!=CogworkDancer.SPIN)return;
        float age=dancer.queuedActionAge(partial);
        int windup=dancer.queuedAttackWindup();
        if(age<0 || age>=windup)return;
        if(dancer.phase()==3 && (action==CogworkDancer.DASH || action==CogworkDancer.SLAM) && !dancer.queuedWarningLocked())return;
        Vec3 start=dancer.queuedStart(),end=dancer.queuedEnd();
        boolean silver=dancer.follower();
        pose.pushPose();
        pose.translate(end.x-camera.x,end.y-camera.y,end.z-camera.z);
        VertexConsumer vertices=buffers.quads();
        Matrix4f matrix=pose.last().pose();
        if(action==CogworkDancer.DASH) {
            lane(vertices,matrix,start.subtract(end),Vec3.ZERO,1.35,Mth.clamp(age/windup,0,1),age,silver);
        } else if(action==CogworkDancer.SLAM) {
            disk(vertices,matrix,Vec3.ZERO,CombatMath.SLAM_WARNING_RADIUS,Mth.clamp(age/(windup+8),0,1),age,silver);
        } else {
            disk(vertices,matrix,Vec3.ZERO,CombatMath.DUET_RADIUS,Mth.clamp(age/(float)windup,0,1),age,silver);
        }
        pose.popPose();
    }

    private static void lane(VertexConsumer v,Matrix4f m,Vec3 start,Vec3 end,double radius,float charge,float age,boolean silver) {
        double angle=Math.atan2(end.z-start.z,end.x-start.x);
        int steps=12,points=2*(steps+1);
        double[] x=new double[points],z=new double[points];
        for(int i=0;i<=steps;i++) {
            double a=angle-Math.PI/2+i*Math.PI/steps;
            x[i]=end.x+Math.cos(a)*radius;z[i]=end.z+Math.sin(a)*radius;
            a=angle+Math.PI/2+i*Math.PI/steps;
            x[steps+1+i]=start.x+Math.cos(a)*radius;z[steps+1+i]=start.z+Math.sin(a)*radius;
        }
        double y=Math.min(start.y,end.y)+GROUND_OFFSET;
        polygon(v,m,x,z,y,charge,silver);
        Vec3 route=end.subtract(start);
        double length=route.horizontalDistance();
        if(length>1.5) {
            Vec3 dir=new Vec3(route.x/length,0,route.z/length);
            Vec3 side=new Vec3(-dir.z,0,dir.x);
            double flow=(charge*0.85+0.08);
            int chevron=silver?color(0.08F+charge*0.11F,190,235,255):color(0.08F+charge*0.11F,255,204,132);
            double cy=y+0.008;
            for(int k=0;k<3;k++) {
                double t=(flow+k*0.33)%1.0;
                double dist=t*length;
                Vec3 tip=new Vec3(start.x+dir.x*dist,cy,start.z+dir.z*dist);
                Vec3 back=tip.subtract(dir.scale(Math.min(1.1,radius*0.8)));
                double span=radius*0.62;
                double thick=0.14;
                for(int sgn:new int[]{-1,1}) {
                    Vec3 wing=back.add(side.scale(sgn*span));
                    Vec3 tipBack=tip.subtract(dir.scale(thick));
                    Vec3 wingBack=wing.subtract(dir.scale(thick));
                    vertex(v,m,tip.x,cy,tip.z,chevron);vertex(v,m,wing.x,cy,wing.z,chevron);
                    vertex(v,m,wingBack.x,cy,wingBack.z,chevron);vertex(v,m,tipBack.x,cy,tipBack.z,chevron);
                }
            }
        }
    }

    private static void disk(VertexConsumer v,Matrix4f m,Vec3 center,double radius,float charge,float age,boolean silver) {
        int steps=96;double[] x=new double[steps],z=new double[steps];
        for(int i=0;i<steps;i++) {
            double angle=i*Math.PI*2/steps;x[i]=center.x+Math.cos(angle)*radius;z[i]=center.z+Math.sin(angle)*radius;
        }
        double y=center.y+GROUND_OFFSET;
        polygon(v,m,x,z,y,charge,silver);
        double innerR=radius*charge;
        if(innerR>0.12) {
            int pulse=silver?color(0.05F+charge*0.07F,185,225,255):color(0.05F+charge*0.07F,255,186,112);
            double iy=y+0.006;
            for(int i=0;i<48;i++) {
                double a=i*Math.PI/24,b=(i+1)*Math.PI/24;
                vertex(v,m,center.x,iy,center.z,pulse);
                vertex(v,m,center.x+Math.cos(a)*innerR,iy,center.z+Math.sin(a)*innerR,pulse);
                vertex(v,m,center.x+Math.cos(b)*innerR,iy,center.z+Math.sin(b)*innerR,pulse);
                vertex(v,m,center.x+Math.cos(b)*innerR,iy,center.z+Math.sin(b)*innerR,pulse);
            }
        }
    }

    private static void polygon(VertexConsumer v,Matrix4f m,double[] x,double[] z,double y,float charge,boolean silver) {
        int fill=silver
            ? color(FILL_ALPHA+charge*FILL_CHARGE_ALPHA,235,122,126)
            : color(FILL_ALPHA+charge*FILL_CHARGE_ALPHA,255,118,68);
        for(int i=1;i<x.length-1;i++) {
            vertex(v,m,x[0],y,z[0],fill);vertex(v,m,x[i],y,z[i],fill);
            vertex(v,m,x[i+1],y,z[i+1],fill);vertex(v,m,x[i+1],y,z[i+1],fill);
        }
    }

    private static int color(float alpha,int red,int green,int blue) {
        return ((int)(Mth.clamp(alpha,0,1)*255)<<24)|(red<<16)|(green<<8)|blue;
    }
    private static void vertex(VertexConsumer v,Matrix4f m,double x,double y,double z,int color) {
        v.addVertex(m,(float)x,(float)y,(float)z).setColor(color);
    }
    private CogworkTelegraph() {}
}
