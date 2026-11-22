package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.entity.CelestialHound;

public final class CelestialHoundRenderer extends MobRenderer<CelestialHound,CelestialHoundRenderer.State,CelestialHoundModel> {
    static final class State extends LivingEntityRenderState {
        int action;
        float actionAge;
    }
    private static final Identifier TEXTURE=MyriadCalamity.id("textures/entity/celestial_hound.png");
    public CelestialHoundRenderer(EntityRendererProvider.Context context) {super(context,new CelestialHoundModel(),.52F);}
    @Override protected void scale(State state,PoseStack pose) {pose.scale(1.18F,1.18F,1.18F);}
    @Override public State createRenderState() { return new State(); }
    @Override public Identifier getTextureLocation(State state) {return TEXTURE;}
    @Override public void extractRenderState(CelestialHound dog,State state,float partial) {
        super.extractRenderState(dog,state,partial);
        state.action=dog.action();
        state.actionAge=dog.actionAge(partial);
    }
}
