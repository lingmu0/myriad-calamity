package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.entity.CogworkBlade;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A thick, angular crescent with a bright cutting edge, visible from every camera direction. */
public final class CogworkBladeRenderer extends EntityRenderer<CogworkBlade> {
    private static final ResourceLocation TEXTURE=MyriadCalamity.id("textures/entity/cogwork_dancer.png");
    private static final double[][] OUTER={{-.7,.25},{-.42,-.22},{0,-.4},{.42,-.22},{.7,.25}};
    private static final double[][] INNER={{-.7,.25},{-.32,.02},{0,-.10},{.32,.02},{.7,.25}};
    public CogworkBladeRenderer(EntityRendererProvider.Context context) { super(context);shadowRadius=0; }
    @Override public void render(CogworkBlade blade,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        pose.pushPose();
        Vec3 velocity=blade.getDeltaMovement();
        if(velocity.lengthSqr()>1E-7)pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0,0,1),
            new Vector3f((float)velocity.x,(float)velocity.y,(float)velocity.z).normalize()));
        VertexConsumer v=buffers.getBuffer(RenderType.debugQuads());Matrix4f m=pose.last().pose();
        int face=blade.silver()?0xEEA7DCE8:0xEEEFA83E,side=blade.silver()?0xEE68A4BD:0xEEAC6A24;
        for(int i=0;i<4;i++) {
            double[] a=OUTER[i],b=OUTER[i+1],c=INNER[i+1],d=INNER[i];
            // The crescent lies in the travel plane.  Its convex outer arc (+local Z) leads the flight.
            for(double depth:new double[]{-.065,.065}) {
                bladeVertex(v,m,a[0],a[1],depth,face);bladeVertex(v,m,b[0],b[1],depth,face);
                bladeVertex(v,m,c[0],c[1],depth,face);bladeVertex(v,m,d[0],d[1],depth,face);
            }
            bladeVertex(v,m,a[0],a[1],-.065,0xFFFFF6CF);bladeVertex(v,m,b[0],b[1],-.065,0xFFFFF6CF);
            bladeVertex(v,m,b[0],b[1],.065,0xFFFFF6CF);bladeVertex(v,m,a[0],a[1],.065,0xFFFFF6CF);
            bladeVertex(v,m,d[0],d[1],-.065,side);bladeVertex(v,m,c[0],c[1],-.065,side);
            bladeVertex(v,m,c[0],c[1],.065,side);bladeVertex(v,m,d[0],d[1],.065,side);
        }
        // These short tails move with the projectile; there is no stationary damaging residue.
        for(double x:new double[]{-.36,0,.36}) {
            vertex(v,m,x-.035,-.05,-.12,0xC0FFF3CD);vertex(v,m,x+.035,-.05,-.12,0xC0FFF3CD);
            vertex(v,m,x,-.05,-.8,0x08FFF3CD);vertex(v,m,x,-.05,-.8,0x08FFF3CD);
        }
        pose.popPose();super.render(blade,yaw,partial,pose,buffers,light);
    }
    private static void bladeVertex(VertexConsumer v,Matrix4f m,double x,double arc,double depth,int color) {
        // OUTER/INNER use their second coordinate as arc depth; invert it so the bulge points +Z.
        vertex(v,m,x,depth,-arc,color);
    }
    private static void vertex(VertexConsumer v,Matrix4f m,double x,double y,double z,int color) {
        v.vertex(m,(float)x,(float)y,(float)z).color(color).endVertex();
    }
    @Override public ResourceLocation getTextureLocation(CogworkBlade blade) { return TEXTURE; }
}
