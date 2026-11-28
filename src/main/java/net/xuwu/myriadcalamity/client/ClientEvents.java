package net.xuwu.myriadcalamity.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.entity.CogworkDancer;
import net.xuwu.myriadcalamity.entity.DivineDragon;
import net.xuwu.myriadcalamity.entity.YangJian;

@Mod.EventBusSubscriber(modid = MyriadCalamity.ID, value = Dist.CLIENT)
public final class ClientEvents {
    private static final double MUSIC_RANGE_SQUARED = 148.0D * 148.0D;
    private static final int SOURCE_NONE = 0;
    private static final int SOURCE_DANCER = 1;
    private static final int SOURCE_YANG_JIAN = 2;
    private static final int SOURCE_DIVINE_DRAGON = 3;

    // Exact measured Vorbis stream lengths in 20-TPS ticks for seamless tail-to-head loop crossfading
    private static final int TICKS_DIVINE_DRAGON_P1 = 2691;
    private static final int TICKS_DIVINE_DRAGON_P2 = 2850;
    private static final int TICKS_DANCER_P1 = 1520;
    private static final int TICKS_DANCER_P2 = 560;
    private static final int TICKS_DANCER_P3 = 1120;
    private static final int TICKS_DANCER_P4 = 760;
    private static final int TICKS_YANG_JIAN_MAIN = 8508;

    private static CrossfadingMusicInstance battleMusic;
    private static final List<CrossfadingMusicInstance> fadingOutTracks = new ArrayList<>();
    private static ClientLevel musicLevel;
    private static int musicPhase = -1;
    private static UUID musicEntity;
    private static int musicSource = SOURCE_NONE;
    private static int yangJianTrack = -1;

    @SubscribeEvent
    public static void battleMusic(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            hardStopAllMusic(minecraft);
            return;
        }
        cleanupFinishedFades(minecraft);

        DivineDragon nearestDragon = null;
        double nearestDragonDistance = MUSIC_RANGE_SQUARED;
        YangJian nearestYangJian = null;
        double nearestYangJianDistance = MUSIC_RANGE_SQUARED;
        CogworkDancer nearestDancer = null;
        double nearestDancerDistance = MUSIC_RANGE_SQUARED;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof DivineDragon dragon && dragon.isAlive() && !dragon.isDissolving() && !dragon.isIntroConcealed()) {
                double distance = dragon.distanceToSqr(minecraft.player);
                if (distance < nearestDragonDistance) {
                    nearestDragon = dragon;
                    nearestDragonDistance = distance;
                }
            } else if (entity instanceof YangJian yangJian && yangJian.isAlive() && !yangJian.isTrialComplete()) {
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
        if (nearestDragon != null) {
            playDivineDragonMusic(minecraft, nearestDragon);
            return;
        }
        if (nearestYangJian != null) {
            playYangJianMusic(minecraft, nearestYangJian);
            return;
        }
        if (nearestDancer == null) {
            fadeOutBattleMusic(BattleMusicRules.PHASE_CROSSFADE_TICKS);
            return;
        }
        playDancerMusic(minecraft, nearestDancer);
    }

    private static void playDivineDragonMusic(Minecraft minecraft, DivineDragon boss) {
        int phase = boss.phase() >= 2 ? 2 : 1;
        minecraft.getMusicManager().stopPlaying();
        boolean sameBoss = musicSource == SOURCE_DIVINE_DRAGON && musicEntity != null && musicEntity.equals(boss.getUUID());
        boolean active = isTrackHealthy(minecraft);
        SoundEvent track = phase >= 2
            ? MyriadCalamity.DIVINE_DRAGON_MUSIC_PHASE2.get()
            : MyriadCalamity.DIVINE_DRAGON_MUSIC_PHASE1.get();
        int durationTicks = phase >= 2 ? TICKS_DIVINE_DRAGON_P2 : TICKS_DIVINE_DRAGON_P1;

        if (BattleMusicRules.restartDivineDragonTrack(musicLevel == minecraft.level, sameBoss, active, musicPhase, phase)) {
            int fadeTicks = (sameBoss && musicLevel == minecraft.level) ? BattleMusicRules.PHASE_CROSSFADE_TICKS : 24;
            fadeOutCurrentOnly(fadeTicks);
            musicLevel = minecraft.level;
            musicEntity = boss.getUUID();
            musicPhase = phase;
            musicSource = SOURCE_DIVINE_DRAGON;
            startTrack(minecraft, track, durationTicks, fadeTicks);
            return;
        }
        checkLoopCrossfade(minecraft, track, durationTicks);
    }

    private static void playDancerMusic(Minecraft minecraft, CogworkDancer nearest) {
        int phase = Mth.clamp(nearest.phase(), 1, 4);
        minecraft.getMusicManager().stopPlaying();
        boolean active = isTrackHealthy(minecraft);
        SoundEvent track = soundForPhase(phase);
        int durationTicks = dancerDurationTicks(phase);

        if (BattleMusicRules.restartDancerTrack(musicLevel == minecraft.level, battleMusic != null,
                musicSource != SOURCE_DANCER, active, musicPhase, phase)) {
            fadeOutCurrentOnly(BattleMusicRules.PHASE_CROSSFADE_TICKS);
            musicLevel = minecraft.level;
            musicPhase = phase;
            musicSource = SOURCE_DANCER;
            startTrack(minecraft, track, durationTicks, BattleMusicRules.PHASE_CROSSFADE_TICKS);
            return;
        }
        checkLoopCrossfade(minecraft, track, durationTicks);
    }

    private static void playYangJianMusic(Minecraft minecraft, YangJian boss) {
        minecraft.getMusicManager().stopPlaying();
        boolean sameBoss = musicSource == SOURCE_YANG_JIAN && musicEntity != null && musicEntity.equals(boss.getUUID());
        boolean active = isTrackHealthy(minecraft);
        int wantedTrack = boss.isArenaBoss() && boss.isArenaPreparing() ? 0 : 1;
        boolean introFinished = sameBoss && yangJianTrack == 0 && (!boss.isArenaPreparing() || !active);

        if (BattleMusicRules.restartYangJianTrack(musicLevel == minecraft.level, sameBoss, active, introFinished)) {
            int trackIdx = sameBoss && yangJianTrack == 0 && introFinished ? 1 : wantedTrack;
            int fadeTicks = introFinished ? 8 : 24;
            fadeOutCurrentOnly(fadeTicks);
            musicLevel = minecraft.level;
            musicEntity = boss.getUUID();
            musicSource = SOURCE_YANG_JIAN;
            yangJianTrack = trackIdx;
            SoundEvent ev = trackIdx == 0
                ? MyriadCalamity.YANG_JIAN_MUSIC_INTRO.get()
                : MyriadCalamity.YANG_JIAN_MUSIC_MAIN.get();
            startTrack(minecraft, ev, trackIdx == 0 ? 0 : TICKS_YANG_JIAN_MAIN, trackIdx == 0 ? 6 : fadeTicks);
            return;
        }
        if (yangJianTrack == 1) {
            checkLoopCrossfade(minecraft, MyriadCalamity.YANG_JIAN_MUSIC_MAIN.get(), TICKS_YANG_JIAN_MAIN);
        }
    }

    /** Seamlessly crossfades the ending tail of a looping track into a fresh opening copy! */
    private static void checkLoopCrossfade(Minecraft minecraft, SoundEvent track, int durationTicks) {
        if (battleMusic == null || durationTicks <= 0) return;
        if (BattleMusicRules.shouldCrossfadeLoop(battleMusic.ageTicks(), durationTicks, BattleMusicRules.LOOP_CROSSFADE_TICKS)) {
            fadeOutCurrentOnly(BattleMusicRules.LOOP_CROSSFADE_TICKS);
            startTrack(minecraft, track, durationTicks, BattleMusicRules.LOOP_CROSSFADE_TICKS);
        }
    }

    private static boolean isTrackHealthy(Minecraft minecraft) {
        return battleMusic != null && !battleMusic.isStopped() && minecraft.getSoundManager().isActive(battleMusic);
    }

    private static void startTrack(Minecraft minecraft, SoundEvent event, int durationTicks, int fadeInTicks) {
        battleMusic = new CrossfadingMusicInstance(event, durationTicks, fadeInTicks);
        minecraft.getSoundManager().play(battleMusic);
    }

    private static void fadeOutCurrentOnly(int fadeTicks) {
        if (battleMusic != null) {
            battleMusic.beginFadeOut(fadeTicks);
            fadingOutTracks.add(battleMusic);
            battleMusic = null;
        }
    }

    private static void fadeOutBattleMusic(int fadeTicks) {
        fadeOutCurrentOnly(fadeTicks);
        musicLevel = null;
        musicPhase = -1;
        musicEntity = null;
        musicSource = SOURCE_NONE;
        yangJianTrack = -1;
    }

    private static void cleanupFinishedFades(Minecraft minecraft) {
        Iterator<CrossfadingMusicInstance> it = fadingOutTracks.iterator();
        while (it.hasNext()) {
            CrossfadingMusicInstance inst = it.next();
            if (inst.isStopped() || !minecraft.getSoundManager().isActive(inst)) {
                minecraft.getSoundManager().stop(inst);
                it.remove();
            }
        }
    }

    private static void hardStopAllMusic(Minecraft minecraft) {
        if (battleMusic != null) minecraft.getSoundManager().stop(battleMusic);
        for (CrossfadingMusicInstance inst : fadingOutTracks) {
            minecraft.getSoundManager().stop(inst);
        }
        fadingOutTracks.clear();
        battleMusic = null;
        musicLevel = null;
        musicPhase = -1;
        musicEntity = null;
        musicSource = SOURCE_NONE;
        yangJianTrack = -1;
    }

    private static SoundEvent soundForPhase(int phase) {
        return switch (phase) {
            case 2 -> MyriadCalamity.DANCER_MUSIC_PHASE2.get();
            case 3 -> MyriadCalamity.DANCER_MUSIC_PHASE3.get();
            case 4 -> MyriadCalamity.DANCER_MUSIC_PHASE4.get();
            default -> MyriadCalamity.DANCER_MUSIC_PHASE1.get();
        };
    }

    private static int dancerDurationTicks(int phase) {
        return switch (phase) {
            case 2 -> TICKS_DANCER_P2;
            case 3 -> TICKS_DANCER_P3;
            case 4 -> TICKS_DANCER_P4;
            default -> TICKS_DANCER_P1;
        };
    }

    /** Tickable music stream supporting overlapping smoothstep fade-in and fade-out transitions. */
    private static final class CrossfadingMusicInstance extends AbstractTickableSoundInstance {
        private final int durationTicks;
        private final int fadeInTicks;
        private int ageTicks;
        private int fadeOutTotal;
        private int fadeOutRemaining;

        CrossfadingMusicInstance(SoundEvent event, int durationTicks, int fadeInTicks) {
            super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.durationTicks = durationTicks;
            this.fadeInTicks = Math.max(1, fadeInTicks);
            this.looping = false;
            this.delay = 0;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
            // Start at 0.02F so OpenAL allocates the streaming channel cleanly on tick 0
            this.volume = fadeInTicks > 1 ? 0.02F : 1.0F;
        }

        int ageTicks() {
            return ageTicks;
        }

        void beginFadeOut(int ticks) {
            if (fadeOutTotal > 0) return;
            this.fadeOutTotal = Math.max(1, ticks);
            this.fadeOutRemaining = this.fadeOutTotal;
        }

        @Override
        public void tick() {
            ageTicks++;
            if (fadeOutTotal > 0) {
                fadeOutRemaining--;
                if (fadeOutRemaining <= 0) {
                    this.volume = 0.0F;
                    this.stop();
                    return;
                }
            }
            this.volume = BattleMusicRules.crossfadeVolume(ageTicks, fadeInTicks, fadeOutRemaining, fadeOutTotal);
        }
    }
}
