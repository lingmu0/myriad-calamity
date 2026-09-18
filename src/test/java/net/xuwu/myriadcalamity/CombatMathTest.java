package net.xuwu.myriadcalamity;

import java.util.UUID;
import net.xuwu.myriadcalamity.client.AnimationTrack;
import net.xuwu.myriadcalamity.world.TheatreLayout;
import net.xuwu.myriadcalamity.world.DanceEncounters;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.xuwu.myriadcalamity.entity.CombatMath;

public final class CombatMathTest {
    private static int checks;
    private static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }
    private static boolean near(double a,double b) { return Math.abs(a-b)<1E-6; }
    public static void main(String[] args) {
        check(CombatMath.phase(480,480,false)==1,"Full shared pool opens in phase one");
        check(CombatMath.phase(317,480,false)==1,"Phase two must not start early");
        check(CombatMath.phase(316,480,false)==2,"Crossing 66% accelerates both dancers");
        check(CombatMath.phase(159,480,false)==2,"Phase three must not start early");
        check(CombatMath.phase(158,480,false)==3,"Crossing 33% offsets the choreography");
        check(CombatMath.phase(96,480,true)==4,"A survivor remains in phase four");
        check(CombatMath.phase(480,480,true)==4,"Healing a survivor must not respawn its partner");
        check(!CombatMath.isFinale(96.01F,480),"Finale must not start above 20%");
        check(CombatMath.isFinale(96,480),"Exactly 20% starts the finale");
        check(CombatMath.isFinale(1,480),"A low legacy pool is recognized as finale health");
        UUID gold=new UUID(0,1),silver=new UUID(0,2);
        check(CombatMath.finaleCasualty(gold,gold,silver).equals(gold),"Last struck gold dancer must die");
        check(CombatMath.finaleCasualty(silver,silver,gold).equals(silver),"Last struck silver dancer must die");
        check(CombatMath.finaleCasualty(silver,gold,silver).equals(silver),"Environmental damage preserves player's last target");
        check(CombatMath.finaleCasualty(null,gold,silver).equals(gold),"No player history falls back to current hitbox");
        check(CombatMath.finaleCasualty(new UUID(0,3),gold,silver).equals(gold),"A stale UUID cannot choose another encounter");
        check(CombatMath.windup(1)>CombatMath.windup(2) && CombatMath.windup(2)>CombatMath.windup(3),"Windups accelerate");
        check(CombatMath.windup(2)==18 && CombatMath.windup(3)==15,"Phase two and three windups stay on the shortened timings");
        check(CombatMath.windup(1)==28 && CombatMath.windup(4)==35,
            "Every phase, including the solo finale, telegraphs for less time than before");
        check(CombatMath.windup(4)>CombatMath.windup(1),"Solo attacks are slower");
        check(CombatMath.DASH_TRAVEL_TICKS==12 && CombatMath.DASH_ACTIVE_TICKS==28,"Dashes use the shortened travel and recovery window");
        check(near(CombatMath.segmentDistanceSquared(5,0,0,0,10,0),0),"Dash detects a player between tick positions");
        check(near(CombatMath.segmentDistanceSquared(5,2,0,0,10,0),4),"Dash respects lateral dodge distance");
        check(near(CombatMath.segmentDistanceSquared(-2,0,0,0,10,0),4),"Dash ray must not extend behind start");
        check(near(CombatMath.segmentDistanceSquared(12,0,0,0,10,0),4),"Dash ray must not extend beyond end");
        check(near(CombatMath.segmentDistanceSquared(4,5,1,1,1,1),25),"Stationary dash has no divide by zero");
        check(near(CombatMath.segmentDistanceSquared(5,5,0,0,10,10),0),"Diagonal dash geometry");
        check(CombatMath.inRing(5,5,0.75),"Wave hits its edge");
        check(!CombatMath.inRing(0,5,0.75),"A passed wave leaves a safe interior");
        check(!CombatMath.inRing(6,5,0.75),"Outside the wave remains safe");
        check(CombatMath.inRing(6,0,CombatMath.DUET_RADIUS),"Expanded duet reaches six blocks");
        check(near(CombatMath.DUET_RADIUS,10.85),"Duet radius is exactly 75 percent larger than 6.2");
        check(CombatMath.inRing(10.8,0,CombatMath.DUET_RADIUS),"Duet reaches its expanded edge");
        check(!CombatMath.inRing(10.9,0,CombatMath.DUET_RADIUS),"Duet does not exceed its announced boundary");
        check(!CombatMath.targetingFollower(0) && CombatMath.targetingFollower(1)
            && !CombatMath.targetingFollower(2),"Successive uses alternate which dancer locks onto the player");
        check(CombatMath.touchesGroundWave(70,71.8,70),"Standing on the floor is vulnerable to the wave");
        check(!CombatMath.touchesGroundWave(70.42,72.22,70),"A normal first jump tick clears the wave");
        check(!CombatMath.touchesGroundWave(71.25,73.05,70),"Jump apex clears the ground wave");
        check(!CombatMath.touchesGroundWave(68,69.8,70),"The shockwave cannot hit an entity beneath its floor");
        check(CombatMath.BARRAGE_ACTIVE_TICKS==68,"Barrage animation and server timing agree");
        check(CombatMath.FIGHT_BOUNDARY_RADIUS>CombatMath.ARENA_RADIUS,"Fight boundary contains the playable arena");
        check(CombatMath.outsideFightBoundary(17,0) && !CombatMath.outsideFightBoundary(16,0),"Boundary blocks an escape beyond the theatre");
        check(near(CombatMath.SLAM_WARNING_RADIUS,2.0) && CombatMath.SLAM_WARNING_RADIUS<CombatMath.SLAM_RADIUS,"Slam telegraph only marks its landing impact");
        check(CombatMath.attackCooldown(3,false)<CombatMath.attackCooldown(1,false) && CombatMath.attackCooldown(4,true)>CombatMath.attackCooldown(3,false),"Attack cooldowns accelerate by phase while solo remains readable");
        check(CombatMath.BARRAGE_WARNING_TICKS+CombatMath.BARRAGE_DASH_TICKS<CombatMath.BARRAGE_PASS_TICKS,
            "Every barrage pass leaves a pause after its warned charge");
        boolean bounded=true,longDashes=true,separatedSlams=true,safePocket=true,fastEnough=true;
        for(int angle=0;angle<48;angle++) {
            double a=angle*Math.PI/24,px=Math.cos(a)*13.5,pz=Math.sin(a)*13.5;
            CombatMath.Lane dash=CombatMath.laneThrough(px,pz,px,pz,CombatMath.ARENA_RADIUS);
            longDashes &= near(Math.hypot(dash.end().x()-dash.start().x(),dash.end().z()-dash.start().z()),29);
            CombatMath.Point adjacent=CombatMath.nearbyImpact(px,pz,a+Math.PI/2,13.5);
            separatedSlams &= near(Math.hypot(adjacent.x()-px,adjacent.z()-pz),5)
                && Math.hypot(adjacent.x(),adjacent.z())<=13.5+1E-6;
            for(int pass=0;pass<CombatMath.BARRAGE_PASSES;pass++)for(boolean follower:new boolean[]{false,true}) {
                double sx=Math.cos(a)*4,sz=Math.sin(a)*4;
                CombatMath.Lane lane=CombatMath.barrageLane(sx,sz,a,pass,follower);
                bounded &= Math.hypot(lane.start().x(),lane.start().z())<=CombatMath.ARENA_RADIUS+1E-6
                    && Math.hypot(lane.end().x(),lane.end().z())<=CombatMath.ARENA_RADIUS+1E-6;
                double distance=Math.sqrt(CombatMath.segmentDistanceSquared(sx,sz,lane.start().x(),lane.start().z(),lane.end().x(),lane.end().z()));
                safePocket &= distance>CombatMath.BARRAGE_SAFE_RADIUS+CombatMath.BARRAGE_LANE_RADIUS+0.3+0.2;
                fastEnough &= Math.hypot(lane.end().x()-lane.start().x(),lane.end().z()-lane.start().z())
                    <=4.2*CombatMath.BARRAGE_DASH_TICKS;
            }
        }
        check(longDashes,"Locked dashes traverse the full 29-block fighting diameter");
        check(separatedSlams,"Secondary slam remains five blocks from the target even at arena edges");
        check(bounded,"All barrage endpoints stay inside the column-free combat disk");
        check(safePocket,"Every announced barrage lane leaves an unmarked gap, including player width and staging tolerance");
        check(fastEnough,"Each barrage charge can traverse its full announced lane in the animation window");
        check(!CombatMath.barrageCharging(-1) && !CombatMath.barrageCharging(0) && !CombatMath.barrageCharging(6),"Barrage windup and staging cannot hurt");
        check(CombatMath.barrageCharging(7) && CombatMath.barrageCharging(13) && !CombatMath.barrageCharging(14),"Barrage body becomes dangerous exactly when the first dash begins");
        check(CombatMath.barrageCharging(52) && CombatMath.barrageCharging(58) && !CombatMath.barrageCharging(59),"Fourth moving body still deals its announced hit");
        check(!CombatMath.barrageCharging(60) && !CombatMath.barrageCharging(67) && !CombatMath.barrageCharging(68),
            "Final recovery contains neither dangerous residual lanes nor an outer barrier");
        int planned=0;
        for(int pass=0;pass<CombatMath.BARRAGE_PASSES;pass++)for(boolean follower:new boolean[]{false,true}) {
            CombatMath.Lane lane=CombatMath.barrageLane(0,4,0,pass,follower);
            if(lane.start()!=null && lane.end()!=null)planned++;
        }
        check(planned==8,"Both dancers announce exactly eight locked lanes before the first charge");
        check(near(CombatMath.perHitCap(480,false),48),"Paired damage is capped at ten percent of maximum health per hit");
        check(near(CombatMath.perHitCap(480,true),24),"Solo damage is capped at five percent of maximum health per hit");
        check(near(CombatMath.minimumHealthAfterHit(480,480,1,false),432),"A full-health boss cannot be one-shot by enormous damage");
        check(near(CombatMath.minimumHealthAfterHit(330,480,1,false),480*0.66F),"Phase one clamps precisely at 66 percent");
        check(near(CombatMath.minimumHealthAfterHit(180,480,2,false),480*0.33F),"Phase two clamps precisely at 33 percent");
        check(near(CombatMath.minimumHealthAfterHit(110,480,3,false),96),"Phase three clamps precisely at 20 percent");
        check(near(CombatMath.minimumHealthAfterHit(96,480,4,true),72),"The solo survivor retains at least 72 health after its first enormous hit");
        check(near(CombatMath.minimumHealthAfterHit(24,480,4,true),0),"The solo survivor can eventually die normally");
        float capFloor=CombatMath.minimumHealthAfterHit(330,480,1,false);
        check(near(CombatMath.protectedHealth(-99999,capFloor),480*0.66F),"Final setter clamps armor-bypassing and post-hook damage at the next phase");
        check(near(CombatMath.protectedHealth(Float.NaN,capFloor),capFloor),"A nonfinite damage calculation cannot corrupt the shared health pool");
        check(near(CombatMath.protectedHealth(328,capFloor),328),"Ordinary small damage keeps its actual armor-adjusted result");
        check(near(CombatMath.minimumHealthAfterHit(50,480,1,false),50),"An old low-health save is never accidentally healed by a cap");
        check(CombatMath.phaseThresholdReached(480*0.66F,480,1),"Exact 66 percent starts transition immediately in the damage call");
        check(!CombatMath.phaseThresholdReached(317,480,1),"Transition does not start before its protected floor");
        check(CombatMath.phaseThresholdReached(96,480,3) && !CombatMath.phaseThresholdReached(0,480,4),
            "The last player target is retired only at the phase-three floor, with no extra solo phase");
        CompoundTag phaseSave=new CompoundTag();phaseSave.putInt("TransitionTicks",37);
        check(CombatMath.restoredTransitionTicks(phaseSave.getInt("TransitionTicks"))==37,
            "Reload preserves all 37 remaining transition immunity ticks without converting seconds");
        check(CombatMath.restoredTransitionTicks(-4)==0 && CombatMath.restoredTransitionTicks(999)==60,
            "Malformed transition durations cannot give negative or permanent protection");
        CombatMath.Lane degenerate=CombatMath.laneThrough(0,0,0,0,14.5);
        check(near(degenerate.start().x(),-14.5) && near(degenerate.end().x(),14.5),"A coincident aim still produces a valid dash");
        check(TheatreLayout.material(0,0)==4,"Altar is exactly at the centre");
        check(TheatreLayout.floor(-4,0) && TheatreLayout.floor(4,0),"Both summon positions have floor");
        check(TheatreLayout.SEALS.length==8,"Eight resonance seals");
        java.util.Set<String> seals=new java.util.HashSet<>();
        for(int[] seal:TheatreLayout.SEALS){check(TheatreLayout.floor(seal[0],seal[1]),"Seal lies on the floor");seals.add(seal[0]+","+seal[1]);}
        check(seals.size()==8,"Resonance seal coordinates are unique");
        boolean clear=true;
        for(int x=-14;x<=14;x++)for(int z=-14;z<=14;z++)if(x*x+z*z<=14*14)clear &= TheatreLayout.floor(x,z)&&!TheatreLayout.pillar(x,z);
        check(clear,"Playable inner disk is continuous and free of columns");
        check(!TheatreLayout.floor(21,0),"Structure never extends past its bounding box");
        float[][] turns={{0,0,0,0},{2,0,720,0}};
        check(near(AnimationTrack.sample(turns,1,0)[1],360),"Duet interpolation preserves complete turns");
        check(near(AnimationTrack.sample(turns,9,0)[1],720),"Animation holds the last pose");
        check(near(AnimationTrack.sample(turns,-1,0)[1],0),"Animation holds the first pose before start");
        check(near(AnimationTrack.sample(new float[0][],0,1)[0],1),"Unanimated bones retain unit scale");
        BlockPos altar=new BlockPos(4,70,8),other=new BlockPos(104,70,8);
        DanceEncounters ledger=new DanceEncounters();
        check(!ledger.active(altar),"Fresh altar accepts a key");
        ledger.begin(altar,gold,silver);
        check(ledger.active(altar)&&!ledger.active(other),"Lock applies only to this theatre");
        ledger.release(altar,new UUID(1,1));
        check(ledger.active(altar),"An unrelated death cannot unlock the altar");
        ledger.release(altar,gold);
        check(ledger.active(altar),"First dancer's exit must not unlock a running solo fight");
        DanceEncounters restored=DanceEncounters.load(ledger.save(new CompoundTag(),null),null);
        check(restored.active(altar),"Solo encounter lock survives save/load");
        restored.release(altar,silver);
        check(!restored.active(altar),"Final survivor's death unlocks the next paid encounter");
        restored.release(altar,silver);
        check(!restored.active(altar),"Duplicate death/removal notifications are safe");
        System.out.println("Combat rules: "+checks+" checks passed.");
    }
}