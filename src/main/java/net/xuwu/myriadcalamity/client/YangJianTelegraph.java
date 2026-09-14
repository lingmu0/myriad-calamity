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
import net.xuwu.myriadcalamity.entity.YangJian;
import org.joml.Matrix4f;

/** Ground warnings read one atomic server plan. No client-generated targets or future plans. */
@EventBusSubscriber(modid=MyriadCalamity.ID,value=Dist.CLIENT)
public final class YangJianTelegraph {
    private static final double HEIGHT=.045,EDGE=.07;

    @SubscribeEvent public static void renderLevel(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        Minecraft minecraft=Minecraft.getInstance();
        if(minecraft.level==null)return;
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera=event.getCamera().getPosition();
        PoseStack pose=event.getPoseStack();
        MultiBufferSource.BufferSource buffers=minecraft.renderBuffers().bufferSource();
        for(Entity entity:minecraft.level.entitiesForRendering()) {
            if(entity instanceof YangJian boss && boss.isAlive() && boss.distanceToSqr(camera)<100*100)
                render(boss,partial,pose,buffers,camera);
        }
        buffers.endBatch(RenderType.debugQuads());
    }

    private static void render(YangJian boss,float partial,PoseStack pose,MultiBufferSource buffers,Vec3 camera) {
        if(boss.action()==YangJian.TRANSITION) {
            YangJianTransitionEffects.ground(boss,partial,pose,buffers,camera);
            return;
        }
        // The PLAN tag carries action, stage, timestamps and every point in one synced update.
        // Testing the timestamp again with partial ticks removes the warning exactly at release.
        if(!boss.warningVisible())return;
        float age=boss.stepAge(partial),windup=boss.stepWindup();
        if(age<0 || age>=windup || windup<=0)return;
        float radius=boss.telegraphRadius();
        if(!Float.isFinite(radius) || radius<=0 || radius>40)return;
        Vec3 origin=boss.attackPoint(),start=boss.dashStart(),end=boss.dashEnd();
        if(!finite(origin) || !finite(start) || !finite(end))return;
        float charge=Mth.clamp(age/windup,0,1);
        pose.pushPose();
        pose.translate(origin.x-camera.x,origin.y-camera.y,origin.z-camera.z);
        VertexConsumer v=buffers.getBuffer(RenderType.debugQuads());
        Matrix4f matrix=pose.last().pose();
        // Shape and angle are part of the same server plan as the hitbox; P1 and
        // P2 share this path, including the re-locked warning after a short vanish.
        switch(boss.telegraphShape()) {
            case 3 -> lane(v,matrix,start.subtract(origin),end.subtract(origin),radius,charge,age);
            case 2 -> sector(v,matrix,Vec3.ZERO,radius,0,Math.PI*2,charge,age);
            case 1 -> {
                double angle=boss.telegraphAngle();
                if(Double.isFinite(angle) && angle>0 && angle<=360) {
                    double heading=Math.atan2(end.z-start.z,end.x-start.x);
                    sector(v,matrix,start.subtract(origin),radius,heading,Math.toRadians(angle),charge,age);
                }
            }
            default -> { }
        }
        pose.popPose();
    }

    private static boolean finite(Vec3 v) {return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);}

    private static void sector(VertexConsumer v,Matrix4f m,Vec3 center,double radius,double heading,double arc,float charge,float age) {
        boolean circle=arc>=Math.PI*2-.001;
        int segments=circle?96:48,offset=circle?0:1;
        double[] x=new double[segments+1+offset],z=new double[x.length];
        if(!circle) {x[0]=center.x;z[0]=center.z;}
        for(int i=0;i<=segments;i++) {
            double a=heading-arc*.5+i*arc/segments;
            x[i+offset]=center.x+Math.cos(a)*radius;z[i+offset]=center.z+Math.sin(a)*radius;
        }
        polygon(v,m,x,z,center.y+HEIGHT,charge,age);
    }

    private static void lane(VertexConsumer v,Matrix4f m,Vec3 start,Vec3 end,double radius,float charge,float age) {
        double angle=Math.atan2(end.z-start.z,end.x-start.x);
        int steps=16;double[] x=new double[2*(steps+1)],z=new double[x.length];
        for(int i=0;i<=steps;i++) {
            double a=angle-Math.PI/2+i*Math.PI/steps;
            x[i]=end.x+Math.cos(a)*radius;z[i]=end.z+Math.sin(a)*radius;
            a=angle+Math.PI/2+i*Math.PI/steps;
            x[steps+1+i]=start.x+Math.cos(a)*radius;z[steps+1+i]=start.z+Math.sin(a)*radius;
        }
        polygon(v,m,x,z,Math.min(start.y,end.y)+HEIGHT,charge,age);
    }

    private static void polygon(VertexConsumer v,Matrix4f m,double[] x,double[] z,double y,float charge,float age) {
        int fill=color(.14F+charge*.16F,225,67,24);
        int edge=color(.85F+.1F*Mth.sin(age*.35F),255,154+(int)(charge*52),74);
        for(int i=1;i<x.length-1;i++) {
            vertex(v,m,x[0],y,z[0],fill);vertex(v,m,x[i],y,z[i],fill);
            vertex(v,m,x[i+1],y,z[i+1],fill);vertex(v,m,x[i+1],y,z[i+1],fill);
        }
        for(int i=0;i<x.length;i++) {
            int next=(i+1)%x.length;
            double dx=x[next]-x[i],dz=z[next]-z[i],length=Math.hypot(dx,dz);
            if(length<1E-6)continue;
            double nx=-dz/length*EDGE,nz=dx/length*EDGE;
            vertex(v,m,x[i]+nx,y+.007,z[i]+nz,edge);vertex(v,m,x[next]+nx,y+.007,z[next]+nz,edge);
            vertex(v,m,x[next]-nx,y+.007,z[next]-nz,edge);vertex(v,m,x[i]-nx,y+.007,z[i]-nz,edge);
        }
    }

    private static int color(float alpha,int r,int g,int b) {
        return ((int)(Mth.clamp(alpha,0,1)*255)<<24)|(r<<16)|(g<<8)|b;
    }
    private static void vertex(VertexConsumer v,Matrix4f m,double x,double y,double z,int color) {
        v.addVertex(m,(float)x,(float)y,(float)z).setColor(color);
    }
    private YangJianTelegraph() {}
}
