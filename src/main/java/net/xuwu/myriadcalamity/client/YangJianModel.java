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
import java.util.Set;
import java.util.WeakHashMap;
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
public final class YangJianModel {
    private record Face(float[][] vertices,float[] normal,boolean glow) {}
    private static final class Bone {
        String name;
        float[] pivot,baseRotation;
        float[] position={0,0,0},rotation={0,0,0},scale={1,1,1};
        final List<Face> faces=new ArrayList<>();
        final List<Bone> children=new ArrayList<>();
    }
    private record Clip(float length,boolean loop,Map<String,Map<String,float[][]>> channels) {}
    private record RenderPose(Map<String,AnimationPlayback.Pose> joints,boolean weaponHidden,float eyeGlow,
                              int selectedWeapon,float weaponGrowth,float weaponLength) {}
    private final List<Bone> roots=new ArrayList<>();
    private final Map<String,Bone> bones=new HashMap<>();
    private final Map<String,YangJianArmKinematics> armJoints=new HashMap<>();
    private static final Set<String> BOUNDED_ARM_JOINTS=Set.of("left_arm","left_forearm","left_hand",
        "right_arm","right_forearm","right_hand");
    private final Map<String,Clip> clips=new HashMap<>();
    private final List<Bone> weaponPath=new ArrayList<>();
    private final List<Bone> headPath=new ArrayList<>();
    private final Map<YangJian,AnimationPlayback> playback=new WeakHashMap<>();
    private final YangJianWeapons weapons;
    private final float[] thirdEye,spearTip;
    private final float eyeU,eyeV;
    private Clip currentClip;
    private float currentClipTime;

    private final Matrix4f heldWeaponTransform=new Matrix4f();
    private boolean weaponHidden;
    private float eyeGlow;
    private int selectedWeapon;
    private float weaponGrowth=1;
    /** Length multiplier applied to the held spear's own axis (P3 divine sweep). */
    private float weaponLength=1;
    private static final float EYE_CHARGE_GLOW=2.6F;

    public YangJianModel(ResourceManager resources) {
        weapons=new YangJianWeapons(resources);
        JsonObject mesh=read(resources,"mesh/yang_jian.json");
        JsonObject anchors=mesh.getAsJsonObject("anchors");
        // Meshes from older resource packs use the original sculpt and atlas.
        thirdEye=anchors==null?new float[]{0,-5.7105F,-3.591F}:array(anchors.getAsJsonArray("third_eye"));
        spearTip=anchors==null?new float[]{0,-43.0691F,0}:array(anchors.getAsJsonArray("spear_tip"));
        eyeU=(anchors==null?1128F:865F)/2048F;
        eyeV=(anchors==null?595F:410F)/2048F;
        for(JsonElement root:mesh.getAsJsonArray("bones"))
            roots.add(bone(root.getAsJsonObject()));
        for(String side:List.of("left","right"))
            armJoints.put(side+"_forearm",new YangJianArmKinematics(bones.get(side+"_forearm").pivot,bones.get(side+"_hand").pivot));
        for(Bone root:roots)if(findPath(root,"weapon",weaponPath))break;
        for(Bone root:roots)if(findPath(root,"head",headPath))break;
        // GeckoLib scans every JSON under animations/ on reload, so custom clip data lives separately.
        read(resources,"boss_animations/yang_jian.json").getAsJsonObject("clips").entrySet().forEach(entry->{
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

    void setupAnim(YangJian boss,float partial,float bodyYaw,float headYaw) {
        float age=boss.tickCount+partial;
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
        currentClip=clip;
        currentClipTime=time;
        Map<String,AnimationPlayback.Pose> sampled=new HashMap<>(bones.size());
        for(Bone b:bones.values()) {
            Map<String,float[][]> channels=clip.channels().getOrDefault(b.name,Map.of());
            float[] rotation=AnimationTrack.sample(channels.get("rotation"),time,0);
            YangJianArmKinematics joint=armJoints.get(b.name);
            if(joint!=null) {
                // Skill changes blend forward flexion/carry/twist, not unrelated
                // Euler axes. Even a valid endpoint pair can fold backwards in Euler space.
                rotation=joint.toJointAngles(new float[]{rotation[0]+b.baseRotation[0],
                    rotation[1]+b.baseRotation[1],rotation[2]+b.baseRotation[2]});
            }
            // In attacks the animation supplies counter-rotation; idle adds restrained target tracking.
            if(b.name.equals("head") && action==YangJian.IDLE) {
                float yawLimit=boss.phase()==3?55:boss.isApproaching()?12:25;
                rotation[1]+=Mth.clamp(headYaw,-yawLimit,yawLimit);
            }
            sampled.put(b.name,new AnimationPlayback.Pose(AnimationTrack.sample(channels.get("position"),time,0),
                rotation,AnimationTrack.sample(channels.get("scale"),time,1)));
        }
        float blend=name.startsWith("step_approach")?0
            :name.equals("thrust") || name.equals("counter")?1
            :name.contains("idle")?4:boss.phase()==3?2:2.75F;
        // No blend is applied at each hit: all segments of a combination are one uninterrupted clip.
        Map<String,AnimationPlayback.Pose> result=playback.computeIfAbsent(boss,ignored->new AnimationPlayback())
            .apply(action+":"+name,age,blend,sampled,BOUNDED_ARM_JOINTS);
        for(Bone b:bones.values()) {
            var value=result.get(b.name);
            b.position=value.position();b.rotation=value.rotation();b.scale=value.scale();
            YangJianArmKinematics joint=armJoints.get(b.name);
            if(joint!=null) {
                float[] euler=joint.toEulerAngles(b.rotation);
                b.rotation=new float[]{euler[0]-b.baseRotation[0],euler[1]-b.baseRotation[1],euler[2]-b.baseRotation[2]};
            }
        }
        weaponHidden=boss.weaponThrown();
        eyeGlow=boss.phase()==3?1:action==YangJian.THIRD_EYE_OPEN?Mth.clamp((elapsed-20)/35,0,1):0;
        YangJianHazard beam=boss.currentBeam();
        if(beam!=null) {
            aimHead(bodyYaw,beam.visualDirection(partial));
            beam.setVisualOrigin(new Vec3(Mth.lerp(partial,boss.xOld,boss.getX()),Mth.lerp(partial,boss.yOld,boss.getY()),
                Mth.lerp(partial,boss.zOld,boss.getZ())).add(eyePoint(bodyYaw)));
            eyeGlow=beam.warning(partial)?1+2*Mth.clamp(beam.age(partial)/Math.max(1,beam.windup()),0,1):2;
        } else if(boss.deathTime==0 && action!=YangJian.PHASE_CLEAR && !boss.isTransitioning()) {
            float pitch=boss.targetLookPitch(partial);
            if(boss.phase()==3 && action!=YangJian.DIVINE_SWEEP) {
                float yaw=Mth.rotLerp(partial,boss.yHeadRotO,boss.yHeadRot);
                aimHead(bodyYaw,Vec3.directionFromRotation(pitch,yaw));
            } else if(Math.abs(pitch)>.01F) {
                tiltAnimatedHead(bodyYaw,pitch);
            }
        }
        selectedWeapon=Mth.clamp(boss.weapon(),0,3);
        // Every P3 laser charges the third eye for its whole windup, including the frames
        // between clone passes and composite casts whose beam hazard is swapped mid-action.
        float laserCharge=laserWindupCharge(action,elapsed);
        if(laserCharge>0)eyeGlow=Math.max(eyeGlow,1F+EYE_CHARGE_GLOW*laserCharge);
        // The lightning starts at the tip after this length multiplier is applied.
        weaponLength=action==YangJian.DIVINE_SWEEP?YangJianSkill.divineSweepWeaponLength(elapsed):1F;
        weaponGrowth=action==YangJian.AXE_SUMMON && selectedWeapon==1?Mth.clamp((elapsed-24)/5F,.1F,1):1;
        if(action==YangJian.TRANSITION) {
            selectedWeapon=elapsed<YangJianTransition.SUMMON_START?0:1;
            weaponHidden=false;
            weaponGrowth=selectedWeapon==0?1:(float)YangJianTransition.weaponScale(elapsed);
        }
        heldWeaponTransform.identity();
        for(Bone bone:weaponPath)appendBone(heldWeaponTransform,bone);
    }

    RiggedBossRenderer.Mesh snapshot() {
        Map<String,AnimationPlayback.Pose> joints=new HashMap<>(bones.size());
        for(Bone bone:bones.values())
            joints.put(bone.name,new AnimationPlayback.Pose(bone.position,bone.rotation,bone.scale));
        RenderPose frame=new RenderPose(joints,weaponHidden,eyeGlow,selectedWeapon,weaponGrowth,weaponLength);
        return (pose,vertices,light,overlay,color)->draw(frame,pose,vertices,light,overlay,color);
    }

    private void draw(RenderPose frame,PoseStack pose,VertexConsumer buffer,int light,int overlay,int color) {
        pose.pushPose();pose.translate(0,1.5,0);
        for(Bone bone:roots)renderBone(bone,frame,pose,buffer,light,overlay,color);
        pose.popPose();
    }

    /** Draws the exact held weapon around its grip, with the spear tip along local -Y. */
    public void renderWeapon(PoseStack pose,VertexConsumer buffer,int light,int overlay) {
        Bone weapon=bones.get("weapon");
        if(weapon==null)throw new IllegalStateException("Yang Jian weapon bone is missing");
        drawFaces(weapon,pose,buffer,light,overlay,0xFFFFFFFF);
    }

    /** Converts the animated handle endpoint into an entity-relative world vector. */
    public Vec3 heldWeaponPoint(float bodyYaw,float x,float y,float z) {
        return weaponPoint(heldWeaponTransform,bodyYaw,x,y,z,weaponGrowth);
    }

    /** Exact animated forehead point in entity-relative world coordinates. */
    public Vec3 eyePoint(float bodyYaw) {
        Matrix4f transform=new Matrix4f();
        for(Bone b:headPath)appendBone(transform,b);
        return weaponPoint(transform,bodyYaw,thirdEye[0]/16F,thirdEye[1]/16F,thirdEye[2]/16F,1);
    }

    private Matrix4f headParent(float bodyYaw) {
        Matrix4f parent=new Matrix4f().rotateY((180-bodyYaw)*Mth.DEG_TO_RAD)
            .scale(-YangJianRenderer.MODEL_SCALE,-YangJianRenderer.MODEL_SCALE,YangJianRenderer.MODEL_SCALE);
        for(int i=0;i<headPath.size()-1;i++)appendBone(parent,headPath.get(i));
        return parent;
    }

    private void tiltAnimatedHead(float bodyYaw,float pitch) {
        if(headPath.isEmpty())return;
        Matrix4f parent=headParent(bodyYaw);
        Matrix4f animated=new Matrix4f(parent);
        appendBone(animated,headPath.get(headPath.size()-1));
        Vector3f forward=animated.transformDirection(new Vector3f(0,0,-1));
        float yaw=(float)Math.toDegrees(Math.atan2(-forward.x,forward.z));
        float animatedPitch=(float)-Math.toDegrees(Math.atan2(forward.y,Math.hypot(forward.x,forward.z)));
        float blend=Math.min(1,Math.abs(pitch)/8F);
        float limit=YangJianSkill.MAX_LOOK_PITCH;
        aimHead(parent,Vec3.directionFromRotation(Mth.clamp(Mth.lerp(blend,animatedPitch,pitch),-limit,limit),yaw));
    }

    private void aimHead(float bodyYaw,Vec3 direction) {
        if(!headPath.isEmpty())aimHead(headParent(bodyYaw),direction);
    }

    private void aimHead(Matrix4f parent,Vec3 direction) {
        Bone head=headPath.get(headPath.size()-1);
        float[] angles=YangJianBeamAim.rotation(parent,direction.x,direction.y,direction.z);
        head.rotation=new float[]{angles[0]-head.baseRotation[0],angles[1]-head.baseRotation[1],angles[2]-head.baseRotation[2]};
    }

    /** Each apparition has its own ray and local head pose; restore the body rig after drawing it. */
    RiggedBossRenderer.Mesh beamClone(float bodyYaw,YangJianHazard beam,float partial) {
        Bone head=bones.get("head");float[] original=head.rotation;float originalGlow=eyeGlow;
        try {
            if(beam!=null) {
                aimHead(bodyYaw,beam.visualDirection(partial));eyeGlow=2;
                // Clone ray origin is its floor position plus the common eye anchor.
                beam.setVisualOrigin(beam.start().add(0,-YangJian.THIRD_EYE_HEIGHT,0).add(eyePoint(bodyYaw)));
            }
            return snapshot();
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
        return weaponPoint(sampleWeapon(clip,Mth.clamp(tick/20F,0,clip.length())),bodyYaw,
            x,y,z,(float)YangJianTransition.weaponScale(tick));
    }

    record BladeEdge(Vec3 heel,Vec3 tip) {}

    BladeEdge bladeEdge(float bodyYaw,float ticksBack,float stretch) {
        Matrix4f transform=ticksBack==0?heldWeaponTransform:
            sampleWeapon(currentClip,Math.max(0,currentClipTime-ticksBack/20F));
        if (selectedWeapon==1) {
            return new BladeEdge(weaponPoint(transform,bodyYaw,0,-30F/16,0,weaponGrowth),
                weaponPoint(transform,bodyYaw,16F/16,-38F/16,0,weaponGrowth));
        }
        if (selectedWeapon==2) {
            return new BladeEdge(weaponPoint(transform,bodyYaw,0,-16F/16,0,weaponGrowth),
                weaponPoint(transform,bodyYaw,0,-39.6F/16,0,weaponGrowth));
        }
        float x=spearTip[0]/16F,y=spearTip[1]*stretch/16F,z=spearTip[2]/16F;
        return new BladeEdge(weaponPoint(transform,bodyYaw,x*.74F,y*.74F,z*.74F,1),
            weaponPoint(transform,bodyYaw,x,y,z,1));
    }

    private Matrix4f sampleWeapon(Clip clip,float time) {
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
        return transform;
    }

    private static Vec3 weaponPoint(Matrix4f transform,float bodyYaw,float x,float y,float z,float growth) {
        Vector3f point=new Vector3f(x*growth,y*growth,z*growth);
        transform.transformPosition(point);
        float scale=YangJianRenderer.MODEL_SCALE;
        point.mul(-scale,-scale,scale);
        new Quaternionf().rotationY((180-bodyYaw)*Mth.DEG_TO_RAD).transform(point);
        return new Vec3(point.x,point.y,point.z);
    }

    private void renderBone(Bone b,RenderPose frame,PoseStack pose,VertexConsumer buffer,int light,int overlay,int color) {
        boolean weapon=b.name.equals("weapon");
        if(weapon && frame.weaponHidden())return;
        AnimationPlayback.Pose joint=frame.joints().get(b.name);
        float[] position=joint.position(),rotation=joint.rotation(),scale=joint.scale();
        pose.pushPose();
        pose.translate((b.pivot[0]+position[0])/16F,(b.pivot[1]+position[1])/16F,(b.pivot[2]+position[2])/16F);
        pose.mulPose(new Quaternionf().rotationZYX((b.baseRotation[2]+rotation[2])*Mth.DEG_TO_RAD,
            (b.baseRotation[1]+rotation[1])*Mth.DEG_TO_RAD,(b.baseRotation[0]+rotation[0])*Mth.DEG_TO_RAD));
        pose.scale(scale[0],scale[1],scale[2]);
        if(weapon && frame.weaponLength()!=1F)pose.scale(1F,frame.weaponLength(),1F);
        if(weapon && frame.selectedWeapon()>0) {
            float growth=frame.weaponGrowth();
            pose.scale(growth,growth,growth);
            weapons.render(frame.selectedWeapon(),pose,buffer,light,overlay,color);
        } else {
            drawFaces(b,pose,buffer,light,overlay,color);
        }
        if(b.name.equals("head") && frame.eyeGlow()>0) {
            float x=thirdEye[0],y=thirdEye[1],z=thirdEye[2];
            float w=.11F+.045F*frame.eyeGlow(),h=.22F+.055F*frame.eyeGlow();
            float[][] corners={{x,y-h},{x-w,y},{x,y+h},{x+w,y}};
            for(int i=0;i<4;i++)buffer.addVertex(pose.last(),corners[i][0]/16F,corners[i][1]/16F,z/16F)
                .setColor(0xFFFFEC9C).setUv(eyeU,eyeV)
                .setOverlay(overlay).setLight(15728880).setNormal(pose.last(),0,0,-1);
        }
        for(Bone child:b.children)renderBone(child,frame,pose,buffer,light,overlay,color);
        pose.popPose();
    }

    private static void drawFaces(Bone b,PoseStack pose,VertexConsumer buffer,int light,int overlay,int color) {
        for(Face face:b.faces)for(float[] v:face.vertices())
            buffer.addVertex(pose.last(),v[0]/16F,v[1]/16F,v[2]/16F).setColor(color).setUv(v[3],v[4])
                .setOverlay(overlay).setLight(face.glow()?15728880:light)
                .setNormal(pose.last(),face.normal()[0],face.normal()[1],face.normal()[2]);
    }
}
