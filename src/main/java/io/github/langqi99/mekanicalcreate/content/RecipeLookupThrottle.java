package io.github.langqi99.mekanicalcreate.content;

/**
 * Coalesces adjacent inventory changes without allowing a continuously fed
 * machine to starve. All values are server game ticks.
 */
final class RecipeLookupThrottle {
    static final int QUIET_TICKS = 1;
    static final int MAX_DEBOUNCE_TICKS = 2;

    private long quietUntil = Long.MIN_VALUE;
    private long deadline = Long.MIN_VALUE;
    private long explicitUntil = Long.MIN_VALUE;

    void inputChanged(long gameTime) {
        if (deadline == Long.MIN_VALUE) {
            deadline = gameTime + MAX_DEBOUNCE_TICKS;
        }
        quietUntil = gameTime + QUIET_TICKS;
    }

    void deferUntil(long gameTime) {
        explicitUntil = Math.max(explicitUntil, gameTime);
    }

    boolean shouldWait(long gameTime) {
        if (isExplicitWait(gameTime)) {
            return true;
        }
        return deadline != Long.MIN_VALUE
                && gameTime < quietUntil
                && gameTime < deadline;
    }

    boolean isExplicitWait(long gameTime) {
        return gameTime < explicitUntil;
    }

    void resolved() {
        quietUntil = Long.MIN_VALUE;
        deadline = Long.MIN_VALUE;
        explicitUntil = Long.MIN_VALUE;
    }

    void clear() {
        resolved();
    }
}
