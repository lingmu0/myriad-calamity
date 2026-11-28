package net.xuwu.myriadcalamity.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.config.MyriadConfig;
import net.xuwu.myriadcalamity.world.CloudWeather;

/**
 * Sekiro Divine Dragon colossal ranged-duel boss anchored atop the real-block Giant Sakura Mound (2.25x Sekiro 1:1 scale).
 * Implements the full 19-state/skill Finite State Machine:
 *   - Phase 1 (Wind of the Everblossom): Sweeps, Vertical Cleaves, Fan Blades, Cross-Gale X-Slash,
 *     360-deg Shockwave Gale Rings, Delayed Sakura Feather Barrage, Sweeping Gale Breath, Sky Lightning,
 *     and Sky-Bite Thunder Throw (unavoidable-once-caught grab/throw combo).
 *   - Phase 2 (Tempest of Heaven, <= 50% HP): Combo Chains, Twin-Moon Low/High Boomerang Slash,
 *     Wandering Cloud-Sea Tornadoes (core hazard + benign outer updraft ring), Sky-Filling Thunder Cataclysm,
 *     higher-cadence Sky-Bite Grab Throws, and a 20% HP Finale (Seven-Branch Heaven's Judgment).
 *   - Counterplay Loop: 5 O(1) animation-synced DragonPart hitboxes (head/neck/chest/mid_body/lower_body);
 *     head weakpoint damage (1.35x) accumulates Stagger to trigger a 4.5s Stagger Collapse (1.6x).
 *   - Solemn 6-second neck-droop and sakura-ash dissolve farewell at 0 HP (no execution).
 */
public final class DivineDragon extends Monster {
    public static final int IDLE = 0;
    public static final int INTRO_AWAKEN = 1;
    public static final int ROAR_PUSH = 2;
    public static final int SLASH_SWEEP = 3;
    public static final int SLASH_VERTICAL = 4;
    public static final int SLASH_FAN = 5;
    public static final int SLASH_COMBO = 6;
    public static final int TEMPEST_BARRAGE = 7;
    public static final int SUMMON_LIGHTNING = 8;
    public static final int THUNDER_CATACLYSM = 9;
    public static final int DEATH_DISSOLVE = 10;
    public static final int SLASH_SLAM = 11;
    public static final int SLASH_WIDE_ARC = 12;
    public static final int CROSS_GALE_CLEAVE = 13;
    public static final int SHOCKWAVE_RING = 14;
    public static final int FEATHER_BARRAGE = 15;
    public static final int SWEEPING_GALE_BREATH = 16;
    public static final int CLOUD_SEA_TORNADO = 17;
    public static final int SKY_BITE_THROW = 18;
    public static final int STAGGER_COLLAPSE = 19;
    public static final int TWIN_MOON_SLASH = 20;
    public static final int FINALE_JUDGMENT = 21;
    private static final int ACTION_COUNT = 22;

    // Grab sub-states synced in DATA_GRAB_INFO
    public static final int GRAB_NONE = 0;
    public static final int GRAB_TELEGRAPH = 1;
    public static final int GRAB_CLENCH = 2;
    public static final int GRAB_AIR_THUNDER = 3;
    public static final int GRAB_WHIFF = 4;

    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(DivineDragon.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_ACTION_START = SynchedEntityData.defineId(DivineDragon.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(DivineDragon.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_DISSOLVING = SynchedEntityData.defineId(DivineDragon.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> DATA_DISSOLVE_START = SynchedEntityData.defineId(DivineDragon.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> DATA_TARGET_YAW = SynchedEntityData.defineId(DivineDragon.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BREATH_YAW = SynchedEntityData.defineId(DivineDragon.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BREATH_RADIUS = SynchedEntityData.defineId(DivineDragon.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<CompoundTag> DATA_GRAB_INFO = SynchedEntityData.defineId(DivineDragon.class, EntityDataSerializers.COMPOUND_TAG);

    private final ServerBossEvent bossBar = new ServerBossEvent(
        Component.translatable("boss.myriad_calamity.divine_dragon_p1"),
        BossEvent.BossBarColor.WHITE,
        BossEvent.BossBarOverlay.NOTCHED_10);

    private final DragonPart headPart;
    private final DragonPart neckPart;
    private final DragonPart chestPart;
    private final DragonPart midBodyPart;
    private final DragonPart lowerPart;
    private final DragonPart[] parts;
    private final double[] anchorBuf = new double[3];

    private BlockPos arenaCenter;
    private UUID arenaOwner;
    private boolean arenaBoss;
    private int prepareTicks;
    private int actionTick;
    private int dissolveTick;
    private boolean startTimesSynced = true;
    private boolean clientYawInit;
    private float clientYaw;
    private float clientPrevYaw;
    private int cooldownTicks = 25;
    private int cataclysmCooldown = 160;
    private final int[] skillCooldowns = new int[ACTION_COUNT];
    private int lastAction = IDLE;
    private int secondLastAction = IDLE;
    private int consecutiveBladeCount;
    private int tornadoBoostTicks;
    private boolean phase2Triggered;
    private boolean finaleTriggered;
    private boolean dissolveComplete;
    private float staggerDamage;
    private int staggerCooldown;
    private Vec3 anchorPos;
    private final Random strikeRng = new Random();

    // Active grab/throw state (server-owned)
    private UUID grabbedTargetId;
    private float grabCorridorYaw;
    private double grabLockX, grabLockZ;
    private double grabJawStartX, grabJawStartY, grabJawStartZ;
    private double grabAirX, grabAirY, grabAirZ;
    private final Map<UUID, Integer> breathHitCooldown = new HashMap<>();

    public DivineDragon(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 250;
        this.setNoGravity(true);
        this.noCulling = true;
        // 5 animation-synced multi-part hitboxes locked 1:1 to baked forward kinematics (HEAD..LOWER_BODY)
        this.headPart = new DragonPart(this, "head", 11.0F, 9.5F, 1.35F);
        this.neckPart = new DragonPart(this, "neck", 9.5F, 11.0F, 1.10F);
        this.chestPart = new DragonPart(this, "chest", 13.5F, 15.0F, 1.0F);
        this.midBodyPart = new DragonPart(this, "mid_body", 13.5F, 14.0F, 0.95F);
        this.lowerPart = new DragonPart(this, "lower_body", 14.0F, 13.0F, 0.90F);
        this.parts = new DragonPart[]{headPart, neckPart, chestPart, midBodyPart, lowerPart};
        setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (parts != null) {
            for (int i = 0; i < parts.length; i++) parts[i].setId(id + i + 1);
        }
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, MyriadConfig.DEFAULT_DIVINE_DRAGON_HEALTH)
            .add(Attributes.ATTACK_DAMAGE, MyriadConfig.DEFAULT_DIVINE_DRAGON_DAMAGE)
            .add(Attributes.ARMOR, 8.0D)
            .add(Attributes.ARMOR_TOUGHNESS, 6.0D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
            .add(Attributes.FOLLOW_RANGE, 160.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ACTION, IDLE);
        entityData.define(DATA_ACTION_START, 0L);
        entityData.define(DATA_PHASE, 1);
        entityData.define(DATA_DISSOLVING, false);
        entityData.define(DATA_DISSOLVE_START, 0L);
        entityData.define(DATA_TARGET_YAW, 0.0F);
        entityData.define(DATA_BREATH_YAW, 0.0F);
        entityData.define(DATA_BREATH_RADIUS, 60.0F);
        entityData.define(DATA_GRAB_INFO, new CompoundTag());
    }

    public int action() { return entityData.get(DATA_ACTION); }
    public long actionStartTick() { return entityData.get(DATA_ACTION_START); }
    public float actionAge(float partial) {
        return Math.max(0.0F, (level().getGameTime() - entityData.get(DATA_ACTION_START)) + partial);
    }
    public int phase() { return entityData.get(DATA_PHASE); }
    public boolean isDissolving() { return entityData.get(DATA_DISSOLVING); }
    public float dissolveAge(float partial) {
        if (!isDissolving()) return 0.0F;
        return Math.max(0.0F, (level().getGameTime() - entityData.get(DATA_DISSOLVE_START)) + partial);
    }
    public boolean isDissolveComplete() { return dissolveComplete; }
    public boolean isArenaBoss() { return arenaBoss; }
    public boolean isIntroActive() { return action() == INTRO_AWAKEN && !isDissolving(); }
    public boolean isIntroConcealed() { return isIntroConcealed(0.0F); }
    public boolean isIntroConcealed(float partial) {
        if (!isIntroActive()) return false;
        float age = level().isClientSide ? actionAge(partial) : actionTick;
        return DivineDragonMath.introConcealed(age);
    }

    public static String clipNameForAction(int action) {
        return switch (action) {
            case INTRO_AWAKEN -> "intro_awaken";
            case ROAR_PUSH -> "roar_push";
            case SLASH_SWEEP -> "slash_sweep";
            case SLASH_VERTICAL -> "slash_vertical";
            case SLASH_FAN -> "slash_fan";
            case SLASH_SLAM -> "slash_slam";
            case SLASH_COMBO -> "slash_combo";
            case SLASH_WIDE_ARC -> "slash_wide_arc";
            case TEMPEST_BARRAGE -> "tempest_barrage";
            case SUMMON_LIGHTNING -> "summon_lightning";
            case THUNDER_CATACLYSM -> "thunder_cataclysm";
            case DEATH_DISSOLVE -> "death_dissolve";
            case CROSS_GALE_CLEAVE -> "cross_gale_cleave";
            case SHOCKWAVE_RING -> "shockwave_ring";
            case FEATHER_BARRAGE -> "feather_barrage";
            case SWEEPING_GALE_BREATH -> "sweeping_gale_breath";
            case CLOUD_SEA_TORNADO -> "cloud_sea_tornado";
            case SKY_BITE_THROW -> "sky_bite_throw";
            case STAGGER_COLLAPSE -> "stagger_collapse";
            case TWIN_MOON_SLASH -> "twin_moon_slash";
            case FINALE_JUDGMENT -> "finale_judgment";
            default -> "idle";
        };
    }

    public static float animationTimeSeconds(int action, int grabState, float actAge, float totalAge,
                                             boolean dissolving, float dissolveAge) {
        if (dissolving) {
            return Math.max(0.0F, dissolveAge) / 20.0F;
        }
        float clampedAct = Math.max(0.0F, actAge);
        if (action == IDLE) {
            // Start Idle cleanly from frame 0 upon returning from any action so blending into neutral rest pose is silky!
            return clampedAct / 20.0F;
        }
        if (action == INTRO_AWAKEN) {
            // Hold pose 0 while concealed in the storm, then play the skyward roar right as the white flash reveals the boss!
            return DivineDragonMath.introRoarAnimSeconds(clampedAct);
        }
        if (action == SKY_BITE_THROW && grabState == GRAB_WHIFF && clampedAct > 42.0F) {
            float whiffProgress = Mth.clamp((clampedAct - 42.0F) / 48.0F, 0.0F, 1.0F);
            return whiffProgress < 0.55F
                ? 2.15F + whiffProgress * 0.25F
                : Mth.lerp((whiffProgress - 0.55F) / 0.45F, 2.29F, 6.25F);
        }
        return clampedAct / 20.0F;
    }

    public boolean isSwordSlashAction(int act) {
        return act == SLASH_SWEEP || act == SLASH_VERTICAL || act == SLASH_FAN
            || act == SLASH_SLAM || act == SLASH_COMBO || act == SLASH_WIDE_ARC
            || act == TEMPEST_BARRAGE || act == CROSS_GALE_CLEAVE
            || act == SHOCKWAVE_RING || act == TWIN_MOON_SLASH || act == FINALE_JUDGMENT;
    }

    public int grabState() { return entityData.get(DATA_GRAB_INFO).getInt("s"); }
    public float grabCorridorYawDeg() { return entityData.get(DATA_GRAB_INFO).getFloat("gy"); }
    public double grabLockX() { return entityData.get(DATA_GRAB_INFO).getDouble("lx"); }
    public double grabLockZ() { return entityData.get(DATA_GRAB_INFO).getDouble("lz"); }
    public double grabAirX() { return entityData.get(DATA_GRAB_INFO).getDouble("ax"); }
    public double grabAirY() { return entityData.get(DATA_GRAB_INFO).getDouble("ay"); }
    public double grabAirZ() { return entityData.get(DATA_GRAB_INFO).getDouble("az"); }
    public double grabLockDistance() {
        CompoundTag tag = entityData.get(DATA_GRAB_INFO);
        if (!tag.contains("lx")) return 48.0;
        return Math.hypot(tag.getDouble("lx") - getX(), tag.getDouble("lz") - getZ());
    }
    public double galeBreathYawDeg(float actAge) {
        return entityData.get(DATA_BREATH_YAW) + DivineDragonMath.galeBreathSweepOffsetDeg(actAge);
    }
    public float breathTargetRadius() {
        return entityData.get(DATA_BREATH_RADIUS);
    }
    public Vec3 headWorldOffset() {
        return headPart.getBoundingBox().getCenter().subtract(position());
    }

    private void syncGrabInfo(int state) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("s", state);
        tag.putFloat("gy", grabCorridorYaw);
        tag.putDouble("lx", grabLockX);
        tag.putDouble("lz", grabLockZ);
        tag.putDouble("ax", grabAirX);
        tag.putDouble("ay", grabAirY);
        tag.putDouble("az", grabAirZ);
        entityData.set(DATA_GRAB_INFO, tag);
    }

    public CloudWeather weather() {
        if (isDissolving()) return CloudWeather.CLEAR;
        if (isIntroActive()) return CloudWeather.phase(3);
        return phase() >= 2 ? CloudWeather.phase(3) : CloudWeather.phase(2);
    }

    public void setArena(BlockPos center, UUID owner) {
        this.arenaCenter = center.immutable();
        this.arenaOwner = owner;
        this.arenaBoss = true;
        applyConfiguredHealth();
    }

    public void prepareForArena(int ticks) {
        this.prepareTicks = Math.max(DivineDragonMath.INTRO_TOTAL_DURATION, ticks);
        // Boss begins concealed in the swirling storm clouds; after mega-lightning + white flash it roars skyward, then fights immediately!
        entityData.set(DATA_ACTION, INTRO_AWAKEN);
        entityData.set(DATA_ACTION_START, level().getGameTime());
        entityData.set(DATA_TARGET_YAW, 0.0F);
        setYRot(0.0F);
        yBodyRot = 0.0F;
        yHeadRot = 0.0F;
        this.actionTick = 0;
        bossBar.setVisible(false);
    }

    private void applyConfiguredHealth() {
        double maxHp = MyriadConfig.divineDragonHealth();
        var attr = getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(maxHp);
            setHealth((float) maxHp);
        }
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    @Override
    public boolean isPickable() {
        // Arrows & raycasts resolve cleanly against the 5 animation-synced DragonPart hitboxes (no ghost root box).
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(getX() - 110.0, getY() - 45.0, getZ() - 110.0,
            getX() + 110.0, getY() + 135.0, getZ() + 110.0);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 320.0 * 320.0;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        releaseGrabbedTarget(true);
        super.remove(reason);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (anchorPos == null) anchorPos = position();
        setDeltaMovement(Vec3.ZERO);
        setPos(anchorPos.x, anchorPos.y, anchorPos.z);

        if (level().isClientSide) {
            // Smooth continuous float yaw isolated from Minecraft's 1.4-deg byte-quantized yaw packets
            float targetYaw = entityData.get(DATA_TARGET_YAW);
            if (!clientYawInit) {
                clientYawInit = true;
                clientYaw = targetYaw;
                clientPrevYaw = targetYaw;
            } else {
                clientPrevYaw = clientYaw;
                clientYaw = Mth.rotLerp(0.32F, clientYaw, targetYaw);
            }
            yBodyRotO = clientPrevYaw;
            yBodyRot = clientYaw;
            yHeadRotO = clientPrevYaw;
            yHeadRot = clientYaw;
            yRotO = clientPrevYaw;
            setYRot(clientYaw);
            updatePartPositions();
            tickClientParticles();
            return;
        }

        if (!startTimesSynced) {
            startTimesSynced = true;
            long now = level().getGameTime();
            entityData.set(DATA_ACTION_START, now - actionTick);
            entityData.set(DATA_DISSOLVE_START, now - dissolveTick);
        }

        updatePartPositions();

        if (isDissolving()) {
            tickDissolveDeath();
            return;
        }

        // Apply configured max health once if spawned via Spawn Egg outside DivineArena
        if (tickCount == 1 && !arenaBoss) {
            applyConfiguredHealth();
        }

        // Tick down per-skill cooldowns, stagger protection, and gentle stagger decay
        for (int i = 0; i < skillCooldowns.length; i++) {
            if (skillCooldowns[i] > 0) skillCooldowns[i]--;
        }
        if (staggerCooldown > 0) staggerCooldown--;
        if (tornadoBoostTicks > 0) tornadoBoostTicks--;
        if (staggerDamage > 0.0F && tickCount % 20 == 0 && action() != STAGGER_COLLAPSE) {
            staggerDamage = Math.max(0.0F, staggerDamage - getMaxHealth() * 0.005F);
        }

        bossBar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0.0F, 1.0F));
        enforceExclusionBarrier();

        LivingEntity target = resolveTarget();
        int curAct = action();
        boolean lockYaw = (curAct == STAGGER_COLLAPSE)
            || (curAct == SWEEPING_GALE_BREATH && actionTick >= 12)
            || (curAct == SKY_BITE_THROW && actionTick >= 24);
        if (!lockYaw) {
            if (target != null) {
                faceTargetSmoothly(target);
            } else {
                Player viewer = level().getNearestPlayer(getX(), getY(), getZ(), 150.0,
                    e -> e instanceof Player p && p.isAlive() && !p.isSpectator());
                if (viewer != null) {
                    faceTargetSmoothly(viewer);
                } else if (tickCount == 1) {
                    entityData.set(DATA_TARGET_YAW, yBodyRot);
                }
            }
        }

        if (prepareTicks > 0) {
            tickArenaIntro(target);
            return;
        }

        // High-priority Phase 2 Transition (triggered safely once current grab finishes or immediately from IDLE)
        if (!phase2Triggered && getHealth() <= getMaxHealth() * DivineDragonMath.PHASE2_HEALTH_THRESHOLD
                && grabbedTargetId == null && curAct != SKY_BITE_THROW) {
            triggerPhase2();
            return;
        }

        // High-priority 20% HP Finale (triggered once from IDLE)
        if (phase2Triggered && !finaleTriggered && curAct == IDLE
                && getHealth() <= getMaxHealth() * DivineDragonMath.FINALE_HEALTH_THRESHOLD
                && target != null) {
            finaleTriggered = true;
            startAction(FINALE_JUDGMENT);
            return;
        }

        tickCombatAI(target);
    }

    /**
     * Three-act cinematic arena entrance (no countdown):
     *   Act 1 (t = 0..89): Boss concealed, swirling cloud mist, raging thunderstorm with accelerating lightning strikes.
     *   Act 2 (t = 90..93): Colossal sky-shattering lightning strikes the central Sakura Tree -> blinding white flash -> boss appears!
     *   Act 3 (t = 93..183): Towering skyward roar (`intro_awaken`) clears the mist, then transitions directly into combat!
     */
    private void tickArenaIntro(LivingEntity target) {
        prepareTicks--;
        int t = ++actionTick;
        boolean concealed = DivineDragonMath.introConcealed(t);
        bossBar.setVisible(!concealed);

        if (level() instanceof ServerLevel sl) {
            double cx = arenaCenter != null ? arenaCenter.getX() + 0.5 : getX();
            double cz = arenaCenter != null ? arenaCenter.getZ() + 0.5 : getZ();
            double floor = arenaCenter != null ? arenaCenter.getY() + 1.0 : getY() - 21.0;

            if (t == 1) {
                playBossSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 6.5F, 0.72F);
            }

            // Act 1: Accelerating visual-only storm lightning bolts crashing across the cloud sea
            for (int idx = 0; idx < DivineDragonMath.INTRO_AMBIENT_BOLT_TICKS.length; idx++) {
                if (t == DivineDragonMath.INTRO_AMBIENT_BOLT_TICKS[idx]) {
                    spawnIntroAmbientBolt(sl, cx, floor, cz, idx, target);
                    break;
                }
            }

            // Gathering thunder crescendo around the ancient Sakura Tree right before impact
            if (t == 46) {
                playBossSound(MyriadCalamity.DIVINE_DRAGON_THUNDER_CHARGE.get(), 8.8F, 0.84F);
            }
            if (t == 68) {
                playBossSound(MyriadCalamity.DIVINE_DRAGON_CATACLYSM.get(), 8.8F, 0.80F);
            }
            if (t >= 64 && t < DivineDragonMath.INTRO_MEGA_BOLT_TICK && t % 2 == 0) {
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, getY() + 2.0, cz, 12, 8.0, 5.0, 8.0, 0.22);
                sl.sendParticles(ParticleTypes.CLOUD, cx, getY() - 4.0, cz, 10, 14.0, 6.0, 14.0, 0.08);
            }

            // Act 2: Colossal Mega-Lightning strikes the center Sakura Tree -> blinding white flash!
            if (t == DivineDragonMath.INTRO_MEGA_BOLT_TICK) {
                for (int i = 0; i < 3; i++) {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
                    if (bolt != null) {
                        bolt.moveTo(cx + (i - 1) * 1.8, getY() - 1.0, cz + (i == 1 ? 0.0 : (i == 0 ? -1.4 : 1.4)));
                        bolt.setVisualOnly(true);
                        sl.addFreshEntity(bolt);
                    }
                }
                playBossSound(MyriadCalamity.DIVINE_DRAGON_CATACLYSM.get(), 10.0F, 0.76F);
                playBossSound(MyriadCalamity.DIVINE_DRAGON_LIGHTNING_STRIKE.get(), 10.0F, 0.70F);
                playBossSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 10.0F, 0.68F);
                playBossSound(SoundEvents.GENERIC_EXPLODE, 6.5F, 0.55F);
                sl.sendParticles(ParticleTypes.FLASH, cx, getY() + 4.0, cz, 4, 2.5, 2.5, 2.5, 0.0);
                sl.sendParticles(ParticleTypes.CLOUD, cx, getY() + 2.0, cz, 48, 14.0, 8.0, 14.0, 0.25);
                sl.sendParticles(ParticleTypes.CHERRY_LEAVES, cx, getY() + 4.0, cz, 42, 16.0, 10.0, 16.0, 0.22);
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, getY() + 4.0, cz, 40, 12.0, 10.0, 12.0, 0.35);
            }

            // Act 3: Boss materializes inside the blinding white flash and lets out a towering skyward roar!
            if (t == DivineDragonMath.INTRO_ROAR_PEAK_TICK) {
                playBossSound(MyriadCalamity.DIVINE_DRAGON_THUNDER_ROAR.get(), 9.8F, 0.94F);
                playBossSound(MyriadCalamity.DIVINE_DRAGON_GALE_ROAR.get(), 9.2F, 0.90F);
            }
            if (t >= DivineDragonMath.INTRO_ROAR_PEAK_TICK && t <= DivineDragonMath.INTRO_ROAR_PEAK_TICK + 32 && t % 2 == 0) {
                double ringR = 14.0 + (t - DivineDragonMath.INTRO_ROAR_PEAK_TICK) * 2.1;
                for (int i = 0; i < 20; i++) {
                    double a = i * (Math.PI * 2.0 / 20.0) + t * 0.08;
                    sl.sendParticles(i % 2 == 0 ? ParticleTypes.CLOUD : ParticleTypes.CHERRY_LEAVES,
                        cx + Math.cos(a) * ringR, floor + 2.5 + (i % 3) * 3.0, cz + Math.sin(a) * ringR,
                        1, 0.8, 0.6, 0.8, 0.16);
                }
            }
        }

        // End of skyward roar -> NO countdown, transition straight into active combat!
        if (prepareTicks <= 0 || t >= DivineDragonMath.INTRO_TOTAL_DURATION) {
            prepareTicks = 0;
            bossBar.setVisible(true);
            finishAction(4);
        }
    }

    private void spawnIntroAmbientBolt(ServerLevel sl, double cx, double floor, double cz, int idx, LivingEntity target) {
        double angle = idx * 2.399963229728653 + 0.55; // golden-angle sky coverage around the arena
        double radius = 34.0 + (idx % 4) * 12.0;
        // Pull the final few lightning bolts tightly around the central Sakura Tree!
        if (idx >= DivineDragonMath.INTRO_AMBIENT_BOLT_TICKS.length - 3) {
            radius = 18.0 + (DivineDragonMath.INTRO_AMBIENT_BOLT_TICKS.length - 1 - idx) * 6.0;
        }
        double bx = cx + Math.cos(angle) * radius;
        double bz = cz + Math.sin(angle) * radius;
        if (target != null && Math.hypot(bx - target.getX(), bz - target.getZ()) < 14.0) {
            bx = cx - Math.cos(angle) * radius;
            bz = cz - Math.sin(angle) * radius;
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
        if (bolt != null) {
            bolt.moveTo(bx, floor, bz);
            bolt.setVisualOnly(true);
            sl.addFreshEntity(bolt);
        }
        float pitch = 0.78F + (idx % 5) * 0.07F;
        sl.playSound(null, bx, floor + 24.0, bz,
            idx % 2 == 0 ? MyriadCalamity.DIVINE_DRAGON_LIGHTNING_STRIKE.get() : SoundEvents.LIGHTNING_BOLT_THUNDER,
            SoundSource.HOSTILE, 6.2F, pitch);
    }

    /** O(1) zero-allocation animation-synced multi-part hitbox sync across all 22 Sekiro clips (<200 ns/tick). */
    private void updatePartPositions() {
        boolean dissolving = isDissolving();
        int act = dissolving ? DEATH_DISSOLVE : action();
        String clip = clipNameForAction(act);
        float tSec = animationTimeSeconds(act, grabState(), actionAge(0.0F), tickCount, dissolving, dissolveAge(0.0F));
        double bx = getX();
        double by = getY();
        double bz = getZ();
        for (int i = 0; i < parts.length; i++) {
            DivineDragonHitboxes.sampleWorld(clip, tSec, i, bx, by, bz, yBodyRot, anchorBuf);
            DragonPart p = parts[i];
            placePart(p, anchorBuf[0], anchorBuf[1] - p.getBbHeight() * 0.5, anchorBuf[2]);
        }
    }

    private static void placePart(DragonPart part, double x, double y, double z) {
        part.xo = part.getX();
        part.yo = part.getY();
        part.zo = part.getZ();
        part.xOld = part.xo;
        part.yOld = part.yo;
        part.zOld = part.zo;
        part.setPos(x, y, z);
    }

    /** Enforces the 38-block absolute wind exclusion barrier (temporarily exempting an active Sky-Bite grab victim). */
    private void enforceExclusionBarrier() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        double cx = arenaCenter != null ? arenaCenter.getX() + 0.5 : getX();
        double cz = arenaCenter != null ? arenaCenter.getZ() + 0.5 : getZ();
        double scanR = DivineDragonMath.EXCLUSION_SOFT_EDGE + 1.0;
        AABB zone = new AABB(cx - scanR, getY() - 36.0, cz - scanR, cx + scanR, getY() + 80.0, cz + scanR);

        for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, zone)) {
            if (entity == this || entity instanceof DivineDragon || !entity.isAlive() || entity.isSpectator()) continue;
            if (grabbedTargetId != null && grabbedTargetId.equals(entity.getUUID())) continue;
            if (entity instanceof Player p && p.getAbilities().instabuild && p.getAbilities().flying) continue;

            DivineDragonMath.PushResult push = DivineDragonMath.computeExclusionPush(cx, cz, entity.getX(), entity.getZ());
            if (!push.active()) continue;

            if (push.hardRepel()) {
                double targetY = arenaCenter != null ? Math.max(arenaCenter.getY() + 1.1, entity.getY()) : entity.getY();
                entity.teleportTo(push.safeX(), targetY, push.safeZ());
                entity.setDeltaMovement(push.vx() * 0.45, push.vy(), push.vz() * 0.45);
                entity.hurtMarked = true;
                serverLevel.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, 0.7F, 1.4F);
                serverLevel.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + 1.0, entity.getZ(),
                    10, 0.6, 0.5, 0.6, 0.08);
            } else {
                Vec3 cur = entity.getDeltaMovement();
                entity.setDeltaMovement(push.vx(), Math.max(cur.y, push.vy()), push.vz());
                entity.hurtMarked = true;
                if (tickCount % 5 == 0) {
                    serverLevel.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + 0.6, entity.getZ(),
                        3, 0.4, 0.3, 0.4, 0.04);
                }
            }
        }
    }

    private LivingEntity resolveTarget() {
        if (arenaOwner != null && level() instanceof ServerLevel sl) {
            ServerPlayer owner = sl.getServer().getPlayerList().getPlayer(arenaOwner);
            if (owner != null && owner.level() == level() && owner.isAlive() && !owner.isSpectator() && !owner.isCreative()) {
                setTarget(owner);
                return owner;
            }
        }
        LivingEntity cur = getTarget();
        if (cur != null && cur.isAlive() && !cur.isSpectator() && cur.distanceToSqr(this) < 155.0 * 155.0) {
            return cur;
        }
        Player nearest = level().getNearestPlayer(getX(), getY(), getZ(), 150.0,
            e -> e instanceof Player p && p.isAlive() && !p.isSpectator() && !p.isCreative());
        if (nearest != null) setTarget(nearest);
        return nearest;
    }

    private void faceTargetSmoothly(LivingEntity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        float desired = (float) (Math.atan2(-dx, dz) * (180.0 / Math.PI));
        int act = action();
        float maxStep;
        if (act == IDLE) {
            maxStep = Mth.lerp(Mth.clamp(actionTick / 16.0F, 0.0F, 1.0F), 0.65F, 2.4F);
        } else if (isSwordSlashAction(act)) {
            maxStep = 2.35F;
        } else {
            maxStep = 1.35F;
        }
        float nextYaw = Mth.approachDegrees(yBodyRot, desired, maxStep);
        setYRot(nextYaw);
        yBodyRot = nextYaw;
        yHeadRot = nextYaw;
        entityData.set(DATA_TARGET_YAW, nextYaw);
    }

    private void triggerPhase2() {
        phase2Triggered = true;
        entityData.set(DATA_PHASE, 2);
        bossBar.setName(Component.translatable("boss.myriad_calamity.divine_dragon_p2"));
        bossBar.setColor(BossEvent.BossBarColor.PURPLE);
        playBossSound(MyriadCalamity.DIVINE_DRAGON_THUNDER_CHARGE.get(), 8.2F, 1.0F);
        cataclysmCooldown = 420;
        startAction(THUNDER_CATACLYSM);
    }

    private void enterStaggerCollapse() {
        releaseGrabbedTarget(true);
        staggerDamage = 0.0F;
        staggerCooldown = DivineDragonMath.STAGGER_COOLDOWN_TICKS;
        playBossSound(MyriadCalamity.DIVINE_DRAGON_HURT.get(), 8.5F, 0.85F);
        playBossSound(MyriadCalamity.DIVINE_DRAGON_DEATH_CRY.get(), 7.2F, 0.88F);
        startAction(STAGGER_COLLAPSE);
    }

    private void tickCombatAI(LivingEntity target) {
        if (cataclysmCooldown > 0) cataclysmCooldown--;
        int act = action();
        if (act == IDLE) {
            actionTick++;
            if (cooldownTicks > 0) {
                cooldownTicks--;
                return;
            }
            if (target == null) return;
            int next = chooseNextAction(target);
            startAction(next);
            return;
        }

        int t = ++actionTick;

        switch (act) {
            case ROAR_PUSH -> {
                if (t == 10) playBossSound(MyriadCalamity.DIVINE_DRAGON_COIL.get(), 7.2F, naturalPitch());
                if (t == 24) fireGaleRoar();
                if (t >= 72) finishAction(30);
            }
            case SLASH_SWEEP -> {
                if (t == 10) {
                    playBossSound(MyriadCalamity.DIVINE_DRAGON_SWORD_SWING.get(), 7.6F, naturalPitch());
                    playBossSound(MyriadCalamity.DIVINE_DRAGON_ATTACK_ROAR.get(), 7.4F, naturalPitch());
                }
                if (t == 30) fireSweepWave(target, 1.0F, true);
                if (t >= 72) finishAction(phase() >= 2 ? 26 : 34);
            }
            case SLASH_VERTICAL -> {
                if (t == 10) {
                    playBossSound(MyriadCalamity.DIVINE_DRAGON_SWORD_SWING.get(), 7.6F, naturalPitch());
                    playBossSound(MyriadCalamity.DIVINE_DRAGON_ATTACK_ROAR.get(), 7.4F, naturalPitch());
                }
                if (t == 34) fireVerticalCleave(target, 1.0F, true);
                if (t >= 80) finishAction(phase() >= 2 ? 26 : 34);
            }
            case SLASH_FAN -> {
                if (t == 8) playBossSound(MyriadCalamity.DIVINE_DRAGON_SWORD_SWING.get(), 7.6F, naturalPitch());
                if (t == 24) fireFanBlades(target, phase() >= 2 ? 7 : 5);
                if (t >= 60) finishAction(phase() >= 2 ? 26 : 36);
            }
            case SLASH_SLAM -> {
                if (t == 8) {
                    playBossSound(MyriadCalamity.DIVINE_DRAGON_SWORD_SWING.get(), 7.8F, naturalPitch());
                    playBossSound(MyriadCalamity.DIVINE_DRAGON_ATTACK_ROAR.get(), 7.6F, naturalPitch());
                }
                if (t == 28) fireVerticalCleave(target, 1.15F, true);
                if (t >= 80) finishAction(phase() >= 2 ? 26 : 36);
            }
            case SLASH_COMBO -> {
                if (t == 8) {
                    playBossSound(MyriadCalamity.DIVINE_DRAGON_ATTACK_ROAR.get(), 7.6F, naturalPitch());
                    playBossSound(MyriadCalamity.DIVINE_DRAGON_SWORD_SWING.get(), 7.6F, naturalPitch());
                }
                if (t == 27) fireVerticalCleave(target, 0.95F, true);
                if (t == 36) playBossSound(MyriadCalamity.DIVINE_DRAGON_SWORD_SWING.get(), 7.6F, naturalPitch());
                if (t == 46) fireSweepWave(target, 0.95F, true);
                if (t == 60) playBossSound(MyriadCalamity.DIVINE_DRAGON_SWORD_SWING.get(), 7.6F, 1.04F);
                if (t == 70) fireFanBlades(target, 5);
                if (t >= 100) finishAction(34);
            }
            case SLASH_WIDE_ARC -> {
                if (t == 12) {
                    playBossSound(MyriadCalamity.DIVINE_DRAGON_COIL.get(), 7.4F, naturalPitch());
                    playBossSound(MyriadCalamity.DIVINE_DRAGON_ATTACK_ROAR.get(), 7.6F, naturalPitch());
                }
                if (t == 26) playBossSound(MyriadCalamity.DIVINE_DRAGON_SWORD_SWING.get(), 7.8F, naturalPitch());
                if (t == 42) fireSweepWave(target, 1.15F, true);
                if (t >= 92) finishAction(30);
            }
            case TEMPEST_BARRAGE -> {
                if (t == 4) playBossSound(MyriadCalamity.DIVINE_DRAGON_GALE_ROAR.get(), 8.5F, naturalPitch());
                if (t == 12) playBossSound(MyriadCalamity.DIVINE_DRAGON_SWORD_SWING.get(), 7.6F, naturalPitch());
                if (t == 23) fireSweepWave(target, 0.95F, true);
                if (t == 36) playBossSound(MyriadCalamity.DIVINE_DRAGON_SWORD_SWING.get(), 7.6F, naturalPitch());
                if (t == 46) fireVerticalCleave(target, 0.95F, true);
                if (t == 56) playBossSound(MyriadCalamity.DIVINE_DRAGON_SWORD_SWING.get(), 7.6F, 1.04F);
                if (t == 64) fireFanBlades(target, 5);
                if (t == 80) playBossSound(MyriadCalamity.DIVINE_DRAGON_SWORD_SWING.get(), 7.8F, naturalPitch());
                if (t == 90) fireSweepWave(target, 1.05F, true);
                if (t == 104) playBossSound(MyriadCalamity.DIVINE_DRAGON_SWORD_SWING.get(), 8.0F, 0.94F);
                if (t == 113) {
                    fireVerticalCleave(target, 1.05F, true);
                    fireSweepWave(target, 0.95F, false);
                }
                if (t >= 140) finishAction(46);
            }
            case SUMMON_LIGHTNING -> {
                if (t == 10) {
                    playBossSound(MyriadCalamity.DIVINE_DRAGON_THUNDER_CHARGE.get(), 8.0F, naturalPitch());
                    playBossSound(MyriadCalamity.DIVINE_DRAGON_THUNDER_ROAR.get(), 8.2F, naturalPitch());
                }
                if (t == 28) spawnTargetedLightningBarrage(target, phase() >= 2 ? 13 : 10);
                if (phase() >= 2 && t == 58) spawnTargetedLightningBarrage(target, 12);
                if (t >= 88) finishAction(phase() >= 2 ? 28 : 38);
            }
            case THUNDER_CATACLYSM -> {
                if (t == 8) playBossSound(MyriadCalamity.DIVINE_DRAGON_THUNDER_ROAR.get(), 8.5F, naturalPitch());
                if (t == 16) playBossSound(MyriadCalamity.DIVINE_DRAGON_THUNDER_CHARGE.get(), 8.2F, naturalPitch());
                if (t == 34 || t == 58 || t == 78) spawnSkyCataclysmWave(target);
                if (t == 88) fireShockwaveRing(target, 1.65F);
                if (t >= 112) finishAction(52);
            }
            case CROSS_GALE_CLEAVE -> {
                if (t == 8) {
                    playBossSound(MyriadCalamity.DIVINE_DRAGON_SWORD_SWING.get(), 7.8F, naturalPitch());
                    playBossSound(MyriadCalamity.DIVINE_DRAGON_ATTACK_ROAR.get(), 7.6F, naturalPitch());
                }
                if (t == 21) fireCrossGaleSlash(target, -42.0F);
                if (t == 33) playBossSound(MyriadCalamity.DIVINE_DRAGON_SWORD_SWING.get(), 7.8F, 1.05F);
                if (t == 41) fireCrossGaleSlash(target, 42.0F);
                if (t >= 84) finishAction(phase() >= 2 ? 26 : 34);
            }
            case SHOCKWAVE_RING -> {
                if (t == 8) playBossSound(MyriadCalamity.DIVINE_DRAGON_COIL.get(), 7.6F, naturalPitch());
                if (t == 14) playBossSound(MyriadCalamity.DIVINE_DRAGON_SWORD_SWING.get(), 7.8F, naturalPitch());
                if (t == 25) fireShockwaveRing(target, 1.58F);
                if (t == 35) playBossSound(MyriadCalamity.DIVINE_DRAGON_SWORD_SWING.get(), 7.8F, 1.05F);
                if (t == 43) {
                    fireShockwaveRing(target, 1.72F);
                    if (phase() >= 2) fireShockwaveRing(target, 1.28F);
                }
                if (t >= 78) finishAction(phase() >= 2 ? 26 : 34);
            }
            case FEATHER_BARRAGE -> {
                if (t == 8) playBossSound(MyriadCalamity.DIVINE_DRAGON_THUNDER_ROAR.get(), 8.4F, 1.04F);
                int perWave = phase() >= 2 ? DivineDragonMath.FEATHER_PER_WAVE_P2 : DivineDragonMath.FEATHER_PER_WAVE_P1;
                if (t == 12) spawnSakuraFeathers(target, perWave);
                if (t >= 110) finishAction(phase() >= 2 ? 28 : 36);
            }
            case SWEEPING_GALE_BREATH -> {
                if (t == 6) {
                    playBossSound(MyriadCalamity.DIVINE_DRAGON_COIL.get(), 7.6F, naturalPitch());
                    playBossSound(MyriadCalamity.DIVINE_DRAGON_ATTACK_ROAR.get(), 7.8F, naturalPitch());
                }
                if (t == 12) {
                    entityData.set(DATA_BREATH_YAW, yBodyRot);
                    lockBreathTargetRadius(target);
                    breathHitCooldown.clear();
                }
                if (t == 24) playBossSound(MyriadCalamity.DIVINE_DRAGON_GALE_ROAR.get(), 8.5F, naturalPitch());
                if (t >= 24 && t <= 72) tickSweepingGaleBreath(t, target);
                if (t == 73) playBossSound(MyriadCalamity.DIVINE_DRAGON_COIL.get(), 7.8F, 1.08F);
                if (t == 76) lockBreathTargetRadius(target);
                if (t == 80) {
                    breathHitCooldown.clear();
                    playBossSound(MyriadCalamity.DIVINE_DRAGON_GALE_ROAR.get(), 8.6F, 1.05F);
                }
                if (t >= 80 && t <= 128) tickSweepingGaleBreath(t, target);
                if (t >= 150) finishAction(phase() >= 2 ? 30 : 38);
            }
            case CLOUD_SEA_TORNADO -> {
                if (t == 10) playBossSound(MyriadCalamity.DIVINE_DRAGON_COIL.get(), 7.8F, naturalPitch());
                if (t == 32) {
                    playBossSound(MyriadCalamity.DIVINE_DRAGON_GALE_ROAR.get(), 8.5F, naturalPitch());
                    spawnCloudSeaTornadoes(target, getHealth() < getMaxHealth() * 0.25F
                        ? DivineDragonMath.TORNADO_COUNT_LOW_HP
                        : DivineDragonMath.TORNADO_COUNT_NORMAL);
                }
                if (t >= 96) finishAction(32);
            }
            case SKY_BITE_THROW -> tickSkyBiteThrow(t, target);
            case STAGGER_COLLAPSE -> {
                if (t >= DivineDragonMath.STAGGER_DURATION_TICKS) finishAction(22);
            }
            case TWIN_MOON_SLASH -> {
                if (t == 10) playBossSound(MyriadCalamity.DIVINE_DRAGON_SWORD_SWING.get(), 7.8F, naturalPitch());
                if (t == 31) fireSweepWave(target, 1.0F, true);
                if (t == 45) playBossSound(MyriadCalamity.DIVINE_DRAGON_SWORD_SWING.get(), 7.8F, 1.08F);
                if (t == 55) fireHighMoonArc(target, 1.1F);
                if (t >= 104) finishAction(32);
            }
            case FINALE_JUDGMENT -> tickFinaleJudgment(t, target);
            default -> finishAction(30);
        }
    }

    /** AI Director: weighted skill scheduler with cooldowns, anti-repeat, blade streak breaker, and tornado-ring synergy. */
    private int chooseNextAction(LivingEntity target) {
        double dist = Math.hypot(target.getX() - getX(), target.getZ() - getZ());
        boolean p2 = phase() >= 2;
        float hpFrac = getHealth() / Math.max(1.0F, getMaxHealth());

        if (p2 && cataclysmCooldown <= 0) {
            cataclysmCooldown = 420 + random.nextInt(140);
            return THUNDER_CATACLYSM;
        }
        if (!p2 && dist < 46.0 && lastAction != ROAR_PUSH && skillCooldowns[ROAR_PUSH] <= 0 && random.nextFloat() < 0.35F) {
            return ROAR_PUSH;
        }

        List<int[]> weighted = new ArrayList<>();
        boolean forceVariety = consecutiveBladeCount >= 2;
        boolean tornadoSynergy = tornadoBoostTicks > 0;

        if (!p2) {
            addCandidate(weighted, SLASH_SWEEP, forceVariety ? 8 : (tornadoSynergy ? 26 : 22));
            addCandidate(weighted, SLASH_VERTICAL, forceVariety ? 8 : 20);
            addCandidate(weighted, SLASH_SLAM, forceVariety ? 6 : 14);
            addCandidate(weighted, SLASH_FAN, forceVariety ? 7 : 16);
            addCandidate(weighted, CROSS_GALE_CLEAVE, forceVariety ? 10 : 16);
            addCandidate(weighted, SHOCKWAVE_RING, (tornadoSynergy ? 26 : 16) + (forceVariety ? 8 : 0));
            addCandidate(weighted, FEATHER_BARRAGE, (dist > 56.0 ? 22 : 14) + (forceVariety ? 6 : 0));
            // Everblossom Gale Breath has NO HP gate — available right from 100% HP!
            addCandidate(weighted, SWEEPING_GALE_BREATH, 16 + (forceVariety ? 8 : 0));
            addCandidate(weighted, SUMMON_LIGHTNING, 18 + (forceVariety ? 8 : 0));
            if (hpFrac < 0.78F && dist <= 72.0) addCandidate(weighted, SKY_BITE_THROW, 12 + (forceVariety ? 6 : 0));
        } else {
            addCandidate(weighted, SLASH_COMBO, forceVariety ? 8 : 16);
            addCandidate(weighted, SLASH_WIDE_ARC, forceVariety ? 6 : (tornadoSynergy ? 18 : 12));
            addCandidate(weighted, TWIN_MOON_SLASH, forceVariety ? 9 : 16);
            addCandidate(weighted, TEMPEST_BARRAGE, forceVariety ? 6 : 12);
            addCandidate(weighted, SLASH_SLAM, forceVariety ? 5 : 10);
            addCandidate(weighted, SLASH_FAN, forceVariety ? 6 : 12);
            addCandidate(weighted, CROSS_GALE_CLEAVE, forceVariety ? 9 : 14);
            addCandidate(weighted, SHOCKWAVE_RING, (tornadoSynergy ? 26 : 15) + (forceVariety ? 8 : 0));
            addCandidate(weighted, FEATHER_BARRAGE, (dist > 56.0 ? 20 : 12) + (forceVariety ? 6 : 0));
            addCandidate(weighted, SWEEPING_GALE_BREATH, 10 + (forceVariety ? 8 : 0));
            addCandidate(weighted, SUMMON_LIGHTNING, 12 + (forceVariety ? 6 : 0));
            if (!hasActiveTornadoes()) addCandidate(weighted, CLOUD_SEA_TORNADO, 18 + (forceVariety ? 8 : 0));
            if (dist <= 72.0) addCandidate(weighted, SKY_BITE_THROW, 20 + (forceVariety ? 8 : 0));
        }

        if (weighted.isEmpty()) {
            return p2 ? SLASH_COMBO : SLASH_SWEEP;
        }
        int totalWeight = 0;
        for (int[] w : weighted) totalWeight += w[1];
        int roll = random.nextInt(Math.max(1, totalWeight));
        for (int[] w : weighted) {
            roll -= w[1];
            if (roll < 0) return w[0];
        }
        return weighted.get(0)[0];
    }

    private void addCandidate(List<int[]> list, int act, int weight) {
        if (weight <= 0 || skillCooldowns[act] > 0) return;
        if (act == lastAction) return;
        if (act == secondLastAction) weight = Math.max(2, weight / 3);
        list.add(new int[]{act, weight});
    }

    private boolean isPureBladeAction(int act) {
        return act == SLASH_SWEEP || act == SLASH_VERTICAL || act == SLASH_FAN
            || act == SLASH_SLAM || act == SLASH_COMBO || act == SLASH_WIDE_ARC;
    }

    private void assignSkillCooldown(int act) {
        int cd = switch (act) {
            case ROAR_PUSH -> 160;
            case SLASH_SWEEP, SLASH_VERTICAL -> 60;
            case SLASH_FAN -> 70;
            case SLASH_SLAM -> 80;
            case CROSS_GALE_CLEAVE, SLASH_WIDE_ARC -> 120;
            case SLASH_COMBO, TWIN_MOON_SLASH, SUMMON_LIGHTNING -> 140;
            case SHOCKWAVE_RING -> 160;
            case FEATHER_BARRAGE -> 180;
            case SWEEPING_GALE_BREATH -> 190;
            case TEMPEST_BARRAGE -> 240;
            case SKY_BITE_THROW -> 320;
            case CLOUD_SEA_TORNADO -> 260;
            default -> 0;
        };
        if (act >= 0 && act < skillCooldowns.length && cd > 0) skillCooldowns[act] = cd;
    }

    private boolean hasActiveTornadoes() {
        if (!(level() instanceof ServerLevel sl)) return false;
        return !sl.getEntitiesOfClass(SakuraTornadoEntity.class, getBoundingBox().inflate(120.0)).isEmpty();
    }

    private void startAction(int act) {
        entityData.set(DATA_ACTION, act);
        entityData.set(DATA_ACTION_START, level().getGameTime());
        actionTick = 0;
        if (act != SKY_BITE_THROW) {
            syncGrabInfo(GRAB_NONE);
        }
        if (act != IDLE && act != INTRO_AWAKEN && act != STAGGER_COLLAPSE && act != DEATH_DISSOLVE) {
            secondLastAction = lastAction;
            lastAction = act;
            consecutiveBladeCount = isPureBladeAction(act) ? (consecutiveBladeCount + 1) : 0;
            assignSkillCooldown(act);
        }
    }

    private void finishAction(int cd) {
        releaseGrabbedTarget(true);
        syncGrabInfo(GRAB_NONE);
        entityData.set(DATA_ACTION, IDLE);
        entityData.set(DATA_ACTION_START, level().getGameTime());
        actionTick = 0;
        cooldownTicks = cd;
    }

    private double floorY(LivingEntity target) {
        if (arenaCenter != null) return arenaCenter.getY() + 1.0;
        return target != null ? target.getY() : getY() - 22.0;
    }

    private Vec3 aimHorizontal(LivingEntity target) {
        if (target != null) {
            Vec3 d = new Vec3(target.getX() - getX(), 0, target.getZ() - getZ());
            if (d.lengthSqr() > 1e-4) return d.normalize();
        }
        float rad = yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Math.sin(rad), 0, Math.cos(rad)).normalize();
    }

    private static final double SWORD_SLASH_SPAWN_DIST = 34.5D;

    /**
     * Aims a Seven-Branched Sword wind-blade along the dragon's actual body/sword cleave yaw (with <= 4.5 deg
     * micro-lead toward target) so wind-blades always rip directly off the physical sword blade!
     */
    private Vec3 aimSwordSlash(LivingEntity target) {
        float slashYaw = yBodyRot;
        if (target != null) {
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            if (dx * dx + dz * dz > 1e-4) {
                float targetYaw = (float) (Math.atan2(-dx, dz) * (180.0 / Math.PI));
                slashYaw = yBodyRot + Mth.clamp(Mth.wrapDegrees(targetYaw - yBodyRot), -4.5F, 4.5F);
            }
        }
        float rad = slashYaw * Mth.DEG_TO_RAD;
        return new Vec3(-Math.sin(rad), 0.0, Math.cos(rad)).normalize();
    }

    private void lockBreathTargetRadius(LivingEntity target) {
        double cx = arenaCenter != null ? arenaCenter.getX() + 0.5 : getX();
        double cz = arenaCenter != null ? arenaCenter.getZ() + 0.5 : getZ();
        double dist = target != null ? Math.hypot(target.getX() - cx, target.getZ() - cz) : 60.0;
        float r = (float) Mth.clamp(dist,
            DivineDragonMath.GALE_BREATH_MIN_TARGET_RADIUS,
            DivineDragonMath.GALE_BREATH_MAX_TARGET_RADIUS);
        entityData.set(DATA_BREATH_RADIUS, r);
    }

    // ==================== EXISTING & NEW SKILL EXECUTORS ====================

    private void fireSweepWave(LivingEntity target, float dmgScale, boolean playSound) {
        Vec3 dir = aimSwordSlash(target);
        double ground = floorY(target);
        double targetY = ground + DivineDragonMath.JUMPABLE_SWEEP_HEIGHT * 0.5;
        Vec3 start = new Vec3(getX() + dir.x * SWORD_SLASH_SPAWN_DIST, targetY, getZ() + dir.z * SWORD_SLASH_SPAWN_DIST);
        float dmg = MyriadConfig.scaleDivineDragonDamage(11.0F * dmgScale);
        SakuraWindBlade wave = SakuraWindBlade.create(level(), this, SakuraWindBlade.MODE_SWEEP,
            start, dir.scale(1.68), 56.0F, DivineDragonMath.JUMPABLE_SWEEP_HEIGHT, 0.0F, dmg, 64);
        level().addFreshEntity(wave);
        if (playSound) playBossSound(MyriadCalamity.DIVINE_DRAGON_WIND_SLASH.get(), 8.0F, naturalPitch());
    }

    private void fireHighMoonArc(LivingEntity target, float dmgScale) {
        Vec3 dir = aimSwordSlash(target);
        double ground = floorY(target);
        // Elevated crescent (Y = ground + 2.155 .. 6.205) — stay grounded/sprint underneath to dodge!
        double targetY = ground + 4.18;
        Vec3 start = new Vec3(getX() + dir.x * SWORD_SLASH_SPAWN_DIST, targetY, getZ() + dir.z * SWORD_SLASH_SPAWN_DIST);
        float dmg = MyriadConfig.scaleDivineDragonDamage(12.0F * dmgScale);
        SakuraWindBlade wave = SakuraWindBlade.create(level(), this, SakuraWindBlade.MODE_HIGH_ARC,
            start, dir.scale(1.85), 56.0F, 4.05F, 0.0F, dmg, 62);
        level().addFreshEntity(wave);
        playBossSound(MyriadCalamity.DIVINE_DRAGON_WIND_SLASH.get(), 8.2F, 1.08F);
    }

    private void fireVerticalCleave(LivingEntity target, float dmgScale, boolean playSound) {
        Vec3 dir = aimSwordSlash(target);
        double ground = floorY(target);
        double targetY = ground + 11.5;
        Vec3 start = new Vec3(getX() + dir.x * SWORD_SLASH_SPAWN_DIST, targetY, getZ() + dir.z * SWORD_SLASH_SPAWN_DIST);
        float dmg = MyriadConfig.scaleDivineDragonDamage(13.0F * dmgScale);
        SakuraWindBlade wave = SakuraWindBlade.create(level(), this, SakuraWindBlade.MODE_VERTICAL,
            start, dir.scale(1.98), 4.6F, 24.0F, 0.0F, dmg, 58);
        level().addFreshEntity(wave);
        if (playSound) playBossSound(MyriadCalamity.DIVINE_DRAGON_WIND_SLASH.get(), 8.0F, naturalPitch());
    }

    private void fireCrossGaleSlash(LivingEntity target, float tiltDeg) {
        Vec3 dir = aimSwordSlash(target);
        double ground = floorY(target);
        double targetY = ground + 4.0;
        Vec3 start = new Vec3(getX() + dir.x * SWORD_SLASH_SPAWN_DIST, targetY, getZ() + dir.z * SWORD_SLASH_SPAWN_DIST);
        float dmg = MyriadConfig.scaleDivineDragonDamage(11.5F);
        SakuraWindBlade wave = SakuraWindBlade.create(level(), this, SakuraWindBlade.MODE_CROSS,
            start, dir.scale(1.78), 48.0F, 2.2F, tiltDeg, dmg, 62);
        level().addFreshEntity(wave);
        playBossSound(MyriadCalamity.DIVINE_DRAGON_WIND_SLASH.get(), 8.2F, tiltDeg < 0 ? 0.96F : 1.06F);
    }

    private void fireFanBlades(LivingEntity target, int count) {
        Vec3 baseDir = aimSwordSlash(target);
        double ground = floorY(target);
        double baseAngle = Math.atan2(baseDir.z, baseDir.x);
        float dmg = MyriadConfig.scaleDivineDragonDamage(9.5F);
        int half = count / 2;
        for (int i = -half; i <= half; i++) {
            double ang = baseAngle + Math.toRadians(i * 12.5);
            Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
            Vec3 start = new Vec3(getX() + dir.x * SWORD_SLASH_SPAWN_DIST, ground + 1.45, getZ() + dir.z * SWORD_SLASH_SPAWN_DIST);
            SakuraWindBlade blade = SakuraWindBlade.create(level(), this, SakuraWindBlade.MODE_FAN,
                start, dir.scale(1.78), 9.5F, 3.4F, i * 16.0F, dmg, 62);
            level().addFreshEntity(blade);
        }
        playBossSound(MyriadCalamity.DIVINE_DRAGON_WIND_SLASH.get(), 7.8F, naturalPitch());
    }

    private void fireShockwaveRing(LivingEntity target, float speed) {
        double cx = arenaCenter != null ? arenaCenter.getX() + 0.5 : getX();
        double cz = arenaCenter != null ? arenaCenter.getZ() + 0.5 : getZ();
        double ground = floorY(target);
        float dmg = MyriadConfig.scaleDivineDragonDamage(10.5F);
        SakuraRingWave ring = SakuraRingWave.create(level(), this, cx, ground, cz,
            22.0F, speed, DivineDragonMath.JUMPABLE_RING_HEIGHT, dmg, 48);
        level().addFreshEntity(ring);
        playBossSound(MyriadCalamity.DIVINE_DRAGON_WIND_SLASH.get(), 8.2F, 0.92F);
    }

    private void spawnSakuraFeathers(LivingEntity target, int perWaveCount) {
        if (level() instanceof ServerLevel sl) {
            // Clear any lingering unlaunched spirit-swords from an interrupted previous cast
            for (SakuraFeatherBlade old : sl.getEntitiesOfClass(SakuraFeatherBlade.class, getBoundingBox().inflate(120.0),
                    b -> !b.isLaunched())) {
                old.discard();
            }
        }
        float dmg = MyriadConfig.scaleDivineDragonDamage(4.0F);
        // Spawn ALL 3x spirit-swords at once in 3 revolving rings around the dragon;
        // Waves 0, 1, 2 lock onto the target's CURRENT position and fire sequentially!
        for (int wave = 0; wave < DivineDragonMath.FEATHER_WAVES; wave++) {
            for (int slot = 0; slot < perWaveCount; slot++) {
                SakuraFeatherBlade sword = SakuraFeatherBlade.createOrbiting(
                    level(), this, target, wave, slot, perWaveCount, dmg);
                level().addFreshEntity(sword);
            }
        }
    }

    private void tickSweepingGaleBreath(int t, LivingEntity target) {
        if (!(level() instanceof ServerLevel sl)) return;
        double ground = floorY(target);
        double sweepYawDeg = galeBreathYawDeg(t);
        double rad = Math.toRadians(sweepYawDeg);
        double fx = -Math.sin(rad), fz = Math.cos(rad);

        // Exact same mouthWorld -> endWorld straight 3D segment rendered by DivineDragonRenderer!
        DivineDragonHitboxes.sampleWorld("sweeping_gale_breath", t / 20.0F,
            DivineDragonHitboxes.MOUTH, getX(), getY(), getZ(), yBodyRot, anchorBuf);
        double mx = anchorBuf[0], my = anchorBuf[1], mz = anchorBuf[2];
        double rTarget = breathTargetRadius();
        double ex = getX() + fx * rTarget;
        double ey = ground + 0.55;
        double ez = getZ() + fz * rTarget;

        if (t % 2 == 0) {
            sl.sendParticles(ParticleTypes.GLOW, mx, my, mz, 3, 1.0, 1.0, 1.0, 0.12);
            sl.sendParticles(ParticleTypes.CLOUD, ex, ground + 0.8, ez, 4, 1.8, 0.5, 1.8, 0.14);
            sl.sendParticles(ParticleTypes.CHERRY_LEAVES, ex, ground + 1.1, ez, 3, 1.5, 0.6, 1.5, 0.10);
        }

        AABB search = new AABB(getX() - 90.0, ground - 2.0, getZ() - 90.0, getX() + 90.0, my + 6.0, getZ() + 90.0);
        float dmg = MyriadConfig.scaleDivineDragonDamage(6.0F);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, search)) {
            if (e == this || e instanceof DivineDragon || !e.isAlive() || e.isSpectator()) continue;
            if (DivineDragonMath.straightDiagonalBreathHits(
                    mx, my, mz, ex, ey, ez, ground,
                    DivineDragonMath.GALE_BREATH_BEAM_RADIUS,
                    DivineDragonMath.GALE_BREATH_SPLASH_RADIUS,
                    DivineDragonMath.GALE_BREATH_SPLASH_HEIGHT,
                    e.getX(), e.getY(), e.getZ(), e.getBbWidth() * 0.5, e.getBbHeight())) {
                int nextHit = breathHitCooldown.getOrDefault(e.getUUID(), 0);
                if (t >= nextHit) {
                    breathHitCooldown.put(e.getUUID(), t + 7);
                    e.hurt(damageSources().mobAttack(this), dmg);
                    e.push(fx * 1.15, 0.34, fz * 1.15);
                    e.hurtMarked = true;
                }
            }
        }
    }

    private void spawnCloudSeaTornadoes(LivingEntity target, int count) {
        double cx = arenaCenter != null ? arenaCenter.getX() + 0.5 : getX();
        double cz = arenaCenter != null ? arenaCenter.getZ() + 0.5 : getZ();
        double tx = target != null ? target.getX() : cx;
        double tz = target != null ? target.getZ() : cz + 58.0;
        double ground = floorY(target);
        float dmg = MyriadConfig.scaleDivineDragonDamage(7.0F);
        List<double[]> pts = DivineDragonMath.planTornadoSpawns(cx, cz, tx, tz, 44.0, 76.0, count, strikeRng);
        for (int i = 0; i < pts.size(); i++) {
            double[] p = pts.get(i);
            double angSpd = (i % 2 == 0 ? 1.0 : -1.0) * (0.012 + random.nextDouble() * 0.005);
            SakuraTornadoEntity tornado = SakuraTornadoEntity.create(
                level(), this, cx, cz, ground, p[0], p[1], p[2], angSpd, dmg, 240);
            level().addFreshEntity(tornado);
        }
        tornadoBoostTicks = 220;
    }

    // ==================== ★ GRAB / THROW SKILL #6: SKY-BITE THUNDER THROW ====================

    private void tickSkyBiteThrow(int t, LivingEntity target) {
        if (!(level() instanceof ServerLevel sl)) return;
        double cx = arenaCenter != null ? arenaCenter.getX() + 0.5 : getX();
        double cz = arenaCenter != null ? arenaCenter.getZ() + 0.5 : getZ();
        double ground = floorY(target);

        if (t == 1) {
            playBossSound(MyriadCalamity.DIVINE_DRAGON_COIL.get(), 7.8F, naturalPitch());
            updateGrabCorridorAim(cx, cz, target);
            syncGrabInfo(GRAB_TELEGRAPH);
        }

        int gs = grabState();

        // Phase 1: Wide straight corridor tracks target until t=24, then locks so player can sidestep laterally (t=24..38)
        if (gs == GRAB_TELEGRAPH) {
            if (t <= 24) {
                updateGrabCorridorAim(cx, cz, target);
                syncGrabInfo(GRAB_TELEGRAPH);
            }
            if (t == 24) {
                playBossSound(MyriadCalamity.DIVINE_DRAGON_ATTACK_ROAR.get(), 8.2F, 0.92F);
                // Snap facing squarely down the locked straight grab corridor
                setYRot(grabCorridorYaw);
                yBodyRot = grabCorridorYaw;
                yHeadRot = grabCorridorYaw;
                entityData.set(DATA_TARGET_YAW, grabCorridorYaw);
            }
            if (t >= 38 && t <= 41) {
                LivingEntity victim = findBiteCorridorVictim(sl, cx, cz, ground, target);
                if (victim != null) {
                    // GRAB HIT anywhere inside the wide straight corridor! Crunch damage + lock victim in jaws!
                    grabbedTargetId = victim.getUUID();
                    grabJawStartX = victim.getX();
                    grabJawStartY = Math.max(ground + 1.2, victim.getY());
                    grabJawStartZ = victim.getZ();
                    double rad = Math.toRadians(grabCorridorYaw);
                    double nx = -Math.sin(rad);
                    double nz = Math.cos(rad);
                    grabAirX = cx + nx * DivineDragonMath.GRAB_THROW_RADIUS;
                    grabAirY = ground + DivineDragonMath.GRAB_THROW_HEIGHT;
                    grabAirZ = cz + nz * DivineDragonMath.GRAB_THROW_RADIUS;
                    syncGrabInfo(GRAB_CLENCH);

                    victim.invulnerableTime = 0;
                    victim.hurt(damageSources().mobAttack(this), MyriadConfig.scaleDivineDragonDamage(10.0F));
                    playBossSound(MyriadCalamity.DIVINE_DRAGON_HURT.get(), 8.5F, 0.78F);
                    sl.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + 1.2, victim.getZ(),
                        24, 1.2, 1.0, 1.2, 0.2);
                    immobilizeAt(victim, grabJawStartX, grabJawStartY, grabJawStartZ);
                    return;
                }
            }
            if (t >= 42) {
                // GRAB WHIFF! Head stays low for 1.5s punish window
                syncGrabInfo(GRAB_WHIFF);
                playBossSound(MyriadCalamity.DIVINE_DRAGON_WIND_SLASH.get(), 7.2F, 0.75F);
            }
            return;
        }

        // Branch A: Whiffed bite -> 1.5s low-head punish window, finish at t=90
        if (gs == GRAB_WHIFF) {
            if (t >= 90) finishAction(34);
            return;
        }

        // Branch B: Caught victim -> must remain valid throughout throw & 4-bolt thunder execution
        LivingEntity victim = resolveGrabbedTarget(sl);
        if (victim == null || !victim.isAlive() || victim.isSpectator()) {
            releaseGrabbedTarget(true);
            finishAction(36);
            return;
        }

        // Stage 2: Clenched in rising dragon jaws (t = 40..56)
        if (gs == GRAB_CLENCH) {
            double u = Mth.clamp((t - 40.0) / 16.0, 0.0, 1.0);
            double jx = Mth.lerp(u, grabJawStartX, cx + (grabJawStartX - cx) * 0.86);
            double jy = Mth.lerp(u, grabJawStartY, ground + 15.0);
            double jz = Mth.lerp(u, grabJawStartZ, cz + (grabJawStartZ - cz) * 0.86);
            immobilizeAt(victim, jx, jy, jz);
            if (t >= 56) {
                grabJawStartX = jx;
                grabJawStartY = jy;
                grabJawStartZ = jz;
                syncGrabInfo(GRAB_AIR_THUNDER);
                playBossSound(MyriadCalamity.DIVINE_DRAGON_GALE_ROAR.get(), 8.5F, 1.05F);
            }
            return;
        }

        // Stage 3 & 4: Skyward throw (t = 56..74) + suspended mid-air immobilization + 4 locked thunderbolts (t = 76,87,98,109)
        if (gs == GRAB_AIR_THUNDER) {
            if (t <= 74) {
                double[] pos = DivineDragonMath.computeGrabArcPosition(
                    grabJawStartX, grabJawStartY, grabJawStartZ,
                    grabAirX, grabAirY, grabAirZ,
                    (t - 56.0) / 18.0);
                immobilizeAt(victim, pos[0], pos[1], pos[2]);
            } else if (t < 118) {
                immobilizeAt(victim, grabAirX, grabAirY, grabAirZ);
            }

            if (t == 70) {
                playBossSound(MyriadCalamity.DIVINE_DRAGON_THUNDER_ROAR.get(), 8.5F, 1.0F);
            }
            if (t == 76 || t == 87 || t == 98 || t == 109) {
                victim.invulnerableTime = 0;
                victim.hurt(damageSources().indirectMagic(this, this), MyriadConfig.scaleDivineDragonDamage(4.5F));
                sl.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    MyriadCalamity.DIVINE_DRAGON_LIGHTNING_STRIKE.get(), SoundSource.HOSTILE, 5.5F, 0.96F + random.nextFloat() * 0.12F);
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, victim.getX(), victim.getY() + 1.0, victim.getZ(),
                    24, 1.4, 1.6, 1.4, 0.25);
            }
            if (t == 118) {
                releaseGrabbedTarget(true);
            }
            if (t >= 126) {
                finishAction(48);
            }
        }
    }

    private void updateGrabCorridorAim(double cx, double cz, LivingEntity target) {
        if (target != null) {
            grabCorridorYaw = (float) (Math.atan2(-(target.getX() - cx), target.getZ() - cz) * (180.0 / Math.PI));
            double[] clamped = DivineDragonMath.clampIntoRing(cx, cz, target.getX(), target.getZ(),
                DivineDragonMath.GRAB_CORRIDOR_MIN_RANGE, DivineDragonMath.GRAB_CORRIDOR_MAX_RANGE);
            grabLockX = clamped[0];
            grabLockZ = clamped[1];
        } else {
            grabCorridorYaw = yBodyRot;
            float rad = yBodyRot * Mth.DEG_TO_RAD;
            grabLockX = cx - Math.sin(rad) * 52.0;
            grabLockZ = cz + Math.cos(rad) * 52.0;
        }
    }

    private LivingEntity findBiteCorridorVictim(ServerLevel sl, double cx, double cz, double ground, LivingEntity preferred) {
        double rad = Math.toRadians(grabCorridorYaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        if (preferred != null && preferred.isAlive() && !preferred.isSpectator()
                && !(preferred instanceof Player p && p.isCreative())
                && DivineDragonMath.biteCorridorHits(cx, cz, ground, fx, fz,
                    DivineDragonMath.GRAB_CORRIDOR_MIN_RANGE, DivineDragonMath.GRAB_CORRIDOR_MAX_RANGE,
                    DivineDragonMath.GRAB_CORRIDOR_HALF_WIDTH, DivineDragonMath.GRAB_CORRIDOR_MAX_HEIGHT,
                    preferred.getX(), preferred.getY(), preferred.getZ(),
                    preferred.getBbWidth() * 0.5, preferred.getBbHeight())) {
            return preferred;
        }
        double scan = DivineDragonMath.GRAB_CORRIDOR_MAX_RANGE + 8.0;
        AABB box = new AABB(cx - scan, ground - 2.0, cz - scan, cx + scan, ground + 10.0, cz + scan);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box)) {
            if (e == this || e instanceof DivineDragon || !e.isAlive() || e.isSpectator()) continue;
            if (e instanceof Player p && p.isCreative()) continue;
            if (DivineDragonMath.biteCorridorHits(cx, cz, ground, fx, fz,
                    DivineDragonMath.GRAB_CORRIDOR_MIN_RANGE, DivineDragonMath.GRAB_CORRIDOR_MAX_RANGE,
                    DivineDragonMath.GRAB_CORRIDOR_HALF_WIDTH, DivineDragonMath.GRAB_CORRIDOR_MAX_HEIGHT,
                    e.getX(), e.getY(), e.getZ(), e.getBbWidth() * 0.5, e.getBbHeight())) {
                return e;
            }
        }
        return null;
    }

    private void immobilizeAt(LivingEntity victim, double x, double y, double z) {
        victim.setNoGravity(true);
        victim.stopUsingItem();
        victim.setDeltaMovement(Vec3.ZERO);
        victim.fallDistance = 0.0F;
        victim.teleportTo(x, y, z);
        victim.hurtMarked = true;
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 10, false, false, false));
    }

    private LivingEntity resolveGrabbedTarget(ServerLevel sl) {
        if (grabbedTargetId == null) return null;
        Entity e = sl.getEntity(grabbedTargetId);
        if (e instanceof LivingEntity living) return living;
        ServerPlayer sp = sl.getServer().getPlayerList().getPlayer(grabbedTargetId);
        return (sp != null && sp.level() == sl) ? sp : null;
    }

    private void releaseGrabbedTarget(boolean ignoredSlowFall) {
        if (grabbedTargetId == null) return;
        UUID id = grabbedTargetId;
        grabbedTargetId = null;
        if (level() instanceof ServerLevel sl) {
            Entity e = sl.getEntity(id);
            LivingEntity victim = e instanceof LivingEntity l ? l : sl.getServer().getPlayerList().getPlayer(id);
            if (victim != null) {
                victim.setNoGravity(false);
                victim.fallDistance = 0.0F;
                victim.setDeltaMovement(Vec3.ZERO);
                victim.hurtMarked = true;
            }
        }
    }

    // ==================== FINALE: SEVEN-BRANCH HEAVEN'S JUDGMENT (<= 20% HP) ====================

    private void tickFinaleJudgment(int t, LivingEntity target) {
        if (t == 8) {
            playBossSound(MyriadCalamity.DIVINE_DRAGON_THUNDER_ROAR.get(), 8.8F, 0.92F);
            playBossSound(MyriadCalamity.DIVINE_DRAGON_THUNDER_CHARGE.get(), 8.5F, 1.0F);
        }
        if (t == 20) {
            fireGaleRoar();
            spawnCloudSeaTornadoes(target, DivineDragonMath.TORNADO_COUNT_FINALE);
        }
        // Wave 1: double shockwave rings + outer lightning
        if (t == 42 || t == 65) playBossSound(MyriadCalamity.DIVINE_DRAGON_SWORD_SWING.get(), 7.8F, naturalPitch());
        if (t == 51) fireShockwaveRing(target, 1.60F);
        if (t == 62) spawnTargetedLightningBarrage(target, 14);
        if (t == 73) fireShockwaveRing(target, 1.72F);
        // Wave 2: Cross-Gale X-slash (-42 deg then +42 deg) + dense storm bolts
        if (t == 88 || t == 112) playBossSound(MyriadCalamity.DIVINE_DRAGON_SWORD_SWING.get(), 8.0F, naturalPitch());
        if (t == 94) fireCrossGaleSlash(target, -42.0F);
        if (t == 112) spawnTargetedLightningBarrage(target, 14);
        if (t == 118) fireCrossGaleSlash(target, 42.0F);
        // Wave 3: 3x12 = 36 orbiting Everblossom Spirit-Swords + towering vertical finisher (tick 172)
        if (t == 142) spawnSakuraFeathers(target, DivineDragonMath.FEATHER_PER_WAVE_FINALE);
        if (t == 172) {
            fireVerticalCleave(target, 1.20F, true);
            playBossSound(MyriadCalamity.DIVINE_DRAGON_CATACLYSM.get(), 8.5F, naturalPitch());
        }
        // t = 190..246: brief exhausted head droop (2x stagger efficiency!)
        if (t >= 246) finishAction(56);
    }

    private void fireGaleRoar() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        playBossSound(MyriadCalamity.DIVINE_DRAGON_GALE_ROAR.get(), 8.5F, naturalPitch());
        double cx = arenaCenter != null ? arenaCenter.getX() + 0.5 : getX();
        double cz = arenaCenter != null ? arenaCenter.getZ() + 0.5 : getZ();
        double floor = arenaCenter != null ? arenaCenter.getY() + 1.0 : getY() - 22.0;

        for (int i = 0; i < 64; i++) {
            double a = i * (Math.PI * 2.0 / 64.0);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                cx + Math.cos(a) * DivineDragonMath.EXCLUSION_RADIUS, floor + 1.4,
                cz + Math.sin(a) * DivineDragonMath.EXCLUSION_RADIUS,
                3, 0.8, 0.6, 0.8, 0.30);
        }
        AABB box = new AABB(cx - 78.0, floor - 2.0, cz - 78.0, cx + 78.0, floor + 32.0, cz + 78.0);
        float dmg = MyriadConfig.scaleDivineDragonDamage(6.5F);
        for (LivingEntity e : serverLevel.getEntitiesOfClass(LivingEntity.class, box)) {
            if (e == this || e instanceof DivineDragon || !e.isAlive() || e.isSpectator()) continue;
            Vec3 away = new Vec3(e.getX() - cx, 0, e.getZ() - cz);
            double d = away.length();
            if (d < 1e-3 || d > 72.0) continue;
            Vec3 n = away.scale(1.0 / d);
            e.push(n.x * 1.65, 0.40, n.z * 1.65);
            e.hurtMarked = true;
            if (d < 54.0) e.hurt(damageSources().mobAttack(this), dmg);
        }
    }

    private void spawnTargetedLightningBarrage(LivingEntity target, int count) {
        double cx = arenaCenter != null ? arenaCenter.getX() + 0.5 : getX();
        double cz = arenaCenter != null ? arenaCenter.getZ() + 0.5 : getZ();
        double ground = floorY(target);
        double tx = target != null ? target.getX() : cx;
        double tz = target != null ? target.getZ() : cz + 60.0;

        List<DivineDragonMath.LightningPoint> pts = DivineDragonMath.planLightningBarrage(
            cx, cz, tx, tz,
            DivineDragonMath.ARENA_MIN_STRIKE_RADIUS,
            DivineDragonMath.ARENA_MAX_STRIKE_RADIUS,
            count, 8.5, strikeRng);

        float dmg = MyriadConfig.scaleDivineDragonDamage(12.5F);
        for (DivineDragonMath.LightningPoint p : pts) {
            DivineThunderHazard bolt = DivineThunderHazard.create(
                level(), this, p.x(), ground, p.z(),
                p.locked() ? 22 : 20 + random.nextInt(7),
                p.locked() ? 4.0F : 3.7F,
                dmg, p.locked());
            level().addFreshEntity(bolt);
        }
        playBossSound(MyriadCalamity.DIVINE_DRAGON_LIGHTNING_STRIKE.get(), 8.0F, naturalPitch());
    }

    private void spawnSkyCataclysmWave(LivingEntity target) {
        double cx = arenaCenter != null ? arenaCenter.getX() + 0.5 : getX();
        double cz = arenaCenter != null ? arenaCenter.getZ() + 0.5 : getZ();
        double ground = floorY(target);
        double tx = target != null ? target.getX() : cx;
        double tz = target != null ? target.getZ() : cz + 60.0;

        List<DivineDragonMath.LightningPoint> pts = DivineDragonMath.planSkyCataclysmWave(
            cx, cz, tx, tz,
            DivineDragonMath.ARENA_MIN_STRIKE_RADIUS,
            DivineDragonMath.ARENA_MAX_STRIKE_RADIUS,
            26, 8.0, 5.4, strikeRng);

        float dmg = MyriadConfig.scaleDivineDragonDamage(13.5F);
        for (DivineDragonMath.LightningPoint p : pts) {
            DivineThunderHazard bolt = DivineThunderHazard.create(
                level(), this, p.x(), ground, p.z(),
                p.locked() ? 23 : 21 + random.nextInt(6),
                3.8F, dmg, p.locked());
            level().addFreshEntity(bolt);
        }
        playBossSound(MyriadCalamity.DIVINE_DRAGON_CATACLYSM.get(), 8.5F, naturalPitch());
    }

    public boolean hurtPart(DragonPart part, DamageSource source, float amount) {
        if (isDissolving() || isIntroConcealed()) return false;
        float mult = (part == headPart && action() == STAGGER_COLLAPSE) ? 1.60F : part.damageMultiplier;
        float scaled = amount * mult;
        boolean ok = super.hurt(source, scaled);
        if (ok && part == headPart && level() instanceof ServerLevel sl) {
            sl.playSound(null, part.getX(), part.getY(), part.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.HOSTILE, 1.2F, action() == STAGGER_COLLAPSE ? 1.55F : 1.35F);
            sl.sendParticles(ParticleTypes.CRIT, part.getX(), part.getY() + 1.5, part.getZ(),
                12, 1.5, 1.2, 1.5, 0.15);

            // Accumulate head-weakpoint stagger meter outside immune states
            int act = action();
            boolean immuneToStagger = (act == INTRO_AWAKEN) || (act == STAGGER_COLLAPSE) || (act == THUNDER_CATACLYSM)
                || (act == FINALE_JUDGMENT && actionTick < 190)
                || (act == SKY_BITE_THROW && (grabState() == GRAB_CLENCH || grabState() == GRAB_AIR_THUNDER));
            if (!isDissolving() && staggerCooldown <= 0 && !immuneToStagger) {
                float multStag = (act == FINALE_JUDGMENT && actionTick >= 190) ? 2.0F : 1.0F;
                staggerDamage += scaled * multStag;
                if (staggerDamage >= getMaxHealth() * DivineDragonMath.STAGGER_THRESHOLD_FRACTION) {
                    enterStaggerCollapse();
                }
            }
        }
        return ok;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isDissolving() || isIntroConcealed()) return false;
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN)
                || source.is(DamageTypes.LIGHTNING_BOLT)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected void actuallyHurt(DamageSource source, float damageAmount) {
        if (isDissolving()) return;
        super.actuallyHurt(source, damageAmount);
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && getHealth() <= 0.0F) {
            beginDissolveDeath();
        }
    }

    @Override
    public void die(DamageSource source) {
        releaseGrabbedTarget(true);
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            super.die(source);
            return;
        }
        beginDissolveDeath();
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 3 && isDissolving()) return;
        super.handleEntityEvent(id);
    }

    private void beginDissolveDeath() {
        if (isDissolving()) return;
        releaseGrabbedTarget(true);
        syncGrabInfo(GRAB_NONE);
        setHealth(1.0F);
        this.hurtTime = 0;
        this.deathTime = 0;
        long now = level().getGameTime();
        entityData.set(DATA_DISSOLVING, true);
        entityData.set(DATA_DISSOLVE_START, now);
        entityData.set(DATA_ACTION, DEATH_DISSOLVE);
        entityData.set(DATA_ACTION_START, now);
        dissolveTick = 0;
        actionTick = 0;
        bossBar.setProgress(0.0F);
        bossBar.setName(Component.translatable("boss.myriad_calamity.divine_dragon_defeated"));
        bossBar.setColor(BossEvent.BossBarColor.WHITE);

        clearActiveHazards();
        playBossSound(MyriadCalamity.DIVINE_DRAGON_DEATH_CRY.get(), 8.5F, 1.0F);
    }

    private void clearActiveHazards() {
        if (!(level() instanceof ServerLevel sl)) return;
        AABB area = getBoundingBox().inflate(140.0);
        for (SakuraWindBlade b : sl.getEntitiesOfClass(SakuraWindBlade.class, area)) b.discard();
        for (DivineThunderHazard h : sl.getEntitiesOfClass(DivineThunderHazard.class, area)) h.discard();
        for (SakuraRingWave r : sl.getEntitiesOfClass(SakuraRingWave.class, area)) r.discard();
        for (SakuraTornadoEntity tor : sl.getEntitiesOfClass(SakuraTornadoEntity.class, area)) tor.discard();
        for (SakuraFeatherBlade f : sl.getEntitiesOfClass(SakuraFeatherBlade.class, area)) f.discard();
    }

    private void tickDissolveDeath() {
        int t = ++dissolveTick;
        actionTick = t;

        if (level() instanceof ServerLevel sl) {
            if (t == 72) {
                playBossSound(SoundEvents.AMETHYST_BLOCK_CHIME, 4.5F, 0.65F);
            }
            if (t >= 44) {
                int burst = t >= 70 ? 40 : 20;
                for (int i = 0; i < burst; i++) {
                    double px = getX() + (random.nextDouble() - 0.5) * 38.0;
                    double py = getY() + random.nextDouble() * 72.0;
                    double pz = getZ() + (random.nextDouble() - 0.5) * 32.0;
                    sl.sendParticles(i % 3 == 0 ? ParticleTypes.CHERRY_LEAVES
                            : (i % 3 == 1 ? ParticleTypes.WHITE_ASH : ParticleTypes.END_ROD),
                        px, py, pz, 1, 0.35, 0.45, 0.35, 0.05);
                }
            }
        }

        if (t >= DivineDragonMath.DISSOLVE_DURATION_TICKS) {
            dissolveComplete = true;
            bossBar.removeAllPlayers();
            if (!arenaBoss) {
                spawnAtLocation(MyriadCalamity.DIVINE_DRAGON_TEARS.get().getDefaultInstance());
                discard();
            }
        }
    }

    private void tickClientParticles() {
        if (isIntroConcealed()) return;
        if (isDissolving()) {
            int t = (int) dissolveAge(0.0F);
            int count = t > 48 ? 24 : 10;
            for (int i = 0; i < count; i++) {
                double px = getX() + (random.nextDouble() - 0.5) * 38.0;
                double py = getY() + random.nextDouble() * 72.0;
                double pz = getZ() + (random.nextDouble() - 0.5) * 32.0;
                level().addParticle(i % 2 == 0 ? ParticleTypes.CHERRY_LEAVES : ParticleTypes.WHITE_ASH,
                    px, py, pz, (random.nextDouble() - 0.5) * 0.07, 0.08 + random.nextDouble() * 0.08, (random.nextDouble() - 0.5) * 0.07);
            }
            return;
        }
        if (tickCount % 2 == 0) {
            double cx = arenaCenter != null ? arenaCenter.getX() + 0.5 : getX();
            double cz = arenaCenter != null ? arenaCenter.getZ() + 0.5 : getZ();
            double floor = arenaCenter != null ? arenaCenter.getY() + 1.0 : getY() - 22.0;
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble() * Math.PI * 2.0;
                double ex = cx + Math.cos(a) * DivineDragonMath.EXCLUSION_RADIUS;
                double ez = cz + Math.sin(a) * DivineDragonMath.EXCLUSION_RADIUS;
                level().addParticle(ParticleTypes.CLOUD, ex, floor + 0.35 + random.nextDouble() * 2.0, ez,
                    -Math.sin(a) * 0.11, 0.02, Math.cos(a) * 0.11);
            }
            for (int i = 0; i < 2; i++) {
                level().addParticle(ParticleTypes.CHERRY_LEAVES,
                    getX() + (random.nextDouble() - 0.5) * 52.0,
                    getY() + 10.0 + random.nextDouble() * 56.0,
                    getZ() + (random.nextDouble() - 0.5) * 52.0,
                    0.05, -0.025, 0.04);
            }
        }
    }

    private float naturalPitch() {
        return 0.96F + random.nextFloat() * 0.08F;
    }

    private void playBossSound(SoundEvent sound, float volume, float pitch) {
        level().playSound(null, getX(), getY() + 28.0, getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isDissolving() ? null : MyriadCalamity.DIVINE_DRAGON_AMBIENT.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    @Override
    protected float getSoundVolume() {
        return 6.8F;
    }

    @Override
    public float getVoicePitch() {
        return naturalPitch();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MyriadCalamity.DIVINE_DRAGON_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MyriadCalamity.DIVINE_DRAGON_DEATH_CRY.get();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("ArenaBoss", arenaBoss);
        if (arenaCenter != null) tag.putLong("ArenaCenter", arenaCenter.asLong());
        if (arenaOwner != null) tag.putUUID("ArenaOwner", arenaOwner);
        tag.putInt("Phase", phase());
        tag.putBoolean("Phase2Triggered", phase2Triggered);
        tag.putBoolean("FinaleTriggered", finaleTriggered);
        tag.putFloat("StaggerDamage", staggerDamage);
        tag.putInt("StaggerCooldown", staggerCooldown);
        tag.putBoolean("Dissolving", isDissolving());
        tag.putInt("Action", action());
        tag.putInt("PrepareTicks", prepareTicks);
        tag.putInt("ActionTick", actionTick);
        tag.putInt("DissolveTick", dissolveTick);
        tag.putFloat("TargetYaw", entityData.get(DATA_TARGET_YAW));
        tag.putBoolean("DissolveComplete", dissolveComplete);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        arenaBoss = tag.getBoolean("ArenaBoss");
        if (tag.contains("ArenaCenter")) arenaCenter = BlockPos.of(tag.getLong("ArenaCenter"));
        if (tag.hasUUID("ArenaOwner")) arenaOwner = tag.getUUID("ArenaOwner");
        entityData.set(DATA_PHASE, Math.max(1, tag.getInt("Phase")));
        phase2Triggered = tag.getBoolean("Phase2Triggered");
        finaleTriggered = tag.getBoolean("FinaleTriggered");
        staggerDamage = tag.getFloat("StaggerDamage");
        staggerCooldown = tag.getInt("StaggerCooldown");
        entityData.set(DATA_DISSOLVING, tag.getBoolean("Dissolving"));
        if (tag.contains("Action")) entityData.set(DATA_ACTION, tag.getInt("Action"));
        prepareTicks = tag.getInt("PrepareTicks");
        actionTick = tag.getInt("ActionTick");
        dissolveTick = tag.getInt("DissolveTick");
        entityData.set(DATA_TARGET_YAW, tag.getFloat("TargetYaw"));
        startTimesSynced = false;
        dissolveComplete = tag.getBoolean("DissolveComplete");
        bossBar.setVisible(!isIntroConcealed());
    }

    public static final class DragonPart extends PartEntity<DivineDragon> {
        public final String partName;
        public final float damageMultiplier;
        private final EntityDimensions size;

        public DragonPart(DivineDragon parent, String name, float width, float height, float damageMultiplier) {
            super(parent);
            this.partName = name;
            this.damageMultiplier = damageMultiplier;
            this.size = EntityDimensions.scalable(width, height);
            this.refreshDimensions();
        }

        @Override
        protected void defineSynchedData() {}

        @Override
        protected void readAdditionalSaveData(CompoundTag tag) {}

        @Override
        protected void addAdditionalSaveData(CompoundTag tag) {}

        @Override
        public boolean isPickable() {
            return !getParent().isIntroConcealed();
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            return !isInvulnerableTo(source) && getParent().hurtPart(this, source, amount);
        }

        @Override
        public boolean is(Entity entity) {
            return this == entity || getParent() == entity;
        }

        @Override
        public EntityDimensions getDimensions(Pose pose) {
            return size;
        }

        @Override
        public boolean shouldBeSaved() {
            return false;
        }
    }
}
