package net.xuwu.myriadcalamity;

import java.util.UUID;
import net.xuwu.myriadcalamity.entity.YangJianEffects;
import net.xuwu.myriadcalamity.entity.YangJianHazardMath;
import net.xuwu.myriadcalamity.entity.YangJianPhaseThree;

/** Safety windows, 3D tracking and edge-of-arena layouts without starting Minecraft. */
public final class YangJianHazardTest {
    private static int checks;
    private static void check(boolean condition,String message) { checks++;if(!condition)throw new AssertionError(message); }
    private static boolean near(double a,double b) { return Math.abs(a-b)<1E-6; }
    public static void main(String[] args) {
        check(!YangJianHazardMath.active(-1,20,32),"Unpublished/future attacks do not hit");
        check(!YangJianHazardMath.active(19.999,20,32),"The entire warning interval is harmless");
        check(YangJianHazardMath.active(20,20,32) && YangJianHazardMath.active(51,20,32),"The advertised beam has its complete active window");
        check(!YangJianHazardMath.active(52,20,32),"An expired beam cannot leave a damaging trace");
        check(YangJianHazardMath.SWEEP_DIRECTION>0,"The world-space laser rake advances clockwise");
        check(near(YangJianHazardMath.sweepOffset(23.9,24,32,3),0),"Charge holds the sweep's initial direction");
        check(near(YangJianHazardMath.sweepOffset(40,24,32,3),Math.toRadians(65)),"The body midpoint starts the clone exactly halfway through its clockwise arc");
        check(near(YangJianHazardMath.sweepOffset(40.5,24,32,3),Math.toRadians(65+2.03125)),"Head and beam sample the same clockwise sub-tick sweep angle");
        check(near(YangJianHazardMath.sweepOffset(100,24,32,3),YangJianHazardMath.FAST_SWEEP_TURN*31),"Finished sweep never extrapolates beyond its last damaging frame");
        check(YangJianHazardMath.COMBO_SWEEP_TICKS==16 && near(YangJianHazardMath.sweepTurn(4)*16,Math.toRadians(64)),"Air-combo sweep keeps its arc but completes in half the previous time");
        check(YangJianHazardMath.tracksWhileWarning(0),"A fixed eye shot keeps tracking its target while the eye charges");
        check(!YangJianHazardMath.tracksWhileWarning(1) && !YangJianHazardMath.tracksWhileWarning(3)
            && !YangJianHazardMath.tracksWhileWarning(4),"Sweeps keep the cast heading through their charge");
        check(!YangJianHazardMath.tracksWhileWarning(2),"The self-turning tracking beam is not re-aimed from the boss");
        check(YangJianHazardMath.RED_THUNDER_HEIGHT>=14,"The red-thunder pillar stays a full-height landmark when it lands");
        var forward=new YangJianEffects.Direction(1,0,0);
        var target=new YangJianEffects.Direction(0,1,1).normalized();
        var turned=YangJianHazardMath.turn(forward,target,YangJianHazardMath.TRACK_TURN);
        check(near(turned.dot(turned),1),"3D tracking preserves beam direction length");
        check(near(Math.acos(forward.dot(turned)),Math.toRadians(.7)),"Diagonal tracking cannot exceed the angular dodge budget");
        check(turned.y()>0 && turned.z()>0,"Elevated and sideways targets both influence tracking");
        var almost=new YangJianEffects.Direction(1,.0001,0).normalized();
        check(near(YangJianHazardMath.turn(forward,almost,YangJianHazardMath.TRACK_TURN).dot(almost),1),"Tracking does not overshoot a nearby aim direction");
        var reverse=YangJianHazardMath.turn(forward,new YangJianEffects.Direction(-1,0,0),YangJianHazardMath.TRACK_TURN);
        check(near(reverse.x(),1),"Running through the beam origin cannot cause an instant 180-degree hit");
        check(near(YangJianPhaseThree.enteringHealth(1,600,.4),240),"The final phase cannot start on one hit point after two guard phases");
        check(near(YangJianPhaseThree.enteringHealth(450,600,.4),450),"Entering P3 preserves health above its configured floor");
        check(near(YangJianPhaseThree.enteringHealth(1,600,0),1),"Setting the configurable floor to zero disables the refill");
        check(near(YangJianPhaseThree.enteringHealth(900,600,.4),600),"Transition health cannot exceed maximum");
        check(near(YangJianPhaseThree.enteringHealth(Float.NaN,600,.4),240),"Malformed health does not poison phase arithmetic");
        for(double x:new double[]{0,10,21,-21})for(double z:new double[]{0,15,-15})for(int wave=0;wave<3;wave++) {
            var points=YangJianPhaseThree.pattern(x,z,1.1,-.7,wave,19,99,22,2.2);
            check(points.size()>=5 && points.size()<=9,"Dense waves remain bounded while retaining several threats");
            for(int i=0;i<points.size();i++) {
                var p=points.get(i);
                check(Math.hypot(p.x(),p.z())+2.2<=22,"Every complete warning circle remains inside the platform");
                for(int j=0;j<i;j++)check(Math.hypot(p.x()-points.get(j).x(),p.z()-points.get(j).z())>=5.05-1E-6,"Clamping at the boundary preserves escape gaps between impact circles");
            }
        }
        var current=YangJianPhaseThree.pattern(0,0,100,0,0,0,1,22,2.1).get(0);
        var predicted=YangJianPhaseThree.pattern(0,0,100,0,1,0,1,22,2.1).get(0);
        check(near(current.x(),0) && near(predicted.x(),4.5),"Only the second wave predicts movement, and its lead remains bounded");
        var windows=new YangJianEffects.DamageWindow();UUID owner=UUID.randomUUID(),player=UUID.randomUUID();
        check(windows.claim(owner,player,100),"The first overlapping threat can hit");
        for(int tick=100;tick<112;tick++)check(!windows.claim(owner,player,tick),"Other sword, thunder and beam entities cannot multiply damage in the shared window");
        check(windows.claim(owner,player,112),"An ongoing beam may hit again after the shared interval");
        check(windows.claim(owner,UUID.randomUUID(),112),"One player's dodge budget does not make another player invulnerable");
        System.out.println("Yang Jian P3: "+checks+" hazard/phase checks passed");
    }
}
