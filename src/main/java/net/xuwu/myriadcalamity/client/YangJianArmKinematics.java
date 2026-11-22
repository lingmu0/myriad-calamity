package net.xuwu.myriadcalamity.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Converts rendered elbow rotations to the rig's physical flex/carry/twist coordinates.
 * All public angles are degrees. The caller supplies rotations including the forearm's rest
 * rotation, and removes that rest rotation after converting back if the rig has one.
 */
public final class YangJianArmKinematics {
    private static final float RAD=(float)(Math.PI/180);
    private static final float DEG=(float)(180/Math.PI);
    private final Vector3f upper,lower,front,hinge;
    private final Quaternionf base;

    /** @param upper shoulder-to-elbow pivot in the shoulder's local coordinates
     *  @param lower elbow-to-wrist pivot in the forearm's local coordinates */
    public YangJianArmKinematics(float[] upper,float[] lower) {
        this.upper=direction(upper);
        this.lower=direction(lower);
        Vector3f forward=new Vector3f(0,0,-1);
        front=forward.sub(new Vector3f(this.upper).mul(forward.dot(this.upper)));
        if(front.lengthSquared()<1e-10F)
            throw new IllegalArgumentException("The upper arm must define a forward hinge plane");
        front.normalize();
        hinge=new Vector3f(this.upper).cross(front).normalize();
        base=new Quaternionf().rotationTo(this.lower,this.upper).normalize();
    }

    /** Decode the source pose without imposing anatomical limits on authored values. */
    public float[] toJointAngles(float[] eulerDegrees) {
        requireAngles(eulerDegrees);
        Quaternionf rotation=new Quaternionf().rotationZYX(
            eulerDegrees[2]*RAD,eulerDegrees[1]*RAD,eulerDegrees[0]*RAD).normalize();
        Vector3f direction=rotation.transform(new Vector3f(lower)).normalize();
        float flex=(float)Math.atan2(direction.dot(front),direction.dot(upper))*DEG;
        float carry=-(float)Math.asin(clamp(direction.dot(hinge),-1,1))*DEG;
        Quaternionf untwisted=rotation(flex,carry,0);
        Quaternionf residual=new Quaternionf(untwisted).conjugate().mul(rotation).normalize();
        // q and -q are the same orientation. Choose the principal SO(3) rotation,
        // matching SciPy's as_rotvec(), before projecting twist onto the raw limb.
        double sign=residual.w<0?-1:1;
        double x=residual.x*sign,y=residual.y*sign,z=residual.z*sign,w=residual.w*sign;
        double length=Math.sqrt(x*x+y*y+z*z);
        float twist=length<1e-10?0:(float)(2*Math.atan2(length,w)
            *(x*lower.x+y*lower.y+z*lower.z)/length*DEG);
        return new float[]{flex,carry,twist};
    }

    /** Encode a blended physical pose; protect the hinge even at interrupted transitions. */
    public float[] toEulerAngles(float[] joint) {
        requireAngles(joint);
        Quaternionf q=rotation(clamp(joint[0],0,135),clamp(joint[1],-13,13),joint[2]);
        // Extract Rz * Ry * Rx explicitly, with a deterministic gimbal-lock branch.
        // Double intermediates avoid amplifying float cancellation near +/-90 pitch.
        double norm=Math.sqrt(q.x*q.x+(double)q.y*q.y+(double)q.z*q.z+(double)q.w*q.w);
        double x=q.x/norm,y=q.y/norm,z=q.z/norm,w=q.w/norm;
        double m00=1-2*(y*y+z*z),m10=2*(x*y+w*z),m20=2*(x*z-w*y);
        double m11=1-2*(x*x+z*z),m12=2*(y*z-w*x),m21=2*(y*z+w*x),m22=1-2*(x*x+y*y);
        double cosine=Math.hypot(m00,m10);
        double pitch=Math.atan2(-m20,cosine);
        double roll,yaw;
        if(cosine<1e-6) {
            pitch=Math.copySign(Math.PI/2,-m20);
            roll=Math.atan2(-m12,m11);
            yaw=0;
        } else {
            roll=Math.atan2(m21,m22);
            yaw=Math.atan2(m10,m00);
        }
        return new float[]{(float)(roll*DEG),(float)(pitch*DEG),(float)(yaw*DEG)};
    }

    private Quaternionf rotation(float flex,float carry,float twist) {
        return new Quaternionf().rotationAxis(flex*RAD,hinge)
            .mul(new Quaternionf().rotationAxis(carry*RAD,front))
            .mul(base).mul(new Quaternionf().rotationAxis(twist*RAD,lower)).normalize();
    }

    private static Vector3f direction(float[] value) {
        requireAngles(value);
        Vector3f result=new Vector3f(value[0],value[1],value[2]);
        if(!Float.isFinite(result.lengthSquared()) || result.lengthSquared()<1e-10F)
            throw new IllegalArgumentException("Arm pivots must be finite nonzero vectors");
        return result.normalize();
    }

    private static void requireAngles(float[] value) {
        if(value==null || value.length!=3 || !Float.isFinite(value[0])
                || !Float.isFinite(value[1]) || !Float.isFinite(value[2]))
            throw new IllegalArgumentException("Expected three finite arm coordinates");
    }

    private static float clamp(float value,float min,float max) {
        return Math.max(min,Math.min(max,value));
    }
}
