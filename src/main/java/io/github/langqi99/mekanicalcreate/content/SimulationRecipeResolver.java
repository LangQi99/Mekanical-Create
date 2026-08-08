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
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the unordered item pool into one deterministic operation. Inventory
 * size affects conflict resolution, but an execution plan always consumes one
 * recipe's worth of materials.
 */
public final class SimulationRecipeResolver {
    private static final int DEFAULT_DURATION = 100;

    private SimulationRecipeResolver() {
    }

    static Optional<ExecutionPlan> resolve(Level level, ItemStack module, ItemStack condition,
                                           List<? extends IInventorySlot> inputSlots) {
        if (module.isEmpty()) {
            return Optional.empty();
        }
        List<ItemStack> inventory = inputSlots.stream().map(IInventorySlot::getStack).toList();
        List<Candidate> candidates = collectCandidates(level, module, condition);
        CandidateMatch best = candidates.stream()
                .map(candidate -> match(candidate, inventory))
                .flatMap(Optional::stream)
                .max(CandidateMatch.ORDER)
                .orElse(null);
        if (best == null) {
            return Optional.empty();
        }
        List<ItemStack> results = best.candidate.resultFactory.apply(level, best.match);
        return Optional.of(new ExecutionPlan(best.candidate.id, best.candidate.duration,
                best.match.stackUses, best.match.catalystUses, results));
    }

    /**
     * The public JEI view is generated from the exact candidates used by the
     * server-side resolver. Module and condition items are configuration
     * catalysts, while {@link DisplayInput#consumed()} controls pattern inputs.
     */
    public static List<DisplayRecipe> getDisplayRecipes(Level level) {
        List<DisplayRecipe> result = new ArrayList<>();
        List<ItemStack> modules = List.of(
                AllBlocks.DEPLOYER.asStack(),
                AllBlocks.MECHANICAL_PRESS.asStack(),
                AllBlocks.MECHANICAL_SAW.asStack(),
                AllBlocks.MILLSTONE.asStack(),
                AllBlocks.CRUSHING_WHEEL.asStack(),
                AllBlocks.MECHANICAL_CRAFTER.asStack());
        for (ItemStack module : modules) {
            appendDisplayRecipes(result, collectCandidates(level, module, ItemStack.EMPTY), module, ItemStack.EMPTY);
        }
        ItemStack fan = AllBlocks.ENCASED_FAN.asStack();
        for (ItemStack condition : List.of(
                new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.SOUL_CAMPFIRE),
                new ItemStack(Items.CAMPFIRE), new ItemStack(Items.LAVA_BUCKET))) {
            appendDisplayRecipes(result, collectCandidates(level, fan, condition), fan, condition);
        }
        return List.copyOf(result);
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
            target.add(new DisplayRecipe(displayId,
                    Component.translatable("jei.mekanicalcreate.process." + candidate.process),
                    module, condition, inputs, candidate.displayOutputs,
                    candidate.sequenceSteps, candidate.loops));
        }
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

    private static List<Candidate> collectCandidates(Level level, ItemStack module, ItemStack condition) {
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
            addSequenced(candidates, recipes, ModuleKind.DEPLOYER);
        } else if (module.is(AllBlocks.MECHANICAL_PRESS.asItem())) {
            RecipeType<PressingRecipe> type = AllRecipeTypes.PRESSING.getType();
            addProcessing(candidates, recipes.getAllRecipesFor(type),
                    "pressing", 20, false);
            addSequenced(candidates, recipes, ModuleKind.PRESS);
        } else if (module.is(AllBlocks.MECHANICAL_SAW.asItem())) {
            RecipeType<CuttingRecipe> type = AllRecipeTypes.CUTTING.getType();
            addProcessing(candidates, recipes.getAllRecipesFor(type),
                    "cutting", 20, false);
            addSequenced(candidates, recipes, ModuleKind.SAW);
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
        }
        return candidates;
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
            if (requirements.isEmpty()) {
                continue;
            }
            int duration = recipe.getProcessingDuration() > 0
                    ? Math.max(20, recipe.getProcessingDuration()) : DEFAULT_DURATION;
            target.add(new Candidate(derivedId(holder.id(), suffix), suffix, requirements,
                    displayOutputs(recipe.getRollableResults(), false), 0, 1,
                    1, priority, duration,
                    (level, match) -> appendRemainders(recipe.rollResults(level.random), match)));
        }
    }

    private static void addSequenced(List<Candidate> target, RecipeManager manager, ModuleKind selectedModule) {
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
            int loops = Math.max(1, recipe.getLoops());
            for (SequencedRecipe<?> step : recipe.getSequence()) {
                ProcessingRecipe<?, ?> processing = step.getRecipe();
                if (processing instanceof DeployerApplicationRecipe deployer) {
                    containsSelectedModule |= selectedModule == ModuleKind.DEPLOYER;
                    if (deployer.getIngredients().size() < 2) {
                        supported = false;
                        break;
                    }
                    requirements.add(new Requirement(deployer.getIngredients().get(1),
                            deployer.shouldKeepHeldItem() ? 1 : loops,
                            !deployer.shouldKeepHeldItem(), -1));
                } else if (processing instanceof PressingRecipe) {
                    containsSelectedModule |= selectedModule == ModuleKind.PRESS;
                } else if (processing instanceof CuttingRecipe) {
                    containsSelectedModule |= selectedModule == ModuleKind.SAW;
                } else {
                    supported = false;
                    break;
                }
            }
            if (!supported || !containsSelectedModule) {
                continue;
            }
            target.add(new Candidate(derivedId(holder.id(), "sequenced_assembly"), "sequenced_assembly",
                    requirements, displayOutputs(recipe.resultPool, true), recipe.getSequence().size(), loops,
                    recipe.getSequence().size() * loops, 100, DEFAULT_DURATION,
                    (level, match) -> appendRemainders(
                            List.of(rollWeighted(recipe.resultPool, level.random)), match)));
        }
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

    private static Optional<CandidateMatch> match(Candidate candidate, List<ItemStack> inventory) {
        Match oneRun = allocate(candidate, inventory, 1);
        if (oneRun == null) {
            return Optional.empty();
        }
        int maximumRuns = 1;
        int totalItems = inventory.stream().mapToInt(ItemStack::getCount).sum();
        int consumedPerRun = candidate.consumedPerRun();
        if (consumedPerRun > 0) {
            int upper = Math.max(1, totalItems / consumedPerRun);
            int low = 1;
            int high = upper;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                if (allocate(candidate, inventory, middle) != null) {
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
    private static Match allocate(Candidate candidate, List<ItemStack> inventory, int multiplier) {
        List<Integer> consumedRequirementIndexes = new ArrayList<>();
        List<CatalystUse> catalystUses = new ArrayList<>();
        for (int requirementIndex = 0; requirementIndex < candidate.requirements.size(); requirementIndex++) {
            Requirement requirement = candidate.requirements.get(requirementIndex);
            if (requirement.consumed) {
                consumedRequirementIndexes.add(requirementIndex);
            } else {
                int slot = findMatchingSlot(inventory, requirement.ingredient);
                if (slot < 0) {
                    return null;
                }
                catalystUses.add(new CatalystUse(slot, inventory.get(slot).copyWithCount(1)));
            }
        }

        int slotCount = inventory.size();
        int requirementCount = consumedRequirementIndexes.size();
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
            Requirement requirement = candidate.requirements.get(consumedRequirementIndexes.get(compactRequirement));
            int demand = requirement.count * multiplier;
            totalDemand += demand;
            flow.addEdge(firstRequirement + compactRequirement, sink, demand);
            for (int slot = 0; slot < slotCount; slot++) {
                ItemStack stack = inventory.get(slot);
                if (!stack.isEmpty() && requirement.ingredient.test(stack)) {
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
            int originalRequirement = consumedRequirementIndexes.get(compactRequirement);
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
        for (CatalystUse catalyst : catalystUses) {
            for (int requirement = 0; requirement < candidate.requirements.size(); requirement++) {
                Requirement value = candidate.requirements.get(requirement);
                if (!value.consumed && assigned.get(requirement).isEmpty()
                        && value.ingredient.test(catalyst.expected)) {
                    assigned.set(requirement, catalyst.expected.copy());
                    break;
                }
            }
        }

        List<StackUse> stackUses = new ArrayList<>();
        for (int slot = 0; slot < consumedBySlot.length; slot++) {
            if (consumedBySlot[slot] > 0) {
                stackUses.add(new StackUse(slot, consumedBySlot[slot],
                        inventory.get(slot).copyWithCount(1)));
            }
        }
        return new Match(stackUses, catalystUses, assigned);
    }

    private static int findMatchingSlot(List<ItemStack> inventory, Ingredient ingredient) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (ingredient.test(inventory.get(slot))) {
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
        private final List<StackUse> stackUses;
        private final List<CatalystUse> catalystUses;
        private final List<ItemStack> results;

        private ExecutionPlan(ResourceLocation id, int duration, List<StackUse> stackUses,
                              List<CatalystUse> catalystUses, List<ItemStack> results) {
            this.id = id;
            this.duration = duration;
            this.stackUses = List.copyOf(stackUses);
            this.catalystUses = List.copyOf(catalystUses);
            this.results = results.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
        }

        ResourceLocation id() {
            return id;
        }

        int duration() {
            return duration;
        }

        List<ItemStack> results() {
            return results;
        }

        boolean stillValid(List<? extends IInventorySlot> slots) {
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
            return true;
        }

        void consume(List<? extends IInventorySlot> slots) {
            for (StackUse use : stackUses) {
                slots.get(use.slot).extractItem(use.count, Action.EXECUTE, AutomationType.INTERNAL);
            }
        }
    }

    private enum ModuleKind {
        DEPLOYER,
        PRESS,
        SAW
    }

    private record Requirement(Ingredient ingredient, int count, boolean consumed, int craftPosition) {
    }

    private record Candidate(ResourceLocation id, String process, List<Requirement> requirements,
                             List<DisplayOutput> displayOutputs, int sequenceSteps, int loops,
                             int complexity, int priority, int duration, ResultFactory resultFactory) {
        int consumedPerRun() {
            return requirements.stream().filter(Requirement::consumed).mapToInt(Requirement::count).sum();
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

    public record DisplayRecipe(ResourceLocation id, Component processName, ItemStack module,
                                ItemStack condition, List<DisplayInput> inputs,
                                List<DisplayOutput> outputs, int sequenceSteps, int loops) {
        public DisplayRecipe {
            module = module.copyWithCount(1);
            condition = condition.copyWithCount(1);
            inputs = List.copyOf(inputs);
            outputs = outputs.stream()
                    .map(output -> new DisplayOutput(output.stack, output.chance))
                    .toList();
        }
    }

    private static final class Match {
        private final List<StackUse> stackUses;
        private final List<CatalystUse> catalystUses;
        private final List<ItemStack> assignedByRequirement;
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

    private record CandidateMatch(Candidate candidate, Match match, int maximumRuns) {
        private static final Comparator<CandidateMatch> ORDER = Comparator
                .comparingInt((CandidateMatch value) -> value.candidate.consumedPerRun())
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
