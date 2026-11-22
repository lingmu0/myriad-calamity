package net.xuwu.myriadcalamity.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.world.CloudArena;

/** Low cloud sea and distant haze; the full 44-block fighting diameter stays clear. */
@EventBusSubscriber(modid = MyriadCalamity.ID, value = Dist.CLIENT)
public final class CloudRealmEffects {

    @SubscribeEvent
    public static void wisps(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()
                || !CloudArena.inCloudRealm(minecraft.level) || minecraft.level.getGameTime() % 3 != 0) return;
        var random = minecraft.level.getRandom();
        for (int i = 0; i < 3; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = 25 + random.nextDouble() * 15;
            minecraft.level.addParticle(ParticleTypes.CLOUD, .5 + Math.cos(angle) * radius,
                124 + random.nextDouble() * 5, .5 + Math.sin(angle) * radius, .028, .005, .009);
        }
    }
}
