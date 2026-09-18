package net.xuwu.myriadcalamity.entity;

import net.minecraft.world.phys.Vec3;

/** Pure geometry and timing rules for Yang Jian's automatic remote guard. */
public final class YangJianDefense {
    public static final double AUTO_GUARD_DISTANCE = 4.0;
    public static final double AUTO_GUARD_DISTANCE_SQR = AUTO_GUARD_DISTANCE * AUTO_GUARD_DISTANCE;
    public static final int STUN_TICKS = 20;

    private YangJianDefense() { }

    /**
     * Returns whether an attack has a supported origin at least four blocks away.
     * Either the direct projectile or its actual living source may establish the range.
     */
    public static boolean isRemote(double directDistanceSqr, double actualDistanceSqr, boolean supportedSource) {
        if(!supportedSource) return false;
        boolean directValid=Double.isFinite(directDistanceSqr),actualValid=Double.isFinite(actualDistanceSqr);
        if(!directValid && !actualValid) return false;
        return directValid && directDistanceSqr >= AUTO_GUARD_DISTANCE_SQR
            || actualValid && actualDistanceSqr >= AUTO_GUARD_DISTANCE_SQR;
    }

    /** Reverse an incoming projectile velocity while keeping its speed and height component. */
    public static Vec3 reflected(Vec3 incoming) {
        if(incoming == null || !Double.isFinite(incoming.x) || !Double.isFinite(incoming.y) || !Double.isFinite(incoming.z)
                || incoming.lengthSqr() < 1.0E-8) return Vec3.ZERO;
        return incoming.scale(-1.0);
    }
}
