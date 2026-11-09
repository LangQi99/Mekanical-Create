package io.github.langqi99.mekanicalcreate.registry;

import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryControllerBlockEntity;
import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryContainer;
import io.github.langqi99.mekanicalcreate.content.MekanicalStressInterfaceMenu;
import io.github.langqi99.mekanicalcreate.content.FactoryStressPortBlockEntity;
import io.github.langqi99.mekanicalcreate.content.SimulationChamberBlockEntity;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.registration.impl.ContainerTypeDeferredRegister;
import mekanism.common.registration.impl.ContainerTypeRegistryObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    private static final ContainerTypeDeferredRegister MENUS = new ContainerTypeDeferredRegister(MekanicalCreate.MOD_ID);

    public static final ContainerTypeRegistryObject<MekanismTileContainer<SimulationChamberBlockEntity>> SIMULATION_CHAMBER = MENUS
            .custom("simulation_chamber", SimulationChamberBlockEntity.class)
            .offset(0, 36)
            .build();

    public static final ContainerTypeRegistryObject<MekanicalFactoryContainer> FLUID_MEKANICAL_FACTORY = MENUS
            .register("fluid_mekanical_factory", MekanicalFactoryControllerBlockEntity.class,
                    MekanicalFactoryContainer::new);

    private static final DeferredRegister<MenuType<?>> PLAIN_MENUS =
            DeferredRegister.create(Registries.MENU, MekanicalCreate.MOD_ID);

    /** 接口界面用的是原版容器（MekanismTileContainer 只收 TileEntityMekanism）。 */
    public static final DeferredHolder<MenuType<?>, MenuType<MekanicalStressInterfaceMenu>> STRESS_INTERFACE =
            PLAIN_MENUS.register("stress_interface", () -> IMenuTypeExtension.create(
                    (id, inventory, buffer) -> new MekanicalStressInterfaceMenu(id, inventory, buffer)));

    private ModMenus() {
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
        PLAIN_MENUS.register(bus);
    }
}
