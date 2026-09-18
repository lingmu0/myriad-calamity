package net.xuwu.myriadcalamity;

import net.xuwu.myriadcalamity.client.YangJianBeamAim;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Checks actual rendered -Z alignment without loading any Minecraft classes. */
public final class YangJianBeamAimTest {
    private static int checks;

    private static void check(boolean condition,String message) {
        checks++;
        if(!condition) throw new AssertionError(message);
    }

    private static float radians(double degrees) { return (float)Math.toRadians(degrees); }

    private static Vector3f renderedForward(Matrix4f parent,float[] angles) {
        return new Matrix4f(parent)
            .rotateZYX(radians(angles[2]),radians(angles[1]),radians(angles[0]))
            .transformDirection(new Vector3f(0,0,-1)).normalize();
    }

    private static void aligned(Matrix4f parent,Vector3f target,String message) {
        Matrix4f before=new Matrix4f(parent);
        float[] angles=YangJianBeamAim.rotation(parent,target.x,target.y,target.z);
        Vector3f actual=renderedForward(parent,angles);
        check(actual.distance(new Vector3f(target).normalize())<.00001F,message);
        check(parent.equals(before),"Aiming must not mutate the shared parent transform");
    }

    private static Vector3f direction(double heading,double elevation) {
        double yaw=Math.toRadians(heading),pitch=Math.toRadians(elevation);
        return new Vector3f((float)(Math.cos(yaw)*Math.cos(pitch)),
            (float)Math.sin(pitch),(float)(Math.sin(yaw)*Math.cos(pitch)));
    }

    private static Matrix4f bodyParent(double heading) {
        return new Matrix4f().translate(140,75,-29)
            .rotateY(radians(180-heading)).scale(-1,-1,1);
    }

    private static void invalid(Matrix4f parent,double x,double y,double z,String message) {
        float[] angles=YangJianBeamAim.rotation(parent,x,y,z);
        check(angles.length==3 && angles[0]==0 && angles[1]==0 && angles[2]==0,message);
    }

    public static void main(String[] args) {
        for(int heading=-180;heading<=180;heading+=15) {
            Matrix4f parent=bodyParent(heading);
            for(int targetHeading=-180;targetHeading<180;targetHeading+=30) {
                for(int pitch:new int[]{-80,-35,0,35,80}) {
                    aligned(parent,direction(targetHeading,pitch),
                        "Mirrored head follows the beam at body "+heading+", target "+targetHeading+", pitch "+pitch);
                }
            }
        }

        Matrix4f leaningParent=bodyParent(67)
            .translate(.2F,1.1F,-.3F).rotateZYX(radians(23),radians(-31),radians(19))
            .scale(.85F,1.2F,1.05F).translate(0,-.8F,0)
            .rotateZYX(radians(-11),radians(42),radians(-27));
        for(int heading=-180;heading<180;heading+=15) {
            for(int pitch:new int[]{-90,-65,0,65,90}) {
                aligned(leaningParent,direction(heading,pitch),"Animated ancestors preserve world beam alignment");
            }
        }

        for(double heading:new double[]{-180.001,-180,-179.999,179.999,180,180.001,359.999,360.001}) {
            aligned(bodyParent(heading),direction(heading+45,-20),"Body heading wrap keeps beam and head aligned");
        }
        Matrix4f identity=new Matrix4f();
        for(double yaw:new double[]{-180.001,-180,-179.999,179.999,180,180.001}) {
            Vector3f target=new Matrix4f().rotateY(radians(yaw)).transformDirection(new Vector3f(0,0,-1));
            aligned(identity,target,"Local head yaw wraps through 180 degrees without a direction jump");
        }

        Matrix4f original=bodyParent(-63).rotateX(radians(16));
        Matrix4f clone=bodyParent(121).rotateZ(radians(-24));
        Vector3f target=direction(37,-22);
        float[] originalAim=YangJianBeamAim.rotation(original,target.x,target.y,target.z);
        float[] cloneAim=YangJianBeamAim.rotation(clone,target.x,target.y,target.z);
        check(Math.abs(originalAim[1]-cloneAim[1])>30,"The clone solves its own parent orientation");
        check(renderedForward(original,originalAim).distance(target)<.00001F,"Original stays aligned after solving a clone");
        check(renderedForward(clone,cloneAim).distance(target)<.00001F,"Clone points at its beam from its distinct pose");
        originalAim[0]=123;
        check(YangJianBeamAim.rotation(clone,target.x,target.y,target.z)[0]==cloneAim[0],
            "Returned rotations have no shared mutable state between entities");

        float[] huge=YangJianBeamAim.rotation(identity,Double.MAX_VALUE,Double.MAX_VALUE,-Double.MAX_VALUE);
        float[] tiny=YangJianBeamAim.rotation(identity,Double.MIN_VALUE,Double.MIN_VALUE,-Double.MIN_VALUE);
        Vector3f diagonal=new Vector3f(1,1,-1).normalize();
        check(renderedForward(identity,huge).distance(diagonal)<.00001F,"Huge finite directions do not overflow");
        check(renderedForward(identity,tiny).distance(diagonal)<.00001F,"Tiny finite directions do not underflow");
        invalid(identity,0,0,0,"A coincident beam target has a neutral fallback");
        invalid(identity,Double.NaN,0,1,"NaN input has a neutral fallback");
        invalid(identity,1,Double.POSITIVE_INFINITY,0,"Infinite input has a neutral fallback");
        invalid(new Matrix4f().scale(0),1,0,-1,"Singular parent transforms cannot produce NaN angles");
        invalid(new Matrix4f().rotateY(Float.NaN),1,0,-1,"Malformed parent transforms have a neutral fallback");
        invalid(null,1,0,-1,"Missing parent transforms have a neutral fallback");
        System.out.println("Yang Jian beam aim: "+checks+" checks passed");
    }
}
