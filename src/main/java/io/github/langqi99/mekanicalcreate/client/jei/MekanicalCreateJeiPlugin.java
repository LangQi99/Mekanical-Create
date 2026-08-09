package io.github.langqi99.mekanicalcreate.client.jei;

import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import io.github.langqi99.mekanicalcreate.client.SimulationChamberScreen;
import io.github.langqi99.mekanicalcreate.client.FluidMekanicalFactoryScreen;
import io.github.langqi99.mekanicalcreate.content.SimulationRecipeResolver;
import io.github.langqi99.mekanicalcreate.registry.ModBlocks;
import io.github.langqi99.mekanicalcreate.registry.ModMenus;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

@JeiPlugin
public final class MekanicalCreateJeiPlugin implements IModPlugin {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            MekanicalCreate.MOD_ID, "jei_plugin");

    private static ItemStack chamberStack() {
        return new ItemStack(ModBlocks.SIMULATION_CHAMBER.get());
    }

    private static List<ItemStack> factoryStacks() {
        return List.of(
                chamberStack(),
                new ItemStack(ModBlocks.BASIC_MEKANICAL_FACTORY.get()),
                new ItemStack(ModBlocks.ADVANCED_MEKANICAL_FACTORY.get()),
                new ItemStack(ModBlocks.ELITE_MEKANICAL_FACTORY.get()),
                new ItemStack(ModBlocks.ULTIMATE_MEKANICAL_FACTORY.get())
        );
    }

    private static ItemStack fluidFactoryStack() {
        return new ItemStack(ModBlocks.FLUID_MEKANICAL_FACTORY.get());
    }

    @NotNull
    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new SimulationChamberRecipeCategory(guiHelper, chamberStack(), false),
                new SimulationChamberRecipeCategory(guiHelper, fluidFactoryStack(), true));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            registration.addRecipes(SimulationChamberRecipeCategory.TYPE,
                    SimulationRecipeResolver.getDisplayRecipes(level, false));
            registration.addRecipes(SimulationChamberRecipeCategory.FLUID_TYPE,
                    SimulationRecipeResolver.getDisplayRecipes(level, true));
        }
        for (ItemStack factory : factoryStacks()) {
            registration.addItemStackInfo(factory,
                    net.minecraft.network.chat.Component.translatable("jei.mekanicalcreate.info"));
        }
        registration.addItemStackInfo(fluidFactoryStack(),
                net.minecraft.network.chat.Component.translatable(
                        "jei.mekanicalcreate.fluid_info"));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (ItemStack factory : factoryStacks()) {
            registration.addRecipeCatalyst(factory, SimulationChamberRecipeCategory.TYPE);
        }
        registration.addRecipeCatalyst(fluidFactoryStack(),
                SimulationChamberRecipeCategory.FLUID_TYPE);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addRecipeClickArea(SimulationChamberScreen.class,
                123, 55, 28, 8, SimulationChamberRecipeCategory.TYPE);
        registration.addRecipeClickArea(FluidMekanicalFactoryScreen.class,
                158, 43, 84, 8, SimulationChamberRecipeCategory.FLUID_TYPE);
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new SimulationChamberTransferInfo<>(
                ModMenus.SIMULATION_CHAMBER.get(), SimulationChamberRecipeCategory.TYPE));
        registration.addRecipeTransferHandler(new MekanicalFactoryTransferInfo(
                ModMenus.FLUID_MEKANICAL_FACTORY.get(),
                SimulationChamberRecipeCategory.FLUID_TYPE));
    }
}
