# Mekanical Create（通用动力）

Mekanical Create is a NeoForge addon for **Create** and **Mekanism**. Its Simulation Chamber turns Create's physical processing lines into a compact, configurable machine while preserving Create's recipes and sequenced-assembly rules.

## Target

- Minecraft 1.21.1
- NeoForge 21.1.x
- Create 6.0.10
- Mekanism 10.7.19
- Java 21

## Simulation Chamber

The chamber has one Create machine-module slot, one fan-condition slot, sixteen unordered material inputs, and four buffered outputs. The condition slot is enabled only for the Encased Fan module.

Supported adapters:

- Deployer, including flattened sequenced assembly
- Mechanical Press
- Mechanical Saw
- Encased Fan + lava/water/soul-fire/heat catalyst
- Millstone and Crushing Wheels
- Mechanical Crafter, for vanilla and Create mechanical-crafting recipes

Modules and fan conditions are configuration items, not recipe consumables. Inputs are matched as an unordered multiset, including tag alternatives and overlapping ingredients. When several recipes match, the recipe that consumes the largest number of items per operation wins. For example, with eight stone present, a four-stone recipe wins over a two-stone recipe.

One completed progress bar always executes exactly one recipe operation. The four-stone recipe therefore consumes four stone and produces one result; the remaining four stone wait for the next progress cycle.

Sequenced assembly is flattened into one operation: the starting item is consumed once, deployer reagents are multiplied by Create's loop count, kept tools remain catalysts, and the final weighted result pool is rolled once. No transitional sequenced-assembly item is emitted.

JEI recipes are generated from the same catalog used by the server resolver. Module, fan condition, and non-consumed held tools use JEI's catalyst role, while flattened material counts use the input role for recipe-transfer and pattern-terminal compatibility.

The machine exterior is derived from Mekanism's Electrolytic Separator model structure and directly reuses its tanks, tubes, casing, ports, QIO screen, supercharged-coil, and energy-glow textures. Its screen is built from Mekanism's own GUI base, inner screens, slots, energy bar, progress indicator, information tab, configuration tab, draggable window, and side-configuration colors. Item and energy access can be configured independently for all six relative sides and is persisted in the block entity.

## Development

```sh
./gradlew build
./gradlew runClient
```

The project intentionally depends on Create and Mekanism through their published artifacts; their source and assets are not vendored. Mekanism model/texture references and GUI classes are supplied by the required Mekanism dependency at runtime.

## License

Mekanical Create is licensed under the [MIT License](LICENSE). Create and Mekanism remain subject to their own licenses.
