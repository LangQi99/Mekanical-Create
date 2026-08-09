package io.github.langqi99.mekanicalcreate.client;

import io.github.langqi99.mekanicalcreate.content.SimulationChamberBlockEntity;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class FluidMekanicalFactoryScreen
        extends AbstractSimulationChamberScreen<SimulationChamberBlockEntity> {
    public FluidMekanicalFactoryScreen(
            MekanismTileContainer<SimulationChamberBlockEntity> menu,
            Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
