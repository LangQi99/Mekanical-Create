package io.github.langqi99.mekanicalcreate.registry;

import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MekanicalCreate.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mekanicalcreate"))
            .icon(() -> ModBlocks.SIMULATION_CHAMBER.asItem().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModBlocks.SIMULATION_CHAMBER.asItem());
                output.accept(ModBlocks.BASIC_MEKANICAL_FACTORY.asItem());
                output.accept(ModBlocks.ADVANCED_MEKANICAL_FACTORY.asItem());
                output.accept(ModBlocks.ELITE_MEKANICAL_FACTORY.asItem());
                output.accept(ModBlocks.ULTIMATE_MEKANICAL_FACTORY.asItem());
            })
            .build());

    private ModItems() {
    }

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
