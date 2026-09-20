package com.mysteriacraft.client;

import com.mysteriacraft.MysteriaCraft;
import com.mysteriacraft.block.ModBlocks;
import com.mysteriacraft.client.screen.MachineScreen;
import com.mysteriacraft.menu.MachineMenu;
import com.mysteriacraft.menu.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = MysteriaCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.LUMINESCENT_LEAVES.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.ENCHANTED_GLASS.get(), RenderType.cutout());

            registerMachineScreen(ModMenuTypes.ESSENCE_EXTRACTOR_MENU.get(), "essence_extractor");
            registerMachineScreen(ModMenuTypes.CRYSTAL_INFUSER_MENU.get(), "crystal_infuser");
            registerMachineScreen(ModMenuTypes.ARCANE_CONDENSER_MENU.get(), "arcane_condenser");
            registerMachineScreen(ModMenuTypes.RUNIC_FORGE_MENU.get(), "runic_forge");
            registerMachineScreen(ModMenuTypes.STARLIGHT_REFINER_MENU.get(), "starlight_refiner");
            registerMachineScreen(ModMenuTypes.RUBY_FURNACE_MENU.get(), "ruby_furnace");
        });
    }

    private static void registerMachineScreen(net.minecraft.world.inventory.MenuType<MachineMenu> menuType, String texture) {
        MenuScreens.register(menuType, (MachineMenu menu, Inventory inv, Component title) -> new MachineScreen(menu, inv, title, texture));
    }
}
