package com.mysteriacraft.voucher;

import com.mysteriacraft.core.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Charge/modifie voucher.yml : CRUD complet des types de Voucher (voir VoucherDefinition). */
public class VoucherManager {

    private final ConfigManager voucherConfig;
    private final Map<String, VoucherDefinition> vouchers = new LinkedHashMap<>();

    public VoucherManager(ConfigManager voucherConfig) {
        this.voucherConfig = voucherConfig;
        load();
    }

    public void load() {
        vouchers.clear();
        ConfigurationSection root = voucherConfig.get().getConfigurationSection("vouchers");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            String nom = section.getString("nom", id);
            Material materiau = Material.matchMaterial(section.getString("materiau", "PAPER"));
            if (materiau == null) {
                materiau = Material.PAPER;
            }
            List<String> lore = new ArrayList<>(section.getStringList("lore"));
            String commande = section.getString("commande", "");
            vouchers.put(id.toLowerCase(), new VoucherDefinition(id.toLowerCase(), nom, materiau, lore, commande));
        }
    }

    public VoucherDefinition getVoucher(String id) {
        return id == null ? null : vouchers.get(id.toLowerCase());
    }

    public boolean exists(String id) {
        return getVoucher(id) != null;
    }

    public List<VoucherDefinition> getVouchersSorted() {
        List<VoucherDefinition> liste = new ArrayList<>(vouchers.values());
        liste.sort(Comparator.comparing(VoucherDefinition::nom, String.CASE_INSENSITIVE_ORDER));
        return liste;
    }

    private void editSection(String id, Consumer<ConfigurationSection> editor) {
        ConfigurationSection root = voucherConfig.get().getConfigurationSection("vouchers");
        if (root == null) {
            root = voucherConfig.get().createSection("vouchers");
        }
        ConfigurationSection section = root.getConfigurationSection(id);
        if (section == null) {
            section = root.createSection(id);
        }
        editor.accept(section);
        voucherConfig.save();
        load();
    }

    public synchronized void addVoucher(String id, String nom, Material materiau) {
        editSection(id.toLowerCase(), section -> {
            section.set("nom", nom);
            section.set("materiau", materiau.name());
            section.set("lore", List.of());
            section.set("commande", "");
        });
    }

    public synchronized void removeVoucher(String id) {
        ConfigurationSection root = voucherConfig.get().getConfigurationSection("vouchers");
        if (root != null) {
            root.set(id.toLowerCase(), null);
            voucherConfig.save();
            load();
        }
    }

    public synchronized void setVoucherNom(String id, String nom) {
        editSection(id.toLowerCase(), section -> section.set("nom", nom));
    }

    public synchronized void setVoucherMateriau(String id, Material materiau) {
        editSection(id.toLowerCase(), section -> section.set("materiau", materiau.name()));
    }

    public synchronized void setVoucherCommande(String id, String commande) {
        editSection(id.toLowerCase(), section -> section.set("commande", commande));
    }

    public synchronized void setVoucherLore(String id, List<String> lore) {
        editSection(id.toLowerCase(), section -> section.set("lore", lore));
    }
}
