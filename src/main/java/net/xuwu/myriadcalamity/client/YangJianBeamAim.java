package net.xuwu.myriadcalamity.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Solves the head's local pitch/yaw from the rendered parent's complete world transform. */
public final class YangJianBeamAim {
    private YangJianBeamAim() {}

    /**
     * Returns pitch, yaw and roll in degrees for a head whose forward axis is local -Z.
     * The parent matrix includes every animated ancestor and the renderer's mirror scale,
     * but excludes the head's own rotation. The supplied direction is in world space.
     */
    public static float[] rotation(Matrix4f parentWorld,double dx,double dy,double dz) {
        double largest=Math.max(Math.abs(dx),Math.max(Math.abs(dy),Math.abs(dz)));
        if(parentWorld==null || !Double.isFinite(largest) || largest==0) return new float[3];

        // Scaling before the float conversion also accepts very long or very short beams.
        Vector3f local=new Vector3f((float)(dx/largest),(float)(dy/largest),(float)(dz/largest));
        new Matrix4f(parentWorld).invert().transformDirection(local);
        if(!Float.isFinite(local.x) || !Float.isFinite(local.y) || !Float.isFinite(local.z)) {
            return new float[3];
        }
        double horizontal=Math.hypot(local.x,local.z);
        if(horizontal==0 && local.y==0) return new float[3];

        // rotationZYX(0, yaw, pitch) maps -Z to (-sin(yaw)*cos(pitch),
        // sin(pitch), -cos(yaw)*cos(pitch)). Inverting the parent first keeps this
        // correct under render mirroring, body rotation, and animated torso lean.
        float pitch=(float)Math.toDegrees(Math.atan2(local.y,horizontal));
        float yaw=(float)Math.toDegrees(Math.atan2(-local.x,-local.z));
        return new float[]{pitch,yaw,0};
    }
}
