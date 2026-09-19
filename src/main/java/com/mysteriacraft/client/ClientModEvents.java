package com.mysteriacraft.client;

import com.mysteriacraft.MysteriaCraft;
import com.mysteriacraft.block.ModBlocks;
import com.mysteriacraft.client.screen.MachineScreen;
import com.mysteriacraft.menu.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
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

            MenuScreens.register(ModMenuTypes.ESSENCE_EXTRACTOR_MENU.get(),
                    (menu, inv, title) -> new MachineScreen(menu, inv, title, "essence_extractor"));
            MenuScreens.register(ModMenuTypes.CRYSTAL_INFUSER_MENU.get(),
                    (menu, inv, title) -> new MachineScreen(menu, inv, title, "crystal_infuser"));
            MenuScreens.register(ModMenuTypes.ARCANE_CONDENSER_MENU.get(),
                    (menu, inv, title) -> new MachineScreen(menu, inv, title, "arcane_condenser"));
            MenuScreens.register(ModMenuTypes.RUNIC_FORGE_MENU.get(),
                    (menu, inv, title) -> new MachineScreen(menu, inv, title, "runic_forge"));
            MenuScreens.register(ModMenuTypes.STARLIGHT_REFINER_MENU.get(),
                    (menu, inv, title) -> new MachineScreen(menu, inv, title, "starlight_refiner"));
            MenuScreens.register(ModMenuTypes.RUBY_FURNACE_MENU.get(),
                    (menu, inv, title) -> new MachineScreen(menu, inv, title, "ruby_furnace"));
        });
    }
}
