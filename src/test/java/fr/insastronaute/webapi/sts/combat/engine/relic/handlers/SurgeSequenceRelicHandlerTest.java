package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class SurgeSequenceRelicHandlerTest {

    @Test
    void testId() {
        SurgeSequenceRelicHandler handler = new SurgeSequenceRelicHandler();
        assertThat(handler.id()).isEqualTo("SurgeSequenceRelic");
    }
}
