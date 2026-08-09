package io.github.langqi99.mekanicalcreate.client.jei;

import io.github.langqi99.mekanicalcreate.content.SimulationChamberBlockEntity;
import io.github.langqi99.mekanicalcreate.content.SimulationRecipeResolver.DisplayRecipe;
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

public final class SimulationChamberTransferInfo<TILE extends SimulationChamberBlockEntity> implements IRecipeTransferInfo<
        MekanismTileContainer<TILE>, DisplayRecipe> {
    private final MenuType<MekanismTileContainer<TILE>> menuType;
    private final RecipeType<DisplayRecipe> recipeType;

    public SimulationChamberTransferInfo(MenuType<MekanismTileContainer<TILE>> menuType,
                                         RecipeType<DisplayRecipe> recipeType) {
        this.menuType = menuType;
        this.recipeType = recipeType;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Class<? extends MekanismTileContainer<TILE>> getContainerClass() {
        return (Class) MekanismTileContainer.class;
    }

    @Override
    public Optional<MenuType<MekanismTileContainer<TILE>>> getMenuType() {
        return Optional.of(menuType);
    }

    @Override
    public RecipeType<DisplayRecipe> getRecipeType() {
        return recipeType;
    }

    @Override
    public boolean canHandle(MekanismTileContainer<TILE> container,
                             DisplayRecipe recipe) {
        long consumedKinds = recipe.inputs().stream().filter(input -> input.consumed()).count();
        return consumedKinds <= SimulationChamberBlockEntity.INPUT_COUNT;
    }

    @Override
    public List<Slot> getRecipeSlots(MekanismTileContainer<TILE> container,
                                     DisplayRecipe recipe) {
        List<IInventorySlot> inputSlots = new ArrayList<>(container.getTileEntity().getInputSlots());
        return container.getInventoryContainerSlots().stream()
                .filter(slot -> inputSlots.contains(slot.getInventorySlot()))
                .map(slot -> (Slot) slot)
                .toList();
    }

    @Override
    public List<Slot> getInventorySlots(MekanismTileContainer<TILE> container,
                                        DisplayRecipe recipe) {
        List<Slot> result = new ArrayList<>();
        result.addAll(container.getMainInventorySlots());
        result.addAll(container.getHotBarSlots());
        return result;
    }
}
