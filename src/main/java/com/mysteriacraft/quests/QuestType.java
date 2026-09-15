package com.mysteriacraft.quests;

/**
 * Type d'action Bukkit suivi par une quete. "cible" dans quests.yml precise le materiau
 * (BREAK_BLOCK/PLACE_BLOCK) ou le type d'entite (KILL_MOB) ; ignore pour FISH.
 * Pour MACHINE_TRANSFORM, "cible" est l'id de la famille de Lucky Block obtenue (absent = toute transformation reussie compte).
 * Pour ISLAND_BLOCK_PLACED, "cible" est optionnel : absent = tout materiau compte, present = seul
 * ce materiau precis compte (module Iles).
 * Pour ISLAND_MEMBER_JOINED, "cible" est ignore (compte chaque NOUVEAU membre invite rejoignant
 * SA PROPRE ile, module Iles).
 * Pour ISLAND_VISITED, "cible" est ignore (compte chaque ile DIFFERENTE de la derniere visitee
 * via /ile visit, module Iles).
 * Pour EXPLORE_BIOME, "cible" est optionnel : absent = tout NOUVEAU biome visite cette semaine
 * compte, present = seul ce biome precis compte (verifie periodiquement, voir QuestService).
 * Pour COLLECT_DISTINCT_CUSTOM_ITEMS, "cible" est ignore : la progression est le nombre
 * d'objets custom DIFFERENTS possedes SIMULTANEMENT dans l'inventaire (verifie periodiquement).
 */
public enum QuestType {
    BREAK_BLOCK,
    PLACE_BLOCK,
    KILL_MOB,
    FISH,
    CRAFT_ITEM,
    CONSUME_ITEM,
    MACHINE_TRANSFORM,
    ISLAND_BLOCK_PLACED,
    ISLAND_MEMBER_JOINED,
    ISLAND_VISITED,
    EXPLORE_BIOME,
    COLLECT_DISTINCT_CUSTOM_ITEMS;

    public static QuestType fromString(String value) {
        try {
            return QuestType.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            return BREAK_BLOCK;
        }
    }
}
