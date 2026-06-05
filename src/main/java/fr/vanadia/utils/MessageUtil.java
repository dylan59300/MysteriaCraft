package fr.vanadia.utils;

import fr.vanadia.Vanadia;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Map;

public class MessageUtil {

    private final Vanadia plugin;
    private FileConfiguration messagesConfig;
    private String prefix;

    public MessageUtil(Vanadia plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    private void loadMessages() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(file);
        prefix = messagesConfig.getString("prefix", "&6[Vanadia] &r");
    }

    public void reload() {
        loadMessages();
    }

    public String getRaw(String key) {
        return messagesConfig.getString(key, "&cMessage manquant: " + key);
    }

    public String getRaw(String key, Map<String, String> placeholders) {
        String message = getRaw(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }

    public Component format(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    public void send(Player player, String key) {
        player.sendMessage(format(prefix + getRaw(key)));
    }

    public void send(Player player, String key, Map<String, String> placeholders) {
        player.sendMessage(format(prefix + getRaw(key, placeholders)));
    }

    public void sendRaw(Player player, String text) {
        player.sendMessage(format(text));
    }
}
