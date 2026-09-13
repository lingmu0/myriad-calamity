package net.xuwu.myriadcalamity.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common server configuration for the Cogwork Dancer encounter. */
public final class MyriadConfig {
    public static final double DEFAULT_BOSS_HEALTH = 480.0D;
    public static final double DEFAULT_BOSS_DAMAGE = 9.0D;
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue BOSS_HEALTH;
    public static final ModConfigSpec.DoubleValue BOSS_DAMAGE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        BOSS_HEALTH = builder
            .comment("Shared maximum health of a newly summoned Cogwork Dancer pair.")
            .defineInRange("bossHealth", DEFAULT_BOSS_HEALTH, 1.0D, 1_000_000.0D);
        BOSS_DAMAGE = builder
            .comment("Base attack damage. Individual moves scale from their normal damage around this value.")
            .defineInRange("bossDamage", DEFAULT_BOSS_DAMAGE, 0.1D, 1_000.0D);
        SPEC = builder.build();
    }

    public static float bossHealth() {
        return BOSS_HEALTH.get().floatValue();
    }

    public static float bossDamage() {
        return BOSS_DAMAGE.get().floatValue();
    }

    /** Keeps the existing per-move ratios while letting one config value tune all attacks. */
    public static float scaleDamage(float normalDamage) {
        return normalDamage * bossDamage() / (float) DEFAULT_BOSS_DAMAGE;
    }

    private MyriadConfig() {}
}
