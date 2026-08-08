package io.github.langqi99.mekanicalcreate.client;

import io.github.langqi99.mekanicalcreate.content.SuperSimulationChamberBlockEntity;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class SuperSimulationChamberScreen
        extends AbstractSimulationChamberScreen<SuperSimulationChamberBlockEntity> {
    public SuperSimulationChamberScreen(MekanismTileContainer<SuperSimulationChamberBlockEntity> menu,
                                        Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
