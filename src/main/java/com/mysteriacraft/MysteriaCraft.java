package com.mysteriacraft;

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
        LOGGER.info("MysteriaCraft se reveille...");
    }
}
