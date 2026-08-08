package io.github.langqi99.mekanicalcreate.client.jei;

import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import io.github.langqi99.mekanicalcreate.content.SimulationRecipeResolver.DisplayInput;
import io.github.langqi99.mekanicalcreate.content.SimulationRecipeResolver.DisplayOutput;
import io.github.langqi99.mekanicalcreate.content.SimulationRecipeResolver.DisplayRecipe;
import java.util.Arrays;
import java.util.List;
import mekanism.common.util.text.TextUtils;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class SimulationChamberRecipeCategory implements IRecipeCategory<DisplayRecipe> {
    public static final RecipeType<DisplayRecipe> TYPE = RecipeType.create(
            MekanicalCreate.MOD_ID, "simulation_chamber", DisplayRecipe.class);
    private static final int WIDTH = 180;
    private static final int HEIGHT = 100;

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable arrow;

    public SimulationChamberRecipeCategory(IGuiHelper guiHelper, ItemStack chamber) {
        background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, chamber);
        arrow = guiHelper.createAnimatedRecipeArrow(100);
    }

    @Override
    public RecipeType<DisplayRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.mekanicalcreate.simulation_chamber");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder, @NotNull DisplayRecipe recipe,
                          @NotNull IFocusGroup focuses) {
        builder.setShapeless();
        builder.addSlot(RecipeIngredientRole.CATALYST, 3, 5)
                .setStandardSlotBackground()
                .setSlotName("module")
                .addItemStack(recipe.module())
                .addTooltipCallback((view, tooltip) -> tooltip.add(
                        Component.translatable("jei.mekanicalcreate.slot.module")
                                .withStyle(ChatFormatting.AQUA)));
        if (!recipe.condition().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.CATALYST, 3, 27)
                    .setStandardSlotBackground()
                    .setSlotName("condition")
                    .addItemStack(recipe.condition())
                    .addTooltipCallback((view, tooltip) -> tooltip.add(
                            Component.translatable("jei.mekanicalcreate.slot.condition")
                                    .withStyle(ChatFormatting.AQUA)));
        }

        for (int index = 0; index < recipe.inputs().size(); index++) {
            DisplayInput input = recipe.inputs().get(index);
            int x = 35 + index % 4 * 19;
            int y = 5 + index / 4 * 19;
            RecipeIngredientRole role = input.consumed()
                    ? RecipeIngredientRole.INPUT : RecipeIngredientRole.CATALYST;
            IRecipeSlotBuilder slot = builder.addSlot(role, x, y)
                    .setStandardSlotBackground()
                    .setSlotName("material_" + index)
                    .addItemStacks(withCount(input));
            if (!input.consumed()) {
                slot.addTooltipCallback((view, tooltip) -> tooltip.add(
                        Component.translatable("jei.mekanicalcreate.not_consumed")
                                .withStyle(ChatFormatting.GRAY)));
            }
        }

        for (int index = 0; index < recipe.outputs().size(); index++) {
            DisplayOutput output = recipe.outputs().get(index);
            int x = 137 + index % 2 * 19;
            int y = 14 + index / 2 * 19;
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.OUTPUT, x, y)
                    .setOutputSlotBackground()
                    .setSlotName("output_" + index)
                    .addItemStack(output.stack());
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
    public void draw(@NotNull DisplayRecipe recipe, @NotNull IRecipeSlotsView recipeSlotsView,
                     @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, 108, 33);
        graphics.drawString(Minecraft.getInstance().font, recipe.processName(), 35, 83,
                0xFF404040, false);
        if (recipe.sequenceSteps() > 0) {
            graphics.drawString(Minecraft.getInstance().font,
                    Component.translatable("jei.mekanicalcreate.sequence_summary",
                            recipe.sequenceSteps(), recipe.loops()),
                    35, 92, 0xFF606060, false);
        }
    }

    @Override
    public ResourceLocation getRegistryName(DisplayRecipe recipe) {
        return recipe.id();
    }
}
