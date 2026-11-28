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
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.entity.DivineDragon;
import net.xuwu.myriadcalamity.entity.DivineDragonMath;
import org.joml.Quaternionf;

/** Articulated 87-bone Blockbench mesh (V5-Opt + inverted-hull glow shells) and genuine Sekiro c5200 animator. */
public final class DivineDragonModel {
    private record Face(float[][] vertices, float[] normal, boolean glow, boolean shell) {}

    private static final class Bone {
        String name;
        float[] pivot, baseRotation;
        float[] position = {0, 0, 0}, rotation = {0, 0, 0}, scale = {1, 1, 1};
        final List<Face> faces = new ArrayList<>();
        final List<Bone> children = new ArrayList<>();
    }

    private record Clip(float length, boolean loop, Map<String, Map<String, float[][]>> channels) {}

    private final List<Bone> roots = new ArrayList<>();
    private final Map<String, Bone> bones = new HashMap<>();
    private final Map<String, Clip> clips = new HashMap<>();
    private final Map<DivineDragon, AnimationPlayback> playback = new WeakHashMap<>();

    private float renderAlpha = 1.0F;
    private float glowIntensity = 1.0F;
    private float shellPulse = 1.0F;

    public DivineDragonModel(ResourceManager resources) {
        JsonObject mesh = read(resources, "mesh/divine_dragon.json");
        for (JsonElement root : mesh.getAsJsonArray("bones")) {
            roots.add(parseBone(root.getAsJsonObject()));
        }
        JsonObject animFile = read(resources, "boss_animations/divine_dragon.json");
        animFile.getAsJsonObject("clips").entrySet().forEach(entry -> {
            JsonObject data = entry.getValue().getAsJsonObject();
            Map<String, Map<String, float[][]>> channels = new HashMap<>();
            data.getAsJsonObject("bones").entrySet().forEach(b -> {
                if (!bones.containsKey(b.getKey())) return;
                Map<String, float[][]> tracks = new HashMap<>();
                b.getValue().getAsJsonObject().entrySet().forEach(c -> {
                    JsonArray keys = c.getValue().getAsJsonArray();
                    float[][] values = new float[keys.size()][];
                    for (int i = 0; i < values.length; i++) values[i] = array(keys.get(i).getAsJsonArray());
                    tracks.put(c.getKey(), values);
                });
                channels.put(b.getKey(), tracks);
            });
            clips.put(entry.getKey(), new Clip(data.get("length").getAsFloat(), data.get("loop").getAsBoolean(), channels));
        });
    }

    private static JsonObject read(ResourceManager resources, String path) {
        try (var stream = resources.getResourceOrThrow(MyriadCalamity.id(path)).open();
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load Divine Dragon asset " + path, ex);
        }
    }

    private static float[] array(JsonArray data) {
        float[] result = new float[data.size()];
        for (int i = 0; i < result.length; i++) result[i] = data.get(i).getAsFloat();
        return result;
    }

    private Bone parseBone(JsonObject data) {
        Bone b = new Bone();
        b.name = data.get("name").getAsString();
        b.pivot = array(data.getAsJsonArray("pivot"));
        b.baseRotation = array(data.getAsJsonArray("rotation"));
        for (JsonElement entry : data.getAsJsonArray("faces")) {
            JsonObject f = entry.getAsJsonObject();
            float[][] vertices = new float[4][];
            for (int i = 0; i < 4; i++) vertices[i] = array(f.getAsJsonArray("v").get(i).getAsJsonArray());
            boolean glow = f.has("glow") && f.get("glow").getAsBoolean();
            boolean shell = f.has("shell") && f.get("shell").getAsBoolean();
            b.faces.add(new Face(vertices, array(f.getAsJsonArray("n")), glow, shell));
        }
        for (JsonElement child : data.getAsJsonArray("children")) {
            b.children.add(parseBone(child.getAsJsonObject()));
        }
        bones.put(b.name, b);
        return b;
    }

    void setupAnim(DivineDragon boss, float partial) {
        float age = boss.tickCount + partial;
        int action = boss.action();
        String clipName;
        float time;

        shellPulse = 0.78F + 0.22F * Mth.sin(age * 0.14F);
        if (boss.isDissolving()) {
            clipName = "death_dissolve";
            float dissolveAge = boss.dissolveAge(partial);
            time = DivineDragon.animationTimeSeconds(action, boss.grabState(), 0.0F, age, true, dissolveAge);
            renderAlpha = DivineDragonMath.dissolveAlpha(dissolveAge);
            glowIntensity = Math.max(0.0F, 1.0F - dissolveAge / 64.0F);
        } else {
            renderAlpha = 1.0F;
            glowIntensity = 1.0F;
            clipName = DivineDragon.clipNameForAction(action);
            float actAge = Math.max(0.0F, boss.actionAge(partial));
            time = DivineDragon.animationTimeSeconds(action, boss.grabState(), actAge, age, false, 0.0F);
        }

        Clip clip = clips.getOrDefault(clipName, clips.get("idle"));
        if (clip == null) return;
        time = clip.loop() ? time % clip.length() : Math.min(time, clip.length());

        Map<String, AnimationPlayback.Pose> sampled = new HashMap<>(bones.size());
        for (Bone b : bones.values()) {
            Map<String, float[][]> channels = clip.channels().getOrDefault(b.name, Map.of());
            float[] pos = AnimationTrack.sample(channels.get("position"), time, 0.0F);
            float[] rot = AnimationTrack.sample(channels.get("rotation"), time, 0.0F);
            float[] scl = AnimationTrack.sample(channels.get("scale"), time, 1.0F);
            sampled.put(b.name, new AnimationPlayback.Pose(pos, rot, scl));
        }

        // Generous weighty smoothstep transition back into Idle (0.80s), with stable "0:idle" key so pre-fight never twitches!
        float blend = boss.isDissolving() ? 10.0F : (action == DivineDragon.IDLE ? 16.0F : 6.0F);
        String clipKey = (action == DivineDragon.IDLE && !boss.isDissolving())
            ? "0:idle"
            : (action + ":" + clipName + ":" + boss.actionStartTick());
        Map<String, AnimationPlayback.Pose> blended = playback
            .computeIfAbsent(boss, ignored -> new AnimationPlayback())
            .apply(clipKey, age, blend, sampled);

        for (Bone b : bones.values()) {
            AnimationPlayback.Pose p = blended.get(b.name);
            if (p != null) {
                b.position = p.position();
                b.rotation = p.rotation();
                b.scale = p.scale();
            }
        }
    }

    private record Frame(Map<String, AnimationPlayback.Pose> joints, float alpha, float glow, float pulse) {}

    record Geometry(RiggedBossRenderer.Mesh body, RiggedBossRenderer.Mesh shell) {}

    Geometry snapshot() {
        Map<String, AnimationPlayback.Pose> joints = new HashMap<>(bones.size());
        for (Bone bone : bones.values()) {
            joints.put(bone.name, new AnimationPlayback.Pose(bone.position, bone.rotation, bone.scale));
        }
        Frame frame = new Frame(joints, renderAlpha, glowIntensity, shellPulse);
        return new Geometry(
            (pose, vertices, light, overlay, tint) -> draw(frame, pose, vertices, light, overlay, tint, false),
            (pose, vertices, light, overlay, tint) -> draw(frame, pose, vertices, light, overlay, tint, true));
    }

    private void draw(Frame frame, PoseStack pose, VertexConsumer buffer, int light, int overlay, int color,
                      boolean shellsOnly) {
        float alpha = (color >>> 24) * frame.alpha();
        if (shellsOnly) alpha *= frame.glow() * .24F * frame.pulse();
        int opacity = Math.round(alpha);
        if (opacity <= (shellsOnly ? 5 : 3)) return;
        int tint = (color & 0x00FFFFFF) | opacity << 24;
        pose.pushPose();
        pose.translate(0.0, 1.5, 0.0);
        Quaternionf rotation = new Quaternionf();
        for (Bone root : roots) renderBone(root, frame, pose, rotation, buffer, light, overlay, tint, shellsOnly);
        pose.popPose();
    }

    private void renderBone(Bone b, Frame frame, PoseStack pose, Quaternionf rotation,
                            VertexConsumer buffer, int light, int overlay, int color, boolean shellsOnly) {
        AnimationPlayback.Pose joint = frame.joints().get(b.name);
        pose.pushPose();
        pose.translate((b.pivot[0] + joint.position()[0]) / 16.0F,
            (b.pivot[1] + joint.position()[1]) / 16.0F,
            (b.pivot[2] + joint.position()[2]) / 16.0F);
        pose.mulPose(rotation.rotationZYX(
            (b.baseRotation[2] + joint.rotation()[2]) * Mth.DEG_TO_RAD,
            (b.baseRotation[1] + joint.rotation()[1]) * Mth.DEG_TO_RAD,
            (b.baseRotation[0] + joint.rotation()[0]) * Mth.DEG_TO_RAD));
        pose.scale(joint.scale()[0], joint.scale()[1], joint.scale()[2]);
        for (Face face : b.faces) {
            if (face.shell() != shellsOnly) continue;
            int faceLight = (shellsOnly || face.glow() && frame.glow() > .2F) ? 15728880 : light;
            for (float[] v : face.vertices()) {
                buffer.addVertex(pose.last().pose(), v[0] / 16.0F, v[1] / 16.0F, v[2] / 16.0F)
                    .setColor(color).setUv(v[3], v[4]).setOverlay(overlay).setLight(faceLight)
                    .setNormal(pose.last(), face.normal()[0], face.normal()[1], face.normal()[2]);
            }
        }
        for (Bone child : b.children) renderBone(child, frame, pose, rotation, buffer, light, overlay, color, shellsOnly);
        pose.popPose();
    }
}
