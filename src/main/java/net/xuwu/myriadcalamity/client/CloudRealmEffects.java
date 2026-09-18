package net.xuwu.myriadcalamity.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.world.CloudArena;

/** Low cloud sea and distant haze; the full 44-block fighting diameter stays clear. */
@Mod.EventBusSubscriber(modid = MyriadCalamity.ID, value = Dist.CLIENT)
public final class CloudRealmEffects extends DimensionSpecialEffects {
    public CloudRealmEffects() { super(119, false, SkyType.NORMAL, false, false); }

    @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return new Vec3(.78, .85, .91);
    }
    @Override public boolean isFoggyAt(int x, int z) { return false; }

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        if (!CloudArena.inCloudRealm(event.getCamera().getEntity().level())
                || event.getCamera().getFluidInCamera() != FogType.NONE) return;
        event.setRed(.78F);
        event.setGreen(.85F);
        event.setBlue(.91F);
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
    public static void wisps(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()
                || !CloudArena.inCloudRealm(minecraft.level) || minecraft.level.getGameTime() % 3 != 0) return;
        var random = minecraft.level.random;
        for (int i = 0; i < 3; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = 25 + random.nextDouble() * 15;
            minecraft.level.addParticle(ParticleTypes.CLOUD, .5 + Math.cos(angle) * radius,
                124 + random.nextDouble() * 5, .5 + Math.sin(angle) * radius, .028, .005, .009);
        }
    }
}
