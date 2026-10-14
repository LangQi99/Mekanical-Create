package io.github.langqi99.mekanicalcreate.client.jei;

import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import io.github.langqi99.mekanicalcreate.client.SimulationChamberScreen;
import io.github.langqi99.mekanicalcreate.client.FluidMekanicalFactoryScreen;
import io.github.langqi99.mekanicalcreate.content.SimulationRecipeResolver;
import io.github.langqi99.mekanicalcreate.content.SimulationDisplayRecipeCatalog;
import io.github.langqi99.mekanicalcreate.content.RecipeRegistrationQueue;
import io.github.langqi99.mekanicalcreate.registry.ModBlocks;
import io.github.langqi99.mekanicalcreate.registry.ModMenus;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jetbrains.annotations.NotNull;

@JeiPlugin
public final class MekanicalCreateJeiPlugin implements IModPlugin {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            MekanicalCreate.MOD_ID, "jei_plugin");
    private static final RecipeRegistrationQueue<SimulationRecipeResolver.DisplayRecipe> QUEUE =
            new RecipeRegistrationQueue<>();
    private static IJeiRuntime runtime;
    private static SimulationDisplayRecipeCatalog.Snapshot registeredSnapshot;
    private static RecipeManager registeredManager;
    private static int batchSize = 64;
    private static long publicationStarted;
    private static long publicationWork;
    private static long maxBatchNanos;
    private static boolean publicationFailed;
    private static boolean loggedComplete;

    private static ItemStack chamberStack() {
        return new ItemStack(ModBlocks.SIMULATION_CHAMBER.get());
    }

    private static List<ItemStack> factoryStacks() {
        return List.of(
                chamberStack(),
                new ItemStack(ModBlocks.BASIC_MEKANICAL_FACTORY.get()),
                new ItemStack(ModBlocks.ADVANCED_MEKANICAL_FACTORY.get()),
                new ItemStack(ModBlocks.ELITE_MEKANICAL_FACTORY.get()),
                new ItemStack(ModBlocks.ULTIMATE_MEKANICAL_FACTORY.get())
        );
    }

    private static ItemStack fluidFactoryStack() {
        return new ItemStack(ModBlocks.FLUID_MEKANICAL_FACTORY.get());
    }

    @NotNull
    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new SimulationChamberRecipeCategory(guiHelper, chamberStack(), false),
                new SimulationChamberRecipeCategory(guiHelper, fluidFactoryStack(), true));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        long started = System.nanoTime();
        runtime = null;
        resetPublication();
        // Catalog preparation and JEI indexing are separate, budgeted phases.
        // Even an already-ready directory is published from frame callbacks.
        registration.addRecipes(SimulationChamberRecipeCategory.TYPE, List.of());
        registration.addRecipes(SimulationChamberRecipeCategory.FLUID_TYPE, List.of());
        for (ItemStack factory : factoryStacks()) {
            registration.addItemStackInfo(factory,
                    net.minecraft.network.chat.Component.translatable("jei.mekanicalcreate.info"));
        }
        registration.addItemStackInfo(fluidFactoryStack(),
                net.minecraft.network.chat.Component.translatable(
                        "jei.mekanicalcreate.fluid_info"));
        MekanicalCreate.LOGGER.info("Factory JEI registration deferred; initial entry took {} microseconds",
                (System.nanoTime() - started) / 1_000L);
    }

    @Override
    public void onRuntimeAvailable(@NotNull IJeiRuntime jeiRuntime) {
        resetPublication();
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        resetPublication();
    }

    private static void resetPublication() {
        QUEUE.reset(0, List.of(), List.of());
        registeredSnapshot = null;
        registeredManager = null;
        publicationFailed = false;
        loggedComplete = false;
        batchSize = 64;
        publicationWork = 0;
        maxBatchNanos = 0;
    }

    /** Called only on the client render thread; never from a server reload. */
    public static void advanceRegistration(long budgetNanos) {
        ClientLevel level = Minecraft.getInstance().level;
        if (runtime == null || level == null || budgetNanos <= 0) return;
        var snapshot = SimulationDisplayRecipeCatalog.peek(level);
        if (snapshot != registeredSnapshot || level.getRecipeManager() != registeredManager) {
            hideRegisteredRecipes();
            resetPublication();
            registeredSnapshot = snapshot;
            registeredManager = level.getRecipeManager();
            publicationStarted = System.nanoTime();
            if (snapshot != null) {
                QUEUE.reset(snapshot.generation(), snapshot.recipes().itemRecipes(),
                        snapshot.recipes().fluidRecipes());
            }
        }
        if (snapshot == null || publicationFailed || loggedComplete) return;
        long deadline = System.nanoTime() + budgetNanos;
        try {
            while (!QUEUE.isComplete() && System.nanoTime() < deadline) {
                var batch = QUEUE.nextBatch(batchSize);
                long started = System.nanoTime();
                runtime.getRecipeManager().addRecipes(batch.fluid()
                        ? SimulationChamberRecipeCategory.FLUID_TYPE
                        : SimulationChamberRecipeCategory.TYPE, batch.recipes());
                long elapsed = System.nanoTime() - started;
                publicationWork += elapsed;
                maxBatchNanos = Math.max(maxBatchNanos, elapsed);
                QUEUE.complete(batch);
                batchSize = (int) Math.clamp(
                        Math.round(batch.recipes().size() * (double) budgetNanos / Math.max(1, elapsed)),
                        1L, 256L);
                if (SimulationDisplayRecipeCatalog.peek(level) != snapshot) return;
            }
            if (QUEUE.isComplete()) {
                loggedComplete = true;
                var recipes = snapshot.recipes();
                long externalSequences = java.util.stream.Stream
                        .concat(recipes.itemRecipes().stream(), recipes.fluidRecipes().stream())
                        .filter(recipe -> recipe.sequenceSteps() > 0)
                        .filter(recipe -> !recipe.id().getNamespace().equals("create")).count();
                MekanicalCreate.LOGGER.info(
                        "Registered {} item and {} fluid factory JEI recipes, including {} "
                                + "external sequenced-assembly variants",
                        recipes.itemRecipes().size(), recipes.fluidRecipes().size(), externalSequences);
                MekanicalCreate.LOGGER.info(
                        "Deferred factory JEI publication: generation={}, work={} ms, max batch={} ms, elapsed={} ms",
                        snapshot.generation(), publicationWork / 1_000_000, maxBatchNanos / 1_000_000,
                        (System.nanoTime() - publicationStarted) / 1_000_000);
            }
        } catch (LinkageError | RuntimeException exception) {
            publicationFailed = true;
            MekanicalCreate.LOGGER.error("Could not publish factory JEI recipes", exception);
        }
    }

    private static void hideRegisteredRecipes() {
        if (!QUEUE.registeredItems().isEmpty()) {
            runtime.getRecipeManager().hideRecipes(SimulationChamberRecipeCategory.TYPE, QUEUE.registeredItems());
        }
        if (!QUEUE.registeredFluids().isEmpty()) {
            runtime.getRecipeManager().hideRecipes(SimulationChamberRecipeCategory.FLUID_TYPE, QUEUE.registeredFluids());
        }
    }

    public static boolean registrationComplete() {
        return registeredSnapshot != null && loggedComplete;
    }

    public static boolean publicationFailed() {
        return publicationFailed;
    }

    public static int registeredCount() {
        return QUEUE.registeredCount();
    }

    public static int totalCount() {
        return QUEUE.totalCount();
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (ItemStack factory : factoryStacks()) {
            registration.addRecipeCatalyst(factory, SimulationChamberRecipeCategory.TYPE);
        }
        registration.addRecipeCatalyst(fluidFactoryStack(),
                SimulationChamberRecipeCategory.FLUID_TYPE);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addRecipeClickArea(SimulationChamberScreen.class,
                123, 55, 28, 8, SimulationChamberRecipeCategory.TYPE);
        registration.addRecipeClickArea(FluidMekanicalFactoryScreen.class,
                158, 43, 84, 8, SimulationChamberRecipeCategory.FLUID_TYPE);
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new SimulationChamberTransferInfo<>(
                ModMenus.SIMULATION_CHAMBER.get(), SimulationChamberRecipeCategory.TYPE));
        registration.addRecipeTransferHandler(new MekanicalFactoryTransferInfo(
                ModMenus.FLUID_MEKANICAL_FACTORY.get(),
                SimulationChamberRecipeCategory.FLUID_TYPE));
    }
}
