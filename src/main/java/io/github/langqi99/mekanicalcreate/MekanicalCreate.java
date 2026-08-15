package io.github.langqi99.mekanicalcreate;

import com.mojang.logging.LogUtils;
import io.github.langqi99.mekanicalcreate.content.CreateFamilyRecipeDiscovery;
import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryMultiblockData;
import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryMultiblockCache;
import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryValidator;
import io.github.langqi99.mekanicalcreate.content.SimulationRecipeResolver;
import io.github.langqi99.mekanicalcreate.registry.ModBlockEntities;
import io.github.langqi99.mekanicalcreate.registry.ModBlocks;
import io.github.langqi99.mekanicalcreate.registry.ModItems;
import io.github.langqi99.mekanicalcreate.registry.ModMenus;
import mekanism.common.lib.multiblock.MultiblockManager;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(MekanicalCreate.MOD_ID)
public final class MekanicalCreate {
    public static final String MOD_ID = "mekanicalcreate";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final MultiblockManager<MekanicalFactoryMultiblockData> MEKANICAL_FACTORY_MANAGER =
            new MultiblockManager<>("mekanicalFactory", MekanicalFactoryMultiblockCache::new,
                    MekanicalFactoryValidator::new);

    public MekanicalCreate(IEventBus modBus) {
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModItems.register(modBus);
        NeoForge.EVENT_BUS.addListener(this::onDatapackSync);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            // A player joining only receives the already-loaded datapack. The
            // RecipeManager did not change, so rebuilding every machine's
            // recipe catalog here would create an avoidable login-time spike.
            return;
        }
        // RecipeManager is reused across /reload, so discard all discovered
        // addon bindings and resolver catalogs after an actual server reload.
        CreateFamilyRecipeDiscovery.clearCache();
        SimulationRecipeResolver.clearCache();
        SimulationRecipeResolver.prewarm(event.getPlayerList().getServer().overworld());
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mekanicalcreate")
                .then(Commands.literal("perf")
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal(
                                    SimulationRecipeResolver.diagnostics().compactSummary()), false);
                            return 1;
                        }))
                .then(Commands.literal("resetperf")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            SimulationRecipeResolver.resetDiagnostics();
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "Mekanical Create recipe diagnostics reset."), false);
                            return 1;
                        })));
    }
}
