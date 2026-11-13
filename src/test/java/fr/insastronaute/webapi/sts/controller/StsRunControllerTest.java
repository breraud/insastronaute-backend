package fr.insastronaute.webapi.sts.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.auth.UserSynchronizationService;
import fr.insastronaute.webapi.sts.dto.StsChooseEventOptionRequest;
import fr.insastronaute.webapi.sts.dto.StsChooseEventOptionResponse;
import fr.insastronaute.webapi.sts.dto.StsCurrentRunResponse;
import fr.insastronaute.webapi.sts.dto.StsRunStateDto;
import fr.insastronaute.webapi.sts.dto.StsStartRunRequest;
import fr.insastronaute.webapi.sts.dto.StsRetireRunResponse;
import fr.insastronaute.webapi.sts.domain.StsRunStatus;
import fr.insastronaute.webapi.sts.service.StsRunService;
import fr.insastronaute.webapi.user.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StsRunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserSynchronizationService synchronizationService;

    @MockitoBean
    private StsRunService stsRunService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    private AppUser user;

    @BeforeEach
    void setUp() {
        user = new AppUser();
        user.setKeycloakSub("sub-1");
        user.setEmail("player@example.com");
        when(synchronizationService.syncWithKeycloak(any())).thenReturn(user);
    }

    @Test
    void authenticatedUserCanStartRunOnApiPrefix() throws Exception {
        UUID runId = UUID.randomUUID();
        when(stsRunService.startRun(eq(user), any(StsStartRunRequest.class))).thenReturn(new StsRunStateDto(
                runId,
                "Active",
                "2026-07-09.1",
                "MECA",
                0,
                0,
                0,
                null,
                apiJson("{\"maxHp\":70,\"currentHp\":70}"),
                apiJson("{\"gold\":0,\"deck\":[],\"relics\":[]}"),
                apiJson("{\"nodes\":[]}"),
                apiJson("[]"),
                null,
                null,
                null
        ));

        mockMvc.perform(post("/api/sts/runs")
                        .contentType("application/json")
                        .content("{\"character\":\"MECA\",\"clientVersion\":\"1.0.0\"}")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId.toString()))
                .andExpect(jsonPath("$.selectedCharacter").value("MECA"))
                .andExpect(jsonPath("$.player.maxHp").value(70));

        verify(stsRunService).startRun(eq(user), any(StsStartRunRequest.class));
    }

    @Test
    void runByIdRouteIsNotExposed() throws Exception {
        UUID runId = UUID.randomUUID();

        mockMvc.perform(get("/api/sts/runs/{runId}", runId).with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void stsRoutesWithoutApiPrefixAreNotMapped() throws Exception {
        mockMvc.perform(get("/sts/runs/current").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isNotFound());

        verify(stsRunService, never()).currentRun(any());
    }

    @Test
    void currentRunSerializesJsonStateAsPayload() throws Exception {
        UUID runId = UUID.randomUUID();
        when(stsRunService.currentRun(user)).thenReturn(new StsCurrentRunResponse(true, new StsRunStateDto(
                runId,
                "Active",
                "2026-07-09.1",
                "ITI",
                0,
                0,
                0,
                null,
                apiJson("{\"maxHp\":70,\"currentHp\":70}"),
                apiJson("{\"gold\":0,\"deck\":[],\"relics\":[]}"),
                apiJson("{\"nodes\":[]}"),
                apiJson("[]"),
                apiJson("{\"encounterInstanceId\":\"encinst_1\"}"),
                null,
                apiJson("{\"id\":\"event_a\"}")
        )));

        mockMvc.perform(get("/api/sts/runs/current").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasRun").value(true))
                .andExpect(jsonPath("$.run.player.maxHp").value(70))
                .andExpect(jsonPath("$.run.runInventory.gold").value(0))
                .andExpect(jsonPath("$.run.pendingRewards").isArray())
                .andExpect(jsonPath("$.run.activeEncounter.encounterInstanceId").value("encinst_1"))
                .andExpect(jsonPath("$.run.activeEvent.id").value("event_a"))
                .andExpect(jsonPath("$.run.player.nodeType").doesNotExist());
    }

    @Test
    void startRunRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/sts/runs")
                        .contentType("application/json")
                        .content("{\"character\":\"MECA\"}")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedUserCanResetRun() throws Exception {
        UUID runId = UUID.randomUUID();

        mockMvc.perform(post("/api/sts/runs/{runId}/reset", runId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(stsRunService).resetRun(user, runId);
    }

    @Test
    void authenticatedUserCanRetireRunAfterAnAct() throws Exception {
        UUID runId = UUID.randomUUID();
        when(stsRunService.retireRun(user, runId)).thenReturn(new StsRetireRunResponse(
                true,
                runId,
                StsRunStatus.Retired,
                665,
                33,
                40,
                75,
                25,
                60,
                20,
                240,
                80,
                15,
                50,
                100,
                20,
                "NEAREST",
                10,
                List.of()
        ));

        mockMvc.perform(post("/api/sts/runs/{runId}/retire", runId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.status").value("Retired"))
                .andExpect(jsonPath("$.score").value(665))
                .andExpect(jsonPath("$.tokensGranted").value(33))
                .andExpect(jsonPath("$.tokenBalance").value(40))
                .andExpect(jsonPath("$.visitedNodeScore").value(75))
                .andExpect(jsonPath("$.remainingHpPercentScore").value(100))
                .andExpect(jsonPath("$.scorePerToken").value(20))
                .andExpect(jsonPath("$.rounding").value("NEAREST"))
                .andExpect(jsonPath("$.minimumReward").value(10));

        verify(stsRunService).retireRun(user, runId);
    }

    @Test
    void retireRunRequiresCsrfToken() throws Exception {
        UUID runId = UUID.randomUUID();

        mockMvc.perform(post("/api/sts/runs/{runId}/retire", runId)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());

        verify(stsRunService, never()).retireRun(any(), any());
    }

    @Test
    void authenticatedUserCanChooseEventOption() throws Exception {
        UUID runId = UUID.randomUUID();
        when(stsRunService.chooseEventOption(eq(user), eq(runId), eq("evtinst_1"), any(StsChooseEventOptionRequest.class)))
                .thenReturn(new StsChooseEventOptionResponse(
                        true,
                        "evtinst_1",
                        true,
                        apiJson("{\"maxHp\":75,\"currentHp\":70}"),
                        apiJson("{\"goldDelta\":12}"),
                        apiJson("{\"tokenDelta\":0}"),
                        apiJson("[]"),
                        apiJson("{\"currentNodeId\":1,\"enteredNodeId\":null}"),
                        "Done",
                        null
                ));

        mockMvc.perform(post("/api/sts/runs/{runId}/events/{eventInstanceId}/choose", runId, "evtinst_1")
                        .contentType("application/json")
                        .content("{\"optionId\":\"option_0\",\"selectedCardInstanceIds\":[\"cardinst_1\"]}")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.eventInstanceId").value("evtinst_1"))
                .andExpect(jsonPath("$.eventCompleted").value(true))
                .andExpect(jsonPath("$.player.maxHp").value(75))
                .andExpect(jsonPath("$.runInventoryPatch.goldDelta").value(12))
                .andExpect(jsonPath("$.completionMessage").value("Done"));

        verify(stsRunService).chooseEventOption(eq(user), eq(runId), eq("evtinst_1"), any(StsChooseEventOptionRequest.class));
    }

    private Object apiJson(String json) throws Exception {
        return objectMapper.convertValue(objectMapper.readTree(json), Object.class);
    }
}
