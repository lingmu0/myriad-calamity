package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.entity.CogworkDancer;
import net.xuwu.myriadcalamity.entity.CombatMath;
import org.joml.Matrix4f;

/** Frozen, server-planned ground geometry, using the same filled warning style as Time's Keeper. */
@EventBusSubscriber(modid=MyriadCalamity.ID, value=Dist.CLIENT)
public final class CogworkTelegraph {
    private static final double GROUND_OFFSET=0.035;
    /**
     * Warning alphas. The hard edge stroke is gone, so these fills carry all of the readability:
     * a readable ground wash, a slightly brighter arena ring and a low wall that never hides an
     * attack. Raise these together if the warnings ever read too faint again.
     */
    private static final float FILL_ALPHA=.12F, FILL_CHARGE_ALPHA=.09F,
        BOUNDARY_ALPHA=.42F, BOUNDARY_PULSE=.08F, WALL_ALPHA=.09F, WALL_PULSE=.03F;

    @SubscribeEvent
    public static void renderLevel(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        Minecraft minecraft=Minecraft.getInstance();
        if(minecraft.level==null)return;
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera=event.getCamera().getPosition();
        PoseStack pose=event.getPoseStack();
        MultiBufferSource.BufferSource buffers=minecraft.renderBuffers().bufferSource();
        pose.pushPose();
        for(Entity entity:minecraft.level.entitiesForRendering()) {
            // Plan rendering is independent of the moving dancer's visibility/frustum.
            if(entity instanceof CogworkDancer dancer && dancer.isAlive() && dancer.distanceToSqr(camera)<96*96) {
                // One coordinator owns the ring in the pair; a phase-four survivor owns it alone.
                if(!dancer.follower() || dancer.phase()==4)renderBoundary(dancer,partial,pose,buffers,camera);
                render(dancer,partial,pose,buffers,camera);
            }
        }
        buffers.endBatch(RenderType.debugQuads());
        pose.popPose();
    }

    private static void renderBoundary(CogworkDancer dancer,float partial,PoseStack pose,MultiBufferSource buffers,Vec3 camera) {
        Vec3 center=dancer.arenaPosition();
        pose.pushPose();
        pose.translate(center.x-camera.x,center.y-camera.y,center.z-camera.z);
        VertexConsumer vertices=buffers.getBuffer(RenderType.debugQuads());
        Matrix4f matrix=pose.last().pose();
        int steps=128;
        double radius=CombatMath.FIGHT_BOUNDARY_RADIUS, inner=radius-0.11, outer=radius+0.11;
        int ground=color(BOUNDARY_ALPHA+BOUNDARY_PULSE*(float)Math.sin((dancer.tickCount+partial)*.16F),255,142,92);
        int wall=color(WALL_ALPHA+WALL_PULSE*(float)Math.sin((dancer.tickCount+partial)*.12F),255,108,58);
        for(int i=0;i<steps;i++) {
            double a=i*Math.PI*2/steps,b=(i+1)*Math.PI*2/steps;
            vertex(vertices,matrix,Math.cos(a)*inner,GROUND_OFFSET,Math.sin(a)*inner,ground);
            vertex(vertices,matrix,Math.cos(a)*outer,GROUND_OFFSET,Math.sin(a)*outer,ground);
            vertex(vertices,matrix,Math.cos(b)*outer,GROUND_OFFSET,Math.sin(b)*outer,ground);
            vertex(vertices,matrix,Math.cos(b)*inner,GROUND_OFFSET,Math.sin(b)*inner,ground);
            // A low-alpha wall makes the locked arena readable from every angle without hiding attacks.
            if(i%2==0) {
                vertex(vertices,matrix,Math.cos(a)*inner,0.02,Math.sin(a)*inner,wall);
                vertex(vertices,matrix,Math.cos(a)*inner,3.1,Math.sin(a)*inner,wall);
                vertex(vertices,matrix,Math.cos(b)*inner,3.1,Math.sin(b)*inner,wall);
                vertex(vertices,matrix,Math.cos(b)*inner,0.02,Math.sin(b)*inner,wall);
            }
        }
        pose.popPose();
    }

    private static void render(CogworkDancer dancer,float partial,PoseStack pose,MultiBufferSource buffers,Vec3 camera) {
        int action=dancer.action();
        if(action!=CogworkDancer.DASH && action!=CogworkDancer.SLAM && action!=CogworkDancer.SPIN && action!=CogworkDancer.FAILED && action!=CogworkDancer.BARRAGE)return;
        float age=dancer.scheduledActionAge(partial);
        if(age<0)return;
        int windup=dancer.attackWindup();
        Vec3 anchor=dancer.attackEnd();
        pose.pushPose();
        // Subtract in double precision before the GPU conversion, including far from world origin.
        pose.translate(anchor.x-camera.x,anchor.y-camera.y,anchor.z-camera.z);
        VertexConsumer vertices=buffers.getBuffer(RenderType.debugQuads());
        Matrix4f matrix=pose.last().pose();
        if(action==CogworkDancer.BARRAGE) {
            var lanes=dancer.telegraphLanes();
            for(int pass=0;pass<lanes.size();pass++) {
                float launch=windup+pass*CombatMath.BARRAGE_PASS_TICKS+CombatMath.BARRAGE_WARNING_TICKS;
                float completed=launch+CombatMath.BARRAGE_DASH_TICKS;
                // Every lane appears at the initial windup and vanishes as soon as its dash ends.
                // A completed route is never rendered as a lingering danger trail.
                if(age>=completed)continue;
                float charge=Mth.clamp(1-(launch-age)/(float)windup,0,1);
                var lane=lanes.get(pass);
                lane(vertices,matrix,lane.start().subtract(anchor),lane.end().subtract(anchor),CombatMath.BARRAGE_LANE_RADIUS,charge,age);
            }
        } else if(action==CogworkDancer.DASH && age<windup) {
            lane(vertices,matrix,dancer.attackStart().subtract(anchor),Vec3.ZERO,1.35,Mth.clamp(age/windup,0,1),age);
        } else if(action==CogworkDancer.SLAM && age<windup+8) {
            // Only the landing impact is warned here; the later ground wave is jumpable.
            disk(vertices,matrix,Vec3.ZERO,CombatMath.SLAM_WARNING_RADIUS,Mth.clamp(age/(windup+8),0,1),age);
        } else if(action==CogworkDancer.SPIN && age<windup+52) {
            // The paired duet warns the enlarged damaging ring through the active spin.
            disk(vertices,matrix,Vec3.ZERO,CombatMath.DUET_RADIUS,Mth.clamp(age/(float)windup,0,1),age);
        } else if(action==CogworkDancer.FAILED && dancer.phase()==4 && age<windup+52) {
            // The final survivor only reaches for its missing partner: this is a visual
            // range cue with no hitbox, followed by the failed-duet animation.
            disk(vertices,matrix,Vec3.ZERO,CombatMath.DUET_RADIUS,Mth.clamp(age/(float)windup,0,1),age);
        }
        pose.popPose();
        renderQueued(dancer,partial,pose,buffers,camera);
    }

    private static void renderQueued(CogworkDancer dancer,float partial,PoseStack pose,MultiBufferSource buffers,Vec3 camera) {
        int action=dancer.queuedAction();
        if(action!=CogworkDancer.DASH && action!=CogworkDancer.SLAM && action!=CogworkDancer.SPIN)return;
        float age=dancer.queuedActionAge(partial);
        int windup=dancer.queuedAttackWindup();
        if(age<0 || age>=windup)return;
        // Phase-three plans are re-locked on the server at the exact warning tick.  Hide the
        // stale pre-lock geometry for that tick so the client never flashes a false circle.
        if(dancer.phase()==3 && (action==CogworkDancer.DASH || action==CogworkDancer.SLAM) && !dancer.queuedWarningLocked())return;
        Vec3 start=dancer.queuedStart(),end=dancer.queuedEnd();
        pose.pushPose();
        pose.translate(end.x-camera.x,end.y-camera.y,end.z-camera.z);
        VertexConsumer vertices=buffers.getBuffer(RenderType.debugQuads());
        Matrix4f matrix=pose.last().pose();
        if(action==CogworkDancer.DASH) {
            lane(vertices,matrix,start.subtract(end),Vec3.ZERO,1.35,Mth.clamp(age/windup,0,1),age);
        } else if(action==CogworkDancer.SLAM) {
            disk(vertices,matrix,Vec3.ZERO,CombatMath.SLAM_WARNING_RADIUS,Mth.clamp(age/(windup+8),0,1),age);
        } else {
            // A queued spin is warned for either dancer, including the staggered phase-three mate.
            disk(vertices,matrix,Vec3.ZERO,CombatMath.DUET_RADIUS,Mth.clamp(age/(float)windup,0,1),age);
        }
        pose.popPose();
    }

    private static void lane(VertexConsumer v,Matrix4f m,Vec3 start,Vec3 end,double radius,float charge,float age) {
        double angle=Math.atan2(end.z-start.z,end.x-start.x);
        int steps=12,points=2*(steps+1);
        double[] x=new double[points],z=new double[points];
        for(int i=0;i<=steps;i++) {
            double a=angle-Math.PI/2+i*Math.PI/steps;
            x[i]=end.x+Math.cos(a)*radius;z[i]=end.z+Math.sin(a)*radius;
            a=angle+Math.PI/2+i*Math.PI/steps;
            x[steps+1+i]=start.x+Math.cos(a)*radius;z[steps+1+i]=start.z+Math.sin(a)*radius;
        }
        polygon(v,m,x,z,Math.min(start.y,end.y)+GROUND_OFFSET,charge,age);
    }

    private static void disk(VertexConsumer v,Matrix4f m,Vec3 center,double radius,float charge,float age) {
        int steps=96;double[] x=new double[steps],z=new double[steps];
        for(int i=0;i<steps;i++) {
            double angle=i*Math.PI*2/steps;x[i]=center.x+Math.cos(angle)*radius;z[i]=center.z+Math.sin(angle)*radius;
        }
        polygon(v,m,x,z,center.y+GROUND_OFFSET,charge,age);
    }

    private static void polygon(VertexConsumer v,Matrix4f m,double[] x,double[] z,double y,float charge,float age) {
        // A soft ground wash only. The hard edge stroke is gone, so the fill has to stay readable
        // on its own without turning the floor into an opaque decal.
        int fill=color(FILL_ALPHA+charge*FILL_CHARGE_ALPHA,255,112,72);
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
