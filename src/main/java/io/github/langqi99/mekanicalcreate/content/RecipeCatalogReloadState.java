package io.github.langqi99.mekanicalcreate.content;

/**
 * Coalesces recipe, tag and world updates before a display catalog is built.
 * Inspired by Useless Mod's alloy-furnace catalog loading lifecycle.
 */
public final class RecipeCatalogReloadState {
    private static final int PACKET_WAIT_TICKS = 100;
    private boolean dirty;
    private long recipesVersion;
    private long consumedRecipesVersion;
    private long tagsVersion;
    private long consumedTagsVersion;
    private int waitingTicks;
    private int quietTicks;

    public void recipesUpdated() {
        recipesVersion++;
        dirty = true;
        quietTicks = 0;
    }

    public void tagsUpdated() {
        tagsVersion++;
        dirty = true;
        quietTicks = 0;
    }

    public void worldJoined() {
        dirty = true;
        quietTicks = 0;
    }

    public boolean shouldBeginBuild() {
        if (!dirty || recipesVersion == 0) return false;
        waitingTicks++;
        quietTicks++;
        // Tags may precede recipes during login or reload. Consume a fresh
        // recipe/tag pair in either order, after one quiet tick for coalescing.
        // A tag-only packet must not reuse the previous reload's recipe signal.
        if (waitingTicks < PACKET_WAIT_TICKS
                && (quietTicks < 2 || recipesVersion == consumedRecipesVersion
                        || tagsVersion == consumedTagsVersion)) return false;
        dirty = false;
        consumedRecipesVersion = recipesVersion;
        consumedTagsVersion = tagsVersion;
        waitingTicks = 0;
        return true;
    }

    public boolean isWaiting() {
        return dirty;
    }

    public void reset() {
        dirty = false;
        recipesVersion = 0;
        consumedRecipesVersion = 0;
        tagsVersion = 0;
        consumedTagsVersion = 0;
        waitingTicks = 0;
        quietTicks = 0;
    }
}
