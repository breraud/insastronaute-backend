package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class LedgerRelicTest {

    @Test
    void testId() {
        LedgerRelic handler = new LedgerRelic();
        assertThat(handler.id()).isEqualTo("LedgerRelic");
    }
}
