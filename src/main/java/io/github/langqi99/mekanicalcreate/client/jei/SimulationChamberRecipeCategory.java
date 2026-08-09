package io.github.langqi99.mekanicalcreate.client.jei;

import com.mojang.serialization.Codec;
import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import io.github.langqi99.mekanicalcreate.content.SimulationRecipeResolver.DisplayInput;
import io.github.langqi99.mekanicalcreate.content.SimulationRecipeResolver.DisplayOutput;
import io.github.langqi99.mekanicalcreate.content.SimulationRecipeResolver.DisplayRecipe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import mekanism.client.gui.element.GuiUpArrow;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.recipe_viewer.RecipeViewerUtils;
import mekanism.client.recipe_viewer.jei.BaseRecipeCategory;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.util.text.TextUtils;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SimulationChamberRecipeCategory extends BaseRecipeCategory<DisplayRecipe> {
    public static final RecipeType<DisplayRecipe> TYPE = RecipeType.create(
            MekanicalCreate.MOD_ID, "simulation_chamber", DisplayRecipe.class);
    public static final RecipeType<DisplayRecipe> FLUID_TYPE = RecipeType.create(
            MekanicalCreate.MOD_ID, "fluid_mekanical_factory", DisplayRecipe.class);

    private static final int HEIGHT = 106;
    private static final int INPUT_X = 32;
    private static final int INPUT_Y = 8;
    private static final int[] OUTPUT_Y = {18, 55};
    private static final int[] OUTPUT_CHANCE_Y = {38, 75};

    private final GuiSlot moduleSlot;
    private final GuiSlot conditionSlot;
    private final List<GuiSlot> inputSlots = new ArrayList<>(16);
    private final List<GuiSlot> outputGroups = new ArrayList<>(2);
    private final List<GuiFluidGauge> inputFluidGauges = new ArrayList<>(2);
    private final List<GuiFluidGauge> outputFluidGauges = new ArrayList<>(2);
    private final boolean supportsFluids;
    private final int outputX;

    public SimulationChamberRecipeCategory(IGuiHelper guiHelper, ItemStack factory,
                                           boolean supportsFluids) {
        super(guiHelper, supportsFluids ? FLUID_TYPE : TYPE,
                Component.translatable(supportsFluids
                        ? "jei.mekanicalcreate.fluid_mekanical_factory"
                        : "jei.mekanicalcreate.simulation_chamber"),
                guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, factory),
                0, 0, supportsFluids ? 286 : 194, HEIGHT);
        this.supportsFluids = supportsFluids;
        outputX = supportsFluids ? 232 : 140;

        moduleSlot = addSlot(SlotType.EXTRA, 5, 8);
        conditionSlot = addSlot(SlotType.EXTRA, 5, 26);
        addElement(new GuiUpArrow(this, 9, 47));
        addSlot(SlotType.POWER, 5, 62).with(SlotOverlay.POWER);

        for (int index = 0; index < 16; index++) {
            inputSlots.add(addSlot(SlotType.INPUT,
                    INPUT_X + index % 4 * 18,
                    INPUT_Y + index / 4 * 18));
        }
        for (int y : OUTPUT_Y) {
            outputGroups.add(addSlot(SlotType.OUTPUT_WIDE, outputX, y));
        }
        if (supportsFluids) {
            for (int tank = 0; tank < 2; tank++) {
                inputFluidGauges.add(addElement(GuiFluidGauge.getDummy(
                        GaugeType.SMALL.with(DataType.INPUT), this, 112 + tank * 20, 30)));
                outputFluidGauges.add(addElement(GuiFluidGauge.getDummy(
                        GaugeType.SMALL.with(DataType.OUTPUT), this, 188 + tank * 20, 30)));
            }
        }

        addSimpleProgress(ProgressType.BAR, supportsFluids ? 156 : 110, 42);
        addElement(new GuiVerticalPowerBar(this, RecipeViewerUtils.FULL_BAR,
                supportsFluids ? 278 : 186, 9));
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder, @NotNull DisplayRecipe recipe,
                          @NotNull IFocusGroup focuses) {
        builder.setShapeless();
        initItem(builder, RecipeIngredientRole.CATALYST, moduleSlot, List.of(recipe.module()))
                .setSlotName("module")
                .addTooltipCallback((view, tooltip) -> tooltip.add(
                        Component.translatable("jei.mekanicalcreate.slot.module")
                                .withStyle(ChatFormatting.AQUA)));

        if (!recipe.condition().isEmpty()) {
            initItem(builder, RecipeIngredientRole.CATALYST, conditionSlot,
                    List.of(recipe.condition()))
                    .setSlotName("condition")
                    .addTooltipCallback((view, tooltip) -> tooltip.add(
                            Component.translatable("jei.mekanicalcreate.slot.condition")
                                    .withStyle(ChatFormatting.AQUA)));
        }

        int inputCount = Math.min(recipe.inputs().size(), inputSlots.size());
        for (int index = 0; index < inputCount; index++) {
            DisplayInput input = recipe.inputs().get(index);
            RecipeIngredientRole role = input.consumed()
                    ? RecipeIngredientRole.INPUT : RecipeIngredientRole.CATALYST;
            IRecipeSlotBuilder slot = initItem(builder, role, inputSlots.get(index), withCount(input))
                    .setSlotName("material_" + index);
            if (!input.consumed()) {
                slot.addTooltipCallback((view, tooltip) -> tooltip.add(
                        Component.translatable("jei.mekanicalcreate.not_consumed")
                                .withStyle(ChatFormatting.GRAY)));
            }
        }

        int fluidInputCount = supportsFluids
                ? Math.min(recipe.fluidInputs().size(), inputFluidGauges.size()) : 0;
        for (int index = 0; index < fluidInputCount; index++) {
            initFluid(builder, RecipeIngredientRole.INPUT, inputFluidGauges.get(index),
                    Arrays.asList(recipe.fluidInputs().get(index).getFluids()))
                    .setSlotName("fluid_input_" + index);
        }

        int fluidOutputCount = supportsFluids
                ? Math.min(recipe.fluidOutputs().size(), outputFluidGauges.size()) : 0;
        for (int index = 0; index < fluidOutputCount; index++) {
            FluidStack output = recipe.fluidOutputs().get(index);
            initFluid(builder, RecipeIngredientRole.OUTPUT, outputFluidGauges.get(index),
                    List.of(output)).setSlotName("fluid_output_" + index);
        }

        int outputCount = Math.min(recipe.outputs().size(), outputGroups.size() * 2);
        for (int index = 0; index < outputCount; index++) {
            DisplayOutput output = recipe.outputs().get(index);
            GuiSlot group = outputGroups.get(index / 2);
            int x = group.getX() + (index % 2 == 0 ? 4 : 20);
            int y = group.getY() + 4;
            IRecipeSlotBuilder slot = initItem(builder, RecipeIngredientRole.OUTPUT,
                    x, y, List.of(output.stack()))
                    .setSlotName("output_" + index);
            if (output.chance() < 0.9999F) {
                slot.addTooltipCallback((view, tooltip) -> tooltip.add(
                        Component.translatable("jei.mekanicalcreate.chance",
                                        TextUtils.getPercent(output.chance()))
                                .withStyle(ChatFormatting.GOLD)));
            }
        }
    }

    private static List<ItemStack> withCount(DisplayInput input) {
        return Arrays.stream(input.ingredient().getItems())
                .map(stack -> stack.copyWithCount(input.count()))
                .toList();
    }

    @Override
    protected void renderElements(DisplayRecipe recipe, IRecipeSlotsView recipeSlotsView,
                                  GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderElements(recipe, recipeSlotsView, graphics, mouseX, mouseY);
        var font = Minecraft.getInstance().font;
        for (int groupIndex = 0; groupIndex < outputGroups.size(); groupIndex++) {
            int firstOutput = groupIndex * 2;
            if (firstOutput >= recipe.outputs().size()) {
                break;
            }
            int displayedOutput = Math.min(firstOutput + 1, recipe.outputs().size() - 1);
            float chance = recipe.outputs().get(displayedOutput).chance();
            if (chance < 0.9999F) {
                graphics.drawCenteredString(font, TextUtils.getPercent(chance),
                        outputX + outputGroups.get(groupIndex).getWidth() / 2 - 1,
                        OUTPUT_CHANCE_Y[groupIndex], 0xFF404040);
            }
        }
        graphics.drawString(font, recipe.processName(), INPUT_X, 86, 0xFF404040, false);
        if (recipe.sequenceSteps() > 0) {
            graphics.drawString(font,
                    Component.translatable("jei.mekanicalcreate.sequence_summary",
                            recipe.sequenceSteps(), recipe.loops()),
                    INPUT_X, 96, 0xFF606060, false);
        }
    }

    @Override
    public ResourceLocation getRegistryName(DisplayRecipe recipe) {
        return recipe.id();
    }

    @Nullable
    @Override
    public Codec<DisplayRecipe> getCodec(ICodecHelper codecHelper, IRecipeManager recipeManager) {
        return null;
    }
}
