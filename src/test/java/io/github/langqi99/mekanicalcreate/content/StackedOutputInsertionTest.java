package io.github.langqi99.mekanicalcreate.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class StackedOutputInsertionTest {
    @Test
    void fillsALaterCompatibleStackBeforeClaimingAnEarlierEmptySlot() {
        List<Slot> slots = List.of(
                new Slot("", 0),
                new Slot("A", 60),
                new Slot("B", 64),
                new Slot("C", 64));

        Stack firstRemainder = insert(slots, new Stack("A", 4));
        Stack secondRemainder = insert(slots, new Stack("D", 1));

        assertTrue(firstRemainder.empty());
        assertTrue(secondRemainder.empty());
        assertEquals(List.of("D:1", "A:64", "B:64", "C:64"),
                slots.stream().map(Slot::description).toList());
    }

    private static Stack insert(List<Slot> slots, Stack stack) {
        return StackedOutputInsertion.insert(
                slots, stack, Stack::empty,
                (slot, remainder) -> !slot.empty()
                        && slot.key.equals(remainder.key),
                Slot::empty,
                Slot::insert);
    }

    private static final class Slot {
        private String key;
        private int count;

        private Slot(String key, int count) {
            this.key = key;
            this.count = count;
        }

        private boolean empty() {
            return count == 0;
        }

        private Stack insert(Stack stack) {
            if (!empty() && !key.equals(stack.key)) {
                return stack;
            }
            int accepted = Math.min(stack.count, 64 - count);
            if (empty() && accepted > 0) {
                key = stack.key;
            }
            count += accepted;
            return new Stack(stack.key, stack.count - accepted);
        }

        private String description() {
            return key + ":" + count;
        }
    }

    private record Stack(String key, int count) {
        private boolean empty() {
            return count == 0;
        }
    }
}
