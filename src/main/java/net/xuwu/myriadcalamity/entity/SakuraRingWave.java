package net.xuwu.myriadcalamity.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import net.xuwu.myriadcalamity.MyriadCalamity;

/**
 * 360-degree expanding emerald sakura shockwave ring unleashed by the Divine Dragon's Shockwave Gale Ring.
 * Cannot be sidestepped radially — jump over it or ride a Cloud-Sea Tornado's outer updraft!
 */
public final class SakuraRingWave extends Entity {
    private static final EntityDataAccessor<Float> DATA_START_RADIUS = SynchedEntityData.defineId(SakuraRingWave.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(SakuraRingWave.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT = SynchedEntityData.defineId(SakuraRingWave.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_MAX_AGE = SynchedEntityData.defineId(SakuraRingWave.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_TIME = SynchedEntityData.defineId(SakuraRingWave.class, EntityDataSerializers.LONG);

    private UUID ownerId;
    private float damage = 10.5F;
    private int ageTicks;
    private final Set<UUID> hitEntities = new HashSet<>();

    public SakuraRingWave(EntityType<? extends SakuraRingWave> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static SakuraRingWave create(Level level, DivineDragon owner, double cx, double floorY, double cz,
                                        float startRadius, float speedPerTick, float height, float damage, int maxAge) {
        SakuraRingWave wave = new SakuraRingWave(MyriadCalamity.SAKURA_RING_WAVE.get(), level);
        wave.ownerId = owner.getUUID();
        wave.damage = damage;
        wave.setPos(cx, floorY, cz);
        wave.entityData.set(DATA_START_RADIUS, startRadius);
        wave.entityData.set(DATA_SPEED, speedPerTick);
        wave.entityData.set(DATA_HEIGHT, height);
        wave.entityData.set(DATA_MAX_AGE, maxAge);
        wave.entityData.set(DATA_START_TIME, level.getGameTime());
        return wave;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_START_RADIUS, 22.0F);
        entityData.define(DATA_SPEED, 1.55F);
        entityData.define(DATA_HEIGHT, DivineDragonMath.JUMPABLE_RING_HEIGHT);
        entityData.define(DATA_MAX_AGE, 46);
        entityData.define(DATA_START_TIME, 0L);
    }

    public float age(float partial) {
        long start = entityData.get(DATA_START_TIME);
        if (start <= 0L) return ageTicks + partial;
        return Math.max(0.0F, (level().getGameTime() - start) + partial);
    }

    public float currentRadius(float partial) {
        return entityData.get(DATA_START_RADIUS) + age(partial) * entityData.get(DATA_SPEED);
    }

    public float ringHeight() { return entityData.get(DATA_HEIGHT); }
    public int maxAge() { return entityData.get(DATA_MAX_AGE); }
    public float lifeProgress(float partial) { return Math.min(1.0F, age(partial) / Math.max(1, maxAge())); }

    @Override
    public void tick() {
        super.tick();
        ageTicks++;
        if (level().isClientSide) {
            float r = currentRadius(0.0F);
            for (int i = 0; i < 10; i++) {
                double a = random.nextDouble() * Math.PI * 2.0;
                level().addParticle(i % 2 == 0 ? ParticleTypes.CHERRY_LEAVES : ParticleTypes.GLOW,
                    getX() + Math.cos(a) * r, getY() + 0.3 + random.nextDouble() * ringHeight(),
                    getZ() + Math.sin(a) * r, Math.cos(a) * 0.14, 0.05, Math.sin(a) * 0.14);
            }
            return;
        }
        checkHits();
        if (ageTicks >= maxAge()) discard();
    }

    private void checkHits() {
        if (!(level() instanceof ServerLevel sl)) return;
        double r = currentRadius(0.0F);
        double halfThick = Math.max(1.25, entityData.get(DATA_SPEED) * 0.85);
        double reach = r + halfThick + 3.0;
        AABB search = new AABB(getX() - reach, getY() - 1.5, getZ() - reach,
            getX() + reach, getY() + ringHeight() + 3.0, getZ() + reach);

        LivingEntity owner = resolveOwner(sl);
        for (LivingEntity target : sl.getEntitiesOfClass(LivingEntity.class, search)) {
            if (!target.isAlive() || target.isSpectator() || target instanceof DivineDragon) continue;
            if (ownerId != null && ownerId.equals(target.getUUID())) continue;
            if (hitEntities.contains(target.getUUID())) continue;

            if (DivineDragonMath.ringWaveHits(
                    getX(), getZ(), getY(), r, halfThick, ringHeight(),
                    target.getX(), target.getY(), target.getZ(),
                    target.getBbWidth() * 0.5, target.getBbHeight())) {
                hitEntities.add(target.getUUID());
                boolean hurt = target.hurt(
                    owner != null ? damageSources().mobAttack(owner) : damageSources().magic(),
                    damage);
                if (hurt) {
                    Vec3 away = new Vec3(target.getX() - getX(), 0, target.getZ() - getZ());
                    Vec3 n = away.lengthSqr() > 1e-4 ? away.normalize() : new Vec3(0, 0, 1);
                    target.push(n.x * 0.72, 0.34, n.z * 0.72);
                    target.hurtMarked = true;
                    sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                        MyriadCalamity.DIVINE_DRAGON_WIND_SLASH.get(), SoundSource.HOSTILE, 1.3F, 1.08F);
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
    public AABB getBoundingBoxForCulling() {
        double r = currentRadius(0.0F) + 6.0;
        return new AABB(getX() - r, getY() - 2.0, getZ() - r, getX() + r, getY() + 8.0, getZ() + r);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 260.0 * 260.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
        damage = tag.getFloat("Damage");
        ageTicks = tag.getInt("Age");
        entityData.set(DATA_START_RADIUS, tag.getFloat("StartRadius"));
        entityData.set(DATA_SPEED, tag.getFloat("Speed"));
        entityData.set(DATA_HEIGHT, tag.getFloat("Height"));
        entityData.set(DATA_MAX_AGE, tag.getInt("MaxAge"));
        entityData.set(DATA_START_TIME, level().getGameTime() - ageTicks);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putFloat("Damage", damage);
        tag.putInt("Age", ageTicks);
        tag.putFloat("StartRadius", entityData.get(DATA_START_RADIUS));
        tag.putFloat("Speed", entityData.get(DATA_SPEED));
        tag.putFloat("Height", ringHeight());
        tag.putInt("MaxAge", maxAge());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
