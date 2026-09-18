package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.entity.CelestialHound;

public final class CelestialHoundRenderer extends MobRenderer<CelestialHound,CelestialHoundModel> {
    private static final ResourceLocation TEXTURE=MyriadCalamity.id("textures/entity/celestial_hound.png");
    public CelestialHoundRenderer(EntityRendererProvider.Context context) {super(context,new CelestialHoundModel(),.52F);}
    @Override protected void scale(CelestialHound dog,PoseStack pose,float partial) {pose.scale(1.18F,1.18F,1.18F);}
    @Override public ResourceLocation getTextureLocation(CelestialHound dog) {return TEXTURE;}
}
