package net.xuwu.myriadcalamity;

import net.xuwu.myriadcalamity.entity.YangJianTransition;

/** Platform impact and expanding shockwave dodge checks without launching Minecraft. */
public final class YangJianTransitionTest {
    private static final double GROUND=144;
    private static final double PLAYER_RADIUS=.3;
    private static final double PLAYER_HEIGHT=1.8;
    private static int checks;

    private static void check(boolean condition,String message) {
        checks++;
        if(!condition)throw new AssertionError(message);
    }

    private static boolean near(double a,double b) { return Math.abs(a-b)<1E-6; }

    public static void main(String[] args) {
        flightAndTelegraphTiming();
        groundImpact();
        expandingWave();
        ordinaryJumpDodgeWindows();
        System.out.println("Yang Jian P1 -> P2: "+checks+" transition/jump checks passed");
    }

    private static void flightAndTelegraphTiming() {
        check(near(YangJianTransition.height(0),0),"Takeoff starts on the platform");
        check(near(YangJianTransition.height(YangJianTransition.ASCEND_END),YangJianTransition.HEIGHT),
            "Yang Jian reaches the advertised airborne height before summoning the axe");
        for(double age=0;age<=YangJianTransition.DURATION;age+=.25) {
            double height=YangJianTransition.height(age);
            check(Double.isFinite(height) && height>=0 && height<=YangJianTransition.HEIGHT,
                "Interpolation cannot put the boss below the platform or above the flight ceiling");
        }
        check(YangJianTransition.SUMMON_END<=YangJianTransition.SWEEP_START,
            "The giant axe appears before its overhead sweep starts");
        check(YangJianTransition.SWEEP_END<YangJianTransition.IMPACT,
            "The half-circle sweep finishes before the platform hit");
        check(near(YangJianTransition.height(YangJianTransition.IMPACT),0),
            "The boss arrives at the floor on the impact frame");
        check(YangJianTransition.weaponScale(YangJianTransition.SUMMON_END)>
            YangJianTransition.weaponScale(YangJianTransition.SUMMON_START),
            "Summoning visibly enlarges the thunder axe");
        check(YangJianTransition.WAVE_START>YangJianTransition.IMPACT+12,
            "A normal jump has time to land before the separate shockwave begins");
        check(YangJianTransition.WAVE_END<YangJianTransition.DURATION,
            "The entire shockwave ends before ordinary P2 attacks resume");
    }

    private static void groundImpact() {
        for(double distance:new double[]{0,4,11,21.5,22,22.29}) {
            check(impact(distance,0),"A grounded player's overlapping hitbox is covered across the whole platform");
            check(!impact(distance,.451),"Jumping above the low damage band clears the platform-wide hit");
        }
        check(!impact(22.31,0),"The impact does not extend beyond the platform plus the player's hitbox");
        check(impact(10,.45),"Feet still touching the top of the low damage band remain exposed");
        check(!YangJianTransition.inGroundBand(GROUND-3,GROUND-.1,GROUND),
            "A player fully below the platform cannot be struck through its floor");
        check(YangJianTransition.inGroundBand(GROUND-.1,GROUND+.1,GROUND),
            "A bounding box crossing the ground plane is still exposed");
    }

    private static void expandingWave() {
        check(YangJianTransition.waveRadius(YangJianTransition.WAVE_START-1)<0,
            "No damaging wave exists during the delayed warning");
        check(near(YangJianTransition.waveRadius(YangJianTransition.WAVE_START),0),
            "The shockwave originates at the impact center");
        check(near(YangJianTransition.waveRadius(YangJianTransition.WAVE_END),YangJianTransition.RADIUS),
            "The shockwave reaches the platform edge");
        check(YangJianTransition.waveRadius(YangJianTransition.WAVE_END+1)<0,
            "The expired wave leaves no persistent damaging trace");
        double previous=-1;
        for(int age=YangJianTransition.WAVE_START;age<=YangJianTransition.WAVE_END;age++) {
            double radius=YangJianTransition.waveRadius(age);
            check(radius>=previous && radius<=YangJianTransition.RADIUS,
                "The ring moves outward without reversing or overshooting the arena");
            previous=radius;
        }
        for(double distance:new double[]{0,1,6,11,17,21.7,22}) {
            boolean groundedHit=false;
            for(int age=0;age<=YangJianTransition.DURATION;age++) {
                groundedHit|=wave(age,distance,distance,0);
                check(!wave(age,distance,distance,.451),"An airborne player can clear the moving wave at every radius");
            }
            check(groundedHit,"The outward wave eventually crosses every grounded position on the platform");
        }
        for(int age=0;age<=YangJianTransition.DURATION;age++)
            check(!wave(age,23,23,0),"Wave thickness cannot leak damage outside the platform");
        check(wave(110,12,8,0),"Running inward through the moving wave between ticks still intersects it");
        check(wave(110,8,12,0),"Running outward through the moving wave between ticks still intersects it");
        check(!wave(110,2,3,0),"The already-cleared center is safe after the wave moves away");
        check(!wave(YangJianTransition.WAVE_START-1,12,8,0),"Swept collision cannot damage before wave activation");
        check(!wave(YangJianTransition.WAVE_END+1,21,22,0),"Swept collision cannot damage after wave expiration");
    }

    private static void ordinaryJumpDodgeWindows() {
        // Vanilla standing jump: vY=0.42, then gravity 0.08 and air drag 0.98 per tick.
        // Two actual ground launches are required; the simulation rejects a midair second jump.
        for(double distance:new double[]{0,11,22}) {
            int firstContact=-1;
            for(int age=YangJianTransition.WAVE_START;age<=YangJianTransition.WAVE_END;age++)
                if(wave(age,distance,distance,0)) { firstContact=age;break; }
            check(firstContact>=0,"The no-jump control is hit by the shockwave");
            int firstJump=YangJianTransition.IMPACT-4;
            int secondJump=firstContact-4;
            double y=0,velocity=0;
            int launches=0;
            boolean impactHit=false,waveHit=false;
            for(int age=0;age<=YangJianTransition.DURATION;age++) {
                if(age==firstJump || age==secondJump) {
                    check(near(y,0),"Each dodge starts after the previous ordinary jump has landed");
                    velocity=.42;launches++;
                }
                y+=velocity;
                if(y<=0) { y=0;velocity=0; }
                else velocity=(velocity-.08)*.98;
                if(age==YangJianTransition.IMPACT)impactHit|=impact(distance,y);
                waveHit|=wave(age,distance,distance,y);
            }
            check(launches==2 && !impactHit && !waveHit,
                "Two normal jumps dodge the slam and subsequent wave at center, mid-platform, and edge");
        }
    }

    private static boolean impact(double distance,double feetHeight) {
        return YangJianTransition.impactTouches(distance,PLAYER_RADIUS,GROUND+feetHeight,GROUND+feetHeight+PLAYER_HEIGHT,GROUND);
    }

    private static boolean wave(int age,double previousDistance,double distance,double feetHeight) {
        return YangJianTransition.waveTouches(age,previousDistance,distance,PLAYER_RADIUS,
            GROUND+feetHeight,GROUND+feetHeight+PLAYER_HEIGHT,GROUND);
    }
}
