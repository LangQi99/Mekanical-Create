package io.github.langqi99.mekanicalcreate.content;

import com.simibubi.create.AllBlocks;
import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MekanicalCreate.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DisplayStackGameTests {
    private DisplayStackGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void distinctStacksPreserveComponentsOrderAndCopies(GameTestHelper helper) {
        ItemStack first = new ItemStack(Items.STONE, 12);
        first.set(DataComponents.CUSTOM_NAME, Component.literal("first"));
        ItemStack duplicate = first.copyWithCount(37);
        ItemStack other = new ItemStack(Items.STONE, 4);
        other.set(DataComponents.CUSTOM_NAME, Component.literal("other"));
        ItemStack dirt = new ItemStack(Items.DIRT, 5);

        List<ItemStack> result = SimulationRecipeResolver.distinctStacks(
                List.of(first, dirt, duplicate, other, dirt.copyWithCount(20)));
        helper.assertValueEqual(result.size(), 3, "counts must not create extra variants");
        helper.assertTrue(ItemStack.isSameItemSameComponents(result.get(0), first),
                "first component variant or insertion order was lost");
        helper.assertTrue(result.get(1).is(Items.DIRT), "item order was changed");
        helper.assertTrue(ItemStack.isSameItemSameComponents(result.get(2), other),
                "different component variants were merged");
        helper.assertTrue(result.stream().allMatch(stack -> stack.getCount() == 1),
                "display stack counts must be normalized to one");
        result.getFirst().set(DataComponents.CUSTOM_NAME, Component.literal("changed"));
        helper.assertValueEqual(first.getCount(), 12, "input count was changed");
        helper.assertTrue(Component.literal("first").equals(first.get(DataComponents.CUSTOM_NAME)),
                "returned stack aliases its input");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void distinctStacksKeepLargeComponentVariantCatalogs(GameTestHelper helper) {
        List<ItemStack> stacks = new ArrayList<>();
        int variants = 10_000;
        for (int index = 0; index < variants; index++) {
            ItemStack stack = new ItemStack(Items.STONE, 3);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("variant_" + index));
            stacks.add(stack);
            stacks.add(stack.copyWithCount(64));
        }
        List<ItemStack> result = SimulationRecipeResolver.distinctStacks(stacks);
        helper.assertValueEqual(result.size(), variants, "large catalog lost or duplicated variants");
        for (int index = 0; index < variants; index++) {
            helper.assertTrue(ItemStack.isSameItemSameComponents(result.get(index), stacks.get(2 * index)),
                    "large catalog changed variant order at " + index);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void ingredientComparisonPreservesOrderCountsAndComponents(GameTestHelper helper) {
        ItemStack first = new ItemStack(Items.STONE, 12);
        first.set(DataComponents.CUSTOM_NAME, Component.literal("first"));
        ItemStack other = new ItemStack(Items.STONE, 4);
        other.set(DataComponents.CUSTOM_NAME, Component.literal("other"));
        Ingredient ordered = Ingredient.of(first, other, new ItemStack(Items.DIRT));
        Ingredient reordered = Ingredient.of(new ItemStack(Items.DIRT, 15),
                other.copyWithCount(64), first.copyWithCount(1));
        helper.assertTrue(SimulationRecipeResolver.sameIngredient(ordered, reordered),
                "option order and stack count changed ingredient equality");
        helper.assertTrue(SimulationRecipeResolver.sameIngredient(ordered, ordered),
                "an ingredient must equal itself");
        helper.assertTrue(!SimulationRecipeResolver.sameIngredient(ordered,
                        Ingredient.of(first, new ItemStack(Items.STONE), new ItemStack(Items.DIRT))),
                "different component options were merged");
        helper.assertTrue(!SimulationRecipeResolver.sameIngredient(ordered, Ingredient.of(first)),
                "different option counts were merged");
        helper.assertTrue(SimulationRecipeResolver.sameIngredient(Ingredient.EMPTY, Ingredient.of()),
                "empty ingredients must compare equally");

        // Preserve the existing multiset contract even for repeated options.
        ordered.getItems()[1] = first.copyWithCount(6);
        helper.assertTrue(!SimulationRecipeResolver.sameIngredient(ordered, reordered),
                "different option multiplicities were merged");
        reordered.getItems()[1] = first.copyWithCount(9);
        helper.assertTrue(SimulationRecipeResolver.sameIngredient(ordered, reordered),
                "equal repeated options failed to compare across different orders");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void ingredientComparisonKeepsLargeReorderedCatalogs(GameTestHelper helper) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int index = 0; index < 10_000; index++) {
            ItemStack stack = new ItemStack(Items.STONE, 3);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("variant_" + index));
            stacks.add(stack);
        }
        Ingredient first = Ingredient.of(stacks.stream());
        Collections.reverse(stacks);
        Ingredient reversed = Ingredient.of(stacks.stream().map(stack -> stack.copyWithCount(64)));
        helper.assertTrue(SimulationRecipeResolver.sameIngredient(first, reversed),
                "large reordered component catalogs must remain equal");
        reversed.getItems()[0] = new ItemStack(Items.DIRT);
        helper.assertTrue(!SimulationRecipeResolver.sameIngredient(first, reversed),
                "a different option in a large catalog was missed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void displayCatalogDefersMatchingAndRebuildsAfterReload(GameTestHelper helper) {
        SimulationRecipeResolver.clearCache();
        SimulationRecipeResolver.resetDiagnostics();
        var displays = SimulationRecipeResolver.getDisplayRecipes(helper.getLevel());
        helper.assertTrue(!displays.itemRecipes().isEmpty() && !displays.fluidRecipes().isEmpty(),
                "display generation lost the recipe catalogs");
        var beforeUse = SimulationRecipeResolver.diagnostics();
        helper.assertValueEqual(beforeUse.indexBuilds(), 0L,
                "display generation eagerly built machine indexes");
        helper.assertValueEqual(beforeUse.metadataBuilds(), 0L,
                "display generation eagerly compiled matching metadata");

        BasicInventorySlot input = BasicInventorySlot.at(null, 0, 0);
        input.setStack(new ItemStack(Items.OAK_LOG));
        var plan = SimulationRecipeResolver.resolve(helper.getLevel(),
                AllBlocks.MECHANICAL_CRAFTER.asStack(), ItemStack.EMPTY, List.of(input),
                List.of(), false, new RecipeRoundRobinState());
        helper.assertTrue(plan.isPresent(), "lazy catalog could not resolve log crafting");
        helper.assertTrue(plan.get().itemResults().stream().anyMatch(stack ->
                        stack.is(Items.OAK_PLANKS) && stack.getCount() == 4),
                "lazy catalog changed the crafting result");
        var afterUse = SimulationRecipeResolver.diagnostics();
        helper.assertValueEqual(afterUse.indexBuilds(), 1L,
                "first factory use must build only the requested catalog index");
        helper.assertTrue(afterUse.metadataBuilds() > 0,
                "first factory use did not compile its matching metadata");

        SimulationRecipeResolver.prewarm(helper.getLevel());
        var warmed = SimulationRecipeResolver.diagnostics();
        helper.assertTrue(warmed.indexBuilds() > afterUse.indexBuilds(),
                "server prewarming did not initialize the other matching indexes");
        SimulationRecipeResolver.prewarm(helper.getLevel());
        helper.assertValueEqual(SimulationRecipeResolver.diagnostics().indexBuilds(),
                warmed.indexBuilds(), "prewarming rebuilt cached indexes");

        SimulationRecipeResolver.clearCache();
        var reloaded = SimulationRecipeResolver.resolve(helper.getLevel(),
                AllBlocks.MECHANICAL_CRAFTER.asStack(), ItemStack.EMPTY, List.of(input),
                List.of(), false, new RecipeRoundRobinState());
        helper.assertTrue(reloaded.isPresent(), "catalog did not resolve after reload");
        helper.assertValueEqual(SimulationRecipeResolver.diagnostics().indexBuilds(),
                warmed.indexBuilds() + 1, "reload reused a stale matching index");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void sharedCatalogReadinessAndGenerationAreIsolated(GameTestHelper helper) {
        var level = helper.getLevel();
        SimulationRecipeResolver.clearCache();
        SimulationRecipeResolver.resetDiagnostics();
        long machineEpoch = SimulationRecipeResolver.cacheEpoch();
        helper.assertTrue(SimulationDisplayRecipeCatalog.peek(level) == null,
                "readiness lookup must not build a missing catalog");
        helper.assertValueEqual(SimulationRecipeResolver.diagnostics().catalogBuilds(), 0L,
                "readiness lookup performed recipe work");
        SimulationDisplayRecipeCatalog.beginRebuild(level);
        long firstGeneration = SimulationDisplayRecipeCatalog.generation(level);
        SimulationDisplayRecipeCatalog.advance(level, 0);
        helper.assertTrue(SimulationDisplayRecipeCatalog.peek(level) == null,
                "zero-budget work unexpectedly published a catalog");
        helper.assertValueEqual(SimulationRecipeResolver.cacheEpoch(), machineEpoch,
                "display reload invalidated machine execution plans");
        SimulationDisplayRecipeCatalog.invalidate(new RecipeManager(level.registryAccess()));
        helper.assertValueEqual(SimulationDisplayRecipeCatalog.generation(level), firstGeneration,
                "invalidating another recipe manager canceled this catalog");
        SimulationDisplayRecipeCatalog.invalidate(level.getRecipeManager());
        SimulationDisplayRecipeCatalog.advance(level, 16_000_000L);
        helper.assertTrue(SimulationDisplayRecipeCatalog.peek(level) == null,
                "a canceled generation was published");
        var ready = SimulationDisplayRecipeCatalog.getOrBuild(level);
        helper.assertTrue(ready.generation() > firstGeneration, "rebuild reused the old generation");
        helper.assertTrue(SimulationRecipeResolver.getDisplayRecipes(level) == ready.recipes(),
                "display consumers did not reuse the shared snapshot");
        helper.assertValueEqual(SimulationRecipeResolver.diagnostics().indexBuilds(), 0L,
                "shared display snapshot eagerly built a machine index");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void chargingSimulationResumesAndPreservesStatefulAcceptance(GameTestHelper helper) {
        ItemStack source = new ItemStack(Items.STONE, 7);
        var storage = new StatefulTestEnergy();
        var work = SimulationRecipeResolver.EnergyChargeWork.fromStorage(
                source.copyWithCount(1), storage, 36);
        helper.assertTrue(work != null, "valid charging storage was rejected");
        helper.assertTrue(!work.advance(0), "expired budget did not yield");
        helper.assertValueEqual(storage.stored, 100, "yield changed stored energy");
        helper.assertTrue(work.advance(Long.MAX_VALUE), "resumed simulation did not complete");
        var result = work.result();
        helper.assertTrue(result != null, "stateful storage produced no charging result");
        helper.assertValueEqual(storage.stored, 210, "stateful acceptance was approximated");
        helper.assertValueEqual(storage.calls, 8, "charging did not stop when acceptance became zero");
        helper.assertValueEqual(result.duration(), 4, "charging duration changed");
        helper.assertValueEqual(result.energyPerTick(), 28L, "charging energy cost changed");
        helper.assertValueEqual(result.output().getCount(), 1, "charged output count was not normalized");
        helper.assertValueEqual(source.getCount(), 7, "charging changed the input stack");
        helper.succeed();
    }

    private static final class StatefulTestEnergy implements IEnergyStorage {
        private int stored = 100;
        private int calls;

        @Override
        public int receiveEnergy(int maximum, boolean simulate) {
            int accepted = Math.min(maximum, simulate || calls < 3 ? 30 : calls < 7 ? 5 : 0);
            if (!simulate) {
                calls++;
                stored += accepted;
            }
            return accepted;
        }

        @Override public int extractEnergy(int maximum, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return stored; }
        @Override public int getMaxEnergyStored() { return 1_000; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    }
}
