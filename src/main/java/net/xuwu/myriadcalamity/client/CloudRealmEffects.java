package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.world.CloudArena;

/** Low cloud sea and distant haze; the full 44-block fighting diameter stays clear. */
@EventBusSubscriber(modid = MyriadCalamity.ID, value = Dist.CLIENT)
public final class CloudRealmEffects {
    private static final ContextKey<CombatGeometry> STORM=new ContextKey<>(MyriadCalamity.id("cloud_realm_storm"));

    @SubscribeEvent
    public static void wisps(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) return;
        YangJianStorm.tick(minecraft);
        if (minecraft.level == null || minecraft.player == null
                || !CloudArena.inCloudRealm(minecraft.level) || minecraft.level.getGameTime() % 3 != 0) return;
        var random = minecraft.level.getRandom();
        for (int i = 0; i < 3; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = 25 + random.nextDouble() * 15;
            minecraft.level.addParticle(ParticleTypes.CLOUD, .5 + Math.cos(angle) * radius,
                124 + random.nextDouble() * 5, .5 + Math.sin(angle) * radius, .028, .005, .009);
        }
    }

    @SubscribeEvent
    public static void extractStorm(ExtractLevelRenderStateEvent event) {
        if (!YangJianStorm.visible(event.getLevel()) || event.getCamera().getFluidInCamera() != FogType.NONE) return;
        CombatGeometry geometry = new CombatGeometry();
        YangJianStorm.render(event.getLevel(), event.getDeltaTracker().getGameTimeDeltaPartialTick(false),
            event.getCamera().position(), Minecraft.getInstance().options.hideLightningFlash().get(),
            geometry.quads(), new PoseStack().last().pose());
        event.getRenderState().setRenderData(STORM,geometry);
    }

    @SubscribeEvent
    public static void submitStorm(SubmitCustomGeometryEvent event) {
        var geometry = event.getLevelRenderState().getRenderData(STORM);
        if (geometry!=null) geometry.submit(event.getPoseStack(),event.getSubmitNodeCollector());
    }
}
