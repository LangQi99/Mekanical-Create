package io.github.langqi99.mekanicalcreate.client;

import io.github.langqi99.mekanicalcreate.content.MekanicalStressInterfaceMenu;
import mekanism.client.gui.GuiMekanism;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 传动接口界面：使用 Mekanism 自己的界面框架（GuiMekanism 自带 Mek 底板、边框与边角按钮），
 * 不再是我手画的矩形面板。
 */
public final class StressInterfaceScreen extends GuiMekanism<MekanicalStressInterfaceMenu> {
    public StressInterfaceScreen(MekanicalStressInterfaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 96;
        this.inventoryLabelY = this.imageHeight - 10;
    }

    @Override
    protected void addGuiElements() {
        // 没有槽位
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        long demand = menu.demand();
        long capacity = menu.capacity();
        int left = (width - imageWidth) / 2 + 8;
        int top = (height - imageHeight) / 2;

        graphics.drawString(font(), Component.translatable(
                "gui.mekanicalcreate.stress_interface.values", demand, capacity), left, top + 16, 0x404040, false);

        Component status;
        int color;
        if (demand > 0 && capacity < demand) {
            status = Component.translatable("gui.mekanicalcreate.stress_interface.starved");
            color = 0xB22222;
        } else if (demand > 0 && capacity >= Math.round(demand * 1.5D)) {
            status = Component.translatable("gui.mekanicalcreate.stress_interface.overclocked");
            color = 0x2E7D32;
        } else {
            status = Component.translatable("gui.mekanicalcreate.stress_interface.normal");
            color = 0x404040;
        }
        graphics.drawString(font(), status, left, top + 34, color, false);

        graphics.drawString(font(), Component.translatable("gui.mekanicalcreate.stress_interface.rpm",
                Math.abs((int) menu.rpm())),
                left, top + 52, 0x404040, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
