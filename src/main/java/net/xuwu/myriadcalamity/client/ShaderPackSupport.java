package net.xuwu.myriadcalamity.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

/** Client-only detection for optional shader loaders, including changes made in the video menu. */
final class ShaderPackSupport {
    enum Pass { ORIGINAL, COLOR, SHADOW }

    private record Hooks(BooleanSupplier enabled, BooleanSupplier shadow) {}
    private static final Hooks HOOKS = findHooks();

    private ShaderPackSupport() {}

    static Pass pass() {
        if (!HOOKS.enabled().getAsBoolean()) return Pass.ORIGINAL;
        return HOOKS.shadow().getAsBoolean() ? Pass.SHADOW : Pass.COLOR;
    }

    private static Hooks findHooks() {
        try {
            // Oculus also exposes the Iris v0 compatibility API.
            Class<?> iris = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object api = iris.getMethod("getInstance").invoke(null);
            return new Hooks(booleanMethod(iris.getMethod("isShaderPackInUse"), api),
                booleanMethod(iris.getMethod("isRenderingShadowPass"), api));
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            return optiFineHooks();
        }
    }

    private static Hooks optiFineHooks() {
        try {
            Class<?> config = Class.forName("net.optifine.Config");
            Method enabled = config.getMethod("isShaders");
            return new Hooks(booleanMethod(enabled, null), optiFineShadow());
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            return new Hooks(() -> false, () -> false);
        }
    }

    private static BooleanSupplier optiFineShadow() {
        try {
            Field field = Class.forName("net.optifine.shaders.Shaders").getField("isShadowPass");
            return () -> {
                try {
                    return field.getBoolean(null);
                } catch (IllegalAccessException | LinkageError ignored) {
                    return false;
                }
            };
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            return () -> false;
        }
    }

    private static BooleanSupplier booleanMethod(Method method, Object owner) {
        return () -> {
            try {
                return Boolean.TRUE.equals(method.invoke(owner));
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return false;
            }
        };
    }
}
