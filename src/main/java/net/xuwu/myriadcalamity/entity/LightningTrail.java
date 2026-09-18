package net.xuwu.myriadcalamity.entity;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.MyriadCalamity;

/**
 * The electrified footprint an axe-aftershock move leaves behind. Unlike a drawn route it covers
 * the whole area the triggering move reached, keeps discharging in blue until the next axe
 * summon, and only bites bodies that are still standing in it.
 */
public final class LightningTrail extends Entity {
    private static final EntityDataAccessor<CompoundTag> ROUTE=SynchedEntityData.defineId(LightningTrail.class,EntityDataSerializers.COMPOUND_TAG);
    private static final Map<ServerLevel,YangJianEffects.DamageWindow> HIT_WINDOWS=new WeakHashMap<>();
    @Nullable private UUID casterId;
    private float damage=4;
    private boolean expired;

    public LightningTrail(EntityType<? extends LightningTrail> type,Level level) { super(type,level);setNoGravity(true);noPhysics=true; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(ROUTE,new CompoundTag()); }

    /**
     * Publishes one scar over the area of the plan that just spent an axe charge.
     *
     * @param anchor  sector centre, or the lane's first end
     * @param end     the sector's strike direction, or the lane's second end
     * @param center  the circle's centre; unused by the other shapes
     */
    public static boolean create(ServerLevel level,YangJian owner,int shape,Vec3 anchor,Vec3 end,Vec3 center,
                              double radius,double arcDegrees,float damage,int lifetime) {
        if(owner.phase()!=2 || !owner.isAlive() || owner.isTransitioning() || owner.action()==YangJian.PHASE_CLEAR
                || owner.isTrialComplete() || !finite(anchor) || !finite(end) || !finite(center)
                || !Float.isFinite(damage) || damage<=0 || !(radius>0) || radius>24)return false;
        // The theatre floor is flat, so one ground sample anchors the whole footprint.
        Vec3 ground=ground(level,owner,center);
        if(ground==null)return false;
        LightningTrail trail=MyriadCalamity.LIGHTNING_TRAIL.get().create(level);if(trail==null)return false;
        trail.casterId=owner.getUUID();trail.damage=damage;
        CompoundTag route=new CompoundTag();
        route.putInt("shape",shape);route.putFloat("radius",(float)radius);route.putFloat("angle",(float)arcDegrees);
        route.putLong("time",level.getGameTime());route.putInt("life",Math.clamp(lifetime,0,80));
        putPoint(route,"s",new Vec3(anchor.x,ground.y,anchor.z));
        putPoint(route,"e",new Vec3(end.x,ground.y,end.z));
        putPoint(route,"p",new Vec3(center.x,ground.y,center.z));
        trail.entityData.set(ROUTE,route);trail.setPos(ground);level.addFreshEntity(trail);
        return true;
    }

    @Nullable private static Vec3 ground(ServerLevel level,YangJian owner,Vec3 point) {
        HitResult hit=level.clip(new ClipContext(point.add(0,1.8,0),point.add(0,-3,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
        return hit.getType()==HitResult.Type.MISS?null:hit.getLocation().add(0,.035,0);
    }
    @Nullable public UUID ownerId() { return casterId; }
    @Nullable public YangJian getOwner() {
        if(casterId==null || !(level() instanceof ServerLevel server))return null;
        Entity owner=server.getEntity(casterId);return owner instanceof YangJian boss?boss:null;
    }
    public boolean hasRoute() { return entityData.get(ROUTE).contains("time"); }
    public int shape() { return entityData.get(ROUTE).getInt("shape"); }
    public float scarRadius() { return entityData.get(ROUTE).getFloat("radius"); }
    public float scarAngle() { return entityData.get(ROUTE).getFloat("angle"); }
    public Vec3 anchor() { return point(entityData.get(ROUTE),"s"); }
    public Vec3 scarEnd() { return point(entityData.get(ROUTE),"e"); }
    public Vec3 scarCenter() { return point(entityData.get(ROUTE),"p"); }
    public double groundY() { return anchor().y; }
    public float effectAge(float partial) { return Math.max(0,level().getGameTime()-entityData.get(ROUTE).getLong("time")+partial); }
    public int lifetime() { return entityData.get(ROUTE).getInt("life"); }
    /** A scar with no lifetime waits for the next axe summon instead of expiring on a timer. */
    public boolean persistent() { return lifetime()==LightningScar.PERSISTENT; }
    /** True while the scar is still allowed to exist: used by the renderer and the damage pass. */
    public boolean live(float partial) { return hasRoute() && (persistent() || effectAge(partial)<lifetime()); }

    @Override public void tick() {
        super.tick();
        if(!(level() instanceof ServerLevel server))return;
        YangJian owner=getOwner();
        if(expired || !live(0) || owner==null || !owner.isAlive()
                || owner.phase()!=2 || owner.action()==YangJian.PHASE_CLEAR || owner.isTrialComplete()) { discard();return; }
        // Damage runs on a short interval, sharing one caster/target budget across every scar.
        if(tickCount%3!=0)return;
        Vec3 anchor=anchor(),end=scarEnd(),center=scarCenter();
        double reach=scarRadius()+1.2,ground=groundY();
        AABB area=new AABB(Math.min(anchor.x,Math.min(end.x,center.x))-reach,ground-1.2,
            Math.min(anchor.z,Math.min(end.z,center.z))-reach,
            Math.max(anchor.x,Math.max(end.x,center.x))+reach,ground+3,
            Math.max(anchor.z,Math.max(end.z,center.z))+reach);
        YangJianEffects.DamageWindow hits=HIT_WINDOWS.computeIfAbsent(server,key->new YangJianEffects.DamageWindow());
        for(LivingEntity player:server.getEntitiesOfClass(LivingEntity.class,area,owner::validTarget)) {
            // A previous victim's thorns may have ended the trial and discarded this scar.
            if(isRemoved() || !owner.isAlive() || owner.isTransitioning() || owner.phase()!=2
                    || owner.action()==YangJian.PHASE_CLEAR || owner.isTrialComplete())return;
            if(!LightningScar.covers(shape(),player.getX(),player.getZ(),player.getY(),player.getBoundingBox().maxY,
                    ground,anchor.x,anchor.z,end.x-anchor.x,end.z-anchor.z,center.x,center.z,
                    scarRadius(),scarAngle(),player.getBbWidth()*.5))continue;
            // A player walled off from the discharge is protected from it.
            if(server.clip(new ClipContext(center.add(0,.4,0),player.position().add(0,.4,0),
                    ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()!=HitResult.Type.MISS)continue;
            if(hits.claim(owner.getUUID(),player.getUUID(),server.getGameTime()))
                player.hurt(damageSources().lightningBolt(),damage);
        }
    }
    @Override public boolean isPickable() { return false; }
    @Override public AABB getBoundingBoxForCulling() {
        if(!hasRoute())return super.getBoundingBoxForCulling();
        Vec3 anchor=anchor(),end=scarEnd(),center=scarCenter();
        double reach=scarRadius()+1;
        return new AABB(Math.min(anchor.x,Math.min(end.x,center.x))-reach,groundY(),
            Math.min(anchor.z,Math.min(end.z,center.z))-reach,
            Math.max(anchor.x,Math.max(end.x,center.x))+reach,groundY()+LightningScar.PILLAR_HEIGHT+1,
            Math.max(anchor.z,Math.max(end.z,center.z))+reach);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("LightningRoute",entityData.get(ROUTE).copy());tag.putFloat("LightningDamage",damage);
        if(casterId!=null)tag.putUUID("LightningCaster",casterId);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        casterId=tag.hasUUID("LightningCaster")?tag.getUUID("LightningCaster"):null;
        // Reloading cancels old attack remnants together with the caster's interrupted action.
        expired=true;setNoGravity(true);noPhysics=true;
    }
    private static boolean finite(Vec3 p) { return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z); }
    private static void putPoint(CompoundTag tag,String name,Vec3 p) {
        tag.putDouble(name+"X",p.x);tag.putDouble(name+"Y",p.y);tag.putDouble(name+"Z",p.z);
    }
    private static Vec3 point(CompoundTag tag,String name) {
        return new Vec3(tag.getDouble(name+"X"),tag.getDouble(name+"Y"),tag.getDouble(name+"Z"));
    }
}
