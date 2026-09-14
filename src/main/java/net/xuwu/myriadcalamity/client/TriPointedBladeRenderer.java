package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.entity.TriPointedBlade;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The held three-pointed spear itself, maintaining its flight heading without decorative spinning. */
public final class TriPointedBladeRenderer extends EntityRenderer<TriPointedBlade> {
    private final YangJianModel model;
    public TriPointedBladeRenderer(EntityRendererProvider.Context context) {
        super(context);model=new YangJianModel(context.getResourceManager());shadowRadius=.2F;
    }
    @Override public void render(TriPointedBlade blade,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        pose.pushPose();
        Vec3 motion=blade.getDeltaMovement();
        if(motion.lengthSqr()<1E-7)motion=blade.getLookAngle();
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0,-1,0),
            new Vector3f((float)motion.x,(float)motion.y,(float)motion.z).normalize()));
        float scale=YangJianRenderer.MODEL_SCALE;
        pose.scale(scale,scale,scale);
        model.renderWeapon(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(YangJianRenderer.TEXTURE)),light,OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(blade,yaw,partial,pose,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(TriPointedBlade blade) { return YangJianRenderer.TEXTURE; }
}
