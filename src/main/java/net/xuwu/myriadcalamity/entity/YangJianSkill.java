package net.xuwu.myriadcalamity.entity;

/** Authored three-phase timings and selection rules, independent of Minecraft for verification. */
public enum YangJianSkill {
    COMBO(1, 28, new int[]{8,7,10,17}, 3, 3, 14, 4.0F),
    FOUR_COMBO(2, 90, new int[]{7,5,7,16}, 3, 2, 16, 4.4F),
    SIX_COMBO(3, 180, new int[]{7,5,15,5,7,18}, 3, 2, 20, 4.8F),
    THROW(4, 140, new int[]{16}, 34, 0, 16, 1.2F),
    THRUST(5, 65, new int[]{18}, 6, 0, 16, 1.2F),
    SUMMON_HOUND(6, 440, new int[]{24}, 1, 0, 18, 0),
    COORDINATED(7, 150, new int[]{12,16}, 5, 5, 18, 4.2F),
    GUARD(8, 55, new int[]{8}, 0, 0, 8, 0),
    COUNTER(9, 75, new int[]{12}, 5, 0, 18, 1.2F),
    ROAR_MARK(11, 0, new int[]{0}, 0, 0, 0, 0),
    AXE_SUMMON(13,100,new int[]{24},1,0,12,0),
    AXE_SLAM(14,150,new int[]{28},3,0,22,6.4F),
    AXE_COMBO(15,110,new int[]{10,8,11,24},4,3,22,5.7F),
    FLYING_SWORDS(16,150,new int[]{24},1,0,18,1.2F),
    DRAW_SLASH(17,75,new int[]{18},2,0,20,7),
    WHIP_SWEEP(18,105,new int[]{22},8,0,18,11),
    WHIP_SPIN(19,160,new int[]{28},12,0,24,10.5F),
    LIGHTNING_THRUST(20,85,new int[]{18},6,0,20,1.2F),
    INVISIBLE_DASH(21,240,new int[]{30},6,0,22,1.2F),
    DELAYED_COMBO(22,105,new int[]{7,6,24},3,3,22,5.5F),
    THIRD_EYE_OPEN(23,0,new int[]{70},0,0,0,0),
    // P3 is deliberately tighter than P1/P2: shorter windups, shorter cooldowns,
    // and less recovery keep the final phase moving without changing hitbox sizes.
    // The eye beam is a short, narrow, high-impact shot rather than a long
    // sustained ray.  Its player lock is sampled once when the hazard starts.
    EYE_BEAM(24,100,new int[]{10},8,0,14,.18F),
    // Body and clone alternate every half sweep: 3 body passes plus the final
    // half of the third clone pass (32 + 16 tick cadence = 112 active ticks).
    SWEEP_BEAM(25,180,new int[]{24},112,0,22,.65F),
    TRACKING_BEAM(26,165,new int[]{22},50,0,22,.55F),
    MYRIAD_SWORDS(27,220,new int[]{24},90,0,26,2.1F),
    SWORD_RAIN(28,185,new int[]{18},132,0,22,2.2F),
    RED_THUNDER(29,150,new int[]{14},56,0,20,2.8F),
    DIVINE_SWEEP(30,85,new int[]{20},10,0,20,12),
    AERIAL_COMBO(31,340,new int[]{24},126,0,30,4.8F),
    DIVINE_JUDGEMENT(32,1200,new int[]{44},240,0,44,6);

    private final int action, cooldown, active, gap, recovery;
    private final int[] windups;
    private final float radius;
    /** Empty defense bars stay exposed for two real minutes before recharging. */
    public static final int AXE_TRAIL_CHARGES=3,GUARD_REGEN_DELAY=20*60*2;
    /** The ground sword rain falls continuously instead of in four discrete waves. */
    public static final int SWORD_RAIN_FIRST=20,SWORD_RAIN_INTERVAL=4,SWORD_RAIN_PER_DROP=2,SWORD_RAIN_WARNING=20;
    /** How many separate drops the rain releases over its active window. */
    public static int swordRainDrops(int active) {
        return active<SWORD_RAIN_FIRST?0:(active-SWORD_RAIN_FIRST)/SWORD_RAIN_INTERVAL+1;
    }
    /** True on the exact ticks the ground rain releases another pair of blades. */
    public static boolean swordRainDropsAt(int age,int active) {
        return age>=SWORD_RAIN_FIRST && age<=active && (age-SWORD_RAIN_FIRST)%SWORD_RAIN_INTERVAL==0;
    }
    /** A dense sword-rain burst: several close waves instead of one sparse spread. */
    public static final int RAIN_BURST_WAVES=3,RAIN_BURST_SPACING=6,RAIN_BURST_BLADES=8,RAIN_BURST_WARNING_STEP=4;
    /** True on the exact ticks a burst opens another one of its waves. */
    public static boolean rainBurstAt(int age,int start) {
        return age>=start && age<start+RAIN_BURST_WAVES*RAIN_BURST_SPACING
            && (age-start)%RAIN_BURST_SPACING==0;
    }
    public static int rainBurstWave(int age,int start) { return (age-start)/RAIN_BURST_SPACING; }
    /** Each later wave of a burst warns for less time, so the whole burst lands as one downpour. */
    public static int rainBurstWarning(int firstWarning,int wave) {
        return firstWarning-RAIN_BURST_WARNING_STEP*Math.max(0,wave);
    }
    YangJianSkill(int action,int cooldown,int[] windups,int active,int gap,int recovery,float radius) {
        this.action=action;this.cooldown=cooldown;this.windups=windups;this.active=active;
        this.gap=gap;this.recovery=recovery;this.radius=radius;
    }
    public int action() { return action; }
    public int phase() { return action>=23?3:action>=13?2:1; }
    public int weapon() {
        return switch(this) {
            case AXE_SUMMON,AXE_SLAM,AXE_COMBO -> 1;
            case FLYING_SWORDS,DRAW_SLASH,INVISIBLE_DASH,DELAYED_COMBO -> 2;
            case WHIP_SWEEP,WHIP_SPIN -> 3;
            default -> 0;
        };
    }
    public int cooldown() { return cooldown; }
    public int steps() { return windups.length; }
    public int stepWindup(int step) { return windups[Math.max(0,Math.min(windups.length-1,step))]; }
    public int stepStart(int step) {
        int time=0;
        for(int i=0;i<Math.max(0,Math.min(steps(),step));i++)time+=windups[i]+active+gap;
        return time;
    }
    public int stepHit(int step) { return stepStart(step)+stepWindup(step); }
    public int stepActive(int step) { return active; }
    public int stepAt(int age) {
        for(int i=steps()-1;i>=0;i--)if(age>=stepStart(i))return i;
        return 0;
    }
    public int recovery() { return recovery; }
    public int duration() { return stepHit(steps()-1)+active+recovery; }
    public int activeEnd() { return stepHit(steps()-1)+active; }
    public float radius() { return radius; }
    public boolean meleeCombo() { return this==COMBO || this==FOUR_COMBO || this==SIX_COMBO; }
    /** P1 melee strings only keep swinging while the target stays inside this reach. */
    public static final double COMBO_REACH=5.5,COMBO_THROW_RANGE=12;
    /**
     * The follow-up a long P1 string becomes once the target broke away: a closing thrust at
     * medium range, a thrown spear beyond it, and none at all while the target is still close.
     */
    public static YangJianSkill comboFollowup(double distance) {
        if(!Double.isFinite(distance) || distance<=COMBO_REACH)return null;
        return distance>COMBO_THROW_RANGE?THROW:THRUST;
    }
    /** Only the long strings convert; the basic three-hit combo always finishes its beats. */
    public boolean convertsWhenTargetEscapes() { return this==FOUR_COMBO || this==SIX_COMBO; }
    public boolean phaseTwoCombo() { return this==AXE_COMBO || this==DELAYED_COMBO; }
    public static final int PHASE_TWO_LINK_RECOVERY=8,PHASE_TWO_FINAL_RECOVERY=28,PHASE_THREE_FINAL_RECOVERY=28;
    public static boolean canChain(int count,int limit) { return count>=1 && count<Math.max(2,Math.min(3,limit)); }
    public static int restoredPhase(int saved) { return Math.max(1,Math.min(3,saved)); }
    /** The authored phase transition action for a health threshold. */
    public static int healthThresholdAction(int phase) { return phase==1?12:phase==2?23:0; }
    /** Kept as a source-compatible alias for older tests and saved-data tools. */
    public static int guardBreakAction(int phase) { return healthThresholdAction(phase); }
    /** P2 starts at 75% health and P3 starts at 50%; guard depletion alone never changes phase. */
    public static boolean phaseThresholdReached(float health,float maximum,int phase) {
        if(!Float.isFinite(health) || !Float.isFinite(maximum) || maximum<=0 || phase<1 || phase>2)return false;
        float ratio=health/maximum;
        return phase==1?ratio<=.75F:ratio<=.5F;
    }
    public static int restoredAction(int phase,boolean complete,float guard,int transitionTicks,int clearTicks) {
        if(complete)return 10;
        // A saved empty bar is a normal exposed state now; only an in-progress
        // transition resumes its warning/action after a chunk reload.
        if(phase==1 && (transitionTicks>0 || clearTicks>0))return 12;
        return phase==2 && (transitionTicks>0 || clearTicks>0)?23:0;
    }
    /** After the axe is summoned, the next authored action must actually use it. */
    public static YangJianSkill axeFollowup(double distance) {
        return Double.isFinite(distance) && distance<=7?AXE_COMBO:AXE_SLAM;
    }
    /** One later attack spends one of the axe's ground-trail charges. */
    public static int consumeAxeTrailCharge(int charges) {
        int bounded=Math.max(0,Math.min(AXE_TRAIL_CHARGES,charges));
        return bounded==0?0:bounded-1;
    }
    public static float protectedHealth(float requested) { return Float.isNaN(requested)?1:Math.max(1,requested); }
    /** Shared vertical band for the regular and returning whip sweeps. */
    public static boolean sweepTouches(double feet,double head,double ground) {
        return Double.isFinite(feet) && Double.isFinite(head) && Double.isFinite(ground)
            && head>=ground+.3 && feet<=ground+3.6;
    }
    /**
     * P3's divine sweep travels at Yang Jian's hand height instead of along
     * the floor. The band is deliberately narrow enough for a jump to clear,
     * while a standing player still intersects it with their upper body.
     */
    public static final double DIVINE_SWEEP_HEIGHT=2.0,DIVINE_SWEEP_HALF_BAND=.62;
    /** The divine sweep stretches the spear along its own axis instead of trailing a bolt. */
    public static final float DIVINE_SWEEP_WEAPON_LENGTH=3F;
    public static float divineSweepWeaponLength(float age) {
        if(!Float.isFinite(age))return 1F;
        float windup=DIVINE_SWEEP.windups[0],active=DIVINE_SWEEP.active;
        float grow=Math.max(0,Math.min(1,age/Math.max(1,windup)));
        float fade=1F-Math.max(0,Math.min(1,(age-(windup+active+4))/10F));
        return 1F+(DIVINE_SWEEP_WEAPON_LENGTH-1F)*Math.min(grow,fade);
    }
    public static boolean divineSweepTouches(double feet,double head,double ground) {
        if(!Double.isFinite(feet) || !Double.isFinite(head) || !Double.isFinite(ground))return false;
        double lower=ground+DIVINE_SWEEP_HEIGHT-DIVINE_SWEEP_HALF_BAND;
        double upper=ground+DIVINE_SWEEP_HEIGHT+DIVINE_SWEEP_HALF_BAND;
        return head>=lower && feet<=upper;
    }
    /** Relative arc angle used by the server hitbox and client chain renderer.
     *  The chain is drawn under the mirrored model transform, which reverses the authored sweep,
     *  so the visible links travel from -half-arc toward +half-arc: the clockwise turn every
     *  rotating attack shares. The damage arc reads the same value, so hits stay on the links. */
    public static double sweepRelative(double arc,double progress) {
        if(!Double.isFinite(arc) || !Double.isFinite(progress))return 0;
        return -arc*.5+arc*Math.max(0,Math.min(1,progress));
    }
    public static double sweepAngle(double heading,double arc,double progress) {
        return heading+sweepRelative(arc,progress);
    }
    public static YangJianSkill forAction(int action) {
        for(YangJianSkill skill:values())if(skill.action==action)return skill;
        return null;
    }

    /** Distances and cooldowns decide the pool before a weighted roll is performed. */
    public double weight(double distance,boolean usingItem,boolean marked,boolean houndAlive,
                         double healthRatio,int previousAction,int cooldownRemaining) {
        if(cooldownRemaining>0)return 0;
        double weight=switch(this) {
            case COMBO -> distance<=5.3?40:distance<8?10:0;
            case FOUR_COMBO -> distance<=5.4?29:0;
            case SIX_COMBO -> distance<=5.5?(healthRatio<0.72?29:13):0;
            case THROW -> distance>=7?(distance>13?42:25):0;
            case THRUST -> distance>=4?(usingItem?70:marked?62:distance>10?40:19):0;
            case SUMMON_HOUND -> !houndAlive?(distance>5?32:13):0;
            case COORDINATED -> houndAlive && distance<14?(marked?40:25):0;
            default -> 0;
        };
        return previousAction==action?weight*0.18:weight;
    }
    public double weightForPhase(int fightPhase,int weapon,double distance,boolean usingItem,boolean marked,
                                 boolean houndAlive,double healthRatio,int previousAction,int remaining,boolean pressure) {
        if(phase()!=fightPhase || remaining>0)return 0;
        if(fightPhase==1)return weight(distance,usingItem,marked,houndAlive,healthRatio,previousAction,remaining);
        double value=switch(this) {
            case AXE_SUMMON -> weapon!=1?(distance>=5?34:24):0;
            case AXE_SLAM -> weapon==1 && distance<=18?(distance>5?38:22):0;
            case AXE_COMBO -> weapon==1 && distance<=7?43:0;
            case FLYING_SWORDS -> distance>=6?(distance>13?49:26):0;
            case DRAW_SLASH -> distance<=8?(pressure || usingItem?52:29):0;
            case WHIP_SWEEP -> distance>=4 && distance<=12?38:0;
            case WHIP_SPIN -> distance<=10?(healthRatio<.5?31:22):0;
            case LIGHTNING_THRUST -> distance>=5?(usingItem?60:distance>12?43:25):0;
            case INVISIBLE_DASH -> distance>=3 && distance<=18?(pressure?42:17):0;
            case DELAYED_COMBO -> distance<=7?(pressure?45:31):0;
            // P3 keeps only the thin fixed shot. The thick sustained beam (TRACKING_BEAM) is
            // the one that never sweeps: it only creeps toward the target at 0.7 degrees per
            // tick, so it reads as a stationary pillar of light, and it is out of the pool.
            // The enum, its timing and its animation stay so the pool can be re-enabled and
            // older saves keep mapping, but nothing selects it.
            case EYE_BEAM -> distance>=4?(usingItem?50:28):9;
            case SWEEP_BEAM -> distance>=6?30:13;
            case MYRIAD_SWORDS -> distance>=7?(distance>14?39:29):16;
            case SWORD_RAIN -> distance>=3?25:12;
            case RED_THUNDER -> distance>=4?30:18;
            case DIVINE_SWEEP -> distance<=12?(distance<6?40:24):0;
            case AERIAL_COMBO -> distance>=5?24:12;
            case DIVINE_JUDGEMENT -> healthRatio<=.85?48:18;
            default -> 0;
        };
        return previousAction==action?0:value;
    }

    /** A broad sweep is an arc of finite weapon segments, not a box that hits behind the caster. */
    public static boolean sweptArc(double px,double pz,double heading,double fromAngle,double toAngle,
                                   double reach,double victimRadius) {
        int samples=Math.max(1,(int)Math.ceil(Math.abs(toAngle-fromAngle)/Math.toRadians(12)));
        double padding=victimRadius+0.27;
        for(int i=0;i<=samples;i++) {
            double angle=heading+fromAngle+(toAngle-fromAngle)*i/samples;
            if(CombatMath.segmentDistanceSquared(px,pz,0,0,Math.cos(angle)*reach,Math.sin(angle)*reach)<=padding*padding)return true;
        }
        return false;
    }
    public static float guardDamage(float incoming,float maximum,boolean blocked) {
        if(!Float.isFinite(incoming) || incoming<=0 || maximum<=0)return 0;
        return Math.min(maximum*0.2F,incoming*(blocked?0.65F:1.0F));
    }
}
