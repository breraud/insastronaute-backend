package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class GCRelicHandlerTest {

    @Test
    void testId() {
        GCRelicHandler handler = new GCRelicHandler();
        assertThat(handler.id()).isNotNull();
    }
}
