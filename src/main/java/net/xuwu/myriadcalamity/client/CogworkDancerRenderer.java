package net.xuwu.myriadcalamity.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.entity.CogworkDancer;

public final class CogworkDancerRenderer extends MobRenderer<CogworkDancer,CogworkDancerModel> {
    private static final ResourceLocation GOLD=MyriadCalamity.id("textures/entity/cogwork_dancer.png");
    private static final ResourceLocation SILVER=MyriadCalamity.id("textures/entity/cogwork_dancer_silver.png");
    public CogworkDancerRenderer(EntityRendererProvider.Context context) {
        super(context,new CogworkDancerModel(context.getResourceManager()),0.65F);
    }
    @Override public ResourceLocation getTextureLocation(CogworkDancer entity) { return entity.follower()?SILVER:GOLD; }
}