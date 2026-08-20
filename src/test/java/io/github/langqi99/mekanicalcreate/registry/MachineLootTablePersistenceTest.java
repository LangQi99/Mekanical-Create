package io.github.langqi99.mekanicalcreate.registry;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MachineLootTablePersistenceTest {
    private static final Set<String> REQUIRED_COMPONENTS = Set.of(
            "mekanism:ejector",
            "mekanism:owner",
            "mekanism:redstone_control",
            "mekanism:security",
            "mekanism:side_config",
            "mekanism:upgrades",
            "mekanism:energy",
            "mekanism:items");

    static Stream<String> portableMachines() {
        return Stream.of(
                "simulation_chamber",
                "basic_mekanical_factory",
                "advanced_mekanical_factory",
                "elite_mekanical_factory",
                "ultimate_mekanical_factory");
    }

    @ParameterizedTest
    @MethodSource("portableMachines")
    void minedMachineCopiesMekanismStateToItsDroppedItem(String machine) throws IOException {
        String resource = "/data/mekanicalcreate/loot_table/blocks/" + machine + ".json";
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            assertNotNull(stream, () -> "Missing loot table " + resource);
            String lootTable = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(lootTable.contains("\"function\": \"minecraft:copy_name\""),
                    "Custom names must survive mining and wrench dismantling");
            assertTrue(lootTable.contains("\"function\": \"minecraft:copy_components\""),
                    "Mekanism state must be copied from the block entity to the drop");
            assertTrue(lootTable.contains("\"source\": \"block_entity\""));
            for (String component : REQUIRED_COMPONENTS) {
                assertTrue(lootTable.contains("\"" + component + "\""),
                        () -> "Missing persisted component " + component + " in " + resource);
            }
        }
    }
}
