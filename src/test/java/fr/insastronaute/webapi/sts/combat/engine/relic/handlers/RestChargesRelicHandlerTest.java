package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class RestChargesRelicHandlerTest {

    @Test
    void testId() {
        RestChargesRelicHandler handler = new RestChargesRelicHandler();
        assertThat(handler.id()).isEqualTo("RestChargesRelic");
    }
}
