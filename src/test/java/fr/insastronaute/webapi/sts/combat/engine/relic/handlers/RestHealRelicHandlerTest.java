package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class RestHealRelicHandlerTest {

    @Test
    void testId() {
        RestHealRelicHandler handler = new RestHealRelicHandler();
        assertThat(handler.id()).isEqualTo("RestHealRelic");
    }
}
