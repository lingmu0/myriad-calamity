package net.xuwu.myriadcalamity.entity;

import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.xuwu.myriadcalamity.MyriadCalamity;

/**
 * Sky-summoned golden divine thunder strike hazard used by both Targeted Lightning Barrage
 * and Phase 2's Sky-Filling Thunder Cataclysm.
 * Always shows a clear golden ground telegraph before striking, with an enhanced double-ring
 * lock indicator when locked directly onto the target's position.
 */
public final class DivineThunderHazard extends Entity {
    private static final EntityDataAccessor<Integer> DATA_WINDUP = SynchedEntityData.defineId(DivineThunderHazard.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DURATION = SynchedEntityData.defineId(DivineThunderHazard.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(DivineThunderHazard.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_LOCKED = SynchedEntityData.defineId(DivineThunderHazard.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_AGE = SynchedEntityData.defineId(DivineThunderHazard.class, EntityDataSerializers.INT);

    private UUID ownerId;
    private float damage = 14.0F;
    private boolean struck;

    public DivineThunderHazard(EntityType<? extends DivineThunderHazard> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static DivineThunderHazard create(Level level, DivineDragon owner, double x, double y, double z,
                                             int windupTicks, float radius, float damage, boolean locked) {
        DivineThunderHazard hazard = new DivineThunderHazard(MyriadCalamity.DIVINE_THUNDER_HAZARD.get(), level);
        hazard.ownerId = owner.getUUID();
        hazard.damage = damage;
        hazard.setPos(x, y, z);
        hazard.entityData.set(DATA_WINDUP, windupTicks);
        hazard.entityData.set(DATA_DURATION, 14);
        hazard.entityData.set(DATA_RADIUS, radius);
        hazard.entityData.set(DATA_LOCKED, locked);
        hazard.entityData.set(DATA_AGE, 0);
        return hazard;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_WINDUP, 22);
        builder.define(DATA_DURATION, 14);
        builder.define(DATA_RADIUS, 3.2F);
        builder.define(DATA_LOCKED, false);
        builder.define(DATA_AGE, 0);
    }

    public int windup() { return entityData.get(DATA_WINDUP); }
    public int duration() { return entityData.get(DATA_DURATION); }
    public float radius() { return entityData.get(DATA_RADIUS); }
    public boolean isLocked() { return entityData.get(DATA_LOCKED); }
    public float age(float partial) { return entityData.get(DATA_AGE) + partial; }
    public boolean isWarning(float partial) { return age(partial) < windup(); }

    @Override
    public void tick() {
        super.tick();
        int w = windup();
        if (level().isClientSide()) {
            int age = entityData.get(DATA_AGE);
            if (age < w && age % 3 == 0) {
                double a = random.nextDouble() * Math.PI * 2.0;
                double r = random.nextDouble() * radius() * 0.9;
                level().addParticle(isLocked() ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD,
                    getX() + Math.cos(a) * r, getY() + 0.15, getZ() + Math.sin(a) * r,
                    0, 0.16, 0);
            }
            return;
        }
        int age = entityData.get(DATA_AGE) + 1;
        entityData.set(DATA_AGE, age);

        if (!struck && age >= w) {
            struck = true;
            triggerStrike();
        }
        if (age >= w + duration()) {
            discard();
        }
    }

    private void triggerStrike() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        float r = radius();
        if (isLocked() || random.nextInt(4) == 0) {
            serverLevel.playSound(null, getX(), getY(), getZ(),
                MyriadCalamity.DIVINE_DRAGON_LIGHTNING_STRIKE.get(), SoundSource.HOSTILE,
                isLocked() ? 2.8F : 1.35F,
                isLocked() ? 1.0F : 0.94F + random.nextFloat() * 0.14F);
        }
        if (isLocked()) {
            serverLevel.playSound(null, getX(), getY(), getZ(),
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 0.9F, 1.05F);
        }
        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.6, getZ(),
            16, r * 0.45, 0.8, r * 0.45, 0.18);

        LivingEntity owner = resolveOwner(serverLevel);
        AABB box = new AABB(getX() - r, getY() - 1.0, getZ() - r, getX() + r, getY() + 18.0, getZ() + r);
        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!target.isAlive() || target.isSpectator()) continue;
            if (ownerId != null && ownerId.equals(target.getUUID())) continue;
            if (target instanceof DivineDragon) continue;
            double dist = Math.hypot(target.getX() - getX(), target.getZ() - getZ());
            if (dist <= r + target.getBbWidth() * 0.45) {
                boolean hurt = target.hurtOrSimulate(
                    owner != null ? damageSources().indirectMagic(this, owner) : damageSources().lightningBolt(),
                    damage);
                if (hurt) {
                    target.push(0, 0.32, 0);
                    target.hurtMarked = true;
                }
            }
        }
    }

    private LivingEntity resolveOwner(ServerLevel level) {
        if (ownerId == null) return null;
        Entity e = level.getEntity(ownerId);
        return e instanceof LivingEntity living ? living : null;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    public AABB combatRenderBounds() {
        double r = radius() + 8.0;
        return new AABB(getX() - r, getY() - 2.0, getZ() - r, getX() + r, getY() + 104.0, getZ() + r);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 260.0 * 260.0;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput tag) {
        ownerId = tag.read("Owner", UUIDUtil.CODEC).orElse(null);
        damage = tag.getFloatOr("Damage", 0F);
        struck = tag.getBooleanOr("Struck", false);
        entityData.set(DATA_WINDUP, tag.getIntOr("Windup", 0));
        entityData.set(DATA_DURATION, tag.getIntOr("Duration", 0));
        entityData.set(DATA_RADIUS, tag.getFloatOr("Radius", 0F));
        entityData.set(DATA_LOCKED, tag.getBooleanOr("Locked", false));
        entityData.set(DATA_AGE, tag.getIntOr("Age", 0));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput tag) {
        if (ownerId != null) tag.store("Owner", UUIDUtil.CODEC, ownerId);
        tag.putFloat("Damage", damage);
        tag.putBoolean("Struck", struck);
        tag.putInt("Windup", windup());
        tag.putInt("Duration", duration());
        tag.putFloat("Radius", radius());
        tag.putBoolean("Locked", isLocked());
        tag.putInt("Age", entityData.get(DATA_AGE));
    }

}
