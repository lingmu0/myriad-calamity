package net.xuwu.myriadcalamity.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.entity.CogworkDancer;

@EventBusSubscriber(modid=MyriadCalamity.ID,value=Dist.CLIENT)
public final class ClientEvents {
    private static final double MUSIC_RANGE_SQUARED = 128.0D * 128.0D;
    private static SoundInstance battleMusic;
    private static ClientLevel musicLevel;
    private static int musicPhase = -1;
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MyriadCalamity.DANCER.get(),CogworkDancerRenderer::new);
        event.registerEntityRenderer(MyriadCalamity.BLADE.get(),CogworkBladeRenderer::new);
    }

    @SubscribeEvent
    public static void battleMusic(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            stopBattleMusic(minecraft);
            return;
        }
        CogworkDancer nearest = null;
        double nearestDistance = MUSIC_RANGE_SQUARED;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof CogworkDancer dancer && dancer.isAlive()) {
                double distance = dancer.distanceToSqr(minecraft.player);
                if (distance < nearestDistance) {
                    nearest = dancer;
                    nearestDistance = distance;
                }
            }
        }
        if (nearest == null) {
            stopBattleMusic(minecraft);
            return;
        }
        int phase = Mth.clamp(nearest.phase(), 1, 4);
        // Keep the regular situational track from competing with the encounter score.
        minecraft.getMusicManager().stopPlaying();
        if (musicLevel != minecraft.level || battleMusic == null || musicPhase != phase
                || !minecraft.getSoundManager().isActive(battleMusic)) {
            stopBattleMusic(minecraft);
            musicLevel = minecraft.level;
            musicPhase = phase;
            battleMusic = SimpleSoundInstance.forMusic(soundForPhase(phase));
            minecraft.getSoundManager().play(battleMusic);
        }
    }

    private static SoundEvent soundForPhase(int phase) {
        return switch (phase) {
            case 2 -> MyriadCalamity.DANCER_MUSIC_PHASE2.get();
            case 3 -> MyriadCalamity.DANCER_MUSIC_PHASE3.get();
            case 4 -> MyriadCalamity.DANCER_MUSIC_PHASE4.get();
            default -> MyriadCalamity.DANCER_MUSIC_PHASE1.get();
        };
    }

    private static void stopBattleMusic(Minecraft minecraft) {
        if (battleMusic != null) minecraft.getSoundManager().stop(battleMusic);
        battleMusic = null;
        musicLevel = null;
        musicPhase = -1;
    }
}