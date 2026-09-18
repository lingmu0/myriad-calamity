package net.xuwu.myriadcalamity.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.EntityModel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.entity.CogworkDancer;
import net.xuwu.myriadcalamity.entity.CombatMath;
import org.joml.Quaternionf;

/** Renders the same articulated mesh and keyframes exported from the editable Blockbench project. */
public final class CogworkDancerModel extends EntityModel<CogworkDancer> {
    private record Face(float[][] vertices,float[] normal,boolean glow) {}
    private static final class Bone {
        String name;float[] pivot,baseRotation;
        float[] position={0,0,0},rotation={0,0,0},scale={1,1,1};
        final List<Face> faces=new ArrayList<>();final List<Bone> children=new ArrayList<>();
    }
    private record Clip(float length,boolean loop,Map<String,Map<String,float[][]>> channels) {}
    private final List<Bone> roots=new ArrayList<>();
    private final Map<String,Bone> bones=new HashMap<>();
    private final Map<String,Clip> clips=new HashMap<>();
    private final Map<CogworkDancer,AnimationPlayback> playback=new WeakHashMap<>();
    private boolean halo;
    public CogworkDancerModel(ResourceManager resources) {
        JsonObject mesh=read(resources,"mesh/cogwork_dancer.json");
        for(JsonElement root:mesh.getAsJsonArray("bones"))roots.add(bone(root.getAsJsonObject()));
        JsonObject animations=read(resources,"animations/cogwork_dancer.json").getAsJsonObject("clips");
        animations.entrySet().forEach(entry->{
            JsonObject data=entry.getValue().getAsJsonObject();Map<String,Map<String,float[][]>> channels=new HashMap<>();
            data.getAsJsonObject("bones").entrySet().forEach(b->{Map<String,float[][]> tracks=new HashMap<>();
                b.getValue().getAsJsonObject().entrySet().forEach(c->{JsonArray keys=c.getValue().getAsJsonArray();float[][] values=new float[keys.size()][];
                    for(int i=0;i<values.length;i++)values[i]=array(keys.get(i).getAsJsonArray());tracks.put(c.getKey(),values);});channels.put(b.getKey(),tracks);});
            clips.put(entry.getKey(),new Clip(data.get("length").getAsFloat(),data.get("loop").getAsBoolean(),channels));
        });
    }
    private static JsonObject read(ResourceManager resources,String path) {
        try(var stream=resources.getResourceOrThrow(MyriadCalamity.id(path)).open();var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch(IOException exception) { throw new IllegalStateException("Cannot load Cogwork Dancer asset "+path,exception); }
    }
    private static float[] array(JsonArray data) {float[] result=new float[data.size()];for(int i=0;i<result.length;i++)result[i]=data.get(i).getAsFloat();return result;}
    private Bone bone(JsonObject data) {
        Bone b=new Bone();b.name=data.get("name").getAsString();b.pivot=array(data.getAsJsonArray("pivot"));b.baseRotation=array(data.getAsJsonArray("rotation"));
        for(JsonElement entry:data.getAsJsonArray("faces")) {
            JsonObject f=entry.getAsJsonObject();float[][] vertices=new float[4][];
            for(int i=0;i<4;i++)vertices[i]=array(f.getAsJsonArray("v").get(i).getAsJsonArray());
            b.faces.add(new Face(vertices,array(f.getAsJsonArray("n")),f.get("glow").getAsBoolean()));
        }
        for(JsonElement child:data.getAsJsonArray("children"))b.children.add(bone(child.getAsJsonObject()));
        bones.put(b.name,b);return b;
    }
    @Override public void setupAnim(CogworkDancer dancer,float limbSwing,float limbSwingAmount,float age,float headYaw,float headPitch) {
        float partial=Mth.clamp(age-dancer.tickCount,0,1),elapsed=dancer.scheduledActionAge(partial),windup=dancer.attackWindup();
        String name;float time;
        if(dancer.deathTime>0){name="death";time=(dancer.deathTime+partial)/20F;}
        else if(elapsed<0 || dancer.action()==CogworkDancer.IDLE){name=dancer.phase()==4?"solo_idle":"idle";time=age/20F;}
        else if(dancer.action()==CogworkDancer.REWIND){name="rewind";time=elapsed/20F;}
        else if(elapsed<windup){
            name=switch(dancer.action()) {
                case CogworkDancer.DASH->"dash_windup";
                case CogworkDancer.SLAM->"slam_windup";
                case CogworkDancer.BARRAGE->"barrage_windup";
                default->"duet_windup";
            };time=elapsed/windup;
        }else if(dancer.action()==CogworkDancer.BARRAGE) {
            float active=elapsed-windup,passes=CombatMath.BARRAGE_PASSES*CombatMath.BARRAGE_PASS_TICKS;
            name=active<passes?"barrage_dash":"barrage_recover";
            time=(active<passes?active%CombatMath.BARRAGE_PASS_TICKS:active-passes)/20F;
        }else{
            name=switch(dancer.action()){case CogworkDancer.DASH->"dash";case CogworkDancer.SLAM->"slam";case CogworkDancer.SPIN->"duet";default->"failed_duet";};time=(elapsed-windup)/20F;
        }
        Clip clip=clips.get(name);
        if(clip==null)throw new IllegalStateException("Missing animation "+name);
        time=clip.loop()?time%clip.length():Math.min(time,clip.length());
        Map<String,AnimationPlayback.Pose> sampled=new HashMap<>(bones.size());
        for(Bone bone:bones.values()) {
            Map<String,float[][]> channels=clip.channels().getOrDefault(bone.name,Map.of());
            sampled.put(bone.name,new AnimationPlayback.Pose(
                AnimationTrack.sample(channels.get("position"),time,0),
                AnimationTrack.sample(channels.get("rotation"),time,0),
                AnimationTrack.sample(channels.get("scale"),time,1)));
        }
        float transition=name.equals("idle") || name.equals("solo_idle")?5:name.endsWith("windup")?3:2;
        if(name.equals("death") || name.endsWith("recover"))transition=4;
        Map<String,AnimationPlayback.Pose> pose=playback.computeIfAbsent(dancer,ignored->new AnimationPlayback())
            .apply(dancer.action()+":"+name,age,transition,sampled);
        for(Bone bone:bones.values()) {
            AnimationPlayback.Pose value=pose.get(bone.name);
            bone.position=value.position();bone.rotation=value.rotation();bone.scale=value.scale();
        }
        halo=dancer.deathTime==0 && elapsed>=0 && dancer.action()!=CogworkDancer.IDLE && (elapsed<windup || dancer.action()==CogworkDancer.SPIN || dancer.action()==CogworkDancer.REWIND || dancer.action()==CogworkDancer.BARRAGE);
    }
    @Override public void renderToBuffer(PoseStack pose,VertexConsumer buffer,int light,int overlay,float red,float green,float blue,float alpha) {
        render(pose,buffer,light,overlay,FastColor.ARGB32.color((int)(alpha*255F),(int)(red*255F),(int)(green*255F),(int)(blue*255F)));
    }
    /** Internal entry point; callers pass one packed ARGB colour. */
    public void render(PoseStack pose,VertexConsumer buffer,int light,int overlay,int color) {
        pose.pushPose();pose.translate(0,1.5,0);
        for(Bone bone:roots)renderBone(bone,pose,buffer,light,overlay,color);
        pose.popPose();
    }
    private void renderBone(Bone bone,PoseStack pose,VertexConsumer buffer,int light,int overlay,int color) {
        if(bone.name.equals("halo")&&!halo)return;
        pose.pushPose();
        pose.translate((bone.pivot[0]+bone.position[0])/16F,(bone.pivot[1]+bone.position[1])/16F,(bone.pivot[2]+bone.position[2])/16F);
        pose.mulPose(new Quaternionf().rotationZYX((bone.baseRotation[2]+bone.rotation[2])*Mth.DEG_TO_RAD,(bone.baseRotation[1]+bone.rotation[1])*Mth.DEG_TO_RAD,(bone.baseRotation[0]+bone.rotation[0])*Mth.DEG_TO_RAD));
        pose.scale(bone.scale[0],bone.scale[1],bone.scale[2]);
        for(Face face:bone.faces) for(float[] v:face.vertices()) {
            buffer.vertex(pose.last().pose(),v[0]/16F,v[1]/16F,v[2]/16F).color(color).uv(v[3],v[4]).overlayCoords(overlay)
                .uv2(face.glow()?15728880:light).normal(pose.last().normal(),face.normal()[0],face.normal()[1],face.normal()[2]).endVertex();
        }
        for(Bone child:bone.children)renderBone(child,pose,buffer,light,overlay,color);
        pose.popPose();
    }
}
