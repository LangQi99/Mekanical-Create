package io.github.langqi99.mekanicalcreate.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Small game-independent model of a fixed-size stacked item inventory.
 * Failed reservations are atomic and leave the original contents untouched.
 */
final class StackedOutputCapacity<K> {
    private final int slotCount;
    private List<Stack<K>> contents;

    StackedOutputCapacity(int slotCount, List<Stack<K>> initialContents) {
        if (slotCount <= 0) {
            throw new IllegalArgumentException("slotCount must be positive");
        }
        if (initialContents.size() > slotCount) {
            throw new IllegalArgumentException("initial contents exceed slot count");
        }
        this.slotCount = slotCount;
        contents = new ArrayList<>(slotCount);
        for (Stack<K> stack : initialContents) {
            contents.add(stack.copy());
        }
        while (contents.size() < slotCount) {
            contents.add(Stack.empty());
        }
    }

    boolean tryReserve(List<Stack<K>> results) {
        List<Stack<K>> candidate = snapshot();
        for (Stack<K> result : results) {
            int remaining = result.count;
            if (remaining == 0) {
                continue;
            }
            for (int slot = 0; slot < slotCount && remaining > 0; slot++) {
                Stack<K> stored = candidate.get(slot);
                if (stored.count == 0 || !Objects.equals(stored.key, result.key)) {
                    continue;
                }
                int limit = Math.min(stored.maxStackSize, result.maxStackSize);
                int accepted = Math.min(remaining, Math.max(0, limit - stored.count));
                if (accepted > 0) {
                    candidate.set(slot, stored.withCount(stored.count + accepted));
                    remaining -= accepted;
                }
            }
            for (int slot = 0; slot < slotCount && remaining > 0; slot++) {
                if (candidate.get(slot).count != 0) {
                    continue;
                }
                int accepted = Math.min(remaining, result.maxStackSize);
                candidate.set(slot, result.withCount(accepted));
                remaining -= accepted;
            }
            if (remaining > 0) {
                return false;
            }
        }
        contents = candidate;
        return true;
    }

    /**
     * Admits one ordered processing unit without conditioning probability
     * rolls on output capacity.
     *
     * <p>Once a batch already contains work, a failed worst-case preflight
     * stops before invoking {@code roller}. For the first unit only, an exact
     * roll may be attempted and returned as {@link AdmissionStatus#BLOCKED} so
     * callers can lock that roll instead of discarding and rerolling it.</p>
     */
    <U> Admission<U> admit(List<Stack<K>> possibleResults,
                           Supplier<RolledOutput<K, U>> roller,
                           boolean batchAlreadyAccepted) {
        StackedOutputCapacity<K> possible = copy();
        if (possible.tryReserve(possibleResults)) {
            RolledOutput<K, U> rolled = roller.get();
            // The preflight guarantees every declared chance output could
            // fit, but only the exact roll occupies capacity for the following
            // unit. This keeps the batch conservative without wasting space
            // when an optional byproduct did not roll.
            StackedOutputCapacity<K> exact = copy();
            contents = exact.tryReserve(rolled.results)
                    ? exact.snapshot() : possible.snapshot();
            return Admission.accepted(rolled.value);
        }
        if (batchAlreadyAccepted) {
            return Admission.stopped();
        }
        RolledOutput<K, U> rolled = roller.get();
        StackedOutputCapacity<K> exact = copy();
        if (exact.tryReserve(rolled.results)) {
            contents = exact.snapshot();
            return Admission.accepted(rolled.value);
        }
        return Admission.blocked(rolled.value);
    }

    StackedOutputCapacity<K> copy() {
        return new StackedOutputCapacity<>(slotCount, contents);
    }

    List<Stack<K>> snapshot() {
        return contents.stream().map(Stack::copy)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    record Stack<K>(K key, int count, int maxStackSize) {
        Stack {
            if (count < 0) {
                throw new IllegalArgumentException("count cannot be negative");
            }
            if (count > 0 && maxStackSize <= 0) {
                throw new IllegalArgumentException("maxStackSize must be positive");
            }
            if (count > 0 && key == null) {
                throw new IllegalArgumentException("non-empty stack requires a key");
            }
        }

        static <K> Stack<K> empty() {
            return new Stack<>(null, 0, 0);
        }

        private Stack<K> withCount(int value) {
            return value == 0 ? empty() : new Stack<>(key, value, maxStackSize);
        }

        private Stack<K> copy() {
            return new Stack<>(key, count, maxStackSize);
        }
    }

    record RolledOutput<K, U>(List<Stack<K>> results, U value) {
        RolledOutput {
            results = List.copyOf(results);
        }
    }

    record Admission<U>(AdmissionStatus status, Optional<U> value) {
        private static <U> Admission<U> accepted(U value) {
            return new Admission<>(AdmissionStatus.ACCEPTED, Optional.of(value));
        }

        private static <U> Admission<U> stopped() {
            return new Admission<>(AdmissionStatus.STOPPED, Optional.empty());
        }

        private static <U> Admission<U> blocked(U value) {
            return new Admission<>(AdmissionStatus.BLOCKED, Optional.of(value));
        }
    }

    enum AdmissionStatus {
        ACCEPTED,
        STOPPED,
        BLOCKED
    }
}
