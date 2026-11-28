package net.xuwu.myriadcalamity.entity;

import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.MyriadCalamity;

/**
 * Towering 28-block wandering Cloud-Sea Tornado:
 *   - Violent core (r <= 3.5): wind-shear damage + knockback.
 *   - Benign outer updraft ring (3.5 < r <= 6.8): lifts challengers smoothly into the air with brief Slow Falling
 *     so they can soar over Shockwave Rings / Sweeps and snipe the Divine Dragon mid-air!
 */
public final class SakuraTornadoEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_MAX_AGE = SynchedEntityData.defineId(SakuraTornadoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_AGE = SynchedEntityData.defineId(SakuraTornadoEntity.class, EntityDataSerializers.INT);

    private UUID ownerId;
    private double orbitCenterX = 0.5, orbitCenterZ = 0.5;
    private double orbitRadius = 58.0;
    private double orbitAngle = 0.0;
    private double angularSpeed = 0.014;
    private float coreDamage = 7.0F;
    private final Map<UUID, Integer> hitCooldowns = new HashMap<>();

    public SakuraTornadoEntity(EntityType<? extends SakuraTornadoEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static SakuraTornadoEntity create(Level level, DivineDragon owner,
                                             double centerX, double centerZ, double floorY,
                                             double spawnX, double spawnZ, double orbitAngle,
                                             double angularSpeed, float coreDamage, int maxAge) {
        SakuraTornadoEntity t = new SakuraTornadoEntity(MyriadCalamity.SAKURA_TORNADO.get(), level);
        t.ownerId = owner.getUUID();
        t.orbitCenterX = centerX;
        t.orbitCenterZ = centerZ;
        t.orbitRadius = Math.hypot(spawnX - centerX, spawnZ - centerZ);
        t.orbitAngle = orbitAngle;
        t.angularSpeed = angularSpeed;
        t.coreDamage = coreDamage;
        t.setPos(spawnX, floorY, spawnZ);
        t.entityData.set(DATA_MAX_AGE, maxAge);
        t.entityData.set(DATA_AGE, 0);
        return t;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_MAX_AGE, 240);
        builder.define(DATA_AGE, 0);
    }

    public int maxAge() { return entityData.get(DATA_MAX_AGE); }
    public float age(float partial) { return entityData.get(DATA_AGE) + partial; }
    public float lifeProgress(float partial) { return Math.min(1.0F, age(partial) / Math.max(1, maxAge())); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            spawnClientParticles();
            return;
        }
        int age = entityData.get(DATA_AGE) + 1;
        entityData.set(DATA_AGE, age);

        // Gently wander along the arena combat ring (r = 44..78)
        orbitAngle += angularSpeed;
        double wobbleR = Math.max(44.0, Math.min(78.0, orbitRadius + Math.sin(age * 0.045) * 6.5));
        double nx = orbitCenterX + Math.cos(orbitAngle) * wobbleR;
        double nz = orbitCenterZ + Math.sin(orbitAngle) * wobbleR;
        setDeltaMovement(nx - getX(), 0.0, nz - getZ());
        setPos(nx, getY(), nz);

        if (age > 12 && age < maxAge() - 10) {
            applyWindEffects(age);
        }
        if (age >= maxAge()) discard();
    }

    private void spawnClientParticles() {
        for (int i = 0; i < 4; i++) {
            double h = random.nextDouble() * DivineDragonMath.TORNADO_HEIGHT;
            double frac = h / DivineDragonMath.TORNADO_HEIGHT;
            double r = 2.0 + frac * 3.8;
            double a = (tickCount * 0.30) + i * 1.57 + frac * 5.0;
            level().addParticle(i == 0 ? ParticleTypes.END_ROD : ParticleTypes.CLOUD,
                getX() + Math.cos(a) * r, getY() + h, getZ() + Math.sin(a) * r,
                -Math.sin(a) * 0.24, 0.16, Math.cos(a) * 0.24);
        }
    }

    private void applyWindEffects(int age) {
        if (!(level() instanceof ServerLevel sl)) return;
        double rScan = DivineDragonMath.TORNADO_UPDRAFT_RADIUS + 1.5;
        AABB zone = new AABB(getX() - rScan, getY() - 1.5, getZ() - rScan,
            getX() + rScan, getY() + DivineDragonMath.TORNADO_HEIGHT + 2.0, getZ() + rScan);

        LivingEntity owner = resolveOwner(sl);
        for (LivingEntity target : sl.getEntitiesOfClass(LivingEntity.class, zone)) {
            if (!target.isAlive() || target.isSpectator() || target instanceof DivineDragon) continue;
            if (ownerId != null && ownerId.equals(target.getUUID())) continue;

            DivineDragonMath.TornadoEffect eff = DivineDragonMath.tornadoEffect(
                getX(), getZ(), getY(), target.getX(), target.getY(), target.getZ());
            if (eff.inCore()) {
                int nextAllowed = hitCooldowns.getOrDefault(target.getUUID(), 0);
                if (age >= nextAllowed) {
                    hitCooldowns.put(target.getUUID(), age + 12);
                    target.hurtOrSimulate(owner != null ? damageSources().mobAttack(owner) : damageSources().magic(), coreDamage);
                    target.push(eff.outX() * 0.95, 0.48, eff.outZ() * 0.95);
                    target.hurtMarked = true;
                }
            } else if (eff.inUpdraft()) {
                // Benign outer updraft lifts the player smoothly up to +11.5 blocks (NO Slow Falling potion effect!)
                Vec3 vel = target.getDeltaMovement();
                double maxAirY = getY() + 11.5;
                double vy = target.getY() < maxAirY ? Math.max(vel.y, 0.56) : Math.max(vel.y, 0.10);
                target.setDeltaMovement(vel.x, vy, vel.z);
                target.fallDistance = 0.0F;
                target.hurtMarked = true;
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
        return new AABB(getX() - 9.0, getY() - 2.0, getZ() - 9.0,
            getX() + 9.0, getY() + DivineDragonMath.TORNADO_HEIGHT + 4.0, getZ() + 9.0);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 260.0 * 260.0;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput tag) {
        ownerId = tag.read("Owner", UUIDUtil.CODEC).orElse(null);
        orbitCenterX = tag.getDoubleOr("OrbitX", 0.0);
        orbitCenterZ = tag.getDoubleOr("OrbitZ", 0.0);
        orbitRadius = tag.getDoubleOr("OrbitR", 0.0);
        orbitAngle = tag.getDoubleOr("OrbitAng", 0.0);
        angularSpeed = tag.getDoubleOr("AngSpd", 0.0);
        coreDamage = tag.getFloatOr("Damage", 0F);
        entityData.set(DATA_MAX_AGE, tag.getIntOr("MaxAge", 0));
        entityData.set(DATA_AGE, tag.getIntOr("Age", 0));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput tag) {
        if (ownerId != null) tag.store("Owner", UUIDUtil.CODEC, ownerId);
        tag.putDouble("OrbitX", orbitCenterX);
        tag.putDouble("OrbitZ", orbitCenterZ);
        tag.putDouble("OrbitR", orbitRadius);
        tag.putDouble("OrbitAng", orbitAngle);
        tag.putDouble("AngSpd", angularSpeed);
        tag.putFloat("Damage", coreDamage);
        tag.putInt("MaxAge", maxAge());
        tag.putInt("Age", entityData.get(DATA_AGE));
    }

}
