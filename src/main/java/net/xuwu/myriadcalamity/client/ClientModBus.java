package net.xuwu.myriadcalamity.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.xuwu.myriadcalamity.MyriadCalamity;

/**
 * Client registrations that belong to the mod event bus.
 *
 * <p>Forge subscribes an {@code @Mod.EventBusSubscriber} class to exactly one bus, and the
 * annotation defaults to the Forge bus. The runtime client handlers therefore live in
 * {@link ClientEvents} and {@link CloudRealmEffects} on the Forge bus, while everything that
 * has to be registered while the mod loads - entity renderers and the cloud realm's
 * dimension effects - is collected here on the mod bus. Registering these on the Forge bus
 * silently skipped them, which left every custom entity without a renderer.
 */
@Mod.EventBusSubscriber(modid = MyriadCalamity.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModBus {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MyriadCalamity.DANCER.get(),CogworkDancerRenderer::new);
        event.registerEntityRenderer(MyriadCalamity.BLADE.get(),CogworkBladeRenderer::new);
        event.registerEntityRenderer(MyriadCalamity.YANG_JIAN.get(),YangJianRenderer::new);
        event.registerEntityRenderer(MyriadCalamity.CELESTIAL_HOUND.get(),CelestialHoundRenderer::new);
        event.registerEntityRenderer(MyriadCalamity.TRI_POINTED_BLADE.get(),TriPointedBladeRenderer::new);
        event.registerEntityRenderer(MyriadCalamity.DIVINE_FLYING_SWORD.get(),DivineFlyingSwordRenderer::new);
        event.registerEntityRenderer(MyriadCalamity.LIGHTNING_TRAIL.get(),LightningTrailRenderer::new);
        event.registerEntityRenderer(MyriadCalamity.YANG_JIAN_HAZARD.get(),YangJianHazardRenderer::new);
    }

    @SubscribeEvent
    public static void dimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(MyriadCalamity.id("cloud_realm"), new CloudRealmEffects());
    }

    private ClientModBus() {}
}
