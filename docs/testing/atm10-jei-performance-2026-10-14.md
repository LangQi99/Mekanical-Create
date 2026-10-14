# ATM10 JEI registration performance

Issue: [#1](https://github.com/LangQi99/Mekanical-Create/issues/1).

## Environment

- Official ATM10 8.2 client manifest: 495 resources, Minecraft 1.21.1, NeoForge 21.1.251.
- Create 6.0.10, Mekanism 10.7.19.85, JEI 19.57.0.446.
- Apple M5, 24 GB RAM, Java 21.0.12, 10 GB maximum heap.
- Separate flat test world and game directory; the original game directories and saves were not modified.
- Baseline: addon 0.2.7 built from commit `5e1af1d`. Both fixes target `main` (0.2.8). The resolver and JEI plugin were identical between the baseline and unmodified `main`.

The unavailable TownTalk 1.2.0 file was recovered from the official ATM10 8.2 server archive. The client manifest downloaded CC:Tweaked 1.113.1, which failed the required version checks of Advanced Peripherals and More Red CCT Compat. The test instance uses CC:Tweaked 1.120.2 from the same official server archive. Both runs used this identical environment.

The development launch could not load Accessories' production mixins, so the measured runs use the official installed NeoForge production runtime and packaged addon JARs.

## Results

Each measured first join ran in a fresh client process. These are JEI's own log timings, with Java Flight Recorder enabled in both runs.

| Measurement | 0.2.7 baseline | Stack deduplication fix | Lazy matching / charging reuse |
| --- | ---: | ---: | ---: |
| Addon recipe registration | 33.28 s | 6.628 s | 2.482 s |
| Entire JEI startup | approximately 85.1 s | approximately 61.4 s | 42.81 s |
| Item factory display recipes | 51,722 | 51,722 | 51,722 |
| Fluid factory display recipes | 51,935 | 51,935 | 51,935 |
| External sequenced assembly variants | 14 | 14 | 14 |

The first fix reduced addon registration by approximately 80.1%. The second round reduced it by another 62.6%, or 92.5% versus baseline. The reported ten-minute duration was not reproduced on this machine, but the render-thread stall during addon registration was reproduced. Entire JEI startup includes other plugins and ingredient search indexing; its change cannot be attributed solely to this addon or compared as a controlled benchmark of those other plugins.

After saving and quitting to the title screen, the second-round build entered the same world again in the same client process. This rejoin registered the same 51,722 item / 51,935 fluid recipes and 14 external sequence variants in **8.965 s**. Entire JEI startup took 1.514 minutes (approximately 90.84 s). This rejoin was slower than the first join; no rejoin measurement of the first fix was completed, so these data do not establish a rejoin speedup over that build.

The rejoin registration interval contained five GC pauses totaling 1.186 s, versus one 0.110 s pause during its first join. JFR machine CPU load samples reached 0.948 during rejoin, compared with 0.278–0.307 during first-join registration. The rejoin profile still showed no matching metadata/index construction; charging simulation was the largest addon hotspot. GC and higher system load may contribute to the difference, but their complete effect was not isolated. The 2.482 s first-join result is an observed measurement, not a stable latency guarantee.

## Cause and fix

JEI's native charging displays enumerate the entire item registry for both factory categories. `distinctStacks` previously compared every input against all prior distinct stacks with a stream, making that pass quadratic in registry size.

The baseline recording contained 1,119 sampled stacks involving the addon on the main/render thread; 966 (86.3%) were in `distinctStacks` or its comparison lambda. The equivalent fixed recording contained only two sampled stacks in `distinctStacks`.

The fix uses Minecraft's `ItemStackLinkedSet` hashing strategy for item and component identity. Counts remain ignored for equality; first-occurrence order and copied, count-one display stacks are retained. No recipes are capped or disabled, and energy charging behavior is unchanged.

The second recording identified ingredient comparisons, runtime matching metadata, and repeated charging simulations as the remaining costs. The second round:

- Memoizes matching metadata and reverse indexes. JEI display generation reads the candidate list without compiling these machine-only structures. Factory processing builds its requested index on first use. Server datapack reload prewarming still initializes indexes explicitly.
- Generates native charging candidates once per charging module and JEI registration, reusing them for both factory categories. The cache is local to one registration, so capabilities are reevaluated on a later registration. Each display still copies its output stacks.
- Uses an identity / same-order fast path and a hashed multiset for ingredient comparison. Option order and stack counts remain ignored; components and duplicate multiplicities remain significant.

The second-round first-join JFR contained 66 addon samples on the main/render thread, versus 182 for the first fix. Ingredient comparison dropped from 37 to two samples, and matching metadata/index construction no longer appeared during display registration. Sampling counts are diagnostic evidence, not timing guarantees.

JEI also offers [query-time dynamic generation](https://github.com/mezz/JustEnoughItems/blob/1.21.1/Common/src/api/java/mezz/jei/api/recipe/advanced/IRecipeManagerPlugin.java). This build retains complete JEI recipe registration and applies lazy initialization to machine matching structures; it does not defer recipe availability until a GUI is opened.

## Validation

- `./gradlew test`: 50 tests passed, no failures or skipped tests.
- `./gradlew runGameTestServer`: all seven required tests passed.
- New GameTests cover count-independent deduplication, distinct components, insertion order, copies that do not alias inputs, and 10,000 component variants with duplicate counts. Additional cases cover reordered ingredient options, duplicate multiplicities, 10,000 reversed component variants, display generation without matching compilation, correct first-use crafting output, explicit prewarming, reuse, and invalidation after reload.
- `./gradlew check build` and `git diff --check` passed.
- The second-round client entered the same test world both from a fresh process and after saving/quitting to the title screen, with unchanged recipe counts.
- After rejoining, JEI usage lookup opened both factory categories successfully. The item factory's full catalog displayed 25,861 two-recipe pages, consistent with 51,722 registered displays.

The delivered second-round artifact is `build/libs/mekanicalcreate-0.2.8-mc1.21.1-neoforge-jei-optimized.jar`, SHA-256 `c90e9365136fcee0283b525ad22691e2cf0972b3ef7a1d409efe945f82869dc7`. It matches the JAR used in the ATM10 runs byte for byte.

Local logs and JFR recordings are stored in `/Users/langqi/tmp/mekanical-atm10-jei-20261014/`, including `baseline-first-join.log`, `fixed-first-join.log`, `lazy-first-join.log`, `lazy-second-join.log`, the corresponding first-join JFR recordings, `lazy-second-join.jfr`, and the profile summaries.
