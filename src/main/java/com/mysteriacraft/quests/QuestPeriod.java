package com.mysteriacraft.quests;

/**
 * Periode de reset d'une quete. Le reset est implicite : la progression est stockee avec une
 * cle de periode (date du jour / semaine ISO) ; des que la cle change, une nouvelle ligne
 * vierge est utilisee automatiquement, sans tache de reset a programmer.
 */
public enum QuestPeriod {
    DAILY,
    WEEKLY
}
