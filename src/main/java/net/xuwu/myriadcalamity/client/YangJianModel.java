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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.entity.YangJian;
import net.xuwu.myriadcalamity.entity.YangJianHazard;
import net.xuwu.myriadcalamity.entity.YangJianSkill;
import net.xuwu.myriadcalamity.entity.YangJianTransition;
import org.joml.Quaternionf;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The approved Blockbench model, with its complete articulated rig and baked combat keyframes. */
public final class YangJianModel extends EntityModel<YangJian> {
    private record Face(float[][] vertices,float[] normal,boolean glow) {}
    private static final class Bone {
        String name;
        float[] pivot,baseRotation;
        float[] position={0,0,0},rotation={0,0,0},scale={1,1,1};
        final List<Face> faces=new ArrayList<>();
        final List<Bone> children=new ArrayList<>();
    }
    private record Clip(float length,boolean loop,Map<String,Map<String,float[][]>> channels) {}
    private final List<Bone> roots=new ArrayList<>();
    private final Map<String,Bone> bones=new HashMap<>();
    private final Map<String,Clip> clips=new HashMap<>();
    private final List<Bone> weaponPath=new ArrayList<>();
    private final List<Bone> headPath=new ArrayList<>();
    private final Map<YangJian,AnimationPlayback> playback=new WeakHashMap<>();
    private final YangJianWeapons weapons;
    private Matrix4f heldWeaponTransform=new Matrix4f();
    private boolean weaponHidden;
    private float eyeGlow;
    private boolean veiled;
    private int selectedWeapon;
    private float weaponGrowth=1;
    /** Length multiplier applied to the held spear's own axis (P3 divine sweep). */
    private float weaponLength=1;
    private static final float EYE_CHARGE_GLOW=2.6F;

    public YangJianModel(ResourceManager resources) {
        weapons=new YangJianWeapons(resources);
        for(JsonElement root:read(resources,"mesh/yang_jian.json").getAsJsonArray("bones"))
            roots.add(bone(root.getAsJsonObject()));
        for(Bone root:roots)if(findWeaponPath(root))break;
        for(Bone root:roots)if(findPath(root,"head",headPath))break;
        read(resources,"animations/yang_jian.json").getAsJsonObject("clips").entrySet().forEach(entry->{
            JsonObject data=entry.getValue().getAsJsonObject();
            Map<String,Map<String,float[][]>> channels=new HashMap<>();
            data.getAsJsonObject("bones").entrySet().forEach(b->{
                if(!bones.containsKey(b.getKey()))throw new IllegalStateException("Unknown Yang Jian animation bone "+b.getKey());
                Map<String,float[][]> tracks=new HashMap<>();
                b.getValue().getAsJsonObject().entrySet().forEach(c->{
                    JsonArray keys=c.getValue().getAsJsonArray();
                    float[][] values=new float[keys.size()][];
                    for(int i=0;i<values.length;i++)values[i]=array(keys.get(i).getAsJsonArray());
                    tracks.put(c.getKey(),values);
                });
                channels.put(b.getKey(),tracks);
            });
            clips.put(entry.getKey(),new Clip(data.get("length").getAsFloat(),data.get("loop").getAsBoolean(),channels));
        });
    }

    private static JsonObject read(ResourceManager resources,String path) {
        try(var stream=resources.getResourceOrThrow(MyriadCalamity.id(path)).open();
            var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch(IOException exception) {
            throw new IllegalStateException("Cannot load Yang Jian asset "+path,exception);
        }
    }

    private static float[] array(JsonArray data) {
        float[] result=new float[data.size()];
        for(int i=0;i<result.length;i++)result[i]=data.get(i).getAsFloat();
        return result;
    }

    private Bone bone(JsonObject data) {
        Bone b=new Bone();
        b.name=data.get("name").getAsString();
        b.pivot=array(data.getAsJsonArray("pivot"));
        b.baseRotation=array(data.getAsJsonArray("rotation"));
        for(JsonElement entry:data.getAsJsonArray("faces")) {
            JsonObject f=entry.getAsJsonObject();float[][] vertices=new float[4][];
            for(int i=0;i<4;i++)vertices[i]=array(f.getAsJsonArray("v").get(i).getAsJsonArray());
            b.faces.add(new Face(vertices,array(f.getAsJsonArray("n")),f.get("glow").getAsBoolean()));
        }
        for(JsonElement child:data.getAsJsonArray("children"))b.children.add(bone(child.getAsJsonObject()));
        if(bones.put(b.name,b)!=null)throw new IllegalStateException("Duplicate Yang Jian bone "+b.name);
        return b;
    }

    @Override public void setupAnim(YangJian boss,float limbSwing,float limbSwingAmount,float age,float headYaw,float headPitch) {
        float partial=Mth.clamp(age-boss.tickCount,0,1);
        float elapsed=Math.max(0,boss.actionAge(partial));
        float stepAge=boss.stepAge(partial),windup=Math.max(1,boss.stepWindup());
        int action=boss.action();
        String name;
        float time;
        if(boss.deathTime>0) {name=boss.phase()==3?"p3_death":"death";time=(boss.deathTime+partial)/20F;}
        else if(action==YangJian.IDLE) {
            String carry=boss.phase()==2 && boss.weapon()>0
                ?switch(boss.weapon()){case 1->"axe";case 2->"sword";default->"whip";}:"";
            if(boss.isApproaching()) {
                name="step_approach"+(carry.isEmpty()?"":"_"+carry);
                time=Math.max(0,boss.approachAge(partial))/20F;
            } else {
                name=!carry.isEmpty()?"p2_idle_"+carry:boss.phase()==3?"p3_idle":"idle";
                time=age/20F;
            }
        } else if(action==YangJian.THRUST || action==YangJian.COUNTER
                || action==YangJian.THROW && elapsed>=26 && elapsed<44
                || action==YangJian.COORDINATED && boss.comboStep()>0) {
            boolean counter=action==YangJian.COUNTER;
            if(stepAge<windup) {
                name=counter?"counter_windup":"thrust_windup";
                time=Mth.clamp(stepAge/windup,0,1);
            } else {
                name=counter?"counter":"thrust";
                time=(stepAge-windup)/20F;
            }
        } else {
            name=switch(action) {
                case YangJian.COMBO -> "combo";
                case YangJian.FOUR_COMBO -> "four_combo";
                case YangJian.SIX_COMBO -> "six_combo";
                case YangJian.THROW -> elapsed>=50?"recall":elapsed>=44?"throw_followup":"throw";
                case YangJian.SUMMON_HOUND -> "summon_hound";
                case YangJian.COORDINATED -> "coordinated";
                case YangJian.GUARD -> "guard";
                case YangJian.PHASE_CLEAR -> "phase_clear";
                case YangJian.TRANSITION -> "transition";
                case YangJian.AXE_SUMMON -> "axe_summon";
                case YangJian.AXE_SLAM -> "axe_slam";
                case YangJian.AXE_COMBO -> "axe_combo";
                case YangJian.FLYING_SWORDS -> "flying_swords";
                case YangJian.DRAW_SLASH -> "draw_slash";
                case YangJian.WHIP_SWEEP -> "whip_sweep";
                case YangJian.WHIP_SPIN -> "whip_spin";
                case YangJian.LIGHTNING_THRUST -> "lightning_thrust";
                case YangJian.INVISIBLE_DASH -> "invisible_dash";
                case YangJian.DELAYED_COMBO -> "delayed_combo";
                case YangJian.THIRD_EYE_OPEN -> "third_eye_open";
                case YangJian.EYE_BEAM -> "eye_beam";
                case YangJian.SWEEP_BEAM -> "sweep_beam";
                case YangJian.TRACKING_BEAM -> "tracking_beam";
                case YangJian.MYRIAD_SWORDS -> "myriad_swords";
                case YangJian.SWORD_RAIN -> "sword_rain";
                case YangJian.RED_THUNDER -> "red_thunder";
                case YangJian.DIVINE_SWEEP -> boss.telegraphAngle()>300?"divine_spin":"divine_sweep";
                case YangJian.AERIAL_COMBO -> "aerial_combo";
                case YangJian.DIVINE_JUDGEMENT -> "divine_judgement";
                default -> "idle";
            };
            time=(action==YangJian.THROW && elapsed>=50?elapsed-50:
                action==YangJian.THROW && elapsed>=44?elapsed-44:elapsed)/20F;
        }
        Clip clip=clips.get(name);
        if(clip==null)throw new IllegalStateException("Missing Yang Jian animation "+name);
        time=clip.loop()?time%clip.length():Math.min(time,clip.length());
        Map<String,AnimationPlayback.Pose> sampled=new HashMap<>(bones.size());
        for(Bone b:bones.values()) {
            Map<String,float[][]> channels=clip.channels().getOrDefault(b.name,Map.of());
            float[] rotation=AnimationTrack.sample(channels.get("rotation"),time,0);
            // In attacks the animation supplies counter-rotation; idle adds restrained target tracking.
            if(b.name.equals("head") && action==YangJian.IDLE) {
                float yawLimit=boss.phase()==3?55:boss.isApproaching()?12:25,pitchLimit=boss.phase()==3?45:boss.isApproaching()?8:14;
                rotation[1]+=Mth.clamp(headYaw,-yawLimit,yawLimit);
                rotation[0]+=Mth.clamp(headPitch,-pitchLimit,pitchLimit);
            }
            sampled.put(b.name,new AnimationPlayback.Pose(AnimationTrack.sample(channels.get("position"),time,0),
                rotation,AnimationTrack.sample(channels.get("scale"),time,1)));
        }
        float blend=boss.phase()==3?1:name.endsWith("windup")?2:name.contains("idle")?4:1;
        // No blend is applied at each hit: all segments of a combination are one uninterrupted clip.
        Map<String,AnimationPlayback.Pose> result=playback.computeIfAbsent(boss,ignored->new AnimationPlayback())
            .apply(action+":"+name,age,blend,sampled);
        for(Bone b:bones.values()) {
            var value=result.get(b.name);
            b.position=value.position();b.rotation=value.rotation();b.scale=value.scale();
        }
        weaponHidden=boss.weaponThrown();
        eyeGlow=boss.phase()==3?1:action==YangJian.THIRD_EYE_OPEN?Mth.clamp((elapsed-20)/35,0,1):0;
        YangJianHazard beam=boss.currentBeam();
        float bodyYaw=Mth.rotLerp(partial,boss.yBodyRotO,boss.yBodyRot);
        if(beam!=null) {
            aimHead(bodyYaw,beam.visualDirection(partial));
            beam.setVisualOrigin(new Vec3(Mth.lerp(partial,boss.xOld,boss.getX()),Mth.lerp(partial,boss.yOld,boss.getY()),
                Mth.lerp(partial,boss.zOld,boss.getZ())).add(eyePoint(bodyYaw)));
            eyeGlow=beam.warning(partial)?1+2*Mth.clamp(beam.age(partial)/Math.max(1,beam.windup()),0,1):2;
        } else if(boss.phase()==3 && action!=YangJian.DIVINE_SWEEP && !boss.isTransitioning()) {
            // Track through aerial casts and recovery instead of inheriting a
            // frozen head pose from a long composite animation.
            float lookYaw=Mth.rotLerp(partial,boss.yHeadRotO,boss.yHeadRot)*Mth.DEG_TO_RAD;
            float pitch=Mth.lerp(partial,boss.xRotO,boss.getXRot())*Mth.DEG_TO_RAD;
            aimHead(bodyYaw,new Vec3(-Math.sin(lookYaw)*Math.cos(pitch),-Math.sin(pitch),Math.cos(lookYaw)*Math.cos(pitch)));
        }
        selectedWeapon=Mth.clamp(boss.weapon(),0,3);
        // Every P3 laser charges the third eye for its whole windup, including the frames
        // between clone passes and composite casts whose beam hazard is swapped mid-action.
        float laserCharge=laserWindupCharge(action,elapsed);
        if(laserCharge>0)eyeGlow=Math.max(eyeGlow,1F+EYE_CHARGE_GLOW*laserCharge);
        // The divine sweep stretches the spear itself along its own axis instead of
        // trailing a bolt, so the visible weapon covers the swept hitbox.
        weaponLength=action==YangJian.DIVINE_SWEEP?YangJianSkill.divineSweepWeaponLength(elapsed):1F;
        veiled=boss.isInvisible() && action==YangJian.INVISIBLE_DASH;
        weaponGrowth=action==YangJian.AXE_SUMMON && selectedWeapon==1?Mth.clamp((elapsed-24)/5F,.1F,1):1;
        if(action==YangJian.TRANSITION) {
            selectedWeapon=elapsed<YangJianTransition.SUMMON_START?0:1;
            weaponHidden=false;
            weaponGrowth=selectedWeapon==0?1:(float)YangJianTransition.weaponScale(elapsed);
        }
        for(Bone root:roots) {
            Matrix4f transform=findWeapon(root,new Matrix4f());
            if(transform!=null){heldWeaponTransform=transform;break;}
        }
    }

    @Override public void renderToBuffer(PoseStack pose,VertexConsumer buffer,int light,int overlay,int color) {
        if(veiled)color=(color&0x00FFFFFF)|0x35000000;
        pose.pushPose();pose.translate(0,1.5,0);
        for(Bone bone:roots)renderBone(bone,pose,buffer,light,overlay,color,false);
        pose.popPose();
    }

    /** Draws the exact held weapon around its grip, with the spear tip along local -Y. */
    public void renderWeapon(PoseStack pose,VertexConsumer buffer,int light,int overlay) {
        Bone weapon=bones.get("weapon");
        if(weapon==null)throw new IllegalStateException("Yang Jian weapon bone is missing");
        renderBone(weapon,pose,buffer,light,overlay,0xFFFFFFFF,true);
    }

    /** Converts the animated handle endpoint into an entity-relative world vector. */
    public Vec3 heldWeaponPoint(float bodyYaw,float x,float y,float z) {
        return weaponPoint(heldWeaponTransform,bodyYaw,x,y,z,weaponGrowth);
    }

    /** Exact animated forehead point in entity-relative world coordinates. */
    public Vec3 eyePoint(float bodyYaw) {
        Matrix4f transform=new Matrix4f();
        for(Bone b:headPath)appendBone(transform,b);
        // Head-local anchor, scaled with the .9 uniform head shrink about the neck joint.
        return weaponPoint(transform,bodyYaw,0,-5.7105F/16F,-3.591F/16F,1);
    }

    private void aimHead(float bodyYaw,Vec3 direction) {
        if(headPath.isEmpty())return;
        Matrix4f parent=new Matrix4f().rotateY((180-bodyYaw)*Mth.DEG_TO_RAD)
            .scale(-YangJianRenderer.MODEL_SCALE,-YangJianRenderer.MODEL_SCALE,YangJianRenderer.MODEL_SCALE);
        for(int i=0;i<headPath.size()-1;i++)appendBone(parent,headPath.get(i));
        Bone head=headPath.getLast();
        float[] angles=YangJianBeamAim.rotation(parent,direction.x,direction.y,direction.z);
        head.rotation=new float[]{angles[0]-head.baseRotation[0],angles[1]-head.baseRotation[1],angles[2]-head.baseRotation[2]};
    }

    /** Each apparition has its own ray and local head pose; restore the body rig after drawing it. */
    public void renderBeamClone(PoseStack pose,VertexConsumer buffer,int light,int overlay,int color,
                               float bodyYaw,YangJianHazard beam,float partial) {
        Bone head=bones.get("head");float[] original=head.rotation;float originalGlow=eyeGlow;
        try {
            if(beam!=null) {
                aimHead(bodyYaw,beam.visualDirection(partial));eyeGlow=2;
                // Clone ray origin is its floor position plus the common eye anchor.
                beam.setVisualOrigin(beam.start().add(0,-2.98,0).add(eyePoint(bodyYaw)));
            }
            renderToBuffer(pose,buffer,light,overlay,color);
        } finally { head.rotation=original;eyeGlow=originalGlow; }
    }

    private static void appendBone(Matrix4f transform,Bone b) {
        transform.translate((b.pivot[0]+b.position[0])/16F,(b.pivot[1]+b.position[1])/16F,(b.pivot[2]+b.position[2])/16F)
            .rotate(new Quaternionf().rotationZYX((b.baseRotation[2]+b.rotation[2])*Mth.DEG_TO_RAD,
                (b.baseRotation[1]+b.rotation[1])*Mth.DEG_TO_RAD,(b.baseRotation[0]+b.rotation[0])*Mth.DEG_TO_RAD))
            .scale(b.scale[0],b.scale[1],b.scale[2]);
    }

    /** Charge of a pure laser windup, or 0 when the action does not charge the third eye. */
    private static float laserWindupCharge(int action,float elapsed) {
        YangJianSkill skill=YangJianSkill.forAction(action);
        if(skill!=YangJianSkill.EYE_BEAM && skill!=YangJianSkill.SWEEP_BEAM && skill!=YangJianSkill.TRACKING_BEAM)
            return 0;
        return Mth.clamp(elapsed/Math.max(1,skill.stepWindup(0)),0,1);
    }

    private boolean findPath(Bone bone,String name,List<Bone> path) {
        path.add(bone);
        if(bone.name.equals(name))return true;
        for(Bone child:bone.children)if(findPath(child,name,path))return true;
        path.removeLast();return false;
    }

    /** Samples the authored axe pose so the lightning trail follows the actual blade. */
    public Vec3 transitionWeaponPoint(float tick,float bodyYaw,float x,float y,float z) {
        Clip clip=clips.get("transition");
        float time=Mth.clamp(tick/20F,0,clip.length());
        Matrix4f transform=new Matrix4f();
        for(Bone bone:weaponPath) {
            Map<String,float[][]> channels=clip.channels().getOrDefault(bone.name,Map.of());
            float[] position=AnimationTrack.sample(channels.get("position"),time,0);
            float[] rotation=AnimationTrack.sample(channels.get("rotation"),time,0);
            float[] scale=AnimationTrack.sample(channels.get("scale"),time,1);
            transform.translate((bone.pivot[0]+position[0])/16F,(bone.pivot[1]+position[1])/16F,
                (bone.pivot[2]+position[2])/16F)
                .rotate(new Quaternionf().rotationZYX((bone.baseRotation[2]+rotation[2])*Mth.DEG_TO_RAD,
                    (bone.baseRotation[1]+rotation[1])*Mth.DEG_TO_RAD,(bone.baseRotation[0]+rotation[0])*Mth.DEG_TO_RAD))
                .scale(scale[0],scale[1],scale[2]);
        }
        return weaponPoint(transform,bodyYaw,x,y,z,(float)YangJianTransition.weaponScale(tick));
    }

    private static Vec3 weaponPoint(Matrix4f transform,float bodyYaw,float x,float y,float z,float growth) {
        Vector3f point=new Vector3f(x*growth,y*growth,z*growth);
        transform.transformPosition(point);
        float scale=YangJianRenderer.MODEL_SCALE;
        point.mul(-scale,-scale,scale);
        new Quaternionf().rotationY((180-bodyYaw)*Mth.DEG_TO_RAD).transform(point);
        return new Vec3(point.x,point.y,point.z);
    }

    private boolean findWeaponPath(Bone bone) {
        weaponPath.add(bone);
        if(bone.name.equals("weapon"))return true;
        for(Bone child:bone.children)if(findWeaponPath(child))return true;
        weaponPath.removeLast();return false;
    }

    private Matrix4f findWeapon(Bone bone,Matrix4f parent) {
        Matrix4f transform=new Matrix4f(parent).translate((bone.pivot[0]+bone.position[0])/16F,
            (bone.pivot[1]+bone.position[1])/16F,(bone.pivot[2]+bone.position[2])/16F)
            .rotate(new Quaternionf().rotationZYX((bone.baseRotation[2]+bone.rotation[2])*Mth.DEG_TO_RAD,
                (bone.baseRotation[1]+bone.rotation[1])*Mth.DEG_TO_RAD,(bone.baseRotation[0]+bone.rotation[0])*Mth.DEG_TO_RAD))
            .scale(bone.scale[0],bone.scale[1],bone.scale[2]);
        if(bone.name.equals("weapon"))return transform;
        for(Bone child:bone.children) {Matrix4f found=findWeapon(child,transform);if(found!=null)return found;}
        return null;
    }

    private void renderBone(Bone b,PoseStack pose,VertexConsumer buffer,int light,int overlay,int color,boolean isolatedWeapon) {
        if(!isolatedWeapon && weaponHidden && b.name.equals("weapon"))return;
        pose.pushPose();
        if(!isolatedWeapon) {
            pose.translate((b.pivot[0]+b.position[0])/16F,(b.pivot[1]+b.position[1])/16F,(b.pivot[2]+b.position[2])/16F);
            pose.mulPose(new Quaternionf().rotationZYX((b.baseRotation[2]+b.rotation[2])*Mth.DEG_TO_RAD,
                (b.baseRotation[1]+b.rotation[1])*Mth.DEG_TO_RAD,(b.baseRotation[0]+b.rotation[0])*Mth.DEG_TO_RAD));
            pose.scale(b.scale[0],b.scale[1],b.scale[2]);
            // The spear's own length axis is local -Y, so the sweep stretches it out to reach.
            if(b.name.equals("weapon") && weaponLength!=1F)pose.scale(1F,weaponLength,1F);
        }
        if(!isolatedWeapon && b.name.equals("weapon") && selectedWeapon>0) {
            pose.scale(weaponGrowth,weaponGrowth,weaponGrowth);
            weapons.render(selectedWeapon,pose,buffer,light,overlay,color);
        } else {
            for(Face face:b.faces)for(float[] v:face.vertices())
                buffer.addVertex(pose.last(),v[0]/16F,v[1]/16F,v[2]/16F).setColor(color).setUv(v[3],v[4])
                    .setOverlay(overlay).setLight(face.glow()?15728880:light)
                    .setNormal(pose.last(),face.normal()[0],face.normal()[1],face.normal()[2]);
        }
        if(b.name.equals("head") && eyeGlow>0 && !isolatedWeapon) {
            // Head-local third-eye glow, scaled with the .9 uniform head shrink.
            float half=.18F*eyeGlow,z=-3.5865F,top=-6.012F,bottom=-5.409F;
            float[][] corners={{-half,top},{half,top},{half,bottom},{-half,bottom}};
            for(int i=0;i<4;i++)buffer.addVertex(pose.last(),corners[i][0]/16F,corners[i][1]/16F,z/16F)
                .setColor(0xFFFFEC9C).setUv((i==0 || i==3?1127F:1129F)/2048F,(i<2?594F:596F)/2048F)
                .setOverlay(overlay).setLight(15728880).setNormal(pose.last(),0,0,-1);
        }
        for(Bone child:b.children)renderBone(child,pose,buffer,light,overlay,color,false);
        pose.popPose();
    }
}
