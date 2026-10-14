package io.github.langqi99.mekanicalcreate.content;

import java.util.List;

/** A generation-bound publication cursor; failed batches can be retried. */
public final class RecipeRegistrationQueue<T> {
    private long generation;
    private List<T> items = List.of();
    private List<T> fluids = List.of();
    private int itemCursor;
    private int fluidCursor;
    private Batch<T> pending;

    public void reset(long generation, List<T> items, List<T> fluids) {
        this.generation = generation;
        this.items = List.copyOf(items);
        this.fluids = List.copyOf(fluids);
        itemCursor = 0;
        fluidCursor = 0;
        pending = null;
    }

    public Batch<T> nextBatch(int maximumSize) {
        if (maximumSize < 1) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        if (pending != null) {
            return pending;
        }
        boolean fluid = itemCursor == items.size();
        List<T> recipes = fluid ? fluids : items;
        int cursor = fluid ? fluidCursor : itemCursor;
        if (cursor == recipes.size()) {
            return null;
        }
        pending = new Batch<>(generation, fluid,
                recipes.subList(cursor, cursor + Math.min(maximumSize, recipes.size() - cursor)));
        return pending;
    }

    /** Rejects stale or already-committed batches without advancing the cursor. */
    public boolean complete(Batch<T> batch) {
        if (batch == null || batch != pending || batch.generation() != generation) {
            return false;
        }
        if (batch.fluid()) {
            fluidCursor += batch.recipes().size();
        } else {
            itemCursor += batch.recipes().size();
        }
        pending = null;
        return true;
    }

    public List<T> registeredItems() {
        return items.subList(0, itemCursor);
    }

    public List<T> registeredFluids() {
        return fluids.subList(0, fluidCursor);
    }

    public int registeredCount() {
        return itemCursor + fluidCursor;
    }

    public int totalCount() {
        return items.size() + fluids.size();
    }

    public boolean isComplete() {
        return registeredCount() == totalCount();
    }

    public record Batch<T>(long generation, boolean fluid, List<T> recipes) {
    }
}
