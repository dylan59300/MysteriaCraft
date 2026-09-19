package com.mysteriacraft.block.entity;

import com.mysteriacraft.MysteriaCraft;
import com.mysteriacraft.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MysteriaCraft.MOD_ID);

    public static final RegistryObject<BlockEntityType<EssenceExtractorBlockEntity>> ESSENCE_EXTRACTOR =
            BLOCK_ENTITIES.register("essence_extractor", () -> BlockEntityType.Builder.of(
                    EssenceExtractorBlockEntity::new, ModBlocks.ESSENCE_EXTRACTOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<CrystalInfuserBlockEntity>> CRYSTAL_INFUSER =
            BLOCK_ENTITIES.register("crystal_infuser", () -> BlockEntityType.Builder.of(
                    CrystalInfuserBlockEntity::new, ModBlocks.CRYSTAL_INFUSER.get()).build(null));

    public static final RegistryObject<BlockEntityType<ArcaneCondenserBlockEntity>> ARCANE_CONDENSER =
            BLOCK_ENTITIES.register("arcane_condenser", () -> BlockEntityType.Builder.of(
                    ArcaneCondenserBlockEntity::new, ModBlocks.ARCANE_CONDENSER.get()).build(null));

    public static final RegistryObject<BlockEntityType<RunicForgeBlockEntity>> RUNIC_FORGE =
            BLOCK_ENTITIES.register("runic_forge", () -> BlockEntityType.Builder.of(
                    RunicForgeBlockEntity::new, ModBlocks.RUNIC_FORGE.get()).build(null));

    public static final RegistryObject<BlockEntityType<StarlightRefinerBlockEntity>> STARLIGHT_REFINER =
            BLOCK_ENTITIES.register("starlight_refiner", () -> BlockEntityType.Builder.of(
                    StarlightRefinerBlockEntity::new, ModBlocks.STARLIGHT_REFINER.get()).build(null));

    public static final RegistryObject<BlockEntityType<RubyFurnaceBlockEntity>> RUBY_FURNACE =
            BLOCK_ENTITIES.register("ruby_furnace", () -> BlockEntityType.Builder.of(
                    RubyFurnaceBlockEntity::new, ModBlocks.RUBY_FURNACE.get()).build(null));
}
