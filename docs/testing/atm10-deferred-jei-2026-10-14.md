# Shared factory catalog and deferred JEI publication

This follow-up targets [issue #1](https://github.com/LangQi99/Mekanical-Create/issues/1) on `main` (Minecraft 1.21.1 / NeoForge). Earlier synchronous measurements and their limitations are recorded in [the first performance report](atm10-jei-performance-2026-10-14.md).

## Reference and adaptation

The loading design follows SorrowMist's Useless Mod, specifically its omniversal alloy furnace at commit [`8ef4fd84f2cdc73315b50eb70f97831a6cdbf122`](https://github.com/SorrowMist/UselessMod/tree/8ef4fd84f2cdc73315b50eb70f97831a6cdbf122):

- [`JEIPlugin`](https://github.com/SorrowMist/UselessMod/blob/8ef4fd84f2cdc73315b50eb70f97831a6cdbf122/src/main/java/com/sorrowmist/useless/compat/jei/JEIPlugin.java) reads only a ready catalog during initial registration, retains the JEI runtime, and publishes recipes after preparation.
- [`ClientSetup`](https://github.com/SorrowMist/UselessMod/blob/8ef4fd84f2cdc73315b50eb70f97831a6cdbf122/src/main/java/com/sorrowmist/useless/init/ClientSetup.java) coalesces world, recipe, and tag updates before rebuilding.
- [`AlloyFurnaceRecipeCatalog`](https://github.com/SorrowMist/UselessMod/blob/8ef4fd84f2cdc73315b50eb70f97831a6cdbf122/src/main/java/com/sorrowmist/useless/content/recipe/AlloyFurnaceRecipeCatalog.java) shares immutable snapshots per recipe manager and rejects superseded generations.

This implementation independently adapts those ideas to Create modules, fan conditions, sifter meshes, fluid variants, and capability-based charging. The reference synchronously prewarms its catalog on a client tick; this implementation additionally resumes display preparation and publishes JEI batches from render frames. It does not run Minecraft item capabilities on a worker thread.

## Behavior

- `registerRecipes` registers empty factory categories and information pages. It never builds the display catalog, matching index, or charging displays.
- A shared snapshot contains both item and fluid factory displays. Readiness checks do not trigger synchronous work. Native charging candidates are prepared once per charging module and reused across categories.
- Recipe and tag packets can arrive in either order. Loading requires the initial recipe packet, consumes a fresh recipe/tag pair after a quiet tick, and has a 100-tick fallback for a missing follow-up packet. A tag packet cannot immediately reuse the previous reload's recipe signal.
- Reloads invalidate only the affected recipe manager. A cancelled generation cannot publish its snapshot. Client display reloads do not advance the integrated server's machine-plan epoch or clear its prewarmed indexes.
- Runtime publication uses a generation-bound queue and adaptive batches of 1–256 recipes. A completed batch advances once; an uncommitted batch remains retryable. Replaced snapshots hide their published recipes before the next generation is added. JEI runtime shutdown clears publication state.
- Preparation and registration share a target budget of 4 ms per render frame, with progress in the action bar. The world can be used while factory recipes are being prepared. Factory categories are initially empty and become available during publication.
- Machine matching metadata and reverse indexes remain lazy. Server datapack reload prewarming explicitly constructs them. The existing bounded positive/negative lookup cache and component-aware equality semantics are retained.

The 4 ms budget is a cooperative target, not a maximum pause guarantee. Individual source-catalog construction, mod capability calls, JEI indexing batches, and garbage collection can exceed it. Measurements distinguish initial JEI entry, actual work, maximum slice/batch, and elapsed time until the full catalog is available.

## Validation

- `./gradlew test check build` passed with 61 JUnit tests, no failures or skipped tests.
- `./gradlew runGameTestServer` passed all nine required tests.
- Lifecycle tests cover packet ordering, coalescing, missing-tag fallback, logout reset, queue boundaries, retries, and stale generations. GameTests cover shared snapshot identity, readiness without synchronous loading, manager isolation, cancellation, index laziness, and resumed stateful energy acceptance.

## Production ATM10 measurements

The runs use the same isolated ATM10 8.2 production environment described in the first report. The following initial measurements used SHA-256 `9aeff9e923eec3886c6a532f26048c9ddf0128ba207410eacd0cccec472808a0`, before the additional recipe/tag pairing guard described below.

| Before the pairing guard, fresh process | Measurement |
| --- | ---: |
| Initial addon `registerRecipes` entry | 310 microseconds |
| Entire JEI startup, all plugins | 1.241 minutes |
| Source recipes at preparation start | 94,966 |
| Shared catalog actual work / elapsed time | 4,137 ms / 19,746 ms |
| Maximum catalog slice | 484 ms |
| Runtime publication actual work / elapsed time | 6,748 ms / 46,199 ms |
| Maximum publication batch | 232 ms |
| Item / fluid factory displays | 51,722 / 51,935 |
| External sequenced assembly variants | 14 |

The full factory catalog was published approximately 65.95 seconds after preparation started. The player could open the inventory while preparation/publication continued. Both factory usage queries opened successfully afterwards, and the item category displayed 25,861 two-recipe pages.

Initial entry and full-catalog availability measure different work. The microsecond entry demonstrates that factory generation has left JEI's initial callback; it is not a claim that generating or indexing 103,657 displays takes microseconds. Spreading work across frames deliberately increases elapsed time to completion. The run had substantial concurrent system load and lower frame rates; whole-JEI startup and elapsed publication time do not establish an overall speedup over the previous synchronous build.

JFR recorded six GC pauses totaling 812.66 ms during catalog preparation, including a 480.41 ms pause that accounts for most of the 484 ms maximum slice. Publication overlapped 15 GC pauses totaling 2,995.73 ms, with a maximum of 1,282.09 ms. Some pauses occur outside publication batches. Machine CPU load samples were 0.513–0.779 during preparation and reached 0.956 during publication. These are observations of this run, not stable performance guarantees.

| Final artifact, fresh process with the probe enabled | Measurement |
| --- | ---: |
| Initial addon `registerRecipes` entry | 281 microseconds |
| Entire JEI startup, all plugins | 1.115 minutes |
| Source recipes at preparation start | 96,116 |
| Shared catalog actual work / elapsed time | 2,542 ms / 12,691 ms |
| Maximum catalog slice | 438 ms |
| Runtime publication actual work / elapsed time | 3,780 ms / 12,091 ms |
| Maximum publication batch | 354 ms |
| Item / fluid displays, including one probe in each | 51,723 / 51,936 |
| External sequenced assembly variants | 14 |

Only generation 1 was prepared and published on this fresh join. The full catalog was available approximately 24.80 seconds after preparation started. The probe could be queried from the player's barrier stack, and both factory categories showed a dragon egg producing seven barriers.

This final preparation interval contained three GC pauses totaling 109.98 ms (maximum 66.03 ms). Its 438 ms maximum slice therefore also demonstrates the remaining atomic source-catalog work beyond the cooperative budget. Publication contained two GC pauses totaling 446.02 ms; the 349.74 ms maximum pause accounts for most of its 354 ms maximum batch. No claim of a hard 4 ms bound is made.

## Reload regression

A separate datapack in the test save adds `mekanical_perf:reload_probe`, a shapeless dragon-egg recipe producing seven barriers. Enabling it rebuilt the client catalog with **51,723 item / 51,936 fluid displays and 14 external sequence variants**, exactly one additional display in each category. Generation 2 was superseded without publishing; generation 3 published the complete changed catalog.

That run exposed premature work when a fresh tag packet arrived before the new recipe packet. The final reload state now tracks and consumes both packet versions, so it cannot reuse the previous generation's recipe notification during its normal coalescing window. A regression test delays the recipe notification for 20 ticks after tags and checks that no build starts early.

The probe JSON was then moved to a backup outside the enabled datapack and `/reload` was executed with the final artifact. Only generation 2 was queued and published. Counts returned to **51,722 item / 51,935 fluid displays and 14 external sequence variants**. Querying the barrier afterwards showed only the other mod's block-heat category; the probe's crafting and both factory categories were absent.

This reload's initial addon entry took 319 microseconds. Catalog work / elapsed time was 1,418 / 5,268 ms, with a 142 ms maximum slice. Publication work / elapsed time was 5,307 / 15,018 ms, with a 1,010 ms maximum batch. The test turn was interrupted, and approximately 99 seconds separated preparation completion from the start of publication. These phase timings are not a controlled end-to-end reload benchmark.

The test world was saved and the client exited at the user's request to stop resource-heavy testing. Same-process rejoin validation of this final artifact was not completed.

The final artifact is `build/libs/mekanicalcreate-0.2.8-mc1.21.1-neoforge-jei-deferred.jar`, SHA-256 `2092f61644f43ac1e793ea01a2ff4c04e81872589e53adc4956c48d38a426d25`; it matches the installed test JAR. Logs, JFR recordings, and profile summaries are under `/Users/langqi/tmp/mekanical-atm10-jei-20261014/`, with the `catalog-final` and `catalog-paired-final` prefixes.
