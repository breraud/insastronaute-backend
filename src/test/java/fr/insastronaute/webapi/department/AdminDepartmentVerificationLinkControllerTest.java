package fr.insastronaute.webapi.department;

import fr.insastronaute.webapi.department.dto.DepartmentVerificationLinkDto;
import fr.insastronaute.webapi.department.service.DepartmentVerificationLinkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDepartmentVerificationLinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DepartmentVerificationLinkService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void adminCanGetVerificationLinks() throws Exception {
        UUID itiId = UUID.randomUUID();
        UUID gmId = UUID.randomUUID();

        when(service.findAll()).thenReturn(List.of(new DepartmentVerificationLinkDto(
                itiId,
                "ITI",
                "Informatique",
                gmId,
                "GM",
                "Génie Mathématique"
        )));

        mockMvc.perform(get("/api/admin/department-verification-links")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].verifyingDepartmentId").value(itiId.toString()))
                .andExpect(jsonPath("$[0].verifyingDepartmentShortName").value("ITI"))
                .andExpect(jsonPath("$[0].verifiedDepartmentId").value(gmId.toString()))
                .andExpect(jsonPath("$[0].verifiedDepartmentShortName").value("GM"));
    }

    @Test
    void challengeManagerCannotGetVerificationLinks() throws Exception {
        mockMvc.perform(get("/api/admin/department-verification-links")
                        .with(user("challenge-manager").roles("RESPOS_DEFIS")))
                .andExpect(status().isForbidden());

        verify(service, never()).findAll();
    }

    @Test
    void assistantChallengeManagerCannotGetVerificationLinks() throws Exception {
        mockMvc.perform(get("/api/admin/department-verification-links")
                        .with(user("assistant-challenge-manager").roles("SOUS_RESPO_DEFIS")))
                .andExpect(status().isForbidden());

        verify(service, never()).findAll();
    }

    @Test
    void studentCannotGetVerificationLinks() throws Exception {
        mockMvc.perform(get("/api/admin/department-verification-links")
                        .with(user("student").roles("STUDENT")))
                .andExpect(status().isForbidden());

        verify(service, never()).findAll();
    }

    @Test
    void adminCanReplaceVerificationLinks() throws Exception {
        UUID itiId = UUID.randomUUID();
        UUID gmId = UUID.randomUUID();

        when(service.replaceAll(any())).thenReturn(List.of(new DepartmentVerificationLinkDto(
                itiId,
                "ITI",
                "Informatique",
                gmId,
                "GM",
                "Génie Mathématique"
        )));

        mockMvc.perform(put("/api/admin/department-verification-links")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "links": [
                                    {
                                      "verifyingDepartmentId": "%s",
                                      "verifiedDepartmentId": "%s"
                                    },
                                    {
                                      "verifyingDepartmentId": "%s",
                                      "verifiedDepartmentId": "%s"
                                    }
                                  ]
                                }
                                """.formatted(itiId, gmId, gmId, itiId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].verifyingDepartmentShortName").value("ITI"))
                .andExpect(jsonPath("$[0].verifiedDepartmentShortName").value("GM"));

        verify(service).replaceAll(any());
    }

    @Test
    void challengeManagerCannotReplaceVerificationLinks() throws Exception {
        mockMvc.perform(put("/api/admin/department-verification-links")
                        .with(user("challenge-manager").roles("RESPOS_DEFIS"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"links\": []}"))
                .andExpect(status().isForbidden());

        verify(service, never()).replaceAll(any());
    }

    @Test
    void assistantChallengeManagerCannotReplaceVerificationLinks() throws Exception {
        mockMvc.perform(put("/api/admin/department-verification-links")
                        .with(user("assistant-challenge-manager").roles("SOUS_RESPO_DEFIS"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"links\": []}"))
                .andExpect(status().isForbidden());

        verify(service, never()).replaceAll(any());
    }

    @Test
    void studentCannotReplaceVerificationLinks() throws Exception {
        mockMvc.perform(put("/api/admin/department-verification-links")
                        .with(user("student").roles("STUDENT"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"links\": []}"))
                .andExpect(status().isForbidden());

        verify(service, never()).replaceAll(any());
    }
}
