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
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.recipe_viewer.RecipeViewerUtils;
import mekanism.client.recipe_viewer.jei.BaseRecipeCategory;
import mekanism.common.inventory.container.slot.SlotOverlay;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SimulationChamberRecipeCategory extends BaseRecipeCategory<DisplayRecipe> {
    public static final RecipeType<DisplayRecipe> TYPE = RecipeType.create(
            MekanicalCreate.MOD_ID, "simulation_chamber", DisplayRecipe.class);

    private static final int WIDTH = 194;
    private static final int HEIGHT = 106;
    private static final int INPUT_X = 32;
    private static final int INPUT_Y = 8;
    private static final int OUTPUT_X = 140;
    private static final int[] OUTPUT_Y = {18, 55};
    private static final int[] OUTPUT_CHANCE_Y = {38, 75};

    private final GuiSlot moduleSlot;
    private final GuiSlot conditionSlot;
    private final List<GuiSlot> inputSlots = new ArrayList<>(16);
    private final List<GuiSlot> outputGroups = new ArrayList<>(2);

    public SimulationChamberRecipeCategory(IGuiHelper guiHelper, ItemStack factory) {
        super(guiHelper, TYPE,
                Component.translatable("jei.mekanicalcreate.simulation_chamber"),
                guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, factory),
                0, 0, WIDTH, HEIGHT);

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
            outputGroups.add(addSlot(SlotType.OUTPUT_WIDE, OUTPUT_X, y));
        }

        addSimpleProgress(ProgressType.BAR, 110, 42);
        addElement(new GuiVerticalPowerBar(this, RecipeViewerUtils.FULL_BAR, 186, 9));
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

        int outputCount = Math.min(recipe.outputs().size(), outputGroups.size() * 2);
        for (int index = 0; index < outputCount; index++) {
            DisplayOutput output = recipe.outputs().get(index);
            GuiSlot group = outputGroups.get(index / 2);
            int x = group.getX() + (index % 2 == 0 ? 4 : 20);
            int y = group.getY() + 4;
            IRecipeSlotBuilder slot = initItem(builder, RecipeIngredientRole.OUTPUT,
                    x, y, List.of(output.stack()))
                    .setSlotName("output_" + index);
            slot.addTooltipCallback((view, tooltip) -> tooltip.add(
                    Component.translatable("jei.mekanicalcreate.chance",
                                    TextUtils.getPercent(output.chance()))
                            .withStyle(output.chance() < 0.9999F
                                    ? ChatFormatting.GOLD : ChatFormatting.GRAY)));
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
            graphics.drawCenteredString(font,
                    TextUtils.getPercent(recipe.outputs().get(displayedOutput).chance()),
                    OUTPUT_X + outputGroups.get(groupIndex).getWidth() / 2 - 1,
                    OUTPUT_CHANCE_Y[groupIndex], 0xFF404040);
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
