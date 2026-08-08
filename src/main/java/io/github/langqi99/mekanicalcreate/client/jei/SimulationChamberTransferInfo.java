package io.github.langqi99.mekanicalcreate.client.jei;

import io.github.langqi99.mekanicalcreate.content.SimulationChamberBlockEntity;
import io.github.langqi99.mekanicalcreate.content.SimulationRecipeResolver.DisplayRecipe;
import io.github.langqi99.mekanicalcreate.registry.ModMenus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

public final class SimulationChamberTransferInfo implements IRecipeTransferInfo<
        MekanismTileContainer<SimulationChamberBlockEntity>, DisplayRecipe> {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Class<? extends MekanismTileContainer<SimulationChamberBlockEntity>> getContainerClass() {
        return (Class) MekanismTileContainer.class;
    }

    @Override
    public Optional<MenuType<MekanismTileContainer<SimulationChamberBlockEntity>>> getMenuType() {
        return Optional.of(ModMenus.SIMULATION_CHAMBER.get());
    }

    @Override
    public RecipeType<DisplayRecipe> getRecipeType() {
        return SimulationChamberRecipeCategory.TYPE;
    }

    @Override
    public boolean canHandle(MekanismTileContainer<SimulationChamberBlockEntity> container,
                             DisplayRecipe recipe) {
        long consumedKinds = recipe.inputs().stream().filter(input -> input.consumed()).count();
        return consumedKinds <= SimulationChamberBlockEntity.INPUT_COUNT;
    }

    @Override
    public List<Slot> getRecipeSlots(MekanismTileContainer<SimulationChamberBlockEntity> container,
                                     DisplayRecipe recipe) {
        List<IInventorySlot> inputSlots = new ArrayList<>(container.getTileEntity().getInputSlots());
        return container.getInventoryContainerSlots().stream()
                .filter(slot -> inputSlots.contains(slot.getInventorySlot()))
                .map(slot -> (Slot) slot)
                .toList();
    }

    @Override
    public List<Slot> getInventorySlots(MekanismTileContainer<SimulationChamberBlockEntity> container,
                                        DisplayRecipe recipe) {
        List<Slot> result = new ArrayList<>();
        result.addAll(container.getMainInventorySlots());
        result.addAll(container.getHotBarSlots());
        return result;
    }
}
