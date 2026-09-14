package net.xuwu.myriadcalamity;

import net.xuwu.myriadcalamity.entity.YangJianSkill;
import net.xuwu.myriadcalamity.entity.YangJianFootwork;

/** Authored combat rules and collision geometry; does not bootstrap or launch Minecraft. */
public final class YangJianCombatTest {
    private static int checks;
    private static void check(boolean condition,String message) { checks++;if(!condition)throw new AssertionError(message); }
    private static boolean near(double a,double b) { return Math.abs(a-b)<1E-5; }
    public static void main(String[] args) {
        check(YangJianSkill.COMBO.steps()==4,"The normal chain contains all four authored attacks");
        check(YangJianSkill.FOUR_COMBO.steps()==4,"The quick chain still ends with its fourth heavy strike");
        check(YangJianSkill.SIX_COMBO.steps()==6,"The high-threat chain contains six separate damage windows");
        check(YangJianSkill.FOUR_COMBO.stepHit(2)<YangJianSkill.COMBO.stepHit(2),"Quick four cuts reach their third hit sooner");
        check(YangJianSkill.SIX_COMBO.stepWindup(2)>YangJianSkill.SIX_COMBO.stepWindup(1)*2,"The third six-chain strike intentionally delays its hit");
        check(YangJianSkill.SIX_COMBO.stepWindup(5)>=16,"The heavy finisher has a readable preparation window");
        for(YangJianSkill skill:YangJianSkill.values()) {
            check(skill.phase()==(skill.action()>=23?3:skill.action()>=13?2:1),"Every action belongs to its authored phase");
            check(YangJianSkill.forAction(skill.action())==skill,"Synchronized IDs map back to their authored timing");
            if(skill.meleeCombo() || skill==YangJianSkill.COORDINATED) {
                for(int step=0;step<skill.steps();step++) {
                    check(skill.stepAt(skill.stepStart(step))==step,"The next warning begins with the actual next step");
                    check(skill.stepHit(step)>skill.stepStart(step),"Each strike receives its own warning first");
                    if(step+1<skill.steps()) {
                        check(skill.stepStart(step+1)>skill.stepHit(step)+skill.stepActive(step)-1,"Attack and next warning never share a stale plan");
                        check(skill.stepAt(skill.stepStart(step+1)-1)==step,"A future step cannot fabricate an early warning");
                    }
                }
                check(skill.duration()-skill.stepHit(skill.steps()-1)-skill.stepActive(skill.steps()-1)>=14,"Every chain leaves a real punishment window");
            }
        }
        check(YangJianSkill.forAction(0)==null && YangJianSkill.forAction(10)==null,"Idle and phase-clear states are not attack pool entries");
        check(YangJianSkill.COMBO.weight(3,false,false,false,1,0,0)>0,"Close targets enable melee");
        check(YangJianSkill.COMBO.weight(18,false,false,false,1,0,0)==0,"Melee cannot be selected against distant targets");
        check(YangJianSkill.THROW.weight(18,false,false,false,1,0,0)>YangJianSkill.THROW.weight(8,false,false,false,1,0,0),"Distance increases ranged pressure");
        check(YangJianSkill.THROW.weight(2,false,false,false,1,0,0)==0,"Point-blank throws are excluded");
        check(YangJianSkill.THRUST.weight(9,true,false,false,1,0,0)>YangJianSkill.THRUST.weight(9,false,false,false,1,0,0),"Using an item attracts a punish thrust");
        check(YangJianSkill.THRUST.weight(9,false,true,false,1,0,0)>YangJianSkill.THRUST.weight(9,false,false,false,1,0,0),"Roar mark increases pursuit priority");
        check(YangJianSkill.SUMMON_HOUND.weight(9,false,false,true,1,0,0)==0,"A surviving hound prevents duplicate summons");
        check(YangJianSkill.COORDINATED.weight(6,false,false,false,1,0,0)==0,"Coordination requires the partner to be alive");
        check(YangJianSkill.COORDINATED.weight(6,false,false,true,1,0,0)>0,"An available hound enables the authored combo");
        check(YangJianSkill.SIX_COMBO.weight(3,false,false,false,.4,0,0)>YangJianSkill.SIX_COMBO.weight(3,false,false,false,1,0,0),"Lower remaining vitality increases the six-chain threat");
        for(YangJianSkill skill:YangJianSkill.values())check(skill.weight(7,true,true,true,.5,0,1)==0,"A cooling-down skill cannot enter the roll");
        check(YangJianSkill.COMBO.weight(3,false,false,false,1,1,0)<YangJianSkill.COMBO.weight(3,false,false,false,1,0,0),"The previous skill is penalized rather than repeated blindly");
        check(YangJianSkill.GUARD.weight(3,false,false,false,1,0,0)==0,"Guard only comes from an actual incoming attack");
        check(YangJianSkill.COUNTER.weight(3,false,false,false,1,0,0)==0,"Counter cannot bypass its successful-block requirement");
        check(YangJianSkill.sweptArc(3,0,0,-.7,.7,4,.3),"Swept blade hits a victim between the endpoint poses");
        check(!YangJianSkill.sweptArc(-3,0,0,-.7,.7,4,.3),"A forward slash cannot hit a victim behind the boss");
        check(!YangJianSkill.sweptArc(6,0,0,-.7,.7,4,.3),"Finite weapon reach admits distance dodges");
        check(YangJianSkill.sweptArc(-3,0,0,-Math.PI,Math.PI,4,.3),"The authored full spin also hits behind");
        check(YangJianSkill.sweptArc(0,3,Math.PI/2,-.7,.7,4,.3),"Sweeps rotate with the announced facing");
        check(!YangJianSkill.sweptArc(3,0,Math.PI/2,-.7,.7,4,.3),"A rotated sweep leaves its opposite side free");
        check(near(YangJianSkill.guardDamage(10000,180,false),36),"Huge damage cannot skip an entire guard phase in one hit");
        check(YangJianSkill.guardDamage(9,180,true)<YangJianSkill.guardDamage(9,180,false),"Successful blocks reduce guard loss but are not free");
        check(YangJianSkill.guardDamage(9,180,true)>0,"Repeated blocked blows still make progress");
        check(YangJianSkill.guardDamage(Float.NaN,180,false)==0,"Malformed damage cannot poison synchronized guard");
        check(YangJianSkill.guardDamage(Float.POSITIVE_INFINITY,180,false)==0,"Infinite damage never enters phase arithmetic");
        check(YangJianSkill.guardDamage(-1,180,false)==0,"Negative damage cannot refill guard");
        int p2Count=0,p3Count=0;
        for(YangJianSkill skill:YangJianSkill.values()) {
            if(skill.phase()==2)p2Count++;
            if(skill.phase()==3)p3Count++;
            for(int distance=1;distance<=25;distance+=2) {
                for(int phase=1;phase<=3;phase++)if(phase!=skill.phase())
                    check(skill.weightForPhase(phase,1,distance,true,true,true,.3,0,0,true)==0,"Other phases cannot enter this phase's weighted pool");
                check(skill.weightForPhase(skill.phase(),1,distance,true,true,true,.3,0,5,true)==0,"Cooldowns also exclude every P2 action");
            }
            if(skill.phaseTwoCombo())for(int step=0;step<skill.steps();step++) {
                check(skill.stepAt(skill.stepStart(step))==step,"P2 segment warnings start at their own authored boundary");
                check(skill.stepHit(step)-skill.stepStart(step)==skill.stepWindup(step),"P2 segment impact is exactly its visible windup later");
                if(step+1<skill.steps())check(skill.stepStart(step+1)>skill.stepHit(step)+skill.stepActive(step)-1,"P2 combo segments never reuse a previous live plan");
            }
        }
        check(p2Count==10,"All ten specified P2 skills are present, without any P3 entry");
        check(p3Count==10,"P3 includes eye opening and all nine final-phase attacks");
        check(YangJianSkill.THIRD_EYE_OPEN.weightForPhase(3,0,8,false,false,false,.4,0,0,false)==0,"Eye opening belongs exclusively to the guard transition");
        check(YangJianSkill.DIVINE_JUDGEMENT.cooldown()>=1200,"The ultimate has at least a one-minute cooldown");
        check(YangJianSkill.DIVINE_JUDGEMENT.stepWindup(0)>=40,"The ultimate has a two-second or longer introduction");
        check(YangJianSkill.PHASE_THREE_FINAL_RECOVERY>=24,"P3 keeps a short but readable punishment window after its faster skills");
        check(YangJianSkill.AXE_COMBO.weightForPhase(2,0,4,false,false,false,.5,0,0,false)==0,"Axe attacks require the summoned axe stance");
        check(YangJianSkill.AXE_COMBO.weightForPhase(2,1,4,false,false,false,.5,0,0,false)>0,"The axe stance unlocks its distinct combo");
        check(YangJianSkill.AXE_SUMMON.weightForPhase(2,1,8,false,false,false,.5,0,0,false)==0,"An already equipped axe does not repeatedly summon itself");
        check(YangJianSkill.axeFollowup(6)==YangJianSkill.AXE_COMBO && YangJianSkill.axeFollowup(12)==YangJianSkill.AXE_SLAM,
            "A completed axe summon must be followed by an axe action before a weapon swap");
        check(YangJianSkill.AXE_TRAIL_CHARGES==3 && YangJianSkill.consumeAxeTrailCharge(3)==2
                && YangJianSkill.consumeAxeTrailCharge(1)==0 && YangJianSkill.consumeAxeTrailCharge(-1)==0,
            "The summoned axe grants exactly three bounded ground-trail charges");
        check(YangJianSkill.DRAW_SLASH.weightForPhase(2,2,4,false,false,false,.5,0,0,true)>YangJianSkill.DRAW_SLASH.weightForPhase(2,2,4,false,false,false,.5,0,0,false),"Draw slash punishes sustained melee pressure");
        check(YangJianSkill.FLYING_SWORDS.weightForPhase(2,2,18,false,false,false,.5,0,0,false)>YangJianSkill.FLYING_SWORDS.weightForPhase(2,2,8,false,false,false,.5,0,0,false),"Distant players invite ranged sword pressure");
        check(YangJianSkill.INVISIBLE_DASH.cooldown()>=200,"Concealment has a long cooldown and cannot be spammed");
        check(YangJianSkill.DELAYED_COMBO.stepWindup(2)>YangJianSkill.DELAYED_COMBO.stepWindup(1)*3,"Fast-fast-heavy changes tempo markedly");
        check(YangJianSkill.WHIP_SPIN.stepWindup(0)>=28,"The returning sweep gives time to prepare a jump");
        check(YangJianSkill.sweepTouches(129,130.8,129),"A grounded player intersects the full-height whip sweep");
        check(YangJianSkill.sweepTouches(131.5,133.3,129),"The returning sweep also reaches players above the old low band");
        check(!YangJianSkill.sweepTouches(132.7,134.5,129),"A high jump clears the full-height sweep");
        check(!YangJianSkill.sweepTouches(126,127.8,129),"The sweep does not hit players beneath the arena floor");
        check(YangJianSkill.divineSweepTouches(129,130.8,129),"The P3 divine sweep reaches a standing player's hand-height body band");
        check(!YangJianSkill.divineSweepTouches(131.8,133.6,129),"A sufficiently high jump clears the raised P3 divine sweep");
        check(!YangJianSkill.divineSweepTouches(127,128.7,129),"The raised P3 divine sweep does not strike below the platform");
        check(YangJianSkill.GUARD_REGEN_DELAY==20*60*2,"An exhausted defense bar stays empty for two real minutes");
        check(YangJianSkill.phaseThresholdReached(480,640,1) && !YangJianSkill.phaseThresholdReached(481,640,1),"P2 begins at 75 percent health");
        check(YangJianSkill.phaseThresholdReached(320,640,2) && !YangJianSkill.phaseThresholdReached(321,640,2),"P3 begins at 50 percent health");
        check(!YangJianSkill.phaseThresholdReached(0,640,3) && !YangJianSkill.phaseThresholdReached(Float.NaN,640,1),"Only P1/P2 finite health thresholds can transition");
        check(YangJianSkill.sweepRelative(Math.PI,0)>YangJianSkill.sweepRelative(Math.PI,1),"Rotating skills use the restored mirrored-model direction");
        check(YangJianSkill.canChain(1,2) && !YangJianSkill.canChain(2,2),"A two-move sequence stops after the second move");
        check(YangJianSkill.canChain(2,3) && !YangJianSkill.canChain(3,3),"A three-move sequence always stops after the third move");
        check(!YangJianSkill.canChain(3,100),"A malformed limit cannot create an endless combo");
        check(YangJianSkill.PHASE_TWO_FINAL_RECOVERY>=28 && YangJianSkill.PHASE_TWO_LINK_RECOVERY>=8,"Both links and complete sequences provide dodge/punish windows");
        check(YangJianSkill.healthThresholdAction(1)==12 && YangJianSkill.healthThresholdAction(2)==23 && YangJianSkill.healthThresholdAction(3)==0,"The two health thresholds unlock P2 then P3; P3 has no later transition");
        check(YangJianSkill.restoredPhase(0)==1 && YangJianSkill.restoredPhase(3)==3 && YangJianSkill.restoredPhase(99)==3,"Legacy tags default to P1; valid P3 persists and malformed phases are bounded");
        check(YangJianSkill.restoredAction(1,true,0,0,0)==10,"An already completed legacy P1 trial stays completed");
        check(YangJianSkill.restoredAction(1,false,0,0,0)==0,"An exhausted P1 guard resumes as an exposed idle state");
        check(YangJianSkill.restoredAction(1,false,0,31,0)==12,"A partially saved transition resumes without an attack warning");
        check(YangJianSkill.restoredAction(2,false,190,0,0)==0,"An interrupted P2 attack loads idle, not a stale warning");
        check(YangJianSkill.restoredAction(2,false,0,0,0)==0,"An exhausted P2 guard resumes as an exposed idle state");
        check(YangJianSkill.restoredAction(2,false,0,0,30)==23,"An unfinished saved P2 transition resumes eye opening");
        check(YangJianSkill.restoredAction(2,true,0,0,0)==10,"Already completed P2 saves do not force the player back into battle");
        check(YangJianSkill.restoredAction(2,false,0,31,0)==23,"Saving during eye opening preserves its transition");
        check(YangJianSkill.restoredAction(3,false,0,0,0)==0,"A zero P3 guard is normal and cannot complete the encounter");
        check(near(YangJianSkill.protectedHealth(-100000),1) && near(YangJianSkill.protectedHealth(Float.NaN),1),"Both guard phases preserve positive health through oversized damage");
        check(YangJianFootwork.canStart(15,1,0,0),"A distant player enables the rapid advancing step");
        for(int phase=1;phase<=3;phase++) {
            check(!YangJianFootwork.canStart(20,phase,1,0),"Footwork never steals a phase's punishment/recovery window");
            check(!YangJianFootwork.canStart(20,phase,0,1),"The boss cannot chain steps without a pause");
            check(!YangJianFootwork.canStart(YangJianFootwork.stoppingDistance(phase)+.5,phase,0,0),"Small range differences cannot trigger jittering steps");
            check(YangJianFootwork.distance(50,phase)<=6,"The approach is a short step, not a full-arena dash");
            check(near(YangJianFootwork.distance(YangJianFootwork.stoppingDistance(phase)-1,phase),0),"An approaching player cannot make the boss step through them");
        }
        check(near(YangJianFootwork.distance(5,1),1.8),"A near step stops outside the player's body");
        check(YangJianFootwork.stoppingDistance(3)>YangJianFootwork.stoppingDistance(1),"P3 still retains its ranged combat distance");
        check(!YangJianFootwork.canStart(Double.NaN,1,0,0) && near(YangJianFootwork.distance(Double.POSITIVE_INFINITY,1),0),"Malformed positions cannot start an unbounded step");
        check(YangJianFootwork.WINDUP==0 && YangJianFootwork.THROW_TICK==0,"The step and its knife release have no windup");
        check(near(YangJianFootwork.progress(0),0) && near(YangJianFootwork.progress(8),1),"Movement begins on the first step tick");
        double previous=0,total=0,peak=0;
        for(int age=0;age<10;age++) {
            double progress=YangJianFootwork.progress(age+1),travel=6*(progress-previous);
            check(travel>=0 && progress<=1,"Step movement never reverses or overshoots its planned endpoint");
            if(age>=8)check(near(travel,0),"Only the settle ticks keep the step's feet planted");
            total+=travel;peak=Math.max(peak,travel);previous=progress;
        }
        check(near(total,6) && peak>=1.1 && peak<=1.2,"Eight travel ticks cover the full rapid step with a bounded peak speed");
        System.out.println("Yang Jian P1/P2/P3: "+checks+" combat checks passed");
    }
}
