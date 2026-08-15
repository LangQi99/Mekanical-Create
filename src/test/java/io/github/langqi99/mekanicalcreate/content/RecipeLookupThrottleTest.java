package io.github.langqi99.mekanicalcreate.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RecipeLookupThrottleTest {
    @Test
    void waitsForOneQuietTick() {
        RecipeLookupThrottle throttle = new RecipeLookupThrottle();
        throttle.inputChanged(10);

        assertTrue(throttle.shouldWait(10));
        assertFalse(throttle.shouldWait(11));
    }

    @Test
    void continuousChangesCannotPostponeForever() {
        RecipeLookupThrottle throttle = new RecipeLookupThrottle();
        throttle.inputChanged(10);
        throttle.inputChanged(11);
        throttle.inputChanged(12);

        assertFalse(throttle.shouldWait(12));
    }

    @Test
    void explicitDeferralAndResetAreHonored() {
        RecipeLookupThrottle throttle = new RecipeLookupThrottle();
        throttle.deferUntil(15);
        assertTrue(throttle.shouldWait(14));
        assertFalse(throttle.shouldWait(15));

        throttle.inputChanged(20);
        throttle.resolved();
        assertFalse(throttle.shouldWait(20));
    }
}
