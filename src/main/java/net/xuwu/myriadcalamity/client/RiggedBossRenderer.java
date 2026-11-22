package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

/** Keeps vanilla's living transforms and overlay while drawing an exported Blockbench mesh. */
abstract class RiggedBossRenderer<E extends Mob>
        extends MobRenderer<E,RiggedBossRenderer.State,RiggedBossRenderer.EmptyModel> {
    @FunctionalInterface
    interface Mesh {
        void draw(PoseStack pose,VertexConsumer vertices,int light,int overlay,int tint);
    }

    static final class State extends LivingEntityRenderState {
        Mesh mesh;
        Identifier texture;
        boolean veiled;
        CombatGeometry effects;
    }

    static final class EmptyModel extends EntityModel<State> {
        private EmptyModel() { super(new ModelPart(List.of(),Map.of())); }
    }

    protected RiggedBossRenderer(EntityRendererProvider.Context context,float shadow) {
        super(context,new EmptyModel(),shadow);
        addLayer(new RenderLayer<State,EmptyModel>(this) {
            @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,State state,float yaw,float pitch) {
                submitMesh(pose,collector,light,state);
            }
        });
    }

    @Override public State createRenderState() { return new State(); }
    @Override public Identifier getTextureLocation(State state) { return state.texture; }
    @Override protected RenderType getRenderType(State state,boolean visible,boolean translucent,boolean glowing) {
        return null; // The custom mesh is submitted by the layer.
    }

    private void submitMesh(PoseStack pose,SubmitNodeCollector collector,int light,State state) {
        boolean visible=isBodyVisible(state);
        boolean translucent=!visible && !state.isInvisibleToPlayer;
        if(!visible && !translucent && !state.veiled && !state.appearsGlowing())return;
        RenderType type=state.veiled?RenderTypes.entityTranslucent(state.texture)
            :translucent?RenderTypes.entityTranslucentCullItemTarget(state.texture)
            :visible?RenderTypes.entityCutout(state.texture):RenderTypes.outline(state.texture);
        int color=state.veiled?0x35FFFFFF:translucent?0x26FFFFFF:0xFFFFFFFF;
        int overlay=getOverlayCoords(state,getWhiteOverlayProgress(state));
        Mesh mesh=state.mesh;
        collector.submitCustomGeometry(pose,type,(entry,vertices)->
            CombatGeometry.draw(entry,vertices,(stack,buffer)->mesh.draw(stack,buffer,light,overlay,color)));
    }

    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        super.submit(state,pose,collector,camera);
        if(state.effects!=null)state.effects.submit(pose,collector);
    }
}
