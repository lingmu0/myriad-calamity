package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.entity.TriPointedBlade;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The held three-pointed spear itself, maintaining its flight heading without decorative spinning. */
public final class TriPointedBladeRenderer extends CombatEntityRenderer<TriPointedBlade> {
    private final YangJianModel model;
    private final YangJianProjectileTrail trail = new YangJianProjectileTrail();
    public TriPointedBladeRenderer(EntityRendererProvider.Context context) {
        super(context);model=new YangJianModel(context.getResourceManager());shadowRadius=.2F;
    }
    @Override protected void collect(TriPointedBlade blade,float partial,PoseStack pose,CombatGeometry buffers,int light) {
        trail.render(blade, partial, 5.2, .20, YangJianVisuals.GOLD,
            buffers.quads(), pose.last().pose());
        pose.pushPose();
        Vec3 motion=blade.getDeltaMovement();
        if(motion.lengthSqr()<1E-7)motion=blade.getLookAngle();
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0,-1,0),
            new Vector3f((float)motion.x,(float)motion.y,(float)motion.z).normalize()));
        float scale=YangJianRenderer.MODEL_SCALE;
        pose.scale(scale,scale,scale);
        buffers.mesh(pose,RenderTypes.entityCutout(YangJianRenderer.TEXTURE),(stack,vertices)->
            model.renderWeapon(stack,vertices,light,OverlayTexture.NO_OVERLAY));
        pose.popPose();
    }
}
