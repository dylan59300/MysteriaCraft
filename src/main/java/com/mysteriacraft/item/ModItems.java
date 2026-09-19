package com.mysteriacraft.item;

import com.mysteriacraft.MysteriaCraft;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MysteriaCraft.MOD_ID);

    public static final RegistryObject<Item> MAGIC_ESSENCE = ITEMS.register("magic_essence",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> MYSTIC_CRYSTAL = ITEMS.register("mystic_crystal",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> MYSTERIA_WAND = ITEMS.register("mysteria_wand",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> STARLIGHT_SHARD = ITEMS.register("starlight_shard",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> INFUSED_CRYSTAL = ITEMS.register("infused_crystal",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> ARCANE_DUST = ITEMS.register("arcane_dust",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> RUNIC_INGOT = ITEMS.register("runic_ingot",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> STARLIGHT_INGOT = ITEMS.register("starlight_ingot",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> INFERNAL_HELMET = ITEMS.register("infernal_helmet",
            () -> new ArmorItem(ModArmorMaterials.INFERNAL, ArmorItem.Type.HELMET, new Item.Properties()));

    public static final RegistryObject<Item> INFERNAL_CHESTPLATE = ITEMS.register("infernal_chestplate",
            () -> new ArmorItem(ModArmorMaterials.INFERNAL, ArmorItem.Type.CHESTPLATE, new Item.Properties()));

    public static final RegistryObject<Item> INFERNAL_LEGGINGS = ITEMS.register("infernal_leggings",
            () -> new ArmorItem(ModArmorMaterials.INFERNAL, ArmorItem.Type.LEGGINGS, new Item.Properties()));

    public static final RegistryObject<Item> INFERNAL_BOOTS = ITEMS.register("infernal_boots",
            () -> new ArmorItem(ModArmorMaterials.INFERNAL, ArmorItem.Type.BOOTS, new Item.Properties()));

    public static final RegistryObject<Item> INFERNAL_SWORD = ITEMS.register("infernal_sword",
            () -> new SwordItem(ModTiers.INFERNAL, 3, -2.4F, new Item.Properties()));
}
