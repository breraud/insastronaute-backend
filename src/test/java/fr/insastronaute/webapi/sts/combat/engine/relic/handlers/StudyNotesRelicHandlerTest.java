package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class StudyNotesRelicHandlerTest {

    @Test
    void testId() {
        StudyNotesRelicHandler handler = new StudyNotesRelicHandler();
        assertThat(handler.id()).isEqualTo("StudyNotesRelic");
    }
}
