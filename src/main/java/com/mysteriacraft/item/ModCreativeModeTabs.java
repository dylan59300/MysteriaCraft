package com.mysteriacraft.item;

import com.mysteriacraft.MysteriaCraft;
import com.mysteriacraft.block.ModBlocks;
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
                        // Objets
                        output.accept(ModItems.MAGIC_ESSENCE.get());
                        output.accept(ModItems.MYSTIC_CRYSTAL.get());
                        output.accept(ModItems.MYSTERIA_WAND.get());
                        output.accept(ModItems.STARLIGHT_SHARD.get());
                        output.accept(ModItems.INFUSED_CRYSTAL.get());
                        output.accept(ModItems.ARCANE_DUST.get());
                        output.accept(ModItems.RUNIC_INGOT.get());
                        output.accept(ModItems.STARLIGHT_INGOT.get());

                        // Blocs de decoration / ressources
                        output.accept(ModBlocks.MYSTIC_STONE.get());
                        output.accept(ModBlocks.ARCANE_BRICKS.get());
                        output.accept(ModBlocks.ARCANE_BRICKS_POLISHED.get());
                        output.accept(ModBlocks.VOID_STONE.get());
                        output.accept(ModBlocks.VOID_STONE_POLISHED.get());
                        output.accept(ModBlocks.SHADOW_STONE.get());
                        output.accept(ModBlocks.SHADOW_BRICKS.get());
                        output.accept(ModBlocks.RUNIC_STONE.get());
                        output.accept(ModBlocks.RUNIC_PILLAR.get());
                        output.accept(ModBlocks.STARLIGHT_ORE.get());
                        output.accept(ModBlocks.CRYSTAL_ORE.get());
                        output.accept(ModBlocks.CRYSTAL_BLOCK.get());
                        output.accept(ModBlocks.STARLIGHT_BLOCK.get());
                        output.accept(ModBlocks.LUMINESCENT_LOG.get());
                        output.accept(ModBlocks.LUMINESCENT_PLANKS.get());
                        output.accept(ModBlocks.LUMINESCENT_LEAVES.get());
                        output.accept(ModBlocks.GLOWING_MOSS.get());
                        output.accept(ModBlocks.ENCHANTED_GLASS.get());
                        output.accept(ModBlocks.ENCHANTED_SAND.get());
                        output.accept(ModBlocks.ESSENCE_INFUSED_SOIL.get());

                        // Machines
                        output.accept(ModBlocks.ESSENCE_EXTRACTOR.get());
                        output.accept(ModBlocks.CRYSTAL_INFUSER.get());
                        output.accept(ModBlocks.ARCANE_CONDENSER.get());
                        output.accept(ModBlocks.RUNIC_FORGE.get());
                        output.accept(ModBlocks.STARLIGHT_REFINER.get());

                        // Equipement Infernal
                        output.accept(ModItems.INFERNAL_SWORD.get());
                        output.accept(ModItems.INFERNAL_HELMET.get());
                        output.accept(ModItems.INFERNAL_CHESTPLATE.get());
                        output.accept(ModItems.INFERNAL_LEGGINGS.get());
                        output.accept(ModItems.INFERNAL_BOOTS.get());
                    })
                    .build());
}
