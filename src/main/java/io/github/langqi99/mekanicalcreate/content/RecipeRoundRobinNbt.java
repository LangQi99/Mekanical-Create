package io.github.langqi99.mekanicalcreate.content;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class RecipeRoundRobinNbt {
    private static final String ENTRIES = "RecipeRoundRobin";
    private static final String GROUP = "Group";
    private static final String CURSOR = "Cursor";

    private RecipeRoundRobinNbt() {
    }

    static void write(CompoundTag tag, RecipeRoundRobinState state) {
        ListTag entries = new ListTag();
        state.snapshot().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag cursor = new CompoundTag();
                    cursor.putString(GROUP, entry.getKey());
                    cursor.putLong(CURSOR, entry.getValue());
                    entries.add(cursor);
                });
        tag.put(ENTRIES, entries);
    }

    static void read(CompoundTag tag, RecipeRoundRobinState state) {
        ListTag entries = tag.getList(ENTRIES, Tag.TAG_COMPOUND);
        Map<String, Long> restored = new LinkedHashMap<>();
        for (int index = 0; index < entries.size()
                && restored.size() < RecipeRoundRobinState.MAX_GROUPS; index++) {
            CompoundTag cursor = entries.getCompound(index);
            String group = cursor.getString(GROUP);
            if (!group.isEmpty()) {
                restored.put(group, cursor.getLong(CURSOR));
            }
        }
        state.restore(restored);
    }
}
