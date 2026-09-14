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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.MyriadCalamity;

/** A single server plan owns its warning, visible beam/strike and damage. Never edits blocks. */
public final class YangJianHazard extends Entity {
    private static final EntityDataAccessor<CompoundTag> PLAN=SynchedEntityData.defineId(YangJianHazard.class,EntityDataSerializers.COMPOUND_TAG);
    private static final Map<ServerLevel,YangJianEffects.DamageWindow> HITS=new WeakHashMap<>();
    private UUID caster,target;
    private long lease;
    private float damage;
    private boolean expired,impacted;
    public YangJianHazard(EntityType<? extends YangJianHazard> type,Level level) { super(type,level);noPhysics=true;setNoGravity(true); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(PLAN,new CompoundTag()); }
    @Nullable public YangJian getOwner() {
        if(caster==null || !(level() instanceof ServerLevel server))return null;
        return server.getEntity(caster) instanceof YangJian boss?boss:null;
    }
    @Nullable public UUID ownerId() { return caster; }
    private static boolean valid(YangJian boss) { return boss!=null && boss.isAlive() && boss.phase()==3 && !boss.isTransitioning() && !boss.isTrialComplete(); }
    private static boolean room(ServerLevel level,YangJian boss) {
        return valid(boss) && level.getEntitiesOfClass(YangJianHazard.class,boss.getBoundingBox().inflate(96),h->boss.getUUID().equals(h.caster)).size()<YangJianHazardMath.MAX_HAZARDS;
    }
    public static void beam(ServerLevel level,YangJian owner,Vec3 origin,Vec3 direction,double range,double radius,int warning,int active,int mode,float damage) {
        if(!room(level,owner) || !finite(origin) || !finite(direction) || direction.lengthSqr()<1E-8)return;
        YangJianHazard h=MyriadCalamity.YANG_JIAN_HAZARD.get().create(level);if(h==null)return;
        h.bind(owner,damage);CompoundTag p=h.base(1,warning,Math.clamp(active,1,100),radius);
        // Mode 3 is a short clone-sweep pass; it shares the beam renderer with
        // the normal sweep but turns farther each active tick.
        p.putInt("mode",Math.clamp(mode,0,3));p.putDouble("range",Math.clamp(range,1,48));
        write(p,"s",origin);write(p,"d",direction.normalize());write(p,"e",h.clip(origin,origin.add(direction.normalize().scale(p.getDouble("range")))));
        h.entityData.set(PLAN,p);h.setPos(origin);level.addFreshEntity(h);
    }
    public static void strike(ServerLevel level,YangJian owner,Vec3 ground,float radius,int warning,boolean sword,float damage) {
        strike(level,owner,ground,radius,warning,sword,damage,false);
    }
    /** Publishes a red-thunder impact that remains at its landing point. */
    public static void persistentStrike(ServerLevel level,YangJian owner,Vec3 ground,float radius,int warning,boolean sword,float damage) {
        strike(level,owner,ground,radius,warning,sword,damage,true);
    }
    private static void strike(ServerLevel level,YangJian owner,Vec3 ground,float radius,int warning,boolean sword,float damage,boolean persistent) {
        if(!room(level,owner) || !finite(ground))return;
        // Find the real floor before publishing anything, including when the target was jumping.
        HitResult floor=level.clip(new ClipContext(ground.add(0,5,0),ground.add(0,-10,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
        if(floor.getType()==HitResult.Type.MISS)return;
        Vec3 at=floor.getLocation().add(0,.035,0);
        YangJianHazard h=MyriadCalamity.YANG_JIAN_HAZARD.get().create(level);if(h==null)return;
        h.bind(owner,damage);CompoundTag p=h.base(sword?2:3,warning,6,radius);p.putBoolean("persistent",persistent);
        write(p,"s",at);write(p,"e",at.add(0,8,0));h.entityData.set(PLAN,p);h.setPos(at);level.addFreshEntity(h);
    }
    private void bind(YangJian owner,float amount) {
        caster=owner.getUUID();lease=owner.attackSequence();damage=Float.isFinite(amount)?Math.clamp(amount,.1F,10000):1;
        // Do not read Mob.target directly: scripted aggro can temporarily
        // point the boss at another creature while the skill still owns a
        // valid locked target. The owner's skill lock is authoritative.
        LivingEntity player=owner.activeTarget();
        if(player!=null)target=player.getUUID();
    }
    private CompoundTag base(int kind,int warning,int active,double radius) {
        CompoundTag p=new CompoundTag();p.putInt("kind",kind);p.putLong("time",level().getGameTime());
        // The aerial sweep is deliberately telegraph-free; other hazards keep
        // their ordinary minimum warning window.
        int minimum=kind==1?0:12;
        p.putInt("w",Math.clamp(warning,minimum,80));p.putInt("active",active);p.putFloat("radius",(float)YangJianHazardMath.impactRadius(kind,radius));return p;
    }
    public boolean ready() { return entityData.get(PLAN).contains("time"); }
    public int kind() { return entityData.get(PLAN).getInt("kind"); }
    public int mode() { return entityData.get(PLAN).getInt("mode"); }
    public int windup() { return entityData.get(PLAN).getInt("w"); }
    public int duration() { return entityData.get(PLAN).getInt("active"); }
    public float radius() { return entityData.get(PLAN).getFloat("radius"); }
    public boolean persistent() { return entityData.get(PLAN).getBoolean("persistent"); }
    public float age(float partial) { return level().getGameTime()-entityData.get(PLAN).getLong("time")+partial; }
    public Vec3 start() { return read(entityData.get(PLAN),"s"); }
    public Vec3 end() { return read(entityData.get(PLAN),"e"); }
    public boolean warning(float partial) { return ready() && age(partial)>=0 && age(partial)<windup(); }
    public boolean active(float partial) { return ready() && YangJianHazardMath.active(age(partial),windup(),duration()); }
    private boolean stillValid(YangJian owner) { return !isRemoved() && valid(owner) && (persistent() || owner.attackSequence()==lease); }
    private Vec3 clip(Vec3 from,Vec3 to) { return level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getLocation(); }
    @Override public void tick() {
        super.tick();if(!(level() instanceof ServerLevel server))return;
        YangJian owner=getOwner();
        boolean persistentStrike=kind()==3 && persistent();
        if(expired || !ready() || !stillValid(owner) || (!persistentStrike && age(0)>=windup()+duration())) { discard();return; }
        if(!persistentStrike && !active(0))return;
        if(kind()==1) {
            CompoundTag p=entityData.get(PLAN).copy();Vec3 direction=read(p,"d");
            if(age(0)>windup()) {
                if(mode()==1 || mode()==3) {
                    double a=mode()==3?YangJianHazardMath.FAST_SWEEP_TURN:YangJianHazardMath.SWEEP_TURN;
                    double c=Math.cos(a),s=Math.sin(a);
                    direction=new Vec3(direction.x*c-direction.z*s,direction.y,direction.x*s+direction.z*c);
                } else if(mode()==2 && target!=null && server.getEntity(target) instanceof LivingEntity player && owner.validTarget(player)) {
                    Vec3 desired=player.position().add(0,player.getBbHeight()*.5,0).subtract(start()).normalize();
                    var turn=YangJianHazardMath.turn(new YangJianEffects.Direction(direction.x,direction.y,direction.z),new YangJianEffects.Direction(desired.x,desired.y,desired.z),YangJianHazardMath.TRACK_TURN);
                    direction=new Vec3(turn.x(),turn.y(),turn.z());
                }
            }
            write(p,"d",direction);write(p,"e",clip(start(),start().add(direction.scale(p.getDouble("range")))));entityData.set(PLAN,p);
            if(age(0)==windup())level().playSound(null,blockPosition(),SoundEvents.BEACON_ACTIVATE,SoundSource.HOSTILE,1,.65F);
            for(LivingEntity player:server.getEntitiesOfClass(LivingEntity.class,new AABB(start(),end()).inflate(radius()+.7),owner::validTarget)) {
                if(!stillValid(owner))return;
                AABB body=player.getBoundingBox().inflate(radius());
                if(body.contains(start()) || body.clip(start(),end()).isPresent())damage(player,owner,server);
            }
        } else if(persistentStrike) {
            if(warning(0))return;
            if(!impacted) {
                impacted=true;
                level().playSound(null,blockPosition(),SoundEvents.TRIDENT_THUNDER.value(),SoundSource.HOSTILE,1,1.35F);
            }
            damageGroundTargets(server,owner);
        } else if(!impacted) {
            impacted=true;
            level().playSound(null,blockPosition(),kind()==3?SoundEvents.TRIDENT_THUNDER.value():SoundEvents.TRIDENT_HIT_GROUND,SoundSource.HOSTILE,1,kind()==3?1.35F:.8F);
            damageGroundTargets(server,owner);
        }
    }
    private void damageGroundTargets(ServerLevel server,YangJian owner) {
        Vec3 point=start();
        for(LivingEntity player:server.getEntitiesOfClass(LivingEntity.class,new AABB(point,point).inflate(radius()+.5,3,radius()+.5),owner::validTarget)) {
            if(!stillValid(owner))return;
            if(player.position().subtract(point).horizontalDistanceSqr()>Math.pow(radius()+player.getBbWidth()*.5,2) || player.getY()>point.y+2.8 || player.getY()+player.getBbHeight()<point.y)continue;
            Vec3 eye=player.position().add(0,.4,0);
            if(clip(point.add(0,.4,0),eye).distanceToSqr(eye)>.01)continue;
            damage(player,owner,server);
        }
    }
    private void damage(LivingEntity player,YangJian owner,ServerLevel server) {
        if(HITS.computeIfAbsent(server,key->new YangJianEffects.DamageWindow()).claim(owner.getUUID(),player.getUUID(),server.getGameTime()))
            player.hurt(damageSources().mobProjectile(this,owner),damage);
    }
    @Override public AABB getBoundingBoxForCulling() {
        if(!ready())return super.getBoundingBoxForCulling();
        return persistent()?new AABB(start(),start()).inflate(radius()+1,2.5,radius()+1):new AABB(start(),end()).inflate(radius()+1);
    }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance<128*128; }
    @Override public boolean isPickable() { return false; }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { if(caster!=null)tag.putUUID("Caster",caster);tag.put("Plan",entityData.get(PLAN).copy()); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { expired=true;caster=tag.hasUUID("Caster")?tag.getUUID("Caster"):null; }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.x+v.y+v.z); }
    private static Vec3 read(CompoundTag p,String name) { return new Vec3(p.getDouble(name+"x"),p.getDouble(name+"y"),p.getDouble(name+"z")); }
    private static void write(CompoundTag p,String name,Vec3 v) { p.putDouble(name+"x",v.x);p.putDouble(name+"y",v.y);p.putDouble(name+"z",v.z); }
}
