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
}
