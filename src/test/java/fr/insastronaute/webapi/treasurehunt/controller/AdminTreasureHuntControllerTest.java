package fr.insastronaute.webapi.treasurehunt.controller;

import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntInputTool;
import fr.insastronaute.webapi.treasurehunt.dto.AdminCheckpointResponse;
import fr.insastronaute.webapi.treasurehunt.dto.AdminTreasureHuntContentResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntActivationStatusResponse;
import fr.insastronaute.webapi.treasurehunt.service.TreasureHuntAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminTreasureHuntControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean private TreasureHuntAdminService service;

    @Test
    void letsAdminsCreateACheckpointWithoutEmbeddingAReward() throws Exception {
        UUID pathId = UUID.randomUUID();
        when(service.createCheckpoint(any(), any())).thenReturn(new AdminCheckpointResponse(UUID.randomUUID(), pathId, 1, "INFO-001", TreasureHuntInputTool.KEYPAD));

        mockMvc.perform(post("/api/admin/treasure-hunt/paths/{pathId}/checkpoints", pathId)
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf().asHeader())
                        .contentType("application/json").content("{\"code\":\"INFO-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stepNumber").value(1))
                .andExpect(jsonPath("$.code").value("INFO-001"))
                // L'outil sort avec l'etape : sans lui, le panneau ne peut pas rouvrir en
                // edition celle qu'il vient de creer sans la ramener au clavier.
                .andExpect(jsonPath("$.inputTool").value("KEYPAD"));
    }

    @Test
    void letsAdminsListCompositeCheckpointContents() throws Exception {
        UUID checkpointId = UUID.randomUUID();
        when(service.listCheckpointContents(checkpointId)).thenReturn(List.of(
                new AdminTreasureHuntContentResponse(UUID.randomUUID(), 1,
                        fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntContentType.RICH_TEXT, "Indice", null, false)));

        mockMvc.perform(get("/api/admin/treasure-hunt/checkpoints/{checkpointId}/contents", checkpointId)
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("RICH_TEXT"));
    }

    @Test
    void letsAdminsActivateAndDeactivateAValidHunt() throws Exception {
        when(service.activationStatus()).thenReturn(new TreasureHuntActivationStatusResponse(false, true, List.of("Six voies requises.")));
        when(service.activate()).thenReturn(new TreasureHuntActivationStatusResponse(true, true, List.of()));
        when(service.deactivate()).thenReturn(new TreasureHuntActivationStatusResponse(false, true, List.of()));

        mockMvc.perform(get("/api/admin/treasure-hunt/status").with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(post("/api/admin/treasure-hunt/activate").with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")).with(SecurityMockMvcRequestPostProcessors.csrf().asHeader()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(post("/api/admin/treasure-hunt/deactivate").with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")).with(SecurityMockMvcRequestPostProcessors.csrf().asHeader()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    }
}
