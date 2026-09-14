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
import net.minecraft.server.packs.resources.ResourceManager;
import net.xuwu.myriadcalamity.MyriadCalamity;

/** P2 weapon meshes share the approved armor atlas and the existing right-hand grip. */
public final class YangJianWeapons {
    private record Face(float[][] vertices,float[] normal,boolean glow) {}
    private final Map<String,List<Face>> meshes=new HashMap<>();
    public YangJianWeapons(ResourceManager resources) {
        try(var stream=resources.getResourceOrThrow(MyriadCalamity.id("mesh/yang_jian_weapons.json")).open();
            var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("weapons").entrySet().forEach(entry->{
                List<Face> faces=new ArrayList<>();
                for(JsonElement value:entry.getValue().getAsJsonObject().getAsJsonArray("faces")) {
                    JsonObject f=value.getAsJsonObject();float[][] vertices=new float[4][];
                    for(int i=0;i<4;i++)vertices[i]=array(f.getAsJsonArray("v").get(i).getAsJsonArray());
                    faces.add(new Face(vertices,array(f.getAsJsonArray("n")),f.get("glow").getAsBoolean()));
                }
                meshes.put(entry.getKey(),faces);
            });
        } catch(IOException error) {throw new IllegalStateException("Cannot load Yang Jian P2 weapons",error);}
    }
    private static float[] array(JsonArray data) {
        float[] values=new float[data.size()];for(int i=0;i<values.length;i++)values[i]=data.get(i).getAsFloat();return values;
    }
    public void renderSword(PoseStack pose,VertexConsumer buffer,int light,int overlay,int color) {render("sword",pose,buffer,light,overlay,color);}
    public void render(int weapon,PoseStack pose,VertexConsumer buffer,int light,int overlay,int color) {
        render(switch(weapon) {case 1->"axe";case 2->"sword";case 3->"whip";default->throw new IllegalArgumentException("Unknown P2 weapon "+weapon);},pose,buffer,light,overlay,color);
    }
    private void render(String weapon,PoseStack pose,VertexConsumer buffer,int light,int overlay,int color) {
        List<Face> faces=meshes.get(weapon);
        if(faces==null)throw new IllegalStateException("Missing P2 weapon "+weapon);
        for(Face face:faces)for(float[] vertex:face.vertices())
            buffer.addVertex(pose.last(),vertex[0]/16F,vertex[1]/16F,vertex[2]/16F)
                .setColor(color).setUv(vertex[3],vertex[4]).setOverlay(overlay).setLight(face.glow()?15728880:light)
                .setNormal(pose.last(),face.normal()[0],face.normal()[1],face.normal()[2]);
    }
}
