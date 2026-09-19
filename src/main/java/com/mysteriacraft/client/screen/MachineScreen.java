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

    private void renderEnergyBar(GuiGraphics guiGraphics, int x, int y) {
        int maxEnergy = Math.max(1, menu.getMaxEnergy());
        int energyHeight = (int) (52 * ((float) menu.getEnergy() / maxEnergy));
        if (energyHeight > 0) {
            guiGraphics.fill(x + 10, y + 69 - energyHeight, x + 20, y + 69, 0xFFE0A030);
        }
    }

    private void renderProgressArrow(GuiGraphics guiGraphics, int x, int y) {
        int maxProgress = Math.max(1, menu.getMaxProgress());
        int arrowWidth = (int) (16 * ((float) menu.getProgress() / maxProgress));
        if (arrowWidth > 0) {
            guiGraphics.fill(x + 79, y + 41, x + 79 + arrowWidth, y + 47, 0xFF3ADF6C);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
