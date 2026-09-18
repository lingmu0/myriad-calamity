package net.xuwu.myriadcalamity;

import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.entity.YangJianDefense;

/** Boundary and reflection rules for Yang Jian's remote guard. */
public final class YangJianDefenseTest {
    private static int checks;
    private static void check(boolean condition,String message) {
        checks++;
        if(!condition)throw new AssertionError(message);
    }
    private static boolean near(double a,double b) { return Math.abs(a-b)<1.0E-6; }

    public static void main(String[] args) {
        check(!YangJianDefense.isRemote(15.999,0,true),"An origin just inside four blocks is not remote");
        check(YangJianDefense.isRemote(16,0,true),"A direct projectile at exactly four blocks is remote");
        check(YangJianDefense.isRemote(0,16,true),"A distant actual source establishes remote range");
        check(!YangJianDefense.isRemote(16,16,false),"Unsupported environmental damage cannot trigger automatic guard");
        check(YangJianDefense.isRemote(Double.NaN,16,true),"A valid actual distance still works when projectile ownership is absent");
        check(!YangJianDefense.isRemote(Double.NaN,Double.POSITIVE_INFINITY,true),"Malformed distances are rejected");

        Vec3 incoming=new Vec3(1.25,.4,-2.5),reflected=YangJianDefense.reflected(incoming);
        check(near(reflected.x,-1.25) && near(reflected.y,-.4) && near(reflected.z,2.5),"Reflection reverses every velocity component");
        check(near(reflected.length(),incoming.length()),"Reflection preserves projectile speed");
        check(YangJianDefense.reflected(Vec3.ZERO)==Vec3.ZERO,"A stationary projectile receives the zero fallback");
        check(YangJianDefense.STUN_TICKS==20,"The guard stun lasts exactly one second");
        System.out.println("Yang Jian defense: "+checks+" checks passed");
    }
}
