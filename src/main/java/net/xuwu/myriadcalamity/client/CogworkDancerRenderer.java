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
import net.xuwu.myriadcalamity.entity.CombatMath;

public final class CogworkDancerRenderer extends MobRenderer<CogworkDancer,CogworkDancerModel> {
    private static final ResourceLocation GOLD=MyriadCalamity.id("textures/entity/cogwork_dancer.png");
    private static final ResourceLocation SILVER=MyriadCalamity.id("textures/entity/cogwork_dancer_silver.png");
    /** Length of the windup slide; the 1.21.1 build read as a short glide of roughly this length. */
    private static final float STAGE_GLIDE_TICKS=4F;
    /** A re-locked lane counts as a new staging point once it moves by more than this. */
    private static final double STAGE_EPSILON=0.0625D;
    /** A barrage lane is crossed one tick before the charge leaves, so the sweep never runs late. */
    private static final float BARRAGE_SWEEP_TICKS=CombatMath.BARRAGE_WARNING_TICKS-1F;
    /** Where each body was last drawn, so a fresh stage can slide away from it. */
    private final Map<CogworkDancer,Vec3> shownPositions=new WeakHashMap<>();
    private final Map<CogworkDancer,Slide> slides=new WeakHashMap<>();
    public CogworkDancerRenderer(EntityRendererProvider.Context context) {
        super(context,new CogworkDancerModel(context.getResourceManager()),0.65F);
    }
    @Override public ResourceLocation getTextureLocation(CogworkDancer entity) { return entity.follower()?SILVER:GOLD; }

    /**
     * The warnings are painted from the locked server plan, so the tracked body is placed by the
     * same plan instead of by position packets.
     *
     * <p>The server stages and holds the body on the plan every tick, but 1.20.1 moves a tracked
     * entity with relative position packets and interpolates each one over three ticks, so the
     * client copy - the one drawn, and the one F3+B outlines - lags several blocks behind and only
     * catches up once the charge has already started. The 1.21.1 build syncs the absolute position
     * every tick and shows none of that. Placing the entity itself (not just the model) removes the
     * gap: the hitbox, the warning and the server all sit on the staged lane, and the four barrage
     * charges still read as one continuous flurry. Everything the plan does not describe keeps
     * following the ordinary tracked position.
     */
    @Override public void render(CogworkDancer dancer,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        placeOnPlan(dancer,partial);
        super.render(dancer,yaw,partial,pose,buffers,light);
    }

    private void placeOnPlan(CogworkDancer dancer,float partial) {
        Vec3 tracked=dancer.getPosition(partial);
        Stage stage=stageWindow(dancer,partial);
        if(stage==null) {
            slides.remove(dancer);
            shownPositions.put(dancer,tracked);
            return;
        }
        Vec3 previous=shownPositions.getOrDefault(dancer,tracked);
        Slide slide=slides.get(dancer);
        if(slide==null || slide.key()!=stage.key() || slide.target().distanceToSqr(stage.target())>STAGE_EPSILON) {
            slide=new Slide(stage.key(),previous,stage.target());
            slides.put(dancer,slide);
        }
        double progress=Mth.clamp(stage.progress(),0D,1D);
        double eased=stage.linear()?progress:1-Math.pow(1-progress,3);
        Vec3 shown=slide.from().lerp(slide.target(),eased);
        shownPositions.put(dancer,shown);
        if(shown.distanceToSqr(dancer.position())>1.0E-4)
            dancer.moveTo(shown.x,shown.y,shown.z,dancer.getYRot(),dancer.getXRot());
    }

    /**
     * The window in which the drawn body is placed on the plan's staged point: the windup of a
     * dash, slam or barrage, and then the four barrage passes, whose warning sweeps the body onto
     * the next lane and whose charge runs down it. Returns {@code null} outside those windows.
     */
    private static Stage stageWindow(CogworkDancer dancer,float partial) {
        int action=dancer.action();
        if(action!=CogworkDancer.DASH && action!=CogworkDancer.BARRAGE && action!=CogworkDancer.SLAM) return null;
        float age=dancer.scheduledActionAge(partial);
        if(age<0) return null;
        int windup=dancer.attackWindup();
        if(age<windup) return new Stage(action,Mth.clamp(age/STAGE_GLIDE_TICKS,0F,1F),dancer.stagePoint(),false);
        if(action!=CogworkDancer.BARRAGE) return null;
        int active=(int)(age-windup);
        if(active>=CombatMath.BARRAGE_PASSES*CombatMath.BARRAGE_PASS_TICKS) return null;
        int pass=active/CombatMath.BARRAGE_PASS_TICKS,local=active%CombatMath.BARRAGE_PASS_TICKS;
        float passAge=age-(windup+active-local);
        if(local<CombatMath.BARRAGE_WARNING_TICKS)
            return new Stage(1000+pass*2,Mth.clamp(passAge/BARRAGE_SWEEP_TICKS,0F,1F),dancer.attackStart(),true);
        if(local<CombatMath.BARRAGE_WARNING_TICKS+CombatMath.BARRAGE_DASH_TICKS)
            return new Stage(1001+pass*2,
                Mth.clamp((passAge-CombatMath.BARRAGE_WARNING_TICKS)/CombatMath.BARRAGE_DASH_TICKS,0F,1F),
                dancer.attackEnd(),true);
        return null;
    }

    /** One staged target plus how far the drawn body should have travelled towards it. */
    private record Stage(int key,double progress,Vec3 target,boolean linear) {}

    /** The slide a body is on: where it started and where it is heading. */
    private static final class Slide {
        private final int key;
        private final Vec3 from;
        private final Vec3 target;
        Slide(int key,Vec3 from,Vec3 target) { this.key=key;this.from=from;this.target=target; }
        int key() { return key; }
        Vec3 from() { return from; }
        Vec3 target() { return target; }
    }
}
