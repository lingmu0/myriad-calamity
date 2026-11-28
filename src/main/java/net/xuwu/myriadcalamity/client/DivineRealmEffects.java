package net.xuwu.myriadcalamity.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.entity.DivineDragon;
import net.xuwu.myriadcalamity.entity.DivineDragonMath;
import net.xuwu.myriadcalamity.world.DivineArena;

/**
 * Fountainhead Sakura Cloud-Sea atmosphere:
 *   - Swirling mist, dark thunderheads, crashing sky lightning, colossal central Sakura Tree bolt,
 *     blinding full-screen white flash, and camera tremor during the Divine Dragon's arena entrance.
 *   - Wide twilight visibility with drifting petals and rim cloud sea once the roar clears the mist.
 */
@EventBusSubscriber(modid = MyriadCalamity.ID, value = Dist.CLIENT)
public final class DivineRealmEffects extends DimensionSpecialEffects {
    public DivineRealmEffects() {
        super(121.0F, false, SkyType.NORMAL, false, false);
    }

    @SubscribeEvent
    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(MyriadCalamity.id("divine_realm"), new DivineRealmEffects());
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return new Vec3(0.32, 0.40, 0.54);
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    private static DivineDragon findIntroDragon(Level level) {
        if (!(level instanceof ClientLevel cl)) return null;
        for (Entity e : cl.entitiesForRendering()) {
            if (e instanceof DivineDragon dragon && dragon.isAlive() && dragon.isIntroActive()) {
                return dragon;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        var level = event.getCamera().getEntity().level();
        if (!DivineArena.inDivineRealm(level) || event.getCamera().getFluidInCamera() != FogType.NONE) return;
        float rain = level.getRainLevel(1.0F);
        float thunder = level.getThunderLevel(1.0F);
        float r = 0.46F - 0.16F * rain - 0.08F * thunder;
        float g = 0.54F - 0.18F * rain - 0.10F * thunder;
        float b = 0.68F - 0.14F * rain - 0.08F * thunder;

        DivineDragon intro = findIntroDragon(level);
        if (intro != null) {
            float age = intro.actionAge((float) event.getPartialTick());
            float mist = DivineDragonMath.introMistDensity(age);
            float flash = DivineDragonMath.introSkyFlash(age);
            // Moody silver-slate swirling mist tinted bright blue-white on every sky lightning flash
            r = Mth.lerp(mist, r, 0.28F);
            g = Mth.lerp(mist, g, 0.34F);
            b = Mth.lerp(mist, b, 0.44F);
            r = Mth.lerp(flash, r, 0.96F);
            g = Mth.lerp(flash, g, 0.98F);
            b = Mth.lerp(flash, b, 1.00F);
        }

        event.setRed(r);
        event.setGreen(g);
        event.setBlue(b);
    }

    @SubscribeEvent
    public static void fogDistance(ViewportEvent.RenderFog event) {
        var level = event.getCamera().getEntity().level();
        if (!DivineArena.inDivineRealm(level)
                || event.getType() != FogType.NONE
                || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;

        float near = 128.0F;
        float far = 268.0F;
        DivineDragon intro = findIntroDragon(level);
        if (intro != null) {
            float age = intro.actionAge((float) event.getPartialTick());
            float mist = DivineDragonMath.introMistDensity(age);
            // Pull fog close during the pre-spawn storm so the Sakura Tree looms through thick swirling mist!
            near = Mth.lerp(mist, 128.0F, 14.0F);
            far = Mth.lerp(mist, 268.0F, 98.0F);
        }
        event.setNearPlaneDistance(near);
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void introCameraShake(ViewportEvent.ComputeCameraAngles event) {
        var level = event.getCamera().getEntity().level();
        if (!DivineArena.inDivineRealm(level)) return;
        DivineDragon intro = findIntroDragon(level);
        if (intro == null) return;
        float age = intro.actionAge((float) event.getPartialTick());
        float shake = DivineDragonMath.introCameraShake(age);
        if (shake <= 0.005F) return;

        float yawOff = (float) (Math.sin(age * 3.7) * 1.45 + Math.cos(age * 6.3) * 0.65) * shake;
        float pitchOff = (float) (Math.cos(age * 4.4) * 1.35 + Math.sin(age * 7.1) * 0.55) * shake;
        float rollOff = (float) (Math.sin(age * 5.2) * 1.85) * shake;
        event.setYaw(event.getYaw() + yawOff);
        event.setPitch(event.getPitch() + pitchOff);
        event.setRoll(event.getRoll() + rollOff);
    }

    @SubscribeEvent
    public static void renderIntroStormWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !DivineArena.inDivineRealm(minecraft.level)) return;
        if (event.getCamera().getFluidInCamera() != FogType.NONE) return;
        DivineDragon intro = findIntroDragon(minecraft.level);
        if (intro == null) return;

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float age = intro.actionAge(partial);
        float mist = DivineDragonMath.introMistDensity(age);
        float flash = DivineDragonMath.introSkyFlash(age);
        Vec3 floorCenter = new Vec3(0.5, 129.0, 0.5).subtract(event.getCamera().getPosition());

        var buffers = minecraft.renderBuffers().bufferSource();
        DivineDragonVfx.introStormAndMegaBolt(
            CombatEffectRenderLayer.buffer(buffers),
            event.getPoseStack().last().pose(),
            floorCenter, age, mist, flash, intro.getId() + 313);
        CombatEffectRenderLayer.flush(buffers);
    }

    @SubscribeEvent
    public static void renderIntroWhiteFlash(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !DivineArena.inDivineRealm(minecraft.level)) return;
        DivineDragon intro = findIntroDragon(minecraft.level);
        if (intro == null) return;

        float age = intro.actionAge(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        float alpha = DivineDragonMath.introWhiteFlashAlpha(age);
        if (alpha <= 0.008F) return;
        if (minecraft.options.hideLightningFlash().get()) {
            alpha *= 0.35F;
        }
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        if (a <= 0) return;
        int w = event.getGuiGraphics().guiWidth();
        int h = event.getGuiGraphics().guiHeight();
        event.getGuiGraphics().fill(0, 0, w, h, (a << 24) | 0xFFFFFF);
    }

    @SubscribeEvent
    public static void atmosphereParticles(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused() || minecraft.level == null || minecraft.player == null) return;
        if (!DivineArena.inDivineRealm(minecraft.level)) return;

        DivineDragon intro = findIntroDragon(minecraft.level);
        var random = minecraft.level.random;
        if (intro != null) {
            // Keep thunderstorm rain/sky immediately synchronized from frame 1 on entering the dimension
            DivineArena.applyWeather(minecraft.level, intro.weather());
            float age = intro.actionAge(0.0F);
            float mist = DivineDragonMath.introMistDensity(age);
            if (mist > 0.05F) {
                int mistCount = Math.max(2, (int) (14 * mist));
                for (int i = 0; i < mistCount; i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0;
                    double radius = 10.0 + random.nextDouble() * 44.0;
                    double sx = -Math.sin(angle) * 0.14;
                    double sz = Math.cos(angle) * 0.14;
                    minecraft.level.addParticle(i % 3 == 0 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.CLOUD,
                        0.5 + Math.cos(angle) * radius,
                        128.5 + random.nextDouble() * 28.0,
                        0.5 + Math.sin(angle) * radius,
                        sx, 0.015 + random.nextDouble() * 0.03, sz);
                }
                // Swirling mist drifting right around the player's vantage point
                for (int i = 0; i < 6; i++) {
                    double px = minecraft.player.getX() + (random.nextDouble() - 0.5) * 34.0;
                    double py = 128.6 + random.nextDouble() * 11.0;
                    double pz = minecraft.player.getZ() + (random.nextDouble() - 0.5) * 34.0;
                    minecraft.level.addParticle(ParticleTypes.CLOUD, px, py, pz, 0.11, 0.012, -0.05);
                }
                // Crackling gathering sparks around the ancient Sakura Tree crown right before the colossal bolt
                if (age >= 58.0F && age < DivineDragonMath.INTRO_MEGA_BOLT_TICK) {
                    for (int i = 0; i < 7; i++) {
                        double a = random.nextDouble() * Math.PI * 2.0;
                        double r = 4.0 + random.nextDouble() * 16.0;
                        minecraft.level.addParticle(i % 2 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD,
                            0.5 + Math.cos(a) * r,
                            146.0 + random.nextDouble() * 18.0,
                            0.5 + Math.sin(a) * r,
                            -Math.cos(a) * 0.22, 0.18, -Math.sin(a) * 0.22);
                    }
                }
            }
        }

        if (minecraft.level.getGameTime() % 2 != 0) return;

        // Swirling outer cloud sea rim scaled for the 86-block Sekiro arena
        for (int i = 0; i < 6; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = 82.0 + random.nextDouble() * 22.0;
            minecraft.level.addParticle(ParticleTypes.CLOUD,
                0.5 + Math.cos(angle) * radius,
                125.0 + random.nextDouble() * 4.5,
                0.5 + Math.sin(angle) * radius,
                0.03, 0.006, 0.015);
        }
        // Drifting cherry blossom petals carried across the arena wind
        for (int i = 0; i < 5; i++) {
            double px = minecraft.player.getX() + (random.nextDouble() - 0.5) * 48.0;
            double py = minecraft.player.getY() + 2.0 + random.nextDouble() * 22.0;
            double pz = minecraft.player.getZ() + (random.nextDouble() - 0.5) * 48.0;
            minecraft.level.addParticle(ParticleTypes.CHERRY_LEAVES,
                px, py, pz, 0.06, -0.025, 0.04);
        }
    }
}
