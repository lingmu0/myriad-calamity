package net.xuwu.myriadcalamity.world;

/** Weather sampled from a synchronized boss transition rather than the Overworld's rain cycle. */
public record CloudWeather(float rain, float thunder, float coverage, float red) {
    public static final int LIGHT_RAIN_TICKS = 100;
    public static final CloudWeather CLEAR = new CloudWeather(0, 0, 0, 0);
    private static final int CLOUD_BUILDUP_TICKS = 80;
    private static final CloudWeather LIGHT_RAIN = new CloudWeather(.43F, 0, 1, 0);
    private static final CloudWeather THUNDERSTORM = new CloudWeather(1, 1, 1, 1);

    public static CloudWeather phase(int phase) {
        return switch (phase) {
            case 2 -> LIGHT_RAIN;
            case 3 -> THUNDERSTORM;
            default -> CLEAR;
        };
    }

    public static CloudWeather enteringLightRain(float elapsed) {
        if (elapsed <= 0) return CLEAR;
        if (elapsed >= LIGHT_RAIN_TICKS) return LIGHT_RAIN;
        return new CloudWeather(LIGHT_RAIN.rain * ease(elapsed / LIGHT_RAIN_TICKS), 0,
            ease(elapsed / CLOUD_BUILDUP_TICKS), 0);
    }

    public static CloudWeather enteringThunderstorm(float progress) {
        if (progress <= 0) return LIGHT_RAIN;
        if (progress >= 1) return THUNDERSTORM;
        float strength = ease(progress);
        return new CloudWeather(LIGHT_RAIN.rain + (1 - LIGHT_RAIN.rain) * strength, strength, 1, strength);
    }

    private static float ease(float progress) {
        float t = Math.min(1, Math.max(0, progress));
        return t * t * (3 - 2 * t);
    }
}
