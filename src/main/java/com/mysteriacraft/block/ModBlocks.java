package com.mysteriacraft.block;

import com.mysteriacraft.MysteriaCraft;
import com.mysteriacraft.item.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MysteriaCraft.MOD_ID);

    private static RegistryObject<Block> registerBlock(String name, Supplier<Block> block) {
        RegistryObject<Block> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static void registerBlockItem(String name, RegistryObject<Block> block) {
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static BlockBehaviour.Properties baseProps(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(2.0f, 6.0f).sound(SoundType.STONE);
    }

    public static final RegistryObject<Block> MYSTIC_STONE = registerBlock("mystic_stone",
            () -> new Block(baseProps(MapColor.COLOR_PURPLE)));
    public static final RegistryObject<Block> ARCANE_BRICKS = registerBlock("arcane_bricks",
            () -> new Block(baseProps(MapColor.COLOR_PURPLE)));
    public static final RegistryObject<Block> ARCANE_BRICKS_POLISHED = registerBlock("arcane_bricks_polished",
            () -> new Block(baseProps(MapColor.COLOR_PURPLE)));
    public static final RegistryObject<Block> VOID_STONE = registerBlock("void_stone",
            () -> new Block(baseProps(MapColor.COLOR_BLACK).strength(3.0f, 8.0f)));
    public static final RegistryObject<Block> VOID_STONE_POLISHED = registerBlock("void_stone_polished",
            () -> new Block(baseProps(MapColor.COLOR_BLACK).strength(3.0f, 8.0f)));
    public static final RegistryObject<Block> SHADOW_STONE = registerBlock("shadow_stone",
            () -> new Block(baseProps(MapColor.COLOR_GRAY)));
    public static final RegistryObject<Block> SHADOW_BRICKS = registerBlock("shadow_bricks",
            () -> new Block(baseProps(MapColor.COLOR_GRAY)));
    public static final RegistryObject<Block> RUNIC_STONE = registerBlock("runic_stone",
            () -> new Block(baseProps(MapColor.STONE)));
    public static final RegistryObject<Block> RUNIC_PILLAR = registerBlock("runic_pillar",
            () -> new Block(baseProps(MapColor.STONE)));

    public static final RegistryObject<Block> STARLIGHT_ORE = registerBlock("starlight_ore",
            () -> new Block(baseProps(MapColor.STONE).strength(3.0f, 6.0f)));
    public static final RegistryObject<Block> CRYSTAL_ORE = registerBlock("crystal_ore",
            () -> new Block(baseProps(MapColor.STONE).strength(3.0f, 6.0f)));
    public static final RegistryObject<Block> CRYSTAL_BLOCK = registerBlock("crystal_block",
            () -> new Block(baseProps(MapColor.COLOR_CYAN).lightLevel(state -> 3)));
    public static final RegistryObject<Block> STARLIGHT_BLOCK = registerBlock("starlight_block",
            () -> new Block(baseProps(MapColor.SNOW).lightLevel(state -> 10)));

    public static final RegistryObject<Block> LUMINESCENT_LOG = registerBlock("luminescent_log",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f)
                    .sound(SoundType.WOOD).lightLevel(state -> 8)));
    public static final RegistryObject<Block> LUMINESCENT_PLANKS = registerBlock("luminescent_planks",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f)
                    .sound(SoundType.WOOD).lightLevel(state -> 6)));
    public static final RegistryObject<Block> LUMINESCENT_LEAVES = registerBlock("luminescent_leaves",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(0.2f)
                    .sound(SoundType.GRASS).lightLevel(state -> 5).noOcclusion()));
    public static final RegistryObject<Block> GLOWING_MOSS = registerBlock("glowing_moss",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(0.2f)
                    .sound(SoundType.MOSS).lightLevel(state -> 6)));

    public static final RegistryObject<Block> ENCHANTED_GLASS = registerBlock("enchanted_glass",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(0.3f)
                    .sound(SoundType.GLASS).lightLevel(state -> 4).noOcclusion()));
    public static final RegistryObject<Block> ENCHANTED_SAND = registerBlock("enchanted_sand",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.5f).sound(SoundType.SAND)));
    public static final RegistryObject<Block> ESSENCE_INFUSED_SOIL = registerBlock("essence_infused_soil",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.5f)
                    .sound(SoundType.GRAVEL).lightLevel(state -> 3)));

    public static final RegistryObject<Block> ESSENCE_EXTRACTOR = registerBlock("essence_extractor",
            () -> new EssenceExtractorBlock(baseProps(MapColor.COLOR_PURPLE)));
    public static final RegistryObject<Block> CRYSTAL_INFUSER = registerBlock("crystal_infuser",
            () -> new CrystalInfuserBlock(baseProps(MapColor.COLOR_CYAN)));
    public static final RegistryObject<Block> ARCANE_CONDENSER = registerBlock("arcane_condenser",
            () -> new ArcaneCondenserBlock(baseProps(MapColor.COLOR_YELLOW)));
    public static final RegistryObject<Block> RUNIC_FORGE = registerBlock("runic_forge",
            () -> new RunicForgeBlock(baseProps(MapColor.COLOR_RED)));
    public static final RegistryObject<Block> STARLIGHT_REFINER = registerBlock("starlight_refiner",
            () -> new StarlightRefinerBlock(baseProps(MapColor.SNOW)));
    public static final RegistryObject<Block> RUBY_FURNACE = registerBlock("ruby_furnace",
            () -> new RubyFurnaceBlock(baseProps(MapColor.COLOR_RED)
                    .lightLevel(state -> state.getValue(RubyFurnaceBlock.LIT) ? 13 : 0)));

    // Blocs Chanceux : ne dropent jamais eux-memes, uniquement un item aleatoire (voir loot_table/blocks/lucky_block*.json).
    public static final RegistryObject<Block> LUCKY_BLOCK = registerBlock("lucky_block",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0.5f)
                    .sound(SoundType.WOOL).lightLevel(state -> 8)));
    public static final RegistryObject<Block> LUCKY_BLOCK_RED = registerBlock("lucky_block_red",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.5f)
                    .sound(SoundType.WOOL).lightLevel(state -> 8)));
    public static final RegistryObject<Block> LUCKY_BLOCK_YELLOW = registerBlock("lucky_block_yellow",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.5f)
                    .sound(SoundType.WOOL).lightLevel(state -> 8)));
    public static final RegistryObject<Block> LUCKY_BLOCK_BLUE = registerBlock("lucky_block_blue",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(0.5f)
                    .sound(SoundType.WOOL).lightLevel(state -> 8)));

    public static final RegistryObject<Block> JADE_ORE = registerBlock("jade_ore",
            () -> new Block(baseProps(MapColor.STONE).strength(3.0f, 6.0f)));
    public static final RegistryObject<Block> JADE_BLOCK = registerBlock("jade_block",
            () -> new Block(baseProps(MapColor.COLOR_GREEN)));
}
