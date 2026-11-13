package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ShatterPulseRelicHandlerTest {

    @Test
    void testId() {
        ShatterPulseRelicHandler handler = new ShatterPulseRelicHandler();
        assertThat(handler.id()).isEqualTo("ShatterPulseRelic");
    }
}
