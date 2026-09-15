package com.mysteriacraft.encheres;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Serialise/deserialise un ItemStack COMPLET (meta, enchantements, PersistentDataContainer...)
 * en base64, pour le stocker tel quel dans une colonne SQLite (voir EnchereManager).
 */
public final class ItemSerializer {

    private ItemSerializer() {
    }

    public static String serialize(ItemStack item) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(byteStream)) {
            dataOutput.writeObject(item);
            return Base64.getEncoder().encodeToString(byteStream.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Erreur serialisation ItemStack pour l'hotel des ventes", e);
        }
    }

    public static ItemStack deserialize(String data) {
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(byteStream)) {
            return (ItemStack) dataInput.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Erreur deserialisation ItemStack de l'hotel des ventes", e);
        }
    }
}
