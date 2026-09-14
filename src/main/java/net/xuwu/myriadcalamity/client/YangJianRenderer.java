package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.entity.YangJian;

public final class YangJianRenderer extends MobRenderer<YangJian,YangJianModel> {
    public static final float MODEL_SCALE=.94F;
    public static final ResourceLocation TEXTURE=MyriadCalamity.id("textures/entity/yang_jian.png");
    public YangJianRenderer(EntityRendererProvider.Context context) {
        super(context,new YangJianModel(context.getResourceManager()),.74F);
    }
    @Override protected void scale(YangJian boss,PoseStack pose,float partial) {
        pose.scale(MODEL_SCALE,MODEL_SCALE,MODEL_SCALE);
    }
    @Override public ResourceLocation getTextureLocation(YangJian boss) { return TEXTURE; }
    @Override protected float getFlipDegrees(YangJian boss) { return 0; }
    @Override public boolean shouldRender(YangJian boss,Frustum frustum,double x,double y,double z) {
        return boss.action()==YangJian.TRANSITION
            ?boss.shouldRender(x,y,z) && frustum.isVisible(boss.getBoundingBox().inflate(13))
            :boss.action()==YangJian.SWEEP_BEAM
                ?boss.shouldRender(x,y,z) && frustum.isVisible(boss.getBoundingBox().inflate(14))
            :super.shouldRender(boss,frustum,x,y,z);
    }
    @Override protected RenderType getRenderType(YangJian boss,boolean visible,boolean translucent,boolean glowing) {
        if(boss.action()==YangJian.INVISIBLE_DASH && boss.isInvisible())return RenderType.entityTranslucent(TEXTURE);
        return super.getRenderType(boss,visible,translucent,glowing);
    }
    @Override public void render(YangJian boss,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        super.render(boss,yaw,partial,pose,buffers,light);
        if(boss.isAlive() && (boss.phase()>=2 || boss.action()==YangJian.TRANSITION))
            YangJianCombatEffects.render(boss,model,partial,pose,buffers);
    }
}
