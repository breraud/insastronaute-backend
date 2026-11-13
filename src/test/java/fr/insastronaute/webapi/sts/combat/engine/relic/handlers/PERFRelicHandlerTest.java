package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class PERFRelicHandlerTest {

    @Test
    void testId() {
        PERFRelicHandler handler = new PERFRelicHandler();
        assertThat(handler.id()).isNotNull();
    }
}
