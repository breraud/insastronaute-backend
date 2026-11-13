package fr.insastronaute.webapi.sts.combat.model;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.engine.CombatEngine;
import fr.insastronaute.webapi.sts.combat.engine.CombatOrchestrator;

import java.util.Objects;
import java.util.UUID;

/**
 * Encapsulates the immutable components required to resolve authoritative STS combat commands.
 * Reused throughout the lifetime of a single combat.
 */
public record CombatRuntimeContext(
        UUID combatId,
        CardCatalog catalog,
        CombatEngine engine,
        CombatOrchestrator orchestrator
) {
    public CombatRuntimeContext {
        Objects.requireNonNull(combatId, "combatId must not be null");
        Objects.requireNonNull(catalog, "catalog must not be null");
        Objects.requireNonNull(engine, "engine must not be null");
        Objects.requireNonNull(orchestrator, "orchestrator must not be null");
    }
}
