package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.entity.CogworkDancer;

public final class CogworkDancerRenderer extends MobRenderer<CogworkDancer,CogworkDancerModel> {
    private static final ResourceLocation GOLD=MyriadCalamity.id("textures/entity/cogwork_dancer.png");
    private static final ResourceLocation SILVER=MyriadCalamity.id("textures/entity/cogwork_dancer_silver.png");
    public CogworkDancerRenderer(EntityRendererProvider.Context context) {
        super(context,new CogworkDancerModel(context.getResourceManager()),0.65F);
    }
    @Override public ResourceLocation getTextureLocation(CogworkDancer entity) { return entity.follower()?SILVER:GOLD; }
    /**
     * The windup paints the whole lane on the ground from the locked plan. The body is pinned to
     * the same staged point while the warning is up, so the model can never trail the telegraph
     * even if a position update is still interpolating or a push moved the tracked entity.
     */
    @Override public void render(CogworkDancer dancer,float yaw,float partial,PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light) {
        Vec3 offset=windupStageOffset(dancer,partial);
        pose.pushPose();
        pose.translate(offset.x,offset.y,offset.z);
        super.render(dancer,yaw,partial,pose,buffers,light);
        pose.popPose();
    }
    static Vec3 windupStageOffset(CogworkDancer dancer,float partial) {
        int action=dancer.action();
        if(action!=CogworkDancer.DASH && action!=CogworkDancer.BARRAGE && action!=CogworkDancer.SLAM) return Vec3.ZERO;
        float age=dancer.scheduledActionAge(partial);
        if(age<0 || age>=dancer.attackWindup()) return Vec3.ZERO;
        // A synced body needs no correction; only a body that is visibly off its warning is pinned.
        Vec3 offset=dancer.stagePoint().subtract(dancer.getPosition(partial));
        return offset.lengthSqr()>0.0625D?offset:Vec3.ZERO;
    }
}
