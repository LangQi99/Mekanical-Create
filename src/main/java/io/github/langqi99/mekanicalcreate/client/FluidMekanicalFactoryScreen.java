package io.github.langqi99.mekanicalcreate.client;

import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryControllerBlockEntity;
import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryContainer;
import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryGuiLayout;
import java.util.ArrayList;
import java.util.List;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.math.FloatingLong;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.util.text.EnergyDisplay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public final class FluidMekanicalFactoryScreen extends GuiMekanismTile<
        MekanicalFactoryControllerBlockEntity,
        MekanicalFactoryContainer> {

    private final List<GuiFluidGauge> inputGauges = new ArrayList<>();
    private final List<GuiFluidGauge> outputGauges = new ArrayList<>();

    public FluidMekanicalFactoryScreen(
            MekanicalFactoryContainer menu,
            Inventory inventory, Component title) {
        super(menu, inventory, title);
        int tankCount = tile.getFluidTankCount();
        int catalystSlotCount = tile.getCatalystSlotCount();
        imageWidth = MekanicalFactoryGuiLayout.imageWidth(tankCount, catalystSlotCount);
        imageHeight = MekanicalFactoryGuiLayout.IMAGE_HEIGHT;
        inventoryLabelX = MekanicalFactoryGuiLayout.inventoryLabelX(
                tankCount, catalystSlotCount);
        inventoryLabelY = MekanicalFactoryGuiLayout.INVENTORY_LABEL_Y;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        inputGauges.clear();
        outputGauges.clear();
        addRenderableWidget(new GuiVerticalPowerBar(this, tile.getEnergyContainer(),
                MekanicalFactoryGuiLayout.POWER_BAR_X,
                MekanicalFactoryGuiLayout.POWER_BAR_Y))
                .warning(WarningType.NOT_ENOUGH_ENERGY, tile::isEnergyStarved);
        int tankCount = tile.getFluidTankCount();
        int catalystSlotCount = tile.getCatalystSlotCount();
        for (int tank = 0; tank < tankCount; tank++) {
            int index = tank;
            GuiFluidGauge inputGauge = addRenderableWidget(new GuiFluidGauge(
                    () -> tile.getAllInputFluidTanks().get(index),
                    tile::getActiveFluidTanks,
                    GaugeType.SMALL_MED.with(DataType.INPUT), this,
                    MekanicalFactoryGuiLayout.inputFluidGaugeX(
                            tankCount, catalystSlotCount, tank),
                    MekanicalFactoryGuiLayout.FLUID_GAUGE_Y));
            GuiFluidGauge outputGauge = addRenderableWidget(new GuiFluidGauge(
                    () -> tile.getAllOutputFluidTanks().get(index),
                    tile::getActiveFluidTanks,
                    GaugeType.SMALL_MED.with(DataType.OUTPUT), this,
                    MekanicalFactoryGuiLayout.outputFluidGaugeX(
                            tankCount, catalystSlotCount, tank),
                    MekanicalFactoryGuiLayout.FLUID_GAUGE_Y));
            inputGauges.add(inputGauge);
            outputGauges.add(outputGauge);
        }
        addRenderableWidget(new GuiProgress(tile::getScaledProgress,
                ProgressType.SMALL_RIGHT, this,
                MekanicalFactoryGuiLayout.progressX(tankCount, catalystSlotCount),
                MekanicalFactoryGuiLayout.PROGRESS_Y));
        addRenderableWidget(new GuiEnergyTab(this, () -> List.of(
                MekanismLang.USING.translate(EnergyDisplay.of(
                        FloatingLong.create(tile.getActive() ? tile.getEnergyPerTick() : 0))),
                MekanismLang.NEEDED.translate(EnergyDisplay.of(
                        tile.getEnergyContainer().getNeeded())))));
    }

    @Override
    protected DataType findDataType(InventoryContainerSlot slot) {
        IInventorySlot inventorySlot = slot.getInventorySlot();
        if (tile.getInputSlots().contains(inventorySlot)) {
            return DataType.INPUT;
        }
        if (tile.getCatalystSlots().contains(inventorySlot)) {
            return DataType.EXTRA;
        }
        if (tile.getOutputSlots().contains(inventorySlot)) {
            return DataType.OUTPUT;
        }
        return super.findDataType(slot);
    }

    @Override
    protected void drawForegroundText(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        renderTitleText(graphics);
        drawString(graphics, playerInventoryTitle, inventoryLabelX, inventoryLabelY, titleTextColor());
        super.drawForegroundText(graphics, mouseX, mouseY);
    }
}
