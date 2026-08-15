package io.github.langqi99.mekanicalcreate.content;

import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Optional, reflection-only integration with Create: Sifter. */
final class CreateSifterCompat {
    private static final String NAMESPACE = CreateSifterIds.NAMESPACE;
    private static final String SIFTING_TYPE_CLASS =
            "com.oierbravo.createsifter.content.contraptions.components.sifter.recipe."
                    + "SiftingRecipe$Type";
    private static final String LEGACY_RECIPE_TYPES_CLASS =
            "com.oierbravo.createsifter.ModRecipeTypes";
    private static final String MESH_INTERFACE =
            "com.oierbravo.createsifter.content.contraptions.components.meshes.IMesh";
    private static final Map<RecipeManager, List<SiftingRecipeData>> CACHE = new WeakHashMap<>();

    private CreateSifterCompat() {
    }

    static synchronized void clearCache() {
        CACHE.clear();
    }

    static boolean isSifterModule(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return isSifterModuleId(id.getNamespace(), id.getPath());
    }

    static boolean isBrassSifter(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return NAMESPACE.equals(id.getNamespace()) && "brass_sifter".equals(id.getPath());
    }

    static boolean isSifterModuleId(String namespace, String path) {
        return CreateSifterIds.isSifterModule(namespace, path);
    }

    static List<ItemStack> sifterModules() {
        List<ItemStack> result = new ArrayList<>(2);
        for (String path : List.of("sifter", "brass_sifter")) {
            Item item = BuiltInRegistries.ITEM.get(
                    new ResourceLocation(NAMESPACE, path));
            if (item != null) {
                ItemStack stack = item.getDefaultInstance();
                if (!stack.isEmpty() && isSifterModule(stack)) {
                    result.add(stack);
                }
            }
        }
        return List.copyOf(result);
    }

    static boolean isMesh(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (NAMESPACE.equals(id.getNamespace()) && id.getPath().endsWith("_mesh")) {
            return true;
        }
        return implementsInterface(stack.getItem().getClass(), MESH_INTERFACE);
    }

    static List<ItemStack> meshes(Level level) {
        Set<Item> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ItemStack> result = new ArrayList<>();
        for (SiftingRecipeData recipe : recipes(level)) {
            if (seen.add(recipe.mesh.getItem())) {
                result.add(recipe.mesh.copyWithCount(1));
            }
        }
        return List.copyOf(result);
    }

    static synchronized List<SiftingRecipeData> recipes(Level level) {
        return CACHE.computeIfAbsent(level.getRecipeManager(),
                ignored -> discoverRecipes(level));
    }

    private static List<SiftingRecipeData> discoverRecipes(Level level) {
        List<SiftingRecipeData> result = new ArrayList<>();
        List<RecipeType<?>> types = siftingRecipeTypes();
        for (RecipeType<?> type : types) {
            for (Recipe<?> recipe
                    : CreateFamilyRecipeDiscovery.allRecipesFor(level.getRecipeManager(), type)) {
                read(recipe).ifPresent(result::add);
            }
        }
        MekanicalCreate.LOGGER.info(
                "Create: Sifter compatibility discovered {} sifting recipes using {} type(s)",
                result.size(), types.size());
        return List.copyOf(result);
    }

    /**
     * Create: Sifter 2.x serializes recipes under its registered serializer,
     * but the recipes themselves report an unregistered singleton type. Its
     * own recipe manager and JEI integration both query that singleton. Use
     * the same object reflectively, then keep registry aliases as a fallback
     * for older/future releases.
     */
    private static List<RecipeType<?>> siftingRecipeTypes() {
        Set<RecipeType<?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<RecipeType<?>> result = new ArrayList<>();
        try {
            Class<?> typeClass = Class.forName(SIFTING_TYPE_CLASS, true,
                    CreateSifterCompat.class.getClassLoader());
            Field instanceField = typeClass.getField("INSTANCE");
            Object instance = instanceField.get(null);
            if (instance instanceof RecipeType<?> type && seen.add(type)) {
                result.add(type);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            MekanicalCreate.LOGGER.debug(
                    "Could not access Create: Sifter's native recipe type", exception);
        }
        try {
            Class<?> typesClass = Class.forName(LEGACY_RECIPE_TYPES_CLASS, true,
                    CreateSifterCompat.class.getClassLoader());
            Object sifting = typesClass.getField("SIFTING").get(null);
            Object value = typesClass.getMethod("getType").invoke(sifting);
            if (value instanceof RecipeType<?> type && seen.add(type)) {
                result.add(type);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            MekanicalCreate.LOGGER.debug(
                    "Could not access Create: Sifter's legacy recipe type", exception);
        }
        for (var entry : BuiltInRegistries.RECIPE_TYPE.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            RecipeType<?> type = entry.getValue();
            if (CreateSifterIds.isSiftingRecipeType(id.getNamespace(), id.getPath())
                    && seen.add(type)) {
                result.add(type);
            }
        }
        return List.copyOf(result);
    }

    private static java.util.Optional<SiftingRecipeData> read(Recipe<?> recipe) {
        if (!recipe.getClass().getName().endsWith(".SiftingRecipe")) {
            return java.util.Optional.empty();
        }
        try {
            Ingredient input = invokeFirst(recipe, Ingredient.class,
                    "getInput", "getSiftableIngredient");
            ItemStack mesh = invokeFirst(recipe, ItemStack.class,
                    "getMesh", "getMeshItemStack");
            Boolean waterlogged = invoke(recipe, "isWaterlogged", Boolean.class);
            Boolean advanced = invokeFirst(recipe, Boolean.class,
                    "requiresAdvancedSifter", "requiresAdvancedMesh");
            Integer processingTime = invokeFirst(recipe, Integer.class,
                    "getProcessingTime", "getProcessingDuration");
            Object rawOutputs = recipe.getClass().getMethod("getRollableResults").invoke(recipe);
            if (!(rawOutputs instanceof List<?> values) || input.isEmpty() || mesh.isEmpty()) {
                return java.util.Optional.empty();
            }
            List<ProcessingOutput> outputs = values.stream()
                    .filter(ProcessingOutput.class::isInstance)
                    .map(ProcessingOutput.class::cast)
                    .toList();
            if (outputs.isEmpty()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new SiftingRecipeData(recipe.getId(), input,
                    mesh.copyWithCount(1), outputs, Math.max(20, processingTime),
                    waterlogged, advanced));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            MekanicalCreate.LOGGER.debug("Could not inspect Create: Sifter recipe {}",
                    recipe.getId(), exception);
            return java.util.Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(Object target, String methodName, Class<T> expected)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = target.getClass().getMethod(methodName);
        Object value = method.invoke(target);
        if (expected == Integer.class && value instanceof Integer integer) {
            return (T) integer;
        }
        if (expected == Boolean.class && value instanceof Boolean bool) {
            return (T) bool;
        }
        return expected.cast(value);
    }

    private static <T> T invokeFirst(Object target, Class<T> expected,
                                     String... methodNames)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        NoSuchMethodException missing = null;
        for (String methodName : methodNames) {
            try {
                return invoke(target, methodName, expected);
            } catch (NoSuchMethodException exception) {
                missing = exception;
            }
        }
        throw missing == null ? new NoSuchMethodException() : missing;
    }

    private static boolean implementsInterface(@Nullable Class<?> type, String interfaceName) {
        if (type == null) {
            return false;
        }
        for (Class<?> implemented : type.getInterfaces()) {
            if (implemented.getName().equals(interfaceName)
                    || implementsInterface(implemented, interfaceName)) {
                return true;
            }
        }
        return implementsInterface(type.getSuperclass(), interfaceName);
    }

    record SiftingRecipeData(ResourceLocation id, Ingredient input, ItemStack mesh,
                             List<ProcessingOutput> outputs, int duration,
                             boolean waterlogged, boolean requiresAdvancedSifter) {
        SiftingRecipeData {
            mesh = mesh.copyWithCount(1);
            outputs = List.copyOf(outputs);
        }
    }
}
