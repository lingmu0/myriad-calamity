package net.xuwu.myriadcalamity.client;

/** Samples baked Blockbench curves without discarding complete 360/720-degree turns. */
public final class AnimationTrack {
    private AnimationTrack() {}
    public static float[] sample(float[][] keys,float time,float fallback) {
        if(keys==null || keys.length==0)return new float[]{fallback,fallback,fallback};
        if(time<=keys[0][0])return value(keys[0]);
        int last=keys.length-1;
        if(time>=keys[last][0])return value(keys[last]);
        // Curves are baked at 60 Hz; binary search keeps large articulated rigs inexpensive.
        int low=0,high=last;
        while(high-low>1) {
            int middle=(low+high)>>>1;
            if(keys[middle][0]<=time)low=middle;else high=middle;
        }
        float[] left=keys[low],right=keys[high];
        float t=right[0]==left[0]?0:Math.max(0,Math.min(1,(time-left[0])/(right[0]-left[0])));
        return new float[]{left[1]+(right[1]-left[1])*t,left[2]+(right[2]-left[2])*t,left[3]+(right[3]-left[3])*t};
    }
    private static float[] value(float[] key) { return new float[]{key[1],key[2],key[3]}; }
}
