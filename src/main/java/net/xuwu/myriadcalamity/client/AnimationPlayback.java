package net.xuwu.myriadcalamity.client;

import java.util.HashMap;
import java.util.Map;

/** One entity's transition history; never shared between the two rendered dancers. */
public final class AnimationPlayback {
    public record Pose(float[] position,float[] rotation,float[] scale) {}
    private String clipKey;
    private float lastClock,transitionStart,transitionDuration;
    private Map<String,Pose> displayed=Map.of(),source=Map.of();
    private Map<String,float[]> rotationOffsets=Map.of();

    public Map<String,Pose> apply(String key,float clock,float duration,Map<String,Pose> sampled) {
        boolean reset=clipKey==null || clock<lastClock || clock-lastClock>10;
        if(reset) {
            clipKey=key;source=Map.of();rotationOffsets=Map.of();transitionDuration=0;
        } else if(!key.equals(clipKey)) {
            clipKey=key;source=displayed;transitionStart=clock;transitionDuration=Math.max(0,duration);
            rotationOffsets=new HashMap<>();
            sampled.forEach((name,pose)->{
                Pose previous=source.get(name);
                if(previous!=null) {
                    float[] shift=new float[3];
                    for(int axis=0;axis<3;axis++)
                        shift[axis]=(float)(360*Math.floor((previous.rotation()[axis]-pose.rotation()[axis]+180)/360));
                    rotationOffsets.put(name,shift);
                }
            });
        }
        float progress=transitionDuration<=0?1:Math.clamp((clock-transitionStart)/transitionDuration,0,1);
        float blend=progress*progress*(3-2*progress);
        Map<String,Pose> result=new HashMap<>(sampled.size());
        sampled.forEach((name,pose)->{
            float[] rotation=pose.rotation().clone(),offset=rotationOffsets.get(name);
            // Keep this offset constant throughout a clip, preserving its intentional full turns.
            if(offset!=null)for(int axis=0;axis<3;axis++)rotation[axis]+=offset[axis];
            Pose previous=source.get(name);
            result.put(name,previous==null || progress>=1
                ?new Pose(pose.position(),rotation,pose.scale())
                :new Pose(mix(previous.position(),pose.position(),blend),mix(previous.rotation(),rotation,blend),mix(previous.scale(),pose.scale(),blend)));
        });
        if(progress>=1)source=Map.of();
        displayed=result;lastClock=clock;
        return result;
    }
    private static float[] mix(float[] from,float[] to,float blend) {
        return new float[]{from[0]+(to[0]-from[0])*blend,from[1]+(to[1]-from[1])*blend,from[2]+(to[2]-from[2])*blend};
    }
}
