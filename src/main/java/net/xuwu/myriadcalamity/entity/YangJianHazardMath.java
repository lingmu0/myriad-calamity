package net.xuwu.myriadcalamity.entity;

/** Shared limits and geometry for the actual P3 hazard simulation. */
public final class YangJianHazardMath {
    public static final int MAX_HAZARDS=48, DAMAGE_INTERVAL=12;
    /** Normal beam sweep turn; clone passes use the same arc in a shorter window. */
    public static final double TRACK_TURN=Math.toRadians(.7), SWEEP_TURN=Math.toRadians(2), FAST_SWEEP_TURN=Math.toRadians(4.0625);
    public static boolean active(double age,int warning,int duration) { return age>=warning && age<warning+duration; }
    public static double impactRadius(int kind,double radius) {
        return Math.clamp(radius,kind==1?.08:.3,kind==1?1.5:8);
    }
    public static YangJianEffects.Direction turn(YangJianEffects.Direction a,YangJianEffects.Direction b,double limit) {
        double al=Math.sqrt(a.x()*a.x()+a.y()*a.y()+a.z()*a.z()),bl=Math.sqrt(b.x()*b.x()+b.y()*b.y()+b.z()*b.z());
        if(al<1E-9 || bl<1E-9 || !Double.isFinite(al+bl))return a;
        double x=a.x()/al,y=a.y()/al,z=a.z()/al,dx=b.x()/bl,dy=b.y()/bl,dz=b.z()/bl;
        double angle=Math.acos(Math.clamp(x*dx+y*dy+z*dz,-1,1));
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
