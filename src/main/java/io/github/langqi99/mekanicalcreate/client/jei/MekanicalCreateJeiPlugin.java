package io.github.langqi99.mekanicalcreate.client.jei;

import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import io.github.langqi99.mekanicalcreate.client.SimulationChamberScreen;
import io.github.langqi99.mekanicalcreate.content.SimulationRecipeResolver;
import io.github.langqi99.mekanicalcreate.registry.ModBlocks;
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

    @NotNull
    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new SimulationChamberRecipeCategory(
                registration.getJeiHelpers().getGuiHelper(), chamberStack()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            registration.addRecipes(SimulationChamberRecipeCategory.TYPE,
                    SimulationRecipeResolver.getDisplayRecipes(level));
        }
        registration.addItemStackInfo(chamberStack(),
                net.minecraft.network.chat.Component.translatable("jei.mekanicalcreate.info"));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(chamberStack(), SimulationChamberRecipeCategory.TYPE);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addRecipeClickArea(SimulationChamberScreen.class,
                49, 29, 24, 14, SimulationChamberRecipeCategory.TYPE);
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new SimulationChamberTransferInfo());
    }
}
