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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import net.xuwu.myriadcalamity.MyriadCalamity;

/**
 * Luminous jade crescent wind-blade unleashed by the Divine Dragon's Seven-Branched Sword.
 * Modes:
 *   0 = Low horizontal ground sweep (jump over it)
 *   1 = Towering vertical cleave (sprint sideways to dodge)
 *   2 = Angled fan crescent blade (bullet-hell weave)
 */
public final class SakuraWindBlade extends Entity {
    public static final int MODE_SWEEP = 0;
    public static final int MODE_VERTICAL = 1;
    public static final int MODE_FAN = 2;
    public static final int MODE_CROSS = 3;
    public static final int MODE_HIGH_ARC = 4;

    private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(SakuraWindBlade.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_WIDTH = SynchedEntityData.defineId(SakuraWindBlade.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT = SynchedEntityData.defineId(SakuraWindBlade.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TILT = SynchedEntityData.defineId(SakuraWindBlade.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_MAX_AGE = SynchedEntityData.defineId(SakuraWindBlade.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_START_Y = SynchedEntityData.defineId(SakuraWindBlade.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TARGET_Y = SynchedEntityData.defineId(SakuraWindBlade.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_GLIDE_TICKS = SynchedEntityData.defineId(SakuraWindBlade.class, EntityDataSerializers.INT);

    private UUID ownerId;
    private float damage = 12.0F;
    private int ageTicks;
    private final Set<UUID> hitEntities = new HashSet<>();

    public SakuraWindBlade(EntityType<? extends SakuraWindBlade> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static SakuraWindBlade create(Level level, DivineDragon owner, int mode, Vec3 pos, Vec3 velocity,
                                         float width, float height, float tiltDeg, float damage, int maxAge) {
        return create(level, owner, mode, pos, velocity, width, height, tiltDeg, damage, maxAge, pos.y, 0);
    }

    public static SakuraWindBlade create(Level level, DivineDragon owner, int mode, Vec3 pos, Vec3 velocity,
                                         float width, float height, float tiltDeg, float damage, int maxAge,
                                         double targetY, int glideTicks) {
        SakuraWindBlade blade = new SakuraWindBlade(MyriadCalamity.SAKURA_WIND_BLADE.get(), level);
        blade.ownerId = owner.getUUID();
        blade.damage = damage;
        blade.setPos(pos.x, pos.y, pos.z);
        blade.setDeltaMovement(velocity);
        blade.entityData.set(DATA_MODE, mode);
        blade.entityData.set(DATA_WIDTH, width);
        blade.entityData.set(DATA_HEIGHT, height);
        blade.entityData.set(DATA_TILT, tiltDeg);
        blade.entityData.set(DATA_MAX_AGE, maxAge);
        blade.entityData.set(DATA_START_Y, (float) pos.y);
        blade.entityData.set(DATA_TARGET_Y, (float) targetY);
        blade.entityData.set(DATA_GLIDE_TICKS, Math.max(0, glideTicks));
        float yaw = (float) (Math.atan2(-velocity.x, velocity.z) * (180.0 / Math.PI));
        blade.setYRot(yaw);
        blade.yRotO = yaw;
        return blade;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_MODE, MODE_SWEEP);
        entityData.define(DATA_WIDTH, 32.0F);
        entityData.define(DATA_HEIGHT, DivineDragonMath.JUMPABLE_SWEEP_HEIGHT);
        entityData.define(DATA_TILT, 0.0F);
        entityData.define(DATA_MAX_AGE, 58);
        entityData.define(DATA_START_Y, 130.0F);
        entityData.define(DATA_TARGET_Y, 130.0F);
        entityData.define(DATA_GLIDE_TICKS, 0);
    }

    public int mode() { return entityData.get(DATA_MODE); }
    public float bladeWidth() { return entityData.get(DATA_WIDTH); }
    public float bladeHeight() { return entityData.get(DATA_HEIGHT); }
    public float tiltDegrees() { return entityData.get(DATA_TILT); }
    public int maxAge() { return entityData.get(DATA_MAX_AGE); }
    public float lifeProgress(float partial) { return Math.min(1.0F, (ageTicks + partial) / Math.max(1, maxAge())); }

    @Override
    public void tick() {
        super.tick();
        ageTicks++;
        Vec3 vel = getDeltaMovement();
        int glide = entityData.get(DATA_GLIDE_TICKS);
        double nextY;
        if (glide > 0 && ageTicks <= glide) {
            float u = (float) ageTicks / glide;
            float ease = 1.0F - (1.0F - u) * (1.0F - u);
            nextY = Mth.lerp(ease, entityData.get(DATA_START_Y), entityData.get(DATA_TARGET_Y));
        } else {
            nextY = getY() + vel.y;
        }
        setPos(getX() + vel.x, nextY, getZ() + vel.z);

        if (level().isClientSide) {
            spawnTrailParticles(vel);
        } else {
            checkHits(vel);
            if (ageTicks >= maxAge()) discard();
        }
    }

    private void spawnTrailParticles(Vec3 vel) {
        double len = Math.hypot(vel.x, vel.z);
        if (len < 1e-4) return;
        double rx = -vel.z / len, rz = vel.x / len;
        float w = bladeWidth();
        float h = bladeHeight();
        int count = mode() == MODE_SWEEP ? 7 : (mode() == MODE_VERTICAL ? 6 : 3);
        for (int i = 0; i < count; i++) {
            double lat = (random.nextDouble() - 0.5) * w * 0.9;
            double vert = (random.nextDouble() - 0.5) * h * 0.85;
            double px = getX() + rx * lat;
            double py = getY() + vert;
            double pz = getZ() + rz * lat;
            level().addParticle(i % 3 == 0 ? ParticleTypes.CHERRY_LEAVES : ParticleTypes.GLOW,
                px, py, pz, -vel.x * 0.08, 0.03 + random.nextDouble() * 0.05, -vel.z * 0.08);
        }
    }

    private void checkHits(Vec3 vel) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        double halfW = bladeWidth() * 0.5;
        double halfH = bladeHeight() * 0.5;
        double halfD = Math.max(1.35, Math.hypot(vel.x, vel.z) * 0.72);
        double reach = Math.max(halfW, halfH) + 3.0;
        AABB search = new AABB(getX() - reach, getY() - halfH - 2.0, getZ() - reach,
            getX() + reach, getY() + halfH + 2.0, getZ() + reach);

        LivingEntity owner = resolveOwner(serverLevel);
        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, search)) {
            if (!target.isAlive() || target.isSpectator()) continue;
            if (ownerId != null && ownerId.equals(target.getUUID())) continue;
            if (target instanceof DivineDragon) continue;
            if (hitEntities.contains(target.getUUID())) continue;

            boolean hit = mode() == MODE_CROSS
                ? DivineDragonMath.tiltedWindBladeHits(
                    getX(), getY(), getZ(), vel.x, vel.z,
                    halfW, halfH, halfD, tiltDegrees(),
                    target.getX(), target.getY(), target.getZ(),
                    target.getBbWidth() * 0.5, target.getBbHeight())
                : DivineDragonMath.windBladeHits(
                    getX(), getY(), getZ(), vel.x, vel.z,
                    halfW, halfH, halfD,
                    target.getX(), target.getY(), target.getZ(),
                    target.getBbWidth() * 0.5, target.getBbHeight());
            if (hit) {
                hitEntities.add(target.getUUID());
                boolean hurt = target.hurt(
                    owner != null ? damageSources().mobAttack(owner) : damageSources().magic(),
                    damage);
                if (hurt) {
                    Vec3 dir = new Vec3(vel.x, 0, vel.z).normalize();
                    target.push(dir.x * 0.65, 0.28, dir.z * 0.65);
                    target.hurtMarked = true;
                    serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(),
                        MyriadCalamity.DIVINE_DRAGON_WIND_SLASH.get(), SoundSource.HOSTILE, 1.4F, 1.05F);
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
        double r = Math.max(bladeWidth(), bladeHeight()) * 0.65 + 4.0;
        return new AABB(getX() - r, getY() - r, getZ() - r, getX() + r, getY() + r, getZ() + r);
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
        entityData.set(DATA_MODE, tag.getInt("Mode"));
        entityData.set(DATA_WIDTH, tag.getFloat("Width"));
        entityData.set(DATA_HEIGHT, tag.getFloat("Height"));
        entityData.set(DATA_TILT, tag.getFloat("Tilt"));
        entityData.set(DATA_MAX_AGE, tag.getInt("MaxAge"));
        entityData.set(DATA_START_Y, tag.contains("StartY") ? tag.getFloat("StartY") : (float) getY());
        entityData.set(DATA_TARGET_Y, tag.contains("TargetY") ? tag.getFloat("TargetY") : (float) getY());
        entityData.set(DATA_GLIDE_TICKS, tag.getInt("GlideTicks"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putFloat("Damage", damage);
        tag.putInt("Age", ageTicks);
        tag.putInt("Mode", mode());
        tag.putFloat("Width", bladeWidth());
        tag.putFloat("Height", bladeHeight());
        tag.putFloat("Tilt", tiltDegrees());
        tag.putInt("MaxAge", maxAge());
        tag.putFloat("StartY", entityData.get(DATA_START_Y));
        tag.putFloat("TargetY", entityData.get(DATA_TARGET_Y));
        tag.putInt("GlideTicks", entityData.get(DATA_GLIDE_TICKS));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
