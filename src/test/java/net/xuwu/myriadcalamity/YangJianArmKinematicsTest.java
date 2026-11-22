package net.xuwu.myriadcalamity;

import net.xuwu.myriadcalamity.client.YangJianArmKinematics;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Pure JOML checks; no Minecraft class loading or game launch is needed. */
public final class YangJianArmKinematicsTest {
    private static int checks;
    private static final float[][] UPPER={{-1.5F,6.8F,0},{1.5F,6.8F,0}};
    private static final float[][] LOWER={{-.5F,7.4F,-.625F},{2.85F,4.3F,-3.375F}};
    // Independent offline forearm_matrix(side,[58,-7,83]) fixtures, listed by row.
    private static final float[][][] FIXTURES={
        {{.301586595F,-.133551596F,.944038927F},
         {-.823617726F,.462308883F,.328518397F},
         {-.480311739F,-.876603940F,.029430700F}},
        {{.152317829F,.540371103F,.827525438F},
         {-.484832665F,.770480462F,-.413880592F},
         {-.861241294F,-.338169970F,.379347473F}}
    };

    private static void check(boolean value,String message) {
        checks++;
        if(!value)throw new AssertionError(message);
    }

    private static Quaternionf rotation(float[] angles) {
        return new Quaternionf().rotationZYX((float)Math.toRadians(angles[2]),
            (float)Math.toRadians(angles[1]),(float)Math.toRadians(angles[0]));
    }

    private static Vector3f vector(float[] values) {
        return new Vector3f(values[0],values[1],values[2]);
    }

    private static void near(float actual,float expected,String message) {
        check(Math.abs(actual-expected)<.003F,message+": "+actual+" != "+expected);
    }

    /** Measure actual wrist geometry independently of the production decoder. */
    private static void anatomical(int side,float[] euler,String message) {
        Vector3f upper=vector(UPPER[side]).normalize();
        Vector3f front=new Vector3f(0,0,-1);
        Vector3f hinge=new Vector3f(upper).cross(front).normalize();
        Vector3f direction=rotation(euler).transform(vector(LOWER[side])).normalize();
        double flex=Math.toDegrees(Math.atan2(direction.dot(front),direction.dot(upper)));
        double lateral=Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,direction.dot(hinge)))));
        check(flex>=-.003 && flex<=135.003,message+" keeps forward flexion: "+flex);
        check(Math.abs(lateral)<=13.003,message+" keeps lateral alignment: "+lateral);
    }

    private static void sameOrientation(float[] first,float[] second,String message) {
        Quaternionf a=rotation(first),b=rotation(second);
        for(Vector3f axis:new Vector3f[]{new Vector3f(1,0,0),new Vector3f(0,1,0),new Vector3f(0,0,1)}) {
            check(a.transform(new Vector3f(axis)).distance(b.transform(new Vector3f(axis)))<.00002F,message);
        }
    }

    public static void main(String[] args) {
        for(int side=0;side<2;side++) {
            YangJianArmKinematics arm=new YangJianArmKinematics(UPPER[side],LOWER[side]);
            for(float flex:new float[]{0,2,35,70,110,130,135})
                for(float carry:new float[]{-13,-12,-8,0,8,12,13})
                    for(float twist:new float[]{-100,-45,0,63,100}) {
                        float[] joint={flex,carry,twist};
                        float[] euler=arm.toEulerAngles(joint),decoded=arm.toJointAngles(euler);
                        for(int i=0;i<3;i++)near(decoded[i],joint[i],"Physical coordinates round trip on side "+side);
                        sameOrientation(euler,arm.toEulerAngles(decoded),"Euler round trip preserves the entire orientation");
                        anatomical(side,euler,"Every authored joint sample");
                    }

            Quaternionf fixture=rotation(arm.toEulerAngles(new float[]{58,-7,83}));
            for(int column=0;column<3;column++) {
                Vector3f axis=new Vector3f(column==0?1:0,column==1?1:0,column==2?1:0);
                Vector3f actual=fixture.transform(axis);
                Vector3f expected=new Vector3f(FIXTURES[side][0][column],FIXTURES[side][1][column],FIXTURES[side][2][column]);
                check(actual.distance(expected)<.000002F,"Runtime agrees with the independently generated Python fixture");
            }

            for(float flex:new float[]{2,45,100,130}) {
                Vector3f origin=rotation(arm.toEulerAngles(new float[]{flex,8,0})).transform(vector(LOWER[side]));
                for(int twist=-100;twist<=100;twist+=5) {
                    Vector3f point=rotation(arm.toEulerAngles(new float[]{flex,8,twist})).transform(vector(LOWER[side]));
                    check(point.distance(origin)<.00003F,"Pronation cannot move the wrist pivot");
                }
            }

            float[][] poses={{2,-12,-100},{130,12,100},{8,12,100},{125,-12,-100},{65,0,0}};
            for(float[] from:poses)for(float[] to:poses)for(int step=0;step<=100;step++) {
                float t=step/100F;
                float[] blended=new float[3];
                for(int i=0;i<3;i++)blended[i]=from[i]+(to[i]-from[i])*t;
                anatomical(side,arm.toEulerAngles(blended),"Blending distant poses");
            }

            // Out-of-range source rotations must remain observable to diagnostics.
            check(arm.toJointAngles(new float[]{80,0,0})[0]<0,"Decoding does not hide a backward-authored elbow");
            for(float[] unsafe:new float[][]{{-30,25,15},{170,-25,-70}})
                anatomical(side,arm.toEulerAngles(unsafe),"Encoding constrains interrupted/out-of-range blends");

            float[] source={57,8,-33};
            float[] euler=arm.toEulerAngles(source);
            arm.toJointAngles(euler);
            check(source[0]==57 && source[1]==8 && source[2]==-33,"Input arrays remain unchanged");
        }
        System.out.println("Yang Jian arm kinematics: "+checks+" checks passed.");
    }
}
