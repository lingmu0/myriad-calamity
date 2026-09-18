package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.entity.CogworkDancer;

public final class CogworkDancerRenderer extends MobRenderer<CogworkDancer,CogworkDancerModel> {
    private static final ResourceLocation GOLD=MyriadCalamity.id("textures/entity/cogwork_dancer.png");
    private static final ResourceLocation SILVER=MyriadCalamity.id("textures/entity/cogwork_dancer_silver.png");
    /** Length of the windup slide; the 1.21.1 build read as a short glide of roughly this length. */
    private static final float STAGE_GLIDE_TICKS=4F;
    /** A re-locked lane counts as a new staging point once it moves by more than this. */
    private static final double STAGE_EPSILON=0.0625D;
    /** Where each body was last drawn, so a fresh windup can slide away from it. */
    private final Map<CogworkDancer,Vec3> shownPositions=new WeakHashMap<>();
    private final Map<CogworkDancer,Glide> glides=new WeakHashMap<>();
    public CogworkDancerRenderer(EntityRendererProvider.Context context) {
        super(context,new CogworkDancerModel(context.getResourceManager()),0.65F);
    }
    @Override public ResourceLocation getTextureLocation(CogworkDancer entity) { return entity.follower()?SILVER:GOLD; }

    /**
     * The windup paints the whole lane on the ground from the locked plan, so the body has to be on
     * the staged point while that warning is up. The server holds that point every tick; this makes
     * the model ease onto it from wherever it was drawn on the previous frame over a few ticks,
     * which is the short slide the 1.21.1 build showed instead of a hard pop. Once the slide ends
     * the body simply stays pinned for the rest of the windup.
     */
    @Override public void render(CogworkDancer dancer,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        Vec3 offset=stageOffset(dancer,partial);
        pose.pushPose();
        pose.translate(offset.x,offset.y,offset.z);
        super.render(dancer,yaw,partial,pose,buffers,light);
        pose.popPose();
    }

    private Vec3 stageOffset(CogworkDancer dancer,float partial) {
        int action=dancer.action();
        Vec3 tracked=dancer.getPosition(partial);
        float age=dancer.scheduledActionAge(partial);
        boolean staged=stagedWindow(action,age,dancer.attackWindup());
        if(!staged) {
            glides.remove(dancer);
            shownPositions.put(dancer,tracked);
            return Vec3.ZERO;
        }
        Vec3 stage=dancer.stagePoint();
        Vec3 previous=shownPositions.getOrDefault(dancer,tracked);
        Glide glide=glides.get(dancer);
        if(glide==null || glide.action()!=action || age<glide.lastAge() || glide.stage().distanceToSqr(stage)>STAGE_EPSILON) {
            glide=new Glide(action,Math.max(0F,age),previous,stage);
            glides.put(dancer,glide);
        }
        glide.lastAge(age);
        double t=Mth.clamp((age-glide.startAge())/STAGE_GLIDE_TICKS,0F,1F);
        double eased=1-Math.pow(1-t,3);
        Vec3 shown=glide.from().lerp(glide.stage(),eased);
        shownPositions.put(dancer,shown);
        Vec3 offset=shown.subtract(tracked);
        return offset.lengthSqr()>1.0E-6?offset:Vec3.ZERO;
    }

    /**
     * True while the body is meant to stand on the staged point: the windup of a dash, slam or
     * barrage. The barrage's own passes keep the body moving, so they are deliberately excluded -
     * the four charges have to read as one continuous flurry.
     */
    private static boolean stagedWindow(int action,float age,int windup) {
        if(action!=CogworkDancer.DASH && action!=CogworkDancer.BARRAGE && action!=CogworkDancer.SLAM) return false;
        return age>=0 && age<windup;
    }

    /** One windup slide: from the last drawn position onto the staged point. */
    private static final class Glide {
        private final int action;
        private final float startAge;
        private final Vec3 from;
        private final Vec3 stage;
        private float lastAge;
        Glide(int action,float startAge,Vec3 from,Vec3 stage) {
            this.action=action;this.startAge=startAge;this.from=from;this.stage=stage;this.lastAge=startAge;
        }
        int action() { return action; }
        float startAge() { return startAge; }
        Vec3 from() { return from; }
        Vec3 stage() { return stage; }
        float lastAge() { return lastAge; }
        void lastAge(float value) { lastAge=value; }
    }
}
