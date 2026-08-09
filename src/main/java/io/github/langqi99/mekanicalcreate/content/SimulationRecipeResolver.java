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
import com.simibubi.create.foundation.fluid.FluidIngredient;
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
import mekanism.api.fluid.IExtendedFluidTank;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import net.minecraftforge.fluids.FluidStack;

/**
 * Resolves the unordered item pool into one deterministic operation. Inventory
 * size affects conflict resolution, but an execution plan always consumes one
 * recipe's worth of materials.
 */
public final class SimulationRecipeResolver {
    private static final int DEFAULT_DURATION = 100;
    private static final AbstractContainerMenu CRAFTING_MENU = new AbstractContainerMenu(null, -1) {
        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return false;
        }
    };

    private SimulationRecipeResolver() {
    }

    static Optional<ExecutionPlan> resolve(Level level, ItemStack module, ItemStack condition,
                                           List<? extends IInventorySlot> inputSlots,
                                           List<? extends IExtendedFluidTank> inputFluidTanks,
                                           boolean allowFluidProcessing) {
        if (module.isEmpty()) {
            return Optional.empty();
        }
        return resolve(level, inputSlots, inputFluidTanks,
                collectCandidates(level, module, condition, allowFluidProcessing));
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
        for (ItemStack module : catalysts) {
            if (!isSupportedModule(module)) {
                continue;
            }
            if (module.is(AllBlocks.ENCASED_FAN.asItem())) {
                for (ItemStack condition : conditions) {
                    candidates.addAll(collectCandidates(level, module, condition,
                            allowFluidProcessing));
                }
            } else {
                candidates.addAll(collectCandidates(level, module, ItemStack.EMPTY,
                        allowFluidProcessing));
            }
        }
        return resolve(level, inputSlots, inputFluidTanks, candidates);
    }

    private static Optional<ExecutionPlan> resolve(Level level,
                                                   List<? extends IInventorySlot> inputSlots,
                                                   List<? extends IExtendedFluidTank> inputFluidTanks,
                                                   List<Candidate> candidates) {
        List<ItemStack> inventory = inputSlots.stream().map(IInventorySlot::getStack).toList();
        List<FluidStack> fluids = inputFluidTanks.stream().map(tank -> tank.getFluid().copy()).toList();
        CandidateMatch best = candidates.stream()
                .map(candidate -> match(candidate, inventory, fluids))
                .flatMap(Optional::stream)
                .max(CandidateMatch.ORDER)
                .orElse(null);
        if (best == null) {
            return Optional.empty();
        }
        List<ItemStack> results = best.candidate.resultFactory.apply(level, best.match);
        return Optional.of(new ExecutionPlan(best.candidate.id, best.candidate.duration,
                best.match.stackUses, best.match.catalystUses, best.match.fluidUses,
                results, best.candidate.fluidOutputs));
    }

    private static List<ItemStack> distinctStacks(List<ItemStack> stacks) {
        List<ItemStack> distinct = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (distinct.stream().noneMatch(existing ->
                    ItemStack.isSameItemSameTags(existing, stack))) {
                distinct.add(copyWithCount(stack, 1));
            }
        }
        return distinct;
    }

    private static boolean isSupportedModule(ItemStack stack) {
        return stack.is(AllBlocks.DEPLOYER.asItem())
                || stack.is(AllBlocks.MECHANICAL_SAW.asItem())
                || stack.is(AllBlocks.MECHANICAL_PRESS.asItem())
                || stack.is(AllBlocks.MILLSTONE.asItem())
                || stack.is(AllBlocks.CRUSHING_WHEEL.asItem())
                || stack.is(AllBlocks.ENCASED_FAN.asItem())
                || stack.is(AllBlocks.MECHANICAL_CRAFTER.asItem())
                || stack.is(AllBlocks.MECHANICAL_MIXER.asItem())
                || stack.is(AllBlocks.SPOUT.asItem())
                || stack.is(AllBlocks.ITEM_DRAIN.asItem());
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
            ResourceLocation displayId = new ResourceLocation(candidate.id.getNamespace(),
                    candidate.id.getPath() + "/" + variant);
            List<DisplayInput> inputs = mergeDisplayInputs(candidate.requirements);
            target.add(new DisplayRecipe(displayId,
                    Component.translatable("jei.mekanicalcreate.process." + candidate.process),
                    module, condition, inputs, candidate.displayOutputs,
                    candidate.fluidRequirements.stream()
                            .map(value -> new DisplayFluidInput(value.ingredient, value.amount)).toList(),
                    candidate.displayFluidOutputs(),
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
                        && ItemStack.isSameItemSameTags(firstItem, secondItems[index])) {
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

    private static List<Candidate> collectCandidates(Level level, ItemStack module, ItemStack condition,
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
            addSequenced(candidates, recipes, ModuleKind.DEPLOYER, allowFluidProcessing);
        } else if (module.is(AllBlocks.MECHANICAL_PRESS.asItem())) {
            RecipeType<PressingRecipe> type = AllRecipeTypes.PRESSING.getType();
            addProcessing(candidates, recipes.getAllRecipesFor(type),
                    "pressing", 20, false);
            if (allowFluidProcessing) {
                RecipeType<CompactingRecipe> compacting = AllRecipeTypes.COMPACTING.getType();
                addProcessing(candidates, recipes.getAllRecipesFor(compacting),
                        "compacting", 35, false);
            }
            addSequenced(candidates, recipes, ModuleKind.PRESS, allowFluidProcessing);
        } else if (module.is(AllBlocks.MECHANICAL_SAW.asItem())) {
            RecipeType<CuttingRecipe> type = AllRecipeTypes.CUTTING.getType();
            addProcessing(candidates, recipes.getAllRecipesFor(type),
                    "cutting", 20, false);
            addSequenced(candidates, recipes, ModuleKind.SAW, allowFluidProcessing);
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
            addSequenced(candidates, recipes, ModuleKind.SPOUT, true);
        } else if (allowFluidProcessing && module.is(AllBlocks.ITEM_DRAIN.asItem())) {
            RecipeType<EmptyingRecipe> type = AllRecipeTypes.EMPTYING.getType();
            addProcessing(candidates, recipes.getAllRecipesFor(type),
                    "emptying", 35, false);
        }
        return candidates;
    }

    private static <R extends ProcessingRecipe<?>> void addProcessing(
            List<Candidate> target, List<R> recipes, String suffix,
            int priority, boolean deployer) {
        for (R recipe : recipes) {
            if (!AllRecipeTypes.CAN_BE_AUTOMATED.test(recipe)) {
                continue;
            }
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
                    .map(value -> new FluidRequirement(value, value.getRequiredAmount())).toList();
            if (requirements.isEmpty() && fluidRequirements.isEmpty()) {
                continue;
            }
            int duration = recipe.getProcessingDuration() > 0
                    ? Math.max(20, recipe.getProcessingDuration()) : DEFAULT_DURATION;
            target.add(new Candidate(derivedId(recipe.getId(), suffix), suffix, requirements,
                    fluidRequirements, displayOutputs(recipe.getRollableResults(), false),
                    recipe.getFluidResults().stream().map(FluidStack::copy).toList(), 0, 1,
                    1, priority, duration,
                    (level, match) -> appendRemainders(recipe.rollResults(), match)));
        }
    }

    private static void addSequenced(List<Candidate> target, RecipeManager manager,
                                     ModuleKind selectedModule, boolean allowFluidProcessing) {
        RecipeType<SequencedAssemblyRecipe> type = AllRecipeTypes.SEQUENCED_ASSEMBLY.getType();
        for (SequencedAssemblyRecipe recipe : manager.getAllRecipesFor(type)) {
            if (recipe.getSequence().isEmpty() || recipe.resultPool.isEmpty()) {
                continue;
            }
            List<Requirement> requirements = new ArrayList<>();
            requirements.add(new Requirement(recipe.getIngredient(), 1, true, -1));
            boolean containsSelectedModule = false;
            boolean supported = true;
            int loops = Math.max(1, recipe.getLoops());
            for (SequencedRecipe<?> step : recipe.getSequence()) {
                ProcessingRecipe<?> processing = step.getRecipe();
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
                } else if (processing instanceof FillingRecipe filling) {
                    if (!allowFluidProcessing) {
                        supported = false;
                        break;
                    }
                    containsSelectedModule |= selectedModule == ModuleKind.SPOUT;
                } else {
                    supported = false;
                    break;
                }
            }
            if (!supported || !containsSelectedModule) {
                continue;
            }
            List<FluidRequirement> fluidRequirements = new ArrayList<>();
            for (SequencedRecipe<?> step : recipe.getSequence()) {
                if (step.getRecipe() instanceof FillingRecipe filling) {
                    FluidIngredient fluid = filling.getRequiredFluid();
                    fluidRequirements.add(new FluidRequirement(fluid,
                            Math.multiplyExact(fluid.getRequiredAmount(), loops)));
                }
            }
            target.add(new Candidate(derivedId(recipe.getId(), "sequenced_assembly"), "sequenced_assembly",
                    requirements, fluidRequirements, displayOutputs(recipe.resultPool, true), List.of(),
                    recipe.getSequence().size(), loops,
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

    private static <R extends ProcessingRecipe<?>> void addFanProcessing(
            List<Candidate> target, List<R> recipes, String suffix, FanProcessingType type) {
        for (R recipe : recipes) {
            if (!AllRecipeTypes.CAN_BE_AUTOMATED.test(recipe) || recipe.getIngredients().isEmpty()) {
                continue;
            }
            target.add(new Candidate(derivedId(recipe.getId(), suffix), suffix,
                    List.of(new Requirement(recipe.getIngredients().get(0), 1, true, 0)),
                    displayOutputs(recipe.getRollableResults(), false), 0, 1,
                    1, 20, DEFAULT_DURATION,
                    (level, match) -> fanResults(type, level, match)));
        }
    }

    private static <R extends AbstractCookingRecipe> void addFanCooking(
            List<Candidate> target, List<R> recipes, String suffix,
            FanProcessingType type, int priority) {
        for (R recipe : recipes) {
            if (!AllRecipeTypes.CAN_BE_AUTOMATED.test(recipe) || recipe.getIngredients().isEmpty()) {
                continue;
            }
            ItemStack output = recipe.getResultItem(net.minecraft.core.RegistryAccess.EMPTY);
            target.add(new Candidate(derivedId(recipe.getId(), suffix), suffix,
                    List.of(new Requirement(recipe.getIngredients().get(0), 1, true, 0)),
                    output.isEmpty() ? List.of() : List.of(new DisplayOutput(output, 1)), 0, 1,
                    1, priority, DEFAULT_DURATION,
                    (level, match) -> fanResults(type, level, match)));
        }
    }

    private static List<ItemStack> fanResults(FanProcessingType type, Level level, Match match) {
        ItemStack input = match.firstAssignedStack();
        List<ItemStack> results = type.process(copyWithCount(input, 1), level);
        return results == null ? List.of() : appendRemainders(results, match);
    }

    private static <R extends CraftingRecipe> void addCrafting(
            List<Candidate> target, List<R> recipes, String suffix, int priority, Level level) {
        for (R recipe : recipes) {
            if (AllRecipeTypes.shouldIgnoreInAutomation(recipe)) {
                continue;
            }
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
            target.add(new Candidate(derivedId(recipe.getId(), suffix), suffix, requirements,
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
                grid.set(position, copyWithCount(match.assignedByRequirement.get(requirement), 1));
            }
        }
        TransientCraftingContainer input = new TransientCraftingContainer(CRAFTING_MENU, width, height);
        for (int slot = 0; slot < grid.size(); slot++) {
            input.setItem(slot, grid.get(slot));
        }
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
                                                   List<FluidStack> fluids) {
        Match oneRun = allocate(candidate, inventory, 1);
        List<FluidUse> oneRunFluids = allocateFluids(candidate, fluids, 1);
        if (oneRun == null || oneRunFluids == null) {
            return Optional.empty();
        }
        oneRun.fluidUses = oneRunFluids;
        int maximumRuns = 1;
        int totalItems = inventory.stream().mapToInt(ItemStack::getCount).sum();
        int consumedPerRun = candidate.consumedPerRun();
        int totalFluid = fluids.stream().mapToInt(FluidStack::getAmount).sum();
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
            FluidRequirement requirementValue = candidate.fluidRequirements.get(requirement);
            int amount = Math.multiplyExact(requirementValue.amount, multiplier);
            demand += amount;
            flow.addEdge(firstRequirement + requirement, sink, amount);
            for (int tank = 0; tank < tankCount; tank++) {
                FluidStack stack = fluids.get(tank);
                if (!stack.isEmpty() && requirementValue.ingredient.test(stack)) {
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
                uses.add(new FluidUse(tank, usedByTank[tank], new FluidStack(fluids.get(tank), 1)));
            }
        }
        return List.copyOf(uses);
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
                catalystUses.add(new CatalystUse(slot, copyWithCount(inventory.get(slot), 1)));
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
                            assigned.set(originalRequirement, copyWithCount(inventory.get(slot), 1));
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
                        copyWithCount(inventory.get(slot), 1)));
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
            if (ItemStack.isSameItemSameTags(stack, toAdd)
                    && stack.getCount() + toAdd.getCount() <= stack.getMaxStackSize()) {
                stack.grow(toAdd.getCount());
                return;
            }
        }
        stacks.add(toAdd);
    }

    private static ItemStack copyWithCount(ItemStack stack, int count) {
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
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
        return pool.get(pool.size() - 1).getStack().copy();
    }

    private static ResourceLocation derivedId(ResourceLocation source, String suffix) {
        return new ResourceLocation(source.getNamespace(),
                source.getPath() + "/mekanicalcreate_" + suffix);
    }

    static final class ExecutionPlan {
        private final ResourceLocation id;
        private final int duration;
        private final List<StackUse> stackUses;
        private final List<CatalystUse> catalystUses;
        private final List<FluidUse> fluidUses;
        private final List<ItemStack> itemResults;
        private final List<FluidStack> fluidResults;

        private ExecutionPlan(ResourceLocation id, int duration, List<StackUse> stackUses,
                              List<CatalystUse> catalystUses, List<FluidUse> fluidUses,
                              List<ItemStack> itemResults, List<FluidStack> fluidResults) {
            this.id = id;
            this.duration = duration;
            this.stackUses = List.copyOf(stackUses);
            this.catalystUses = List.copyOf(catalystUses);
            this.fluidUses = List.copyOf(fluidUses);
            this.itemResults = itemResults.stream().filter(stack -> !stack.isEmpty())
                    .map(ItemStack::copy).toList();
            this.fluidResults = fluidResults.stream().filter(stack -> !stack.isEmpty())
                    .map(FluidStack::copy).toList();
        }

        ResourceLocation id() {
            return id;
        }

        int duration() {
            return duration;
        }

        List<ItemStack> itemResults() {
            return itemResults;
        }

        List<FluidStack> fluidResults() {
            return fluidResults;
        }

        boolean stillValid(List<? extends IInventorySlot> slots,
                           List<? extends IExtendedFluidTank> fluidTanks) {
            for (StackUse use : stackUses) {
                ItemStack current = slots.get(use.slot).getStack();
                if (current.getCount() < use.count
                        || !ItemStack.isSameItemSameTags(current, use.expected)) {
                    return false;
                }
            }
            for (CatalystUse use : catalystUses) {
                ItemStack current = slots.get(use.slot).getStack();
                if (current.isEmpty() || !ItemStack.isSameItemSameTags(current, use.expected)) {
                    return false;
                }
            }
            for (FluidUse use : fluidUses) {
                FluidStack current = fluidTanks.get(use.tank).getFluid();
                if (current.getAmount() < use.amount
                        || !current.isFluidEqual(use.expected)
                        || !FluidStack.areFluidStackTagsEqual(current, use.expected)) {
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

    private enum ModuleKind {
        DEPLOYER,
        PRESS,
        SAW,
        SPOUT
    }

    private record Requirement(Ingredient ingredient, int count, boolean consumed, int craftPosition) {
    }

    private record FluidRequirement(FluidIngredient ingredient, int amount) {
    }

    private record Candidate(ResourceLocation id, String process, List<Requirement> requirements,
                             List<FluidRequirement> fluidRequirements,
                             List<DisplayOutput> displayOutputs, List<FluidStack> fluidOutputs,
                             int sequenceSteps, int loops,
                             int complexity, int priority, int duration, ResultFactory resultFactory) {
        private Candidate(ResourceLocation id, String process, List<Requirement> requirements,
                          List<DisplayOutput> displayOutputs, int sequenceSteps, int loops,
                          int complexity, int priority, int duration, ResultFactory resultFactory) {
            this(id, process, requirements, List.of(), displayOutputs, List.of(),
                    sequenceSteps, loops, complexity, priority, duration, resultFactory);
        }

        int consumedPerRun() {
            return requirements.stream().filter(Requirement::consumed).mapToInt(Requirement::count).sum();
        }

        int fluidConsumedPerRun() {
            return fluidRequirements.stream().mapToInt(FluidRequirement::amount).sum();
        }

        List<FluidStack> displayFluidOutputs() {
            return fluidOutputs;
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

    public record DisplayFluidInput(FluidIngredient ingredient, int amount) {
    }

    public record DisplayFluidOutput(FluidStack stack) {
        public DisplayFluidOutput {
            stack = stack.copy();
        }
    }

    public record DisplayRecipe(ResourceLocation id, Component processName, ItemStack module,
                                ItemStack condition, List<DisplayInput> inputs,
                                List<DisplayOutput> outputs,
                                List<DisplayFluidInput> fluidInputs,
                                List<FluidStack> fluidOutputs,
                                int sequenceSteps, int loops) {
        public DisplayRecipe {
            module = copyWithCount(module, 1);
            condition = copyWithCount(condition, 1);
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
