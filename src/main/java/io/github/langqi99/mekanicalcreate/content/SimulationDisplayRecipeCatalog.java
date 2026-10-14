package io.github.langqi99.mekanicalcreate.content;

import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Shared display snapshots with per-manager lifetimes, following Useless Mod's
 * alloy-furnace catalog design. Readiness checks never start a synchronous build.
 */
public final class SimulationDisplayRecipeCatalog {
    private static final Map<RecipeManager, BuildState> STATES = new WeakHashMap<>();
    private static final AtomicLong GENERATIONS = new AtomicLong();

    private SimulationDisplayRecipeCatalog() {
    }

    public static synchronized void invalidate(RecipeManager manager) {
        STATES.remove(manager);
    }

    public static synchronized void invalidateAll() {
        STATES.clear();
    }

    @Nullable
    public static synchronized Snapshot peek(Level level) {
        BuildState state = STATES.get(level.getRecipeManager());
        return state == null ? null : state.ready;
    }

    public static synchronized long generation(Level level) {
        BuildState state = STATES.get(level.getRecipeManager());
        return state == null ? 0 : state.generation;
    }

    public static synchronized int producedCount(Level level) {
        BuildState state = STATES.get(level.getRecipeManager());
        if (state == null) return 0;
        return state.ready != null
                ? state.ready.recipes.itemRecipes().size() + state.ready.recipes.fluidRecipes().size()
                : state.build == null ? 0 : state.build.producedCount();
    }

    public static synchronized boolean failed(Level level) {
        BuildState state = STATES.get(level.getRecipeManager());
        return state != null && state.failure != null;
    }

    public static void beginRebuild(Level level) {
        RecipeManager manager = level.getRecipeManager();
        // Clear only this side's source catalogs. Client reloads leave the
        // integrated server's warmed indexes and current machine plans intact.
        SimulationRecipeResolver.invalidateRecipeData(manager);
        BuildState state = new BuildState(GENERATIONS.incrementAndGet(),
                new SimulationRecipeResolver.DisplayRecipeBuild(level));
        synchronized (SimulationDisplayRecipeCatalog.class) {
            STATES.put(manager, state);
        }
        MekanicalCreate.LOGGER.info("Queued factory display catalog: generation={}, source recipes={}",
                state.generation, manager.getRecipes().size());
    }

    public static void advance(Level level, long budgetNanos) {
        RecipeManager manager = level.getRecipeManager();
        BuildState state;
        synchronized (SimulationDisplayRecipeCatalog.class) {
            state = STATES.get(manager);
        }
        if (state == null) return;
        synchronized (state) {
            if (state.build == null) return;
            long started = System.nanoTime();
            try {
                boolean complete = state.build.advance(budgetNanos);
                long elapsed = System.nanoTime() - started;
                state.workNanos += elapsed;
                state.maxSliceNanos = Math.max(state.maxSliceNanos, elapsed);
                if (!complete) return;
                Snapshot ready = new Snapshot(state.generation, state.build.result());
                synchronized (SimulationDisplayRecipeCatalog.class) {
                    // A reload/disconnect may have replaced this generation
                    // while it was being built. Never publish its old result.
                    if (STATES.get(manager) != state) return;
                    state.ready = ready;
                    state.build = null;
                }
                MekanicalCreate.LOGGER.info(
                        "Built shared factory display catalog: generation={}, item={}, fluid={}, "
                                + "work={} ms, max slice={} ms, elapsed={} ms",
                        state.generation, ready.recipes.itemRecipes().size(),
                        ready.recipes.fluidRecipes().size(), state.workNanos / 1_000_000,
                        state.maxSliceNanos / 1_000_000,
                        (System.nanoTime() - state.started) / 1_000_000);
            } catch (LinkageError | RuntimeException exception) {
                state.build = null;
                state.failure = exception;
                MekanicalCreate.LOGGER.error("Could not prepare factory display catalog", exception);
            }
        }
    }

    /** Synchronous access for non-JEI consumers; JEI uses only peek(). */
    public static Snapshot getOrBuild(Level level) {
        while (true) {
            Snapshot ready = peek(level);
            if (ready != null) return ready;
            if (generation(level) == 0) beginRebuild(level);
            advance(level, 16_000_000L);
            if (failed(level)) throw new IllegalStateException("Factory display catalog build failed");
        }
    }

    public record Snapshot(long generation, SimulationRecipeResolver.DisplayRecipes recipes) {
    }

    private static final class BuildState {
        private final long generation;
        private final long started = System.nanoTime();
        private SimulationRecipeResolver.DisplayRecipeBuild build;
        private volatile Snapshot ready;
        private volatile Throwable failure;
        private long workNanos;
        private long maxSliceNanos;

        private BuildState(long generation, SimulationRecipeResolver.DisplayRecipeBuild build) {
            this.generation = generation;
            this.build = build;
        }
    }
}
