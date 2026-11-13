package fr.insastronaute.webapi.sts.combat.event;

import java.util.UUID;

public sealed interface CombatEvent permits
        ArmorBroken,
        ArmorGained,
        CardAltered,
        CardDrawn,
        CardEnchanted,
        CardMerged,
        CardMoved,
        CardPlayed,
        CardSelectionResolved,
        CardTransformed,
        CombatEnded,
        DamageApplied,
        EnergyGained,
        EnergySpent,
        HealApplied,
        HpLost,
        PileShuffled,
        StatusApplied,
        StatusBlocked,
        StatusRemoved,
        TurnEnded,
        TurnStarted {

    UUID combatId();

    long revision();
}
