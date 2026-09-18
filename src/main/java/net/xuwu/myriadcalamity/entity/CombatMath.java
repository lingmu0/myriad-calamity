package net.xuwu.myriadcalamity.entity;

/** Geometry and encounter rules shared by the fight and dependency-free verification. */
public final class CombatMath {
    public static final double ARENA_RADIUS=14.5, FIGHT_BOUNDARY_RADIUS=16.5,
        DUET_RADIUS=6.2*1.75, SLAM_RADIUS=8.5, SLAM_WARNING_RADIUS=2.0, WAVE_HEIGHT=0.4;
    public static final int DASH_TRAVEL_TICKS=12, DASH_ACTIVE_TICKS=28;
    public static final int BARRAGE_PASSES=4, BARRAGE_PASS_TICKS=15;
    public static final int BARRAGE_WARNING_TICKS=7, BARRAGE_DASH_TICKS=7, BARRAGE_RECOVERY_TICKS=8;
    public static final int BARRAGE_ACTIVE_TICKS=BARRAGE_PASSES*BARRAGE_PASS_TICKS+BARRAGE_RECOVERY_TICKS;
    public static final double BARRAGE_LANE_RADIUS=1.5, BARRAGE_SAFE_RADIUS=2.4;
    public static final int TRANSITION_TICKS=60, SEQUENTIAL_GAP_TICKS=4;
    public static final float PAIRED_HIT_CAP=0.10F, SOLO_HIT_CAP=0.05F;
    public record Point(double x,double z) {}
    public record Lane(Point start,Point end) {}
    private CombatMath() {}
    /** Separate counters per move keep the targeting role independent of the fixed attack sequence. */
    public static boolean targetingFollower(int moveCount) { return (moveCount&1)!=0; }
    public static Point clampDisk(double x,double z,double radius) {
        double length=Math.hypot(x,z);
        return length>radius?new Point(x*radius/length,z*radius/length):new Point(x,z);
    }
    /** The complete chord through an aim point; both endpoints stay inside the unobstructed arena. */
    public static Lane laneThrough(double px,double pz,double dx,double dz,double radius) {
        double length=Math.hypot(dx,dz);
        if(length<1E-9) { dx=1; dz=0; } else { dx/=length; dz/=length; }
        Point p=clampDisk(px,pz,radius-0.01);
        double projection=p.x*dx+p.z*dz;
        double perpendicularSquared=Math.max(0,p.x*p.x+p.z*p.z-projection*projection);
        double extent=Math.sqrt(Math.max(0,radius*radius-perpendicularSquared));
        double a=-projection-extent,b=-projection+extent;
        return new Lane(new Point(p.x+a*dx,p.z+a*dz),new Point(p.x+b*dx,p.z+b*dz));
    }
    /** A second impact five blocks away; rotate inward when an edge would collapse the separation. */
    public static Point nearbyImpact(double px,double pz,double angle,double radius) {
        Point target=clampDisk(px,pz,radius);
        for(int i=0;i<8;i++) {
            double direction=angle+i*Math.PI/4;
            Point candidate=new Point(target.x+Math.cos(direction)*5,target.z+Math.sin(direction)*5);
            if(Math.hypot(candidate.x,candidate.z)<=radius)return candidate;
        }
        return clampDisk(target.x*0.5,target.z*0.5,radius);
    }
    /** Tangent chords leave a small unmarked gap in the complete opening warning pattern. */
    public static Lane barrageLane(double safeX,double safeZ,double rotation,int pass,boolean follower) {
        double angle=rotation+pass*0.83;
        double dx=Math.cos(angle),dz=Math.sin(angle),side=follower?-1:1;
        double px=safeX-dz*4.5*side,pz=safeZ+dx*4.5*side;
        return laneThrough(px,pz,dx*side,dz*side,ARENA_RADIUS);
    }
    /** Only the moving body deals barrage damage; staging, spent lanes and recovery are harmless. */
    public static boolean barrageCharging(int active) {
        if(active<0 || active>=BARRAGE_PASSES*BARRAGE_PASS_TICKS)return false;
        int local=active%BARRAGE_PASS_TICKS;
        return local>=BARRAGE_WARNING_TICKS && local<BARRAGE_WARNING_TICKS+BARRAGE_DASH_TICKS;
    }
    public static int attackCooldown(int phase,boolean solo) {
        if(solo)return 14;
        return switch(phase) { case 3 -> 5; case 2 -> 7; default -> 14; };
    }
    public static boolean outsideFightBoundary(double x,double z) {
        return x*x+z*z>FIGHT_BOUNDARY_RADIUS*FIGHT_BOUNDARY_RADIUS;
    }
    public static float perHitCap(float maximum,boolean solo) {
        return Math.max(1,maximum)*(solo?SOLO_HIT_CAP:PAIRED_HIT_CAP);
    }
    public static float phaseFloor(float maximum,int phase,boolean solo) {
        if(solo)return 0;
        return maximum*switch(phase) { case 1 -> 0.66F; case 2 -> 0.33F; default -> 0.20F; };
    }
    /** Applied at the final health setter, after armor, magic and mod damage hooks. */
    public static float minimumHealthAfterHit(float health,float maximum,int phase,boolean solo) {
        return Math.min(health,Math.max(phaseFloor(maximum,phase,solo),health-perHitCap(maximum,solo)));
    }
    public static float protectedHealth(float requested,float floor) {
        return Float.isNaN(requested)?floor:Math.max(floor,requested);
    }
    public static boolean phaseThresholdReached(float health,float maximum,int phase) {
        return phase<4 && health<=phaseFloor(maximum,phase,false);
    }
    public static int restoredTransitionTicks(int savedTicks) {
        return Math.max(0,Math.min(TRANSITION_TICKS,savedTicks));
    }
    public static boolean touchesGroundWave(double feetY,double headY,double groundY) {
        return feetY<=groundY+WAVE_HEIGHT && headY>=groundY;
    }
    public static int phase(float shared, float maximum, boolean solo) {
        if (solo) return 4;
        float ratio = shared / Math.max(1, maximum);
        return ratio <= 0.33F ? 3 : ratio <= 0.66F ? 2 : 1;
    }
    public static java.util.UUID finaleCasualty(java.util.UUID lastStruck,java.util.UUID hit,java.util.UUID partner) {
        return partner.equals(lastStruck)?partner:hit;
    }
    public static boolean isFinale(float health,float maximum) { return health <= maximum*0.2F; }
    /** Every phase telegraphs for less time than before, keeping the original ordering. */
    public static int windup(int phase) { return switch (phase) { case 2 -> 18; case 3 -> 15; case 4 -> 35; default -> 28; }; }
    public static double segmentDistanceSquared(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az;
        double length = dx * dx + dz * dz;
        double t = length < 1E-9 ? 0 : Math.max(0,Math.min(1,((px-ax)*dx+(pz-az)*dz)/length));
        double x = px - (ax+t*dx), z = pz - (az+t*dz);
        return x*x+z*z;
    }
    public static boolean inRing(double distance, double radius, double halfWidth) {
        return Math.abs(distance-radius) <= halfWidth;
    }
}
