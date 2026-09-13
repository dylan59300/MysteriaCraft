package com.mysteriacraft.quests;

/**
 * Type d'action Bukkit suivi par une quete. "cible" dans quests.yml precise le materiau
 * (BREAK_BLOCK/PLACE_BLOCK) ou le type d'entite (KILL_MOB) ; ignore pour FISH.
 */
public enum QuestType {
    BREAK_BLOCK,
    PLACE_BLOCK,
    KILL_MOB,
    FISH;

    public static QuestType fromString(String value) {
        try {
            return QuestType.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            return BREAK_BLOCK;
        }
    }
}
