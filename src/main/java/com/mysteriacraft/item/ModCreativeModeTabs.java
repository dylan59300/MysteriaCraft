package com.mysteriacraft.item;

import com.mysteriacraft.MysteriaCraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeModeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(ForgeRegistries.CREATIVE_MODE_TABS, MysteriaCraft.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MYSTERIACRAFT_TAB = CREATIVE_MODE_TABS.register("mysteriacraft_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("creativetab.mysteriacraft.mysteriacraft_tab"))
                    .icon(() -> new ItemStack(ModItems.MYSTIC_CRYSTAL.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.MAGIC_ESSENCE.get());
                        output.accept(ModItems.MYSTIC_CRYSTAL.get());
                        output.accept(ModItems.MYSTERIA_WAND.get());
                    })
                    .build());
}
