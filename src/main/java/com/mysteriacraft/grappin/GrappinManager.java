package com.mysteriacraft.grappin;

import com.mysteriacraft.core.config.ConfigManager;

/** Charge la configuration du Grappin (cooldown, portee, force de traction) depuis grappin.yml. */
public class GrappinManager {

    private final ConfigManager grappinConfig;

    public GrappinManager(ConfigManager grappinConfig) {
        this.grappinConfig = grappinConfig;
    }

    public String getItemId() {
        return grappinConfig.get().getString("item-id", "grappin");
    }

    public long getCooldownSeconds() {
        return grappinConfig.get().getLong("cooldown-secondes", 5);
    }

    public double getDistanceMax() {
        return grappinConfig.get().getDouble("distance-max", 25);
    }

    public double getForceTraction() {
        return grappinConfig.get().getDouble("force-traction", 2.2);
    }
}
