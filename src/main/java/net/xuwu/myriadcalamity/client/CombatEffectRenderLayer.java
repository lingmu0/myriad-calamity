package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.xuwu.myriadcalamity.MyriadCalamity;

/** Preserves the original position/color path and adapts the same quads for active shader packs. */
final class CombatEffectRenderLayer implements VertexConsumer {
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final class Layers {
        static final RenderType ORIGINAL = RenderTypes.debugQuads();
        // Shader loaders recognize the beacon pass as fullbright. It preserves the low alpha
        // values used by soft glow and fog, which the emissive entity pass can cut away.
        static final RenderType SHADERS = RenderTypes.beaconBeam(
            MyriadCalamity.id("textures/effect/combat_quad.png"), true);
    }

    private final VertexConsumer output;
    private final float[] positions = new float[12];
    private final int[] colors = new int[4];
    private int corner;

    private CombatEffectRenderLayer(VertexConsumer output) {
        this.output = output;
    }

    static RenderType type(ShaderPackSupport.Pass pass) {
        return pass == ShaderPackSupport.Pass.COLOR ? Layers.SHADERS : Layers.ORIGINAL;
    }

    static VertexConsumer adapt(VertexConsumer output, ShaderPackSupport.Pass pass) {
        return pass == ShaderPackSupport.Pass.COLOR ? new CombatEffectRenderLayer(output) : output;
    }

    @Override public VertexConsumer addVertex(float x, float y, float z) {
        int index = corner * 3;
        positions[index] = x;
        positions[index + 1] = y;
        positions[index + 2] = z;
        return this;
    }

    @Override public VertexConsumer setColor(int argb) {
        colors[corner++] = argb;
        if (corner == 4) {
            writeQuad();
            corner = 0;
        }
        return this;
    }

    @Override public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        return setColor(alpha << 24 | red << 16 | green << 8 | blue);
    }

    private void writeQuad() {
        float ax = positions[3] - positions[0], ay = positions[4] - positions[1], az = positions[5] - positions[2];
        float bx = positions[6] - positions[0], by = positions[7] - positions[1], bz = positions[8] - positions[2];
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float length = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 1E-8F && Float.isFinite(length)) {
            nx /= length;
            ny /= length;
            nz /= length;
        } else {
            nx = 0;
            ny = 1;
            nz = 0;
        }
        // The beacon layer culls back faces. Send both windings for effects visible from either side.
        writeFace(0, 1, nx, ny, nz);
        writeFace(3, -1, -nx, -ny, -nz);
    }

    private void writeFace(int first, int step, float nx, float ny, float nz) {
        for (int i = 0; i < 4; i++) {
            int corner = first + step * i;
            int at = corner * 3;
            float u = corner == 1 || corner == 2 ? 1 : 0;
            float v = corner >= 2 ? 1 : 0;
            output.addVertex(positions[at], positions[at + 1], positions[at + 2])
                .setColor(colors[corner]).setUv(u, v).setLight(FULL_BRIGHT).setNormal(nx, ny, nz);
        }
    }

    @Override public VertexConsumer setUv(float u, float v) { return this; }
    @Override public VertexConsumer setUv1(int u, int v) { return this; }
    @Override public VertexConsumer setUv2(int u, int v) { return this; }
    @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    @Override public VertexConsumer setLineWidth(float width) { return this; }
}
