package com.mysteriacraft.resourcepack;

import com.mysteriacraft.core.config.ConfigManager;

/** Lit resourcepack.yml (url du pack, envoi automatique, obligatoire) et sait renvoyer/recharger. */
public class ResourcePackManager {

    private final ConfigManager config;

    public ResourcePackManager(ConfigManager config) {
        this.config = config;
    }

    public String getUrl() {
        return config.get().getString("url", "");
    }

    public boolean isEnvoiAutomatique() {
        return config.get().getBoolean("envoi-automatique", true);
    }

    public boolean isObligatoire() {
        return config.get().getBoolean("obligatoire", false);
    }

    public boolean isConfigure() {
        return getUrl() != null && !getUrl().isBlank();
    }

    public void reload() {
        config.reload();
    }
}
