package net.xuwu.myriadcalamity;

import java.util.UUID;
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
                "Every queued sword receives the same visible aim window");
        check(YangJianEffects.TRACKING_TICKS*YangJianEffects.MAX_TURN_RADIANS<=Math.toRadians(18)+1E-9,"Total steering is limited to eighteen degrees");
        check(YangJianEffects.TRACKING_TICKS<YangJianEffects.FLIGHT_TICKS/2,"Most of flight is committed straight travel");
        YangJianEffects.Direction direction=new YangJianEffects.Direction(1,0,0),target=new YangJianEffects.Direction(0,1,0);
        for(int i=0;i<YangJianEffects.TRACKING_TICKS;i++) {
            YangJianEffects.Direction next=YangJianEffects.turnSword(direction,target);
            check(Math.acos(Math.clamp(direction.dot(next),-1,1))<=YangJianEffects.MAX_TURN_RADIANS+1E-7,"Actual homing helper obeys its angular speed limit");
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
        System.out.println("Yang Jian P2 effects: "+checks+" checks passed");
    }
}
