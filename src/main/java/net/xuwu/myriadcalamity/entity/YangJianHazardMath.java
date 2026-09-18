package net.xuwu.myriadcalamity.entity;

/** Shared limits and geometry for the actual P3 hazard simulation. */
public final class YangJianHazardMath {
    public static final int MAX_HAZARDS=48, DAMAGE_INTERVAL=12;
    /** The red-thunder pillar holds this full height while it lands and while it persists. */
    public static final double RED_THUNDER_HEIGHT=14;
    /** Normal beam sweep turn; clone passes use the same arc in a shorter window. */
    public static final double TRACK_TURN=Math.toRadians(.7), SWEEP_TURN=Math.toRadians(2), FAST_SWEEP_TURN=Math.toRadians(4.0625);
    public static final int COMBO_SWEEP_TICKS=16;
    /**
     * Sign of the laser rake's angular advance. A beam is drawn in plain world space, where a
     * positive atan2(z,x) rotation is the clockwise turn every rotating attack shares. The
     * mirrored model reaches that same clockwise look with increasing authored yaw instead, which
     * is why the animated spins below are authored upwards. Flip this one value to reverse every
     * laser rake.
     */
    public static final double SWEEP_DIRECTION=1;
    /** Preserve the former 64 degree combo arc while completing it twice as fast. */
    public static double sweepTurn(int mode) { return mode==3?FAST_SWEEP_TURN:mode==4?SWEEP_TURN*2:SWEEP_TURN; }
    public static boolean sweeping(int mode) { return mode==1 || mode==3 || mode==4; }
    /**
     * Fixed shots keep re-aiming while their eye is charging and only lock at release, so a
     * player who keeps moving is still tracked during the warning. Sweeps and the tracking
     * beam own their own turn, and must not be re-aimed from the boss.
     */
    public static boolean tracksWhileWarning(int mode) { return mode==0; }
    public static double sweepOffset(double age,int warning,int duration,int mode) {
        return SWEEP_DIRECTION*sweepTurn(mode)*Math.max(0,Math.min(Math.max(0,duration-1),age-warning));
    }
    public static boolean active(double age,int warning,int duration) { return age>=warning && age<warning+duration; }
    public static double impactRadius(int kind,double radius) {
        return Math.max(kind==1?.08:.3,Math.min(kind==1?1.5:8,radius));
    }
    public static YangJianEffects.Direction turn(YangJianEffects.Direction a,YangJianEffects.Direction b,double limit) {
        double al=Math.sqrt(a.x()*a.x()+a.y()*a.y()+a.z()*a.z()),bl=Math.sqrt(b.x()*b.x()+b.y()*b.y()+b.z()*b.z());
        if(al<1E-9 || bl<1E-9 || !Double.isFinite(al+bl))return a;
        double x=a.x()/al,y=a.y()/al,z=a.z()/al,dx=b.x()/bl,dy=b.y()/bl,dz=b.z()/bl;
        double angle=Math.acos(Math.max(-1,Math.min(1,x*dx+y*dy+z*dz)));
        if(angle<=limit)return new YangJianEffects.Direction(dx,dy,dz);
        double ax=y*dz-z*dy,ay=z*dx-x*dz,az=x*dy-y*dx,n=Math.sqrt(ax*ax+ay*ay+az*az);
        if(n<1E-9)return new YangJianEffects.Direction(x,y,z);
        ax/=n;ay/=n;az/=n;double c=Math.cos(limit),s=Math.sin(limit);
        return new YangJianEffects.Direction(x*c+(ay*z-az*y)*s,y*c+(az*x-ax*z)*s,z*c+(ax*y-ay*x)*s);
    }
    /** Rotated, staggered rings leave corridors between independently warned impact circles. */
    public static CombatMath.Point wavePoint(int index,int count,double rotation,double radius) {
        double angle=rotation+index*Math.PI*2/Math.max(1,count);
        return new CombatMath.Point(Math.cos(angle)*radius,Math.sin(angle)*radius);
    }
    private YangJianHazardMath() {}
}
