package io.github.langqi99.mekanicalcreate.content;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.crusher.CrushingRecipe;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.ManualApplicationRecipe;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.kinetics.fan.processing.HauntingRecipe;
import com.simibubi.create.content.kinetics.fan.processing.SplashingRecipe;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.kinetics.mixer.CompactingRecipe;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.fluids.transfer.EmptyingRecipe;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiFunction;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.fluid.IExtendedFluidTank;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * Resolves the unordered item pool into one deterministic operation. Inventory
 * size affects conflict resolution, but an execution plan always consumes one
 * recipe's worth of materials.
 */
public final class SimulationRecipeResolver {
    private static final int DEFAULT_DURATION = 100;
    private static final int MAX_NATIVE_CHARGING_DURATION = 1_000_000;
    private static final int RESOLUTION_CACHE_SIZE = 1_024;
    private static final Map<RecipeManager, Map<CandidateCacheKey, CandidateCatalog>> CANDIDATE_CACHE =
            new WeakHashMap<>();
    private static final Map<RecipeManager,
            BoundedLruCache<ResolutionCacheKey, CachedResolution>> RESOLUTION_CACHE =
            new WeakHashMap<>();
    private static final ResolverDiagnostics DIAGNOSTICS = new ResolverDiagnostics();
    private static long cacheEpoch;

    private SimulationRecipeResolver() {
    }

    public static synchronized void clearCache() {
        CANDIDATE_CACHE.clear();
        RESOLUTION_CACHE.clear();
        cacheEpoch++;
    }

    static synchronized long cacheEpoch() {
        return cacheEpoch;
    }

    /** Builds shared catalogs once after a datapack reload. */
    public static void prewarm(Level level) {
        List<ItemStack> commonModules = List.of(
                AllBlocks.DEPLOYER.asStack(), AllBlocks.MECHANICAL_PRESS.asStack(),
                AllBlocks.MECHANICAL_SAW.asStack(), AllBlocks.MILLSTONE.asStack(),
                AllBlocks.CRUSHING_WHEEL.asStack(), AllBlocks.MECHANICAL_CRAFTER.asStack());
        for (ItemStack module : commonModules) {
            prewarmCatalog(level, module, ItemStack.EMPTY, false);
            prewarmCatalog(level, module, ItemStack.EMPTY, true);
        }
        for (ItemStack condition : List.of(
                new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.SOUL_CAMPFIRE),
                new ItemStack(Items.CAMPFIRE), new ItemStack(Items.LAVA_BUCKET))) {
            prewarmCatalog(level, AllBlocks.ENCASED_FAN.asStack(), condition, false);
            prewarmCatalog(level, AllBlocks.ENCASED_FAN.asStack(), condition, true);
        }
        for (ItemStack module : List.of(AllBlocks.MECHANICAL_MIXER.asStack(),
                AllBlocks.SPOUT.asStack(), AllBlocks.ITEM_DRAIN.asStack())) {
            prewarmCatalog(level, module, ItemStack.EMPTY, true);
        }
        for (CreateFamilyRecipeDiscovery.DynamicModuleProfile profile
                : CreateFamilyRecipeDiscovery.profiles(level)) {
            if (profile.supports(false)) {
                prewarmCatalog(level, profile.module(), ItemStack.EMPTY, false);
            }
            if (profile.supports(true)) {
                prewarmCatalog(level, profile.module(), ItemStack.EMPTY, true);
            }
        }
    }

    private static void prewarmCatalog(Level level, ItemStack module, ItemStack condition,
                                       boolean allowFluidProcessing) {
        try {
            candidateCatalog(level, module, condition, allowFluidProcessing);
        } catch (LinkageError | RuntimeException exception) {
            MekanicalCreate.LOGGER.warn("Could not prewarm recipe catalog for {}",
                    BuiltInRegistries.ITEM.getKey(module.getItem()), exception);
        }
    }

    public static DiagnosticsSnapshot diagnostics() {
        return DIAGNOSTICS.snapshot();
    }

    public static void resetDiagnostics() {
        DIAGNOSTICS.reset();
    }

    static void recordDebounceDeferral() {
        DIAGNOSTICS.debounceDeferrals.increment();
    }

    static void recordReloadDeferral() {
        DIAGNOSTICS.reloadDeferrals.increment();
    }

    static Optional<ExecutionPlan> resolve(Level level, ItemStack module, ItemStack condition,
                                           List<? extends IInventorySlot> inputSlots,
                                           List<? extends IExtendedFluidTank> inputFluidTanks,
                                           boolean allowFluidProcessing) {
        if (module.isEmpty() || !isSupportedModule(level, module, allowFluidProcessing)) {
            return Optional.empty();
        }
        List<ItemStack> inventory = inputSlots.stream().map(IInventorySlot::getStack).toList();
        List<Candidate> candidates = new ArrayList<>(candidateCatalog(
                level, module, condition, allowFluidProcessing).candidatesFor(inventory));
        addNativeItemChargingCandidates(candidates, level, module,
                inventory);
        List<ItemStack> contextStacks = condition.isEmpty()
                ? List.of(module) : List.of(module, condition);
        return resolve(level, inputSlots, inputFluidTanks, inventory, candidates,
                new ResolverContext(ItemPoolKey.create(contextStacks), allowFluidProcessing));
    }

    /**
     * Resolves a multiblock recipe from an unordered pool of configuration
     * catalysts. Every slot may contain either a machine module or a fan
     * condition; unsupported items simply do not contribute candidates.
     */
    static Optional<ExecutionPlan> resolve(Level level,
                                           List<? extends IInventorySlot> catalystSlots,
                                           List<? extends IInventorySlot> inputSlots,
                                           List<? extends IExtendedFluidTank> inputFluidTanks,
                                           boolean allowFluidProcessing) {
        List<ItemStack> catalysts = distinctStacks(catalystSlots.stream()
                .map(IInventorySlot::getStack)
                .filter(stack -> !stack.isEmpty())
                .toList());
        List<ItemStack> conditions = catalysts.stream()
                .filter(SimulationRecipeResolver::isSupportedCondition)
                .toList();
        List<Candidate> candidates = new ArrayList<>();
        List<ItemStack> inventory = inputSlots.stream().map(IInventorySlot::getStack).toList();
        for (ItemStack module : catalysts) {
            if (!isSupportedModule(level, module, allowFluidProcessing)) {
                continue;
            }
            if (module.is(AllBlocks.ENCASED_FAN.asItem())) {
                for (ItemStack condition : conditions) {
                    candidates.addAll(candidateCatalog(level, module, condition,
                            allowFluidProcessing).candidatesFor(inventory));
                }
            } else {
                candidates.addAll(candidateCatalog(level, module, ItemStack.EMPTY,
                        allowFluidProcessing).candidatesFor(inventory));
            }
            addNativeItemChargingCandidates(candidates, level, module,
                    inventory);
        }
        return resolve(level, inputSlots, inputFluidTanks, inventory, candidates,
                new ResolverContext(ItemPoolKey.create(catalysts), allowFluidProcessing));
    }

    private static Optional<ExecutionPlan> resolve(Level level,
                                                   List<? extends IInventorySlot> inputSlots,
                                                   List<? extends IExtendedFluidTank> inputFluidTanks,
                                                   List<ItemStack> inventory,
                                                   List<Candidate> candidates,
                                                   ResolverContext context) {
        List<FluidStack> fluids = inputFluidTanks.stream().map(tank -> tank.getFluid().copy()).toList();
        int totalItems = inventory.stream().mapToInt(ItemStack::getCount).sum();
        int totalFluid = fluids.stream().mapToInt(FluidStack::getAmount).sum();
        ResolutionCacheKey cacheKey = new ResolutionCacheKey(context,
                ItemPoolKey.create(inventory), FluidPoolKey.create(fluids));
        CachedResolution cached = getCachedResolution(level.getRecipeManager(), cacheKey);
        if (cached != null) {
            DIAGNOSTICS.resolutionCacheHits.increment();
            if (cached.candidate == null) {
                return Optional.empty();
            }
            CandidateMatch cachedMatch = match(cached.candidate, inventory, fluids,
                    totalItems, totalFluid).orElse(null);
            if (cachedMatch != null) {
                return Optional.of(createPlan(level, cachedMatch, cached.exclusiveMatch));
            }
            // A defensive fallback for unusual mutable item capabilities. The
            // exact signature should normally make this path unreachable.
            removeCachedResolution(level.getRecipeManager(), cacheKey);
        }
        DIAGNOSTICS.resolutionCacheMisses.increment();
        DIAGNOSTICS.fullSearches.increment();
        long searchStarted = System.nanoTime();
        CandidateMatch best = null;
        Set<ResourceLocation> matchedRecipes = new LinkedHashSet<>();
        for (Candidate candidate : candidates) {
            DIAGNOSTICS.candidatesConsidered.increment();
            if (!canPossiblyMatch(candidate, inventory, fluids, totalItems, totalFluid)) {
                DIAGNOSTICS.quickRejects.increment();
                continue;
            }
            DIAGNOSTICS.exactChecks.increment();
            CandidateMatch match = match(candidate, inventory, fluids, totalItems, totalFluid)
                    .orElse(null);
            if (match == null) {
                continue;
            }
            matchedRecipes.add(candidate.id);
            if (best == null || CandidateMatch.ORDER.compare(match, best) > 0) {
                best = match;
            }
        }
        if (best == null) {
            putCachedResolution(level.getRecipeManager(), cacheKey,
                    CachedResolution.NO_MATCH);
            DIAGNOSTICS.noMatches.increment();
            DIAGNOSTICS.recordSearchTime(System.nanoTime() - searchStarted);
            return Optional.empty();
        }
        boolean exclusiveMatch = matchedRecipes.size() == 1;
        putCachedResolution(level.getRecipeManager(), cacheKey,
                new CachedResolution(best.candidate, exclusiveMatch));
        DIAGNOSTICS.recordSearchTime(System.nanoTime() - searchStarted);
        return Optional.of(createPlan(level, best, exclusiveMatch));
    }

    @Nullable
    private static synchronized CachedResolution getCachedResolution(
            RecipeManager manager, ResolutionCacheKey key) {
        BoundedLruCache<ResolutionCacheKey, CachedResolution> cache = RESOLUTION_CACHE.get(manager);
        return cache == null ? null : cache.get(key);
    }

    private static synchronized void putCachedResolution(RecipeManager manager,
                                                         ResolutionCacheKey key,
                                                         CachedResolution value) {
        RESOLUTION_CACHE.computeIfAbsent(manager,
                        ignored -> new BoundedLruCache<>(RESOLUTION_CACHE_SIZE))
                .put(key, value);
    }

    private static synchronized void removeCachedResolution(RecipeManager manager,
                                                            ResolutionCacheKey key) {
        BoundedLruCache<ResolutionCacheKey, CachedResolution> cache = RESOLUTION_CACHE.get(manager);
        if (cache != null) {
            cache.remove(key);
        }
    }

    private static ExecutionPlan createPlan(Level level, CandidateMatch selected,
                                            boolean exclusiveMatch) {
        List<ItemStack> results = selected.candidate.resultFactory.apply(level, selected.match);
        return new ExecutionPlan(selected.candidate.id, selected.candidate.duration,
                selected.candidate.energyPerTick,
                selected.match.stackUses, selected.match.catalystUses,
                selected.match.fluidUses, results, selected.candidate.fluidOutputs,
                selected.candidate, exclusiveMatch);
    }

    private static boolean canPossiblyMatch(Candidate candidate, List<ItemStack> inventory,
                                            List<FluidStack> fluids, int totalItems,
                                            int totalFluid) {
        if (totalItems < candidate.consumedPerRun()
                || totalFluid < candidate.fluidConsumedPerRun()) {
            return false;
        }
        for (Requirement requirement : candidate.requirements) {
            int available = 0;
            for (ItemStack stack : inventory) {
                if (!stack.isEmpty() && requirement.test(stack)) {
                    available += requirement.consumed ? stack.getCount() : 1;
                    if (available >= requirement.count) {
                        break;
                    }
                }
            }
            if (available < requirement.count) {
                return false;
            }
        }
        for (FluidRequirement requirement : candidate.fluidRequirements) {
            int available = 0;
            for (FluidStack stack : fluids) {
                if (!stack.isEmpty() && requirement.ingredient.ingredient().test(stack)) {
                    available += stack.getAmount();
                    if (available >= requirement.ingredient.amount()) {
                        break;
                    }
                }
            }
            if (available < requirement.ingredient.amount()) {
                return false;
            }
        }
        return true;
    }

    private static List<ItemStack> distinctStacks(List<ItemStack> stacks) {
        List<ItemStack> distinct = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (distinct.stream().noneMatch(existing ->
                    ItemStack.isSameItemSameComponents(existing, stack))) {
                distinct.add(stack.copyWithCount(1));
            }
        }
        return distinct;
    }

    static boolean isSupportedModule(Level level, ItemStack stack,
                                     boolean allowFluidProcessing) {
        boolean common = stack.is(AllBlocks.DEPLOYER.asItem())
                || stack.is(AllBlocks.MECHANICAL_SAW.asItem())
                || stack.is(AllBlocks.MECHANICAL_PRESS.asItem())
                || stack.is(AllBlocks.MILLSTONE.asItem())
                || stack.is(AllBlocks.CRUSHING_WHEEL.asItem())
                || stack.is(AllBlocks.ENCASED_FAN.asItem())
                || stack.is(AllBlocks.MECHANICAL_CRAFTER.asItem());
        boolean fluid = allowFluidProcessing && (stack.is(AllBlocks.MECHANICAL_MIXER.asItem())
                || stack.is(AllBlocks.SPOUT.asItem())
                || stack.is(AllBlocks.ITEM_DRAIN.asItem()));
        return common || fluid || CreateFamilyRecipeDiscovery.profile(level, stack)
                .filter(profile -> profile.supports(allowFluidProcessing))
                .isPresent();
    }

    private static boolean isSupportedCondition(ItemStack stack) {
        return stack.is(Items.LAVA_BUCKET)
                || stack.is(Items.WATER_BUCKET)
                || stack.is(Items.SOUL_CAMPFIRE)
                || stack.is(Items.CAMPFIRE);
    }

    /**
     * The public JEI view is generated from the exact candidates used by the
     * server-side resolver. Module and condition items are configuration
     * catalysts, while {@link DisplayInput#consumed()} controls pattern inputs.
     */
    public static List<DisplayRecipe> getDisplayRecipes(Level level, boolean allowFluidProcessing) {
        List<DisplayRecipe> result = new ArrayList<>();
        List<ItemStack> modules = new ArrayList<>(List.of(
                AllBlocks.DEPLOYER.asStack(),
                AllBlocks.MECHANICAL_PRESS.asStack(),
                AllBlocks.MECHANICAL_SAW.asStack(),
                AllBlocks.MILLSTONE.asStack(),
                AllBlocks.CRUSHING_WHEEL.asStack(),
                AllBlocks.MECHANICAL_CRAFTER.asStack()));
        if (allowFluidProcessing) {
            modules.add(AllBlocks.MECHANICAL_MIXER.asStack());
            modules.add(AllBlocks.SPOUT.asStack());
            modules.add(AllBlocks.ITEM_DRAIN.asStack());
        }
        for (ItemStack module : modules) {
            appendDisplayRecipes(result, collectCandidates(level, module, ItemStack.EMPTY,
                    allowFluidProcessing), module, ItemStack.EMPTY);
        }
        ItemStack fan = AllBlocks.ENCASED_FAN.asStack();
        for (ItemStack condition : List.of(
                new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.SOUL_CAMPFIRE),
                new ItemStack(Items.CAMPFIRE), new ItemStack(Items.LAVA_BUCKET))) {
            appendDisplayRecipes(result, collectCandidates(level, fan, condition,
                    allowFluidProcessing), fan, condition);
        }
        for (CreateFamilyRecipeDiscovery.DynamicModuleProfile profile
                : CreateFamilyRecipeDiscovery.profiles(level)) {
            if (profile.supports(allowFluidProcessing)) {
                List<Candidate> candidates = new ArrayList<>(collectCandidates(
                        level, profile.module(), ItemStack.EMPTY,
                        allowFluidProcessing));
                if (profile.chargesItems()) {
                    addNativeItemChargingCandidates(candidates, level,
                            profile.module(), BuiltInRegistries.ITEM.stream()
                                    .map(item -> item.getDefaultInstance())
                                    .filter(stack -> !stack.isEmpty())
                                    .toList());
                }
                appendDisplayRecipes(result,
                        candidates,
                        profile.module(), ItemStack.EMPTY);
            }
        }
        return List.copyOf(result);
    }

    private static void addNativeItemChargingCandidates(
            List<Candidate> target, Level level, ItemStack module,
            List<ItemStack> availableStacks) {
        Optional<CreateFamilyRecipeDiscovery.DynamicModuleProfile> profile =
                CreateFamilyRecipeDiscovery.profile(level, module);
        if (profile.isEmpty() || !profile.get().chargesItems()) {
            return;
        }
        long moduleRate = NativeRecipeEnergy.itemChargingRate(module);
        for (ItemStack input : distinctStacks(availableStacks.stream()
                .filter(stack -> !stack.isEmpty()).toList())) {
            EnergyChargePlan charge = energyChargePlan(input, moduleRate);
            if (charge == null) {
                continue;
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(input.getItem());
            String suffix = "native_item_charging_"
                    + profile.get().moduleId().getNamespace() + "_"
                    + profile.get().moduleId().getPath().replace('/', '_');
            Requirement requirement = new Requirement(Ingredient.of(input.getItem()),
                    1, true, 0, input.copyWithCount(1));
            target.add(new Candidate(derivedId(itemId, suffix),
                    "native_item_charging", List.of(requirement), List.of(),
                    List.of(new DisplayOutput(charge.output(), 1)), List.of(),
                    0, 1, 1, 60, charge.duration(),
                    (recipeLevel, match) -> List.of(charge.output().copy()),
                    charge.energyPerTick()));
        }
    }

    @Nullable
    private static EnergyChargePlan energyChargePlan(ItemStack source,
                                                     long requestedRate) {
        ItemStack output = source.copyWithCount(1);
        IEnergyStorage storage = output.getCapability(Capabilities.EnergyStorage.ITEM);
        if (storage == null || !storage.canReceive()) {
            return null;
        }
        int stored = storage.getEnergyStored();
        int maximum = storage.getMaxEnergyStored();
        if (maximum <= stored) {
            return null;
        }
        int request = (int) Math.min(Integer.MAX_VALUE, Math.max(1, requestedRate));
        int acceptedPerTick = storage.receiveEnergy(request, true);
        if (acceptedPerTick <= 0) {
            return null;
        }
        long missing = (long) maximum - stored;
        long requiredTicks = 1L + (missing - 1L) / acceptedPerTick;
        if (requiredTicks > MAX_NATIVE_CHARGING_DURATION) {
            return null;
        }
        int duration = (int) requiredTicks;
        long received = 0;
        for (int tick = 0; tick < duration; tick++) {
            int accepted = storage.receiveEnergy(request, false);
            if (accepted <= 0) {
                break;
            }
            received += accepted;
        }
        if (received <= 0) {
            return null;
        }
        int actualDuration = (int) Math.min(Integer.MAX_VALUE,
                1L + (received - 1L) / acceptedPerTick);
        long energyPerTick = 1L + (received - 1L) / actualDuration;
        return new EnergyChargePlan(output, actualDuration, energyPerTick);
    }

    private static void appendDisplayRecipes(List<DisplayRecipe> target, List<Candidate> candidates,
                                             ItemStack module, ItemStack condition) {
        ResourceLocation moduleId = BuiltInRegistries.ITEM.getKey(module.getItem());
        String variant = moduleId.getNamespace() + "_" + moduleId.getPath().replace('/', '_');
        if (!condition.isEmpty()) {
            ResourceLocation conditionId = BuiltInRegistries.ITEM.getKey(condition.getItem());
            variant += "_" + conditionId.getNamespace() + "_" + conditionId.getPath().replace('/', '_');
        }
        for (Candidate candidate : candidates) {
            ResourceLocation displayId = ResourceLocation.fromNamespaceAndPath(candidate.id.getNamespace(),
                    candidate.id.getPath() + "/" + variant);
            List<DisplayInput> inputs = mergeDisplayInputs(candidate.requirements);
            String processKey = "jei.mekanicalcreate.process." + candidate.process;
            target.add(new DisplayRecipe(displayId,
                    Component.translatableWithFallback(processKey,
                            humanize(candidate.process)),
                    module, condition, inputs, candidate.displayOutputs,
                    candidate.fluidRequirements.stream().map(FluidRequirement::ingredient).toList(),
                    candidate.displayFluidOutputs(),
                    candidate.sequenceSteps, candidate.loops));
        }
    }

    private static String humanize(String value) {
        String[] words = value.replace('/', '_').split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank() || word.equals("dynamic")) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static List<DisplayInput> mergeDisplayInputs(List<Requirement> requirements) {
        List<DisplayInput> merged = new ArrayList<>();
        for (Requirement requirement : requirements) {
            int existingIndex = -1;
            for (int index = 0; index < merged.size(); index++) {
                DisplayInput existing = merged.get(index);
                if (existing.consumed == requirement.consumed
                        && sameIngredient(existing.ingredient, requirement.ingredient)) {
                    existingIndex = index;
                    break;
                }
            }
            if (existingIndex < 0) {
                merged.add(new DisplayInput(requirement.ingredient, requirement.count,
                        requirement.consumed));
            } else {
                DisplayInput existing = merged.get(existingIndex);
                int count = requirement.consumed
                        ? existing.count + requirement.count
                        : Math.max(existing.count, requirement.count);
                merged.set(existingIndex, new DisplayInput(existing.ingredient, count,
                        existing.consumed));
            }
        }
        return List.copyOf(merged);
    }

    private static boolean sameIngredient(Ingredient first, Ingredient second) {
        ItemStack[] firstItems = first.getItems();
        ItemStack[] secondItems = second.getItems();
        if (firstItems.length != secondItems.length) {
            return false;
        }
        boolean[] matched = new boolean[secondItems.length];
        for (ItemStack firstItem : firstItems) {
            boolean found = false;
            for (int index = 0; index < secondItems.length; index++) {
                if (!matched[index]
                        && ItemStack.isSameItemSameComponents(firstItem, secondItems[index])) {
                    matched[index] = true;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static List<Candidate> collectCandidates(Level level, ItemStack module,
                                                     ItemStack condition,
                                                     boolean allowFluidProcessing) {
        return candidateCatalog(level, module, condition, allowFluidProcessing).all();
    }

    private static CandidateCatalog candidateCatalog(Level level, ItemStack module,
                                                     ItemStack condition,
                                                     boolean allowFluidProcessing) {
        RecipeManager manager = level.getRecipeManager();
        CandidateCacheKey key = new CandidateCacheKey(module.getItem(),
                condition.getItem(), allowFluidProcessing);
        synchronized (SimulationRecipeResolver.class) {
            Map<CandidateCacheKey, CandidateCatalog> managerCache =
                    CANDIDATE_CACHE.computeIfAbsent(manager,
                            ignored -> new HashMap<>());
            return managerCache.computeIfAbsent(key,
                    ignored -> {
                        DIAGNOSTICS.catalogBuilds.increment();
                        return CandidateCatalog.create(buildCandidates(
                                level, module.copyWithCount(1),
                                condition.copyWithCount(condition.isEmpty() ? 0 : 1),
                                allowFluidProcessing));
                    });
        }
    }

    private static List<Candidate> buildCandidates(Level level, ItemStack module,
                                                   ItemStack condition,
                                                   boolean allowFluidProcessing) {
        List<Candidate> candidates = new ArrayList<>();
        RecipeManager recipes = level.getRecipeManager();

        if (module.is(AllBlocks.DEPLOYER.asItem())) {
            RecipeType<DeployerApplicationRecipe> type = AllRecipeTypes.DEPLOYING.getType();
            addProcessing(candidates, recipes.getAllRecipesFor(type),
                    "deploying", 20, true);
            RecipeType<ManualApplicationRecipe> applicationType = AllRecipeTypes.ITEM_APPLICATION.getType();
            addProcessing(candidates, recipes.getAllRecipesFor(applicationType).stream()
                            .map(ManualApplicationRecipe::asDeploying)
                            .toList(),
                    "deploying", 20, true);
        } else if (module.is(AllBlocks.MECHANICAL_PRESS.asItem())) {
            RecipeType<PressingRecipe> type = AllRecipeTypes.PRESSING.getType();
            addProcessing(candidates, recipes.getAllRecipesFor(type),
                    "pressing", 20, false);
            if (allowFluidProcessing) {
                RecipeType<CompactingRecipe> compacting = AllRecipeTypes.COMPACTING.getType();
                addProcessing(candidates, recipes.getAllRecipesFor(compacting),
                        "compacting", 35, false);
            }
        } else if (module.is(AllBlocks.MECHANICAL_SAW.asItem())) {
            RecipeType<CuttingRecipe> type = AllRecipeTypes.CUTTING.getType();
            addProcessing(candidates, recipes.getAllRecipesFor(type),
                    "cutting", 20, false);
        } else if (module.is(AllBlocks.MILLSTONE.asItem())) {
            RecipeType<MillingRecipe> type = AllRecipeTypes.MILLING.getType();
            addProcessing(candidates, recipes.getAllRecipesFor(type),
                    "milling", 10, false);
        } else if (module.is(AllBlocks.CRUSHING_WHEEL.asItem())) {
            RecipeType<CrushingRecipe> type = AllRecipeTypes.CRUSHING.getType();
            addProcessing(candidates, recipes.getAllRecipesFor(type),
                    "crushing", 10, false);
        } else if (module.is(AllBlocks.ENCASED_FAN.asItem())) {
            addFan(candidates, recipes, condition);
        } else if (module.is(AllBlocks.MECHANICAL_CRAFTER.asItem())) {
            addCrafting(candidates, recipes.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING),
                    "crafting", 10, level);
            RecipeType<CraftingRecipe> type = AllRecipeTypes.MECHANICAL_CRAFTING.getType();
            addCrafting(candidates, recipes.getAllRecipesFor(type),
                    "mechanical_crafting", 20, level);
        } else if (allowFluidProcessing && module.is(AllBlocks.MECHANICAL_MIXER.asItem())) {
            RecipeType<MixingRecipe> type = AllRecipeTypes.MIXING.getType();
            addProcessing(candidates, recipes.getAllRecipesFor(type),
                    "mixing", 35, false);
        } else if (allowFluidProcessing && module.is(AllBlocks.SPOUT.asItem())) {
            RecipeType<FillingRecipe> type = AllRecipeTypes.FILLING.getType();
            addProcessing(candidates, recipes.getAllRecipesFor(type),
                    "filling", 35, false);
        } else if (allowFluidProcessing && module.is(AllBlocks.ITEM_DRAIN.asItem())) {
            RecipeType<EmptyingRecipe> type = AllRecipeTypes.EMPTYING.getType();
            addProcessing(candidates, recipes.getAllRecipesFor(type),
                    "emptying", 35, false);
        } else {
            CreateFamilyRecipeDiscovery.profile(level, module)
                    .ifPresent(profile -> addDynamicProcessing(candidates, level,
                            recipes, profile, allowFluidProcessing));
        }
        addSequenced(candidates, level, recipes, module, allowFluidProcessing);
        return candidates.stream()
                .map(candidate -> candidate.energyPerTick > 0
                        ? candidate
                        : candidate.withEnergyPerTick(
                        StressEnergyConverter.energyPerTick(module,
                                candidate.complexity)))
                .toList();
    }

    private static void addDynamicProcessing(
            List<Candidate> target, Level level, RecipeManager manager,
            CreateFamilyRecipeDiscovery.DynamicModuleProfile profile,
            boolean allowFluidProcessing) {
        for (CreateFamilyRecipeDiscovery.RecipeTypeBinding binding : profile.bindings()) {
            for (RecipeHolder<?> holder
                    : CreateFamilyRecipeDiscovery.allRecipesFor(manager, binding.type())) {
                if (!AllRecipeTypes.CAN_BE_AUTOMATED.test(holder)) {
                    continue;
                }
                Recipe<?> recipe = holder.value();
                ProcessingRecipe<?, ?> processing = recipe instanceof ProcessingRecipe<?, ?> value
                        ? value : null;
                boolean requiresFluids = processing != null
                        && (!processing.getFluidIngredients().isEmpty()
                        || !processing.getFluidResults().isEmpty());
                if (requiresFluids && !allowFluidProcessing) {
                    continue;
                }
                List<Requirement> requirements = new ArrayList<>();
                for (int index = 0; index < recipe.getIngredients().size(); index++) {
                    Ingredient ingredient = recipe.getIngredients().get(index);
                    if (!ingredient.isEmpty()) {
                        requirements.add(new Requirement(ingredient, 1, true, index));
                    }
                }
                List<FluidRequirement> fluidRequirements = processing == null ? List.of()
                        : processing.getFluidIngredients().stream()
                        .map(FluidRequirement::new).toList();
                if (requirements.isEmpty() && fluidRequirements.isEmpty()) {
                    continue;
                }
                List<ItemStack> itemOutputs = AddonRecipeIntrospection.itemOutputs(recipe, level);
                List<FluidStack> fluidOutputs = processing == null ? List.of()
                        : processing.getFluidResults().stream().map(FluidStack::copy).toList();
                if (itemOutputs.isEmpty() && fluidOutputs.isEmpty()) {
                    continue;
                }
                int duration = processing != null && processing.getProcessingDuration() > 0
                        ? Math.max(20, processing.getProcessingDuration()) : DEFAULT_DURATION;
                long energyPerTick = 0;
                if (!profile.kinetic()) {
                    Optional<NativeRecipeEnergy.EnergyProfile> nativeEnergy =
                            NativeRecipeEnergy.profile(recipe, duration);
                    if (nativeEnergy.isEmpty()) {
                        continue;
                    }
                    duration = nativeEnergy.get().duration();
                    energyPerTick = nativeEnergy.get().energyPerTick();
                }
                String process = "dynamic_" + binding.id().getNamespace() + "_"
                        + binding.id().getPath().replace('/', '_');
                List<DisplayOutput> displayOutputs = processing == null
                        ? itemOutputs.stream().map(stack -> new DisplayOutput(stack, 1)).toList()
                        : displayOutputs(processing.getRollableResults(), false);
                target.add(new Candidate(
                        derivedId(holder.id(), process), process, requirements,
                        fluidRequirements, displayOutputs, fluidOutputs,
                        0, 1, requirements.size(), 50, duration,
                        (recipeLevel, match) -> appendRemainders(
                                processing == null ? itemOutputs
                                        : processing.rollResults(recipeLevel.random), match),
                        energyPerTick));
            }
        }
    }

    private static <R extends ProcessingRecipe<?, ?>> void addProcessing(
            List<Candidate> target, List<RecipeHolder<R>> recipes, String suffix,
            int priority, boolean deployer) {
        for (RecipeHolder<R> holder : recipes) {
            if (!AllRecipeTypes.CAN_BE_AUTOMATED.test(holder)) {
                continue;
            }
            R recipe = holder.value();
            List<Requirement> requirements = new ArrayList<>();
            for (int index = 0; index < recipe.getIngredients().size(); index++) {
                Ingredient ingredient = recipe.getIngredients().get(index);
                if (ingredient.isEmpty()) {
                    continue;
                }
                boolean consumed = !deployer || index == 0
                        || !(recipe instanceof DeployerApplicationRecipe application)
                        || !application.shouldKeepHeldItem();
                requirements.add(new Requirement(ingredient, 1, consumed, index));
            }
            List<FluidRequirement> fluidRequirements = recipe.getFluidIngredients().stream()
                    .map(FluidRequirement::new).toList();
            if (requirements.isEmpty() && fluidRequirements.isEmpty()) {
                continue;
            }
            int duration = recipe.getProcessingDuration() > 0
                    ? Math.max(20, recipe.getProcessingDuration()) : DEFAULT_DURATION;
            target.add(new Candidate(derivedId(holder.id(), suffix), suffix, requirements,
                    fluidRequirements, displayOutputs(recipe.getRollableResults(), false),
                    recipe.getFluidResults().stream().map(FluidStack::copy).toList(), 0, 1,
                    1, priority, duration,
                    (level, match) -> appendRemainders(recipe.rollResults(level.random), match)));
        }
    }

    private static void addSequenced(List<Candidate> target, Level level,
                                     RecipeManager manager, ItemStack selectedModule,
                                     boolean allowFluidProcessing) {
        RecipeType<SequencedAssemblyRecipe> type = AllRecipeTypes.SEQUENCED_ASSEMBLY.getType();
        for (RecipeHolder<SequencedAssemblyRecipe> holder
                : manager.getAllRecipesFor(type)) {
            SequencedAssemblyRecipe recipe = holder.value();
            if (recipe.getSequence().isEmpty() || recipe.resultPool.isEmpty()) {
                continue;
            }
            List<Requirement> requirements = new ArrayList<>();
            requirements.add(new Requirement(recipe.getIngredient(), 1, true, -1));
            boolean containsSelectedModule = false;
            boolean supported = true;
            long sequenceEnergyPerLoop = 0;
            int loops = Math.max(1, recipe.getLoops());
            for (SequencedRecipe<?> step : recipe.getSequence()) {
                ProcessingRecipe<?, ?> processing = step.getRecipe();
                ItemStack stepModule = sequenceModule(level, processing).orElse(ItemStack.EMPTY);
                if (stepModule.isEmpty()) {
                    supported = false;
                    break;
                }
                containsSelectedModule |= ItemStack.isSameItemSameComponents(
                        selectedModule, stepModule);
                sequenceEnergyPerLoop = saturatedAdd(sequenceEnergyPerLoop,
                        sequenceStepEnergy(level, processing, stepModule));

                boolean deployerStep = processing.getType()
                        == AllRecipeTypes.DEPLOYING.getType();
                if (deployerStep && processing.getIngredients().size() < 2) {
                    supported = false;
                    break;
                }
                // The first ingredient is always the transitional assembly
                // item. Any additional ingredients are supplied by the step's
                // machine and must be flattened into the factory input pool.
                for (int ingredientIndex = 1;
                     ingredientIndex < processing.getIngredients().size();
                     ingredientIndex++) {
                    Ingredient ingredient = processing.getIngredients().get(ingredientIndex);
                    if (ingredient.isEmpty()) {
                        continue;
                    }
                    boolean keepHeld = deployerStep
                            && processing instanceof DeployerApplicationRecipe deployer
                            && deployer.shouldKeepHeldItem();
                    requirements.add(new Requirement(ingredient,
                            keepHeld ? 1 : loops, !keepHeld, -1));
                }
                if (!processing.getFluidIngredients().isEmpty()
                        && !allowFluidProcessing) {
                        supported = false;
                        break;
                }
            }
            if (!supported || !containsSelectedModule) {
                continue;
            }
            List<FluidRequirement> fluidRequirements = new ArrayList<>();
            for (SequencedRecipe<?> step : recipe.getSequence()) {
                for (SizedFluidIngredient fluid : step.getRecipe().getFluidIngredients()) {
                    fluidRequirements.add(new FluidRequirement(new SizedFluidIngredient(
                            fluid.ingredient(), Math.multiplyExact(fluid.amount(), loops))));
                }
            }
            target.add(new Candidate(derivedId(holder.id(), "sequenced_assembly"), "sequenced_assembly",
                    requirements, fluidRequirements, displayOutputs(recipe.resultPool, true), List.of(),
                    recipe.getSequence().size(), loops,
                    recipe.getSequence().size() * loops, 100, DEFAULT_DURATION,
                    (recipeLevel, match) -> appendRemainders(
                            List.of(rollWeighted(recipe.resultPool, recipeLevel.random)), match),
                    saturatedMultiply(sequenceEnergyPerLoop, loops)));
        }
    }

    private static long sequenceStepEnergy(Level level,
                                           ProcessingRecipe<?, ?> recipe,
                                           ItemStack module) {
        Optional<CreateFamilyRecipeDiscovery.DynamicModuleProfile> profile =
                CreateFamilyRecipeDiscovery.profile(level, module);
        if (profile.isPresent() && !profile.get().kinetic()) {
            Optional<NativeRecipeEnergy.EnergyProfile> nativeEnergy =
                    NativeRecipeEnergy.profile(recipe, DEFAULT_DURATION);
            if (nativeEnergy.isPresent()) {
                long totalEnergy = saturatedMultiply(
                        nativeEnergy.get().energyPerTick(),
                        nativeEnergy.get().duration());
                return Math.max(1, saturatedAdd(totalEnergy,
                        DEFAULT_DURATION - 1) / DEFAULT_DURATION);
            }
        }
        return StressEnergyConverter.energyPerTick(module, 1);
    }

    private static long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static long saturatedMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static Optional<ItemStack> sequenceModule(Level level,
                                                       ProcessingRecipe<?, ?> recipe) {
        RecipeType<?> type = recipe.getType();
        if (type == AllRecipeTypes.DEPLOYING.getType()) {
            return Optional.of(AllBlocks.DEPLOYER.asStack());
        }
        if (type == AllRecipeTypes.PRESSING.getType()) {
            return Optional.of(AllBlocks.MECHANICAL_PRESS.asStack());
        }
        if (type == AllRecipeTypes.CUTTING.getType()) {
            return Optional.of(AllBlocks.MECHANICAL_SAW.asStack());
        }
        if (type == AllRecipeTypes.FILLING.getType()) {
            return Optional.of(AllBlocks.SPOUT.asStack());
        }
        return CreateFamilyRecipeDiscovery.profiles(level).stream()
                .filter(profile -> profile.bindings().stream()
                        .anyMatch(binding -> binding.type() == type))
                .map(CreateFamilyRecipeDiscovery.DynamicModuleProfile::module)
                .findFirst();
    }

    private static void addFan(List<Candidate> target, RecipeManager manager, ItemStack condition) {
        if (condition.is(Items.WATER_BUCKET)) {
            RecipeType<SplashingRecipe> type = AllRecipeTypes.SPLASHING.getType();
            addFanProcessing(target, manager.getAllRecipesFor(type),
                    "fan_washing", AllFanProcessingTypes.SPLASHING);
        } else if (condition.is(Items.SOUL_CAMPFIRE)) {
            RecipeType<HauntingRecipe> type = AllRecipeTypes.HAUNTING.getType();
            addFanProcessing(target, manager.getAllRecipesFor(type),
                    "fan_haunting", AllFanProcessingTypes.HAUNTING);
        } else if (condition.is(Items.CAMPFIRE)) {
            addFanCooking(target, manager.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.SMOKING),
                    "fan_smoking", AllFanProcessingTypes.SMOKING, 20);
        } else if (condition.is(Items.LAVA_BUCKET)) {
            addFanCooking(target, manager.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.SMELTING),
                    "fan_blasting", AllFanProcessingTypes.BLASTING, 30);
            addFanCooking(target, manager.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.BLASTING),
                    "fan_blasting", AllFanProcessingTypes.BLASTING, 20);
        }
    }

    private static <R extends ProcessingRecipe<?, ?>> void addFanProcessing(
            List<Candidate> target, List<RecipeHolder<R>> recipes, String suffix, FanProcessingType type) {
        for (RecipeHolder<R> holder : recipes) {
            if (!AllRecipeTypes.CAN_BE_AUTOMATED.test(holder) || holder.value().getIngredients().isEmpty()) {
                continue;
            }
            target.add(new Candidate(derivedId(holder.id(), suffix), suffix,
                    List.of(new Requirement(holder.value().getIngredients().getFirst(), 1, true, 0)),
                    displayOutputs(holder.value().getRollableResults(), false), 0, 1,
                    1, 20, DEFAULT_DURATION,
                    (level, match) -> fanResults(type, level, match)));
        }
    }

    private static <R extends AbstractCookingRecipe> void addFanCooking(
            List<Candidate> target, List<RecipeHolder<R>> recipes, String suffix,
            FanProcessingType type, int priority) {
        for (RecipeHolder<R> holder : recipes) {
            if (!AllRecipeTypes.CAN_BE_AUTOMATED.test(holder) || holder.value().getIngredients().isEmpty()) {
                continue;
            }
            ItemStack output = holder.value().getResultItem(net.minecraft.core.RegistryAccess.EMPTY);
            target.add(new Candidate(derivedId(holder.id(), suffix), suffix,
                    List.of(new Requirement(holder.value().getIngredients().getFirst(), 1, true, 0)),
                    output.isEmpty() ? List.of() : List.of(new DisplayOutput(output, 1)), 0, 1,
                    1, priority, DEFAULT_DURATION,
                    (level, match) -> fanResults(type, level, match)));
        }
    }

    private static List<ItemStack> fanResults(FanProcessingType type, Level level, Match match) {
        ItemStack input = match.firstAssignedStack();
        List<ItemStack> results = type.process(input.copyWithCount(1), level);
        return results == null ? List.of() : appendRemainders(results, match);
    }

    private static <R extends CraftingRecipe> void addCrafting(
            List<Candidate> target, List<RecipeHolder<R>> recipes, String suffix, int priority, Level level) {
        for (RecipeHolder<R> holder : recipes) {
            if (AllRecipeTypes.shouldIgnoreInAutomation(holder)) {
                continue;
            }
            R recipe = holder.value();
            NonNullList<Ingredient> ingredients = recipe.getIngredients();
            List<Requirement> requirements = new ArrayList<>();
            for (int position = 0; position < ingredients.size(); position++) {
                Ingredient ingredient = ingredients.get(position);
                if (!ingredient.isEmpty()) {
                    requirements.add(new Requirement(ingredient, 1, true, position));
                }
            }
            if (requirements.isEmpty()) {
                continue;
            }
            ItemStack output = recipe.getResultItem(level.registryAccess());
            target.add(new Candidate(derivedId(holder.id(), suffix), suffix, requirements,
                    output.isEmpty() ? List.of() : List.of(new DisplayOutput(output, 1)), 0, 1,
                    requirements.size(), priority, DEFAULT_DURATION,
                    (recipeLevel, match) -> craft(recipe, ingredients.size(), recipeLevel, match)));
        }
    }

    private static List<DisplayOutput> displayOutputs(List<ProcessingOutput> outputs, boolean weighted) {
        float totalWeight = weighted
                ? outputs.stream().map(ProcessingOutput::getChance).reduce(0F, Float::sum)
                : 1F;
        List<DisplayOutput> result = new ArrayList<>();
        for (ProcessingOutput output : outputs) {
            ItemStack stack = output.getStack();
            if (!stack.isEmpty()) {
                float chance = weighted && totalWeight > 0 ? output.getChance() / totalWeight : output.getChance();
                result.add(new DisplayOutput(stack, chance));
            }
        }
        return List.copyOf(result);
    }

    private static List<ItemStack> craft(CraftingRecipe recipe, int ingredientSlots,
                                         Level level, Match match) {
        int width;
        int height;
        if (recipe instanceof ShapedRecipe shaped) {
            width = shaped.getWidth();
            height = shaped.getHeight();
        } else {
            width = Math.min(3, Math.max(1, ingredientSlots));
            height = Math.max(1, (ingredientSlots + width - 1) / width);
        }
        int gridSize = Math.max(ingredientSlots, width * height);
        List<ItemStack> grid = new ArrayList<>(gridSize);
        for (int i = 0; i < gridSize; i++) {
            grid.add(ItemStack.EMPTY);
        }
        for (int requirement = 0; requirement < match.candidate.requirements.size(); requirement++) {
            int position = match.candidate.requirements.get(requirement).craftPosition;
            if (position >= 0 && position < grid.size()) {
                grid.set(position, match.assignedByRequirement.get(requirement).copyWithCount(1));
            }
        }
        CraftingInput input = CraftingInput.of(width, height, grid);
        List<ItemStack> results = new ArrayList<>();
        ItemStack result = recipe.assemble(input, level.registryAccess());
        if (!result.isEmpty()) {
            results.add(result);
        }
        for (ItemStack remainder : recipe.getRemainingItems(input)) {
            if (!remainder.isEmpty()) {
                addStacked(results, remainder.copy());
            }
        }
        return results;
    }

    private static Optional<CandidateMatch> match(Candidate candidate, List<ItemStack> inventory,
                                                   List<FluidStack> fluids,
                                                   int totalItems, int totalFluid) {
        Match oneRun = allocate(candidate, inventory, 1);
        List<FluidUse> oneRunFluids = allocateFluids(candidate, fluids, 1);
        if (oneRun == null || oneRunFluids == null) {
            return Optional.empty();
        }
        oneRun.fluidUses = oneRunFluids;
        int maximumRuns = 1;
        int consumedPerRun = candidate.consumedPerRun();
        int fluidPerRun = candidate.fluidConsumedPerRun();
        if (consumedPerRun > 0 || fluidPerRun > 0) {
            int upper = Integer.MAX_VALUE;
            if (consumedPerRun > 0) {
                upper = Math.min(upper, totalItems / consumedPerRun);
            }
            if (fluidPerRun > 0) {
                upper = Math.min(upper, totalFluid / fluidPerRun);
            }
            upper = Math.max(1, upper);
            int low = 1;
            int high = upper;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                if (allocate(candidate, inventory, middle) != null
                        && allocateFluids(candidate, fluids, middle) != null) {
                    maximumRuns = middle;
                    low = middle + 1;
                } else {
                    high = middle - 1;
                }
            }
        }
        oneRun.candidate = candidate;
        return Optional.of(new CandidateMatch(candidate, oneRun, maximumRuns));
    }

    @Nullable
    private static List<FluidUse> allocateFluids(Candidate candidate, List<FluidStack> fluids,
                                                  int multiplier) {
        if (candidate.fluidRequirements.isEmpty()) {
            return List.of();
        }
        if (candidate.metadata.directFluidAllocation) {
            DIAGNOSTICS.directFluidAllocations.increment();
            return allocateDisjointFluids(candidate, fluids, multiplier);
        }
        DIAGNOSTICS.fluidFlowAllocations.increment();
        int tankCount = fluids.size();
        int requirementCount = candidate.fluidRequirements.size();
        int source = 0;
        int firstTank = 1;
        int firstRequirement = firstTank + tankCount;
        int sink = firstRequirement + requirementCount;
        FlowNetwork flow = new FlowNetwork(sink + 1);
        for (int tank = 0; tank < tankCount; tank++) {
            FluidStack stack = fluids.get(tank);
            if (!stack.isEmpty()) {
                flow.addEdge(source, firstTank + tank, stack.getAmount());
            }
        }
        int demand = 0;
        FlowEdge[][] assignmentEdges = new FlowEdge[tankCount][requirementCount];
        for (int requirement = 0; requirement < requirementCount; requirement++) {
            SizedFluidIngredient ingredient = candidate.fluidRequirements.get(requirement).ingredient;
            int amount = Math.multiplyExact(ingredient.amount(), multiplier);
            demand += amount;
            flow.addEdge(firstRequirement + requirement, sink, amount);
            for (int tank = 0; tank < tankCount; tank++) {
                FluidStack stack = fluids.get(tank);
                if (!stack.isEmpty() && ingredient.ingredient().test(stack)) {
                    assignmentEdges[tank][requirement] = flow.addEdge(
                            firstTank + tank, firstRequirement + requirement, stack.getAmount());
                }
            }
        }
        if (flow.maxFlow(source, sink) != demand) {
            return null;
        }
        int[] usedByTank = new int[tankCount];
        for (int tank = 0; tank < tankCount; tank++) {
            for (int requirement = 0; requirement < requirementCount; requirement++) {
                FlowEdge edge = assignmentEdges[tank][requirement];
                if (edge != null) {
                    usedByTank[tank] += edge.originalCapacity - edge.capacity;
                }
            }
        }
        List<FluidUse> uses = new ArrayList<>();
        for (int tank = 0; tank < tankCount; tank++) {
            if (usedByTank[tank] > 0) {
                uses.add(new FluidUse(tank, usedByTank[tank], fluids.get(tank).copyWithAmount(1)));
            }
        }
        return List.copyOf(uses);
    }

    @Nullable
    private static List<FluidUse> allocateDisjointFluids(Candidate candidate,
                                                         List<FluidStack> fluids,
                                                         int multiplier) {
        DisjointRequirementAllocator.Allocation allocation =
                DisjointRequirementAllocator.allocate(fluids, FluidStack::getAmount,
                        candidate.fluidRequirements.size(),
                        requirement -> Math.multiplyExact(candidate.fluidRequirements.get(
                                requirement).ingredient.amount(), multiplier),
                        (stack, requirement) -> !stack.isEmpty()
                                && candidate.fluidRequirements.get(requirement)
                                .ingredient.ingredient().test(stack));
        if (allocation == null) {
            return null;
        }
        int[] usedByTank = allocation.usedBySlot();
        List<FluidUse> uses = new ArrayList<>();
        for (int tank = 0; tank < usedByTank.length; tank++) {
            if (usedByTank[tank] > 0) {
                uses.add(new FluidUse(tank, usedByTank[tank],
                        fluids.get(tank).copyWithAmount(1)));
            }
        }
        return List.copyOf(uses);
    }

    @Nullable
    private static Match allocate(Candidate candidate, List<ItemStack> inventory, int multiplier) {
        List<CatalystUse> catalystUses = new ArrayList<>();
        for (int requirementIndex = 0; requirementIndex < candidate.requirements.size(); requirementIndex++) {
            Requirement requirement = candidate.requirements.get(requirementIndex);
            if (!requirement.consumed) {
                int slot = findMatchingSlot(inventory, requirement);
                if (slot < 0) {
                    return null;
                }
                catalystUses.add(new CatalystUse(slot, inventory.get(slot).copyWithCount(1)));
            }
        }
        int[] consumedRequirementIndexes = candidate.metadata.consumedRequirements;
        if (candidate.metadata.directItemAllocation) {
            DIAGNOSTICS.directItemAllocations.increment();
            return allocateDisjoint(candidate, inventory, multiplier,
                    consumedRequirementIndexes, catalystUses);
        }
        DIAGNOSTICS.itemFlowAllocations.increment();

        int slotCount = inventory.size();
        int requirementCount = consumedRequirementIndexes.length;
        int source = 0;
        int firstSlot = 1;
        int firstRequirement = firstSlot + slotCount;
        int sink = firstRequirement + requirementCount;
        FlowNetwork flow = new FlowNetwork(sink + 1);
        for (int slot = 0; slot < slotCount; slot++) {
            ItemStack stack = inventory.get(slot);
            if (!stack.isEmpty()) {
                flow.addEdge(source, firstSlot + slot, stack.getCount());
            }
        }

        int totalDemand = 0;
        FlowEdge[][] assignmentEdges = new FlowEdge[slotCount][requirementCount];
        for (int compactRequirement = 0; compactRequirement < requirementCount; compactRequirement++) {
            Requirement requirement = candidate.requirements.get(
                    consumedRequirementIndexes[compactRequirement]);
            int demand = requirement.count * multiplier;
            totalDemand += demand;
            flow.addEdge(firstRequirement + compactRequirement, sink, demand);
            for (int slot = 0; slot < slotCount; slot++) {
                ItemStack stack = inventory.get(slot);
                if (!stack.isEmpty() && requirement.test(stack)) {
                    assignmentEdges[slot][compactRequirement] = flow.addEdge(
                            firstSlot + slot, firstRequirement + compactRequirement, stack.getCount());
                }
            }
        }
        if (flow.maxFlow(source, sink) != totalDemand) {
            return null;
        }

        int[] consumedBySlot = new int[slotCount];
        List<ItemStack> assigned = new ArrayList<>(candidate.requirements.size());
        for (int i = 0; i < candidate.requirements.size(); i++) {
            assigned.add(ItemStack.EMPTY);
        }
        for (int compactRequirement = 0; compactRequirement < requirementCount; compactRequirement++) {
            int originalRequirement = consumedRequirementIndexes[compactRequirement];
            for (int slot = 0; slot < slotCount; slot++) {
                FlowEdge edge = assignmentEdges[slot][compactRequirement];
                if (edge != null) {
                    int used = edge.originalCapacity - edge.capacity;
                    if (used > 0) {
                        consumedBySlot[slot] += used;
                        if (assigned.get(originalRequirement).isEmpty()) {
                            assigned.set(originalRequirement, inventory.get(slot).copyWithCount(1));
                        }
                    }
                }
            }
        }
        assignCatalysts(candidate, catalystUses, assigned);

        List<StackUse> stackUses = new ArrayList<>();
        for (int slot = 0; slot < consumedBySlot.length; slot++) {
            if (consumedBySlot[slot] > 0) {
                stackUses.add(new StackUse(slot, consumedBySlot[slot],
                        inventory.get(slot).copyWithCount(1)));
            }
        }
        return new Match(stackUses, catalystUses, assigned);
    }

    @Nullable
    private static Match allocateDisjoint(Candidate candidate, List<ItemStack> inventory,
                                          int multiplier,
                                          int[] consumedRequirementIndexes,
                                          List<CatalystUse> catalystUses) {
        DisjointRequirementAllocator.Allocation allocation =
                DisjointRequirementAllocator.allocate(inventory, ItemStack::getCount,
                        consumedRequirementIndexes.length,
                        compact -> Math.multiplyExact(candidate.requirements.get(
                                consumedRequirementIndexes[compact]).count, multiplier),
                        (stack, compact) -> !stack.isEmpty()
                                && candidate.requirements.get(
                                consumedRequirementIndexes[compact]).test(stack));
        if (allocation == null) {
            return null;
        }
        int[] usedBySlot = allocation.usedBySlot();
        int[] firstSlotByRequirement = allocation.firstSlotByRequirement();
        List<ItemStack> assigned = new ArrayList<>(candidate.requirements.size());
        for (int index = 0; index < candidate.requirements.size(); index++) {
            assigned.add(ItemStack.EMPTY);
        }
        List<StackUse> stackUses = new ArrayList<>();
        for (int slot = 0; slot < usedBySlot.length; slot++) {
            if (usedBySlot[slot] > 0) {
                stackUses.add(new StackUse(slot, usedBySlot[slot],
                        inventory.get(slot).copyWithCount(1)));
            }
        }
        for (int compact = 0; compact < consumedRequirementIndexes.length; compact++) {
            int slot = firstSlotByRequirement[compact];
            if (slot >= 0) {
                assigned.set(consumedRequirementIndexes[compact],
                        inventory.get(slot).copyWithCount(1));
            }
        }
        assignCatalysts(candidate, catalystUses, assigned);
        return new Match(stackUses, catalystUses, assigned);
    }

    private static void assignCatalysts(Candidate candidate, List<CatalystUse> catalystUses,
                                        List<ItemStack> assigned) {
        for (CatalystUse catalyst : catalystUses) {
            for (int requirement = 0; requirement < candidate.requirements.size(); requirement++) {
                Requirement value = candidate.requirements.get(requirement);
                if (!value.consumed && assigned.get(requirement).isEmpty()
                        && value.test(catalyst.expected)) {
                    assigned.set(requirement, catalyst.expected.copy());
                    break;
                }
            }
        }
    }

    private static int findMatchingSlot(List<ItemStack> inventory, Requirement requirement) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (requirement.test(inventory.get(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private static List<ItemStack> appendRemainders(List<ItemStack> base, Match match) {
        List<ItemStack> results = new ArrayList<>();
        for (ItemStack stack : base) {
            if (!stack.isEmpty()) {
                addStacked(results, stack.copy());
            }
        }
        for (StackUse use : match.stackUses) {
            if (use.expected.hasCraftingRemainingItem()) {
                ItemStack remainder = use.expected.getCraftingRemainingItem();
                for (int count = 0; count < use.count; count++) {
                    addStacked(results, remainder.copy());
                }
            }
        }
        return results;
    }

    private static void addStacked(List<ItemStack> stacks, ItemStack toAdd) {
        for (ItemStack stack : stacks) {
            if (ItemStack.isSameItemSameComponents(stack, toAdd)
                    && stack.getCount() + toAdd.getCount() <= stack.getMaxStackSize()) {
                stack.grow(toAdd.getCount());
                return;
            }
        }
        stacks.add(toAdd);
    }

    private static ItemStack rollWeighted(List<ProcessingOutput> pool, RandomSource random) {
        float totalWeight = 0;
        for (ProcessingOutput output : pool) {
            totalWeight += output.getChance();
        }
        float selected = random.nextFloat() * totalWeight;
        for (ProcessingOutput output : pool) {
            selected -= output.getChance();
            if (selected < 0) {
                return output.getStack().copy();
            }
        }
        return pool.getLast().getStack().copy();
    }

    private static ResourceLocation derivedId(ResourceLocation source, String suffix) {
        return ResourceLocation.fromNamespaceAndPath(source.getNamespace(),
                source.getPath() + "/mekanicalcreate_" + suffix);
    }

    static final class ExecutionPlan {
        private final ResourceLocation id;
        private final int duration;
        private final long energyPerTick;
        private final List<StackUse> stackUses;
        private final List<CatalystUse> catalystUses;
        private final List<FluidUse> fluidUses;
        private final List<ItemStack> itemResults;
        private final List<FluidStack> fluidResults;
        private final Candidate candidate;
        private final boolean exclusiveMatch;

        private ExecutionPlan(ResourceLocation id, int duration, long energyPerTick,
                              List<StackUse> stackUses,
                              List<CatalystUse> catalystUses, List<FluidUse> fluidUses,
                              List<ItemStack> itemResults, List<FluidStack> fluidResults,
                              Candidate candidate, boolean exclusiveMatch) {
            this.id = id;
            this.duration = duration;
            this.energyPerTick = energyPerTick;
            this.stackUses = List.copyOf(stackUses);
            this.catalystUses = List.copyOf(catalystUses);
            this.fluidUses = List.copyOf(fluidUses);
            this.itemResults = itemResults.stream().filter(stack -> !stack.isEmpty())
                    .map(ItemStack::copy).toList();
            this.fluidResults = fluidResults.stream().filter(stack -> !stack.isEmpty())
                    .map(FluidStack::copy).toList();
            this.candidate = candidate;
            this.exclusiveMatch = exclusiveMatch;
        }

        ResourceLocation id() {
            return id;
        }

        int duration() {
            return duration;
        }

        long energyPerTick() {
            return energyPerTick;
        }

        List<ItemStack> itemResults() {
            return itemResults;
        }

        List<FluidStack> fluidResults() {
            return fluidResults;
        }

        Optional<ExecutionPlan> repeat(Level level,
                                       List<? extends IInventorySlot> slots,
                                       List<? extends IExtendedFluidTank> fluidTanks) {
            if (!exclusiveMatch) {
                return Optional.empty();
            }
            List<ItemStack> inventory = slots.stream().map(IInventorySlot::getStack).toList();
            List<FluidStack> fluids = fluidTanks.stream()
                    .map(tank -> tank.getFluid().copy()).toList();
            int totalItems = inventory.stream().mapToInt(ItemStack::getCount).sum();
            int totalFluid = fluids.stream().mapToInt(FluidStack::getAmount).sum();
            if (!canPossiblyMatch(candidate, inventory, fluids, totalItems, totalFluid)) {
                return Optional.empty();
            }
            CandidateMatch repeated = match(candidate, inventory, fluids,
                    totalItems, totalFluid).orElse(null);
            return repeated == null ? Optional.empty()
                    : Optional.of(createPlan(level, repeated, true));
        }

        boolean stillValid(List<? extends IInventorySlot> slots,
                           List<? extends IExtendedFluidTank> fluidTanks) {
            for (StackUse use : stackUses) {
                ItemStack current = slots.get(use.slot).getStack();
                if (current.getCount() < use.count
                        || !ItemStack.isSameItemSameComponents(current, use.expected)) {
                    return false;
                }
            }
            for (CatalystUse use : catalystUses) {
                ItemStack current = slots.get(use.slot).getStack();
                if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, use.expected)) {
                    return false;
                }
            }
            for (FluidUse use : fluidUses) {
                FluidStack current = fluidTanks.get(use.tank).getFluid();
                if (current.getAmount() < use.amount
                        || !FluidStack.isSameFluidSameComponents(current, use.expected)) {
                    return false;
                }
            }
            return true;
        }

        void consume(List<? extends IInventorySlot> slots,
                     List<? extends IExtendedFluidTank> fluidTanks) {
            for (StackUse use : stackUses) {
                slots.get(use.slot).extractItem(use.count, Action.EXECUTE, AutomationType.INTERNAL);
            }
            for (FluidUse use : fluidUses) {
                fluidTanks.get(use.tank).shrinkStack(use.amount, Action.EXECUTE);
            }
        }
    }

    private record Requirement(Ingredient ingredient, int count, boolean consumed,
                               int craftPosition, ItemStack exactStack) {
        private Requirement(Ingredient ingredient, int count, boolean consumed,
                            int craftPosition) {
            this(ingredient, count, consumed, craftPosition, ItemStack.EMPTY);
        }

        private Requirement {
            exactStack = exactStack.copyWithCount(exactStack.isEmpty() ? 0 : 1);
        }

        private boolean test(ItemStack stack) {
            return exactStack.isEmpty() ? ingredient.test(stack)
                    : ItemStack.isSameItemSameComponents(stack, exactStack);
        }
    }

    private record FluidRequirement(SizedFluidIngredient ingredient) {
    }

    private record EnergyChargePlan(ItemStack output, int duration,
                                    long energyPerTick) {
        private EnergyChargePlan {
            output = output.copyWithCount(1);
        }
    }

    private record ResolverContext(ItemPoolKey catalysts, boolean allowFluidProcessing) {
    }

    private record ResolutionCacheKey(ResolverContext context, ItemPoolKey items,
                                      FluidPoolKey fluids) {
    }

    private record CachedResolution(@Nullable Candidate candidate, boolean exclusiveMatch) {
        private static final CachedResolution NO_MATCH = new CachedResolution(null, false);
    }

    private static final class ItemPoolKey {
        private final List<ItemAmount> entries;
        private final int hash;

        private ItemPoolKey(List<ItemAmount> entries) {
            this.entries = List.copyOf(entries);
            int value = 0;
            for (ItemAmount entry : entries) {
                value += 31 * ItemStack.hashItemAndComponents(entry.stack) + entry.amount;
            }
            hash = value;
        }

        private static ItemPoolKey create(List<ItemStack> stacks) {
            List<ItemAmount> entries = new ArrayList<>();
            for (ItemStack stack : stacks) {
                if (stack.isEmpty()) {
                    continue;
                }
                ItemAmount matching = null;
                for (ItemAmount entry : entries) {
                    if (ItemStack.isSameItemSameComponents(entry.stack, stack)) {
                        matching = entry;
                        break;
                    }
                }
                if (matching == null) {
                    entries.add(new ItemAmount(stack.copyWithCount(1), stack.getCount()));
                } else {
                    matching.amount = Math.addExact(matching.amount, stack.getCount());
                }
            }
            return new ItemPoolKey(entries);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ItemPoolKey key) || entries.size() != key.entries.size()) {
                return false;
            }
            for (ItemAmount entry : entries) {
                boolean found = false;
                for (ItemAmount candidate : key.entries) {
                    if (entry.amount == candidate.amount
                            && ItemStack.isSameItemSameComponents(entry.stack, candidate.stack)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final class ItemAmount {
        private final ItemStack stack;
        private int amount;

        private ItemAmount(ItemStack stack, int amount) {
            this.stack = stack;
            this.amount = amount;
        }
    }

    private static final class FluidPoolKey {
        private final List<FluidAmount> entries;
        private final int hash;

        private FluidPoolKey(List<FluidAmount> entries) {
            this.entries = List.copyOf(entries);
            int value = 0;
            for (FluidAmount entry : entries) {
                value += 31 * FluidStack.hashFluidAndComponents(entry.stack) + entry.amount;
            }
            hash = value;
        }

        private static FluidPoolKey create(List<FluidStack> stacks) {
            List<FluidAmount> entries = new ArrayList<>();
            for (FluidStack stack : stacks) {
                if (stack.isEmpty()) {
                    continue;
                }
                FluidAmount matching = null;
                for (FluidAmount entry : entries) {
                    if (FluidStack.isSameFluidSameComponents(entry.stack, stack)) {
                        matching = entry;
                        break;
                    }
                }
                if (matching == null) {
                    entries.add(new FluidAmount(stack.copyWithAmount(1), stack.getAmount()));
                } else {
                    matching.amount = Math.addExact(matching.amount, stack.getAmount());
                }
            }
            return new FluidPoolKey(entries);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FluidPoolKey key) || entries.size() != key.entries.size()) {
                return false;
            }
            for (FluidAmount entry : entries) {
                boolean found = false;
                for (FluidAmount candidate : key.entries) {
                    if (entry.amount == candidate.amount
                            && FluidStack.isSameFluidSameComponents(entry.stack, candidate.stack)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final class FluidAmount {
        private final FluidStack stack;
        private int amount;

        private FluidAmount(FluidStack stack, int amount) {
            this.stack = stack;
            this.amount = amount;
        }
    }

    public record DiagnosticsSnapshot(long catalogBuilds, long fullSearches,
                                      long resolutionCacheHits, long resolutionCacheMisses,
                                      long candidatesConsidered, long quickRejects,
                                      long exactChecks, long directItemAllocations,
                                      long itemFlowAllocations, long directFluidAllocations,
                                      long fluidFlowAllocations, long noMatches,
                                      long debounceDeferrals, long reloadDeferrals,
                                      long averageSearchMicros, long maxSearchMicros) {
        public String compactSummary() {
            return "catalogs=" + catalogBuilds + ", searches=" + fullSearches
                    + ", cache=" + resolutionCacheHits + "/" + resolutionCacheMisses
                    + ", candidates=" + candidatesConsidered + ", rejected=" + quickRejects
                    + ", exact=" + exactChecks + ", itemDirect/flow="
                    + directItemAllocations + "/" + itemFlowAllocations
                    + ", fluidDirect/flow=" + directFluidAllocations + "/"
                    + fluidFlowAllocations + ", noMatch=" + noMatches
                    + ", debounce/reload=" + debounceDeferrals + "/" + reloadDeferrals
                    + ", searchAvg/MaxUs=" + averageSearchMicros + "/" + maxSearchMicros;
        }
    }

    private static final class ResolverDiagnostics {
        private final LongAdder catalogBuilds = new LongAdder();
        private final LongAdder fullSearches = new LongAdder();
        private final LongAdder resolutionCacheHits = new LongAdder();
        private final LongAdder resolutionCacheMisses = new LongAdder();
        private final LongAdder candidatesConsidered = new LongAdder();
        private final LongAdder quickRejects = new LongAdder();
        private final LongAdder exactChecks = new LongAdder();
        private final LongAdder directItemAllocations = new LongAdder();
        private final LongAdder itemFlowAllocations = new LongAdder();
        private final LongAdder directFluidAllocations = new LongAdder();
        private final LongAdder fluidFlowAllocations = new LongAdder();
        private final LongAdder noMatches = new LongAdder();
        private final LongAdder debounceDeferrals = new LongAdder();
        private final LongAdder reloadDeferrals = new LongAdder();
        private final LongAdder totalSearchNanos = new LongAdder();
        private final AtomicLong maxSearchNanos = new AtomicLong();

        private void recordSearchTime(long nanos) {
            totalSearchNanos.add(Math.max(0, nanos));
            maxSearchNanos.accumulateAndGet(Math.max(0, nanos), Math::max);
        }

        private DiagnosticsSnapshot snapshot() {
            return new DiagnosticsSnapshot(catalogBuilds.sum(), fullSearches.sum(),
                    resolutionCacheHits.sum(), resolutionCacheMisses.sum(),
                    candidatesConsidered.sum(), quickRejects.sum(), exactChecks.sum(),
                    directItemAllocations.sum(), itemFlowAllocations.sum(),
                    directFluidAllocations.sum(), fluidFlowAllocations.sum(),
                    noMatches.sum(), debounceDeferrals.sum(), reloadDeferrals.sum(),
                    fullSearches.sum() == 0 ? 0
                            : totalSearchNanos.sum() / fullSearches.sum() / 1_000,
                    maxSearchNanos.get() / 1_000);
        }

        private void reset() {
            catalogBuilds.reset();
            fullSearches.reset();
            resolutionCacheHits.reset();
            resolutionCacheMisses.reset();
            candidatesConsidered.reset();
            quickRejects.reset();
            exactChecks.reset();
            directItemAllocations.reset();
            itemFlowAllocations.reset();
            directFluidAllocations.reset();
            fluidFlowAllocations.reset();
            noMatches.reset();
            debounceDeferrals.reset();
            reloadDeferrals.reset();
            totalSearchNanos.reset();
            maxSearchNanos.set(0);
        }
    }

    private record CandidateCacheKey(Item module, Item condition,
                                     boolean allowFluidProcessing) {
    }

    private record CandidateCatalog(List<Candidate> all,
                                    Map<Item, List<Candidate>> byAnchorItem,
                                    List<Candidate> unindexed) {
        private static CandidateCatalog create(List<Candidate> candidates) {
            List<Candidate> all = List.copyOf(candidates);
            Map<Item, List<Candidate>> mutableIndex = new IdentityHashMap<>();
            List<Candidate> unindexed = new ArrayList<>();
            for (Candidate candidate : all) {
                Set<Item> anchor = candidate.metadata.anchorItems;
                if (anchor == null || anchor.isEmpty()) {
                    unindexed.add(candidate);
                    continue;
                }
                for (Item item : anchor) {
                    mutableIndex.computeIfAbsent(item, ignored -> new ArrayList<>())
                            .add(candidate);
                }
            }
            Map<Item, List<Candidate>> frozenIndex = new IdentityHashMap<>();
            mutableIndex.forEach((item, indexed) ->
                    frozenIndex.put(item, List.copyOf(indexed)));
            return new CandidateCatalog(all, Collections.unmodifiableMap(frozenIndex),
                    List.copyOf(unindexed));
        }

        private List<Candidate> candidatesFor(List<ItemStack> inventory) {
            LinkedHashSet<Candidate> selected = new LinkedHashSet<>(unindexed);
            Set<Item> seenItems = Collections.newSetFromMap(new IdentityHashMap<>());
            for (ItemStack stack : inventory) {
                if (stack.isEmpty() || !seenItems.add(stack.getItem())) {
                    continue;
                }
                List<Candidate> indexed = byAnchorItem.get(stack.getItem());
                if (indexed != null) {
                    selected.addAll(indexed);
                }
            }
            return List.copyOf(selected);
        }
    }

    private record Candidate(ResourceLocation id, String process, List<Requirement> requirements,
                             List<FluidRequirement> fluidRequirements,
                             List<DisplayOutput> displayOutputs, List<FluidStack> fluidOutputs,
                             int sequenceSteps, int loops,
                             int complexity, int priority, int duration,
                             ResultFactory resultFactory, long energyPerTick,
                             CandidateMetadata metadata) {
        private Candidate(ResourceLocation id, String process, List<Requirement> requirements,
                          List<FluidRequirement> fluidRequirements,
                          List<DisplayOutput> displayOutputs, List<FluidStack> fluidOutputs,
                          int sequenceSteps, int loops, int complexity, int priority,
                          int duration, ResultFactory resultFactory, long energyPerTick) {
            this(id, process, requirements, fluidRequirements, displayOutputs, fluidOutputs,
                    sequenceSteps, loops, complexity, priority, duration, resultFactory,
                    energyPerTick, CandidateMetadata.compile(requirements, fluidRequirements));
        }

        private Candidate(ResourceLocation id, String process, List<Requirement> requirements,
                          List<FluidRequirement> fluidRequirements,
                          List<DisplayOutput> displayOutputs, List<FluidStack> fluidOutputs,
                          int sequenceSteps, int loops, int complexity, int priority,
                          int duration, ResultFactory resultFactory) {
            this(id, process, requirements, fluidRequirements, displayOutputs, fluidOutputs,
                    sequenceSteps, loops, complexity, priority, duration, resultFactory, 0L);
        }

        private Candidate(ResourceLocation id, String process, List<Requirement> requirements,
                          List<DisplayOutput> displayOutputs, int sequenceSteps, int loops,
                          int complexity, int priority, int duration, ResultFactory resultFactory) {
            this(id, process, requirements, List.of(), displayOutputs, List.of(),
                    sequenceSteps, loops, complexity, priority, duration, resultFactory, 0L);
        }

        private Candidate withEnergyPerTick(long energyPerTick) {
            return new Candidate(id, process, requirements, fluidRequirements,
                    displayOutputs, fluidOutputs, sequenceSteps, loops, complexity,
                    priority, duration, resultFactory, energyPerTick, metadata);
        }

        int consumedPerRun() {
            return metadata.consumedPerRun;
        }

        int fluidConsumedPerRun() {
            return metadata.fluidConsumedPerRun;
        }

        List<FluidStack> displayFluidOutputs() {
            return fluidOutputs;
        }
    }

    private record CandidateMetadata(int consumedPerRun, int fluidConsumedPerRun,
                                     int[] consumedRequirements,
                                     boolean directItemAllocation,
                                     boolean directFluidAllocation,
                                     Set<Item> anchorItems) {
        private CandidateMetadata {
            consumedRequirements = consumedRequirements.clone();
            anchorItems = anchorItems == null ? null : Set.copyOf(anchorItems);
        }

        @Override
        public int[] consumedRequirements() {
            return consumedRequirements.clone();
        }

        private static CandidateMetadata compile(List<Requirement> requirements,
                                                 List<FluidRequirement> fluids) {
            int consumed = 0;
            List<Integer> consumedIndexes = new ArrayList<>();
            List<RequirementDomain> consumedDomains = new ArrayList<>();
            Set<Item> smallestAnchor = null;
            boolean direct = true;
            for (int index = 0; index < requirements.size(); index++) {
                Requirement requirement = requirements.get(index);
                if (requirement.consumed) {
                    consumed = Math.addExact(consumed, requirement.count);
                    consumedIndexes.add(index);
                }
                Set<Item> options = enumerableItems(requirement);
                if (options == null || options.isEmpty()) {
                    if (requirement.consumed) {
                        direct = false;
                    }
                    continue;
                }
                if (smallestAnchor == null || options.size() < smallestAnchor.size()) {
                    smallestAnchor = options;
                }
                if (requirement.consumed) {
                    RequirementDomain domain = new RequirementDomain(requirement, options);
                    for (RequirementDomain previous : consumedDomains) {
                        if (!directlyCompatible(previous, domain)) {
                            direct = false;
                            break;
                        }
                    }
                    consumedDomains.add(domain);
                }
            }
            int fluidConsumed = 0;
            boolean directFluids = true;
            List<Set<Fluid>> fluidDomains = new ArrayList<>();
            for (FluidRequirement requirement : fluids) {
                fluidConsumed = Math.addExact(fluidConsumed, requirement.ingredient.amount());
                if (!requirement.ingredient.ingredient().isSimple()) {
                    directFluids = fluids.size() == 1;
                    continue;
                }
                Set<Fluid> options = Collections.newSetFromMap(new IdentityHashMap<>());
                for (FluidStack stack : requirement.ingredient.ingredient().getStacks()) {
                    if (!stack.isEmpty()) {
                        options.add(stack.getFluid());
                    }
                }
                if (options.isEmpty()) {
                    directFluids = false;
                    continue;
                }
                for (Set<Fluid> previous : fluidDomains) {
                    if (!Collections.disjoint(previous, options)
                            && !previous.equals(options)) {
                        directFluids = false;
                        break;
                    }
                }
                fluidDomains.add(options);
            }
            int[] indexes = consumedIndexes.stream().mapToInt(Integer::intValue).toArray();
            return new CandidateMetadata(consumed, fluidConsumed, indexes,
                    direct, directFluids, smallestAnchor);
        }

        private static boolean directlyCompatible(RequirementDomain first,
                                                  RequirementDomain second) {
            if (Collections.disjoint(first.items, second.items)) {
                return true;
            }
            boolean firstExact = !first.requirement.exactStack.isEmpty();
            boolean secondExact = !second.requirement.exactStack.isEmpty();
            if (firstExact && secondExact) {
                // Equal exact stacks share one homogeneous pool; unequal
                // component stacks are actually disjoint despite sharing an item.
                return true;
            }
            return !firstExact && !secondExact && first.items.equals(second.items);
        }

        @Nullable
        private static Set<Item> enumerableItems(Requirement requirement) {
            Set<Item> options = Collections.newSetFromMap(new IdentityHashMap<>());
            if (!requirement.exactStack.isEmpty()) {
                options.add(requirement.exactStack.getItem());
                return options;
            }
            if (requirement.ingredient.isCustom()) {
                return null;
            }
            try {
                for (ItemStack stack : requirement.ingredient.getItems()) {
                    if (!stack.isEmpty()) {
                        options.add(stack.getItem());
                    }
                }
                return options;
            } catch (LinkageError | RuntimeException ignored) {
                // Custom or late-bound ingredients remain on the conservative
                // max-flow path and out of the reverse index.
                return null;
            }
        }

        private record RequirementDomain(Requirement requirement, Set<Item> items) {
        }
    }

    @FunctionalInterface
    private interface ResultFactory extends BiFunction<Level, Match, List<ItemStack>> {
    }

    public record DisplayInput(Ingredient ingredient, int count, boolean consumed) {
        public DisplayInput {
            if (count < 1) {
                throw new IllegalArgumentException("Display input count must be positive");
            }
        }
    }

    public record DisplayOutput(ItemStack stack, float chance) {
        public DisplayOutput {
            stack = stack.copy();
        }
    }

    public record DisplayFluidInput(SizedFluidIngredient ingredient) {
    }

    public record DisplayFluidOutput(FluidStack stack) {
        public DisplayFluidOutput {
            stack = stack.copy();
        }
    }

    public record DisplayRecipe(ResourceLocation id, Component processName, ItemStack module,
                                ItemStack condition, List<DisplayInput> inputs,
                                List<DisplayOutput> outputs,
                                List<SizedFluidIngredient> fluidInputs,
                                List<FluidStack> fluidOutputs,
                                int sequenceSteps, int loops) {
        public DisplayRecipe {
            module = module.copyWithCount(1);
            condition = condition.copyWithCount(1);
            inputs = List.copyOf(inputs);
            outputs = outputs.stream()
                    .map(output -> new DisplayOutput(output.stack, output.chance))
                    .toList();
            fluidInputs = List.copyOf(fluidInputs);
            fluidOutputs = fluidOutputs.stream().map(FluidStack::copy).toList();
        }
    }

    private static final class Match {
        private final List<StackUse> stackUses;
        private final List<CatalystUse> catalystUses;
        private final List<ItemStack> assignedByRequirement;
        private List<FluidUse> fluidUses = List.of();
        private Candidate candidate;

        private Match(List<StackUse> stackUses, List<CatalystUse> catalystUses,
                      List<ItemStack> assignedByRequirement) {
            this.stackUses = stackUses;
            this.catalystUses = catalystUses;
            this.assignedByRequirement = assignedByRequirement;
        }

        private ItemStack firstAssignedStack() {
            return assignedByRequirement.stream().filter(stack -> !stack.isEmpty()).findFirst()
                    .orElse(ItemStack.EMPTY);
        }
    }

    private record StackUse(int slot, int count, ItemStack expected) {
    }

    private record CatalystUse(int slot, ItemStack expected) {
    }

    private record FluidUse(int tank, int amount, FluidStack expected) {
    }

    private record CandidateMatch(Candidate candidate, Match match, int maximumRuns) {
        private static final Comparator<CandidateMatch> ORDER = Comparator
                .comparingInt((CandidateMatch value) -> value.candidate.consumedPerRun())
                .thenComparingInt(value -> value.candidate.fluidConsumedPerRun())
                .thenComparingInt(value -> value.candidate.complexity)
                .thenComparingInt(value -> value.candidate.requirements.size())
                .thenComparingInt(CandidateMatch::maximumRuns)
                .thenComparingInt(value -> value.candidate.priority)
                .thenComparing(value -> value.candidate.id.toString(), Comparator.reverseOrder());
    }

    private static final class FlowNetwork {
        private final List<List<FlowEdge>> graph;
        private int[] level;
        private int[] next;

        private FlowNetwork(int nodes) {
            graph = new ArrayList<>(nodes);
            for (int i = 0; i < nodes; i++) {
                graph.add(new ArrayList<>());
            }
        }

        private FlowEdge addEdge(int from, int to, int capacity) {
            FlowEdge forward = new FlowEdge(to, graph.get(to).size(), capacity);
            FlowEdge backward = new FlowEdge(from, graph.get(from).size(), 0);
            graph.get(from).add(forward);
            graph.get(to).add(backward);
            return forward;
        }

        private int maxFlow(int source, int sink) {
            int result = 0;
            while (buildLevels(source, sink)) {
                next = new int[graph.size()];
                int pushed;
                while ((pushed = push(source, sink, Integer.MAX_VALUE)) > 0) {
                    result += pushed;
                }
            }
            return result;
        }

        private boolean buildLevels(int source, int sink) {
            level = new int[graph.size()];
            Arrays.fill(level, -1);
            level[source] = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(source);
            while (!queue.isEmpty()) {
                int node = queue.removeFirst();
                for (FlowEdge edge : graph.get(node)) {
                    if (edge.capacity > 0 && level[edge.to] < 0) {
                        level[edge.to] = level[node] + 1;
                        queue.addLast(edge.to);
                    }
                }
            }
            return level[sink] >= 0;
        }

        private int push(int node, int sink, int available) {
            if (node == sink) {
                return available;
            }
            List<FlowEdge> edges = graph.get(node);
            for (; next[node] < edges.size(); next[node]++) {
                FlowEdge edge = edges.get(next[node]);
                if (edge.capacity <= 0 || level[edge.to] != level[node] + 1) {
                    continue;
                }
                int sent = push(edge.to, sink, Math.min(available, edge.capacity));
                if (sent > 0) {
                    edge.capacity -= sent;
                    graph.get(edge.to).get(edge.reverse).capacity += sent;
                    return sent;
                }
            }
            return 0;
        }
    }

    private static final class FlowEdge {
        private final int to;
        private final int reverse;
        private final int originalCapacity;
        private int capacity;

        private FlowEdge(int to, int reverse, int capacity) {
            this.to = to;
            this.reverse = reverse;
            this.capacity = capacity;
            this.originalCapacity = capacity;
        }
    }
}
