package net.xuwu.myriadcalamity.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.world.CloudArena;

/** Low cloud sea and distant haze; the full 44-block fighting diameter stays clear. */
@EventBusSubscriber(modid = MyriadCalamity.ID, value = Dist.CLIENT)
public final class CloudRealmEffects extends DimensionSpecialEffects {
    public CloudRealmEffects() { super(119, false, SkyType.NORMAL, false, false); }

    @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return new Vec3(.78, .85, .91);
    }
    @Override public boolean isFoggyAt(int x, int z) { return false; }

    @SubscribeEvent
    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(MyriadCalamity.id("cloud_realm"), new CloudRealmEffects());
    }

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        if (!CloudArena.inCloudRealm(event.getCamera().getEntity().level())
                || event.getCamera().getFluidInCamera() != FogType.NONE) return;
        var level = event.getCamera().getEntity().level();
        float rain = level.getRainLevel(1), thunder = level.getThunderLevel(1);
        event.setRed(.78F - .26F * rain - .06F * thunder);
        event.setGreen(.85F - .29F * rain - .14F * thunder);
        event.setBlue(.91F - .29F * rain - .12F * thunder);
    }

    @SubscribeEvent
    public static void fogDistance(ViewportEvent.RenderFog event) {
        if (!CloudArena.inCloudRealm(event.getCamera().getEntity().level()) || event.getType() != FogType.NONE
                || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;
        event.setNearPlaneDistance(52);
        event.setFarPlaneDistance(116);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void wisps(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) return;
        YangJianStorm.tick(minecraft);
        if (minecraft.level == null || minecraft.player == null
                || !CloudArena.inCloudRealm(minecraft.level) || minecraft.level.getGameTime() % 3 != 0) return;
        var random = minecraft.level.random;
        for (int i = 0; i < 3; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = 25 + random.nextDouble() * 15;
            minecraft.level.addParticle(ParticleTypes.CLOUD, .5 + Math.cos(angle) * radius,
                124 + random.nextDouble() * 5, .5 + Math.sin(angle) * radius, .028, .005, .009);
        }
    }

    @SubscribeEvent
    public static void storm(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!YangJianStorm.visible(minecraft.level) || event.getCamera().getFluidInCamera() != FogType.NONE) return;
        var buffers = minecraft.renderBuffers().bufferSource();
        YangJianStorm.render(minecraft.level, event.getPartialTick().getGameTimeDeltaPartialTick(false),
            event.getCamera().getPosition(), minecraft.options.hideLightningFlash().get(),
            CombatEffectRenderLayer.buffer(buffers), event.getPoseStack().last().pose());
        CombatEffectRenderLayer.flush(buffers);
    }
}
