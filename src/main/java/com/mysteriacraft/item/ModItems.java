package com.mysteriacraft.item;

import com.mysteriacraft.MysteriaCraft;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
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

    public static final RegistryObject<Item> ARDENT_MERCURY_HELMET = ITEMS.register("ardent_mercury_helmet",
            () -> new ArmorItem(ModArmorMaterials.ARDENT_MERCURY, ArmorItem.Type.HELMET, new Item.Properties()));

    public static final RegistryObject<Item> ARDENT_MERCURY_CHESTPLATE = ITEMS.register("ardent_mercury_chestplate",
            () -> new ArmorItem(ModArmorMaterials.ARDENT_MERCURY, ArmorItem.Type.CHESTPLATE, new Item.Properties()));

    public static final RegistryObject<Item> ARDENT_MERCURY_LEGGINGS = ITEMS.register("ardent_mercury_leggings",
            () -> new ArmorItem(ModArmorMaterials.ARDENT_MERCURY, ArmorItem.Type.LEGGINGS, new Item.Properties()));

    public static final RegistryObject<Item> ARDENT_MERCURY_BOOTS = ITEMS.register("ardent_mercury_boots",
            () -> new ArmorItem(ModArmorMaterials.ARDENT_MERCURY, ArmorItem.Type.BOOTS, new Item.Properties()));

    public static final RegistryObject<Item> ARDENT_MERCURY_SWORD = ITEMS.register("ardent_mercury_sword",
            () -> new SwordItem(ModTiers.ARDENT_MERCURY, 3, -2.4F, new Item.Properties()));

    public static final RegistryObject<Item> RUBY = ITEMS.register("ruby",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> JADE = ITEMS.register("jade",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> JADE_AXE = ITEMS.register("jade_axe",
            () -> new JadeAxeItem(ModTiers.JADE, 6.0F, -3.0F, new Item.Properties()));

    public static final RegistryObject<Item> ARDENT_MERCURY_HOE = ITEMS.register("ardent_mercury_hoe",
            () -> new HoeItem(ModTiers.ARDENT_MERCURY, -2, -1.0F, new Item.Properties()));

    public static final RegistryObject<Item> ARDENT_MERCURY_CLEAVER_HOE = ITEMS.register("ardent_mercury_cleaver_hoe",
            () -> new HoeItem(ModTiers.ARDENT_MERCURY, -1, -1.5F, new Item.Properties()));

    public static final RegistryObject<Item> ARDENT_MERCURY_PICKAXE = ITEMS.register("ardent_mercury_pickaxe",
            () -> new PickaxeItem(ModTiers.ARDENT_MERCURY, 1, -2.8F, new Item.Properties()));

    public static final RegistryObject<Item> ARDENT_MERCURY_WAR_PICKAXE = ITEMS.register("ardent_mercury_war_pickaxe",
            () -> new PickaxeItem(ModTiers.ARDENT_MERCURY, 2, -3.0F, new Item.Properties()));

    public static final RegistryObject<Item> ARDENT_MERCURY_SHOVEL = ITEMS.register("ardent_mercury_shovel",
            () -> new ShovelItem(ModTiers.ARDENT_MERCURY, 1.5F, -3.0F, new Item.Properties()));
}
