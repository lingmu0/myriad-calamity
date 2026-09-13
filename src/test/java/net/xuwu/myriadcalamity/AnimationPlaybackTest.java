package net.xuwu.myriadcalamity;

import java.util.Map;
import net.xuwu.myriadcalamity.client.AnimationPlayback;
import net.xuwu.myriadcalamity.client.AnimationTrack;

/** Checks animation behavior directly without booting Minecraft. */
public final class AnimationPlaybackTest {
    private static int checks;
    private static void check(boolean value,String message) { checks++;if(!value)throw new AssertionError(message); }
    private static boolean near(float a,float b) { return Math.abs(a-b)<0.0001F; }
    private static Map<String,AnimationPlayback.Pose> pose(float x,float angle,float scale) {
        return Map.of("body",new AnimationPlayback.Pose(new float[]{x,0,0},new float[]{0,angle,0},new float[]{scale,scale,scale}));
    }
    private static AnimationPlayback.Pose body(Map<String,AnimationPlayback.Pose> pose) { return pose.get("body"); }
    public static void main(String[] args) {
        float[][] dense=new float[361][4];
        for(int i=0;i<dense.length;i++)dense[i]=new float[]{i/60F,i,2*i,-i};
        boolean denseCorrect=true;
        for(int i=0;i<360;i++) {
            float[] value=AnimationTrack.sample(dense,(i+0.25F)/60,0);
            denseCorrect&=near(value[0],i+0.25F)&&near(value[1],2*(i+0.25F))&&near(value[2],-i-0.25F);
        }
        check(denseCorrect,"Binary sampling follows dense curves between every pair of keys");
        check(near(AnimationTrack.sample(dense,-2,0)[1],0),"Sampling before a clip holds its first pose");
        check(near(AnimationTrack.sample(dense,8,0)[1],720),"Sampling after a clip preserves the complete 720 degree turn");
        check(near(AnimationTrack.sample(null,0,1)[2],1),"Missing channels preserve unit scale");
        check(near(AnimationTrack.sample(new float[][]{{0,2,3,4}},1,0)[1],3),"Single key channels stay constant");

        AnimationPlayback dancer=new AnimationPlayback();
        var first=body(dancer.apply("idle",0,4,pose(0,350,1)));
        check(near(first.rotation()[1],350),"An entity first rendered mid-clip starts at its current pose");
        var target=pose(10,10,2);
        var start=body(dancer.apply("dash",1,4,target));
        check(near(start.position()[0],0)&&near(start.rotation()[1],350),"A new attack starts at the displayed source pose");
        var quarter=body(dancer.apply("dash",2,4,target));
        check(near(quarter.position()[0],1.5625F),"Transition eases gently into the new pose");
        var half=body(dancer.apply("dash",3,4,target));
        check(near(half.rotation()[1],360),"350 to 10 degrees crosses zero along the short arc");
        check(near(half.position()[0],5)&&near(half.scale()[0],1.5F),"Position and scale blend with rotation");
        var end=body(dancer.apply("dash",5,4,target));
        check(near(end.rotation()[1],370)&&near(end.position()[0],10),"Transition reaches the destination without residual lag");
        var fullTurn=body(dancer.apply("dash",6,4,pose(10,730,2)));
        check(near(fullTurn.rotation()[1]-end.rotation()[1],720),"A constant transition offset retains full turns inside the clip");
        check(near(body(target).rotation()[1],10),"Playback never modifies the sampled source data");

        AnimationPlayback sparse=new AnimationPlayback();
        sparse.apply("idle",0,4,pose(0,350,1));sparse.apply("dash",1,4,target);
        var sparseHalf=body(sparse.apply("dash",3,4,target));
        check(near(sparseHalf.rotation()[1],half.rotation()[1])&&near(sparseHalf.position()[0],half.position()[0]),"Transitions are independent of render frame rate");
        var interrupt=body(sparse.apply("death",3,4,pose(-10,160,0.5F)));
        check(near(interrupt.position()[0],sparseHalf.position()[0])&&near(interrupt.rotation()[1],sparseHalf.rotation()[1]),"Interrupted attacks blend from the pose actually on screen");

        AnimationPlayback partner=new AnimationPlayback();
        partner.apply("idle",0,4,pose(-8,-20,1));
        var partnerStart=body(partner.apply("dash",1,4,target));
        check(near(partnerStart.position()[0],-8)&&near(partnerStart.rotation()[1],-20),"Each dancer retains an independent transition history");
        var reappeared=body(partner.apply("dash",30,4,pose(7,90,1)));
        check(near(reappeared.position()[0],7)&&near(reappeared.rotation()[1],90),"A long invisible interval discards stale transition poses");
        var reset=body(partner.apply("idle",0,4,pose(1,0,1)));
        check(near(reset.position()[0],1),"Clock reset discards the previous entity timeline");
        System.out.println("Animation playback: "+checks+" checks passed.");
    }
}
