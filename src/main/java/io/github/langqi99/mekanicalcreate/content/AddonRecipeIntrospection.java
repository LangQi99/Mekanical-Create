package io.github.langqi99.mekanicalcreate.content;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;

/**
 * Lossless, conservative access to addon recipes which do not extend Create's
 * {@link ProcessingRecipe}. No addon classes are linked directly.
 */
final class AddonRecipeIntrospection {
    private static final List<String> OUTPUT_GETTERS = List.of(
            "getResultStack", "getOutput", "getResult", "getResultItem");

    private AddonRecipeIntrospection() {
    }

    static boolean hasPotentialItemTransformation(Recipe<?> recipe) {
        if (recipe instanceof ProcessingRecipe<?> processing) {
            return !processing.getRollableResults().isEmpty()
                    || !processing.getFluidResults().isEmpty();
        }
        if (recipe.getIngredients().isEmpty()) {
            return false;
        }
        return hasItemStackGetter(recipe.getClass());
    }

    static List<ItemStack> itemOutputs(Recipe<?> recipe, Level level) {
        if (recipe instanceof ProcessingRecipe<?> processing) {
            return processing.getRollableResults().stream()
                    .map(output -> output.getStack().copy())
                    .filter(stack -> !stack.isEmpty())
                    .toList();
        }
        List<ItemStack> outputs = new ArrayList<>();
        try {
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (!result.isEmpty()) {
                outputs.add(result.copy());
            }
        } catch (LinkageError | RuntimeException ignored) {
            // Some specialized recipes require a concrete container instead.
        }
        if (!outputs.isEmpty()) {
            return List.copyOf(outputs);
        }
        for (String getter : OUTPUT_GETTERS) {
            try {
                Method method = recipe.getClass().getMethod(getter);
                if (method.getParameterCount() != 0
                        || !ItemStack.class.isAssignableFrom(method.getReturnType())) {
                    continue;
                }
                Object value = method.invoke(recipe);
                if (value instanceof ItemStack stack && !stack.isEmpty()) {
                    return List.of(stack.copy());
                }
            } catch (NoSuchMethodException ignored) {
                // Getter names are intentionally probed across unrelated addons.
            } catch (IllegalAccessException | InvocationTargetException
                     | LinkageError exception) {
                MekanicalCreate.LOGGER.debug(
                        "Could not read addon recipe output from {}",
                        recipe.getClass().getName(), exception);
                return List.of();
            }
        }
        return List.of();
    }

    private static boolean hasItemStackGetter(Class<?> recipeClass) {
        for (Method method : recipeClass.getMethods()) {
            String name = method.getName().toLowerCase(Locale.ROOT);
            if ((name.contains("result") || name.contains("output"))
                    && ItemStack.class.isAssignableFrom(method.getReturnType())) {
                return true;
            }
        }
        return false;
    }
}
