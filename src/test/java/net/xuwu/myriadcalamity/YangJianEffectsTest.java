package net.xuwu.myriadcalamity;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import net.xuwu.myriadcalamity.entity.LightningScar;
import net.xuwu.myriadcalamity.entity.YangJianEffects;

/** Authoritative route geometry and bounded volley/damage budgets, without launching Minecraft. */
public final class YangJianEffectsTest {
    private static int checks;
    private static void check(boolean result,String message) { checks++;if(!result)throw new AssertionError(message); }
    private static boolean ground(double x,double y,double z) {
        return YangJianEffects.groundContact(x,y,z,0,.035,0,10,.035,0,1.2,.3);
    }
    public static void main(String[] args) {
        check(YangJianEffects.swordCount(-1)==0,"Negative summon requests do not spawn swords");
        check(YangJianEffects.swordCount(999)==5,"No volley exceeds five swords");
        check(YangJianEffects.swordCount(3)==3,"Smaller requested volleys are respected");
        for(int i=0;i<5;i++)
            check(YangJianEffects.releaseTick()==YangJianEffects.ORBIT_TICKS+YangJianEffects.AIM_TICKS,
                "Removing targeting rays preserves every P2 sword's aim and release timing");
        check(YangJianEffects.myriadSwordCount(-1,0)==0,"A negative P3 wave cannot spawn swords");
        check(YangJianEffects.myriadSwordCount(YangJianEffects.MYRIAD_WAVES,0)==0,"P3 cannot spawn waves past the end of the skill");
        check(YangJianEffects.myriadSwordCount(0,YangJianEffects.MAX_MYRIAD_ACTIVE)==0,"A full P3 stream cannot exceed its active entity cap");
        check(YangJianEffects.myriadSwordCount(0,Integer.MAX_VALUE)==0,"An overfull stream cannot overflow its spawn budget");
        check(YangJianEffects.myriadSwordCount(0,YangJianEffects.MAX_MYRIAD_ACTIVE-1)==1,"The last available P3 slot admits just one blade");
        List<Integer> expiry=new ArrayList<>();
        int total=0,peak=0;
        for(int tick=0;tick<=180;tick++) {
            final int now=tick;
            expiry.removeIf(end->end<=now);
            if(tick>=YangJianEffects.MYRIAD_START_TICK && tick<=YangJianEffects.MYRIAD_END_TICK
                    && (tick-YangJianEffects.MYRIAD_START_TICK)%YangJianEffects.MYRIAD_WAVE_INTERVAL==0) {
                int wave=(tick-YangJianEffects.MYRIAD_START_TICK)/YangJianEffects.MYRIAD_WAVE_INTERVAL;
                int count=YangJianEffects.myriadSwordCount(wave,expiry.size());
                check(count==3,"Normal P3 cadence fits the active cap even if every sword misses");
                for(int index=0;index<count;index++)expiry.add(tick+YangJianEffects.myriadReleaseTick(index)+YangJianEffects.FLIGHT_TICKS);
                total+=count;peak=Math.max(peak,expiry.size());
            }
        }
        check(total==69,"The continuous P3 invocation releases sixty-nine swords");
        check(peak<=YangJianEffects.MAX_MYRIAD_ACTIVE,"Missed projectiles remain bounded throughout the stream");
        check(expiry.isEmpty(),"All missed swords expire after the invocation finishes");
        for(int wave=0;wave<YangJianEffects.MYRIAD_WAVES;wave++) {
            for(int slot=0;slot<YangJianEffects.MYRIAD_SWORDS_PER_WAVE;slot++) {
                var start=YangJianEffects.myriadOrbit(0,wave,slot);
                var later=YangJianEffects.myriadOrbit(6,wave,slot);
                double radius=Math.hypot(later.x(),later.z());
                check(radius>=2.4 && radius<=3.2,"P3 swords orbit visibly outside the body within a bounded radius");
                check(later.y()>-.1 && later.y()<1.5,"Stacked rings remain close to the caster's upper body");
                check(Math.hypot(later.x()-start.x(),later.z()-start.z())>2,"Preparing blades visibly revolve instead of freezing");
            }
        }
        check(YangJianEffects.TRACKING_TICKS*YangJianEffects.MAX_TURN_RADIANS<=Math.toRadians(18)+1E-9,"Total steering is limited to eighteen degrees");
        check(YangJianEffects.TRACKING_TICKS<YangJianEffects.FLIGHT_TICKS/2,"Most of flight is committed straight travel");
        YangJianEffects.Direction direction=new YangJianEffects.Direction(1,0,0),target=new YangJianEffects.Direction(0,1,0);
        for(int i=0;i<YangJianEffects.TRACKING_TICKS;i++) {
            YangJianEffects.Direction next=YangJianEffects.turnSword(direction,target);
            check(Math.acos(Math.max(-1,Math.min(1,direction.dot(next))))<=YangJianEffects.MAX_TURN_RADIANS+1E-7,"Actual homing helper obeys its angular speed limit");
            check(Math.abs(next.dot(next)-1)<1E-8,"Steering preserves normalized flight speed");
            check(next.dot(target)>direction.dot(target),"Homing makes measurable progress toward its target");
            direction=next;
        }
        check(Math.acos(direction.x())<=Math.toRadians(18)+1E-7,"Eight actual steering steps cannot exceed the whole tracking budget");
        check(YangJianEffects.turnSword(new YangJianEffects.Direction(1,0,0),new YangJianEffects.Direction(-1,0,0)).x()==1,"A target directly behind cannot cause an instantaneous U-turn");
        YangJianEffects.Direction nearby=new YangJianEffects.Direction(Math.cos(.01),Math.sin(.01),0);
        check(YangJianEffects.turnSword(new YangJianEffects.Direction(1,0,0),nearby).dot(nearby)>.999999,"A target inside one angular step is reached without overshoot");
        check(ground(5,0,0),"A standing player's feet contact the live path");
        check(ground(5,0,1.49),"The player's body radius participates in boundary contact");
        check(!ground(5,0,1.51),"Outside the displayed lane plus player radius is safe");
        check(!ground(5,.7,0),"Jumping clears the lightning route");
        check(!ground(5,2,0),"Airborne players are not struck by ground lightning");
        check(!ground(5,-1,0),"The route does not strike a lower floor");
        check(ground(-1,0,0),"Route endpoints use the same rounded collision radius");
        check(!ground(-2,0,0),"Routes do not extend infinitely behind the dash");
        check(!ground(12,0,0),"Routes do not extend beyond the actual dash endpoint");
        check(!ground(Double.NaN,0,0),"Nonfinite input cannot damage a player");
        check(YangJianEffects.groundContact(0,0,0,0,.035,0,0,.035,0,1.2,.3),"A zero-length geometry probe is finite");
        check(YangJianEffects.groundContact(5,.5,0,0,.035,0,10,1.035,0,1.2,.3),"Contact height follows a gently sloped route");
        check(!YangJianEffects.groundContact(5,1.3,0,0,.035,0,10,1.035,0,1.2,.3),"Jumping is also possible above sloped ground");
        YangJianEffects.DamageWindow hits=new YangJianEffects.DamageWindow();
        UUID boss=UUID.randomUUID(),player=UUID.randomUUID(),other=UUID.randomUUID();
        check(hits.claim(boss,player,100),"The first ground hit spends its damage budget");
        for(int tick=100;tick<112;tick++)check(!hits.claim(boss,player,tick),"Adjacent route segments cannot stack damage during the shared cooldown");
        check(hits.claim(boss,other,105),"Different players maintain independent damage windows");
        check(hits.claim(boss,player,112),"Ground contact can deal a new hit after twelve ticks");
        check(hits.claim(boss,player,500),"Expired entries are reclaimed on the next contact");
        check(hits.pendingEntries()==1,"Old player UUID entries do not accumulate in the ledger");
        scarFootprints();
        System.out.println("Yang Jian P2/P3 effects: "+checks+" checks passed");
    }

    /** The scar covers the triggering move's whole area, and only while standing in it. */
    private static void scarFootprints() {
        check(LightningScar.PERSISTENT==0,"A persistent scar waits for the next axe summon instead of a timer");
        check(LightningScar.PILLAR_HEIGHT>=12,"A scar pillar is a full-height landmark like the P3 red thunder");
        // Circle footprint: the slam's own impact radius, not a thin route.
        check(covered(LightningScar.CIRCLE,6,0,0),"A circle scar reaches its own radius");
        check(!covered(LightningScar.CIRCLE,11,0,0),"A circle scar stops outside its radius plus the body");
        check(covered(LightningScar.CIRCLE,6,6,0),"The circle is a disk, not a line toward the target");
        check(!covered(LightningScar.CIRCLE,0,0,GROUND_BAND+.05),"Jumping clears a circle scar");
        // Sector footprint: the whip arc keeps its opening and its reach.
        check(covered(LightningScar.SECTOR,8,0,0),"A sector scar covers its mid direction");
        check(covered(LightningScar.SECTOR,9.5*Math.cos(Math.toRadians(80)),9.5*Math.sin(Math.toRadians(80)),0),
            "A sector scar reaches its own outer edge of the arc");
        check(!covered(LightningScar.SECTOR,8*Math.cos(Math.toRadians(140)),8*Math.sin(Math.toRadians(140)),0),
            "A sector scar does not wrap past its arc");
        check(!covered(LightningScar.SECTOR,20,0,0),"A sector scar does not extend past its radius");
        // Lane footprint: the whole travelled thrust route, not one dot per tick.
        check(covered(LightningScar.LANE,9,0,0),"A lane scar covers the middle of the travelled route");
        check(covered(LightningScar.LANE,18,0,0),"A lane scar covers the far end of the route");
        check(!covered(LightningScar.LANE,24,0,0),"A lane scar does not extend past the dash end");
        check(!covered(LightningScar.LANE,9,3.5,0),"A lane scar keeps its own half width");
        // Outlines and pillar layouts stay bounded and finite for every shape.
        for(int shape:new int[]{LightningScar.SECTOR,LightningScar.CIRCLE,LightningScar.LANE}) {
            double[][] outline=LightningScar.outline(shape,0,0,10,0,0,0,10,200);
            check(outline.length>=4,"Every scar footprint has a closed outline");
            for(double[] flat:outline)
                check(Double.isFinite(flat[0]) && Double.isFinite(flat[1]),"Outline points stay finite");
            double[][] pillars=LightningScar.pillars(shape,0,0,10,0,0,0,10,200,LightningScar.MAX_PILLARS);
            check(pillars.length==LightningScar.MAX_PILLARS,"Every scar spreads its pillars over the footprint");
            for(double[] flat:pillars) {
                check(Double.isFinite(flat[0]) && Double.isFinite(flat[1]),"Pillar anchors stay finite");
                if(shape==LightningScar.LANE)continue;
                check(Math.hypot(flat[0],flat[1])<=10.001,"Pillars stay inside the footprint radius");
            }
            check(LightningScar.pillars(shape,0,0,10,0,0,0,10,200,99).length==12,"The pillar cap bounds a large footprint");
        }
        check(!LightningScar.covers(LightningScar.CIRCLE,Double.NaN,0,0,1,0,0,0,1,0,0,0,10,0,.3),
            "Nonfinite positions cannot be inside a scar");
        check(!LightningScar.covers(LightningScar.CIRCLE,0,0,GROUND_BAND,1,0,0,0,1,0,0,0,0,0,.3),
            "A zero radius scar covers nothing");
    }

    private static final double GROUND_BAND=LightningScar.GROUND_BAND;
    /**
     * Triggering geometry shared by the checks: a 10 block reach centred on the origin, or a
     * 20 block lane along +X with a 1 block half width.
     */
    private static boolean covered(int shape,double x,double z,double feet) {
        double centerX=shape==LightningScar.LANE?20:0;
        return LightningScar.covers(shape,x,z,feet,feet+1.8,0,
            0,0,1,0,centerX,0,shape==LightningScar.LANE?1:10,180,.3);
    }
}
