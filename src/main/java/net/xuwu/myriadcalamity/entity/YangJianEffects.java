package net.xuwu.myriadcalamity.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Geometry and damage budgets shared by the P2 effects; independent of Minecraft bootstrap. */
public final class YangJianEffects {
    public static final int MAX_SWORDS=5,ORBIT_TICKS=24,AIM_TICKS=12;
    public static final int TRACKING_TICKS=8,FLIGHT_TICKS=40,TRAIL_HIT_INTERVAL=12;
    public static final double SWORD_SPEED=1.7,MAX_TURN_RADIANS=Math.toRadians(2.25),TRAIL_RADIUS=1.2;

    public static int swordCount(int requested) { return Math.clamp(requested,0,MAX_SWORDS); }
    /** Every queued sword uses the same local sequence: orbit, warn, release. */
    public static int releaseTick() { return ORBIT_TICKS+AIM_TICKS; }

    public record Direction(double x,double y,double z) {
        public double dot(Direction other) { return x*other.x+y*other.y+z*other.z; }
        public Direction normalized() {
            double length=Math.sqrt(x*x+y*y+z*z);
            return length<1E-9 || !Double.isFinite(length)?new Direction(0,0,0):new Direction(x/length,y/length,z/length);
        }
    }
    /** Rodrigues rotation takes a bounded angular step, preserving speed when multiplied by the caller. */
    public static Direction turnSword(Direction source,Direction destination) {
        Direction current=source.normalized(),desired=destination.normalized();
        if(desired.dot(desired)<1E-9)return current;
        if(current.dot(current)<1E-9)return desired;
        double angle=Math.acos(Math.clamp(current.dot(desired),-1,1));
        if(angle<MAX_TURN_RADIANS)return desired;
        Direction axis=new Direction(current.y*desired.z-current.z*desired.y,current.z*desired.x-current.x*desired.z,
            current.x*desired.y-current.y*desired.x).normalized();
        if(axis.dot(axis)<1E-9)return current;
        double sin=Math.sin(MAX_TURN_RADIANS),cos=Math.cos(MAX_TURN_RADIANS);
        return new Direction(current.x*cos+(axis.y*current.z-axis.z*current.y)*sin,
            current.y*cos+(axis.z*current.x-axis.x*current.z)*sin,
            current.z*cos+(axis.x*current.y-axis.y*current.x)*sin).normalized();
    }

    public static boolean groundContact(double px,double py,double pz,double sx,double sy,double sz,
                                        double ex,double ey,double ez,double radius,double padding) {
        if(!Double.isFinite(px+py+pz+sx+sy+sz+ex+ey+ez+radius+padding) || radius<0 || padding<0)return false;
        double dx=ex-sx,dz=ez-sz,lengthSquared=dx*dx+dz*dz;
        double t=lengthSquared<1E-9?0:Math.clamp(((px-sx)*dx+(pz-sz)*dz)/lengthSquared,0,1);
        double height=py-(sy+(ey-sy)*t),x=px-(sx+dx*t),z=pz-(sz+dz*t);
        return height>=-.15 && height<=.55 && x*x+z*z<=(radius+padding)*(radius+padding);
    }

    /** No adjacent route segments may multiply the damage budget of the same caster. */
    public static final class DamageWindow {
        private record Key(UUID owner,UUID target) {}
        private final Map<Key,Long> nextHit=new HashMap<>();
        public boolean claim(UUID owner,UUID target,long tick) {
            nextHit.values().removeIf(expiry->expiry<=tick);
            Key key=new Key(owner,target);
            if(nextHit.containsKey(key))return false;
            nextHit.put(key,tick+TRAIL_HIT_INTERVAL);return true;
        }
        public int pendingEntries() { return nextHit.size(); }
    }
    private YangJianEffects() {}
}
