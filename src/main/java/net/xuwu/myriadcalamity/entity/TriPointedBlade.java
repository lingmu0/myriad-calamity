package net.xuwu.myriadcalamity.entity;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.config.MyriadConfig;

/** One thrown weapon, with bounded steering only during the opening half-second. */
public final class TriPointedBlade extends Projectile {
    public static final int MAX_LIFE=50,TRACKING_TICKS=10;
    public static final double MAX_TURN_RADIANS=Math.toRadians(3.5),SPEED=1.35,MAX_RANGE=55;
    @Nullable private UUID targetId;
    private int age;
    private float damage=11;
    private Vec3 launchPoint=Vec3.ZERO;
    public TriPointedBlade(EntityType<? extends TriPointedBlade> type,Level level) { super(type,level);setNoGravity(true); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { }
    public static void launch(ServerLevel level,YangJian owner,LivingEntity target) {
        TriPointedBlade blade=MyriadCalamity.TRI_POINTED_BLADE.get().create(level);if(blade==null) { owner.recoverWeapon();return; }
        // Launch along the published warning, even if the player has already dodged away from it.
        // Only the short, angularly capped steering window below can correct the flight afterwards.
        Vec3 start=owner.position().add(0,2.1,0),aim=owner.dashEnd().add(0,.9,0);
        Vec3 direction=aim.subtract(start).normalize();
        blade.setOwner(owner);owner.bindWeapon(blade.getUUID());blade.targetId=target.getUUID();blade.launchPoint=start;blade.damage=MyriadConfig.scaleYangJianDamage(11);
        blade.setPos(start.add(direction.scale(.8)));blade.shoot(direction.x,direction.y,direction.z,(float)SPEED,0);
        if(!level.addFreshEntity(blade))owner.recoverWeapon();
    }
    @Override public void tick() {
        super.tick();
        if(!level().isClientSide) {
            if(++age>=MAX_LIFE || !(getOwner() instanceof YangJian owner) || !owner.isAlive()
                || owner.action()!=YangJian.THROW || !owner.weaponThrown() || !owner.ownsWeapon(getUUID()) || position().distanceToSqr(launchPoint)>MAX_RANGE*MAX_RANGE) { finish();return; }
            if(age<=TRACKING_TICKS && targetId!=null && level() instanceof ServerLevel server) {
                Entity target=server.getEntity(targetId);
                if(target instanceof LivingEntity player && owner.validTarget(player)) {
                    Vec3 desired=player.position().add(0,player.getBbHeight()*.5,0).subtract(position()).normalize();
                    setDeltaMovement(turnTowards(getDeltaMovement().normalize(),desired).scale(SPEED));
                }
            }
            if(!level().hasChunkAt(BlockPos.containing(position().add(getDeltaMovement())))) { finish();return; }
            // Vanilla clipping truncates the entity sweep at the first solid block.
            HitResult result=ProjectileUtil.getHitResultOnMoveVector(this,this::canHitEntity);
            if(result.getType()!=HitResult.Type.MISS) {
                if(!EventHooks.onProjectileImpact(this,result)) {
                    if(result instanceof EntityHitResult hit)hit.getEntity().hurt(damageSources().mobProjectile(this,owner),damage);
                    finish();return;
                }
            }
        }
        setPos(position().add(getDeltaMovement()));updateRotation();
    }
    static Vec3 turnTowards(Vec3 current,Vec3 desired) {
        double angle=Math.acos(Math.clamp(current.dot(desired),-1,1));
        if(angle<MAX_TURN_RADIANS)return desired;
        Vec3 axis=current.cross(desired);
        if(axis.lengthSqr()<1E-8)return current;
        axis=axis.normalize();
        return current.scale(Math.cos(MAX_TURN_RADIANS)).add(axis.cross(current).scale(Math.sin(MAX_TURN_RADIANS))).normalize();
    }
    private void finish() { if(getOwner() instanceof YangJian owner)owner.recoverWeapon(getUUID());discard(); }
    @Override protected boolean canHitEntity(Entity entity) {
        return entity instanceof LivingEntity living && getOwner() instanceof YangJian owner && owner.validTarget(living) && super.canHitEntity(entity);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);tag.putInt("BladeAge",age);tag.putFloat("BladeDamage",damage);
        if(targetId!=null)tag.putUUID("BladeTarget",targetId);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // The caster cancels partially saved attacks too: restoring this projectile could create an unwarned hit.
        age=MAX_LIFE;setNoGravity(true);
    }
}
