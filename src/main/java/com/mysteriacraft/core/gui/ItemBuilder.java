package com.mysteriacraft.core.gui;

import com.mysteriacraft.core.config.MessageManager;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Petit constructeur fluide pour creer des ItemStack d'interface (icones de menu),
 * avec traduction automatique des couleurs (&).
 */
public class ItemBuilder {

    private final ItemStack itemStack;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this(material, 1);
    }

    public ItemBuilder(Material material, int amount) {
        this.itemStack = new ItemStack(material, amount);
        this.meta = itemStack.getItemMeta();
    }

    public ItemBuilder name(String name) {
        if (meta != null) {
            meta.setDisplayName(MessageManager.color(name));
        }
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        if (meta != null && lines != null) {
            List<String> colored = new ArrayList<>();
            for (String line : lines) {
                colored.add(MessageManager.color(line));
            }
            meta.setLore(colored);
        }
        return this;
    }

    public ItemBuilder hideAttributes() {
        if (meta != null) {
            for (ItemFlag flag : ItemFlag.values()) {
                meta.addItemFlags(flag);
            }
        }
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }
}
