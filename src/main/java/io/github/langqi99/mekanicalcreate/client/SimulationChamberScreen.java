package io.github.langqi99.mekanicalcreate.client;

import io.github.langqi99.mekanicalcreate.content.SimulationChamberBlockEntity;
import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
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
        imageHeight += 36;
        inventoryLabelY += 36;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addRenderableWidget(new GuiVerticalPowerBar(this, tile.getEnergyContainer(), 11, 24))
                .warning(WarningType.NOT_ENOUGH_ENERGY, tile::isEnergyStarved);
        addRenderableWidget(new GuiProgress(tile::getScaledProgress, ProgressType.SMALL_RIGHT, this, 123, 55));
        addRenderableWidget(new GuiEnergyTab(this, tile.getEnergyContainer(), tile::getActive));
    }

    @Override
    protected void drawForegroundText(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        renderTitleText(graphics);
        drawString(graphics, playerInventoryTitle, inventoryLabelX, inventoryLabelY, titleTextColor());
        super.drawForegroundText(graphics, mouseX, mouseY);
    }
}
