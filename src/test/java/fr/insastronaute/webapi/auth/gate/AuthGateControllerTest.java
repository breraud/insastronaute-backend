package fr.insastronaute.webapi.auth.gate;

import fr.insastronaute.webapi.auth.gate.dto.GateEnterResponse;
import fr.insastronaute.webapi.auth.gate.dto.GateStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthGateControllerTest {

    private final AuthGateService authGateService = mock(AuthGateService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthGateController controller = new AuthGateController(authGateService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void enterEndpointReturnsAdmittedStatus() throws Exception {
        when(authGateService.enter(any(), any())).thenReturn(
                new GateEnterResponse(GateStatus.ADMITTED, "t-1", "token-xyz", "/oauth2/auth", 0, 0L, 0L)
        );

        mockMvc.perform(post("/api/auth/gate/enter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"redirectUri\":\"/evenements\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ADMITTED"))
                .andExpect(jsonPath("$.gateToken").value("token-xyz"));
    }

    @Test
    void statusEndpointReturnsQueuedStatus() throws Exception {
        when(authGateService.getStatus(any(), any())).thenReturn(
                new GateStatusResponse(GateStatus.QUEUED, "t-1", null, null, 3, 10L, 2L)
        );

        mockMvc.perform(get("/api/auth/gate/status")
                        .param("ticketId", "t-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.position").value(3));
    }
}
