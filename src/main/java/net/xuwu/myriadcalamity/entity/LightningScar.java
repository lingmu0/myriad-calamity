package net.xuwu.myriadcalamity.entity;

/**
 * The footprint of a P2 lightning scar: the area the triggering move actually covered, in the
 * same shapes the attack telegraph uses (sector, circle, lane). Kept free of Minecraft types so
 * the damage area can be checked without launching the game.
 */
public final class LightningScar {
    public static final int SECTOR=1,CIRCLE=2,LANE=3;
    /** A scar only bites this close to its own ground plane, so an ordinary jump clears it. */
    public static final double GROUND_BAND=.55;
    /** A scar with this lifetime never expires on its own; the caster clears it. */
    public static final int PERSISTENT=0;
    /** Every pillar in a scar stands this tall, matching the P3 red-thunder field. */
    public static final double PILLAR_HEIGHT=14;
    /** Pillars per scar are capped so a large footprint stays affordable to draw. */
    public static final int MAX_PILLARS=6;

    private LightningScar() {}

    /**
     * True when a standing body overlaps the scar.
     *
     * @param shape        {@link #SECTOR}, {@link #CIRCLE} or {@link #LANE}
     * @param anchorX/Z    sector centre, or the lane's first end
     * @param headingX/Z   the sector's mid direction (from the anchor toward the strike)
     * @param centerX/Z    the circle's centre, or the lane's second end
     * @param radius       sector/circle reach, or the lane's half width
     * @param arcDegrees   the sector's total opening; ignored by the other shapes
     */
    public static boolean covers(int shape,double px,double pz,double feetY,double headY,double groundY,
                                 double anchorX,double anchorZ,double headingX,double headingZ,
                                 double centerX,double centerZ,double radius,double arcDegrees,double victimRadius) {
        if(!finite(px,pz,feetY,headY,groundY,anchorX,anchorZ,headingX,headingZ,centerX,centerZ,radius,arcDegrees,victimRadius))
            return false;
        if(!(radius>0) || victimRadius<0)return false;
        if(headY<groundY || feetY>groundY+GROUND_BAND)return false;
        double reach=radius+victimRadius;
        return switch(shape) {
            case CIRCLE -> squared(px-centerX,pz-centerZ)<=reach*reach;
            case LANE -> segmentSquared(px,pz,anchorX,anchorZ,centerX,centerZ)<=reach*reach;
            // An exact angular test, unlike the attack's sampled arc: a field that persists for a
            // whole phase must not have the sampling gaps a single weapon pass is allowed to have.
            default -> withinSector(px-anchorX,pz-anchorZ,Math.atan2(headingZ,headingX),radius,arcDegrees,victimRadius);
        };
    }

    private static boolean withinSector(double px,double pz,double heading,double radius,double arcDegrees,double victimRadius) {
        double distance=Math.hypot(px,pz);
        if(distance<=victimRadius)return true;
        if(distance>radius+victimRadius)return false;
        // The body radius widens the opening as the target closes on the pivot.
        double slack=Math.asin(Math.clamp(victimRadius/distance,0,1));
        double delta=Math.abs(normalize(Math.atan2(pz,px)-heading));
        return delta<=Math.toRadians(arcDegrees)*.5+slack;
    }

    private static double normalize(double radians) {
        double value=radians%(Math.PI*2);
        if(value<=-Math.PI)value+=Math.PI*2;
        else if(value>Math.PI)value-=Math.PI*2;
        return value;
    }

    /** The scar's ground outline as a closed polygon, in the same local space as the renderer. */
    public static double[][] outline(int shape,double anchorX,double anchorZ,double headingX,double headingZ,
                                     double centerX,double centerZ,double radius,double arcDegrees) {
        double heading=Math.atan2(headingZ,headingX);
        return switch(shape) {
            case LANE -> new double[][] {
                {anchorX-Math.sin(heading)*radius,anchorZ+Math.cos(heading)*radius},
                {centerX-Math.sin(heading)*radius,centerZ+Math.cos(heading)*radius},
                {centerX+Math.sin(heading)*radius,centerZ+Math.cos(heading)*radius},
                {anchorX+Math.sin(heading)*radius,anchorZ+Math.cos(heading)*radius}};
            case CIRCLE -> ring(centerX,centerZ,radius,0,Math.PI*2,48);
            default -> sector(anchorX,anchorZ,radius,heading,Math.toRadians(arcDegrees),32);
        };
    }

    private static double[][] ring(double x,double z,double radius,double from,double sweep,int steps) {
        double[][] points=new double[steps+1][2];
        for(int i=0;i<=steps;i++) {
            double angle=from+sweep*i/steps;
            points[i][0]=x+Math.cos(angle)*radius;points[i][1]=z+Math.sin(angle)*radius;
        }
        return points;
    }

    private static double[][] sector(double x,double z,double radius,double heading,double arc,int steps) {
        double start=heading-arc*.5;
        double[][] points=new double[steps+2][2];
        points[0][0]=x;points[0][1]=z;
        for(int i=0;i<=steps;i++) {
            double angle=start+arc*i/steps;
            points[i+1][0]=x+Math.cos(angle)*radius;points[i+1][1]=z+Math.sin(angle)*radius;
        }
        return points;
    }

    /** Points inside the footprint that carry a lightning pillar, spread over the whole area. */
    public static double[][] pillars(int shape,double anchorX,double anchorZ,double headingX,double headingZ,
                                     double centerX,double centerZ,double radius,double arcDegrees,int maximum) {
        int count=Math.clamp(maximum,1,12);
        double heading=Math.atan2(headingZ,headingX);
        double[][] points=new double[count][2];
        for(int i=0;i<count;i++) {
            if(shape==LANE) {
                double t=(i+.5)/count;
                points[i][0]=anchorX+(centerX-anchorX)*t;points[i][1]=anchorZ+(centerZ-anchorZ)*t;
                continue;
            }
            // Two staggered rings keep the field readable without stacking pillars on the centre.
            double ring=i%2==0?.48:.82;
            double angle=shape==SECTOR
                ?heading-Math.toRadians(arcDegrees)*.5+Math.toRadians(arcDegrees)*(i+.5)/count
                :heading+i*Math.PI*2/count;
            points[i][0]=centerX+Math.cos(angle)*radius*ring;
            points[i][1]=centerZ+Math.sin(angle)*radius*ring;
        }
        return points;
    }

    private static boolean finite(double... values) {
        for(double value:values) if(!Double.isFinite(value)) return false;
        return true;
    }
    private static double squared(double x,double z) { return x*x+z*z; }
    private static double segmentSquared(double px,double pz,double ax,double az,double bx,double bz) {
        double dx=bx-ax,dz=bz-az,length=dx*dx+dz*dz;
        if(length<1E-9)return squared(px-ax,pz-az);
        double t=Math.clamp(((px-ax)*dx+(pz-az)*dz)/length,0,1);
        return squared(px-(ax+dx*t),pz-(az+dz*t));
    }
}
