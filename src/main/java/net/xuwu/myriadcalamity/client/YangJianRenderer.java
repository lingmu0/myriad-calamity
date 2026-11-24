package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.entity.YangJian;

public final class YangJianRenderer extends RiggedBossRenderer<YangJian> {
    public static final float MODEL_SCALE=.94F;
    public static final Identifier TEXTURE=MyriadCalamity.id("textures/entity/yang_jian.png");
    private final YangJianModel rig;
    public YangJianRenderer(EntityRendererProvider.Context context) {
        super(context,.74F);
        rig=new YangJianModel(context.getResourceManager());
    }
    @Override protected void scale(State state,PoseStack pose) {
        pose.scale(MODEL_SCALE,MODEL_SCALE,MODEL_SCALE);
    }
    @Override protected float getFlipDegrees() { return 0; }
    @Override protected AABB getBoundingBoxForCulling(YangJian boss) { return boss.combatRenderBounds(); }
    @Override public boolean shouldRender(YangJian boss,Frustum frustum,double x,double y,double z) {
        return boss.action()==YangJian.TRANSITION
            ?boss.shouldRender(x,y,z) && frustum.isVisible(boss.getBoundingBox().inflate(13))
            :boss.action()==YangJian.SWEEP_BEAM
                ?boss.shouldRender(x,y,z) && frustum.isVisible(boss.getBoundingBox().inflate(14))
            :super.shouldRender(boss,frustum,x,y,z);
    }
    @Override public void extractRenderState(YangJian boss,State state,float partial) {
        super.extractRenderState(boss,state,partial);
        state.bodyRot=Mth.rotLerp(partial,boss.yBodyRotO,boss.yBodyRot);
        state.yRot=Mth.wrapDegrees(Mth.rotLerp(partial,boss.yHeadRotO,boss.yHeadRot)-state.bodyRot);
        state.texture=TEXTURE;
        state.veiled=boss.action()==YangJian.INVISIBLE_DASH && boss.isInvisible();
        rig.setupAnim(boss,partial,state.bodyRot,state.yRot);
        state.mesh=rig.snapshot();
        state.effects=new CombatGeometry();
        if(boss.isAlive())YangJianCombatEffects.render(boss,rig,partial,new PoseStack(),state.effects);
    }
}
