package net.xuwu.myriadcalamity;

import net.xuwu.myriadcalamity.client.BattleMusicRules;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks the encounter score restart rules without loading any Minecraft classes.
 *
 * The duet fight is the case that broke: the pair shares one health pool and one phase and
 * trades places as it dashes, so the score must survive the nearest dancer changing.
 */
public final class BattleMusicRulesTest {
    private static int checks;

    private static void check(boolean condition,String message) {
        checks++;
        if(!condition) throw new AssertionError(message);
    }

    /** Faithful mirror of the state ClientEvents keeps for one encounter track. */
    private static final class DancerScore {
        boolean sameLevel=true,hasTrack,active,wasYangJian;
        int phase=-1,starts;
        final List<Integer> startedPhases=new ArrayList<>();

        void tick(int wantedPhase) {
            if(BattleMusicRules.restartDancerTrack(sameLevel,hasTrack,wasYangJian,active,phase,wantedPhase)) {
                hasTrack=true; active=true; wasYangJian=false; phase=wantedPhase;
                starts++; startedPhases.add(wantedPhase);
            }
        }

        void trackEnds() { active=false; }
        void leavesRange() { hasTrack=false; active=false; wasYangJian=false; phase=-1; }
    }

    /** Faithful mirror of the Yang Jian entrance hand-off in ClientEvents. */
    private static final class YangJianScore {
        boolean sameLevel=true,active,wasYangJian;
        int index=-1,lastBoss=-1,starts;
        final List<Integer> played=new ArrayList<>();

        void tick(boolean preparing,int bossId) {
            boolean bossMatches=wasYangJian && lastBoss==bossId;
            boolean introFinished=bossMatches && index==0 && (!preparing || !active);
            if(BattleMusicRules.restartYangJianTrack(sameLevel,bossMatches,active,introFinished)) {
                index=bossMatches && index==0 && introFinished ? 1 : (preparing ? 0 : 1);
                wasYangJian=true; lastBoss=bossId; active=true; starts++; played.add(index);
            }
        }

        void trackEnds() { active=false; }
    }

    private static void dancerLadderKeepsOneStartPerPhase() {
        DancerScore score=new DancerScore();
        // The nearest dancer changes every few ticks for the whole two-dancer fight; that is
        // exactly the input that used to re-key the score.
        int[] thresholds={1,1,1,1,2,2,2,2,3,3,3,3,4,4,4,4};
        for(int step=0;step<thresholds.length;step++) {
            int wantedPhase=thresholds[step];
            for(int tick=0;tick<60;tick++) score.tick(wantedPhase);
        }
        check(score.starts==4,"A four phase duet starts the score once per phase, got "+score.starts);
        check(score.startedPhases.equals(List.of(1,2,3,4)),"Phases start in order: "+score.startedPhases);
        check(score.phase==4,"The score settles on the final phase");

        // Walking away and coming back is the only other restart during one encounter.
        score.leavesRange();
        check(score.starts==4,"Leaving the encounter range stops the score without starting another");
        score.tick(4);
        check(score.starts==5 && score.phase==4,"Returning to the same phase restarts that phase only");
    }

    private static void finishedTrackLoopsAtTheSamePhase() {
        DancerScore score=new DancerScore();
        score.tick(2);
        check(score.starts==1,"The first tick starts the phase track");
        for(int tick=0;tick<200;tick++) score.tick(2);
        check(score.starts==1,"An active track is never restarted mid-phase");
        score.trackEnds();
        score.tick(2);
        check(score.starts==2,"A finished track restarts at the same phase, giving the loop");
        for(int tick=0;tick<50;tick++) {
            score.trackEnds();
            score.tick(2);
        }
        check(score.starts==52,"Repeated endings keep looping one start at a time");
    }

    private static void phaseAndLevelChangesReKey() {
        for(int from=1;from<=4;from++) {
            for(int to=1;to<=4;to++) {
                boolean expected=from!=to;
                check(BattleMusicRules.restartDancerTrack(true,true,false,true,from,to)==expected,
                    "Phase "+from+" to "+to+" re-keys only when the phase actually changes");
            }
        }
        check(BattleMusicRules.restartDancerTrack(false,true,false,true,2,2),"A new level re-keys the score");
        check(BattleMusicRules.restartDancerTrack(true,false,false,false,1,1),"The first observation starts the score");
        check(BattleMusicRules.restartDancerTrack(true,true,true,true,3,3),"Handing over from Yang Jian re-keys the score");
        check(!BattleMusicRules.restartDancerTrack(true,true,false,true,3,3),"A healthy duet track keeps playing");
    }

    private static void rulesAreStateless() {
        for(int phase=1;phase<=4;phase++) {
            boolean first=BattleMusicRules.restartDancerTrack(true,true,false,true,phase,phase);
            for(int repeat=0;repeat<25;repeat++) {
                check(BattleMusicRules.restartDancerTrack(true,true,false,true,phase,phase)==first,
                    "The rule has no hidden state at phase "+phase);
            }
            boolean yangFirst=BattleMusicRules.restartYangJianTrack(true,true,true,false);
            for(int repeat=0;repeat<25;repeat++) {
                check(BattleMusicRules.restartYangJianTrack(true,true,true,false)==yangFirst,
                    "The Yang Jian rule has no hidden state");
            }
        }
    }

    private static void yangJianEntranceHandsOffToTheMainTheme() {
        YangJianScore score=new YangJianScore();
        for(int tick=0;tick<80;tick++) score.tick(true,1);
        check(score.starts==1 && score.played.equals(List.of(0)),"The arena entrance plays the intro cut once");
        for(int tick=0;tick<600;tick++) score.tick(false,1);
        check(score.starts==2 && score.played.equals(List.of(0,1)),"The visible boss hands the intro over to the main theme once");
        check(score.index==1,"The main theme stays selected");

        YangJianScore direct=new YangJianScore();
        for(int tick=0;tick<400;tick++) direct.tick(false,1);
        check(direct.starts==1 && direct.played.equals(List.of(1)),"A direct summon plays the main theme without the intro cut");

        YangJianScore replaced=new YangJianScore();
        for(int tick=0;tick<100;tick++) replaced.tick(false,1);
        for(int tick=0;tick<100;tick++) replaced.tick(false,2);
        check(replaced.starts==2,"A different Yang Jian entity restarts the arena score exactly once");

        YangJianScore looped=new YangJianScore();
        looped.tick(false,1);
        looped.trackEnds();
        looped.tick(false,1);
        check(looped.starts==2 && looped.played.equals(List.of(1,1)),"A finished Yang Jian track restarts the main theme");
        check(!BattleMusicRules.restartYangJianTrack(true,true,true,false),"An active Yang Jian track keeps playing");
    }

    public static void main(String[] args) {
        dancerLadderKeepsOneStartPerPhase();
        finishedTrackLoopsAtTheSamePhase();
        phaseAndLevelChangesReKey();
        rulesAreStateless();
        yangJianEntranceHandsOffToTheMainTheme();
        System.out.println("Battle music rules: "+checks+" checks passed");
    }
}
