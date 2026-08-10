package io.github.langqi99.mekanicalcreate.content;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/** Reads optional, addon-defined FE costs without linking against the addon. */
final class NativeRecipeEnergy {
    private static final List<String> TOTAL_ENERGY_GETTERS = List.of(
            "getEnergy", "getEnergyRequired", "getTotalEnergy");
    private static final List<String> RATE_GETTERS = List.of(
            "getMaxChargeRate", "getEnergyPerTick", "getPower",
            "getMaxEnergyUsage");

    private NativeRecipeEnergy() {
    }

    static Optional<EnergyProfile> profile(ProcessingRecipe<?, ?> recipe,
                                           int fallbackDuration) {
        long totalEnergy = readPositiveLong(recipe, TOTAL_ENERGY_GETTERS);
        long maximumRate = readPositiveLong(recipe, RATE_GETTERS);
        if (totalEnergy <= 0 && maximumRate <= 0) {
            return Optional.empty();
        }

        int duration = Math.max(1, fallbackDuration);
        long energyPerTick;
        if (totalEnergy > 0 && maximumRate > 0) {
            duration = Math.toIntExact(Math.min(Integer.MAX_VALUE,
                    ceilDiv(totalEnergy, maximumRate)));
            // Preserve the declared total as closely as fixed-per-tick machine
            // processing allows, without exceeding the addon's rate limit.
            energyPerTick = ceilDiv(totalEnergy, duration);
        } else if (totalEnergy > 0) {
            energyPerTick = ceilDiv(totalEnergy, duration);
        } else {
            energyPerTick = maximumRate;
        }
        return Optional.of(new EnergyProfile(duration, Math.max(1, energyPerTick)));
    }

    private static long readPositiveLong(Object target, List<String> getterNames) {
        for (String getterName : getterNames) {
            try {
                Method method = target.getClass().getMethod(getterName);
                if (method.getParameterCount() == 0
                        && Number.class.isAssignableFrom(method.getReturnType())) {
                    Object value = method.invoke(target);
                    if (value instanceof Number number && number.longValue() > 0) {
                        return number.longValue();
                    }
                } else if (method.getParameterCount() == 0
                        && method.getReturnType().isPrimitive()) {
                    Object value = method.invoke(target);
                    if (value instanceof Number number && number.longValue() > 0) {
                        return number.longValue();
                    }
                }
            } catch (NoSuchMethodException ignored) {
                // Getter names are intentionally probed across unrelated addons.
            } catch (IllegalAccessException | InvocationTargetException
                     | LinkageError exception) {
                return 0;
            }
        }
        return 0;
    }

    private static long ceilDiv(long numerator, long denominator) {
        return 1L + (numerator - 1L) / denominator;
    }

    record EnergyProfile(int duration, long energyPerTick) {
    }
}
