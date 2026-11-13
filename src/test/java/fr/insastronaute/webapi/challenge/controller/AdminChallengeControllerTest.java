package fr.insastronaute.webapi.challenge.controller;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameterType;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTypeMetadata;
import fr.insastronaute.webapi.challenge.auto.registry.AutoChallengeTypeRegistry;
import fr.insastronaute.webapi.challenge.dto.AdminChallengeDto;
import fr.insastronaute.webapi.challenge.dto.AdminChallengePageDto;
import fr.insastronaute.webapi.challenge.dto.ChallengeBulkUpdateDto;
import fr.insastronaute.webapi.challenge.dto.ChallengeBulkUpdateResultDto;
import fr.insastronaute.webapi.challenge.service.ChallengeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminChallengeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChallengeService challengeService;

    @MockitoBean
    private AutoChallengeTypeRegistry autoChallengeTypeRegistry;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void getAllChallengesReturnsEveryChallengeForAdminUsers() throws Exception {
        UUID challengeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        var challenge = new AdminChallengeDto(
                challengeId,
                "Photo equipe",
                "Une preuve en groupe",
                10,
                false,
                "photo",
                java.util.List.of("photo"),
                null,
                null,
                null,
                null,
                "ADMIN",
                "MEDIA",
                null,
                null,
                true,
                false,
                1,
                3,
                List.of(departmentId),
                List.of(),
                null,
                null
        , null);
        when(challengeService.getChallengesPage("photo", "active", "photo", "ADMIN", "START_DATE_DESC", 1, 25))
                .thenReturn(new AdminChallengePageDto(List.of(challenge), 1, 25, 26, 2, 7, 3, 4));

        mockMvc.perform(get("/api/admin/challenges")
                        .param("q", "photo")
                        .param("status", "active")
                        .param("category", "photo")
                        .param("validation", "ADMIN")
                        .param("page", "1")
                        .param("size", "25")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content[0].id").value(challengeId.toString()))
                .andExpect(jsonPath("$.content[0].title").value("Photo equipe"))
                .andExpect(jsonPath("$.totalElements").value(26))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.activeCount").value(7));
    }

    @Test
    void getAutoTypesReturnsRegistryMetadataForAdminUsers() throws Exception {
        when(autoChallengeTypeRegistry.metadata()).thenReturn(List.of(new AutoChallengeTypeMetadata(
                "PERMANENT_QR_SCANNED",
                "QR permanent scanne",
                "Valide le defi lorsque le QR permanent est scanne.",
                List.of(new AutoChallengeParameter(
                        "minimumScans",
                        "Nombre minimum de scans",
                        AutoChallengeParameterType.INTEGER,
                        false,
                        JsonNodeFactory.instance.numberNode(1),
                        1L,
                        null,
                        List.of()
                ))
        , false)));

        mockMvc.perform(get("/api/admin/challenges/auto-types")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("PERMANENT_QR_SCANNED"))
                .andExpect(jsonPath("$[0].parameters[0].name").value("minimumScans"));
    }

    @Test
    void getAutoTypesRejectsNonAdminUsers() throws Exception {
        mockMvc.perform(get("/api/admin/challenges/auto-types")
                        .with(user("player").roles("USER")))
                .andExpect(status().isForbidden());

        verify(autoChallengeTypeRegistry, never()).metadata();
    }

    @Test
    void createChallengeReturnsFieldErrorWhenMinGroupSizeIsInvalid() throws Exception {
        mockMvc.perform(post("/api/admin/challenges")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Photo equipe",
                                  "description": "Une preuve en groupe",
                                  "pointsReward": 10,
                                  "category": "photo",
                                  "validationMode": "ADMIN",
                                  "sensitive": false,
                                  "minGroupSize": 0,
                                  "maxGroupSize": 1,
                                  "targetDepartmentIds": [],
                                  "prerequisiteChallengeIds": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message")
                        .value("La taille minimale du groupe doit être supérieure ou égale à 1."))
                .andExpect(jsonPath("$.path").value("/api/admin/challenges"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("minGroupSize"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value("La taille minimale du groupe doit être supérieure ou égale à 1."));

        verify(challengeService, never()).createChallenge(any());
    }

    /**
     * Le panel envoie une chaîne vide, jamais null, pour une date qu'on n'a pas
     * saisie. Le contrat en dépend depuis toujours pour startDate ; publishedAt
     * emprunte le même chemin, et ce test le retient.
     */
    @org.junit.jupiter.api.Test
    void anEmptyPublicationDateIsReadAsNoPublicationDate() throws Exception {
        org.mockito.ArgumentCaptor<fr.insastronaute.webapi.challenge.dto.ChallengeCreateDto> captor =
                org.mockito.ArgumentCaptor.forClass(fr.insastronaute.webapi.challenge.dto.ChallengeCreateDto.class);

        mockMvc.perform(post("/api/admin/challenges")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Photo equipe",
                                  "description": "Une preuve en groupe",
                                  "pointsReward": 10,
                                  "category": "photo",
                                  "startDate": "",
                                  "endDate": "",
                                  "publishedAt": "",
                                  "validationMode": "ADMIN",
                                  "sensitive": false,
                                  "minGroupSize": 1,
                                  "maxGroupSize": 1,
                                  "targetDepartmentIds": [],
                                  "prerequisiteChallengeIds": []
                                }
                                """))
                .andExpect(status().isCreated());

        verify(challengeService).createChallenge(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().publishedAt()).isNull();
        org.assertj.core.api.Assertions.assertThat(captor.getValue().startDate()).isNull();
    }

    /**
     * Un défi automatique porte ses paramètres en JSON libre.
     *
     * <p>La couche HTTP est passée à Jackson 3 ; le champ était resté typé avec le
     * {@code JsonNode} de Jackson 2, que Jackson 3 ne sait pas construire. Toute
     * création d'un défi automatique répondait 500 — « Cannot construct instance of
     * com.fasterxml.jackson.databind.JsonNode ». Ce test poste le corps tel que le
     * panneau l'envoie, donc il traverse le vrai convertisseur.</p>
     */
    @org.junit.jupiter.api.Test
    void automaticChallengeParametersSurviveTheHttpBoundary() throws Exception {
        org.mockito.ArgumentCaptor<fr.insastronaute.webapi.challenge.dto.ChallengeCreateDto> captor =
                org.mockito.ArgumentCaptor.forClass(fr.insastronaute.webapi.challenge.dto.ChallengeCreateDto.class);

        mockMvc.perform(post("/api/admin/challenges")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Trois legendaires",
                                  "description": "Obtiens trois cartes",
                                  "pointsReward": 10,
                                  "category": "photo",
                                  "validationMode": "AUTO",
                                  "autoChallengeType": "CARD_CATEGORY_OBTAINED",
                                  "autoChallengeParameters": {
                                    "categoryId": "6f1b6f0e-6b3e-4f3a-9a4a-2d2f0f9f5a11",
                                    "count": 3,
                                    "collectionType": "PHYSICAL"
                                  },
                                  "sensitive": false,
                                  "minGroupSize": 1,
                                  "maxGroupSize": 1,
                                  "targetDepartmentIds": [],
                                  "prerequisiteChallengeIds": []
                                }
                                """))
                .andExpect(status().isCreated());

        verify(challengeService).createChallenge(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().autoChallengeParameters())
                .containsEntry("count", 3)
                .containsEntry("collectionType", "PHYSICAL");
    }

    @Test
    void getIdsReturnsMatchingChallengeIdsForAdminUsers() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        when(challengeService.getChallengeIds("photo", "active", "photo", "ADMIN"))
                .thenReturn(List.of(id1, id2));

        mockMvc.perform(get("/api/admin/challenges/ids")
                        .param("q", "photo")
                        .param("status", "active")
                        .param("category", "photo")
                        .param("validation", "ADMIN")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0]").value(id1.toString()))
                .andExpect(jsonPath("$[1]").value(id2.toString()));

        verify(challengeService).getChallengeIds("photo", "active", "photo", "ADMIN");
    }

    @Test
    void getIdsRejectsNonAdminUsers() throws Exception {
        mockMvc.perform(get("/api/admin/challenges/ids")
                        .with(user("player").roles("USER")))
                .andExpect(status().isForbidden());

        verify(challengeService, never()).getChallengeIds(any(), any(), any(), any());
    }

    @Test
    void bulkUpdateCallsServiceAndReturnsOk() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        ChallengeBulkUpdateDto requestDto = new ChallengeBulkUpdateDto(
                List.of(id1, id2),
                true,
                Instant.parse("2026-09-01T00:00:00Z"),
                false,
                null,
                false,
                null,
                true,
                List.of("photo")
        , null);
        ChallengeBulkUpdateResultDto responseDto = new ChallengeBulkUpdateResultDto(2, List.of(id1, id2), 0);

        when(challengeService.bulkUpdateChallenges(eq(requestDto), eq("admin-sub")))
                .thenReturn(responseDto);

        mockMvc.perform(post("/api/admin/challenges/bulk-update")
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "challengeIds": ["%s", "%s"],
                                  "updateStartDate": true,
                                  "startDate": "2026-09-01T00:00:00Z",
                                  "updateEndDate": false,
                                  "endDate": null,
                                  "updatePublishedAt": false,
                                  "publishedAt": null,
                                  "updateCategories": true,
                                  "categories": ["photo"]
                                }
                                """.formatted(id1, id2)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.updatedCount").value(2))
                .andExpect(jsonPath("$.updatedIds[0]").value(id1.toString()))
                .andExpect(jsonPath("$.updatedIds[1]").value(id2.toString()));

        verify(challengeService).bulkUpdateChallenges(eq(requestDto), eq("admin-sub"));
    }

    @Test
    void bulkUpdateReturnsBadRequestWhenChallengeIdsIsEmpty() throws Exception {
        mockMvc.perform(post("/api/admin/challenges/bulk-update")
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "challengeIds": [],
                                  "updateStartDate": false,
                                  "updateEndDate": false,
                                  "updatePublishedAt": false,
                                  "updateCategories": false
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(challengeService, never()).bulkUpdateChallenges(any(), any());
    }

    @Test
    void bulkUpdateRejectsNonAdminUsers() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/admin/challenges/bulk-update")
                        .with(user("player").roles("USER"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "challengeIds": ["%s"],
                                  "updateStartDate": false,
                                  "updateEndDate": false,
                                  "updatePublishedAt": false,
                                  "updateCategories": false
                                }
                                """.formatted(id)))
                .andExpect(status().isForbidden());

        verify(challengeService, never()).bulkUpdateChallenges(any(), any());
    }
}
