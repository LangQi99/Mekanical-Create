package io.github.langqi99.mekanicalcreate.client.jei;

import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryControllerBlockEntity;
import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryContainer;
import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryMultiblockData;
import io.github.langqi99.mekanicalcreate.content.SimulationRecipeResolver.DisplayRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mekanism.api.inventory.IInventorySlot;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

public final class MekanicalFactoryTransferInfo implements IRecipeTransferInfo<
        MekanicalFactoryContainer, DisplayRecipe> {
    private final MenuType<MekanicalFactoryContainer> menuType;
    private final RecipeType<DisplayRecipe> recipeType;

    public MekanicalFactoryTransferInfo(
            MenuType<MekanicalFactoryContainer> menuType,
            RecipeType<DisplayRecipe> recipeType) {
        this.menuType = menuType;
        this.recipeType = recipeType;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Class<? extends MekanicalFactoryContainer> getContainerClass() {
        return MekanicalFactoryContainer.class;
    }

    @Override
    public Optional<MenuType<MekanicalFactoryContainer>> getMenuType() {
        return Optional.of(menuType);
    }

    @Override
    public RecipeType<DisplayRecipe> getRecipeType() {
        return recipeType;
    }

    @Override
    public boolean canHandle(MekanicalFactoryContainer container,
                             DisplayRecipe recipe) {
        return recipe.inputs().stream().filter(input -> input.consumed()).count()
                <= MekanicalFactoryMultiblockData.INPUT_COUNT;
    }

    @Override
    public List<Slot> getRecipeSlots(
            MekanicalFactoryContainer container,
            DisplayRecipe recipe) {
        List<IInventorySlot> inputs = new ArrayList<>(container.getTileEntity().getInputSlots());
        return container.getInventoryContainerSlots().stream()
                .filter(slot -> inputs.contains(slot.getInventorySlot()))
                .map(slot -> (Slot) slot)
                .toList();
    }

    @Override
    public List<Slot> getInventorySlots(
            MekanicalFactoryContainer container,
            DisplayRecipe recipe) {
        List<Slot> result = new ArrayList<>();
        result.addAll(container.getMainInventorySlots());
        result.addAll(container.getHotBarSlots());
        return result;
    }
}
