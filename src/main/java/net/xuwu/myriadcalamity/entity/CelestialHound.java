package net.xuwu.myriadcalamity.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.config.MyriadConfig;

/** A killable combat partner, with an explicit side-pounce command from Yang Jian. */
public final class CelestialHound extends Monster {
    public static final int IDLE=0,STALK=1,WINDUP=2,POUNCE=3,RETREAT=4;
    public static final int WINDUP_TICKS=10,POUNCE_TICKS=8,MARK_TICKS=80;
    private static final EntityDataAccessor<Integer> ACTION=SynchedEntityData.defineId(CelestialHound.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> STARTED=SynchedEntityData.defineId(CelestialHound.class,EntityDataSerializers.LONG);
    @Nullable private UUID ownerId,targetId;
    private Vec3 jumpStart=Vec3.ZERO,jumpEnd=Vec3.ZERO,flankPoint=Vec3.ZERO;
    private int life=1800,orphanTicks,delay=20,coordinatedTicks;
    private boolean initialized;
    private final Set<UUID> pounceHits=new HashSet<>();

    public CelestialHound(EntityType<? extends CelestialHound> type,Level level) {
        super(type,level);setPersistenceRequired();xpReward=0;
    }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,MyriadConfig.DEFAULT_HOUND_HEALTH).add(Attributes.ATTACK_DAMAGE,MyriadConfig.DEFAULT_HOUND_DAMAGE)
            .add(Attributes.ARMOR,2).add(Attributes.MOVEMENT_SPEED,.4).add(Attributes.FOLLOW_RANGE,48)
            .add(ForgeMod.STEP_HEIGHT_ADDITION.get(),0.4);
    }
    @Override protected void registerGoals() { }
    @Override public void travel(Vec3 input) { super.travel(action()==STALK?input:Vec3.ZERO); }
    @Override protected void defineSynchedData() {
        super.defineSynchedData();this.entityData.define(ACTION,IDLE);this.entityData.define(STARTED,0L);
    }
    public int action() { return entityData.get(ACTION); }
    public float actionAge(float partial) { return Math.max(0,level().getGameTime()-entityData.get(STARTED)+partial); }
    @Nullable public UUID ownerId() { return ownerId; }
    @Nullable public YangJian ownerBoss() {
        if(ownerId==null || !(level() instanceof ServerLevel server))return null;
        Entity owner=server.getEntity(ownerId);return owner instanceof YangJian boss && boss.isAlive()?boss:null;
    }
    public void bind(YangJian owner,LivingEntity target) {
        ownerId=owner.getUUID();targetId=target.getUUID();initialize();setAction(STALK);
    }
    private void initialize() {
        if(initialized || level().isClientSide)return;
        initialized=true;getAttribute(Attributes.MAX_HEALTH).setBaseValue(MyriadConfig.houndHealth());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(MyriadConfig.houndDamage());setHealth(getMaxHealth());
    }
    public void coordinate(LivingEntity target,int side) {
        YangJian owner=ownerBoss();if(owner==null)return;
        Vec3 forward=target.position().subtract(owner.position()).multiply(1,0,1).normalize();
        flankPoint=owner.clampPoint(target.position().add(-forward.z*5*side,0,forward.x*5*side));
        targetId=target.getUUID();coordinatedTicks=10;delay=0;setNoGravity(false);setAction(STALK);
    }
    private void setAction(int action) { entityData.set(ACTION,action);entityData.set(STARTED,level().getGameTime()); }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();initialize();
        YangJian owner=ownerBoss();
        if(--life<=0 || owner!=null && (!owner.ownsHound(getUUID()) || owner.isTrialComplete() || owner.action()==YangJian.PHASE_CLEAR)) { discard();return; }
        if(owner==null) { getNavigation().stop();if(++orphanTicks>60)discard();return; }
        orphanTicks=0;
        LivingEntity target=target(owner);
        if(target==null) { getNavigation().stop();setNoGravity(false);setAction(IDLE);return; }
        setTarget(target);
        if(owner.isArenaBoss() && (getY()<owner.arenaCenter().getY()-1 || position().subtract(owner.clampPoint(position())).horizontalDistanceSqr()>1)) {
            setPos(owner.clampPoint(position()));setDeltaMovement(Vec3.ZERO);setAction(STALK);setNoGravity(false);
        }
        if(action()==IDLE)setAction(STALK);
        if(action()==STALK) {
            face(target.position());
            if(coordinatedTicks>0) {
                getNavigation().stop();
                Vec3 offset=flankPoint.subtract(position()).multiply(1,0,1);
                moveWithinArena(owner,offset.normalize().scale(Math.min(.68,offset.length())));
                if(--coordinatedTicks==0)preparePounce(owner,target);
            } else {
                getNavigation().moveTo(target,1.3);
                if(delay>0)delay--;
                if(delay==0 && distanceToSqr(target)<7*7 && hasLineOfSight(target))preparePounce(owner,target);
            }
            return;
        }
        getNavigation().stop();
        int age=(int)actionAge(0);
        if(action()==WINDUP && age>=WINDUP_TICKS) { setAction(POUNCE);setNoGravity(true); }
        else if(action()==POUNCE) {
            Vec3 old=position();double progress=Math.min(1,(age+1.0)/POUNCE_TICKS);
            Vec3 next=jumpStart.lerp(jumpEnd,progress).add(0,Math.sin(progress*Math.PI)*1.1,0);
            moveWithinArena(owner,next.subtract(position()));
            for(LivingEntity victim:level().getEntitiesOfClass(LivingEntity.class,new AABB(old,position()).inflate(1.1,1.5,1.1),owner::validTarget)) {
                double radius=.8+victim.getBbWidth()*.5;
                if(victim.getY()+victim.getBbHeight()<Math.min(old.y,getY()) || victim.getY()>Math.max(old.y,getY())+1.5)continue;
                if(CombatMath.segmentDistanceSquared(victim.getX(),victim.getZ(),old.x,old.z,getX(),getZ())<=radius*radius && pounceHits.add(victim.getUUID()) && hasLineOfSight(victim)) {
                    if(victim.hurt(damageSources().mobAttack(this),(float)getAttributeValue(Attributes.ATTACK_DAMAGE))) {
                        victim.addEffect(new MobEffectInstance(MyriadCalamity.ROAR_MARK.get(),MARK_TICKS,0,false,true,true),this);
                        victim.knockback(.35F,getX()-victim.getX(),getZ()-victim.getZ());victim.hurtMarked=true;
                        playSound(SoundEvents.WOLF_GROWL,1,.9F);
                    }
                }
            }
            if(age>=POUNCE_TICKS-1) { setAction(RETREAT);setNoGravity(false);setDeltaMovement(Vec3.ZERO); }
        } else if(action()==RETREAT) {
            if(age<12)moveWithinArena(owner,position().subtract(target.position()).multiply(1,0,1).normalize().scale(.2));
            if(age>=20) { delay=16;setAction(STALK); }
        }
    }
    @Nullable private LivingEntity target(YangJian owner) {
        // A live boss lock supersedes a stale saved victim.  Once a pounce has
        // begun, keep its original target for deterministic collision frames.
        LivingEntity forced=owner.activeTarget();
        if(action()!=WINDUP && action()!=POUNCE && forced!=null) {
            targetId=forced.getUUID();return forced;
        }
        if(level() instanceof ServerLevel server && targetId!=null) {
            Entity selected=server.getEntity(targetId);
            if(selected instanceof LivingEntity living && owner.validTarget(living))return living;
        }
        // Once a pounce starts it never jumps to a replacement victim.
        if(action()==WINDUP || action()==POUNCE)return null;
        LivingEntity nearest=null;double closest=Double.MAX_VALUE;
        for(Player player:level().players())if(owner.validTarget(player)) {
            double distance=distanceToSqr(player);
            if(distance<closest) { nearest=player;closest=distance; }
        }
        if(nearest!=null)targetId=nearest.getUUID();return nearest;
    }
    private void preparePounce(YangJian owner,LivingEntity target) {
        jumpStart=position();Vec3 desired=owner.clampPoint(target.position());
        Vec3 delta=desired.subtract(jumpStart);if(delta.horizontalDistance()>9)delta=delta.normalize().scale(9);
        jumpEnd=jumpStart.add(delta);pounceHits.clear();getNavigation().stop();setDeltaMovement(Vec3.ZERO);
        face(jumpEnd);setAction(WINDUP);playSound(SoundEvents.WOLF_GROWL,.9F,.65F);
    }
    private void moveWithinArena(YangJian owner,Vec3 delta) {
        Vec3 desired=position().add(delta),clamped=owner.clampPoint(desired);
        Vec3 next=owner.isArenaBoss()?new Vec3(clamped.x,Math.max(clamped.y,desired.y),clamped.z):desired;
        move(MoverType.SELF,next.subtract(position()));setDeltaMovement(Vec3.ZERO);hurtMarked=true;
    }
    private void face(Vec3 at) {
        Vec3 delta=at.subtract(position());float yaw=(float)(Math.atan2(delta.z,delta.x)*Mth.RAD_TO_DEG)-90;
        setYRot(yaw);yBodyRot=yaw;yHeadRot=yaw;
    }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean causeFallDamage(float distance,float multiplier,DamageSource source) { return false; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.WOLF_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.WOLF_DEATH; }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);if(ownerId!=null)tag.putUUID("YangJianOwner",ownerId);if(targetId!=null)tag.putUUID("HoundTarget",targetId);
        tag.putInt("HoundLife",life);tag.putBoolean("HoundInitialized",initialized);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);ownerId=tag.hasUUID("YangJianOwner")?tag.getUUID("YangJianOwner"):null;
        targetId=tag.hasUUID("HoundTarget")?tag.getUUID("HoundTarget"):null;life=Mth.clamp(tag.getInt("HoundLife"),0,1800);
        initialized=tag.getBoolean("HoundInitialized");setNoGravity(false);setAction(STALK);delay=25;coordinatedTicks=0;pounceHits.clear();
    }
}
