package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;

abstract class CombatEntityRenderer<E extends Entity> extends EntityRenderer<E,CombatEntityRenderer.State> {
    static final class State extends EntityRenderState {
        private CombatGeometry geometry;
    }

    protected CombatEntityRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(E entity,State state,float partial) {
        super.extractRenderState(entity,state,partial);
        state.geometry=new CombatGeometry();
        collect(entity,partial,new PoseStack(),state.geometry,state.lightCoords);
    }

    protected abstract void collect(E entity,float partial,PoseStack pose,CombatGeometry buffers,int light);

    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        state.geometry.submit(pose,collector);
        super.submit(state,pose,collector,camera);
    }
}
