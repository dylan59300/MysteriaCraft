package com.mysteriacraft.core.config;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Map;

/**
 * Recupere les messages depuis messages.yml, applique les couleurs (&) et
 * remplace les variables {cle} par leur valeur.
 */
public class MessageManager {

    private final ConfigManager messagesConfig;
    private final ConfigManager mainConfig;

    public MessageManager(ConfigManager messagesConfig, ConfigManager mainConfig) {
        this.messagesConfig = messagesConfig;
        this.mainConfig = mainConfig;
    }

    public String getPrefix() {
        return color(mainConfig.get().getString("prefix", ""));
    }

    public String raw(String path) {
        String message = messagesConfig.get().getString(path);
        if (message == null) {
            return ChatColor.RED + "Message manquant : " + path;
        }
        return color(message);
    }

    public String get(String path) {
        return getPrefix() + raw(path);
    }

    public String get(String path, Map<String, String> placeholders) {
        String message = raw(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return getPrefix() + message;
    }

    public void send(CommandSender sender, String path) {
        sender.sendMessage(get(path));
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(get(path, placeholders));
    }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
