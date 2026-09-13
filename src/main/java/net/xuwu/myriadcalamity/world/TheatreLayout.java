package net.xuwu.myriadcalamity.world;

/** All measurements are relative to the centre of the flat dance floor. */
public final class TheatreLayout {
    public static final int RADIUS=20;
    public static final int[][] SEALS={{10,0},{-10,0},{0,10},{0,-10},{7,7},{-7,7},{7,-7},{-7,-7}};
    private TheatreLayout() {}
    public static boolean floor(int x,int z) { return x*x+z*z<=RADIUS*RADIUS; }
    public static boolean seal(int x,int z) {
        for(int[] p:SEALS) if(p[0]==x && p[1]==z) return true;
        return false;
    }
    public static int material(int x,int z) {
        double r=Math.hypot(x,z);
        if(x==0 && z==0) return 4;
        if(seal(x,z)) return 3;
        if(Math.abs(r-14)<.55 || Math.abs(r-18)<.55 || Math.abs(r-4)<.5) return 2;
        if((x==0 || z==0) && r>4 && r<18) return 2;
        return (Math.floorDiv(x,3)+Math.floorDiv(z,3))%2==0?0:1;
    }
    public static boolean pillar(int x,int z) {
        for(int i=0;i<8;i++) {
            double a=(i+.5)*Math.PI/4;
            int px=(int)Math.round(Math.cos(a)*18),pz=(int)Math.round(Math.sin(a)*18);
            if(Math.abs(x-px)<=1 && Math.abs(z-pz)<=1) return true;
        }
        return false;
    }
}
