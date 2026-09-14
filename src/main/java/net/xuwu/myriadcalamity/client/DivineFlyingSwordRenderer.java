package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.entity.DivineFlyingSword;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A real jian blade aligned with its fixed aim/flight direction; orbiting P2 swords also show a targeting ray. */
public final class DivineFlyingSwordRenderer extends EntityRenderer<DivineFlyingSword> {
    private final YangJianWeapons weapons;
    public DivineFlyingSwordRenderer(EntityRendererProvider.Context context) {
        super(context);weapons=new YangJianWeapons(context.getResourceManager());shadowRadius=.12F;
    }
    @Override public void render(DivineFlyingSword sword,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        if(!sword.hasPlan())return;
        if(sword.aiming(partial)) {
            Vec3 delta=sword.aimPoint().subtract(sword.position());
            VertexConsumer warning=buffers.getBuffer(RenderType.debugQuads());
            LightningTrailRenderer.ribbon(warning,pose,Vec3.ZERO,delta,.033,0xCEF1CB69);
            // A second vertical ribbon keeps the thin ray readable from both overhead and ground views.
            Vec3 normal=new Vec3(0,.033,0);
            LightningTrailRenderer.quad(warning,pose,normal,delta.add(normal),delta.subtract(normal),normal.scale(-1),0xB8FFFFCC);
            Vec3 spot=delta.add(0,-.84,0);
            for(int i=0;i<32;i++) {
                double a=i*Math.PI/16,b=(i+1)*Math.PI/16;
                LightningTrailRenderer.ribbon(warning,pose,spot.add(Math.cos(a)*.6,0,Math.sin(a)*.6),
                    spot.add(Math.cos(b)*.6,0,Math.sin(b)*.6),.035,0xCFF9D480);
            }
        }
        pose.pushPose();
        Vec3 heading=sword.facing(partial);
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0,-1,0),new Vector3f((float)heading.x,(float)heading.y,(float)heading.z)));
        float scale=sword.stepThrow()?.4F:.65F;
        pose.scale(scale,scale,scale);
        weapons.renderSword(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(YangJianRenderer.TEXTURE)),
            LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0xFFFFFFFF);
        pose.popPose();
        super.render(sword,yaw,partial,pose,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(DivineFlyingSword sword) { return YangJianRenderer.TEXTURE; }
}
