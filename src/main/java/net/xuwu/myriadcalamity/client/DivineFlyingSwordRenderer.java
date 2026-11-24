package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.entity.DivineFlyingSword;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Visible revolving jian blades align with their flight direction; neither volley draws targeting rays. */
public final class DivineFlyingSwordRenderer extends CombatEntityRenderer<DivineFlyingSword> {
    private final YangJianWeapons weapons;
    private final YangJianProjectileTrail trail = new YangJianProjectileTrail();
    public DivineFlyingSwordRenderer(EntityRendererProvider.Context context) {
        super(context);weapons=new YangJianWeapons(context.getResourceManager());shadowRadius=.12F;
    }
    @Override protected AABB getBoundingBoxForCulling(DivineFlyingSword entity) { return entity.combatRenderBounds(); }

    @Override protected void collect(DivineFlyingSword sword,float partial,PoseStack pose,CombatGeometry buffers,int light) {
        if(!sword.hasPlan())return;
        if (!sword.stepThrow() || sword.flying(partial)) {
            trail.render(sword, partial, sword.myriadSword() ? 6.2 : 4.8, sword.stepThrow() ? .12 : .19,
                sword.myriadSword() ? YangJianVisuals.RED : YangJianVisuals.SILVER,
                buffers.quads(), pose.last().pose());
        }
        pose.pushPose();
        Vec3 heading=sword.facing(partial);
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0,-1,0),new Vector3f((float)heading.x,(float)heading.y,(float)heading.z)));
        float scale=sword.stepThrow()?.4F:.65F;
        pose.scale(scale,scale,scale);
        buffers.mesh(pose,RenderTypes.entityCutout(YangJianRenderer.TEXTURE),(stack,vertices)->
            weapons.renderSword(stack,vertices,15728880,OverlayTexture.NO_OVERLAY,0xFFFFFFFF));
        pose.popPose();
    }
}
