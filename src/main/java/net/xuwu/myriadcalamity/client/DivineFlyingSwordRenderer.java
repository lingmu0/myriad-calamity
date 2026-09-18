package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
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

/** Visible revolving jian blades align with their flight direction; neither volley draws targeting rays. */
public final class DivineFlyingSwordRenderer extends EntityRenderer<DivineFlyingSword> {
    private final YangJianWeapons weapons;
    public DivineFlyingSwordRenderer(EntityRendererProvider.Context context) {
        super(context);weapons=new YangJianWeapons(context.getResourceManager());shadowRadius=.12F;
    }
    @Override public void render(DivineFlyingSword sword,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        if(!sword.hasPlan())return;
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
