package net.xuwu.myriadcalamity.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.entity.CogworkDancer;

public final class CogworkDancerRenderer extends RiggedBossRenderer<CogworkDancer> {
    private static final Identifier GOLD=MyriadCalamity.id("textures/entity/cogwork_dancer.png");
    private static final Identifier SILVER=MyriadCalamity.id("textures/entity/cogwork_dancer_silver.png");
    private final CogworkDancerModel rig;
    public CogworkDancerRenderer(EntityRendererProvider.Context context) {
        super(context,0.65F);
        rig=new CogworkDancerModel(context.getResourceManager());
    }
    @Override public void extractRenderState(CogworkDancer entity,State state,float partial) {
        super.extractRenderState(entity,state,partial);
        state.mesh=rig.pose(entity,partial);
        state.texture=entity.follower()?SILVER:GOLD;
    }
}
