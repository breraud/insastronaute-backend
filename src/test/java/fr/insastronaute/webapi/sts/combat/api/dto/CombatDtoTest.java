package fr.insastronaute.webapi.sts.combat.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class CombatDtoTest {
    @Test
    void testCombatMessageEnvelopeSerialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CombatMessageEnvelope env = new CombatMessageEnvelope(
                1, "combat-123", "43", "CARD_PLAYED", "action-123", Map.of("cardId", "Strike")
        );
        String json = mapper.writeValueAsString(env);
        assertThat(json).contains("\"protocolVersion\":1");
        assertThat(json).contains("\"revision\":\"43\"");
    }
}
