package io.github.langqi99.mekanicalcreate;

import com.mojang.logging.LogUtils;
import io.github.langqi99.mekanicalcreate.content.CreateFamilyRecipeDiscovery;
import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryMultiblockData;
import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryMultiblockCache;
import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryValidator;
import io.github.langqi99.mekanicalcreate.registry.ModBlockEntities;
import io.github.langqi99.mekanicalcreate.registry.ModBlocks;
import io.github.langqi99.mekanicalcreate.registry.ModItems;
import io.github.langqi99.mekanicalcreate.registry.ModMenus;
import mekanism.common.lib.multiblock.MultiblockManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(MekanicalCreate.MOD_ID)
public final class MekanicalCreate {
    public static final String MOD_ID = "mekanicalcreate";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final MultiblockManager<MekanicalFactoryMultiblockData> MEKANICAL_FACTORY_MANAGER =
            new MultiblockManager<>("mekanicalFactory", MekanicalFactoryMultiblockCache::new,
                    MekanicalFactoryValidator::new);

    public MekanicalCreate() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModItems.register(modBus);
        MinecraftForge.EVENT_BUS.addListener(this::onDatapackSync);
    }

    private void onDatapackSync(OnDatapackSyncEvent event) {
        // RecipeManager is reused across /reload, so discard all discovered
        // addon bindings whenever datapack contents are synchronized.
        CreateFamilyRecipeDiscovery.clearCache();
    }
}
