package com.mysteriacraft.client.screen;

import com.mysteriacraft.MysteriaCraft;
import com.mysteriacraft.menu.MachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Écran générique pour les machines MysteriaCraft : affiche la texture de
 * fond propre à la machine, la barre d'énergie et la flèche de progression.
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {

    private final ResourceLocation texture;

    public MachineScreen(MachineMenu menu, Inventory playerInventory, Component title, String textureName) {
        super(menu, playerInventory, title);
        this.texture = new ResourceLocation(MysteriaCraft.MOD_ID, "textures/gui/" + textureName + ".png");
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        guiGraphics.blit(texture, x, y, 0, 0, imageWidth, imageHeight);

        renderEnergyBar(guiGraphics, x, y);
        renderProgressArrow(guiGraphics, x, y);
    }

    private static final int ENERGY_BAR_HEIGHT = 46;
    private static final int PROGRESS_ARROW_WIDTH = 18;

    private void renderEnergyBar(GuiGraphics guiGraphics, int x, int y) {
        int maxEnergy = Math.max(1, menu.getMaxEnergy());
        int energyHeight = (int) (ENERGY_BAR_HEIGHT * ((float) menu.getEnergy() / maxEnergy));
        if (energyHeight > 0) {
            guiGraphics.fill(x + 11, y + 66 - energyHeight, x + 19, y + 66, 0xFFE0A030);
        }
    }

    private void renderProgressArrow(GuiGraphics guiGraphics, int x, int y) {
        int maxProgress = Math.max(1, menu.getMaxProgress());
        int arrowWidth = (int) (PROGRESS_ARROW_WIDTH * ((float) menu.getProgress() / maxProgress));
        if (arrowWidth > 0) {
            guiGraphics.fill(x + 80, y + 36, x + 80 + arrowWidth, y + 40, 0xFF3ADF6C);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
