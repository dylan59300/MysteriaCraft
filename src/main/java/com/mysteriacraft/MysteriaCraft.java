package com.mysteriacraft;

import com.mysteriacraft.block.ModBlocks;
import com.mysteriacraft.block.entity.ModBlockEntities;
import com.mysteriacraft.item.ModCreativeModeTabs;
import com.mysteriacraft.item.ModItems;
import com.mysteriacraft.menu.ModMenuTypes;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MysteriaCraft.MOD_ID)
public class MysteriaCraft {

    public static final String MOD_ID = "mysteriacraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MysteriaCraft.class);

    public MysteriaCraft() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModCreativeModeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        LOGGER.info("MysteriaCraft se reveille...");
    }
}
