package io.github.langqi99.mekanicalcreate.client.ponder;

import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import io.github.langqi99.mekanicalcreate.registry.ModBlocks;
import mekanism.common.registries.MekanismBlocks;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public final class MekanicalCreatePonderPlugin implements PonderPlugin {
    // The tutorial draws the largest supported 7 x 7 x 7 factory. Ponder clips
    // dynamic blocks to the source schematic, so use a 9 x 8 x 9 scene and clear
    // its contents before drawing our own structure.
    private static final ResourceLocation TEMPLATE = ResourceLocation.fromNamespaceAndPath(
            "create", "train_track/portal");

    @Override
    public String getModId() {
        return MekanicalCreate.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(
                        ModBlocks.FLUID_MEKANICAL_FACTORY.getId(),
                        ModBlocks.MEKANICAL_FACTORY_CASING.getId(),
                        MekanismBlocks.STRUCTURAL_GLASS.getId(),
                        ModBlocks.MEKANICAL_FACTORY_PORT.getId(),
                        ModBlocks.MEKANICAL_FACTORY_SPEED_CORE.getId(),
                        ModBlocks.MEKANICAL_FACTORY_ENERGY_CORE.getId(),
                        ModBlocks.MEKANICAL_FACTORY_FLUID_CORE.getId(),
                        ModBlocks.MEKANICAL_FACTORY_CATALYST_CORE.getId())
                .addStoryBoard(TEMPLATE, MekanicalFactoryPonderScene::build);
    }
}
