package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class FieldRationsRelicHandlerTest {

    @Test
    void testId() {
        FieldRationsRelicHandler handler = new FieldRationsRelicHandler();
        assertThat(handler.id()).isEqualTo("FieldRationsRelic");
    }
}
