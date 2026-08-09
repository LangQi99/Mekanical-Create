package io.github.langqi99.mekanicalcreate.client;

import io.github.langqi99.mekanicalcreate.content.SimulationChamberBlockEntity;
import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import mekanism.common.tile.component.config.DataType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public final class SimulationChamberScreen extends AbstractSimulationChamberScreen<SimulationChamberBlockEntity> {
    public SimulationChamberScreen(MekanismTileContainer<SimulationChamberBlockEntity> menu,
                                   Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}

abstract class AbstractSimulationChamberScreen<TILE extends SimulationChamberBlockEntity>
        extends GuiConfigurableTile<TILE, MekanismTileContainer<TILE>> {
    protected AbstractSimulationChamberScreen(MekanismTileContainer<TILE> menu,
                                              Inventory inventory, Component title) {
        super(menu, inventory, title);
        if (menu.getTileEntity().supportsFluids()) {
            imageWidth += 92;
            inventoryLabelX += 46;
        }
        imageHeight += 36;
        inventoryLabelY += 36;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addRenderableWidget(new GuiVerticalPowerBar(this, tile.getEnergyContainer(), 11, 24))
                .warning(WarningType.NOT_ENOUGH_ENERGY, tile::isEnergyStarved);
        if (tile.supportsFluids()) {
            for (int tank = 0; tank < 2; tank++) {
                int index = tank;
                addRenderableWidget(new GuiFluidGauge(
                        () -> tile.getInputFluidTanks().get(index),
                        () -> tile.getFluidTanks(null),
                        GaugeType.SMALL.with(DataType.INPUT), this, 128 + tank * 20, 24));
                addRenderableWidget(new GuiFluidGauge(
                        () -> tile.getOutputFluidTanks().get(index),
                        () -> tile.getFluidTanks(null),
                        GaugeType.SMALL.with(DataType.OUTPUT), this, 202 + tank * 20, 24));
            }
            addRenderableWidget(new GuiProgress(tile::getScaledProgress,
                    ProgressType.SMALL_RIGHT, this, 170, 55));
        } else {
            addRenderableWidget(new GuiProgress(tile::getScaledProgress,
                    ProgressType.SMALL_RIGHT, this, 123, 55));
        }
        addRenderableWidget(new GuiEnergyTab(this, tile.getEnergyContainer(), tile::getActive));
    }

    @Override
    protected void drawForegroundText(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        renderTitleText(graphics);
        renderInventoryText(graphics);
        super.drawForegroundText(graphics, mouseX, mouseY);
    }
}
