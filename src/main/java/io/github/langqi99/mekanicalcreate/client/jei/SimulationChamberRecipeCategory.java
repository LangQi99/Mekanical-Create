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
import mekanism.client.gui.element.progress.GuiProgress;
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
    private static final int COMPACT_HEIGHT = 106;
    private static final int COMPACT_WIDTH = 194;
    private static final int INPUT_X = 32;
    private static final int INPUT_Y = 8;
    private static final int COMPACT_FLOW_LEFT = 28;
    private static final int COMPACT_FLOW_RIGHT = 183;
    private static final int COMPACT_CONTENT_TOP = 8;
    private static final int COMPACT_CONTENT_HEIGHT = 72;
    private static final int COMPACT_ENERGY_X = 186;
    private static final int COMPACT_GAUGE_SPACING = 20;
    private static final int COMPACT_COMBINE_GAP = 10;
    private static final int COMPACT_ARROW_GAP = 5;
    private static final int[] OUTPUT_Y = {18, 55};

    private final GuiSlot moduleSlot;
    private final GuiSlot conditionSlot;
    private final List<GuiSlot> inputSlots = new ArrayList<>(16);
    private final List<GuiSlot> outputGroups = new ArrayList<>(2);
    private final List<GuiSlot> compactOutputSlots = new ArrayList<>(4);
    private final List<GuiFluidGauge> inputFluidGauges = new ArrayList<>(3);
    private final List<GuiFluidGauge> outputFluidGauges = new ArrayList<>(3);
    private final GuiProgress processProgress;
    private final boolean supportsFluids;
    private final int inputX;
    private final int outputX;
    private int compactTextX = COMPACT_FLOW_LEFT;
    private int inputPlusX = -1;
    private int outputPlusX = -1;

    public SimulationChamberRecipeCategory(IGuiHelper guiHelper, ItemStack factory,
                                           boolean supportsFluids) {
        super(guiHelper, supportsFluids ? FLUID_TYPE : TYPE,
                Component.translatable(supportsFluids
                        ? "jei.mekanicalcreate.fluid_mekanical_factory"
                        : "jei.mekanicalcreate.simulation_chamber"),
                guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, factory),
                0, 0, supportsFluids ? COMPACT_WIDTH : 194,
                supportsFluids ? COMPACT_HEIGHT : HEIGHT);
        this.supportsFluids = supportsFluids;
        inputX = supportsFluids ? COMPACT_FLOW_LEFT : INPUT_X;
        outputX = 140;

        moduleSlot = addSlot(SlotType.EXTRA, 5, 8);
        conditionSlot = addSlot(SlotType.EXTRA, 5, 26);
        addElement(new GuiUpArrow(this, 9, 47));
        addSlot(SlotType.POWER, 5, 62).with(SlotOverlay.POWER);

        for (int index = 0; index < 16; index++) {
            inputSlots.add(addSlot(SlotType.INPUT,
                    inputX + index % 4 * 18,
                    INPUT_Y + index / 4 * 18));
        }
        if (supportsFluids) {
            for (int output = 0; output < 4; output++) {
                compactOutputSlots.add(addSlot(SlotType.OUTPUT,
                        COMPACT_FLOW_LEFT, COMPACT_CONTENT_TOP));
            }
            for (int tank = 0; tank < 3; tank++) {
                inputFluidGauges.add(addElement(GuiFluidGauge.getDummy(
                        GaugeType.SMALL_MED.with(DataType.INPUT), this,
                        COMPACT_FLOW_LEFT, COMPACT_CONTENT_TOP)));
                outputFluidGauges.add(addElement(GuiFluidGauge.getDummy(
                        GaugeType.SMALL_MED.with(DataType.OUTPUT), this,
                        COMPACT_FLOW_LEFT, COMPACT_CONTENT_TOP)));
            }
        } else {
            for (int y : OUTPUT_Y) {
                outputGroups.add(addSlot(SlotType.OUTPUT_WIDE, outputX, y));
            }
        }

        processProgress = addSimpleProgress(
                supportsFluids ? ProgressType.SMALL_RIGHT : ProgressType.BAR,
                supportsFluids ? COMPACT_FLOW_LEFT : 110,
                supportsFluids ? 40 : 42);
        addElement(new GuiVerticalPowerBar(this, RecipeViewerUtils.FULL_BAR,
                supportsFluids ? COMPACT_ENERGY_X : 186, 9));
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder, @NotNull DisplayRecipe recipe,
                          @NotNull IFocusGroup focuses) {
        layoutDynamicElements(recipe);
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

        int outputCount = Math.min(recipe.outputs().size(), supportsFluids
                ? compactOutputSlots.size() : outputGroups.size() * 2);
        for (int index = 0; index < outputCount; index++) {
            DisplayOutput output = recipe.outputs().get(index);
            IRecipeSlotBuilder slot;
            if (supportsFluids) {
                slot = initItem(builder, RecipeIngredientRole.OUTPUT,
                        compactOutputSlots.get(index), List.of(output.stack()))
                        .setSlotName("output_" + index);
            } else {
                GuiSlot group = outputGroups.get(index / 2);
                int x = group.getX() + (index % 2 == 0 ? 4 : 20);
                int y = group.getY() + 4;
                slot = initItem(builder, RecipeIngredientRole.OUTPUT,
                        x, y, List.of(output.stack()))
                        .setSlotName("output_" + index);
            }
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
        layoutDynamicElements(recipe);
        updateDynamicElementVisibility(recipe);
        super.renderElements(recipe, recipeSlotsView, graphics, mouseX, mouseY);
        var font = Minecraft.getInstance().font;
        if (supportsFluids) {
            if (inputPlusX >= 0) {
                graphics.drawCenteredString(font, "+", inputPlusX, 40, 0xFF606060);
            }
            if (outputPlusX >= 0) {
                graphics.drawCenteredString(font, "+", outputPlusX, 40, 0xFF606060);
            }
        }
        graphics.drawString(font, recipe.processName(),
                supportsFluids ? compactTextX : inputX,
                supportsFluids ? 84 : 86, 0xFF404040, false);
        if (recipe.sequenceSteps() > 0) {
            graphics.drawString(font,
                    Component.translatable("jei.mekanicalcreate.sequence_summary",
                            recipe.sequenceSteps(), recipe.loops()),
                    supportsFluids ? compactTextX : inputX,
                    supportsFluids ? 94 : 96, 0xFF606060, false);
        }
    }

    private void layoutDynamicElements(DisplayRecipe recipe) {
        if (!supportsFluids) {
            return;
        }

        if (recipe.condition().isEmpty()) {
            moveSlot(moduleSlot, 5, 17);
        } else {
            moveSlot(moduleSlot, 5, 8);
            moveSlot(conditionSlot, 5, 26);
        }

        int inputCount = Math.min(recipe.inputs().size(), inputSlots.size());
        int fluidInputCount = Math.min(recipe.fluidInputs().size(), inputFluidGauges.size());
        int itemOutputCount = Math.min(recipe.outputs().size(), compactOutputSlots.size());
        int fluidOutputCount = Math.min(recipe.fluidOutputs().size(), outputFluidGauges.size());

        int inputColumns = Math.min(4, inputCount);
        int inputRows = inputCount == 0 ? 0 : (inputCount + inputColumns - 1) / inputColumns;
        int inputItemsWidth = inputColumns * 18;
        int inputFluidsWidth = gaugeGroupWidth(fluidInputCount);

        int outputColumns = Math.min(2, itemOutputCount);
        int outputRows = itemOutputCount == 0 ? 0
                : (itemOutputCount + outputColumns - 1) / outputColumns;
        int outputItemsWidth = outputColumns * 18;
        int outputFluidsWidth = gaugeGroupWidth(fluidOutputCount);

        int combineGap = COMPACT_COMBINE_GAP;
        int arrowGap = COMPACT_ARROW_GAP;
        int inputGroupWidth = combinedWidth(inputItemsWidth, inputFluidsWidth, combineGap);
        int outputGroupWidth = combinedWidth(outputFluidsWidth, outputItemsWidth, combineGap);
        int flowWidth = inputGroupWidth + arrowGap + processProgress.getWidth()
                + arrowGap + outputGroupWidth;
        int availableWidth = COMPACT_FLOW_RIGHT - COMPACT_FLOW_LEFT;
        if (flowWidth > availableWidth) {
            combineGap = 6;
            arrowGap = 3;
            inputGroupWidth = combinedWidth(inputItemsWidth, inputFluidsWidth, combineGap);
            outputGroupWidth = combinedWidth(outputFluidsWidth, outputItemsWidth, combineGap);
            flowWidth = inputGroupWidth + arrowGap + processProgress.getWidth()
                    + arrowGap + outputGroupWidth;
        }

        int cursor = flowWidth <= availableWidth
                ? COMPACT_FLOW_LEFT + (availableWidth - flowWidth) / 2
                : Math.max(24, COMPACT_FLOW_RIGHT - flowWidth);
        compactTextX = cursor;
        inputPlusX = -1;
        outputPlusX = -1;

        if (inputCount > 0) {
            int inputHeight = inputRows * 18;
            int inputY = centeredY(inputHeight);
            for (int index = 0; index < inputCount; index++) {
                moveSlot(inputSlots.get(index), cursor + index % inputColumns * 18,
                        inputY + index / inputColumns * 18);
            }
            cursor += inputItemsWidth;
        }
        if (inputCount > 0 && fluidInputCount > 0) {
            inputPlusX = cursor + combineGap / 2;
            cursor += combineGap;
        }
        if (fluidInputCount > 0) {
            layoutGaugeRow(inputFluidGauges, fluidInputCount, cursor);
            cursor += inputFluidsWidth;
        }

        cursor += arrowGap;
        moveElement(processProgress, cursor, centeredY(processProgress.getHeight()));
        cursor += processProgress.getWidth() + arrowGap;

        if (fluidOutputCount > 0) {
            layoutGaugeRow(outputFluidGauges, fluidOutputCount, cursor);
            cursor += outputFluidsWidth;
        }
        if (fluidOutputCount > 0 && itemOutputCount > 0) {
            outputPlusX = cursor + combineGap / 2;
            cursor += combineGap;
        }
        if (itemOutputCount > 0) {
            int outputRowSpacing = 18;
            int outputHeight = outputRows * outputRowSpacing;
            int outputY = centeredY(outputHeight);
            for (int index = 0; index < itemOutputCount; index++) {
                moveSlot(compactOutputSlots.get(index),
                        cursor + index % outputColumns * 18,
                        outputY + index / outputColumns * outputRowSpacing);
            }
        }
    }

    private static int combinedWidth(int firstWidth, int secondWidth, int gap) {
        if (firstWidth == 0) {
            return secondWidth;
        }
        if (secondWidth == 0) {
            return firstWidth;
        }
        return firstWidth + gap + secondWidth;
    }

    private static int gaugeGroupWidth(int count) {
        return count == 0 ? 0 : (count - 1) * COMPACT_GAUGE_SPACING + 18;
    }

    private static int centeredY(int height) {
        return COMPACT_CONTENT_TOP + (COMPACT_CONTENT_HEIGHT - height) / 2;
    }

    private static void layoutGaugeRow(List<GuiFluidGauge> gauges, int visibleCount,
                                       int x) {
        int y = centeredY(gauges.get(0).getHeight());
        for (int index = 0; index < visibleCount; index++) {
            moveElement(gauges.get(index), x + index * COMPACT_GAUGE_SPACING, y);
        }
    }

    private static void moveSlot(GuiSlot slot, int x, int y) {
        moveElement(slot, x - 1, y - 1);
    }

    private static void moveElement(mekanism.client.gui.element.GuiElement element,
                                    int x, int y) {
        element.move(x - element.getX(), y - element.getY());
    }

    /** The fluid category only draws channels used by the current recipe. */
    private void updateDynamicElementVisibility(DisplayRecipe recipe) {
        if (!supportsFluids) {
            return;
        }
        conditionSlot.visible = !recipe.condition().isEmpty();
        for (int index = 0; index < inputSlots.size(); index++) {
            inputSlots.get(index).visible = index < recipe.inputs().size();
        }
        for (int index = 0; index < compactOutputSlots.size(); index++) {
            compactOutputSlots.get(index).visible = index < recipe.outputs().size();
        }
        for (int index = 0; index < inputFluidGauges.size(); index++) {
            inputFluidGauges.get(index).visible = index < recipe.fluidInputs().size();
        }
        for (int index = 0; index < outputFluidGauges.size(); index++) {
            outputFluidGauges.get(index).visible = index < recipe.fluidOutputs().size();
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
