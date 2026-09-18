package net.xuwu.myriadcalamity.entity;

import net.minecraft.util.Mth;
import java.util.ArrayList;
import java.util.List;

/** Bounded threat layouts and transition rules, with space between every announced impact. */
public final class YangJianPhaseThree {
    public static final int OPEN_TICKS=70,MAX_WAVE=9;
    public static final double MAX_TARGET_PREDICTION=4.5;
    private YangJianPhaseThree() { }
    public static float enteringHealth(float health,float maximum,double floor) {
        if(!Float.isFinite(maximum) || maximum<1)maximum=1;
        if(!Float.isFinite(health))health=1;
        double fraction=Double.isFinite(floor)?Mth.clamp(floor,0,1):.4;
        return Math.min(maximum,Math.max(1,Math.max(health,(float)(maximum*fraction))));
    }
    /** wave 0: position; 1: bounded movement prediction; 2: nearby displaced pressure. */
    public static List<CombatMath.Point> pattern(double px,double pz,double vx,double vz,int wave,int turn,
                                               int requested,double arenaRadius,double impactRadius) {
        int count=Mth.clamp(requested,0,MAX_WAVE);List<CombatMath.Point> points=new ArrayList<>(count);
        if(count==0)return points;
        double radius=Math.max(impactRadius+1,arenaRadius-impactRadius-.35);
        double angle=turn*.73+wave*1.39;
        if(wave==1) {
            CombatMath.Point lead=CombatMath.clampDisk(vx*8,vz*8,MAX_TARGET_PREDICTION);px+=lead.x();pz+=lead.z();
        } else if(wave>=2) { px+=Math.cos(angle)*3.2;pz+=Math.sin(angle)*3.2; }
        CombatMath.Point anchor=CombatMath.clampDisk(px,pz,radius);points.add(anchor);
        double spacing=impactRadius*2+.65;
        for(int i=0;i<96 && points.size()<count;i++) {
            double direction=angle+i*2.399963229728653;
            double ring=spacing+1+(i%3)*2.5;
            CombatMath.Point candidate=CombatMath.clampDisk(anchor.x()+Math.cos(direction)*ring,anchor.z()+Math.sin(direction)*ring,radius);
            boolean separated=true;
            for(CombatMath.Point previous:points)if(Math.hypot(previous.x()-candidate.x(),previous.z()-candidate.z())<spacing) { separated=false;break; }
            if(separated)points.add(candidate);
        }
        return List.copyOf(points);
    }
}
