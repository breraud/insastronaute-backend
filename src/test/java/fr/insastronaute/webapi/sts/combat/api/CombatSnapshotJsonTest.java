package fr.insastronaute.webapi.sts.combat.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.auth.UserSynchronizationService;
import fr.insastronaute.webapi.sts.service.StsRunService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The combat snapshot as it actually reaches a client, over HTTP.
 *
 * <p>The state crosses a Jackson boundary on its way out, and a tree that is handed over without
 * being converted serializes as a description of itself: {@code {"array":false,"object":true,...}}
 * instead of the combat. That is invisible to a test asserting on the DTO, and fatal to a client,
 * so this one reads the response body.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CombatSnapshotJsonTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StsRunService runService;

    @MockitoBean
    private UserSynchronizationService synchronizationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void servesTheCombatItselfRatherThanADescriptionOfTheTreeHoldingIt() throws Exception {
        UUID runId = UUID.randomUUID();
        when(runService.authoritativeCombatSnapshot(anyString(), any())).thenReturn(
                objectMapper.readTree("""
                        {"combatId":"engine-combat","revision":42,"status":"ACTIVE",
                         "combatants":[{"combatantId":"hero","hp":30,
                           "piles":{"hand":[{"instanceId":"card-1","definitionId":"frappe"}]}}]}
                        """));

        mockMvc.perform(get("/api/sts/combats/{runId}", runId).with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.protocolVersion").value(1))
                .andExpect(jsonPath("$.revision").value("42"))
                .andExpect(jsonPath("$.mode").value("PVE"))
                .andExpect(jsonPath("$.payload.combatId").value("engine-combat"))
                .andExpect(jsonPath("$.payload.combatants[0].combatantId").value("hero"))
                .andExpect(jsonPath("$.payload.combatants[0].piles.hand[0].definitionId")
                        .value("frappe"))
                .andExpect(jsonPath("$.payload.array").doesNotExist())
                .andExpect(jsonPath("$.payload.object").doesNotExist())
                .andExpect(jsonPath("$.payload.nodeType").doesNotExist());
    }
}
