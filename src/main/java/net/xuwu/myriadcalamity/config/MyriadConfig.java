package net.xuwu.myriadcalamity.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Common server configuration for the boss encounters. */
public final class MyriadConfig {
    public static final double DEFAULT_DANCER_HEALTH = 480.0D;
    public static final double DEFAULT_DANCER_DAMAGE = 9.0D;
    public static final double DEFAULT_YANG_JIAN_HEALTH = 640.0D;
    public static final double DEFAULT_YANG_JIAN_DAMAGE = 9.0D;
    public static final double DEFAULT_YANG_JIAN_GUARD = 180.0D;
    public static final double DEFAULT_YANG_JIAN_PHASE_2_GUARD = 220.0D;
    public static final double DEFAULT_YANG_JIAN_PHASE_3_GUARD = 260.0D;
    public static final double DEFAULT_YANG_JIAN_PHASE_3_HEALTH_FLOOR = .4D;
    public static final boolean DEFAULT_SHOW_ATTACK_INDICATORS = false;
    public static final double DEFAULT_HOUND_HEALTH = 40.0D;
    public static final double DEFAULT_HOUND_DAMAGE = 5.0D;
    public static final double DEFAULT_DIVINE_DRAGON_HEALTH = 800.0D;
    public static final double DEFAULT_DIVINE_DRAGON_DAMAGE = 10.0D;
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue DANCER_HEALTH;
    public static final ForgeConfigSpec.DoubleValue DANCER_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue YANG_JIAN_HEALTH;
    public static final ForgeConfigSpec.DoubleValue YANG_JIAN_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue YANG_JIAN_GUARD;
    public static final ForgeConfigSpec.DoubleValue YANG_JIAN_PHASE_2_GUARD;
    public static final ForgeConfigSpec.DoubleValue YANG_JIAN_PHASE_3_GUARD;
    public static final ForgeConfigSpec.DoubleValue YANG_JIAN_PHASE_3_HEALTH_FLOOR;
    public static final ForgeConfigSpec.BooleanValue SHOW_ATTACK_INDICATORS;
    public static final ForgeConfigSpec.DoubleValue HOUND_HEALTH;
    public static final ForgeConfigSpec.DoubleValue HOUND_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue DIVINE_DRAGON_HEALTH;
    public static final ForgeConfigSpec.DoubleValue DIVINE_DRAGON_DAMAGE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("cogworkDancer");
        DANCER_HEALTH = builder
            .comment("Shared maximum health of a newly summoned Cogwork Dancer pair.")
            .defineInRange("health", DEFAULT_DANCER_HEALTH, 1.0D, 1_000_000.0D);
        DANCER_DAMAGE = builder
            .comment("Base attack damage. Individual moves scale from their normal damage around this value.")
            .defineInRange("damage", DEFAULT_DANCER_DAMAGE, 0.1D, 1_000.0D);
        builder.pop();
        builder.push("yangJian");
        YANG_JIAN_HEALTH = builder.comment("Maximum health for a newly started Yang Jian challenge.")
            .defineInRange("health", DEFAULT_YANG_JIAN_HEALTH, 1.0D, 1_000_000.0D);
        YANG_JIAN_DAMAGE = builder.comment("Base damage for all three phases; each move keeps its own damage multiplier.")
            .defineInRange("damage", DEFAULT_YANG_JIAN_DAMAGE, .1D, 1_000.0D);
        YANG_JIAN_GUARD = builder.comment("P1 defense bar. Exhausting this bar starts the transition to P2.")
            .defineInRange("guard", DEFAULT_YANG_JIAN_GUARD, 1.0D, 1_000_000.0D);
        YANG_JIAN_PHASE_2_GUARD = builder.comment("P2 defense bar restored after the transition. Exhausting it starts P3.")
            .defineInRange("phase2Guard", DEFAULT_YANG_JIAN_PHASE_2_GUARD, 1.0D, 1_000_000.0D);
        YANG_JIAN_PHASE_3_GUARD = builder.comment("P3 defense bar. After it is exhausted, health damage is enabled until the bar recovers.")
            .defineInRange("phase3Guard", DEFAULT_YANG_JIAN_PHASE_3_GUARD, 1.0D, 1_000_000.0D);
        YANG_JIAN_PHASE_3_HEALTH_FLOOR = builder.comment("Minimum fraction of maximum health retained when P3 begins. Higher remaining health is preserved.")
            .defineInRange("phase3HealthFloor", DEFAULT_YANG_JIAN_PHASE_3_HEALTH_FLOOR, 0.0D, 1.0D);
        SHOW_ATTACK_INDICATORS = builder.comment("Show Yang Jian's ground attack ranges, including lightning, sword rain, sweeps and the transition slam.",
                "The attack animations, falling bolts, sword models and traveling shock waves remain visible.")
            .define("showAttackIndicators", DEFAULT_SHOW_ATTACK_INDICATORS);
        HOUND_HEALTH = builder.comment("Maximum health of a newly summoned celestial hound.")
            .defineInRange("houndHealth", DEFAULT_HOUND_HEALTH, 1.0D, 100_000.0D);
        HOUND_DAMAGE = builder.comment("Base celestial hound pounce damage.")
            .defineInRange("houndDamage", DEFAULT_HOUND_DAMAGE, .1D, 1_000.0D);
        builder.pop();
        builder.push("divineDragon");
        DIVINE_DRAGON_HEALTH = builder.comment("Maximum health of the Divine Dragon boss.")
            .defineInRange("health", DEFAULT_DIVINE_DRAGON_HEALTH, 1.0D, 1_000_000.0D);
        DIVINE_DRAGON_DAMAGE = builder.comment("Base damage for the Divine Dragon's wind blades and lightning strikes.")
            .defineInRange("damage", DEFAULT_DIVINE_DRAGON_DAMAGE, .1D, 1_000.0D);
        builder.pop();
        SPEC = builder.build();
    }

    public static float dancerHealth() {
        return DANCER_HEALTH.get().floatValue();
    }

    public static float dancerDamage() {
        return DANCER_DAMAGE.get().floatValue();
    }

    /** Keeps the existing per-move ratios while letting one config value tune all dancer attacks. */
    public static float scaleDancerDamage(float normalDamage) {
        return normalDamage * dancerDamage() / (float) DEFAULT_DANCER_DAMAGE;
    }

    public static float yangJianHealth() { return YANG_JIAN_HEALTH.get().floatValue(); }
    public static float yangJianDamage() { return YANG_JIAN_DAMAGE.get().floatValue(); }
    public static float yangJianGuard() { return YANG_JIAN_GUARD.get().floatValue(); }
    public static float yangJianPhase2Guard() { return YANG_JIAN_PHASE_2_GUARD.get().floatValue(); }
    public static float yangJianPhase3Guard() { return YANG_JIAN_PHASE_3_GUARD.get().floatValue(); }
    public static float yangJianPhase3HealthFloor() { return YANG_JIAN_PHASE_3_HEALTH_FLOOR.get().floatValue(); }
    /** Off by default: the fight reads from the boss's own motion instead of painted ground shapes. */
    public static boolean showYangJianAttackIndicators() { return SHOW_ATTACK_INDICATORS.get(); }
    public static float houndHealth() { return HOUND_HEALTH.get().floatValue(); }
    public static float houndDamage() { return HOUND_DAMAGE.get().floatValue(); }
    public static float scaleYangJianDamage(float normalDamage) {
        return normalDamage * yangJianDamage() / (float) DEFAULT_YANG_JIAN_DAMAGE;
    }
    public static float divineDragonHealth() { return DIVINE_DRAGON_HEALTH.get().floatValue(); }
    public static float divineDragonDamage() { return DIVINE_DRAGON_DAMAGE.get().floatValue(); }
    public static float scaleDivineDragonDamage(float normalDamage) {
        return normalDamage * divineDragonDamage() / (float) DEFAULT_DIVINE_DRAGON_DAMAGE;
    }

    private MyriadConfig() {}
}
