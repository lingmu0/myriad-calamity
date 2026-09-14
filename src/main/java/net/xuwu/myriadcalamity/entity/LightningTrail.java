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

/** A finite, jumpable ground route that delivers extra lightning damage. */
public final class LightningTrail extends Entity {
    private static final EntityDataAccessor<CompoundTag> ROUTE=SynchedEntityData.defineId(LightningTrail.class,EntityDataSerializers.COMPOUND_TAG);
    private static final Map<ServerLevel,YangJianEffects.DamageWindow> HIT_WINDOWS=new WeakHashMap<>();
    @Nullable private UUID casterId;
    private float damage=4;
    private boolean expired;

    public LightningTrail(EntityType<? extends LightningTrail> type,Level level) { super(type,level);setNoGravity(true);noPhysics=true; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(ROUTE,new CompoundTag()); }

    public static void create(ServerLevel level,YangJian owner,Vec3 from,Vec3 to,float damage,int lifetime) {
        if(owner.phase()!=2 || !owner.isAlive() || owner.isTransitioning() || owner.action()==YangJian.PHASE_CLEAR || owner.isTrialComplete()
                || !finite(from) || !finite(to) || from.distanceToSqr(to)<.0009
                || !Float.isFinite(damage) || damage<=0 || from.distanceToSqr(to)>20*20)return;
        Vec3 start=ground(level,owner,from),end=ground(level,owner,to);
        if(start==null || end==null || Math.abs(start.y-end.y)>1.2)return;
        LightningTrail trail=MyriadCalamity.LIGHTNING_TRAIL.get().create(level);if(trail==null)return;
        trail.casterId=owner.getUUID();trail.damage=damage;
        CompoundTag route=new CompoundTag();putPoint(route,"start",start);putPoint(route,"end",end);
        route.putLong("time",level.getGameTime());route.putInt("life",Math.clamp(lifetime,1,80));
        trail.entityData.set(ROUTE,route);trail.setPos(start.add(end).scale(.5));level.addFreshEntity(trail);
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
    public Vec3 routeStart() { return point(entityData.get(ROUTE),"start"); }
    public Vec3 routeEnd() { return point(entityData.get(ROUTE),"end"); }
    public float effectAge(float partial) { return Math.max(0,level().getGameTime()-entityData.get(ROUTE).getLong("time")+partial); }
    public int lifetime() { return entityData.get(ROUTE).getInt("life"); }

    @Override public void tick() {
        super.tick();
        if(!(level() instanceof ServerLevel server))return;
        YangJian owner=getOwner();
        if(expired || !hasRoute() || effectAge(0)>=lifetime() || owner==null || !owner.isAlive()
                || owner.phase()!=2 || owner.action()==YangJian.PHASE_CLEAR || owner.isTrialComplete()) { discard();return; }
        // Collision runs on a short interval, with a shared caster/target budget across all route segments.
        if(tickCount%3!=0)return;
        Vec3 start=routeStart(),end=routeEnd();
        AABB area=new AABB(start,end).inflate(YangJianEffects.TRAIL_RADIUS+.4,1.2,YangJianEffects.TRAIL_RADIUS+.4);
        YangJianEffects.DamageWindow hits=HIT_WINDOWS.computeIfAbsent(server,key->new YangJianEffects.DamageWindow());
        for(LivingEntity player:server.getEntitiesOfClass(LivingEntity.class,area,owner::validTarget)) {
            // A previous victim's thorns may have ended the trial and discarded this route.
            if(isRemoved() || !owner.isAlive() || owner.isTransitioning() || owner.phase()!=2
                    || owner.action()==YangJian.PHASE_CLEAR || owner.isTrialComplete())return;
            Vec3 p=player.position();
            if(!YangJianEffects.groundContact(p.x,p.y,p.z,start.x,start.y,start.z,end.x,end.y,end.z,YangJianEffects.TRAIL_RADIUS,player.getBbWidth()*.5))continue;
            Vec3 segment=end.subtract(start);
            double t=segment.horizontalDistanceSqr()<1E-8?0:Math.clamp(((p.x-start.x)*segment.x+(p.z-start.z)*segment.z)/segment.horizontalDistanceSqr(),0,1);
            Vec3 nearest=start.add(segment.scale(t));
            if(server.clip(new ClipContext(p.add(0,.3,0),nearest.add(0,.3,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()!=HitResult.Type.MISS)continue;
            if(hits.claim(owner.getUUID(),player.getUUID(),server.getGameTime()))
                player.hurt(damageSources().lightningBolt(),damage);
        }
    }
    @Override public boolean isPickable() { return false; }
    @Override public AABB getBoundingBoxForCulling() {
        return hasRoute()?new AABB(routeStart(),routeEnd()).inflate(YangJianEffects.TRAIL_RADIUS,.8,YangJianEffects.TRAIL_RADIUS):super.getBoundingBoxForCulling();
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
