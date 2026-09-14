package net.xuwu.myriadcalamity.entity;

/** Shared flight, axe growth and jumpable ground-wave timeline for the first guard break. */
public final class YangJianTransition {
    public static final int ASCEND_END=24,SUMMON_START=24,SUMMON_END=40,SWEEP_START=40,SWEEP_END=64;
    public static final int IMPACT=72,WAVE_START=96,WAVE_END=126,DURATION=144;
    public static final double HEIGHT=8,RADIUS=22,LOW_HEIGHT=.45,WAVE_HALF_WIDTH=.65;

    private YangJianTransition() {}
    private static double smooth(double t) { t=Math.clamp(t,0,1);return t*t*(3-2*t); }
    public static double height(double age) {
        if(!Double.isFinite(age) || age<0 || age>=IMPACT)return 0;
        if(age<ASCEND_END)return HEIGHT*smooth(age/ASCEND_END);
        return age<=SWEEP_END?HEIGHT:HEIGHT*(1-smooth((age-SWEEP_END)/(IMPACT-SWEEP_END)));
    }
    public static double weaponScale(double age) {
        if(!Double.isFinite(age) || age<SUMMON_START)return 0;
        if(age<SUMMON_END)return 3*smooth((age-SUMMON_START)/(SUMMON_END-SUMMON_START));
        return age<=WAVE_END?3:3-2*smooth((age-WAVE_END)/(DURATION-WAVE_END));
    }
    public static double waveRadius(double age) {
        if(!Double.isFinite(age) || age<WAVE_START || age>WAVE_END)return -1;
        return RADIUS*(age-WAVE_START)/(WAVE_END-WAVE_START);
    }
    public static boolean inGroundBand(double feet,double head,double ground) {
        return Double.isFinite(feet) && Double.isFinite(head) && Double.isFinite(ground)
            && head>=ground && feet<=ground+LOW_HEIGHT;
    }
    public static boolean impactTouches(double distance,double victimRadius,double feet,double head,double ground) {
        return Double.isFinite(distance) && distance>=0 && Double.isFinite(victimRadius) && victimRadius>=0
            && distance<=RADIUS+victimRadius && inGroundBand(feet,head,ground);
    }
    public static boolean waveTouches(int age,double previousDistance,double distance,double victimRadius,
                                     double feet,double head,double ground) {
        if(!impactTouches(distance,victimRadius,feet,head,ground) || !Double.isFinite(previousDistance) || previousDistance<0)return false;
        double radius=waveRadius(age);
        if(radius<0)return false;
        double previousRadius=Math.max(0,waveRadius(age-1));
        double before=previousDistance-previousRadius,after=distance-radius;
        double padding=WAVE_HALF_WIDTH+victimRadius;
        return Math.min(before,after)<=padding && Math.max(before,after)>=-padding;
    }
}
