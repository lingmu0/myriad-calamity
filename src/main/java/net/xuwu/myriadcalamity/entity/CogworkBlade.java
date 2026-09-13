package net.xuwu.myriadcalamity.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.MyriadCalamity;

/** A finite, collision-tested blade of light emitted during the shared dance. */
public final class CogworkBlade extends Projectile {
    public static final int MAX_LIFE=64, MAX_PER_OWNER=144, MAX_PER_WAVE=32;
    public static final double MAX_RANGE=34;
    private static final EntityDataAccessor<Boolean> SILVER=SynchedEntityData.defineId(CogworkBlade.class,EntityDataSerializers.BOOLEAN);
    private int life=MAX_LIFE;
    private float damage=6;
    private Vec3 origin=Vec3.ZERO;

    public CogworkBlade(EntityType<? extends CogworkBlade> type,Level level) {
        super(type,level);setNoGravity(true);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(SILVER,false); }
    public boolean silver() { return entityData.get(SILVER); }

    /** Center is an absolute launch position, including its height above the arena floor. */
    public static void spawnWave(ServerLevel level,CogworkDancer owner,Vec3 center,int count,float speed,float damage,double angleOffset) {
        if(!owner.isAlive() || owner.action()!=CogworkDancer.SPIN)return;
        int existing=level.getEntitiesOfClass(CogworkBlade.class,new AABB(center,center).inflate(MAX_RANGE+4),
            blade->blade.getOwner()==owner).size();
        int number=Math.min(Math.clamp(count,0,MAX_PER_WAVE),Math.max(0,MAX_PER_OWNER-existing));
        if(number==0)return;
        float velocity=Math.clamp(speed,.1F,1.2F);
        for(int i=0;i<number;i++) {
            double angle=angleOffset+i*Math.PI*2/number;
            Vec3 direction=new Vec3(Math.cos(angle),0,Math.sin(angle));
            Vec3 start=center.add(direction.scale(1.4));
            if(!level.hasChunkAt(BlockPos.containing(start)))continue;
            CogworkBlade blade=MyriadCalamity.BLADE.get().create(level);
            if(blade==null)continue;
            blade.setOwner(owner);blade.origin=center;blade.damage=Math.clamp(damage,0.1F,1000F);
            blade.entityData.set(SILVER,owner.follower());
            blade.setPos(start);blade.shoot(direction.x,0,direction.z,velocity,0);
            if(level.noCollision(blade,blade.getBoundingBox()))level.addFreshEntity(blade);
        }
    }

    @Override public void tick() {
        super.tick();
        Vec3 movement=getDeltaMovement();
        if(!level().isClientSide) {
            Entity source=getOwner();
            // Cancelling the dance, losing/unloading the caster, or finishing the encounter clears its volley.
            if(--life<=0 || !(source instanceof CogworkDancer owner) || !owner.isAlive() || owner.action()!=CogworkDancer.SPIN
                || position().distanceToSqr(origin)>MAX_RANGE*MAX_RANGE || !level().hasChunkAt(BlockPos.containing(position().add(movement)))) {
                discard();return;
            }
            // Vanilla swept ray checks blocks first, truncating the entity ray at the wall.
            HitResult result=ProjectileUtil.getHitResultOnMoveVector(this,this::canHitEntity);
            if(result.getType()!=HitResult.Type.MISS) {
                if(result instanceof EntityHitResult hit)hit.getEntity().hurt(damageSources().mobProjectile(this,owner),damage);
                discard();return;
            }
        }
        setPos(position().add(movement));updateRotation();
    }
    @Override protected boolean canHitEntity(Entity entity) {
        return entity instanceof Player player && player.isAlive() && !player.isCreative() && !player.isSpectator() && super.canHitEntity(entity);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);tag.putInt("BladeLife",life);tag.putFloat("BladeDamage",damage);
        tag.putDouble("BladeOriginX",origin.x);tag.putDouble("BladeOriginY",origin.y);tag.putDouble("BladeOriginZ",origin.z);
        tag.putBoolean("BladeSilver",silver());
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);life=Math.clamp(tag.getInt("BladeLife"),0,MAX_LIFE);damage=Math.clamp(tag.getFloat("BladeDamage"),0.1F,1000F);
        origin=new Vec3(tag.getDouble("BladeOriginX"),tag.getDouble("BladeOriginY"),tag.getDouble("BladeOriginZ"));
        entityData.set(SILVER,tag.getBoolean("BladeSilver"));setNoGravity(true);
    }
}
