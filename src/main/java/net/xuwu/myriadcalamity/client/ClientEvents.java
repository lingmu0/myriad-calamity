package net.xuwu.myriadcalamity.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.entity.CogworkDancer;
import net.xuwu.myriadcalamity.entity.YangJian;

@Mod.EventBusSubscriber(modid=MyriadCalamity.ID,value=Dist.CLIENT)
public final class ClientEvents {
    private static final double MUSIC_RANGE_SQUARED = 128.0D * 128.0D;
    private static SoundInstance battleMusic;
    private static ClientLevel musicLevel;
    private static int musicPhase = -1;
    private static java.util.UUID musicEntity;
    private static boolean musicWasYangJian;
    private static int yangJianTrack = -1;
    @SubscribeEvent
    public static void battleMusic(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            stopBattleMusic(minecraft);
            return;
        }
        YangJian nearestYangJian = null;
        double nearestYangJianDistance = MUSIC_RANGE_SQUARED;
        CogworkDancer nearestDancer = null;
        double nearestDancerDistance = MUSIC_RANGE_SQUARED;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof YangJian yangJian && yangJian.isAlive() && !yangJian.isTrialComplete()) {
                double distance = yangJian.distanceToSqr(minecraft.player);
                if (distance < nearestYangJianDistance) {
                    nearestYangJian = yangJian;
                    nearestYangJianDistance = distance;
                }
            } else if (entity instanceof CogworkDancer dancer && dancer.isAlive()) {
                double distance = dancer.distanceToSqr(minecraft.player);
                if (distance < nearestDancerDistance) {
                    nearestDancer = dancer;
                    nearestDancerDistance = distance;
                }
            }
        }
        // Yang Jian's track takes priority if both encounters happen to be loaded.
        if (nearestYangJian != null) {
            playYangJianMusic(minecraft, nearestYangJian);
            return;
        }
        if (nearestDancer == null) {
            stopBattleMusic(minecraft);
            return;
        }
        playDancerMusic(minecraft, nearestDancer);
    }

    private static void playDancerMusic(Minecraft minecraft, CogworkDancer nearest) {
        int phase = Mth.clamp(nearest.phase(), 1, 4);
        // Keep the regular situational track from competing with the encounter score.
        minecraft.getMusicManager().stopPlaying();
        // The pair shares one health pool and one phase, and they swap places as they dash, so
        // only the level, the phase and a surviving track may re-key the score. Restarting on the
        // nearest dancer cut the music back to its opening bars every time the player crossed
        // between the two, which is what made the duet score sound broken.
        boolean active = battleMusic != null && minecraft.getSoundManager().isActive(battleMusic);
        if (BattleMusicRules.restartDancerTrack(musicLevel == minecraft.level, battleMusic != null,
                musicWasYangJian, active, musicPhase, phase)) {
            stopBattleMusic(minecraft);
            musicLevel = minecraft.level;
            musicPhase = phase;
            musicWasYangJian = false;
            battleMusic = SimpleSoundInstance.forMusic(soundForPhase(phase));
            minecraft.getSoundManager().play(battleMusic);
        }
    }

    private static void playYangJianMusic(Minecraft minecraft, YangJian boss) {
        minecraft.getMusicManager().stopPlaying();
        boolean sameBoss = musicWasYangJian && musicEntity != null && musicEntity.equals(boss.getUUID());
        boolean active = battleMusic != null && minecraft.getSoundManager().isActive(battleMusic);
        int wantedTrack = boss.isArenaBoss() && boss.isArenaPreparing() ? 0 : 1;
        boolean introFinished = sameBoss && yangJianTrack == 0 && (!boss.isArenaPreparing() || !active);
        if (BattleMusicRules.restartYangJianTrack(musicLevel == minecraft.level, sameBoss, active, introFinished)) {
            // The arena entrance uses the four-second 49-53 cut while the boss is
            // hidden. Once it appears, and for every direct summon/replay, use 53s.
            int track = sameBoss && yangJianTrack == 0 && introFinished ? 1 : wantedTrack;
            stopBattleMusic(minecraft);
            musicLevel = minecraft.level;
            musicEntity = boss.getUUID();
            musicWasYangJian = true;
            yangJianTrack = track;
            battleMusic = SimpleSoundInstance.forMusic(track == 0
                    ? MyriadCalamity.YANG_JIAN_MUSIC_INTRO.get()
                    : MyriadCalamity.YANG_JIAN_MUSIC_MAIN.get());
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
        musicEntity = null;
        musicWasYangJian = false;
        yangJianTrack = -1;
    }
}
