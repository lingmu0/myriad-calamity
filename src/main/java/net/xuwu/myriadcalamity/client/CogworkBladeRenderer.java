package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.entity.CogworkBlade;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A multi-layered luminous clockwork crescent wave with soft outer aura and tapering motion ribbons. */
public final class CogworkBladeRenderer extends EntityRenderer<CogworkBlade> {
    private static final ResourceLocation TEXTURE=MyriadCalamity.id("textures/entity/cogwork_dancer.png");
    private static final int SEGMENTS=8;
    private static final double[][] AURA=arc(0.94,0.54,-0.32);
    private static final double[][] OUTER=arc(0.76,0.44,-0.26);
    private static final double[][] INNER=arc(0.76,0.12,-0.26);
    private static final double[][] CORE=arc(0.72,0.42,-0.22);

    private static double[][] arc(double width,double bulge,double tipZ) {
        double[][] pts=new double[SEGMENTS+1][2];
        for(int i=0;i<=SEGMENTS;i++) {
            double t=(i/(double)SEGMENTS)*2-1; // -1..+1
            double curve=Math.cos(t*Math.PI*0.5);
            pts[i][0]=t*width;
            pts[i][1]=tipZ-(tipZ+bulge)*curve;
        }
        return pts;
    }

    public CogworkBladeRenderer(EntityRendererProvider.Context context) { super(context);shadowRadius=0; }

    @Override public void render(CogworkBlade blade,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        pose.pushPose();
        Vec3 velocity=blade.getDeltaMovement();
        if(velocity.lengthSqr()>1E-7)pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0,0,1),
            new Vector3f((float)velocity.x,(float)velocity.y,(float)velocity.z).normalize()));
        // Subtle rhythmic bank-shimmer as the blade slices forward
        float shimmer=Mth.sin((blade.tickCount+partial)*0.45F+blade.getId())*6F;
        pose.mulPose(new Quaternionf().rotationZ(shimmer*Mth.DEG_TO_RAD));

        VertexConsumer v=CombatEffectRenderLayer.buffer(buffers);
        Matrix4f m=pose.last().pose();
        boolean silver=blade.silver();
        int auraCol=silver?0x465EB8FF:0x46F49B2A;
        int auraZero=silver?0x005EB8FF:0x00F49B2A;
        int faceCol=silver?0xE698E2FF:0xE6FFBE4D;
        int sideCol=silver?0xEE4C8CC4:0xEEB8681C;
        int edgeCol=silver?0xFFF5FEFF:0xFFFFFAE0;
        int trailBody=silver?0xA880D4FF:0xA8FFCA62;
        int trailZero=silver?0x0080D4FF:0x00FFCA62;

        // 1. Translucent outer bloom crescent aura
        for(int i=0;i<SEGMENTS;i++) {
            double[] ao=AURA[i],bo=AURA[i+1],ai=OUTER[i],bi=OUTER[i+1];
            bladeQuad(v,m,ao[0],ao[1],0,bo[0],bo[1],0,bi[0],bi[1],0,ai[0],ai[1],0,
                auraZero,auraZero,auraCol,auraCol);
        }

        // 2. Sculpted beveled metallic-light crescent body + incandescent leading edge
        for(int i=0;i<SEGMENTS;i++) {
            double[] a=OUTER[i],b=OUTER[i+1],c=INNER[i+1],d=INNER[i];
            double[] ca=CORE[i],cb=CORE[i+1];
            double thickness=0.068*Math.sin((i+0.5)*Math.PI/SEGMENTS)+0.018;
            for(double depth:new double[]{-thickness,thickness}) {
                bladeQuad(v,m,a[0],a[1],depth,b[0],b[1],depth,c[0],c[1],depth,d[0],d[1],depth,
                    edgeCol,edgeCol,faceCol,faceCol);
            }
            // Bright razor leading cutting bevel
            bladeQuad(v,m,a[0],a[1],-thickness,b[0],b[1],-thickness,b[0],b[1],thickness,a[0],a[1],thickness,
                edgeCol,edgeCol,edgeCol,edgeCol);
            // Deeper inner concave rim
            bladeQuad(v,m,d[0],d[1],-thickness,c[0],c[1],-thickness,c[0],c[1],thickness,d[0],d[1],thickness,
                sideCol,sideCol,sideCol,sideCol);
            // Hot luminous spine highlight
            bladeQuad(v,m,a[0],a[1],thickness+0.008,b[0],b[1],thickness+0.008,cb[0],cb[1],thickness+0.008,ca[0],ca[1],thickness+0.008,
                edgeCol,edgeCol,faceCol,faceCol);
        }

        // 3. Swept multi-ribbon kinetic light wake (moves cleanly with projectile, zero static residue)
        double[] lanes={-0.62,-0.28,0,0.28,0.62};
        double[] lengths={0.95,1.35,1.65,1.35,0.95};
        double[] widths={0.042,0.055,0.075,0.055,0.042};
        for(int k=0;k<lanes.length;k++) {
            double x=lanes[k],len=lengths[k],w=widths[k];
            double zStart=-0.04-Math.abs(x)*0.22;
            int colHead=k==2?edgeCol:trailBody;
            // Horizontal light tail
            vertex(v,m,x-w,0,zStart,colHead);vertex(v,m,x+w,0,zStart,colHead);
            vertex(v,m,x+w*0.1,0,zStart-len,trailZero);vertex(v,m,x-w*0.1,0,zStart-len,trailZero);
            // Crossed vertical fin on central & wingtip streams for 3D readability from side angles
            if(k==0 || k==2 || k==4) {
                vertex(v,m,x,-w*0.75,zStart,colHead);vertex(v,m,x,w*0.75,zStart,colHead);
                vertex(v,m,x,w*0.08,zStart-len*0.85,trailZero);vertex(v,m,x,-w*0.08,zStart-len*0.85,trailZero);
            }
        }
        pose.popPose();
        super.render(blade,yaw,partial,pose,buffers,light);
    }

    private static void bladeQuad(VertexConsumer v,Matrix4f m,
                                  double ax,double aArc,double aD,double bx,double bArc,double bD,
                                  double cx,double cArc,double cD,double dx,double dArc,double dD,
                                  int colA,int colB,int colC,int colD) {
        vertex(v,m,ax,aD,-aArc,colA);vertex(v,m,bx,bD,-bArc,colB);
        vertex(v,m,cx,cD,-cArc,colC);vertex(v,m,dx,dD,-dArc,colD);
    }
    private static void vertex(VertexConsumer v,Matrix4f m,double x,double y,double z,int color) {
        v.vertex(m,(float)x,(float)y,(float)z).color(color).endVertex();
    }
    @Override public ResourceLocation getTextureLocation(CogworkBlade blade) { return TEXTURE; }
}
