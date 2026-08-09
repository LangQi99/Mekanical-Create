package io.github.langqi99.mekanicalcreate;

import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryMultiblockData;
import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryMultiblockCache;
import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryValidator;
import io.github.langqi99.mekanicalcreate.registry.ModBlockEntities;
import io.github.langqi99.mekanicalcreate.registry.ModBlocks;
import io.github.langqi99.mekanicalcreate.registry.ModItems;
import io.github.langqi99.mekanicalcreate.registry.ModMenus;
import mekanism.common.lib.multiblock.MultiblockManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(MekanicalCreate.MOD_ID)
public final class MekanicalCreate {
    public static final String MOD_ID = "mekanicalcreate";
    public static final MultiblockManager<MekanicalFactoryMultiblockData> MEKANICAL_FACTORY_MANAGER =
            new MultiblockManager<>("mekanicalFactory", MekanicalFactoryMultiblockCache::new,
                    MekanicalFactoryValidator::new);

    public MekanicalCreate(IEventBus modBus) {
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModItems.register(modBus);
    }
}
