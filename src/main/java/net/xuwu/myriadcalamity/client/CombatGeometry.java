package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/** Geometry extracted from a single frame, with no live entity references at draw time. */
final class CombatGeometry {
    private record Pass(PoseStack.Pose transform,RenderType type,BiConsumer<PoseStack,VertexConsumer> draw) {}
    private final QuadVertices quads=new QuadVertices();
    private final List<Pass> meshes=new ArrayList<>();

    VertexConsumer quads() { return quads; }

    void mesh(PoseStack relative,RenderType type,BiConsumer<PoseStack,VertexConsumer> draw) {
        meshes.add(new Pass(relative.last().copy(),type,draw));
    }

    void submit(PoseStack base,SubmitNodeCollector collector) {
        if(!quads.colors.isEmpty())collector.submitCustomGeometry(base,RenderTypes.debugQuads(),quads::draw);
        for(Pass pass:meshes) {
            base.pushPose();
            base.mulPose(pass.transform().pose());
            collector.submitCustomGeometry(base,pass.type(),(entry,vertices)->draw(entry,vertices,pass.draw()));
            base.popPose();
        }
    }

    static void draw(PoseStack.Pose entry,VertexConsumer vertices,BiConsumer<PoseStack,VertexConsumer> draw) {
        PoseStack pose=new PoseStack();
        pose.last().set(entry);
        draw.accept(pose,vertices);
    }

    private static final class QuadVertices implements VertexConsumer {
        private final FloatArrayList positions=new FloatArrayList();
        private final IntArrayList colors=new IntArrayList();

        @Override public VertexConsumer addVertex(float x,float y,float z) {
            positions.add(x);positions.add(y);positions.add(z);
            return this;
        }
        @Override public VertexConsumer setColor(int color) { colors.add(color);return this; }
        @Override public VertexConsumer setColor(int r,int g,int b,int a) {
            return setColor(a<<24 | r<<16 | g<<8 | b);
        }
        @Override public VertexConsumer setUv(float u,float v) { return this; }
        @Override public VertexConsumer setUv1(int u,int v) { return this; }
        @Override public VertexConsumer setUv2(int u,int v) { return this; }
        @Override public VertexConsumer setNormal(float x,float y,float z) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }

        private void draw(PoseStack.Pose pose,VertexConsumer vertices) {
            for(int i=0;i<colors.size();i++) {
                int at=i*3;
                vertices.addVertex(pose,positions.getFloat(at),positions.getFloat(at+1),positions.getFloat(at+2))
                    .setColor(colors.getInt(i));
            }
        }
    }
}
