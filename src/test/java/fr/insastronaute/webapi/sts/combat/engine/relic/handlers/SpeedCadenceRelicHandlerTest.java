package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class SpeedCadenceRelicHandlerTest {

    @Test
    void testId() {
        SpeedCadenceRelicHandler handler = new SpeedCadenceRelicHandler();
        assertThat(handler.id()).isEqualTo("SpeedCadenceRelic");
    }
}
