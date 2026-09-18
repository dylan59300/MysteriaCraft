package com.mysteriacraft.menu;

import com.mysteriacraft.MysteriaCraft;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MysteriaCraft.MOD_ID);

    public static final RegistryObject<MenuType<MachineMenu>> ESSENCE_EXTRACTOR_MENU =
            MENU_TYPES.register("essence_extractor_menu", () -> IForgeMenuType.create(
                    (windowId, inv, extraData) -> new MachineMenu(ESSENCE_EXTRACTOR_MENU.get(), windowId, inv, extraData)));

    public static final RegistryObject<MenuType<MachineMenu>> CRYSTAL_INFUSER_MENU =
            MENU_TYPES.register("crystal_infuser_menu", () -> IForgeMenuType.create(
                    (windowId, inv, extraData) -> new MachineMenu(CRYSTAL_INFUSER_MENU.get(), windowId, inv, extraData)));

    public static final RegistryObject<MenuType<MachineMenu>> ARCANE_CONDENSER_MENU =
            MENU_TYPES.register("arcane_condenser_menu", () -> IForgeMenuType.create(
                    (windowId, inv, extraData) -> new MachineMenu(ARCANE_CONDENSER_MENU.get(), windowId, inv, extraData)));

    public static final RegistryObject<MenuType<MachineMenu>> RUNIC_FORGE_MENU =
            MENU_TYPES.register("runic_forge_menu", () -> IForgeMenuType.create(
                    (windowId, inv, extraData) -> new MachineMenu(RUNIC_FORGE_MENU.get(), windowId, inv, extraData)));

    public static final RegistryObject<MenuType<MachineMenu>> STARLIGHT_REFINER_MENU =
            MENU_TYPES.register("starlight_refiner_menu", () -> IForgeMenuType.create(
                    (windowId, inv, extraData) -> new MachineMenu(STARLIGHT_REFINER_MENU.get(), windowId, inv, extraData)));
}
