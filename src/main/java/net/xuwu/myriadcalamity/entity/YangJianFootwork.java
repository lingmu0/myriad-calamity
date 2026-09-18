package net.xuwu.myriadcalamity.entity;

/** Short grounded advances, independent of the damaging skill state machine. */
public final class YangJianFootwork {
    // The advance starts on the first tick; its off-hand knife leaves at once.
    public static final int WINDUP=0,MOVE_END=8,DURATION=10,COOLDOWN=12;
    public static final int THROW_TICK=0;
    public static final double MAX_DISTANCE=6;
    public static double stoppingDistance(int phase) { return phase==3?8.5:3.2; }
    public static boolean canStart(double distance,int phase,int recovery,int cooldown) {
        return Double.isFinite(distance) && recovery<=0 && cooldown<=0 && distance>stoppingDistance(phase)+.75;
    }
    public static double distance(double targetDistance,int phase) {
        return Double.isFinite(targetDistance)?Math.max(0,Math.min(MAX_DISTANCE,targetDistance-stoppingDistance(phase))):0;
    }
    public static double progress(double age) {
        double t=Math.max(0,Math.min(1,(age-WINDUP)/(MOVE_END-WINDUP)));
        return .5-.5*Math.cos(Math.PI*t);
    }
    private YangJianFootwork() { }
}
