package net.xuwu.myriadcalamity.entity;

import net.xuwu.myriadcalamity.world.DanceEncounters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.config.MyriadConfig;

/** A linked pair with server-authoritative attacks and two hitboxes consuming one shared health pool. */
public final class CogworkDancer extends Monster {
    public static final int IDLE=0, DASH=1, SLAM=2, SPIN=3, FAILED=4, REWIND=5, BARRAGE=6;
    private static final EntityDataAccessor<Integer> ACTION=SynchedEntityData.defineId(CogworkDancer.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE=SynchedEntityData.defineId(CogworkDancer.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FOLLOWER=SynchedEntityData.defineId(CogworkDancer.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> STARTED=SynchedEntityData.defineId(CogworkDancer.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<CompoundTag> ATTACK_PLAN=SynchedEntityData.defineId(CogworkDancer.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<CompoundTag> ARENA_DATA=SynchedEntityData.defineId(CogworkDancer.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<CompoundTag> NEXT_PLAN=SynchedEntityData.defineId(CogworkDancer.class, EntityDataSerializers.COMPOUND_TAG);
    private final ServerBossEvent bossBar=new ServerBossEvent(Component.translatable("entity.myriad_calamity.cogwork_dancer"), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
    private final Set<UUID> hitThisAttack=new HashSet<>();
    @Nullable private UUID partnerId;
    @Nullable private BlockPos summoningAltar;
    private boolean initialized, solo, applyingDamage;
    private long warningLockStarted=Long.MIN_VALUE;
    private float damageFloor;
    private int transitionTicks;
    @Nullable private UUID lastStruck;
    private Vec3 arena=Vec3.ZERO, attackStart=Vec3.ZERO, attackEnd=Vec3.ZERO;
    private int cooldown=30, sequence, dashSequence, slamSequence, phaseThreePairSequence;
    private CombatMath.Lane[] barrageLanes=new CombatMath.Lane[0];
    private boolean barrageReady;

    public CogworkDancer(EntityType<? extends CogworkDancer> type, Level level) {
        super(type,level);
        setPersistenceRequired();
        setNoGravity(true);
        xpReward=80;
    }
    public static AttributeSupplier.Builder attributes() {
        // Entity attributes are requested before NeoForge has loaded the common config.
        // Use the spec defaults here; configured values are applied when an encounter is summoned.
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,MyriadConfig.DEFAULT_DANCER_HEALTH).add(Attributes.ARMOR,6)
            .add(Attributes.MOVEMENT_SPEED,0.32).add(Attributes.ATTACK_DAMAGE,MyriadConfig.DEFAULT_DANCER_DAMAGE)
            .add(Attributes.FOLLOW_RANGE,40).add(Attributes.KNOCKBACK_RESISTANCE,1);
    }
    private void applyConfiguredAttributes(boolean fillHealth) {
        var maxHealth=getAttribute(Attributes.MAX_HEALTH);
        if(maxHealth!=null) maxHealth.setBaseValue(MyriadConfig.dancerHealth());
        var attackDamage=getAttribute(Attributes.ATTACK_DAMAGE);
        if(attackDamage!=null) attackDamage.setBaseValue(MyriadConfig.dancerDamage());
        if(fillHealth) setHealth(getMaxHealth());
    }
    @Override protected void registerGoals() { /* The encounter choreography owns movement and targeting. */ }
    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ACTION,IDLE); this.entityData.define(PHASE,1); this.entityData.define(FOLLOWER,false); this.entityData.define(STARTED,0L); this.entityData.define(ATTACK_PLAN,new CompoundTag()); this.entityData.define(ARENA_DATA,new CompoundTag()); this.entityData.define(NEXT_PLAN,new CompoundTag());
    }
    public int action() { return entityData.get(ACTION); }
    public int phase() { return entityData.get(PHASE); }
    /** Every move, including the interwoven dash barrage, telegraphs for its own phase's windup. */
    public int attackWindup() { return CombatMath.windup(phase()); }
    public int queuedAction() { return entityData.get(NEXT_PLAN).getInt("a"); }
    public int queuedAttackWindup() { return CombatMath.windup(phase()); }
    public float queuedActionAge(float partial) {
        CompoundTag plan=entityData.get(NEXT_PLAN);
        return plan.contains("t")?level().getGameTime()-plan.getLong("t")+partial:-1;
    }
    public Vec3 queuedStart() { return readPlanPoint(entityData.get(NEXT_PLAN),"s"); }
    public Vec3 queuedEnd() { return readPlanPoint(entityData.get(NEXT_PLAN),"e"); }
    public boolean queuedWarningLocked() { return entityData.get(NEXT_PLAN).getBoolean("locked"); }
    public boolean follower() { return entityData.get(FOLLOWER); }
    public Vec3 arenaPosition() { return readPlanPoint(entityData.get(ARENA_DATA),"p"); }
    /** The solo flag is deliberately separate from FOLLOWER so the final dancer still telegraphs attacks. */
    public boolean solo() { return solo; }
    public float actionAge(float partial) { return Math.max(0,level().getGameTime()-entityData.get(STARTED)+partial); }
    /** Raw scheduled age; negative values are the intentional phase-three stagger before a warning begins. */
    public float scheduledActionAge(float partial) { return level().getGameTime()-entityData.get(STARTED)+partial; }
    public record AttackLane(Vec3 start,Vec3 end) {}
    public Vec3 attackStart() { return readPlanPoint(entityData.get(ATTACK_PLAN),"s"); }
    public Vec3 attackEnd() { return readPlanPoint(entityData.get(ATTACK_PLAN),"e"); }
    /**
     * Where an attack's body stands while it is still winding up: on the start of its lane for a
     * dash or the barrage, and above the landing point for a slam. Server staging and the client's
     * windup presentation both read this one rule so the body always matches the ground telegraph.
     */
    public static Vec3 stagePoint(int action,Vec3 attackStart,Vec3 attackEnd) {
        return action==SLAM?attackEnd.add(0,4.5,0):attackStart;
    }
    /** The synced windup stage point of the current action, valid once a plan exists. */
    public Vec3 stagePoint() { return stagePoint(action(),attackStart(),attackEnd()); }
    /** Each dancer publishes four lanes together; the pair therefore announces all eight at once. */
    public List<AttackLane> telegraphLanes() {
        ListTag tags=entityData.get(ATTACK_PLAN).getList("lanes",Tag.TAG_COMPOUND);
        List<AttackLane> result=new ArrayList<>(tags.size());
        for(int i=0;i<tags.size();i++) {
            CompoundTag lane=tags.getCompound(i);
            result.add(new AttackLane(readPlanPoint(lane,"s"),readPlanPoint(lane,"e")));
        }
        return result;
    }
    private static Vec3 readPlanPoint(CompoundTag tag,String prefix) {
        return new Vec3(tag.getDouble(prefix+"x"),tag.getDouble(prefix+"y"),tag.getDouble(prefix+"z"));
    }
    private static void writePlanPoint(CompoundTag tag,String prefix,Vec3 point) {
        tag.putDouble(prefix+"x",point.x);tag.putDouble(prefix+"y",point.y);tag.putDouble(prefix+"z",point.z);
    }
    private void syncArena() {
        CompoundTag tag=new CompoundTag();
        writePlanPoint(tag,"p",arena);
        entityData.set(ARENA_DATA,tag);
    }
    private void syncAttackPlan(int kind) {
        CompoundTag plan=new CompoundTag();
        writePlanPoint(plan,"s",attackStart);writePlanPoint(plan,"e",attackEnd);
        ListTag lanes=new ListTag();
        if(kind==BARRAGE)for(CombatMath.Lane lane:barrageLanes) {
            CompoundTag entry=new CompoundTag();
            writePlanPoint(entry,"s",worldPoint(lane.start()));writePlanPoint(entry,"e",worldPoint(lane.end()));
            lanes.add(entry);
        }
        plan.put("lanes",lanes);entityData.set(ATTACK_PLAN,plan);
    }
    @Nullable private CogworkDancer partner() {
        if(partnerId==null || !(level() instanceof ServerLevel server)) return null;
        Entity other=server.getEntity(partnerId);
        return other instanceof CogworkDancer dancer && dancer.isAlive() ? dancer : null;
    }
    public static boolean validSpawn(Level level, Vec3 at) {
        BlockPos floor=BlockPos.containing(at).below();
        return level.hasChunkAt(floor) && level.getBlockState(floor).isFaceSturdy(level,floor,Direction.UP)
            && level.noCollision(new AABB(at.x-0.65,at.y,at.z-0.65,at.x+0.65,at.y+3.6,at.z+0.65));
    }
    public static boolean summonAtAltar(ServerLevel level,BlockPos altar) {
        Vec3 centre=Vec3.atBottomCenterOf(altar.above());
        Vec3 a=centre.add(-4,0,0),b=centre.add(4,0,0);
        if(!validSpawn(level,a) || !validSpawn(level,b))return false;
        CogworkDancer first=MyriadCalamity.DANCER.get().create(level),second=MyriadCalamity.DANCER.get().create(level);
        if(first==null || second==null)return false;
        first.applyConfiguredAttributes(true); second.applyConfiguredAttributes(true);
        first.moveTo(a.x,a.y,a.z,-90,0);second.moveTo(b.x,b.y,b.z,90,0);
        first.initialized=second.initialized=true;
        first.arena=second.arena=centre; first.syncArena(); second.syncArena();
        first.partnerId=second.getUUID();second.partnerId=first.getUUID();
        second.entityData.set(FOLLOWER,true);
        if(!level.addFreshEntity(first))return false;
        if(!level.addFreshEntity(second)){first.discard();return false;}
        first.summoningAltar=second.summoningAltar=altar.immutable();
        DanceEncounters.get(level).begin(altar,first.getUUID(),second.getUUID());
        return true;
    }
    private void releaseAltar() {
        if(summoningAltar!=null && level() instanceof ServerLevel server)
            DanceEncounters.get(server).release(summoningAltar,getUUID());
    }
    private boolean createPartner() {
        arena=position(); syncArena();
        for(Vec3 offset : new Vec3[]{new Vec3(4,0,0),new Vec3(-4,0,0),new Vec3(0,0,4),new Vec3(0,0,-4)}) {
            Vec3 at=arena.add(offset);
            if(!validSpawn(level(),at)) continue;
            CogworkDancer other=MyriadCalamity.DANCER.get().create(level());
            if(other==null) return false;
            other.applyConfiguredAttributes(false);
            other.moveTo(at.x,at.y,at.z,getYRot()+180,0);
            other.initialized=true; other.arena=arena; other.syncArena(); other.partnerId=getUUID(); other.setHealth(getHealth());
            other.entityData.set(FOLLOWER,true);
            if(!level().addFreshEntity(other)) return false;
            partnerId=other.getUUID(); initialized=true;
            return true;
        }
        return false;
    }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if(!initialized) {
            if(!createPartner()) { setDeltaMovement(Vec3.ZERO); return; }
        }
        CogworkDancer mate=partner();
        // An unloaded partner must never be mistaken for a defeated partner or re-created.
        if(!solo && mate==null) { cancelAttack(); bossBar.removeAllPlayers(); return; }
        // The fight owns a fixed theatre footprint; nearby survival players are returned to its inner edge.
        if(!follower() || solo) enforceArenaBoundary();
        // Phase protection is persisted and progresses even when no player is currently visible.
        if(transitionTicks>0) {
            if(action()!=REWIND)resumeTransition();
            if(tickCount%10==0)updateBossBar(mate);
            tickAttack();return;
        }
        int current=Math.max(phase(),CombatMath.phase(getHealth(),getMaxHealth(),solo));
        if(current!=phase()) {
            startTransition(current);
            if(mate!=null)mate.startTransition(current);
            tickAttack();return;
        }
        if(tickCount%10==0) updateBossBar(mate);
        // The lane network belongs to the arena; moving to its far side must not cancel one half.
        Player target=action()==BARRAGE
            ? level().getNearestPlayer(arena.x,arena.y,arena.z,32,p -> p instanceof Player player && validTarget(player))
            : level().getNearestPlayer(getX(),getY(),getZ(),32,p -> p instanceof Player player && validTarget(player)
                && p.position().distanceToSqr(arena)<32*32 && hasLineOfSight(p));
        setTarget(target);
        if(target==null) { cancelAttack(); hoverHome(); return; }
        if(action()!=IDLE) { tickAttack(); return; }
        hoverHome();
        if(!solo && follower()) return;
        if(--cooldown>0 || (mate!=null && mate.action()!=IDLE)) return;
        int turn=sequence++;
        int kind=solo ? switch(turn%3) {
            case 1 -> SLAM; case 2 -> FAILED; default -> DASH;
        } : switch(turn%6) {
            case 1,5 -> SLAM; case 2 -> SPIN; case 4 -> BARRAGE; default -> DASH;
        };
        Vec3 aim=clampToArena(target.position(),13.5);
        if(phase()==3 && (kind==DASH || kind==SLAM)) planPhaseThreePair(aim,mate);
        else if(kind==DASH) planDashes(aim,mate);
        else if(kind==SLAM && mate!=null) planSlams(aim,mate);
        else if(kind==BARRAGE && mate!=null) planBarrage(mate,turn);
        else {
            // A solo slam still locks the impact to the current player position; the duet
            // planner owns the paired two-point slam choreography above.
            Vec3 genericEnd=solo && kind==SLAM?aim:arena;
            begin(kind,position(),genericEnd,0);
            if(mate!=null)mate.begin(kind,mate.position(),arena,kind==BARRAGE?0:(phase()==3?CombatMath.windup(3):0));
            if(mate!=null && phase()==3 && kind!=BARRAGE) {
                scheduleQueued(kind,position(),arena,CombatMath.windup(3)*2);
                mate.scheduleQueued(kind,mate.position(),arena,CombatMath.windup(3)*3);
            }
        }
        cooldown=CombatMath.attackCooldown(phase(),solo);
    }
    private boolean validTarget(Player p) { return p.isAlive() && !p.isCreative() && !p.isSpectator(); }
    private void enforceArenaBoundary() {
        if(!(level() instanceof ServerLevel server)) return;
        double radius=CombatMath.FIGHT_BOUNDARY_RADIUS, maxPull=48;
        for(ServerPlayer player:server.players()) {
            if(!validTarget(player)) continue;
            double dx=player.getX()-arena.x,dz=player.getZ()-arena.z;
            double distanceSquared=dx*dx+dz*dz;
            // Do not abduct players who were already far away when the paid encounter began.
            if(distanceSquared>maxPull*maxPull || !CombatMath.outsideFightBoundary(dx,dz)) continue;
            CombatMath.Point edge=CombatMath.clampDisk(dx,dz,radius-0.35);
            player.teleportTo(arena.x+edge.x(),player.getY(),arena.z+edge.z());
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked=true;
        }
    }
    private Vec3 clampToArena(Vec3 aim,double radius) {
        CombatMath.Point point=CombatMath.clampDisk(aim.x-arena.x,aim.z-arena.z,radius);
        return arena.add(point.x(),0,point.z());
    }
    private Vec3 worldPoint(CombatMath.Point point) { return arena.add(point.x(),0,point.z()); }
    private void beginLane(CombatMath.Lane lane,int delay) { begin(DASH,worldPoint(lane.start()),worldPoint(lane.end()),delay); }
    private void planDashes(Vec3 aim,@Nullable CogworkDancer mate) {
        int dashTurn=dashSequence++;
        // Phase three and the final solo phase always derive every route from the player;
        // earlier paired phases retain the alternating lead and nearby crossing lane.
        boolean everyRouteTargetsPlayer=phase()==3 || solo;
        boolean followerTargets=!everyRouteTargetsPlayer && CombatMath.targetingFollower(dashTurn);
        CogworkDancer lead=mate!=null && followerTargets?mate:this;
        CogworkDancer flank=lead==this?mate:this;
        double px=aim.x-arena.x,pz=aim.z-arena.z;
        Vec3 heading=new Vec3(px,0,pz);
        if(heading.lengthSqr()<4)heading=new Vec3(aim.x-lead.getX(),0,aim.z-lead.getZ());
        if(heading.lengthSqr()<0.01)heading=new Vec3(1,0,0);
        heading=heading.normalize();
        CombatMath.Lane direct=CombatMath.laneThrough(px,pz,heading.x,heading.z,CombatMath.ARENA_RADIUS);
        lead.beginLane(direct,0);
        if(flank!=null) {
            double angle=Math.atan2(heading.z,heading.x)+(dashTurn%2==0?1:-1)*Math.toRadians(80);
            CombatMath.Lane second;
            if(everyRouteTargetsPlayer) {
                // A different crossing direction still passes through the locked player point.
                second=CombatMath.laneThrough(px,pz,Math.cos(angle),Math.sin(angle),CombatMath.ARENA_RADIUS);
            } else {
                // In phases one and two the partner's route passes near the target instead.
                CombatMath.Point side=CombatMath.clampDisk(px-heading.x*4.5,pz-heading.z*4.5,10);
                second=CombatMath.laneThrough(side.x(),side.z(),Math.cos(angle),Math.sin(angle),CombatMath.ARENA_RADIUS);
            }
            int delay=phase()==3?CombatMath.windup(3):0;
            flank.beginLane(second,delay);
            if(phase()==3) {
                lead.scheduleQueued(DASH,worldPoint(direct.start()),worldPoint(direct.end()),CombatMath.windup(3)*2);
                flank.scheduleQueued(DASH,worldPoint(second.start()),worldPoint(second.end()),CombatMath.windup(3)*3);
            }
        }
    }
    private void planPhaseThreePair(Vec3 aim,CogworkDancer mate) {
        int pattern=phaseThreePairSequence++&3;
        int leadAction=switch(pattern) { case 1,3 -> SLAM; default -> DASH; };
        int flankAction=switch(pattern) { case 0 -> SLAM; case 1,2 -> DASH; default -> SLAM; };
        int routeTurn=dashSequence++;
        double px=aim.x-arena.x,pz=aim.z-arena.z;
        Vec3 heading=new Vec3(px,0,pz);
        if(heading.lengthSqr()<4)heading=new Vec3(aim.x-getX(),0,aim.z-getZ());
        if(heading.lengthSqr()<0.01)heading=new Vec3(1,0,0);
        heading=heading.normalize();
        CombatMath.Lane direct=CombatMath.laneThrough(px,pz,heading.x,heading.z,CombatMath.ARENA_RADIUS);
        double angle=Math.atan2(heading.z,heading.x)+((routeTurn&1)==0?Math.toRadians(80):-Math.toRadians(80));
        CombatMath.Lane second=CombatMath.laneThrough(px,pz,Math.cos(angle),Math.sin(angle),CombatMath.ARENA_RADIUS);
        Vec3 leadBody=position(),flankBody=mate.position();
        Vec3 leadStart=leadAction==DASH?worldPoint(direct.start()):leadBody;
        Vec3 leadEnd=leadAction==DASH?worldPoint(direct.end()):aim;
        Vec3 flankStart=flankAction==DASH?worldPoint(second.start()):flankBody;
        Vec3 flankEnd=flankAction==DASH?worldPoint(second.end()):aim;
        begin(leadAction,leadStart,leadEnd,0);
        mate.begin(flankAction,flankStart,flankEnd,CombatMath.windup(3));
        int windup=CombatMath.windup(3);
        scheduleQueued(leadAction,leadStart,leadEnd,windup*2);
        mate.scheduleQueued(flankAction,flankStart,flankEnd,windup*3);
    }
    private void planSlams(Vec3 aim,CogworkDancer mate) {
        int slamTurn=slamSequence++;
        boolean followerTargets=CombatMath.targetingFollower(slamTurn);
        CogworkDancer lead=followerTargets?mate:this,flank=followerTargets?this:mate;
        double px=aim.x-arena.x,pz=aim.z-arena.z;
        double angle=Math.atan2(pz,px)+Math.PI/2+(slamTurn%2)*Math.PI;
        CombatMath.Point nearby=CombatMath.nearbyImpact(px,pz,angle,13.5);
        lead.begin(SLAM,lead.position(),aim,0);
        // A slam and a crossing dash are one combined beat in the earlier paired phases.
        Vec3 heading=new Vec3(px,0,pz);
        if(heading.lengthSqr()<0.01)heading=new Vec3(1,0,0);
        heading=heading.normalize();
        double crossingAngle=Math.atan2(heading.z,heading.x)+(slamTurn%2==0?1:-1)*Math.toRadians(80);
        CombatMath.Point side=CombatMath.clampDisk(px-heading.x*4.5,pz-heading.z*4.5,10);
        CombatMath.Lane crossing=CombatMath.laneThrough(side.x(),side.z(),Math.cos(crossingAngle),Math.sin(crossingAngle),CombatMath.ARENA_RADIUS);
        flank.beginLane(crossing,0);
    }
    private void planBarrage(CogworkDancer mate,int turn) {
        double rotation=(turn*0.73+getRandom().nextDouble()*Math.PI*2)%(Math.PI*2);
        Vec3 safe=arena.add(Math.cos(rotation+Math.PI/2)*4,0,Math.sin(rotation+Math.PI/2)*4);
        prepareBarrage(safe,rotation);mate.prepareBarrage(safe,rotation);
    }
    private void prepareBarrage(Vec3 safe,double rotation) {
        barrageLanes=new CombatMath.Lane[CombatMath.BARRAGE_PASSES];
        for(int pass=0;pass<barrageLanes.length;pass++)
            barrageLanes[pass]=CombatMath.barrageLane(safe.x-arena.x,safe.z-arena.z,rotation,pass,follower());
        CombatMath.Lane first=barrageLanes[0];
        begin(BARRAGE,worldPoint(first.start()),worldPoint(first.end()),0);
    }
    private void scheduleQueued(int action,Vec3 start,Vec3 end,int delay) {
        CompoundTag plan=new CompoundTag();
        plan.putInt("a",action); plan.putLong("t",level().getGameTime()+delay);
        writePlanPoint(plan,"s",start); writePlanPoint(plan,"e",end);
        entityData.set(NEXT_PLAN,plan);
    }
    private void clearQueued() { entityData.set(NEXT_PLAN,new CompoundTag()); }
    @Nullable private Player currentArenaTarget() {
        return level().getNearestPlayer(arena.x,arena.y,arena.z,32,
            p -> p instanceof Player player && validTarget(player) && p.position().distanceToSqr(arena)<32*32);
    }
    private CombatMath.Lane laneForCurrentTarget(Vec3 aim,Vec3 oldStart,Vec3 oldEnd) {
        double dx=oldEnd.x-oldStart.x,dz=oldEnd.z-oldStart.z;
        if(Math.hypot(dx,dz)<0.01) { dx=aim.x-getX(); dz=aim.z-getZ(); }
        if(Math.hypot(dx,dz)<0.01) { dx=1; dz=0; }
        return CombatMath.laneThrough(aim.x-arena.x,aim.z-arena.z,dx,dz,CombatMath.ARENA_RADIUS);
    }
    /** Re-lock a phase-three warning when this dancer's own warning window opens. */
    private void relockWarningToCurrentTarget() {
        if(phase()!=3 || (action()!=DASH && action()!=SLAM)) return;
        long started=entityData.get(STARTED),age=level().getGameTime()-started;
        // A plan is created during the coordinator tick, so its first observed tick can be age 1.
        if(age<0 || age>1 || warningLockStarted==started) return;
        Player target=currentArenaTarget();
        if(target==null) return;
        warningLockStarted=started;
        Vec3 aim=clampToArena(target.position(),13.5);
        if(action()==DASH) {
            CombatMath.Lane lane=laneForCurrentTarget(aim,attackStart,attackEnd);
            attackStart=worldPoint(lane.start()); attackEnd=worldPoint(lane.end());
            syncAttackPlan(DASH);
            teleportTo(attackStart.x,attackStart.y,attackStart.z);
        } else {
            attackStart=position(); attackEnd=aim;
            syncAttackPlan(SLAM);
            teleportTo(attackEnd.x,attackEnd.y+4.5,attackEnd.z);
        }
    }
    /** Re-lock a staggered phase-three queued warning before its client telegraph is drawn. */
    private void relockQueuedWarningToCurrentTarget() {
        if(phase()!=3) return;
        CompoundTag plan=entityData.get(NEXT_PLAN);
        int next=plan.getInt("a");
        if(next!=DASH && next!=SLAM || !plan.contains("t")) return;
        long now=level().getGameTime();
        if(now!=plan.getLong("t") || plan.getBoolean("locked")) return;
        Player target=currentArenaTarget();
        if(target==null) return;
        Vec3 aim=clampToArena(target.position(),13.5);
        if(next==DASH) {
            Vec3 oldStart=readPlanPoint(plan,"s"),oldEnd=readPlanPoint(plan,"e");
            CombatMath.Lane lane=laneForCurrentTarget(aim,oldStart,oldEnd);
            writePlanPoint(plan,"s",worldPoint(lane.start())); writePlanPoint(plan,"e",worldPoint(lane.end()));
        } else {
            writePlanPoint(plan,"e",aim);
        }
        plan.putBoolean("locked",true);
        entityData.set(NEXT_PLAN,plan);
    }
    private void begin(int action,Vec3 start,Vec3 end,int delay) {
        entityData.set(ACTION,action); entityData.set(STARTED,level().getGameTime()+delay);
        warningLockStarted=Long.MIN_VALUE;
        attackStart=start; attackEnd=end; hitThisAttack.clear(); barrageReady=false; setDeltaMovement(Vec3.ZERO);
        syncAttackPlan(action);
        // Stage the body instantly instead of letting the windup slowly drag it across the arena.
        // Delayed phase-three partners are staged as soon as their warning is planned.
        if(action==DASH || action==BARRAGE || action==SLAM) {
            Vec3 stage=stagePoint(action,attackStart,attackEnd);
            teleportTo(stage.x,stage.y,stage.z);
        }
    }
    private void activateQueued() {
        CompoundTag plan=entityData.get(NEXT_PLAN);
        boolean lockedAtWarning=plan.getBoolean("locked");
        if(phase()==3 && (plan.getInt("a")==DASH || plan.getInt("a")==SLAM) && !plan.getBoolean("locked")) {
            // If a loaded or lagged entity missed the exact warning tick, still lock at activation.
            Player target=currentArenaTarget();
            if(target!=null) {
                Vec3 aim=clampToArena(target.position(),13.5);
                int next=plan.getInt("a");
                if(next==DASH) {
                    Vec3 oldStart=readPlanPoint(plan,"s"),oldEnd=readPlanPoint(plan,"e");
                    CombatMath.Lane lane=laneForCurrentTarget(aim,oldStart,oldEnd);
                    writePlanPoint(plan,"s",worldPoint(lane.start())); writePlanPoint(plan,"e",worldPoint(lane.end()));
                } else writePlanPoint(plan,"e",aim);
                plan.putBoolean("locked",true);
                entityData.set(NEXT_PLAN,plan);
                lockedAtWarning=true;
            }
        }
        int next=plan.getInt("a"); Vec3 start=readPlanPoint(plan,"s"),end=readPlanPoint(plan,"e");
        clearQueued(); begin(next,start,end,0);
        if(lockedAtWarning && phase()==3 && (next==DASH || next==SLAM)) warningLockStarted=entityData.get(STARTED);
    }
    private void cancelAttack() { clearQueued(); barrageLanes=new CombatMath.Lane[0]; entityData.set(ACTION,IDLE); entityData.set(ATTACK_PLAN,new CompoundTag()); hitThisAttack.clear(); setDeltaMovement(Vec3.ZERO); cooldown=Math.max(cooldown,16); }
    private void finishAttack() {
        if(queuedAction()!=IDLE && queuedActionAge(0)>=0) { activateQueued(); return; }
        clearQueued(); entityData.set(ACTION,IDLE); entityData.set(ATTACK_PLAN,new CompoundTag()); hitThisAttack.clear(); setDeltaMovement(Vec3.ZERO);
    }
    private void startTransition(int nextPhase) {
        clearQueued(); entityData.set(PHASE,nextPhase);transitionTicks=CombatMath.TRANSITION_TICKS;
        begin(REWIND,position(),position(),0);
        playSound(SoundEvents.ANVIL_USE,0.9F,nextPhase==4?0.6F:1.3F);
    }
    private void resumeTransition() {
        begin(REWIND,position(),position(),0);
        entityData.set(STARTED,level().getGameTime()-(CombatMath.TRANSITION_TICKS-transitionTicks));
    }
    private void hoverHome() {
        Vec3 station=arena.add(follower()?4:-4,0,0);
        double bob=phase()==4?0:Math.sin(tickCount*0.06)*0.12;
        steer(station.add(0,bob,0),0.13);
    }
    private void face(Vec3 point,float speed) {
        Vec3 delta=point.subtract(position());
        if(delta.horizontalDistanceSqr()>0.01) {
            float desired=(float)(Math.atan2(-delta.x,delta.z)*180/Math.PI);
            setYRot(Mth.approachDegrees(getYRot(),desired,speed));setYBodyRot(getYRot());
        }
    }
    private void steer(Vec3 point,double maximum) { steer(point,maximum,true); }
    private void steer(Vec3 point,double maximum,boolean turn) {
        Vec3 delta=point.subtract(position());
        Vec3 requested=delta.length()>maximum?delta.normalize().scale(maximum):delta;
        // Check the swept path before applying hit detection, including player-built obstacles.
        int steps=Math.max(1,(int)Math.ceil(requested.length()/0.3));
        Vec3 allowed=Vec3.ZERO;
        for(int i=1;i<=steps;i++) {
            Vec3 candidate=requested.scale((double)i/steps);
            if(!level().noCollision(this,getBoundingBox().move(candidate)))break;
            allowed=candidate;
        }
        setDeltaMovement(allowed);if(turn)face(point,30);getNavigation().stop();
    }
    private void tickAttack() {
        int age=(int)(level().getGameTime()-entityData.get(STARTED));
        relockQueuedWarningToCurrentTarget();
        if(age<0) { setDeltaMovement(Vec3.ZERO); return; }
        relockWarningToCurrentTarget();
        int windup=attackWindup();
        if(action()==REWIND) {
            hoverHome();
            if(age%6==0) particles(ParticleTypes.ELECTRIC_SPARK,position().add(0,1.5,0),8,0.6);
            if(--transitionTicks<=0) { transitionTicks=0;finishAttack(); cooldown=12; }
            return;
        }
        if(age<windup) {
            // begin() has already teleported the dancer to its staging point; the windup
            // holds that position instead of visibly drifting across the theatre.
            // The hold is re-asserted every tick: entity pushes, collision resolution and the
            // partner's own crossing dash must never drag the body off the painted warning.
            Vec3 stage=stagePoint(action(),attackStart,attackEnd);
            if(position().distanceToSqr(stage)>1.0E-4) teleportTo(stage.x,stage.y,stage.z);
            setDeltaMovement(Vec3.ZERO);
            if(action()==DASH || action()==BARRAGE) face(attackEnd,30);
            // Ground ribbons and circles are drawn client-side from the synchronized locked plan.
            if(age%10==0) playSound(SoundEvents.NOTE_BLOCK_HAT.value(),0.8F,phase()==4?0.6F:1.2F);
            return;
        }
        int active=age-windup;
        if(active==0) playSound(SoundEvents.PLAYER_ATTACK_SWEEP,1.5F,phase()==4?0.65F:1.1F);
        switch(action()) {
            case DASH -> {
                // An obstructed staging route cancels rather than attacking along an unannounced line.
                if(active==0 && position().distanceToSqr(attackStart)>1) { finishAttack();return; }
                if(active<CombatMath.DASH_TRAVEL_TICKS) {
                    Vec3 before=position();steer(attackEnd,phase()==4?2.8:2.6);
                    strikeSegment(before,before.add(getDeltaMovement()),1.35,MyriadConfig.scaleDancerDamage(phase()==4?5:9));
                    if(active%2==0)particles(ParticleTypes.SWEEP_ATTACK,position().add(0,1,0),1,0.1);
                } else { setDeltaMovement(Vec3.ZERO);if(active>=CombatMath.DASH_ACTIVE_TICKS)finishAttack(); }
            }
            case SLAM -> {
                if(active<8)steer(attackEnd,0.95);
                else {
                    setDeltaMovement(Vec3.ZERO);
                    if(active==8) {
                        if(position().distanceToSqr(attackEnd)>1) { finishAttack();return; }
                        playSound(SoundEvents.IRON_GOLEM_ATTACK,1.5F,0.65F);
                        particles(ParticleTypes.CRIT,attackEnd.add(0,0.15,0),32,1.3);
                    }
                    double radius=Math.min(CombatMath.SLAM_RADIUS,(active-8)*0.45);
                    // The surface and hit height are independent of the hovering entity's position.
                    if(active<=27) { ring(attackEnd.add(0,0.08,0),radius);strikeGroundWave(attackEnd,radius,0.55,MyriadConfig.scaleDancerDamage(10)); }
                    if(active>=42)finishAttack();
                }
            }
            case SPIN -> {
                double angle=(active*0.15)+(follower()?Math.PI:0);
                if(active<16)steer(attackEnd.add(Math.cos(angle)*1.5,0.6,Math.sin(angle)*1.5),0.6);
                else if(active<52) {
                    steer(attackEnd.add(Math.cos(angle)*1.5,0.6,Math.sin(angle)*1.5),0.65);
                    if(active%2==0) {
                        for(int blade=0;blade<8;blade++) {
                            double sweepAngle=angle+blade*Math.PI/4;
                            double sweepRadius=3.5+(blade%3)*2.9;
                            particles(ParticleTypes.SWEEP_ATTACK,attackEnd.add(Math.cos(sweepAngle)*sweepRadius,
                                0.7+(blade%2)*0.65,Math.sin(sweepAngle)*sweepRadius),1,0.3);
                        }
                        if(active%4==0)ring(attackEnd.add(0,1,0),CombatMath.DUET_RADIUS);
                    }
                    if(!follower() && (active-16)%7==0) {
                        playSound(SoundEvents.TRIDENT_THROW,0.8F,1.35F+(active-16)*0.006F);
                        CogworkBlade.spawnWave((ServerLevel)level(),this,attackEnd.add(0,1.2,0),24,0.7F,MyriadConfig.scaleDancerDamage(6),
                            (active-16)*0.065);
                    }
                    strikeRing(attackEnd,0,CombatMath.DUET_RADIUS,3.2,MyriadConfig.scaleDancerDamage(12));
                } else {
                    steer(attackEnd.add(follower()?0.7:-0.7,1,0),0.15);
                    if(active>=78)finishAttack();
                }
            }
            case BARRAGE -> tickBarrage(active);
            case FAILED -> {
                // The surviving dancer tries to reach its missing partner, then falls without a hitbox.
                steer(attackEnd.add(0,active<25?1.8:0,0),active<25?0.1:0.22);
                if(active==26)playSound(SoundEvents.IRON_GOLEM_HURT,1,0.55F);
                if(active>=90)finishAttack();
            }
            default -> finishAttack();
        }
    }
    private void tickBarrage(int active) {
        if(barrageLanes.length!=CombatMath.BARRAGE_PASSES) { finishAttack();return; }
        int pass=active/CombatMath.BARRAGE_PASS_TICKS,local=active%CombatMath.BARRAGE_PASS_TICKS;
        if(pass<CombatMath.BARRAGE_PASSES) {
            if(local==0)hitThisAttack.clear();
            CombatMath.Lane lane=barrageLanes[pass];
            Vec3 start=worldPoint(lane.start()),end=worldPoint(lane.end());
            if(local<CombatMath.BARRAGE_WARNING_TICKS) {
                // The body sweeps onto the lane it is about to run, so the four charges read as one
                // continuous flurry instead of four separate stops. The plan is republished here so
                // the warning and the body agree, and the last warning tick snaps onto the lane
                // start if the sweep fell short: a pass is never silently cancelled.
                if(local==0) {
                    attackStart=start; attackEnd=end;
                    syncAttackPlan(BARRAGE);
                    playSound(SoundEvents.NOTE_BLOCK_HAT.value(),1,1.5F);
                }
                steer(start,4.2,false);face(end,50);
            } else if(CombatMath.barrageCharging(active)) {
                if(local==CombatMath.BARRAGE_WARNING_TICKS) {
                    if(position().distanceToSqr(start)>0.04) teleportTo(start.x,start.y,start.z);
                    barrageReady=true;
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP,1.3F,1.6F);
                }
                if(barrageReady) {
                    Vec3 before=position();steer(end,4.2);
                    strikeSegment(before,before.add(getDeltaMovement()),CombatMath.BARRAGE_LANE_RADIUS,MyriadConfig.scaleDancerDamage(7));
                    particles(ParticleTypes.SWEEP_ATTACK,position().add(0,1,0),2,0.2);
                } else setDeltaMovement(Vec3.ZERO);
            } else setDeltaMovement(Vec3.ZERO);
        } else { setDeltaMovement(Vec3.ZERO); }
        if(active>=CombatMath.BARRAGE_ACTIVE_TICKS)finishAttack();
    }
    private void strikeGroundWave(Vec3 center,double radius,double width,float damage) {
        for(Player p:level().getEntitiesOfClass(Player.class,new AABB(center,center).inflate(radius+width+1,2,radius+width+1),this::validTarget)) {
            if(!CombatMath.touchesGroundWave(p.getY(),p.getBoundingBox().maxY,center.y))continue;
            double distance=Math.hypot(p.getX()-center.x,p.getZ()-center.z);
            if(CombatMath.inRing(distance,radius,width+p.getBbWidth()*0.5) && hasLineOfSight(p))hit(p,damage);
        }
    }
    private void strikeSegment(Vec3 a,Vec3 b,double radius,float damage) {
        AABB area=new AABB(a,b).inflate(radius,2, radius);
        for(Player p:level().getEntitiesOfClass(Player.class,area,this::validTarget)) {
            if(p.getBoundingBox().maxY<a.y || p.getY()>a.y+2.8) continue;
            double reach=radius+p.getBbWidth()*0.5;
            if(CombatMath.segmentDistanceSquared(p.getX(),p.getZ(),a.x,a.z,b.x,b.z)<=reach*reach && hasLineOfSight(p)) hit(p,damage);
        }
    }
    private void strikeRing(Vec3 center,double radius,double width,double height,float damage) {
        for(Player p:level().getEntitiesOfClass(Player.class,new AABB(center,center).inflate(radius+width+1,height+2,radius+width+1),this::validTarget)) {
            if(p.getY()>center.y+height || p.getBoundingBox().maxY<center.y) continue;
            double distance=Math.sqrt(Math.pow(p.getX()-center.x,2)+Math.pow(p.getZ()-center.z,2));
            if(CombatMath.inRing(distance,radius,width+p.getBbWidth()*0.5) && hasLineOfSight(p)) hit(p,damage);
        }
    }
    private void hit(Player player,float damage) {
        if(hitThisAttack.contains(player.getUUID())) return;
        if(player.hurt(damageSources().mobAttack(this),damage)) {
            hitThisAttack.add(player.getUUID());
            player.knockback(0.55, getX()-player.getX(), getZ()-player.getZ());
        }
    }
    private void ring(Vec3 at,double radius) {
        ServerLevel server=(ServerLevel)level();
        for(int i=0;i<40;i++) {
            double angle=i*Math.PI/20;
            server.sendParticles(ParticleTypes.CRIT,at.x+Math.cos(angle)*radius,at.y,at.z+Math.sin(angle)*radius,1,0,0,0,0);
        }
    }
    private void particles(net.minecraft.core.particles.SimpleParticleType type,Vec3 pos,int count,double spread) {
        ((ServerLevel)level()).sendParticles(type,pos.x,pos.y,pos.z,count,spread,spread*0.5,spread,0.02);
    }
    private void updateBossBar(@Nullable CogworkDancer mate) {
        if(follower() && !solo) { bossBar.removeAllPlayers(); return; }
        bossBar.setName(Component.translatable("boss.myriad_calamity.phase"+phase()));
        bossBar.setColor(phase()==4?BossEvent.BossBarColor.BLUE:BossEvent.BossBarColor.YELLOW);
        bossBar.setProgress(Mth.clamp(getHealth()/getMaxHealth(),0,1));
        for(ServerPlayer p:((ServerLevel)level()).players()) {
            if(!p.isSpectator() && p.distanceToSqr(this)<48*48) bossBar.addPlayer(p); else bossBar.removePlayer(p);
        }
        for(ServerPlayer p:Set.copyOf(bossBar.getPlayers())) if(p.level()!=level() || !p.isAlive()) bossBar.removePlayer(p);
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(level().isClientSide || source.is(DamageTypes.GENERIC_KILL))return super.hurt(source,amount);
        if(applyingDamage || transitionTicks>0 || action()==REWIND || Float.isNaN(amount) || amount<=0)return false;
        CogworkDancer mate=partner();
        // Pause the shared pool when its other hitbox is unloaded; /kill remains available.
        if(!solo && mate==null)return false;
        float before=getHealth();
        damageFloor=CombatMath.minimumHealthAfterHit(before,getMaxHealth(),phase(),solo);
        boolean damaged;
        applyingDamage=true;
        try {
            // Bound incoming arithmetic and then clamp final health as well, including armor-bypassing damage.
            damaged=super.hurt(source,Math.min(amount,CombatMath.perHitCap(getMaxHealth(),solo)));
        } finally { applyingDamage=false; }
        if(!damaged || getHealth()>=before)return damaged;
        if(solo)return true;
        mate.setHealth(getHealth());
        if(source.getEntity() instanceof Player) { lastStruck=getUUID();mate.lastStruck=lastStruck; }
        if(CombatMath.phaseThresholdReached(getHealth(),getMaxHealth(),phase())) {
            if(phase()<3) {
                int next=phase()+1;
                startTransition(next);mate.startTransition(next);
            } else {
                CogworkDancer casualty=CombatMath.finaleCasualty(lastStruck,getUUID(),mate.getUUID()).equals(mate.getUUID())?mate:this;
                CogworkDancer survivor=casualty==this?mate:this;
                survivor.solo=true;survivor.partnerId=null;
                survivor.startTransition(4);
                casualty.setHealth(0);casualty.die(source);
            }
        }
        return true;
    }
    @Override public void setHealth(float health) {
        super.setHealth(applyingDamage?CombatMath.protectedHealth(health,damageFloor):health);
    }
    @Override public void heal(float amount) {
        super.heal(amount);
        CogworkDancer mate=partner();
        if(!solo && mate!=null) mate.setHealth(getHealth());
    }
    @Override public void die(DamageSource source) {
        releaseAltar();
        if(!level().isClientSide) {
            CogworkDancer mate=partner();
            if(mate!=null && !mate.solo) { mate.solo=true; mate.partnerId=null; mate.setHealth(Math.max(1,Math.min(mate.getHealth(),getMaxHealth()*0.2F))); mate.startTransition(4); }
            bossBar.removeAllPlayers();
        }
        super.die(source);
    }
    @Override public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }
    @Override public void remove(Entity.RemovalReason reason) {
        if(reason==Entity.RemovalReason.KILLED || reason==Entity.RemovalReason.DISCARDED)releaseAltar();
        bossBar.removeAllPlayers();super.remove(reason);
    }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean causeFallDamage(float distance,float multiplier,DamageSource source) { return false; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.IRON_GOLEM_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.IRON_GOLEM_DEATH; }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if(summoningAltar!=null)tag.putLong("SummoningAltar",summoningAltar.asLong());
        tag.putBoolean("DanceInitialized",initialized); tag.putBoolean("Solo",solo); tag.putBoolean("Follower",follower());
        if(partnerId!=null) tag.putUUID("DancePartner",partnerId);
        if(lastStruck!=null) tag.putUUID("LastStruckDancer",lastStruck);
        tag.putDouble("ArenaX",arena.x); tag.putDouble("ArenaY",arena.y); tag.putDouble("ArenaZ",arena.z);
        tag.putInt("DanceSequence",sequence); tag.putInt("DancePhase",phase());
        tag.putInt("DashSequence",dashSequence);tag.putInt("SlamSequence",slamSequence);tag.putInt("PhaseThreePairSequence",phaseThreePairSequence);
        tag.putInt("TransitionTicks",transitionTicks);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        summoningAltar=tag.contains("SummoningAltar")?BlockPos.of(tag.getLong("SummoningAltar")):null;
        initialized=tag.getBoolean("DanceInitialized"); solo=tag.getBoolean("Solo");
        entityData.set(FOLLOWER,tag.getBoolean("Follower"));
        entityData.set(PHASE,Mth.clamp(tag.getInt("DancePhase"),1,4));
        partnerId=tag.hasUUID("DancePartner")?tag.getUUID("DancePartner"):null;
        arena=new Vec3(tag.getDouble("ArenaX"),tag.getDouble("ArenaY"),tag.getDouble("ArenaZ")); syncArena();
        lastStruck=tag.hasUUID("LastStruckDancer")?tag.getUUID("LastStruckDancer"):null;
        sequence=tag.getInt("DanceSequence");dashSequence=tag.getInt("DashSequence");slamSequence=tag.getInt("SlamSequence");phaseThreePairSequence=tag.getInt("PhaseThreePairSequence");
        transitionTicks=CombatMath.restoredTransitionTicks(tag.getInt("TransitionTicks"));
        cancelAttack();if(transitionTicks>0)resumeTransition();setNoGravity(true);
    }
}
