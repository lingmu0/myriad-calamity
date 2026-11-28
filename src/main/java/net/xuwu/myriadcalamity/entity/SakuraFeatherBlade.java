package net.xuwu.myriadcalamity.entity;

import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.MyriadCalamity;

/**
 * Yang Jian-inspired 3-Wave Orbiting Everblossom Spirit-Sword (常樱神锋飞星):
 *   - All 3x blades spawn together at once in 3 majestic revolving celestial rings around the Divine Dragon.
 *   - Each wave (0, 1, 2) keeps visibly orbiting around the dragon until its wave lock tick (t = 20, 46, 72),
 *     whips its sword-tips over 5 ticks to aim directly at the target's CURRENT live position, locks that
 *     position right as the wave launches, and rockets out in a luminous sword-formation volley!
 */
public final class SakuraFeatherBlade extends Entity {
    private static final EntityDataAccessor<Long> DATA_SPAWN_TIME = SynchedEntityData.defineId(SakuraFeatherBlade.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_LAUNCH_TIME = SynchedEntityData.defineId(SakuraFeatherBlade.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_WAVE = SynchedEntityData.defineId(SakuraFeatherBlade.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SLOT = SynchedEntityData.defineId(SakuraFeatherBlade.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_WAVE_SIZE = SynchedEntityData.defineId(SakuraFeatherBlade.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET_ID = SynchedEntityData.defineId(SakuraFeatherBlade.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_LOCKED = SynchedEntityData.defineId(SakuraFeatherBlade.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_CENTER_X = SynchedEntityData.defineId(SakuraFeatherBlade.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CENTER_Y = SynchedEntityData.defineId(SakuraFeatherBlade.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CENTER_Z = SynchedEntityData.defineId(SakuraFeatherBlade.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AIM_X = SynchedEntityData.defineId(SakuraFeatherBlade.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AIM_Y = SynchedEntityData.defineId(SakuraFeatherBlade.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AIM_Z = SynchedEntityData.defineId(SakuraFeatherBlade.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_VEL_X = SynchedEntityData.defineId(SakuraFeatherBlade.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_VEL_Y = SynchedEntityData.defineId(SakuraFeatherBlade.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_VEL_Z = SynchedEntityData.defineId(SakuraFeatherBlade.class, EntityDataSerializers.FLOAT);

    public static final double LAUNCH_SPEED = 2.65;

    private UUID ownerId;
    private UUID targetUuid;
    private float damage = 4.0F;
    private boolean released;

    public SakuraFeatherBlade(EntityType<? extends SakuraFeatherBlade> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static SakuraFeatherBlade createOrbiting(Level level, DivineDragon owner, LivingEntity target,
                                                    int wave, int slot, int waveSize, float damage) {
        SakuraFeatherBlade f = new SakuraFeatherBlade(MyriadCalamity.SAKURA_FEATHER_BLADE.get(), level);
        f.ownerId = owner.getUUID();
        f.targetUuid = target != null ? target.getUUID() : null;
        f.damage = damage;
        f.entityData.set(DATA_SPAWN_TIME, level.getGameTime());
        f.entityData.set(DATA_LAUNCH_TIME, -1L);
        f.entityData.set(DATA_WAVE, wave);
        f.entityData.set(DATA_SLOT, slot);
        f.entityData.set(DATA_WAVE_SIZE, Math.max(1, waveSize));
        f.entityData.set(DATA_TARGET_ID, target != null ? target.getId() : -1);
        f.entityData.set(DATA_LOCKED, false);
        f.entityData.set(DATA_CENTER_X, (float) owner.getX());
        f.entityData.set(DATA_CENTER_Y, (float) owner.getY());
        f.entityData.set(DATA_CENTER_Z, (float) owner.getZ());

        Vec3 initialAim = target != null
            ? target.position().add(0, target.getBbHeight() * 0.5, 0)
            : owner.position().add(0, -18.0, 52.0);
        f.setLockedAimPoint(initialAim);
        Vec3 p0 = f.orbitPosition(0.0F);
        f.setPos(p0.x, p0.y, p0.z);
        return f;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SPAWN_TIME, 0L);
        builder.define(DATA_LAUNCH_TIME, -1L);
        builder.define(DATA_WAVE, 0);
        builder.define(DATA_SLOT, 0);
        builder.define(DATA_WAVE_SIZE, 12);
        builder.define(DATA_TARGET_ID, -1);
        builder.define(DATA_LOCKED, false);
        builder.define(DATA_CENTER_X, 0.5F);
        builder.define(DATA_CENTER_Y, 150.0F);
        builder.define(DATA_CENTER_Z, 0.5F);
        builder.define(DATA_AIM_X, 0.5F);
        builder.define(DATA_AIM_Y, 130.0F);
        builder.define(DATA_AIM_Z, 55.0F);
        builder.define(DATA_VEL_X, 0.0F);
        builder.define(DATA_VEL_Y, -0.5F);
        builder.define(DATA_VEL_Z, 2.0F);
    }

    private void setLockedAimPoint(Vec3 p) {
        entityData.set(DATA_AIM_X, (float) p.x);
        entityData.set(DATA_AIM_Y, (float) p.y);
        entityData.set(DATA_AIM_Z, (float) p.z);
    }

    public Vec3 lockedAimPoint() {
        return new Vec3(entityData.get(DATA_AIM_X), entityData.get(DATA_AIM_Y), entityData.get(DATA_AIM_Z));
    }

    public Vec3 syncedVelocity() {
        return new Vec3(entityData.get(DATA_VEL_X), entityData.get(DATA_VEL_Y), entityData.get(DATA_VEL_Z));
    }

    public int wave() { return entityData.get(DATA_WAVE); }
    public int slot() { return entityData.get(DATA_SLOT); }
    public int waveSize() { return Math.max(1, entityData.get(DATA_WAVE_SIZE)); }
    public int waveLockTick() { return DivineDragonMath.featherWaveLockTick(wave()); }
    public int releaseTick() { return DivineDragonMath.featherBladeReleaseTick(wave(), slot(), waveSize()); }

    public float effectAge(float partial) {
        return Math.max(0.0F, (level().getGameTime() - entityData.get(DATA_SPAWN_TIME)) + partial);
    }

    public boolean isLaunched() {
        return entityData.get(DATA_LAUNCH_TIME) >= 0L;
    }

    public float flightAge(float partial) {
        long lt = entityData.get(DATA_LAUNCH_TIME);
        if (lt < 0L) return -1.0F;
        return Math.max(0.0F, (level().getGameTime() - lt) + partial);
    }

    public Vec3 orbitPosition(float age) {
        double[] off = DivineDragonMath.featherOrbitOffset(age, wave(), slot(), waveSize());
        return new Vec3(
            entityData.get(DATA_CENTER_X) + off[0],
            entityData.get(DATA_CENTER_Y) + off[1],
            entityData.get(DATA_CENTER_Z) + off[2]);
    }

    private Vec3 liveTargetPoint() {
        int tid = entityData.get(DATA_TARGET_ID);
        if (tid >= 0) {
            Entity e = level().getEntity(tid);
            if (e instanceof LivingEntity living && living.isAlive()) {
                return living.position().add(0, living.getBbHeight() * 0.55, 0);
            }
        }
        return lockedAimPoint();
    }

    /** Smoothly rotates blade nose from orbit tangent -> live target during the 6-tick pre-launch aim window! */
    public Vec3 heading(float partial) {
        if (isLaunched()) {
            Vec3 v = syncedVelocity();
            return v.lengthSqr() > 1e-6 ? v.normalize() : new Vec3(0, -0.3, 1).normalize();
        }
        float age = effectAge(partial);
        Vec3 p0 = orbitPosition(age);
        Vec3 p1 = orbitPosition(age + 0.2F);
        Vec3 tangent = p1.subtract(p0);
        if (tangent.lengthSqr() < 1e-6) tangent = new Vec3(1, 0, 0);
        else tangent = tangent.normalize();

        int lockT = waveLockTick();
        double align = Mth.clamp((age - (lockT - 7.0)) / 6.0, 0.0, 1.0);
        if (align <= 0.001) return tangent;

        Vec3 aimTarget = entityData.get(DATA_LOCKED) ? lockedAimPoint() : liveTargetPoint();
        Vec3 toAim = aimTarget.subtract(p0);
        if (toAim.lengthSqr() < 1e-6) return tangent;
        toAim = toAim.normalize();

        double smooth = align * align * (3.0 - 2.0 * align);
        Vec3 blended = tangent.scale(1.0 - smooth).add(toAim.scale(smooth));
        return blended.lengthSqr() > 1e-6 ? blended.normalize() : toAim;
    }

    @Override
    public void tick() {
        super.tick();
        float age = effectAge(0.0F);
        int lockT = waveLockTick();
        int relT = releaseTick();

        // Client-side: deterministic smooth orbit, then smooth linear rocket flight!
        if (level().isClientSide) {
            if (!isLaunched()) {
                Vec3 orb = orbitPosition(Math.min(age, relT));
                setDeltaMovement(orb.subtract(position()));
                setPos(orb.x, orb.y, orb.z);
            } else {
                Vec3 vel = syncedVelocity();
                setDeltaMovement(vel);
                setPos(getX() + vel.x, getY() + vel.y, getZ() + vel.z);
                if (tickCount % 2 == 0) {
                    level().addParticle(slot() % 2 == 0 ? ParticleTypes.CHERRY_LEAVES : ParticleTypes.END_ROD,
                        getX(), getY(), getZ(), -vel.x * 0.05, 0.01, -vel.z * 0.05);
                }
            }
            return;
        }

        ServerLevel sl = (ServerLevel) level();
        LivingEntity owner = resolveOwner(sl);
        if (owner == null || !owner.isAlive() || (owner instanceof DivineDragon d && d.isDissolving())) {
            discard();
            return;
        }

        // Right when THIS wave's lock tick arrives, lock onto the target's CURRENT live position!
        if ((int) age >= lockT && !entityData.get(DATA_LOCKED)) {
            Vec3 baseAim = resolveServerTargetPoint(sl);
            // Subtle sword-formation spread (+/-2.1 blocks around target) so a 12..16-sword wave forms a glorious constellation!
            double spreadAng = (slot() * Math.PI * 2.0) / waveSize();
            double spreadR = (slot() == 0) ? 0.0 : (1.1 + (slot() % 2) * 1.15);
            Vec3 locked = baseAim.add(Math.cos(spreadAng) * spreadR, (slot() % 3 - 1) * 0.35, Math.sin(spreadAng) * spreadR);
            setLockedAimPoint(locked);
            entityData.set(DATA_LOCKED, true);
            if (slot() == 0) {
                sl.playSound(null, owner.getX(), owner.getY() + 24.0, owner.getZ(),
                    SoundEvents.TRIDENT_RETURN, SoundSource.HOSTILE, 5.2F, 1.35F + wave() * 0.12F);
            }
        }

        // Keep revolving smoothly around the dragon until this blade's releaseTick!
        if ((int) age < relT) {
            Vec3 orb = orbitPosition(age);
            setDeltaMovement(Vec3.ZERO);
            setPos(orb.x, orb.y, orb.z);
            return;
        }

        if (!released) {
            released = true;
            Vec3 launchOrigin = orbitPosition(relT);
            setPos(launchOrigin.x, launchOrigin.y, launchOrigin.z);
            Vec3 dir = lockedAimPoint().subtract(launchOrigin);
            if (dir.lengthSqr() < 1e-6) dir = new Vec3(0, -0.35, 1);
            Vec3 vel = dir.normalize().scale(LAUNCH_SPEED);
            entityData.set(DATA_VEL_X, (float) vel.x);
            entityData.set(DATA_VEL_Y, (float) vel.y);
            entityData.set(DATA_VEL_Z, (float) vel.z);
            entityData.set(DATA_LAUNCH_TIME, level().getGameTime());
            setDeltaMovement(vel);
            if (slot() % 3 == 0) {
                sl.playSound(null, launchOrigin.x, launchOrigin.y, launchOrigin.z,
                    SoundEvents.TRIDENT_THROW, SoundSource.HOSTILE, 3.8F, 1.48F + wave() * 0.08F);
                sl.playSound(null, launchOrigin.x, launchOrigin.y, launchOrigin.z,
                    MyriadCalamity.DIVINE_DRAGON_WIND_SLASH.get(), SoundSource.HOSTILE, 2.2F, 1.32F);
            }
        }

        Vec3 vel = syncedVelocity();
        Vec3 prev = position();
        Vec3 next = prev.add(vel);
        setDeltaMovement(vel);
        setPos(next.x, next.y, next.z);

        if (checkSegmentHit(sl, owner, prev, next) || getY() < 127.0 || age >= relT + 56) {
            discard();
        }
    }

    private Vec3 resolveServerTargetPoint(ServerLevel sl) {
        if (targetUuid != null) {
            Entity t = sl.getEntity(targetUuid);
            if (t instanceof LivingEntity living && living.isAlive() && !living.isSpectator()) {
                return living.position().add(0, living.getBbHeight() * 0.55, 0);
            }
        }
        return lockedAimPoint();
    }

    private boolean checkSegmentHit(ServerLevel sl, LivingEntity owner, Vec3 prev, Vec3 next) {
        AABB sweep = new AABB(prev, next).inflate(1.25);
        for (LivingEntity target : sl.getEntitiesOfClass(LivingEntity.class, sweep)) {
            if (!target.isAlive() || target.isSpectator() || target instanceof DivineDragon) continue;
            if (ownerId != null && ownerId.equals(target.getUUID())) continue;
            if (target.getBoundingBox().inflate(0.70).clip(prev, next).isPresent()
                    || target.getBoundingBox().inflate(0.70).contains(next)) {
                boolean hurt = target.hurt(
                    owner != null ? damageSources().mobProjectile(this, owner) : damageSources().magic(),
                    damage);
                if (hurt) {
                    Vec3 dir = syncedVelocity().normalize();
                    target.push(dir.x * 0.32, 0.12, dir.z * 0.32);
                    target.hurtMarked = true;
                    sl.sendParticles(ParticleTypes.CHERRY_LEAVES, target.getX(), target.getY() + 1.0, target.getZ(),
                        6, 0.35, 0.35, 0.35, 0.08);
                }
                return true;
            }
        }
        return false;
    }

    private LivingEntity resolveOwner(ServerLevel level) {
        if (ownerId == null) return null;
        Entity e = level.getEntity(ownerId);
        return e instanceof LivingEntity living ? living : null;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(18.0);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 280.0 * 280.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
        if (tag.hasUUID("Target")) targetUuid = tag.getUUID("Target");
        damage = tag.getFloat("Damage");
        released = tag.getBoolean("Released");
        // Partial orbiting volleys expire cleanly across world reload (matching DivineFlyingSword)
        if (!released) discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        if (targetUuid != null) tag.putUUID("Target", targetUuid);
        tag.putFloat("Damage", damage);
        tag.putBoolean("Released", released);
    }

}
