package net.xuwu.myriadcalamity.entity;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.config.MyriadConfig;

/** Bounded P2/P3 revolving sword volleys, or a fixed-aim off-hand knife during an advance. */
public final class DivineFlyingSword extends Projectile {
    private static final EntityDataAccessor<CompoundTag> PLAN=SynchedEntityData.defineId(DivineFlyingSword.class,EntityDataSerializers.COMPOUND_TAG);
    @Nullable private UUID casterId,targetId;
    private boolean released,expired;
    private float damage=8;

    public DivineFlyingSword(EntityType<? extends DivineFlyingSword> type,Level level) { super(type,level);setNoGravity(true); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(PLAN,new CompoundTag()); }

    public static void launch(ServerLevel level,YangJian owner,LivingEntity target,int requested) {
        if(owner.phase()!=2 || !owner.isAlive() || owner.isTransitioning() || owner.action()==YangJian.PHASE_CLEAR
                || owner.isTrialComplete() || !owner.validTarget(target))return;
        int count=YangJianEffects.swordCount(requested);
        if(count==0)return;
        // A recast replaces its previous volley, bounding both active entities and threat density.
        for(DivineFlyingSword old:level.getEntitiesOfClass(DivineFlyingSword.class,owner.getBoundingBox().inflate(100),
                sword->owner.getUUID().equals(sword.ownerId())))old.discard();
        // All blades appear together in the ring. Only the first one is armed;
        // each later blade waits visibly in orbit until the previous release
        // opens its own aim window. The swords themselves are the visual cue.
        for(int i=0;i<count;i++)launchSingle(level,owner,target,i,count,level.getGameTime(),i==0);
        level.playSound(null,owner.blockPosition(),SoundEvents.TRIDENT_RETURN,SoundSource.HOSTILE,1.2F,1.35F);
    }

    /** One wave of the P3 stream; the caller schedules waves 0..22 every four ticks. */
    public static void launchMyriad(ServerLevel level,YangJian owner,LivingEntity target,int wave) {
        if(owner.phase()!=3 || owner.action()!=YangJian.MYRIAD_SWORDS || !owner.isAlive() || owner.isTransitioning()
                || owner.isTrialComplete() || !owner.validTarget(target))return;
        var existing=level.getEntitiesOfClass(DivineFlyingSword.class,owner.getBoundingBox().inflate(100),
            sword->owner.getUUID().equals(sword.ownerId()) && sword.myriadSword());
        // A new invocation replaces only its own previous stream. Flight from
        // earlier waves in this invocation remains intact.
        if(wave==0)for(DivineFlyingSword old:existing)old.discard();
        int count=YangJianEffects.myriadSwordCount(wave,wave==0?0:existing.size());
        for(int index=0;index<count;index++) {
            DivineFlyingSword sword=MyriadCalamity.DIVINE_FLYING_SWORD.get().create(level);if(sword==null)continue;
            sword.setOwner(owner);sword.casterId=owner.getUUID();sword.targetId=target.getUUID();
            sword.damage=MyriadConfig.scaleYangJianDamage(8);
            CompoundTag plan=new CompoundTag();plan.putLong("time",level.getGameTime());
            plan.putBoolean("myriad",true);plan.putBoolean("armed",true);plan.putInt("wave",wave);plan.putInt("index",index);
            plan.putLong("sequence",owner.attackSequence());plan.putInt("target",target.getId());
            putPoint(plan,"center",owner.position().add(0,2.2,0));
            putPoint(plan,"aim",target.position().add(0,target.getBbHeight()*.5,0));
            sword.entityData.set(PLAN,plan);sword.setPos(sword.orbitPoint(0));level.addFreshEntity(sword);
        }
        if(count>0 && wave%3==0)
            level.playSound(null,owner.blockPosition(),SoundEvents.TRIDENT_RETURN,SoundSource.HOSTILE,.55F,1.55F);
    }

    private static void launchSingle(ServerLevel level,YangJian owner,LivingEntity target,int index,int count,long startTime,boolean armed) {
        DivineFlyingSword sword=MyriadCalamity.DIVINE_FLYING_SWORD.get().create(level);if(sword==null)return;
        sword.setOwner(owner);sword.casterId=owner.getUUID();sword.targetId=target.getUUID();
        sword.damage=MyriadConfig.scaleYangJianDamage(8);
        Vec3 center=owner.position().add(0,2.2,0),aim=owner.attackPoint().add(0,.9,0);
        CompoundTag plan=new CompoundTag();plan.putLong("time",startTime);plan.putInt("index",index);plan.putInt("count",count);plan.putBoolean("armed",armed);
        // The player's entity id travels with the plan so both the waiting blades and the
        // aiming blade can keep their nose on the live target after the orbit ends.
        plan.putInt("target",target.getId());
        putPoint(plan,"center",center);putPoint(plan,"aim",aim);sword.entityData.set(PLAN,plan);
        sword.setPos(sword.orbitPoint(0));level.addFreshEntity(sword);
    }

    private static void armNext(ServerLevel level,YangJian owner,UUID targetId,int index,int count) {
        if(targetId==null || index+1>=count || !owner.isAlive() || owner.phase()!=2 || owner.isTransitioning()
                || owner.action()==YangJian.PHASE_CLEAR || owner.isTrialComplete())return;
        Entity entity=level.getEntity(targetId);
        if(entity instanceof LivingEntity target && owner.validTarget(target)) {
            // The next blade is already visible. Rebase its local clock at the
            // orbit-complete frame so each blade aims before its own release.
            for(DivineFlyingSword next:level.getEntitiesOfClass(DivineFlyingSword.class,owner.getBoundingBox().inflate(100),
                    sword->owner.getUUID().equals(sword.ownerId()) && !sword.stepThrow() && !sword.myriadSword()
                        && sword.entityData.get(PLAN).getInt("count")==count
                        && sword.entityData.get(PLAN).getInt("index")==index+1
                        && !sword.entityData.get(PLAN).getBoolean("armed"))) {
                CompoundTag plan=next.entityData.get(PLAN).copy();plan.putBoolean("armed",true);
                plan.putLong("time",level.getGameTime()-YangJianEffects.ORBIT_TICKS);plan.putBoolean("locked",false);
                next.entityData.set(PLAN,plan);return;
            }
        }
    }

    /** A single off-hand knife is prepared during each advance, without taking away the held weapon. */
    public static void launchStep(ServerLevel level,YangJian owner,LivingEntity target) {
        if(!owner.isApproaching() || !owner.validTarget(target))return;
        // Keep an earlier airborne knife, but replace stale preparations from an interrupted step.
        for(DivineFlyingSword old:level.getEntitiesOfClass(DivineFlyingSword.class,owner.getBoundingBox().inflate(80),
                sword->owner.getUUID().equals(sword.ownerId()) && sword.stepThrow() && !sword.released))old.discard();
        DivineFlyingSword sword=MyriadCalamity.DIVINE_FLYING_SWORD.get().create(level);if(sword==null)return;
        sword.setOwner(owner);sword.casterId=owner.getUUID();sword.targetId=target.getUUID();
        sword.damage=MyriadConfig.scaleYangJianDamage(7);
        CompoundTag plan=new CompoundTag();plan.putLong("time",level.getGameTime());plan.putBoolean("step",true);plan.putBoolean("locked",true);
        putPoint(plan,"center",owner.position());putPoint(plan,"aim",target.position().add(0,target.getBbHeight()*.5,0));
        sword.entityData.set(PLAN,plan);sword.setPos(sword.orbitPoint(0));level.addFreshEntity(sword);
    }

    public boolean hasPlan() { return entityData.get(PLAN).contains("time"); }
    public float effectAge(float partial) { return hasPlan()?Math.max(0,level().getGameTime()-entityData.get(PLAN).getLong("time")+partial):0; }
    public boolean stepThrow() { return entityData.get(PLAN).getBoolean("step"); }
    public boolean myriadSword() { return entityData.get(PLAN).getBoolean("myriad"); }
    private boolean armed() { return stepThrow() || entityData.get(PLAN).getBoolean("armed"); }
    public int releaseTick() {
        if(stepThrow())return YangJianFootwork.THROW_TICK;
        return myriadSword()?YangJianEffects.myriadReleaseTick(entityData.get(PLAN).getInt("index")):YangJianEffects.releaseTick();
    }
    public boolean flying(float partial) { return armed() && effectAge(partial)>=releaseTick(); }
    public Vec3 aimPoint() { return point(entityData.get(PLAN),"aim"); }
    public Vec3 castingCenter() { return point(entityData.get(PLAN),"center"); }
    @Nullable public UUID ownerId() { return casterId; }
    @Override @Nullable public YangJian getOwner() { Entity entity=super.getOwner();return entity instanceof YangJian boss?boss:null; }
    public Vec3 facing(float partial) {
        if(myriadSword() && !flying(partial)) {
            float age=effectAge(partial);
            Vec3 tangent=orbitPoint(age+.1F).subtract(orbitPoint(age));
            Entity target=level().getEntity(entityData.get(PLAN).getInt("target"));
            Vec3 aim=target instanceof LivingEntity living && living.isAlive()?living.position().add(0,living.getBbHeight()*.5,0):aimPoint();
            Vec3 direction=aim.subtract(position()).normalize();
            double align=Math.clamp((age-(releaseTick()-4))/4D,0,1);
            Vec3 blend=tangent.normalize().scale(1-align).add(direction.scale(align));
            if(blend.lengthSqr()>1E-8)return blend.normalize();
        }
        // Once the P2 orbit has finished the blade aims at the player for the rest of the
        // aim window, including the blades still waiting their turn in the ring.
        if(!stepThrow() && !myriadSword() && !flying(partial)
                && effectAge(partial)>=YangJianEffects.ORBIT_TICKS) {
            Vec3 direction=liveAimPoint().subtract(position());
            if(direction.lengthSqr()>1E-8)return direction.normalize();
        }
        Vec3 delta=flying(partial)?getDeltaMovement():aimPoint().subtract(position());
        return delta.lengthSqr()>1E-8?delta.normalize():new Vec3(0,1,0);
    }
    /** The target's live aiming point when the plan carries its id, else the locked aim. */
    private Vec3 liveAimPoint() {
        Entity target=level().getEntity(entityData.get(PLAN).getInt("target"));
        return target instanceof LivingEntity living && living.isAlive()
            ?living.position().add(0,living.getBbHeight()*.5,0):aimPoint();
    }
    private Vec3 orbitPoint(float age) {
        if(stepThrow()) {
            YangJian owner=getOwner();
            if(owner==null)return position();
            Vec3 forward=owner.getLookAngle().multiply(1,0,1).normalize();
            // Release position sampled from the authored left-hand pose at tick five, including model scale.
            return owner.position().add(0,2.24,0).add(new Vec3(-forward.z,0,forward.x).scale(1.01)).add(forward.scale(.66));
        }
        CompoundTag plan=entityData.get(PLAN);
        if(myriadSword()) {
            YangJian owner=getOwner();
            Vec3 center=owner==null?castingCenter():owner.position().add(0,2.2,0);
            YangJianEffects.Direction offset=YangJianEffects.myriadOrbit(age,plan.getInt("wave"),plan.getInt("index"));
            return center.add(offset.x(),offset.y(),offset.z());
        }
        double angle=plan.getInt("index")*Math.PI*2/Math.max(1,plan.getInt("count"))
            +Math.min(YangJianEffects.ORBIT_TICKS,age)*.16;
        return castingCenter().add(Math.cos(angle)*2.1,.5+Math.sin(angle*1.3)*.25,Math.sin(angle)*2.1);
    }

    @Override public void tick() {
        super.tick();
        if(level().isClientSide && !hasPlan())return;
        int age=(int)effectAge(0),flightAge=age-releaseTick();
        boolean armed=armed();
        if(!level().isClientSide) {
            YangJian owner=getOwner();
            if(expired || (armed && age>=releaseTick()+YangJianEffects.FLIGHT_TICKS) || owner==null || !owner.isAlive()
                    || (!stepThrow() && owner.phase()!=(myriadSword()?3:2)) || owner.isTransitioning() || owner.action()==YangJian.PHASE_CLEAR || owner.isTrialComplete()
                    || !owner.getUUID().equals(casterId) || distanceToSqr(castingCenter())>75*75) { discard();return; }
            if(myriadSword() && !released && (owner.action()!=YangJian.MYRIAD_SWORDS
                    || owner.attackSequence()!=entityData.get(PLAN).getLong("sequence"))) { discard();return; }
            if(stepThrow()) {
                Entity target=targetId==null?null:((ServerLevel)level()).getEntity(targetId);
                if(!(target instanceof LivingEntity player) || !owner.validTarget(player)
                    || (!released && !owner.isApproaching())) { discard();return; }
            }
        }
        // Unarmed blades stay visible in the orbit until their predecessor
        // releases them.  This branch is shared by server and client so the
        // waiting entities never freeze at the end of the initial orbit.
        if(!stepThrow() && !armed) {
            setDeltaMovement(Vec3.ZERO);setPos(orbitPoint(Math.min(YangJianEffects.ORBIT_TICKS,Math.max(0,age))));
            return;
        }
        if(!level().isClientSide) {
            YangJian owner=getOwner();
            int lockTick=myriadSword()?releaseTick():YangJianEffects.ORBIT_TICKS;
            // The P2 volley keeps its blade trained on the player for the whole aim window:
            // the stored aim is refreshed every tick, so the shot leaves along the player's
            // live position instead of where they stood when the orbit ended.
            boolean keepAiming=!myriadSword() && !stepThrow() && !released;
            if(age>=lockTick && (keepAiming || !entityData.get(PLAN).getBoolean("locked"))) {
                Entity target=targetId==null?null:((ServerLevel)level()).getEntity(targetId);
                if(!(target instanceof LivingEntity player) || !owner.validTarget(player)) { discard();return; }
                // P3 samples the target at release, after the visible orbit;
                // P2 retains its existing sequential aim/release timing.
                Vec3 aim=player.position().add(0,player.getBbHeight()*.5,0),wait=orbitPoint(lockTick);
                HitResult wall=level().clip(new ClipContext(wait,aim,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
                if(wall.getType()!=HitResult.Type.MISS)aim=wall.getLocation();
                CompoundTag plan=entityData.get(PLAN).copy();putPoint(plan,"aim",aim);plan.putBoolean("locked",true);
                entityData.set(PLAN,plan);
            }
        }
        if(age<releaseTick()) {
            setDeltaMovement(Vec3.ZERO);setPos(orbitPoint(age));return;
        }
        if(!level().isClientSide) {
            YangJian owner=getOwner();
            if(!released) {
                if(stepThrow() || myriadSword())setPos(orbitPoint(age));
                if(myriadSword() && owner!=null) {
                    CompoundTag plan=entityData.get(PLAN).copy();putPoint(plan,"center",owner.position().add(0,2.2,0));entityData.set(PLAN,plan);
                }
                released=true;
                Vec3 direction=aimPoint().subtract(position()).normalize();
                if(direction.lengthSqr()<1E-8) { discard();return; }
                if(!stepThrow() && owner!=null)owner.leaveSwordLightningTrail(position(),aimPoint());
                shoot(direction.x,direction.y,direction.z,(float)YangJianEffects.SWORD_SPEED,0);
                if(!myriadSword() || entityData.get(PLAN).getInt("index")==0)
                    level().playSound(null,blockPosition(),SoundEvents.TRIDENT_THROW.value(),SoundSource.HOSTILE,myriadSword()?.4F:.65F,1.6F);
                if(!stepThrow() && !myriadSword() && level() instanceof ServerLevel server)
                    armNext(server,owner,targetId,entityData.get(PLAN).getInt("index"),entityData.get(PLAN).getInt("count"));
            }
            // Start with the locked heading; corrections begin only after the first movement tick.
            if(!stepThrow() && flightAge>0 && flightAge<=YangJianEffects.TRACKING_TICKS && targetId!=null && level() instanceof ServerLevel server) {
                Entity target=server.getEntity(targetId);
                if(target instanceof LivingEntity player && owner!=null && owner.validTarget(player)) {
                    Vec3 desired=player.position().add(0,player.getBbHeight()*.5,0).subtract(position()).normalize();
                    setDeltaMovement(turnTowards(getDeltaMovement().normalize(),desired).scale(YangJianEffects.SWORD_SPEED));
                    hasImpulse=true;
                }
            }
            if(!level().hasChunkAt(BlockPos.containing(position().add(getDeltaMovement())))) { discard();return; }
            HitResult hit=ProjectileUtil.getHitResultOnMoveVector(this,this::canHitEntity);
            if(hit.getType()!=HitResult.Type.MISS) {
                if(!EventHooks.onProjectileImpact(this,hit)) {
                    if(hit instanceof EntityHitResult entityHit && owner!=null)
                        entityHit.getEntity().hurt(damageSources().mobProjectile(this,owner),damage);
                    // One impact ends the sword: no repeated damage, penetration, or blocks modified.
                    discard();return;
                }
            }
        }
        setPos(position().add(getDeltaMovement()));updateRotation();
    }

    private static Vec3 turnTowards(Vec3 current,Vec3 desired) {
        YangJianEffects.Direction turned=YangJianEffects.turnSword(new YangJianEffects.Direction(current.x,current.y,current.z),
            new YangJianEffects.Direction(desired.x,desired.y,desired.z));
        return new Vec3(turned.x(),turned.y(),turned.z());
    }
    @Override protected boolean canHitEntity(Entity entity) {
        YangJian owner=getOwner();
        return entity instanceof LivingEntity living && owner!=null && owner.validTarget(living) && super.canHitEntity(entity);
    }
    @Override public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(2);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);tag.put("SwordPlan",entityData.get(PLAN).copy());tag.putFloat("SwordDamage",damage);
        if(casterId!=null)tag.putUUID("SwordCaster",casterId);if(targetId!=null)tag.putUUID("SwordTarget",targetId);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);casterId=tag.hasUUID("SwordCaster")?tag.getUUID("SwordCaster"):null;
        // A saved caster cancels its partial attack; its old volley must not reappear on reload.
        expired=true;setNoGravity(true);
    }
    private static void putPoint(CompoundTag tag,String name,Vec3 point) {
        tag.putDouble(name+"X",point.x);tag.putDouble(name+"Y",point.y);tag.putDouble(name+"Z",point.z);
    }
    private static Vec3 point(CompoundTag tag,String name) {
        return new Vec3(tag.getDouble(name+"X"),tag.getDouble(name+"Y"),tag.getDouble(name+"Z"));
    }
}
