package com.mysteriacraft.quests;

/**
 * Type d'action Bukkit suivi par une quete. "cible" dans quests.yml precise le materiau
 * (BREAK_BLOCK/PLACE_BLOCK) ou le type d'entite (KILL_MOB) ; ignore pour FISH.
 * Pour MACHINE_TRANSFORM, "cible" est l'id de la famille de Lucky Block obtenue (absent = toute transformation reussie compte).
 */
public enum QuestType {
    BREAK_BLOCK,
    PLACE_BLOCK,
    KILL_MOB,
    FISH,
    CRAFT_ITEM,
    CONSUME_ITEM,
    MACHINE_TRANSFORM;

    public static QuestType fromString(String value) {
        try {
            return QuestType.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            return BREAK_BLOCK;
        }
    }
}
