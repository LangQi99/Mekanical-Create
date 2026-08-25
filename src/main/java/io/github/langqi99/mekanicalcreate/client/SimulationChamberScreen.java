package io.github.langqi99.mekanicalcreate.client;

import io.github.langqi99.mekanicalcreate.content.SimulationChamberBlockEntity;
import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.GuiElement;
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
        int progressX = 123;
        addRenderableWidget(new GuiProgress(tile::getScaledProgress,
                ProgressType.SMALL_RIGHT, this, progressX, 55));
        addRenderableWidget(new GuiElement(this, progressX, 43, 28, 9) {
            @Override
            public void drawBackground(@NotNull GuiGraphics graphics, int mouseX,
                                       int mouseY, float partialTicks) {
                int capacity = tile.getParallelProcessCount();
                if (capacity <= 1) {
                    return;
                }
                String text = tile.getActiveLaneCount() + "/" + capacity;
                graphics.drawString(minecraft.font, text,
                        getX() + (width - minecraft.font.width(text)) / 2, getY(),
                        0x404040, false);
            }

            @Override
            public void renderToolTip(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
                int capacity = tile.getParallelProcessCount();
                if (capacity > 1) {
                    graphics.renderTooltip(minecraft.font, Component.translatable(
                            "gui.mekanicalcreate.parallel_lanes.tooltip",
                            tile.getActiveLaneCount(), tile.getRunningLaneCount(), capacity),
                            mouseX, mouseY);
                }
            }
        });
        addRenderableWidget(new GuiEnergyTab(this, tile.getEnergyContainer(), tile::getActive));
    }

    @Override
    protected void drawForegroundText(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        renderTitleText(graphics);
        drawString(graphics, playerInventoryTitle, inventoryLabelX, inventoryLabelY, titleTextColor());
        super.drawForegroundText(graphics, mouseX, mouseY);
    }
}
