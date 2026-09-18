package net.xuwu.myriadcalamity.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.config.MyriadConfig;

/** Server-authoritative three-phase encounter. Only the final phase can be killed normally. */
public final class YangJian extends Monster {
    public static final int IDLE=0,COMBO=1,FOUR_COMBO=2,SIX_COMBO=3,THROW=4,THRUST=5,
        SUMMON_HOUND=6,COORDINATED=7,GUARD=8,COUNTER=9,PHASE_CLEAR=10,TRANSITION=12,
        AXE_SUMMON=13,AXE_SLAM=14,AXE_COMBO=15,FLYING_SWORDS=16,DRAW_SLASH=17,
        WHIP_SWEEP=18,WHIP_SPIN=19,LIGHTNING_THRUST=20,INVISIBLE_DASH=21,DELAYED_COMBO=22,
        THIRD_EYE_OPEN=23,EYE_BEAM=24,SWEEP_BEAM=25,TRACKING_BEAM=26,MYRIAD_SWORDS=27,
        SWORD_RAIN=28,RED_THUNDER=29,DIVINE_SWEEP=30,AERIAL_COMBO=31,DIVINE_JUDGEMENT=32;
    public static final int CLEAR_TICKS=60;
    public static final double ARENA_RADIUS=22;
    private static final EntityDataAccessor<Integer> ACTION=SynchedEntityData.defineId(YangJian.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE=SynchedEntityData.defineId(YangJian.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> WEAPON=SynchedEntityData.defineId(YangJian.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> STARTED=SynchedEntityData.defineId(YangJian.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> APPROACH_STARTED=SynchedEntityData.defineId(YangJian.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> GUARD_VALUE=SynchedEntityData.defineId(YangJian.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> GUARD_MAX=SynchedEntityData.defineId(YangJian.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> THROWN=SynchedEntityData.defineId(YangJian.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> AXE_TRAIL_CHARGES=SynchedEntityData.defineId(YangJian.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> COMPLETE=SynchedEntityData.defineId(YangJian.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ARENA_PREPARING=SynchedEntityData.defineId(YangJian.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<CompoundTag> PLAN=SynchedEntityData.defineId(YangJian.class,EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<CompoundTag> ARENA=SynchedEntityData.defineId(YangJian.class,EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Integer> BEAM=SynchedEntityData.defineId(YangJian.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CLONE_BEAM=SynchedEntityData.defineId(YangJian.class,EntityDataSerializers.INT);
    private static final int SWEEP_BEAM_SWEEP_TICKS=32;
    private static final int SWEEP_BEAM_HALF_TICKS=SWEEP_BEAM_SWEEP_TICKS/2;
    private static final int SWEEP_BEAM_PASS_COUNT=6;
    /** Three body sweeps start one full sweep apart; the last clone finishes the final half. */
    private static final int SWEEP_BEAM_TOTAL_ACTIVE_TICKS=SWEEP_BEAM_SWEEP_TICKS*3+SWEEP_BEAM_HALF_TICKS;
    /** The aerial burst and the ultimate's two sword segments share one dense downpour shape. */
    private static final int AERIAL_RAIN_START=24,AERIAL_RAIN_WARNING=18;
    private static final int JUDGEMENT_RAIN_START=50,JUDGEMENT_DOWNPOUR_START=264;
    private final ServerBossEvent healthBar=new ServerBossEvent(Component.translatable("boss.myriad_calamity.yang_jian_p1"),BossEvent.BossBarColor.WHITE,BossEvent.BossBarOverlay.PROGRESS);
    private final ServerBossEvent guardBar=new ServerBossEvent(Component.translatable("boss.myriad_calamity.yang_jian_guard"),BossEvent.BossBarColor.BLUE,BossEvent.BossBarOverlay.NOTCHED_10);
    private final int[] cooldowns=new int[33];
    private final Set<UUID> hitThisStep=new HashSet<>();
    private final Map<UUID,Integer> stunnedAttackers=new HashMap<>();
    private final Map<UUID,Double> transitionDistances=new HashMap<>();
    @Nullable private UUID arenaOwner,lockedTarget,houndId,bladeId;
    @Nullable private BlockPos arenaCenter;
    private boolean initialized,applyingDamage,counterPending;
    private int idleDelay=30,previousAction,serverStep=-1,guardPressure,guardRegenRemaining,clearRemaining,transitionRemaining,chainCount,chainLimit,preparationRemaining;
    private boolean phaseTwoOpening,axeFollowupRequired,phaseThreeOpeningSweep;
    private Vec3 invisibleAim=Vec3.ZERO,invisibleReappearance=Vec3.ZERO;
    private Vec3 flightAnchor=Vec3.ZERO;
    private int sweepBeamPass=-1;
    private Vec3 sweepBodyPosition=Vec3.ZERO,sweepClonePosition=Vec3.ZERO;
    private double patternRotation;
    private long lastMeleeTick=-100;
    private int approachCooldown;
    private Vec3 approachStart=Vec3.ZERO,approachEnd=Vec3.ZERO;

    public YangJian(EntityType<? extends YangJian> type,Level level) {
        super(type,level);setPersistenceRequired();xpReward=0;
    }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,MyriadConfig.DEFAULT_YANG_JIAN_HEALTH).add(Attributes.ATTACK_DAMAGE,MyriadConfig.DEFAULT_YANG_JIAN_DAMAGE)
            .add(Attributes.ARMOR,7).add(Attributes.MOVEMENT_SPEED,.34).add(Attributes.FOLLOW_RANGE,52)
            .add(Attributes.KNOCKBACK_RESISTANCE,1).add(Attributes.STEP_HEIGHT,1.2);
    }
    @Override protected void registerGoals() { /* One explicit state machine owns all targeting and movement. */ }
    @Override public void travel(Vec3 input) {
        // Every intentional movement is a server-owned step or skill; retain gravity but no walk input.
        super.travel(Vec3.ZERO);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);builder.define(ACTION,IDLE);builder.define(PHASE,1);builder.define(WEAPON,0);builder.define(STARTED,0L);
        builder.define(APPROACH_STARTED,-1L);
        builder.define(GUARD_VALUE,180F);builder.define(GUARD_MAX,180F);builder.define(THROWN,false);builder.define(AXE_TRAIL_CHARGES,0);
        builder.define(COMPLETE,false);builder.define(ARENA_PREPARING,false);builder.define(PLAN,new CompoundTag());builder.define(ARENA,new CompoundTag());
        builder.define(BEAM,-1);builder.define(CLONE_BEAM,-1);
    }
    public int action() { return entityData.get(ACTION); }
    public int phase() { return entityData.get(PHASE); }
    public int weapon() { return entityData.get(WEAPON); }
    public boolean isTransitioning() { return action()==TRANSITION || action()==THIRD_EYE_OPEN; }
    public long attackSequence() { return entityData.get(STARTED); }
    public boolean isApproaching() {
        return action()==IDLE && !isTrialComplete() && entityData.get(APPROACH_STARTED)>=0
            && approachAge(0)>=0 && approachAge(0)<YangJianFootwork.DURATION;
    }
    public float approachAge(float partial) { return level().getGameTime()-entityData.get(APPROACH_STARTED)+partial; }
    public Vec3 eyeBeamOrigin() { return position().add(0,2.98,0).add(getLookAngle().multiply(.3,0,.3)); }
    public float actionAge(float partial) { return Math.max(0,level().getGameTime()-entityData.get(STARTED)+partial); }
    public int attackWindup() { YangJianSkill skill=YangJianSkill.forAction(action());return skill==null?0:skill.stepWindup(0); }
    public int comboStep() { return entityData.get(PLAN).getInt("step"); }
    public int stepWindup() { return entityData.get(PLAN).getInt("w"); }
    public float stepAge(float partial) { return level().getGameTime()-entityData.get(PLAN).getLong("t")+partial; }
    public float guard() { return entityData.get(GUARD_VALUE); }
    public float maxGuard() { return entityData.get(GUARD_MAX); }
    public boolean hasGuard() { return guard()>0; }
    public int guardRegenRemaining() { return guardRegenRemaining; }
    public int axeTrailCharges() { return entityData.get(AXE_TRAIL_CHARGES); }
    public Vec3 attackPoint() { return point(entityData.get(PLAN),"p"); }
    public Vec3 dashStart() { return point(entityData.get(PLAN),"s"); }
    public Vec3 dashEnd() { return point(entityData.get(PLAN),"e"); }
    public Vec3 transitionCenter() { return attackPoint(); }
    public double transitionRadius() { return YangJianTransition.RADIUS; }
    public float telegraphRadius() { return entityData.get(PLAN).getFloat("r"); }
    public int telegraphShape() { return entityData.get(PLAN).getInt("shape"); }
    public float telegraphAngle() { return entityData.get(PLAN).getFloat("angle"); }
    public boolean weaponThrown() { return entityData.get(THROWN); }
    public boolean warningVisible() {
        CompoundTag plan=entityData.get(PLAN);
        return plan.getInt("a")==action() && plan.contains("t") && plan.getFloat("r")>0
            && stepAge(0)>=0 && stepAge(0)<stepWindup() && telegraphShape()>0 && action()!=PHASE_CLEAR && !isTransitioning();
    }
    public boolean isTrialComplete() { return entityData.get(COMPLETE); }
    public boolean isArenaPreparing() { return entityData.get(ARENA_PREPARING); }
    public boolean hasSweepClone() { return entityData.get(PLAN).getBoolean("cloneReady"); }
    public Vec3 sweepClone() { return point(entityData.get(PLAN),"clone"); }
    public float sweepCloneYaw() { return entityData.get(PLAN).getFloat("cloneYaw"); }
    @Nullable public YangJianHazard currentBeam() { return liveBeam(entityData.get(BEAM)); }
    @Nullable public YangJianHazard cloneBeam() { return liveBeam(entityData.get(CLONE_BEAM)); }
    @Nullable private YangJianHazard liveBeam(int id) {
        return level().getEntity(id) instanceof YangJianHazard beam && beam.isAlive() && beam.kind()==1
            && (beam.warning(0) || beam.active(0))?beam:null;
    }
    public boolean isArenaBoss() { return entityData.get(ARENA).contains("center"); }
    public BlockPos arenaCenter() { return isArenaBoss()?BlockPos.of(entityData.get(ARENA).getLong("center")):blockPosition(); }
    @Nullable public UUID arenaOwner() { return arenaOwner; }
    public void setArena(BlockPos center,UUID owner) {
        arenaCenter=center.immutable();arenaOwner=owner;
        CompoundTag tag=new CompoundTag();tag.putLong("center",center.asLong());tag.putUUID("owner",owner);entityData.set(ARENA,tag);
        initialize();
    }
    /** Hide and hold the arena boss during the entrance countdown so its music can start on arrival. */
    public void prepareForArena(int ticks) {
        if (level().isClientSide) return;
        preparationRemaining=Math.max(0,ticks);
        entityData.set(ARENA_PREPARING,preparationRemaining>0);
        if (preparationRemaining>0) {
            setInvisible(true);setNoGravity(true);setTarget(null);setDeltaMovement(Vec3.ZERO);
        }
    }
    private void initialize() {
        if(initialized || level().isClientSide)return;
        initialized=true;
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(MyriadConfig.yangJianHealth());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(MyriadConfig.yangJianDamage());
        setHealth(getMaxHealth());entityData.set(GUARD_MAX,MyriadConfig.yangJianGuard());entityData.set(GUARD_VALUE,maxGuard());
    }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();initialize();
        if(!isAlive())return;
        if(preparationRemaining>0) {
            preparationRemaining--;setTarget(null);setDeltaMovement(Vec3.ZERO);setNoGravity(true);setInvisible(true);
            if(preparationRemaining==0) { entityData.set(ARENA_PREPARING,false);setNoGravity(false);setInvisible(false);idleDelay=8; }
            return;
        }
        tickStunnedAttackers();
        for(int i=0;i<cooldowns.length;i++)if(cooldowns[i]>0)cooldowns[i]--;
        if(approachCooldown>0)approachCooldown--;
        if(isArenaBoss())confineToArena();
        tickGuardRegen();
        tickHealthTransitions();
        if(tickCount%10==0)updateBars();
        if(isTransitioning()) {
            getNavigation().stop();setDeltaMovement(Vec3.ZERO);
            if(action()==TRANSITION) { tickThunderAxeTransition();return; }
            if(transitionRemaining>0 && --transitionRemaining==0) {
                enterPhaseThree();
            }
            return;
        }
        if(action()==PHASE_CLEAR) {
            getNavigation().stop();setDeltaMovement(Vec3.ZERO);
            if(clearRemaining>0 && --clearRemaining==0) {
                entityData.set(COMPLETE,true);healthBar.removeAllPlayers();guardBar.removeAllPlayers();
            }
            // A command-summoned trial has no arena manager, so finish its display cleanly itself.
            if(isTrialComplete() && !isArenaBoss() && actionAge(0)>CLEAR_TICKS+100)discard();
            return;
        }
        LivingEntity target=action()==IDLE && entityData.get(APPROACH_STARTED)<0?chooseTarget():lockedEntity();
        if(target==null && action()!=GUARD) {
            if(action()!=IDLE || entityData.get(APPROACH_STARTED)>=0) { clearThirdHazards(true);cancelAttack(); }
            getNavigation().stop();setDeltaMovement(new Vec3(0,getDeltaMovement().y,0));return;
        }
        if(target!=null)setTarget(target);
        if(phase()==3 && target!=null && currentBeam()==null) {
            Vec3 towardEye=target.position().add(0,target.getBbHeight()*.5,0).subtract(eyeBeamOrigin());
            setXRot((float)(-Math.atan2(towardEye.y,towardEye.horizontalDistance())*Mth.RAD_TO_DEG));
        }
        if(action()==IDLE) {
            getNavigation().stop();setDeltaMovement(new Vec3(0,getDeltaMovement().y,0));
            if(entityData.get(APPROACH_STARTED)>=0 && tickApproach(target))return;
            face(target.position());
            if(idleDelay>0) { idleDelay--;return; }
            double distance=distanceTo(target);
            if(!(phase()==2 && phaseTwoOpening) && YangJianFootwork.canStart(target.position().subtract(position()).horizontalDistance(),phase(),idleDelay,approachCooldown)
                    && beginApproach(target))return;
            YangJianSkill choice=selectSkill(target,distance);
            if(choice!=null)begin(choice,target);
            return;
        }
        getNavigation().stop();
        YangJianSkill skill=YangJianSkill.forAction(action());
        int age=(int)actionAge(0);
        if(action()==GUARD) {
            if(target!=null)face(target.position());
            if(age>=8 && counterPending && target!=null) { counterPending=false;begin(phase()==2?YangJianSkill.DRAW_SLASH:YangJianSkill.COUNTER,target); }
            else if(age>=16)finishAttack();
            return;
        }
        if(skill==null) { cancelAttack();return; }
        if(skill.phase()==3)tickPhaseThree(skill,age,target);
        else if(skill.phase()==2)tickPhaseTwo(skill,age,target);
        else if(skill.meleeCombo() || skill==YangJianSkill.COORDINATED)tickCombo(skill,age,target);
        else if(skill==YangJianSkill.THRUST || skill==YangJianSkill.COUNTER) {
            int windup=skill.stepWindup(0);
            if(age>=windup && age<windup+skill.stepActive(0))dashTick(age-windup,skill.stepActive(0),skill==YangJianSkill.COUNTER?14:12);
        } else if(skill==YangJianSkill.THROW)tickThrow(age,target);
        else if(skill==YangJianSkill.SUMMON_HOUND && age==skill.stepWindup(0))summonHound(target);
        if(!isAlive() || isTransitioning() || isTrialComplete() || action()!=skill.action())return;
        if(phase()==2 && age==skill.activeEnd()+YangJianSkill.PHASE_TWO_LINK_RECOVERY && tryPhaseTwoChain(target))return;
        if(age>=skill.duration())finishAttack();
    }

    private boolean beginApproach(LivingEntity target) {
        if(!onGround())return false;
        Vec3 toward=target.position().subtract(position()).multiply(1,0,1);
        double length=YangJianFootwork.distance(toward.length(),phase());
        Vec3 direction=toward.normalize(),start=position(),end=start;
        approachCooldown=YangJianFootwork.COOLDOWN;
        // Check clearance and floor along the entire short route, not just its destination.
        for(double at=.4;at<=length+.399;at+=.4) {
            Vec3 point=clampPoint(start.add(direction.scale(Math.min(at,length))));
            point=new Vec3(point.x,start.y,point.z);
            AABB box=getBoundingBox().move(point.subtract(start));
            if(!level().hasChunkAt(BlockPos.containing(point)) || !level().noCollision(this,box)
                    || level().noCollision(this,box.move(0,-.65,0)))break;
            end=point;
        }
        if(end.subtract(start).horizontalDistanceSqr()<.65*.65)return false;
        approachStart=start;approachEnd=end;lockedTarget=target.getUUID();face(end);
        entityData.set(APPROACH_STARTED,level().getGameTime());
        DivineFlyingSword.launchStep((ServerLevel)level(),this,target);
        return true;
    }
    private boolean tickApproach(LivingEntity target) {
        int age=(int)approachAge(0);
        if(phase()==3 && target!=null)face(target.position());
        if(age>=YangJianFootwork.DURATION) { endApproach();return false; }
        if(age<YangJianFootwork.WINDUP || age>=YangJianFootwork.MOVE_END)return true;
        Vec3 from=position(),toward=target.position().subtract(from).multiply(1,0,1);
        Vec3 desired=approachStart.lerp(approachEnd,YangJianFootwork.progress(age+1));
        Vec3 move=desired.subtract(from).multiply(1,0,1);
        double allowed=YangJianFootwork.distance(toward.length(),phase());
        if(allowed<.05 || toward.dot(move)<0) { endApproach();return true; }
        if(move.length()>allowed)move=move.normalize().scale(allowed);
        Vec3 next=from.add(move);
        if(!level().hasChunkAt(BlockPos.containing(next)) || level().noCollision(this,getBoundingBox().move(move).move(0,-.65,0))) {
            endApproach();return true;
        }
        moveSafely(move);
        if(position().subtract(from).horizontalDistanceSqr()<move.horizontalDistanceSqr()*.16) { endApproach();return true; }
        if(age==YangJianFootwork.WINDUP || age==YangJianFootwork.MOVE_END-1)
            ((ServerLevel)level()).sendParticles(ParticleTypes.CLOUD,getX(),getY()+.08,getZ(),4,.22,.04,.22,.008);
        return true;
    }
    private void endApproach() {
        if(entityData.get(APPROACH_STARTED)>=0)approachCooldown=YangJianFootwork.COOLDOWN;
        entityData.set(APPROACH_STARTED,-1L);approachStart=approachEnd=Vec3.ZERO;
        setDeltaMovement(new Vec3(0,getDeltaMovement().y,0));
    }

    @Nullable private LivingEntity chooseTarget() {
        LivingEntity forced=getTarget();
        if(forced!=null && validTarget(forced))return forced;
        Player nearest=null,marked=null;double best=Double.MAX_VALUE,markDistance=Double.MAX_VALUE;
        for(Player candidate:level().players())if(validTarget(candidate)) {
            double distance=distanceToSqr(candidate);
            if(distance<best) { nearest=candidate;best=distance; }
            if(candidate.hasEffect(MyriadCalamity.ROAR_MARK) && distance<markDistance) {
                marked=candidate;markDistance=distance;
            }
        }
        return phase()==1 && marked!=null && cooldowns[THRUST]==0?marked:nearest;
    }
    @Nullable private LivingEntity lockedEntity() {
        if(lockedTarget==null || !(level() instanceof ServerLevel server))return null;
        Entity entity=server.getEntity(lockedTarget);
        return entity instanceof LivingEntity living && validTarget(living)?living:null;
    }
    /** The living entity bound to the current skill, independent of Mob.target. */
    @Nullable LivingEntity activeTarget() {
        LivingEntity locked=lockedEntity();
        if(locked!=null)return locked;
        return getTarget() instanceof LivingEntity living && validTarget(living)?living:null;
    }
    public boolean validTarget(LivingEntity target) {
        if(target==null || target==this || !target.isAlive() || target.level()!=level())return false;
        if(target instanceof CelestialHound hound && getUUID().equals(hound.ownerId()))return false;
        if(target instanceof Player player && (player.isSpectator() || player.isCreative()))return false;
        if(isArenaBoss()) {
            Vec3 center=Vec3.atBottomCenterOf(arenaCenter().above());
            return target.position().subtract(center).horizontalDistanceSqr()<24*24 && Math.abs(target.getY()-center.y)<14;
        }
        return distanceToSqr(target)<52*52;
    }
    @Nullable private YangJianSkill selectSkill(LivingEntity target,double distance) {
        if(phase()==2 && phaseTwoOpening) { phaseTwoOpening=false;return YangJianSkill.AXE_SUMMON; }
        // The eye-opening transition always resolves into the aerial sweep.  It
        // is selected before the normal weighted pool so another P3 skill cannot
        // interrupt the reveal's authored first attack.
        if(phase()==3 && phaseThreeOpeningSweep) {
            phaseThreeOpeningSweep=false;
            return YangJianSkill.SWEEP_BEAM;
        }
        if(phase()==2 && axeFollowupRequired) {
            // Do not let a newly materialized axe be replaced before it gets one
            // complete authored axe action.  The lock is consumed when the
            // forced action is selected, whether it chains immediately or after
            // the normal recovery window.
            axeFollowupRequired=false;
            return YangJianSkill.axeFollowup(distance);
        }
        double total=0;boolean dog=hound()!=null,marked=target.hasEffect(MyriadCalamity.ROAR_MARK);
        double ratio=phase()==3?getHealth()/getMaxHealth():Math.min(getHealth()/getMaxHealth(),guard()/Math.max(1,maxGuard()));
        boolean pressured=level().getGameTime()-lastMeleeTick<35 && guardPressure>=2;
        for(YangJianSkill skill:YangJianSkill.values())total+=skill.weightForPhase(phase(),weapon(),distance,target.isUsingItem(),marked,dog,ratio,previousAction,cooldowns[skill.action()],pressured);
        if(total<=0)return null;
        double roll=random.nextDouble()*total;
        for(YangJianSkill skill:YangJianSkill.values()) {
            roll-=skill.weightForPhase(phase(),weapon(),distance,target.isUsingItem(),marked,dog,ratio,previousAction,cooldowns[skill.action()],pressured);
            if(roll<0)return skill;
        }
        return null;
    }
    private void begin(YangJianSkill skill,LivingEntity target) {
        endApproach();
        // A new red-thunder cast replaces the previous field of lingering
        // impact zones. Other skills leave those zones in place.
        if(skill==YangJianSkill.RED_THUNDER)clearThirdHazards(true);
        if(phase()==2 && chainCount==0) { chainCount=1;chainLimit=2+random.nextInt(2); }
        entityData.set(PLAN,new CompoundTag());setInvisible(false);setNoGravity(false);
        entityData.set(BEAM,-1);entityData.set(CLONE_BEAM,-1);
        entityData.set(ACTION,skill.action());entityData.set(STARTED,level().getGameTime());
        lockedTarget=target.getUUID();serverStep=-1;hitThisStep.clear();getNavigation().stop();
        setDeltaMovement(new Vec3(0,getDeltaMovement().y,0));cooldowns[skill.action()]=skill.cooldown();
        if(skill.phase()==3) { beginPhaseThree(skill,target);return; }
        if(skill.phase()==2) { beginPhaseTwo(skill,target);return; }
        if(skill==YangJianSkill.THRUST || skill==YangJianSkill.COUNTER)planDash(target,skill.stepWindup(0),0,skill==YangJianSkill.COUNTER?9:17);
        else if(skill==YangJianSkill.THROW) {
            Vec3 away=position().subtract(target.position()).multiply(1,0,1).normalize();
            moveSafely(away.scale(1.8));face(target.position());
            publishPlan(0,16,1.2F,position(),aim(target),target.position());
        } else if(skill.meleeCombo() || skill==YangJianSkill.COORDINATED) {
            planComboStep(skill,0,target);
            if(skill==YangJianSkill.COORDINATED) {
                CelestialHound dog=hound();if(dog!=null)dog.coordinate(target,(random.nextBoolean()?1:-1));
            }
        } else publishPlan(0,skill.stepWindup(0),0,position(),position(),position());
        if(skill==YangJianSkill.THRUST || skill==YangJianSkill.COUNTER)playSound(SoundEvents.TRIDENT_RETURN,1,.65F);
        else if(skill==YangJianSkill.SUMMON_HOUND)playSound(SoundEvents.WOLF_GROWL,1,.6F);
    }
    private void beginPhaseThree(YangJianSkill skill,LivingEntity target) {
        entityData.set(WEAPON,0);flightAnchor=clampPoint(position());patternRotation=random.nextDouble()*Math.PI*2;face(target.position());
        publishPlan(0,skill.stepWindup(0),0,position(),aim(target),aim(target),0,0);
        if(skill==YangJianSkill.SWEEP_BEAM)
            beginSweepBeam(target);
        else if(skill==YangJianSkill.EYE_BEAM || skill==YangJianSkill.TRACKING_BEAM)
            castBeam(target,skill.stepWindup(0),skill.stepActive(0),skill==YangJianSkill.TRACKING_BEAM?2:0,skill.radius());
        else if(skill==YangJianSkill.DIVINE_SWEEP) {
            boolean circle=distanceTo(target)<6 && random.nextBoolean();
            Vec3 end=position().add(aim(target).subtract(position()).multiply(1,0,1).normalize().scale(skill.radius()));
            publishPlan(0,20,circle?10:12,position(),end,position(),circle?2:1,circle?360:170);
        } else if(skill==YangJianSkill.DIVINE_JUDGEMENT) {
            healthBar.setName(Component.translatable("boss.myriad_calamity.yang_jian_judgement"));
            playSound(SoundEvents.BEACON_ACTIVATE,1.8F,.5F);
        }
    }
    private void castBeam(LivingEntity target,int warning,int duration,int mode,double radius) {
        double range=mode==0?24:42;
        float damage=MyriadConfig.scaleYangJianDamage(mode==0?14:7);
        castBeamAt(target,warning,duration,mode,radius,range,damage);
    }
    private void castBeamAt(LivingEntity target,int warning,int duration,int mode,double radius,double range,float damage) {
        Vec3 aim=target.position().add(0,target.getBbHeight()*.5,0);
        YangJianHazard beam=castBeamAt(target,warning,duration,mode,radius,range,damage,eyeBeamOrigin(),aim);
        entityData.set(BEAM,beam==null?-1:beam.getId());
    }
    private void castBeamAt(LivingEntity target,int warning,int duration,int mode,double radius,double range,float damage,Vec3 aim) {
        YangJianHazard beam=castBeamAt(target,warning,duration,mode,radius,range,damage,eyeBeamOrigin(),aim);
        entityData.set(BEAM,beam==null?-1:beam.getId());
    }
    @Nullable private YangJianHazard castBeamAt(LivingEntity target,int warning,int duration,int mode,double radius,double range,float damage,Vec3 start,Vec3 aim) {
        Vec3 direction=aim.subtract(start).normalize();
        if(YangJianHazardMath.sweeping(mode)) {
            // Publish the beam at the counter-clockwise end of the arc. The hazard then advances
            // clockwise, the same way every other rotating attack turns.
            double turn=YangJianHazardMath.sweepTurn(mode);
            double angle=-YangJianHazardMath.SWEEP_DIRECTION*turn*duration*.5,c=Math.cos(angle),s=Math.sin(angle);
            direction=new Vec3(direction.x*c-direction.z*s,direction.y,direction.x*s+direction.z*c);
        }
        return YangJianHazard.beam((ServerLevel)level(),this,start,direction,range,radius,warning,duration,mode,damage);
    }
    private void beginSweepBeam(LivingEntity target) {
        setNoGravity(true);sweepBeamPass=-1;sweepBodyPosition=position();sweepClonePosition=Vec3.ZERO;
        CompoundTag plan=entityData.get(PLAN).copy();plan.putInt("sweepPass",-1);plan.putBoolean("cloneReady",false);
        writePoint(plan,"body",sweepBodyPosition);plan.putFloat("bodyYaw",yBodyRot);entityData.set(PLAN,plan);
        startSweepPass(target,0);
    }
    private void tickSweepBeam(int age,LivingEntity target) {
        setNoGravity(true);
        if(target==null)return;
        int windup=YangJianSkill.SWEEP_BEAM.stepWindup(0);
        while(sweepBeamPass+1<SWEEP_BEAM_PASS_COUNT && age>=windup+(sweepBeamPass+1)*SWEEP_BEAM_HALF_TICKS)
            startSweepPass(target,sweepBeamPass+1);
        if(age>=windup+SWEEP_BEAM_TOTAL_ACTIVE_TICKS && hasSweepClone()) {
            CompoundTag plan=entityData.get(PLAN).copy();plan.putBoolean("cloneReady",false);entityData.set(PLAN,plan);
        }
    }
    private void startSweepPass(LivingEntity target,int pass) {
        if(pass<0 || pass>=SWEEP_BEAM_PASS_COUNT)return;
        boolean body=(pass&1)==0;int index=pass/2;
        if(body) {
            Vec3 destination=index==0?position():sweepBodyDestination(index,target);
            moveElevated(new Vec3(destination.x,flightAnchor.y,destination.z));setNoGravity(true);face(target.position());
            sweepBodyPosition=position();
        } else {
            sweepClonePosition=sweepCloneDestination(index,target);
        }
        Vec3 origin=body?eyeBeamOrigin():sweepEyeOrigin(sweepClonePosition);
        Vec3 targetAim=target.position().add(0,target.getBbHeight()*.5,0);
        int warning=pass==0?YangJianSkill.SWEEP_BEAM.stepWindup(0):0;
        YangJianHazard beam=castBeamAt(target,warning,SWEEP_BEAM_SWEEP_TICKS,3,.65F,42,MyriadConfig.scaleYangJianDamage(7),origin,targetAim);
        entityData.set(body?BEAM:CLONE_BEAM,beam==null?-1:beam.getId());
        CompoundTag plan=entityData.get(PLAN).copy();plan.putInt("sweepPass",pass);plan.putInt("activeActor",body?0:1);
        writePoint(plan,"body",sweepBodyPosition);
        if(!body) {
            writePoint(plan,"clone",sweepClonePosition);plan.putBoolean("cloneReady",true);
            plan.putFloat("cloneYaw",sweepYaw(sweepClonePosition,target.position()));
            mist(sweepClonePosition,10);playSound(SoundEvents.ENDERMAN_TELEPORT,1.1F,.9F+index*.04F);
        } else if(index>0) {
            mist(sweepBodyPosition,10);playSound(SoundEvents.ENDERMAN_TELEPORT,1.15F,.82F+index*.05F);
        }
        entityData.set(PLAN,plan);sweepBeamPass=pass;
    }
    private Vec3 sweepBodyDestination(int index,LivingEntity target) {
        Vec3 center=aim(target);double angle=patternRotation+index*Math.PI*2/3+.35;
        double radius=8.5+index*1.2;
        Vec3 destination=clampPoint(new Vec3(center.x+Math.cos(angle)*radius,flightAnchor.y,center.z+Math.sin(angle)*radius));
        if(destination.distanceToSqr(sweepBodyPosition)<16) {
            angle+=Math.PI*.65;
            destination=clampPoint(new Vec3(center.x+Math.cos(angle)*radius,flightAnchor.y,center.z+Math.sin(angle)*radius));
        }
        return destination;
    }
    private Vec3 sweepCloneDestination(int index,LivingEntity target) {
        Vec3 toward=aim(target).subtract(sweepBodyPosition).multiply(1,0,1);
        if(toward.lengthSqr()<1E-6)toward=getLookAngle().multiply(1,0,1);
        toward=toward.normalize();Vec3 side=new Vec3(-toward.z,0,toward.x);
        double lateral=index%2==0?4.2:-4.2,forward=index%2==0?-1.2:1.2;
        Vec3 destination=clampPoint(sweepBodyPosition.add(side.scale(lateral)).add(toward.scale(forward)));
        if(destination.distanceToSqr(sweepBodyPosition)<4)destination=clampPoint(sweepBodyPosition.add(side.scale(index%2==0?3.2:-3.2)));
        return new Vec3(destination.x,flightAnchor.y,destination.z);
    }
    private Vec3 sweepEyeOrigin(Vec3 base) { return base.add(0,2.98,0); }
    private static float sweepYaw(Vec3 from,Vec3 to) {
        Vec3 direction=to.subtract(from);return (float)(Math.atan2(direction.z,direction.x)*Mth.RAD_TO_DEG)-90;
    }
    private void swordWave(LivingEntity target,int wave,int count,int warning,boolean rain) {
        Vec3 center=aim(target),origin=isArenaBoss()?Vec3.atBottomCenterOf(arenaCenter().above()):flightAnchor;
        float radius=rain?2.2F:2.1F;
        // Pack the layout before publishing it. Clamping separate circles at the rim would stack them.
        var points=YangJianPhaseThree.pattern(center.x-origin.x,center.z-origin.z,
            target.getDeltaMovement().x,target.getDeltaMovement().z,rain?0:wave,
            (int)(patternRotation*100)+wave*7,count,ARENA_RADIUS,radius);
        for(var point:points) {
            Vec3 ground=new Vec3(origin.x+point.x(),center.y,origin.z+point.z());
            YangJianHazard.strike((ServerLevel)level(),this,ground,radius,warning,true,MyriadConfig.scaleYangJianDamage(10));
        }
    }
    private void thunderWave(LivingEntity target,int wave,int warning) { thunderWave(target,wave,warning,false); }
    private void thunderWave(LivingEntity target,int wave,int warning,boolean persistent) {
        Vec3 center=aim(target);
        for(int i=0;i<3;i++) {
            var offset=YangJianHazardMath.wavePoint(i,3,patternRotation+wave*.9,6.5);
            Vec3 point=i==0?center:clampPoint(center.add(offset.x(),0,offset.z()));
            if(persistent)YangJianHazard.persistentStrike((ServerLevel)level(),this,point,2.8F,warning,false,MyriadConfig.scaleYangJianDamage(14));
            else YangJianHazard.strike((ServerLevel)level(),this,point,2.8F,warning,false,MyriadConfig.scaleYangJianDamage(14));
        }
    }
    private void airHeight(double height) { setNoGravity(height>.001);moveElevated(flightAnchor.add(0,height,0)); }
    private void planDive(int stage,int windup,float radius,LivingEntity target) {
        Vec3 at=aim(target);face(at);hitThisStep.clear();publishPlan(stage,windup,radius,position(),at,at,2,360);
    }
    private void dive(int age,int first,int travel,float damage) {
        if(age>=first && age<first+travel)moveElevated(dashStart().lerp(attackPoint(),(age-first+1D)/travel));
        if(age==first+travel) {
            moveElevated(attackPoint());
            if(position().distanceToSqr(attackPoint())<2.25)radialHit(attackPoint(),telegraphRadius(),damage);
            flightAnchor=clampPoint(position());setNoGravity(false);
        }
    }
    private void thirdStage(int index) { publishPlan(index,0,0,position(),position(),position(),0,0); }
    private void tickPhaseThree(YangJianSkill skill,int age,LivingEntity target) {
        YangJianHazard beam=currentBeam();
        if(beam!=null) {
            // A fixed eye shot keeps the player in its sight for the whole charge and only
            // freezes when the warning ends, so it fires along the live position instead of
            // the spot the player occupied when the windup began.
            if(target!=null && YangJianHazardMath.tracksWhileWarning(beam.mode()))
                beam.aimWhileWarning(eyeBeamOrigin(),target.position().add(0,target.getBbHeight()*.5,0));
            // A sweep keeps its cast heading: the head follows the ray across
            // that fixed arc. Directed shots turn the body with the live ray.
            if(!YangJianHazardMath.sweeping(beam.mode())) {
                Vec3 direction=beam.visualDirection(0);face(position().add(direction));
                setXRot((float)(-Math.atan2(direction.y,direction.horizontalDistance())*Mth.RAD_TO_DEG));
            }
        } else if(skill==YangJianSkill.DIVINE_SWEEP && age<skill.activeEnd()) {
            // The weapon and damage arc share the lock published at windup.
            face(dashEnd());
        } else if(target!=null)face(target.position());
        switch(skill) {
            case SWEEP_BEAM -> tickSweepBeam(age,target);
            case TRACKING_BEAM -> {
                // Directional tracking is handled by the hazard; the body
                // orientation above remains synchronized with its target.
            }
            case MYRIAD_SWORDS -> {
                if(age<=20)airHeight(5.5*age/20D);
                if(age>=YangJianEffects.MYRIAD_START_TICK && age<=YangJianEffects.MYRIAD_END_TICK
                        && (age-YangJianEffects.MYRIAD_START_TICK)%YangJianEffects.MYRIAD_WAVE_INTERVAL==0) {
                    int wave=(age-YangJianEffects.MYRIAD_START_TICK)/YangJianEffects.MYRIAD_WAVE_INTERVAL;thirdStage(wave);
                    DivineFlyingSword.launchMyriad((ServerLevel)level(),this,target,wave);
                }
                if(age>=118 && age<=138)airHeight(5.5*(138-age)/20D);
            }
            case SWORD_RAIN -> {
                // A continuous downpour: a pair of blades lands every four ticks for the whole
                // window instead of four discrete waves, each still announced by its own warning.
                if(YangJianSkill.swordRainDropsAt(age,skill.activeEnd())) {
                    int drop=(age-YangJianSkill.SWORD_RAIN_FIRST)/YangJianSkill.SWORD_RAIN_INTERVAL;
                    thirdStage(drop);
                    swordWave(target,drop,YangJianSkill.SWORD_RAIN_PER_DROP,YangJianSkill.SWORD_RAIN_WARNING,true);
                }
            }
            case RED_THUNDER -> {
                if(age>=14 && age<=56 && (age-14)%14==0) { int wave=(age-14)/14;thirdStage(wave);thunderWave(target,wave,16,true); }
            }
            case DIVINE_SWEEP -> {
                int active=age-20;
                if(active>=0 && active<10) {
                    double arc=Math.toRadians(telegraphAngle());
                    divineArcHit(heading(),YangJianSkill.sweepRelative(arc,active/10D),YangJianSkill.sweepRelative(arc,(active+1)/10D),telegraphRadius(),15);
                    if(active==0)playSound(SoundEvents.PLAYER_ATTACK_SWEEP,1.8F,.6F);
                }
            }
            case AERIAL_COMBO -> {
                if(age<=20)airHeight(4.5*age/20D);
                // The aerial sword rain is a short, dense burst of three close waves.
                if(YangJianSkill.rainBurstAt(age,AERIAL_RAIN_START)) {
                    int wave=YangJianSkill.rainBurstWave(age,AERIAL_RAIN_START);thirdStage(wave);
                    swordWave(target,wave,YangJianSkill.RAIN_BURST_BLADES,
                        YangJianSkill.rainBurstWarning(AERIAL_RAIN_WARNING,wave),false);
                }
                if(age==60) { thirdStage(1);castBeam(target,16,22,0,YangJianSkill.EYE_BEAM.radius()); }
                if(age==104)planDive(2,20,4.8F,target);
                dive(age,124,6,16);
                if(age==140) { thirdStage(3);thunderWave(target,1,18); }
            }
            case DIVINE_JUDGEMENT -> {
                if(age<=24)airHeight(6*age/24D);
                // Both ultimate sword segments use the aerial combo's dense burst timing.
                if(YangJianSkill.rainBurstAt(age,JUDGEMENT_RAIN_START)) {
                    int wave=YangJianSkill.rainBurstWave(age,JUDGEMENT_RAIN_START);thirdStage(wave);
                    swordWave(target,wave,YangJianSkill.RAIN_BURST_BLADES,
                        YangJianSkill.rainBurstWarning(26,wave),false);
                }
                if(age==88)planDive(2,22,6,target);
                dive(age,110,6,18);
                if(age>=118 && age<=128)airHeight(4*(age-118)/10D);
                if(age==130) { thirdStage(3);castBeam(target,24,YangJianHazardMath.COMBO_SWEEP_TICKS,4,.65); }
                if(age==184) { thirdStage(4);castBeam(target,YangJianSkill.EYE_BEAM.stepWindup(0),YangJianSkill.EYE_BEAM.stepActive(0),0,YangJianSkill.EYE_BEAM.radius()); }
                if(age==228) { thirdStage(5);thunderWave(target,2,24); }
                if(YangJianSkill.rainBurstAt(age,JUDGEMENT_DOWNPOUR_START)) {
                    int wave=YangJianSkill.rainBurstWave(age,JUDGEMENT_DOWNPOUR_START);thirdStage(6+wave);
                    swordWave(target,wave,YangJianSkill.RAIN_BURST_BLADES,
                        YangJianSkill.rainBurstWarning(24,wave),true);
                }
                if(age>=284 && age<=304)airHeight(4*(304-age)/20D);
                if(age==305)thirdStage(9);
            }
            default -> { }
        }
    }
    private void beginPhaseTwo(YangJianSkill skill,LivingEntity target) {
        if(skill!=YangJianSkill.AXE_SUMMON)entityData.set(WEAPON,skill.weapon());
        switch(skill) {
            case AXE_SUMMON -> publishPlan(0,24,0,position(),position(),position(),0,0);
            case AXE_SLAM -> {
                Vec3 impact=aim(target);face(impact);
                publishPlan(0,28,skill.radius(),position(),impact,impact,2,360);
            }
            case AXE_COMBO,DELAYED_COMBO -> planPhaseTwoStep(skill,0,target);
            case LIGHTNING_THRUST -> planDash(target,18,0,21);
            case INVISIBLE_DASH -> {
                invisibleAim=aim(target);
                Vec3 away=position().subtract(invisibleAim).multiply(1,0,1).normalize();
                invisibleReappearance=clampPoint(position().add(away.scale(3)));
                // Reappear on the same side of the player, visibly indicated by gathering cloud.
                if(!level().noCollision(this,getBoundingBox().move(invisibleReappearance.subtract(position()))))invisibleReappearance=position();
                publishPlan(0,30,0,invisibleReappearance,invisibleAim,invisibleAim,0,0);
                mist(invisibleReappearance,12);
            }
            case FLYING_SWORDS -> {
                // Summoning is harmless. Each real sword owns its later, precisely locked warning line.
                Vec3 aim=aim(target);face(aim);publishPlan(0,24,0,position(),aim,aim,0,0);
            }
            default -> {
                Vec3 aim=aim(target),direction=aim.subtract(position()).multiply(1,0,1).normalize();
                if(skill==YangJianSkill.DRAW_SLASH && distanceTo(target)>5)moveSafely(direction.scale(1));
                Vec3 end=position().add(direction.scale(skill.radius()));face(end);
                int shape=skill==YangJianSkill.WHIP_SPIN?2:1;
                float angle=skill==YangJianSkill.WHIP_SPIN?360:skill==YangJianSkill.WHIP_SWEEP?200:160;
                publishPlan(0,skill.stepWindup(0),skill.radius(),position(),end,position(),shape,angle);
            }
        }
        playSound(skill==YangJianSkill.AXE_SUMMON?SoundEvents.TRIDENT_THUNDER.value():SoundEvents.TRIDENT_RETURN,
            skill==YangJianSkill.AXE_SUMMON?1.1F:.8F,skill==YangJianSkill.DRAW_SLASH?.65F:.85F);
    }
    private void tickPhaseTwo(YangJianSkill skill,int age,LivingEntity target) {
        int active=age-skill.stepWindup(0);
        switch(skill) {
            case AXE_SUMMON -> {
                if(age==24) {
                    entityData.set(WEAPON,1);axeFollowupRequired=true;
                    // A fresh axe sweeps the previous field of scars before the new one charges.
                    clearLightningScars();
                    entityData.set(AXE_TRAIL_CHARGES,YangJianSkill.AXE_TRAIL_CHARGES);
                    sparks(position().add(0,2,0),1.2,28);
                }
            }
            case AXE_SLAM -> {
                if(age>=8 && age<28) {
                    setNoGravity(true);double progress=(age-7.0)/20;
                    Vec3 spot=dashStart().lerp(attackPoint(),progress).add(0,Math.sin(progress*Math.PI)*4.8,0);
                    moveElevated(spot);
                }
                if(age==28) {
                    setNoGravity(false);leaveAxeLightningTrail();
                    axeBurst(attackPoint(),skill.radius(),19,13);
                }
            }
            case AXE_COMBO,DELAYED_COMBO -> tickPhaseTwoCombo(skill,age,target);
            case FLYING_SWORDS -> {
                if(age==24) { DivineFlyingSword.launch((ServerLevel)level(),this,target,5);playSound(SoundEvents.TRIDENT_THROW.value(),1,.8F); }
            }
            case DRAW_SLASH,WHIP_SWEEP,WHIP_SPIN -> {
                if(active>=0 && active<skill.stepActive(0)) {
                    if(active==0)leaveAxeLightningTrail();
                    double arc=Math.toRadians(telegraphAngle());
                    double from,to;
                    if(skill==YangJianSkill.WHIP_SWEEP || skill==YangJianSkill.WHIP_SPIN) {
                        from=YangJianSkill.sweepRelative(arc,active/(double)skill.stepActive(0));
                        to=YangJianSkill.sweepRelative(arc,(active+1)/(double)skill.stepActive(0));
                    } else {
                        from=-arc*.5+arc*active/skill.stepActive(0);
                        to=-arc*.5+arc*(active+1)/skill.stepActive(0);
                    }
                    arcHit(heading(),from,to,skill.radius(),skill==YangJianSkill.DRAW_SLASH?16:11);
                    if(active==0)playSound(SoundEvents.PLAYER_ATTACK_SWEEP,1.5F,skill==YangJianSkill.DRAW_SLASH?.7F:.5F);
                    if(skill!=YangJianSkill.DRAW_SLASH)sparks(position().add(Math.cos(heading()+to)*skill.radius(),.45,Math.sin(heading()+to)*skill.radius()),.25,3);
                }
            }
            case LIGHTNING_THRUST -> {
                if(active>=0 && active<6) {
                    if(active==0)consumeAxeLightningCharge();
                    dashTick(active,6,14);
                    // The whole travelled lane is electrified once the thrust has finished, so the
                    // scar covers the route the dash actually reached instead of a stack of dots.
                    if(active==5)LightningTrail.create((ServerLevel)level(),this,LightningScar.LANE,
                        dashStart(),position(),position(),telegraphRadius(),0,
                        MyriadConfig.scaleYangJianDamage(4),LightningScar.PERSISTENT);
                }
            }
            case INVISIBLE_DASH -> {
                if(age==8)setInvisible(true);
                if(age<18 && age%3==0)mist(invisibleReappearance,3);
                if(age==18) {
                    setInvisible(false);setPos(invisibleReappearance);hurtMarked=true;setDeltaMovement(Vec3.ZERO);
                    Vec3 direction=invisibleAim.subtract(position()).multiply(1,0,1).normalize();
                    Vec3 end=clampPoint(position().add(direction.scale(Math.clamp(position().distanceTo(invisibleAim)+3,5,18))));
                    face(end);publishPlan(1,12,1.2F,position(),end,invisibleAim,3,0);mist(position(),18);
                    playSound(SoundEvents.TRIDENT_RETURN,1.3F,.7F);
                }
                if(age>=30 && age<36) {
                    if(age==30)leaveAxeLightningTrail();
                    dashTick(age-30,6,14);
                }
            }
            default -> { }
        }
    }
    private void planPhaseTwoStep(YangJianSkill skill,int step,LivingEntity target) {
        serverStep=step;hitThisStep.clear();
        Vec3 direction=aim(target).subtract(position()).multiply(1,0,1).normalize();
        if(distanceTo(target)>4.2)moveSafely(direction.scale(Math.min(.8,distanceTo(target)-4.2)));
        Vec3 start=position(),end=start.add(direction.scale(skill.radius()));face(end);
        boolean last=step==skill.steps()-1,slam=skill==YangJianSkill.AXE_COMBO && last;
        float radius=slam?5.8F:skill==YangJianSkill.DELAYED_COMBO && last?6.8F:skill.radius();
        float angle=skill==YangJianSkill.AXE_COMBO || last?180:155;
        publishPlan(step,skill.stepWindup(step),radius,start,end,slam?start.add(direction.scale(2)):start,slam?2:1,slam?360:angle);
    }
    private void tickPhaseTwoCombo(YangJianSkill skill,int age,LivingEntity target) {
        int step=skill.stepAt(age);if(serverStep!=step)planPhaseTwoStep(skill,step,target);
        int active=age-skill.stepHit(step);if(active<0 || active>=skill.stepActive(step))return;
        boolean last=step==skill.steps()-1;
        if(active==0)leaveAxeLightningTrail();
        if(skill==YangJianSkill.AXE_COMBO && last) {
            if(active==0)axeBurst(attackPoint(),telegraphRadius(),18,13);
            return;
        }
        double arc=Math.toRadians(telegraphAngle());
        double from=-arc*.5+arc*active/skill.stepActive(step),to=-arc*.5+arc*(active+1)/skill.stepActive(step);
        if((step&1)==1) { double swap=from;from=-to;to=-swap; }
        arcHit(heading(),from,to,telegraphRadius(),last?17:skill==YangJianSkill.AXE_COMBO?11:9);
        if(active==0)playSound(last?SoundEvents.PLAYER_ATTACK_STRONG:SoundEvents.PLAYER_ATTACK_SWEEP,1.3F,last?.6F:.9F);
    }
    private void axeBurst(Vec3 center,double radius,float centerDamage,float outerDamage) {
        for(LivingEntity player:level().getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius+.5,3.2,radius+.5),this::validTarget)) {
            double distance=player.position().subtract(center).horizontalDistance();
            if(distance<=radius+player.getBbWidth()*.5 && player.getY()<=center.y+3 && player.getY()+player.getBbHeight()>=center.y) {
                if(hit(player,distance<radius*.42?centerDamage:outerDamage,.9F)) { player.push(0,.32,0);player.hurtMarked=true; }
            }
        }
        sparks(center.add(0,.4,0),radius*.65,45);((ServerLevel)level()).sendParticles(ParticleTypes.EXPLOSION,center.x,center.y+.3,center.z,4,1,.1,1,0);
        playSound(SoundEvents.TRIDENT_THUNDER.value(),1.1F,1.3F);
    }
    /**
     * Spend one axe-aftershock charge and electrify the whole area of the move that just landed,
     * so the scar reads as that attack's footprint instead of a single drawn line. The scar stays
     * until the next axe summon replaces it.
     */
    void leaveAxeLightningTrail() {
        if(axeTrailCharges()<=0 || !(level() instanceof ServerLevel server))return;
        entityData.set(AXE_TRAIL_CHARGES,YangJianSkill.consumeAxeTrailCharge(axeTrailCharges()));
        boolean placed=LightningTrail.create(server,this,telegraphShape(),dashStart(),dashEnd(),attackPoint(),
            telegraphRadius(),telegraphAngle(),MyriadConfig.scaleYangJianDamage(4),LightningScar.PERSISTENT);
        if(placed)sparks(dashStart().add(dashEnd()).scale(.5).add(0,.08,0),.35,6);
    }
    /** A released volley blade electrifies the lane it was launched along. */
    void leaveSwordLightningTrail(Vec3 from,Vec3 to) {
        if(axeTrailCharges()<=0 || !(level() instanceof ServerLevel server))return;
        entityData.set(AXE_TRAIL_CHARGES,YangJianSkill.consumeAxeTrailCharge(axeTrailCharges()));
        LightningTrail.create(server,this,LightningScar.LANE,from,to,to,YangJianEffects.TRAIL_RADIUS,0,
            MyriadConfig.scaleYangJianDamage(4),LightningScar.PERSISTENT);
    }
    /** The next axe summon sweeps the previous field of scars away before laying a new one. */
    private void clearLightningScars() {
        if(level() instanceof ServerLevel server)
            for(LightningTrail trail:server.getEntitiesOfClass(LightningTrail.class,getBoundingBox().inflate(96)))
                if(getUUID().equals(trail.ownerId()))trail.discard();
    }
    /** Lightning thrust already creates its own route; only spend the buff stack. */
    private void consumeAxeLightningCharge() {
        if(axeTrailCharges()>0)entityData.set(AXE_TRAIL_CHARGES,YangJianSkill.consumeAxeTrailCharge(axeTrailCharges()));
    }
    private void moveElevated(Vec3 desired) {
        Vec3 clamped=clampPoint(desired),next=new Vec3(clamped.x,desired.y,clamped.z);
        if(level().hasChunkAt(BlockPos.containing(next)))move(MoverType.SELF,next.subtract(position()));
        setDeltaMovement(Vec3.ZERO);hurtMarked=true;
    }
    private double heading() { return Math.atan2(dashEnd().z-dashStart().z,dashEnd().x-dashStart().x); }
    private void mist(Vec3 point,int count) { ((ServerLevel)level()).sendParticles(ParticleTypes.CLOUD,point.x,point.y+1,point.z,count,.5,.8,.5,.02); }
    private void sparks(Vec3 point,double width,int count) { ((ServerLevel)level()).sendParticles(ParticleTypes.ELECTRIC_SPARK,point.x,point.y,point.z,count,width,.3,width,.06); }
    private boolean tryPhaseTwoChain(LivingEntity target) {
        if(!YangJianSkill.canChain(chainCount,chainLimit))return false;
        previousAction=action();YangJianSkill next=selectSkill(target,distanceTo(target));
        if(next==null)return false;
        chainCount++;begin(next,target);return true;
    }
    private void tickCombo(YangJianSkill skill,int age,LivingEntity target) {
        int step=skill.stepAt(age);
        if(step!=serverStep) {
            planComboStep(skill,step,target);
            // The string converted into a lunge or a thrown spear: it no longer owns this tick.
            if(action()!=skill.action())return;
        }
        int active=age-skill.stepHit(step);
        if(active<0 || active>=skill.stepActive(step))return;
        if(skill==YangJianSkill.COORDINATED && step==1) { dashTick(active,skill.stepActive(step),12);return; }
        boolean finisher=skill.meleeCombo() && step==skill.steps()-1;
        if(active==0)playSound(finisher?SoundEvents.PLAYER_ATTACK_STRONG:SoundEvents.PLAYER_ATTACK_SWEEP,1.2F,finisher?.65F:1.0F+step*.06F);
        if(finisher) {
            if(active==0)radialHit(attackPoint(),telegraphRadius(),skill==YangJianSkill.SIX_COMBO?15:skill==YangJianSkill.FOUR_COMBO?13:12);
        } else {
            double angle=Math.atan2(dashEnd().z-dashStart().z,dashEnd().x-dashStart().x);
            boolean spin=skill==YangJianSkill.SIX_COMBO?(step==1 || step==2):step==2;
            double arc=spin?Math.PI*2:Math.toRadians(155);
            double from=-arc/2+arc*active/skill.stepActive(step),to=-arc/2+arc*(active+1)/skill.stepActive(step);
            if((step&1)==1) { double swap=from;from=-to;to=-swap; }
            arcHit(angle,from,to,telegraphRadius(),skill==YangJianSkill.SIX_COMBO?8:7);
        }
    }
    private void planComboStep(YangJianSkill skill,int step,LivingEntity target) {
        serverStep=step;hitThisStep.clear();
        // A follow-up step only exists while the target is still in reach. If they broke away the
        // string converts into a closing thrust, or a thrown spear at long range, instead of
        // swinging at empty air; the abandoned string still pays its own cooldown.
        if(step>0 && target!=null && skill.convertsWhenTargetEscapes() && action()==skill.action()) {
            YangJianSkill followup=YangJianSkill.comboFollowup(distanceTo(target));
            if(followup!=null) {
                cooldowns[skill.action()]=skill.cooldown();
                begin(followup,target);
                return;
            }
        }
        if(skill==YangJianSkill.COORDINATED && step==1) { planDash(target,skill.stepWindup(step),step,13);return; }
        Vec3 direction=aim(target).subtract(position()).multiply(1,0,1).normalize();
        if(distanceTo(target)>3.1)moveSafely(direction.scale(Math.min(.85,distanceTo(target)-3.1)));
        Vec3 start=position(),end=start.add(direction.scale(skill.radius()));face(end);
        boolean finalHit=skill.meleeCombo() && step==skill.steps()-1;
        float radius=finalHit?skill==YangJianSkill.SIX_COMBO?4.3F:skill==YangJianSkill.FOUR_COMBO?3.8F:3.2F:skill.radius();
        publishPlan(step,skill.stepWindup(step),radius,start,end,finalHit?start.add(direction.scale(1.65)):start);
    }
    private void tickThrow(int age,LivingEntity target) {
        if(age==16) {
            entityData.set(THROWN,true);TriPointedBlade.launch((ServerLevel)level(),this,target);
            playSound(SoundEvents.TRIDENT_THROW.value(),1.3F,.8F);
        }
        if(age==26) { hitThisStep.clear();planDash(target,12,1,18); }
        if(age>=38 && age<44)dashTick(age-38,6,8);
        if(age==44) {
            entityData.set(THROWN,false);hitThisStep.clear();
            double heading=Math.atan2(dashEnd().z-dashStart().z,dashEnd().x-dashStart().x);
            arcHit(heading,-1.25,1.25,4,11);playSound(SoundEvents.PLAYER_ATTACK_SWEEP,1.2F,.7F);
        }
    }
    private Vec3 aim(LivingEntity target) {
        double prediction=target instanceof Player player && player.hasEffect(MyriadCalamity.ROAR_MARK)?4:1.5;
        return clampPoint(target.position().add(target.getDeltaMovement().multiply(prediction,0,prediction)));
    }
    private void planDash(LivingEntity target,int windup,int step,double maximumDistance) {
        Vec3 start=position(),aim=aim(target),direction=aim.subtract(start).multiply(1,0,1).normalize();
        if(direction.lengthSqr()<.001)direction=getLookAngle().multiply(1,0,1).normalize();
        double distance=Math.clamp(start.distanceTo(aim)+3,5,maximumDistance);
        Vec3 end=clampPoint(start.add(direction.scale(distance)));face(end);
        publishPlan(step,windup,1.2F,start,end,aim);
    }
    private void publishPlan(int step,int windup,float radius,Vec3 start,Vec3 end,Vec3 point) {
        YangJianSkill skill=YangJianSkill.forAction(action());
        boolean route=action()==THRUST || action()==COUNTER || action()==THROW || action()==LIGHTNING_THRUST
            || action()==INVISIBLE_DASH || action()==COORDINATED && step==1;
        boolean finisher=skill!=null && skill.meleeCombo() && step==skill.steps()-1;
        boolean spin=action()==SIX_COMBO?(step==1 || step==2):(action()==COMBO || action()==FOUR_COMBO) && step==2;
        int shape=radius<=0?0:route?3:finisher || spin?2:1;
        publishPlan(step,windup,radius,start,end,point,shape,shape==2?360:shape==1?155:0);
    }
    private void publishPlan(int step,int windup,float radius,Vec3 start,Vec3 end,Vec3 point,int shape,float angle) {
        CompoundTag plan=new CompoundTag();plan.putInt("a",action());plan.putInt("step",step);
        plan.putInt("w",windup);plan.putFloat("r",radius);plan.putLong("t",level().getGameTime());
        plan.putInt("shape",shape);plan.putFloat("angle",angle);
        writePoint(plan,"s",start);writePoint(plan,"e",end);writePoint(plan,"p",point);entityData.set(PLAN,plan);
    }
    private void dashTick(int active,int travel,float damage) {
        Vec3 from=position(),desired=dashStart().lerp(dashEnd(),(active+1.0)/travel);
        moveSafely(desired.subtract(from));Vec3 to=position();
        for(LivingEntity player:level().getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(2,2.6,2),this::validTarget)) {
            if(player.getY()+player.getBbHeight()<Math.min(from.y,to.y) || player.getY()>Math.max(from.y,to.y)+3.3)continue;
            double radius=telegraphRadius()+player.getBbWidth()*.5;
            if(CombatMath.segmentDistanceSquared(player.getX(),player.getZ(),from.x,from.z,to.x,to.z)<=radius*radius)hit(player,damage,.6F);
        }
        if(active==0)playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK,1.2F,.7F);
    }
    private void arcHit(double heading,double from,double to,double reach,float damage) {
        for(LivingEntity player:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(reach,1.0,reach),this::validTarget)) {
            if(!YangJianSkill.sweepTouches(player.getY(),player.getY()+player.getBbHeight(),getY()))continue;
            if(YangJianSkill.sweptArc(player.getX()-getX(),player.getZ()-getZ(),heading,from,to,reach,player.getBbWidth()*.5))hit(player,damage,.35F);
        }
    }
    /** P3 divine sweep is a hand-height arc, separate from the full-height whip band. */
    private void divineArcHit(double heading,double from,double to,double reach,float damage) {
        for(LivingEntity player:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(reach,1.0,reach),this::validTarget)) {
            if(!YangJianSkill.divineSweepTouches(player.getY(),player.getY()+player.getBbHeight(),getY()))continue;
            if(YangJianSkill.sweptArc(player.getX()-getX(),player.getZ()-getZ(),heading,from,to,reach,player.getBbWidth()*.5))hit(player,damage,.35F);
        }
    }
    private void radialHit(Vec3 center,double radius,float damage) {
        for(LivingEntity player:level().getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius+.5,3,radius+.5),this::validTarget)) {
            double r=radius+player.getBbWidth()*.5;
            if(player.position().subtract(center).horizontalDistanceSqr()<=r*r && player.getY()<=center.y+2.8)hit(player,damage,.85F);
        }
        ((ServerLevel)level()).sendParticles(ParticleTypes.SWEEP_ATTACK,center.x,center.y+.25,center.z,12,radius*.6,.15,radius*.6,0);
    }
    private boolean hit(LivingEntity player,float damage,float knockback) {
        // Thorns can synchronously break guard inside another victim's hurt callback.
        if(!isAlive() || isTransitioning() || action()==PHASE_CLEAR || isTrialComplete())return false;
        if(!hitThisStep.add(player.getUUID()) || !hasLineOfSight(player))return false;
        // A victim is admitted once per authored strike; short combo intervals still produce distinct hits.
        player.invulnerableTime=0;
        if(player.hurt(damageSources().mobAttack(this),MyriadConfig.scaleYangJianDamage(damage))) {
            player.knockback(knockback,getX()-player.getX(),getZ()-player.getZ());player.hurtMarked=true;return true;
        }
        return false;
    }
    private void face(Vec3 point) {
        Vec3 direction=point.subtract(position());
        if(direction.horizontalDistanceSqr()<1E-6)return;
        float yaw=(float)(Math.atan2(direction.z,direction.x)*Mth.RAD_TO_DEG)-90;
        // Keep the angle continuous across north so P3 always takes the short turn.
        if(phase()==3)yaw=getYRot()+Mth.wrapDegrees(yaw-getYRot());
        setYRot(yaw);yBodyRot=yaw;yHeadRot=yaw;
    }
    private void moveSafely(Vec3 movement) {
        Vec3 desired=clampPoint(position().add(movement));
        Vec3 delta=desired.subtract(position());
        if(!level().hasChunkAt(BlockPos.containing(desired)))return;
        move(MoverType.SELF,delta);setDeltaMovement(new Vec3(0,getDeltaMovement().y,0));hurtMarked=true;
    }
    public Vec3 clampPoint(Vec3 point) {
        if(!isArenaBoss())return point;
        Vec3 center=Vec3.atBottomCenterOf(arenaCenter().above());
        CombatMath.Point p=CombatMath.clampDisk(point.x-center.x,point.z-center.z,ARENA_RADIUS-1);
        return new Vec3(center.x+p.x(),center.y,center.z+p.z());
    }
    private void confineToArena() {
        Vec3 clamped=clampPoint(position());
        if(position().subtract(clamped).horizontalDistanceSqr()>.01 || getY()<clamped.y-2 || getY()>clamped.y+12) {
            setPos(clamped);setDeltaMovement(Vec3.ZERO);hurtMarked=true;
        }
    }
    private void finishAttack() {
        previousAction=action();
        if(phase()==3)clearThirdHazards();
        cancelAttack();idleDelay=phase()==3?YangJianSkill.PHASE_THREE_FINAL_RECOVERY:phase()==2?YangJianSkill.PHASE_TWO_FINAL_RECOVERY:8+random.nextInt(7);
        if(phase()==3)healthBar.setName(Component.translatable("boss.myriad_calamity.yang_jian_p3"));
    }
    private void cancelAttack() {
        endApproach();
        transitionDistances.clear();
        entityData.set(ACTION,IDLE);entityData.set(PLAN,new CompoundTag());entityData.set(THROWN,false);bladeId=null;
        entityData.set(STARTED,level().getGameTime());lockedTarget=null;serverStep=-1;hitThisStep.clear();counterPending=false;
        chainCount=chainLimit=0;sweepBeamPass=-1;sweepBodyPosition=sweepClonePosition=Vec3.ZERO;
        setInvisible(false);setNoGravity(false);getNavigation().stop();
    }
    @Nullable public CelestialHound hound() {
        if(houndId==null || !(level() instanceof ServerLevel server))return null;
        Entity entity=server.getEntity(houndId);return entity instanceof CelestialHound dog && dog.isAlive()?dog:null;
    }
    public boolean ownsHound(UUID id) { return phase()==1 && !isTransitioning() && id.equals(houndId); }
    private void summonHound(LivingEntity target) {
        if(hound()!=null)return;
        CelestialHound dog=MyriadCalamity.CELESTIAL_HOUND.get().create(level());if(dog==null)return;
        Vec3 spot=clampPoint(position().add(getLookAngle().multiply(1,0,1).normalize().scale(-2.2)));
        dog.setPos(spot);dog.bind(this,target);
        if(level().noCollision(dog,dog.getBoundingBox()) && level().addFreshEntity(dog)) {
            houndId=dog.getUUID();playSound(SoundEvents.WOLF_HOWL,1.4F,.75F);
            ((ServerLevel)level()).sendParticles(ParticleTypes.CLOUD,spot.x,spot.y+.7,spot.z,25,.7,.5,.7,.025);
        } else cooldowns[SUMMON_HOUND]=60;
    }
    public void bindWeapon(UUID id) { bladeId=id; }
    public boolean ownsWeapon(UUID id) { return id.equals(bladeId); }
    public void recoverWeapon() { entityData.set(THROWN,false);bladeId=null; }
    public void recoverWeapon(UUID id) { if(ownsWeapon(id))recoverWeapon(); }

    @Override public boolean hurt(DamageSource source,float amount) {
        if(level().isClientSide || source.is(DamageTypes.GENERIC_KILL))return super.hurt(source,amount);
        initialize();
        if(action()==PHASE_CLEAR || isTransitioning() || action()==INVISIBLE_DASH && isInvisible()
            || applyingDamage || !Float.isFinite(amount) || amount<=0)return false;

        Entity actual=source.getEntity(),direct=source.getDirectEntity();
        LivingEntity sameLivingAttacker=actual instanceof LivingEntity living && living!=this && direct==living?living:null;
        boolean melee=sameLivingAttacker!=null && sameLivingAttacker.distanceToSqr(this)<25;
        boolean front=false;
        if(melee) {
            Vec3 towards=sameLivingAttacker.position().subtract(position()).multiply(1,0,1);
            Vec3 look=getLookAngle().multiply(1,0,1);
            front=towards.lengthSqr()>1E-8 && look.lengthSqr()>1E-8 && look.normalize().dot(towards.normalize())>.3;
            guardPressure=level().getGameTime()-lastMeleeTick<35?Math.min(4,guardPressure+1):1;
            lastMeleeTick=level().getGameTime();
        }
        boolean defensiveState=action()==IDLE || action()==GUARD;
        boolean supportedSource=direct instanceof Projectile || actual instanceof LivingEntity || direct instanceof LivingEntity;
        double directDistance=direct==null?Double.NaN:direct.distanceToSqr(this);
        double actualDistance=actual==null?Double.NaN:actual.distanceToSqr(this);
        boolean remote=defensiveState && YangJianDefense.isRemote(directDistance,actualDistance,supportedSource);
        boolean canGuard=guard()>0 && (action()==GUARD || action()==IDLE);
        boolean blocked=guard()>0 && (remote || front && canGuard && (action()==GUARD || cooldowns[GUARD]==0 && random.nextFloat()<.22F+guardPressure*.065F));
        float incoming=Math.min(amount,getMaxHealth()*.15F);
        if(!Float.isFinite(incoming) || incoming<=0)return false;

        // The defense bar is a real shield: while it has charge, damage never
        // reaches health.  Blocks still spend a reduced amount of that shield.
        if(guard()>0) {
            float loss=YangJianSkill.guardDamage(incoming,maxGuard(),blocked);
            if(loss<=0)return false;
            entityData.set(GUARD_VALUE,Math.max(0,guard()-loss));
            if(guard()<=0)guardRegenRemaining=YangJianSkill.GUARD_REGEN_DELAY;
            if(blocked)respondToBlock(actual,direct,sameLivingAttacker);
            return true;
        }

        // A depleted bar leaves every phase exposed.  Only actual health loss
        // can cross a phase threshold; guard depletion itself never transitions.
        boolean damaged=super.hurt(source,incoming);
        if(damaged && isAlive() && phase()<3 && YangJianSkill.phaseThresholdReached(getHealth(),getMaxHealth(),phase())) {
            if(YangJianSkill.healthThresholdAction(phase())==TRANSITION)beginTransition();
            else beginEyeOpening();
        }
        return damaged;
    }

    /** Called by the NeoForge projectile-impact hook before vanilla on-hit code can discard the projectile. */
    public boolean guardProjectile(Projectile projectile) {
        if(level().isClientSide || !isAlive() || projectile.isRemoved() || action()==PHASE_CLEAR || isTransitioning()
                || action()==INVISIBLE_DASH && isInvisible() || action()!=IDLE && action()!=GUARD || guard()<=0) return false;
        Entity owner=projectile.getOwner();
        if(owner==this) return false;
        // A projectile with no owner still has no reliable origin; the impact hook is the safe point to deflect it.
        if(owner!=null && !YangJianDefense.isRemote(Double.NaN,owner.distanceToSqr(this),true)) return false;
        initialize();
        reflectProjectile(projectile);
        respondToBlockPose(owner);
        return true;
    }

    private void respondToBlock(@Nullable Entity actual,@Nullable Entity direct,@Nullable LivingEntity sameLivingAttacker) {
        if(direct instanceof Projectile projectile)reflectProjectile(projectile);
        if(sameLivingAttacker!=null) {
            stunAttacker(sameLivingAttacker);
            if(sameLivingAttacker.isAlive()) {
                sameLivingAttacker.knockback(.5F,getX()-sameLivingAttacker.getX(),getZ()-sameLivingAttacker.getZ());
                sameLivingAttacker.hurtMarked=true;
            }
        }
        respondToBlockPose(actual);
    }

    private void respondToBlockPose(@Nullable Entity actual) {
        LivingEntity target=action()==GUARD?lockedEntity():null;
        if(action()!=GUARD) {
            target=actual instanceof LivingEntity living && validTarget(living)?living:chooseTarget();
            startGuardPose(target);
        }
        counterPending=phase()<3 && target!=null && cooldowns[phase()==2?DRAW_SLASH:COUNTER]==0 && random.nextFloat()<.7F;
        playSound(SoundEvents.SHIELD_BLOCK,1.3F,.7F);
    }

    private void startGuardPose(@Nullable LivingEntity target) {
        if(target!=null) { begin(YangJianSkill.GUARD,target);return; }
        endApproach();
        entityData.set(ACTION,GUARD);entityData.set(PLAN,new CompoundTag());entityData.set(STARTED,level().getGameTime());
        lockedTarget=null;serverStep=-1;hitThisStep.clear();cooldowns[GUARD]=YangJianSkill.GUARD.cooldown();
        publishPlan(0,YangJianSkill.GUARD.stepWindup(0),0,position(),position(),position(),0,0);
    }

    private void reflectProjectile(Projectile projectile) {
        Vec3 velocity=YangJianDefense.reflected(projectile.getDeltaMovement());
        if(velocity==Vec3.ZERO)velocity=YangJianDefense.reflected(projectile.position().subtract(position()));
        if(velocity==Vec3.ZERO)velocity=new Vec3(0,.35,0);
        // Yang Jian's own projectiles carry stateful owner references; only foreign projectiles change owner.
        if(!(projectile instanceof TriPointedBlade) && !(projectile instanceof DivineFlyingSword) && !(projectile instanceof CogworkBlade))
            projectile.setOwner(this);
        projectile.setDeltaMovement(velocity);projectile.hasImpulse=true;projectile.hurtMarked=true;
        double horizontal=Math.sqrt(velocity.x*velocity.x+velocity.z*velocity.z);
        if(horizontal>1E-8)projectile.setYRot((float)(Math.atan2(velocity.z,velocity.x)*Mth.RAD_TO_DEG)-90F);
        projectile.setXRot((float)(Math.atan2(velocity.y,horizontal)*Mth.RAD_TO_DEG));
    }

    private void stunAttacker(LivingEntity attacker) {
        attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,YangJianDefense.STUN_TICKS,255,false,true,true),this);
        attacker.setDeltaMovement(Vec3.ZERO);attacker.hurtMarked=true;
        if(attacker instanceof Mob mob) { mob.getNavigation().stop();mob.setAggressive(false); }
        stunnedAttackers.put(attacker.getUUID(),YangJianDefense.STUN_TICKS);
    }

    private void tickStunnedAttackers() {
        if(stunnedAttackers.isEmpty() || !(level() instanceof ServerLevel server))return;
        Iterator<Map.Entry<UUID,Integer>> iterator=stunnedAttackers.entrySet().iterator();
        while(iterator.hasNext()) {
            Map.Entry<UUID,Integer> entry=iterator.next();
            int remaining=entry.getValue();Entity entity=server.getEntity(entry.getKey());
            if(remaining<=1 || !(entity instanceof LivingEntity attacker) || !attacker.isAlive()) { iterator.remove();continue; }
            attacker.setDeltaMovement(Vec3.ZERO);attacker.hurtMarked=true;
            if(attacker instanceof Mob mob)mob.getNavigation().stop();
            entry.setValue(remaining-1);
        }
    }
    /** Recharges an exhausted guard after the full two-minute exposed window. */
    private void tickGuardRegen() {
        if(maxGuard()<=0 || guard()>0 || isTransitioning() || action()==PHASE_CLEAR)return;
        if(guardRegenRemaining>0) { guardRegenRemaining--;return; }
        entityData.set(GUARD_VALUE,maxGuard());guardPressure=0;
        sparks(position().add(0,1,0),.8,12);playSound(SoundEvents.SHIELD_BLOCK,1.1F,.55F);
    }
    /** Keep externally restored/edited health on the same 75%/50% phase contract as combat damage. */
    private void tickHealthTransitions() {
        if(phase()>=3 || !isAlive() || action()==PHASE_CLEAR || isTransitioning()
                || !YangJianSkill.phaseThresholdReached(getHealth(),getMaxHealth(),phase()))return;
        if(phase()==1)beginTransition();else beginEyeOpening();
    }
    @Override public void setHealth(float value) { super.setHealth(applyingDamage?YangJianSkill.protectedHealth(value):value); }
    private void beginTransition() {
        Vec3 center=isArenaBoss()?Vec3.atBottomCenterOf(arenaCenter().above()):position();
        beginThunderAxeTransition(center,true);
    }
    private void beginThunderAxeTransition(Vec3 center,boolean announce) {
        cancelAttack();clearSummons();setTarget(null);transitionRemaining=YangJianTransition.DURATION;
        Vec3 start=isArenaBoss()?clampPoint(position()):new Vec3(getX(),center.y,getZ());
        setPos(start);setDeltaMovement(Vec3.ZERO);setNoGravity(true);fallDistance=0;
        entityData.set(ACTION,TRANSITION);entityData.set(STARTED,level().getGameTime());entityData.set(WEAPON,1);
        publishPlan(0,YangJianTransition.IMPACT,(float)transitionRadius(),start,center,center,2,360);
        LivingEntity target=chooseTarget();if(target!=null)face(target.position());
        transitionDistances.clear();
        healthBar.setName(Component.translatable("boss.myriad_calamity.yang_jian_transition"));
        if(announce) { playSound(SoundEvents.SHIELD_BREAK,1.5F,.6F);sparks(position().add(0,1,0),2,35); }
    }
    private void tickThunderAxeTransition() {
        int age=(int)actionAge(0);
        Vec3 center=transitionCenter();
        if(age>=YangJianTransition.DURATION) { setPos(center);enterPhaseTwo();return; }
        transitionRemaining=YangJianTransition.DURATION-age;
        double progress=Math.clamp(age/(double)YangJianTransition.ASCEND_END,0,1);
        progress=progress*progress*(3-2*progress);
        Vec3 ground=dashStart().lerp(center,progress);
        setNoGravity(true);setDeltaMovement(Vec3.ZERO);fallDistance=0;
        setPos(ground.x,center.y+YangJianTransition.height(age),ground.z);hasImpulse=true;
        if(age==YangJianTransition.SUMMON_START)playSound(SoundEvents.BEACON_ACTIVATE,1.5F,.6F);
        if(age==YangJianTransition.SWEEP_START)playSound(SoundEvents.TRIDENT_RETURN,1.8F,.5F);
        if(age==YangJianTransition.IMPACT) {
            hitThisStep.clear();transitionGroundHit(center,age,false);
            if(!isAlive() || action()!=TRANSITION)return;
            playSound(SoundEvents.TRIDENT_THUNDER.value(),3,.65F);
            playSound(SoundEvents.GENERIC_EXPLODE.value(),2,.65F);
            ((ServerLevel)level()).sendParticles(ParticleTypes.CLOUD,center.x,center.y+.12,center.z,60,2.8,.08,2.8,.045);
        }
        if(age==YangJianTransition.EARLY_WAVE_START || age==YangJianTransition.WAVE_START) {
            // Each crest owns its own approach window, so one player can be caught by both.
            hitThisStep.clear();transitionDistances.clear();playSound(SoundEvents.ELDER_GUARDIAN_CURSE,1.2F,.65F);
        }
        if(YangJianTransition.inWave(age))
            transitionGroundHit(center,age,true);
    }
    private void transitionGroundHit(Vec3 center,int age,boolean wave) {
        AABB area=new AABB(center,center).inflate(transitionRadius()+2,3,transitionRadius()+2);
        for(LivingEntity player:level().getEntitiesOfClass(LivingEntity.class,area,this::validTarget)) {
            double distance=player.position().subtract(center).horizontalDistance();
            double previous=transitionDistances.getOrDefault(player.getUUID(),distance);
            transitionDistances.put(player.getUUID(),distance);
            boolean touches=wave?YangJianTransition.waveTouches(age,previous,distance,player.getBbWidth()*.5,
                player.getY(),player.getBoundingBox().maxY,center.y):YangJianTransition.impactTouches(distance,player.getBbWidth()*.5,
                player.getY(),player.getBoundingBox().maxY,center.y);
            if(!touches || !hitThisStep.add(player.getUUID()))continue;
            // This is a ground pulse, not the aerial boss's melee hitbox. Never raise it with the caster.
            player.hurt(damageSources().mobAttack(this),MyriadConfig.scaleYangJianDamage(wave?10:14));
            if(!isAlive() || action()!=TRANSITION)return;
        }
    }
    private void enterPhaseTwo() {
        cancelAttack();entityData.set(PHASE,2);entityData.set(WEAPON,1);transitionRemaining=0;
        entityData.set(AXE_TRAIL_CHARGES,0);
        entityData.set(GUARD_MAX,MyriadConfig.yangJianPhase2Guard());entityData.set(GUARD_VALUE,maxGuard());guardRegenRemaining=0;
        setHealth(Math.max(1,getHealth()));phaseTwoOpening=false;axeFollowupRequired=false;idleDelay=28;previousAction=AXE_SUMMON;
        java.util.Arrays.fill(cooldowns,0);cooldowns[AXE_SUMMON]=YangJianSkill.AXE_SUMMON.cooldown();clearSummons();
        healthBar.setName(Component.translatable("boss.myriad_calamity.yang_jian_p2"));healthBar.setColor(BossEvent.BossBarColor.BLUE);
        playSound(SoundEvents.TRIDENT_THUNDER.value(),1.2F,1.1F);sparks(position().add(0,2,0),2,40);
    }
    private void beginEyeOpening() {
        cancelAttack();clearSummons();setTarget(null);transitionRemaining=YangJianPhaseThree.OPEN_TICKS;
        if(isArenaBoss())setPos(clampPoint(position()));
        entityData.set(ACTION,THIRD_EYE_OPEN);entityData.set(STARTED,level().getGameTime());entityData.set(WEAPON,0);
        healthBar.setName(Component.translatable("boss.myriad_calamity.yang_jian_eye_open"));
        playSound(SoundEvents.BEACON_ACTIVATE,1.4F,.55F);sparks(eyeBeamOrigin(),.4,32);
    }
    private void enterPhaseThree() {
        cancelAttack();clearSummons();entityData.set(PHASE,3);entityData.set(WEAPON,0);transitionRemaining=0;
        axeFollowupRequired=false;phaseThreeOpeningSweep=true;
        entityData.set(AXE_TRAIL_CHARGES,0);
        entityData.set(GUARD_MAX,MyriadConfig.yangJianPhase3Guard());entityData.set(GUARD_VALUE,maxGuard());guardRegenRemaining=0;
        setHealth(YangJianPhaseThree.enteringHealth(getHealth(),getMaxHealth(),MyriadConfig.yangJianPhase3HealthFloor()));
        java.util.Arrays.fill(cooldowns,0);cooldowns[DIVINE_JUDGEMENT]=180;
        previousAction=0;idleDelay=0;
        healthBar.setName(Component.translatable("boss.myriad_calamity.yang_jian_p3"));healthBar.setColor(BossEvent.BossBarColor.PURPLE);
        playSound(SoundEvents.BEACON_POWER_SELECT,1.5F,.7F);
    }
    private void beginClear() {
        cancelAttack();entityData.set(ACTION,PHASE_CLEAR);entityData.set(STARTED,level().getGameTime());clearRemaining=CLEAR_TICKS;
        setTarget(null);clearSummons();playSound(SoundEvents.SHIELD_BREAK,1.5F,.65F);
        healthBar.setName(Component.translatable(phase()==2?"boss.myriad_calamity.yang_jian_p2_clear":"boss.myriad_calamity.yang_jian_clear"));
    }
    private void clearSummons() {
        if(!(level() instanceof ServerLevel server))return;
        for(CelestialHound dog:server.getEntitiesOfClass(CelestialHound.class,getBoundingBox().inflate(70)))if(getUUID().equals(dog.ownerId()))dog.discard();
        for(TriPointedBlade blade:server.getEntitiesOfClass(TriPointedBlade.class,getBoundingBox().inflate(80)))if(blade.getOwner()==this)blade.discard();
        for(DivineFlyingSword sword:server.getEntitiesOfClass(DivineFlyingSword.class,getBoundingBox().inflate(96)))if(getUUID().equals(sword.ownerId()))sword.discard();
        for(LightningTrail trail:server.getEntitiesOfClass(LightningTrail.class,getBoundingBox().inflate(96)))if(getUUID().equals(trail.ownerId()))trail.discard();
        clearThirdHazards(true);
        houndId=null;
    }
    private void clearThirdHazards() { clearThirdHazards(false); }
    private void clearThirdHazards(boolean includePersistent) {
        entityData.set(BEAM,-1);entityData.set(CLONE_BEAM,-1);
        if(level() instanceof ServerLevel server)
            for(YangJianHazard h:server.getEntitiesOfClass(YangJianHazard.class,getBoundingBox().inflate(100)))
                if(getUUID().equals(h.ownerId()) && (includePersistent || !h.persistent()))h.discard();
    }
    private void updateBars() {
        if(isTrialComplete())return;
        guardBar.setVisible(maxGuard()>0);
        healthBar.setProgress(Mth.clamp(getHealth()/getMaxHealth(),0,1));guardBar.setProgress(Mth.clamp(guard()/Math.max(1,maxGuard()),0,1));
        for(ServerPlayer player:((ServerLevel)level()).players()) {
            if(player.isAlive() && !player.isSpectator() && distanceToSqr(player)<58*58) { healthBar.addPlayer(player);guardBar.addPlayer(player); }
            else { healthBar.removePlayer(player);guardBar.removePlayer(player); }
        }
        for(ServerPlayer player:Set.copyOf(healthBar.getPlayers()))if(player.level()!=level() || !player.isAlive()) { healthBar.removePlayer(player);guardBar.removePlayer(player); }
    }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player);healthBar.removePlayer(player);guardBar.removePlayer(player); }
    @Override public void remove(Entity.RemovalReason reason) {
        stunnedAttackers.clear();
        if(reason==RemovalReason.KILLED || reason==RemovalReason.DISCARDED)clearSummons();
        healthBar.removeAllPlayers();guardBar.removeAllPlayers();super.remove(reason);
    }
    @Override public void die(DamageSource source) {
        stunnedAttackers.clear();
        cancelAttack();clearSummons();healthBar.removeAllPlayers();guardBar.removeAllPlayers();super.die(source);
        if(phase()==3 && dead) {
            entityData.set(COMPLETE,true);
            healthBar.setName(Component.translatable("boss.myriad_calamity.yang_jian_defeated"));
        }
    }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public AABB getBoundingBoxForCulling() {
        if(action()==TRANSITION)return super.getBoundingBoxForCulling().inflate(16);
        return phase()==3 || action()==THIRD_EYE_OPEN?super.getBoundingBoxForCulling().inflate(13,3,13):super.getBoundingBoxForCulling();
    }
    @Override public boolean causeFallDamage(float distance,float multiplier,DamageSource source) { return false; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.PLAYER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.IRON_GOLEM_DEATH; }
    private static Vec3 point(CompoundTag tag,String name) { return new Vec3(tag.getDouble(name+"x"),tag.getDouble(name+"y"),tag.getDouble(name+"z")); }
    private static void writePoint(CompoundTag tag,String name,Vec3 value) { tag.putDouble(name+"x",value.x);tag.putDouble(name+"y",value.y);tag.putDouble(name+"z",value.z); }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);tag.putBoolean("YangJianInitialized",initialized);tag.putFloat("Guard",guard());tag.putFloat("MaxGuard",maxGuard());
        tag.putInt("YangJianPhase",phase());tag.putInt("YangJianWeapon",weapon());tag.putInt("TransitionRemaining",transitionRemaining);
        tag.putBoolean("PhaseTwoOpening",phaseTwoOpening);tag.putBoolean("AxeFollowupRequired",axeFollowupRequired);
        tag.putBoolean("PhaseThreeOpeningSweep",phaseThreeOpeningSweep);tag.putInt("GuardRegenRemaining",guardRegenRemaining);
        tag.putInt("AxeTrailCharges",axeTrailCharges());
        if(action()==TRANSITION)writePoint(tag,"ThunderAxeCenter",transitionCenter());
        tag.putBoolean("TrialComplete",isTrialComplete());tag.putInt("ClearRemaining",clearRemaining);tag.putInt("PreparationRemaining",preparationRemaining);tag.putInt("PreviousSkill",previousAction);
        tag.putIntArray("SkillCooldowns",cooldowns);tag.putInt("IdleDelay",idleDelay);
        if(arenaCenter!=null)tag.putLong("CloudArenaCenter",arenaCenter.asLong());
        if(arenaOwner!=null)tag.putUUID("CloudArenaOwner",arenaOwner);if(houndId!=null)tag.putUUID("CelestialHound",houndId);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);initialized=tag.getBoolean("YangJianInitialized");
        entityData.set(PHASE,YangJianSkill.restoredPhase(tag.getInt("YangJianPhase")));
        entityData.set(WEAPON,Math.clamp(tag.getInt("YangJianWeapon"),0,3));
        transitionRemaining=Math.clamp(tag.getInt("TransitionRemaining"),0,phase()==2?70:YangJianTransition.DURATION);
        preparationRemaining=Math.clamp(tag.getInt("PreparationRemaining"),0,200);
        phaseTwoOpening=tag.getBoolean("PhaseTwoOpening");axeFollowupRequired=tag.getBoolean("AxeFollowupRequired");
        phaseThreeOpeningSweep=tag.getBoolean("PhaseThreeOpeningSweep");
        entityData.set(AXE_TRAIL_CHARGES,Math.clamp(tag.getInt("AxeTrailCharges"),0,YangJianSkill.AXE_TRAIL_CHARGES));
        float maximum=tag.contains("MaxGuard")?tag.getFloat("MaxGuard"):
            phase()==3?MyriadConfig.yangJianPhase3Guard():phase()==2?MyriadConfig.yangJianPhase2Guard():MyriadConfig.yangJianGuard();
        entityData.set(GUARD_MAX,Float.isFinite(maximum)?Math.max(1,maximum):180F);
        float saved=tag.contains("Guard")?tag.getFloat("Guard"):maxGuard();entityData.set(GUARD_VALUE,Float.isFinite(saved)?Mth.clamp(saved,0,maxGuard()):maxGuard());
        guardRegenRemaining=Math.clamp(tag.getInt("GuardRegenRemaining"),0,YangJianSkill.GUARD_REGEN_DELAY);
        if(guard()>0)guardRegenRemaining=0;
        else if(!tag.contains("GuardRegenRemaining"))guardRegenRemaining=YangJianSkill.GUARD_REGEN_DELAY;
        entityData.set(COMPLETE,tag.getBoolean("TrialComplete"));clearRemaining=Math.clamp(tag.getInt("ClearRemaining"),0,CLEAR_TICKS);
        previousAction=Math.clamp(tag.getInt("PreviousSkill"),0,32);idleDelay=Math.clamp(tag.getInt("IdleDelay"),phase()==3?10:phase()==2?28:20,80);
        int[] restored=tag.getIntArray("SkillCooldowns");for(int i=0;i<Math.min(restored.length,cooldowns.length);i++)cooldowns[i]=Math.clamp(restored[i],0,1200);
        arenaCenter=tag.contains("CloudArenaCenter")?BlockPos.of(tag.getLong("CloudArenaCenter")):null;
        arenaOwner=tag.hasUUID("CloudArenaOwner")?tag.getUUID("CloudArenaOwner"):null;
        CompoundTag arena=new CompoundTag();if(arenaCenter!=null)arena.putLong("center",arenaCenter.asLong());if(arenaOwner!=null)arena.putUUID("owner",arenaOwner);entityData.set(ARENA,arena);
        entityData.set(ARENA_PREPARING,preparationRemaining>0);
        if(preparationRemaining>0) { setInvisible(true);setNoGravity(true); }
        houndId=tag.hasUUID("CelestialHound")?tag.getUUID("CelestialHound"):null;
        // Never restore only an action ID: an old partial plan would fabricate warnings after chunk reload.
        cancelAttack();
        int restoredAction=YangJianSkill.restoredAction(phase(),isTrialComplete(),guard(),transitionRemaining,clearRemaining);
        if(isTrialComplete()) {
            entityData.set(ACTION,PHASE_CLEAR);clearRemaining=0;transitionRemaining=0;
            healthBar.setName(Component.translatable(phase()==2?"boss.myriad_calamity.yang_jian_p2_clear":"boss.myriad_calamity.yang_jian_clear"));
        } else if(restoredAction==TRANSITION) {
            // Reloads repeat the complete warned opening instead of restoring an unwarned impact mid-flight.
            Vec3 center=isArenaBoss()?Vec3.atBottomCenterOf(arenaCenter().above()):
                tag.contains("ThunderAxeCenterx")?point(tag,"ThunderAxeCenter"):position();
            if(!Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z))center=position();
            clearRemaining=0;beginThunderAxeTransition(center,false);
        } else if(restoredAction==THIRD_EYE_OPEN) {
            entityData.set(ACTION,THIRD_EYE_OPEN);clearRemaining=0;
            if(transitionRemaining<=0)transitionRemaining=70;
            entityData.set(STARTED,level().getGameTime()-(70-transitionRemaining));
            healthBar.setName(Component.translatable("boss.myriad_calamity.yang_jian_eye_open"));
        } else if(restoredAction==PHASE_CLEAR) {
            entityData.set(ACTION,PHASE_CLEAR);clearRemaining=isTrialComplete()?0:Math.max(1,clearRemaining);
            entityData.set(STARTED,level().getGameTime()-(CLEAR_TICKS-clearRemaining));
            healthBar.setName(Component.translatable("boss.myriad_calamity.yang_jian_p2_clear"));
        } else healthBar.setName(Component.translatable(phase()==3?"boss.myriad_calamity.yang_jian_p3":phase()==2?"boss.myriad_calamity.yang_jian_p2":"boss.myriad_calamity.yang_jian_p1"));
        if(phase()==2)healthBar.setColor(BossEvent.BossBarColor.BLUE);
        if(phase()==3) { healthBar.setColor(BossEvent.BossBarColor.PURPLE);guardBar.setVisible(maxGuard()>0); }
    }
}
