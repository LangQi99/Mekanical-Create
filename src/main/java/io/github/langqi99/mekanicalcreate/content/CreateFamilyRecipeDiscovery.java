package io.github.langqi99.mekanicalcreate.content;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

/**
 * Discovers Create-addon processing machines without linking against any
 * particular addon. A mod belongs to the Create family when it directly or
 * transitively depends on Create. Recipe types are then paired with kinetic
 * blocks from the same mod by their registry names.
 */
public final class CreateFamilyRecipeDiscovery {
    private static final Map<RecipeManager, DiscoveryResult> CACHE = new WeakHashMap<>();

    private CreateFamilyRecipeDiscovery() {
    }

    public static synchronized void clearCache() {
        CACHE.clear();
    }

    static List<DynamicModuleProfile> profiles(Level level) {
        RecipeManager manager = level.getRecipeManager();
        synchronized (CreateFamilyRecipeDiscovery.class) {
            return CACHE.computeIfAbsent(manager,
                    CreateFamilyRecipeDiscovery::discover).profiles();
        }
    }

    static Optional<DynamicModuleProfile> profile(Level level, ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return profiles(level).stream()
                .filter(profile -> stack.is(profile.module().getItem()))
                .findFirst();
    }

    private static DiscoveryResult discover(RecipeManager manager) {
        ModFamily family = findCreateFamily();
        List<ModuleCandidate> modules = findModuleCandidates(family.namespaces());
        Map<Item, MutableProfile> profiles = new LinkedHashMap<>();
        List<ResourceLocation> unmatchedTypes = new ArrayList<>();
        List<ResourceLocation> nonTransformingTypes = new ArrayList<>();

        for (RecipeType<?> type : BuiltInRegistries.RECIPE_TYPE) {
            ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(type);
            if (typeId == null || typeId.getNamespace().equals("create")
                    || !family.namespaces().contains(typeId.getNamespace())) {
                continue;
            }
            List<RecipeHolder<?>> recipes = allRecipesFor(manager, type);
            List<Recipe<?>> addonRecipes = new ArrayList<>();
            for (RecipeHolder<?> holder : recipes) {
                addonRecipes.add(holder.value());
            }
            if (addonRecipes.isEmpty()) {
                continue;
            }
            List<Recipe<?>> transformations = addonRecipes.stream()
                    .filter(AddonRecipeIntrospection::hasPotentialItemTransformation)
                    .toList();
            if (transformations.isEmpty()) {
                nonTransformingTypes.add(typeId);
                continue;
            }

            Optional<ModuleCandidate> match = matchModule(typeId, transformations, modules);
            if (match.isEmpty()) {
                unmatchedTypes.add(typeId);
                continue;
            }
            boolean hasItemOnly = transformations.stream()
                    .anyMatch(recipe -> !requiresFluids(recipe));
            boolean hasFluid = transformations.stream()
                    .anyMatch(CreateFamilyRecipeDiscovery::requiresFluids);
            ModuleCandidate module = match.get();
            profiles.computeIfAbsent(module.item(), ignored -> new MutableProfile(module))
                    .bindings.add(new RecipeTypeBinding(typeId, type, hasItemOnly, hasFluid));
        }

        // A few electric Create-addon machines perform capability-based work
        // in addition to their data-driven recipes. Tesla coils, for example,
        // can charge any FE item without a recipe JSON. Keep those modules in
        // the discovered set even when that is their only supported operation.
        modules.stream().filter(ModuleCandidate::chargesItems)
                .forEach(module -> profiles.computeIfAbsent(module.item(),
                        ignored -> new MutableProfile(module)));

        List<DynamicModuleProfile> result = profiles.values().stream()
                .map(MutableProfile::freeze)
                .toList();
        List<String> discoveredBindings = result.stream()
                .map(profile -> profile.moduleId() + " <- "
                        + profile.bindings().stream()
                        .map(binding -> binding.id().toString())
                        .toList()
                        + " @ " + (profile.kinetic()
                        ? StressEnergyConverter.energyPerTick(profile.module(), 1) + " FE/t"
                        : "native FE")
                        + (profile.chargesItems() ? " + item charging" : ""))
                .toList();
        MekanicalCreate.LOGGER.info(
                "Create-family discovery found mods {}, dynamic bindings {}, unmatched processing types {} "
                        + "and ignored non-transforming types {}",
                family.modIds(), discoveredBindings,
                unmatchedTypes, nonTransformingTypes);
        return new DiscoveryResult(result);
    }

    private static ModFamily findCreateFamily() {
        List<IModInfo> mods = ModList.get().getMods();
        Set<String> family = new LinkedHashSet<>();
        family.add("create");
        boolean changed;
        do {
            changed = false;
            for (IModInfo mod : mods) {
                if (family.contains(mod.getModId())) {
                    continue;
                }
                boolean dependsOnFamily = mod.getDependencies().stream()
                        .filter(dependency -> dependency.getType()
                                != IModInfo.DependencyType.INCOMPATIBLE)
                        .filter(dependency -> dependency.getType()
                                != IModInfo.DependencyType.DISCOURAGED)
                        .anyMatch(dependency -> family.contains(dependency.getModId()));
                if (dependsOnFamily) {
                    changed |= family.add(mod.getModId());
                }
            }
        } while (changed);

        Set<String> namespaces = new LinkedHashSet<>(family);
        for (IModInfo mod : mods) {
            if (family.contains(mod.getModId())) {
                namespaces.add(mod.getNamespace());
            }
        }
        return new ModFamily(Set.copyOf(family), Set.copyOf(namespaces));
    }

    private static List<ModuleCandidate> findModuleCandidates(Set<String> familyNamespaces) {
        List<ModuleCandidate> result = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id.getNamespace().equals("create")
                    || !familyNamespaces.contains(id.getNamespace())) {
                continue;
            }
            // Some addons calculate stress in their block entity instead of
            // registering a value in Create's BlockStressValues table. IRotate
            // is therefore the reliable signal that a module is kinetic; the
            // converter resolves the actual stress value separately.
            boolean kinetic = block instanceof IRotate;
            Set<Class<?>> referencedRecipes = referencedProcessingRecipes(block);
            boolean chargesItems = supportsItemCharging(block);
            if (!kinetic && referencedRecipes.isEmpty() && !chargesItems) {
                continue;
            }
            Item item = block.asItem();
            if (item instanceof BlockItem) {
                result.add(new ModuleCandidate(id, item, kinetic, chargesItems,
                        referencedRecipes));
            }
        }
        return result;
    }

    private static boolean supportsItemCharging(Block block) {
        if (!(block instanceof EntityBlock entityBlock)) {
            return false;
        }
        try {
            BlockEntity blockEntity = entityBlock.newBlockEntity(
                    BlockPos.ZERO, block.defaultBlockState());
            if (blockEntity == null) {
                return false;
            }
            for (Class<?> type = blockEntity.getClass(); type != null
                    && type != BlockEntity.class; type = type.getSuperclass()) {
                for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                    String name = method.getName().toLowerCase(Locale.ROOT);
                    if (name.contains("charge") && Arrays.stream(method.getParameterTypes())
                            .anyMatch(ItemStack.class::isAssignableFrom)) {
                        return true;
                    }
                }
            }
        } catch (LinkageError | RuntimeException exception) {
            MekanicalCreate.LOGGER.debug(
                    "Could not inspect item charging support for {}",
                    BuiltInRegistries.BLOCK.getKey(block), exception);
        }
        return false;
    }

    private static Optional<ModuleCandidate> matchModule(
            ResourceLocation recipeType, List<Recipe<?>> recipes,
            List<ModuleCandidate> modules) {
        int bestScore = 0;
        ModuleCandidate best = null;
        boolean tied = false;
        for (ModuleCandidate module : modules) {
            if (!module.id().getNamespace().equals(recipeType.getNamespace())) {
                continue;
            }
            int score = similarity(recipeType.getPath(), module.id().getPath());
            if (recipes.stream().map(Object::getClass)
                    .anyMatch(module.referencedRecipes()::contains)) {
                // A block entity holding this exact recipe class is much
                // stronger evidence than registry-name similarity. This maps
                // names such as charging -> tesla_coil without addon-specific
                // aliases.
                score += 10_000;
            }
            if (score > bestScore) {
                bestScore = score;
                best = module;
                tied = false;
            } else if (score > 0 && score == bestScore) {
                tied = true;
            }
        }
        return bestScore > 0 && !tied ? Optional.of(best) : Optional.empty();
    }

    private static Set<Class<?>> referencedProcessingRecipes(Block block) {
        if (!(block instanceof EntityBlock entityBlock)) {
            return Set.of();
        }
        try {
            BlockEntity blockEntity = entityBlock.newBlockEntity(
                    BlockPos.ZERO, block.defaultBlockState());
            if (blockEntity == null) {
                return Set.of();
            }
            Set<Class<?>> result = new LinkedHashSet<>();
            for (Class<?> type = blockEntity.getClass(); type != null
                    && type != BlockEntity.class; type = type.getSuperclass()) {
                Arrays.stream(type.getDeclaredFields())
                        .forEach(field -> collectProcessingRecipeTypes(
                                field.getGenericType(), result));
                Arrays.stream(type.getDeclaredMethods()).forEach(method -> {
                    collectProcessingRecipeTypes(method.getGenericReturnType(), result);
                    Arrays.stream(method.getGenericParameterTypes())
                            .forEach(parameter -> collectProcessingRecipeTypes(
                                    parameter, result));
                });
            }
            return Set.copyOf(result);
        } catch (LinkageError | RuntimeException exception) {
            MekanicalCreate.LOGGER.debug(
                    "Could not inspect block entity recipe references for {}",
                    BuiltInRegistries.BLOCK.getKey(block), exception);
            return Set.of();
        }
    }

    private static void collectProcessingRecipeTypes(Type type, Set<Class<?>> result) {
        if (type instanceof Class<?> clazz) {
            if (Recipe.class.isAssignableFrom(clazz)
                    && clazz != Recipe.class
                    && clazz != ProcessingRecipe.class) {
                result.add(clazz);
            }
        } else if (type instanceof ParameterizedType parameterized) {
            collectProcessingRecipeTypes(parameterized.getRawType(), result);
            for (Type argument : parameterized.getActualTypeArguments()) {
                collectProcessingRecipeTypes(argument, result);
            }
        } else if (type instanceof GenericArrayType array) {
            collectProcessingRecipeTypes(array.getGenericComponentType(), result);
        } else if (type instanceof WildcardType wildcard) {
            Arrays.stream(wildcard.getUpperBounds())
                    .forEach(bound -> collectProcessingRecipeTypes(bound, result));
            Arrays.stream(wildcard.getLowerBounds())
                    .forEach(bound -> collectProcessingRecipeTypes(bound, result));
        } else if (type instanceof TypeVariable<?> variable) {
            Arrays.stream(variable.getBounds())
                    .forEach(bound -> collectProcessingRecipeTypes(bound, result));
        }
    }

    static int similarity(String recipeTypePath, String blockPath) {
        Set<String> recipeTokens = tokens(recipeTypePath);
        Set<String> blockTokens = tokens(blockPath);
        Set<String> shared = new HashSet<>(recipeTokens);
        shared.retainAll(blockTokens);
        if (shared.isEmpty()) {
            return 0;
        }
        int score = shared.size() * 100;
        String recipe = String.join("_", recipeTokens);
        String block = String.join("_", blockTokens);
        if (recipe.equals(block)) {
            score += 500;
        } else if (block.contains(recipe) || recipe.contains(block)) {
            score += 250;
        }
        return score;
    }

    private static Set<String> tokens(String path) {
        Set<String> result = new LinkedHashSet<>();
        for (String raw : path.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (raw.isBlank() || raw.equals("mechanical") || raw.equals("kinetic")
                    || raw.equals("machine") || raw.equals("block")) {
                continue;
            }
            result.add(stem(raw));
        }
        return result;
    }

    private static String stem(String token) {
        if (token.endsWith("ing") && token.length() > 5) {
            String stem = token.substring(0, token.length() - 3);
            if (stem.endsWith("ll")) {
                stem = stem.substring(0, stem.length() - 1);
            }
            return stem;
        }
        if (token.endsWith("er") && token.length() > 4) {
            return token.substring(0, token.length() - 2);
        }
        if (token.endsWith("ed") && token.length() > 4) {
            return token.substring(0, token.length() - 2);
        }
        return token;
    }

    private static boolean requiresFluids(Recipe<?> recipe) {
        return recipe instanceof ProcessingRecipe<?, ?> processing
                && (!processing.getFluidIngredients().isEmpty()
                || !processing.getFluidResults().isEmpty());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static List<RecipeHolder<?>> allRecipesFor(RecipeManager manager, RecipeType<?> type) {
        return (List) manager.getAllRecipesFor((RecipeType) type);
    }

    record RecipeTypeBinding(ResourceLocation id, RecipeType<?> type,
                             boolean hasItemOnlyRecipes, boolean hasFluidRecipes) {
    }

    record DynamicModuleProfile(ResourceLocation moduleId, ItemStack module,
                                boolean kinetic, boolean chargesItems,
                                List<RecipeTypeBinding> bindings) {
        DynamicModuleProfile {
            module = module.copyWithCount(1);
            bindings = List.copyOf(bindings);
        }

        boolean supports(boolean allowFluidProcessing) {
            return chargesItems || bindings.stream().anyMatch(binding -> binding.hasItemOnlyRecipes()
                    || allowFluidProcessing && binding.hasFluidRecipes());
        }
    }

    private record ModuleCandidate(ResourceLocation id, Item item, boolean kinetic,
                                   boolean chargesItems,
                                   Set<Class<?>> referencedRecipes) {
    }

    private record ModFamily(Set<String> modIds, Set<String> namespaces) {
    }

    private record DiscoveryResult(List<DynamicModuleProfile> profiles) {
        DiscoveryResult {
            profiles = List.copyOf(profiles);
        }
    }

    private static final class MutableProfile {
        private final ModuleCandidate module;
        private final List<RecipeTypeBinding> bindings = new ArrayList<>();

        private MutableProfile(ModuleCandidate module) {
            this.module = module;
        }

        private DynamicModuleProfile freeze() {
            return new DynamicModuleProfile(module.id(), new ItemStack(module.item()),
                    module.kinetic(), module.chargesItems(), bindings);
        }
    }
}
