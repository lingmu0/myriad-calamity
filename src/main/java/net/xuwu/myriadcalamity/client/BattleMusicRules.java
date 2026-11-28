package net.xuwu.myriadcalamity.client;

/**
 * Pure restart rules for the encounter scores, kept free of client and game types so the
 * behaviour can be checked without launching Minecraft.
 */
public final class BattleMusicRules {
    private BattleMusicRules() {}

    /**
     * Whether the dancer encounter score must be (re)started for the phase the player is in.
     *
     * The two dancers share one health pool and one phase, and they trade places as they
     * dash, so the decision deliberately ignores which dancer happens to be nearest and how
     * many dancers are loaded. Keying it on the nearest entity restarted the score every time
     * the player crossed between the pair, which cut the track back to its opening bars.
     */
    public static boolean restartDancerTrack(boolean sameLevel,boolean hasTrack,boolean previousWasYangJian,
                                             boolean trackActive,int currentPhase,int wantedPhase) {
        return !sameLevel || !hasTrack || previousWasYangJian || !trackActive || currentPhase != wantedPhase;
    }

    /**
     * Whether the Yang Jian arena score must be (re)started. This also covers the hand-off
     * from the four-second entrance cut to the main theme once the boss becomes visible, and
     * every direct summon that never used the entrance cut.
     */
    public static boolean restartYangJianTrack(boolean sameLevel,boolean sameBoss,boolean trackActive,
                                               boolean introFinished) {
        return !sameLevel || !sameBoss || !trackActive || introFinished;
    }

    public static final int LOOP_CROSSFADE_TICKS = 64;
    public static final int PHASE_CROSSFADE_TICKS = 40;

    /**
     * Whether the Divine Dragon score must be (re)started: switches seamlessly between Sekiro's
     * Phase 1 theme and Phase 2 theme, loops on completion, and restarts on boss or level change.
     */
    public static boolean restartDivineDragonTrack(boolean sameLevel,boolean sameBoss,boolean trackActive,
                                                   int currentPhase,int wantedPhase) {
        return !sameLevel || !sameBoss || !trackActive || currentPhase != wantedPhase;
    }

    /**
     * Returns true when a looping encounter track enters its tail crossfade window so the next loop
     * copy can fade in smoothly underneath the outgoing tail instead of hard-cutting at EOF.
     */
    public static boolean shouldCrossfadeLoop(int elapsedTicks,int trackDurationTicks,int crossfadeTicks) {
        return trackDurationTicks > crossfadeTicks * 2
            && crossfadeTicks > 0
            && elapsedTicks >= trackDurationTicks - crossfadeTicks;
    }

    /**
     * Smoothstep gain envelope in [0.01, 1.0] combining opening fade-in and tail/phase fade-out.
     * Starts at 0.02 rather than 0.0 so Minecraft's OpenAL SoundEngine never culls the streaming channel.
     */
    public static float crossfadeVolume(int ageTicks,int fadeInTicks,int fadeOutRemaining,int fadeOutTotal) {
        float inGain = 1.0F;
        if (fadeInTicks > 0 && ageTicks < fadeInTicks) {
            float u = Math.max(0.0F, Math.min(1.0F, (float) ageTicks / fadeInTicks));
            float s = u * u * (3.0F - 2.0F * u);
            inGain = 0.02F + 0.98F * s;
        }
        float outGain = 1.0F;
        if (fadeOutTotal > 0) {
            float u = Math.max(0.0F, Math.min(1.0F, (float) fadeOutRemaining / fadeOutTotal));
            float s = u * u * (3.0F - 2.0F * u);
            outGain = 0.01F + 0.99F * s;
        }
        return Math.max(0.01F, Math.min(1.0F, inGain * outGain));
    }
}
