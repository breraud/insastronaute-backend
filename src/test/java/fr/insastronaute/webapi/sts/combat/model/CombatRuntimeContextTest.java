package fr.insastronaute.webapi.sts.combat.model;

import fr.insastronaute.webapi.sts.combat.ai.EnemyPolicy;
import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.engine.CombatEngine;
import fr.insastronaute.webapi.sts.combat.engine.CombatOrchestrator;
import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentEngine;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CombatRuntimeContextTest {

    @Test
    void storesAndExposesComponents() {
        UUID combatId = UUID.randomUUID();
        CardCatalog catalog = new CardCatalog("1.0.0", "IRONCLAD", Map.of());
        CombatEngine engine = new CombatEngine(catalog, new RelicEngine(List.of()), new EnchantmentEngine(List.of()));
        EnemyPolicy policy = state -> null;
        CombatOrchestrator orchestrator = new CombatOrchestrator(engine, policy);

        CombatRuntimeContext context = new CombatRuntimeContext(combatId, catalog, engine, orchestrator);

        assertThat(context.combatId()).isEqualTo(combatId);
        assertThat(context.catalog()).isSameAs(catalog);
        assertThat(context.engine()).isSameAs(engine);
        assertThat(context.orchestrator()).isSameAs(orchestrator);
    }

    @Test
    void rejectsNullFields() {
        UUID combatId = UUID.randomUUID();
        CardCatalog catalog = new CardCatalog("1.0.0", "IRONCLAD", Map.of());
        CombatEngine engine = new CombatEngine(catalog, new RelicEngine(List.of()), new EnchantmentEngine(List.of()));
        CombatOrchestrator orchestrator = new CombatOrchestrator(engine, state -> null);

        assertThatThrownBy(() -> new CombatRuntimeContext(null, catalog, engine, orchestrator))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CombatRuntimeContext(combatId, null, engine, orchestrator))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CombatRuntimeContext(combatId, catalog, null, orchestrator))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CombatRuntimeContext(combatId, catalog, engine, null))
                .isInstanceOf(NullPointerException.class);
    }
}
