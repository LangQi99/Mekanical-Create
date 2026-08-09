package io.github.langqi99.mekanicalcreate.content;

import io.github.langqi99.mekanicalcreate.registry.ModMenus;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.world.entity.player.Inventory;

/** Centers the player inventory beneath the factory's variable-width panel. */
public final class MekanicalFactoryContainer
        extends MekanismTileContainer<MekanicalFactoryControllerBlockEntity> {
    public MekanicalFactoryContainer(int id, Inventory inventory,
                                     MekanicalFactoryControllerBlockEntity tile) {
        super(ModMenus.FLUID_MEKANICAL_FACTORY, id, inventory, tile);
    }

    @Override
    protected int getInventoryXOffset() {
        return MekanicalFactoryGuiLayout.inventoryXOffset(
                tile.getFluidTankCount(), tile.getCatalystSlotCount());
    }

    @Override
    protected int getInventoryYOffset() {
        return MekanicalFactoryGuiLayout.INVENTORY_Y_OFFSET;
    }
}
