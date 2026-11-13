package fr.insastronaute.webapi.common.adminaudit.controller;

import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.dto.AdminActionEventDto;
import fr.insastronaute.webapi.common.adminaudit.dto.AdminActionScope;
import fr.insastronaute.webapi.common.adminaudit.dto.AdminActionTypeDto;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionEventQueryService;
import fr.insastronaute.webapi.common.web.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminActionEventControllerTest {

    private static final String PATH = "/api/admin/action-events";

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID PLAYER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @MockitoBean
    private AdminActionEventQueryService queryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @BeforeEach
    void setUp() {
        when(queryService.search(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PageResponse.from(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0)));
        when(queryService.actionTypes())
                .thenReturn(Arrays.stream(AdminActionType.values()).map(AdminActionTypeDto::from).toList());
    }

    @Test
    void anAdministratorCanReadTheRegister() throws Exception {
        mockMvc.perform(get(PATH).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void aPlayerIsRefused() throws Exception {
        mockMvc.perform(get(PATH).with(user("player").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    /**
     * Le registre nomme des administrateurs et ce qu'ils ont donne : les roles
     * de staff qui ouvrent d'autres ecrans d'administration n'y ont pas acces.
     */
    @Test
    void anotherAdministrationRoleIsRefused() throws Exception {
        for (String role : List.of("RESPOS_DEFIS", "RESPO_CARTES", "RESPO_DEPART", "SOUS_RESPO_GOODIES")) {
            mockMvc.perform(get(PATH).with(user("staff").roles(role)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(PATH + "/types").with(user("staff").roles(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void anAnonymousCallerIsRefused() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    /**
     * La forme d'une ligne, telle que la page la lit : la cible absente est
     * rendue par {@code null} et non par un objet vide, et l'auteur absent
     * aussi - un objet vide se lirait comme un joueur dont on aurait perdu le
     * nom.
     */
    @Test
    void aPageCarriesResolvedNamesParsedDetailsAndGenuinelyAbsentSides() throws Exception {
        AdminActionEventDto nominal = new AdminActionEventDto(
                UUID.randomUUID(),
                Instant.parse("2026-08-26T10:00:00Z"),
                new AdminActionEventDto.AdminActionUser(ADMIN_ID, "Admin"),
                new AdminActionEventDto.AdminActionUser(PLAYER_ID, "Joueur"),
                AdminActionType.TOKENS_GRANTED,
                false,
                Map.of("delta", 50, "balance", 120),
                false);
        AdminActionEventDto global = new AdminActionEventDto(
                UUID.randomUUID(),
                Instant.parse("2026-08-25T10:00:00Z"),
                new AdminActionEventDto.AdminActionUser(ADMIN_ID, "Admin"),
                null,
                AdminActionType.COLLECTION_SCALE_CHANGED,
                true,
                Map.of("scope", "CARD_CATEGORY", "affectedPlayers", 42),
                false);
        AdminActionEventDto authorless = new AdminActionEventDto(
                UUID.randomUUID(),
                Instant.parse("2026-08-24T10:00:00Z"),
                null,
                new AdminActionEventDto.AdminActionUser(PLAYER_ID, "Joueur"),
                AdminActionType.CHALLENGE_VALIDATED,
                false,
                Map.of("challengeTitle", "Bullseye"),
                true);
        when(queryService.search(any(), any(), any(), any(), any(), any(), any())).thenReturn(
                PageResponse.from(new PageImpl<>(List.of(nominal, global, authorless), PageRequest.of(0, 50), 3)));

        String body = mockMvc.perform(get(PATH).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].actor.displayName").value("Admin"))
                .andExpect(jsonPath("$.content[0].target.displayName").value("Joueur"))
                .andExpect(jsonPath("$.content[0].action").value("TOKENS_GRANTED"))
                .andExpect(jsonPath("$.content[0].globalScope").value(false))
                .andExpect(jsonPath("$.content[0].details.delta").value(50))
                .andExpect(jsonPath("$.content[0].backfilled").value(false))
                .andExpect(jsonPath("$.content[1].globalScope").value(true))
                .andExpect(jsonPath("$.content[1].details.affectedPlayers").value(42))
                .andExpect(jsonPath("$.content[2].backfilled").value(true))
                .andReturn().getResponse().getContentAsString();

        JsonNode content = objectMapper.readTree(body).get("content");
        assertAbsent(content.get(1), "target");
        assertAbsent(content.get(2), "actor");
    }

    /**
     * Absent veut dire {@code null}, pas un objet vide : {@code jsonPath} rend
     * les deux de la meme facon, d'ou cette lecture directe de l'arbre.
     */
    private void assertAbsent(JsonNode row, String side) {
        assertThat(row.has(side)).as("la cle %s reste presente", side).isTrue();
        assertThat(row.get(side).isNull()).as("la cle %s vaut null", side).isTrue();
    }

    /** Chaque filtre arrive type au service, sans reparsage cote metier. */
    @Test
    void everyFilterReachesTheServiceTyped() throws Exception {
        mockMvc.perform(get(PATH)
                        .param("actorUserId", ADMIN_ID.toString())
                        .param("targetUserId", PLAYER_ID.toString())
                        .param("action", "TOKENS_GRANTED")
                        .param("action", "TOKENS_REVOKED")
                        .param("scope", "NOMINAL")
                        .param("from", "2026-08-24T10:00:00Z")
                        .param("to", "2026-08-26T10:00:00Z")
                        .param("page", "2")
                        .param("size", "10")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        verify(queryService).search(
                eq(ADMIN_ID),
                eq(PLAYER_ID),
                eq(List.of(AdminActionType.TOKENS_GRANTED, AdminActionType.TOKENS_REVOKED)),
                eq(AdminActionScope.NOMINAL),
                eq(Instant.parse("2026-08-24T10:00:00Z")),
                eq(Instant.parse("2026-08-26T10:00:00Z")),
                eq(PageRequest.of(2, 10)));
    }

    /**
     * Sans parametre de portee, le registre se lit entier : les lignes de
     * portee globale sont visibles par defaut, et c'est demander une portee
     * qui restreint.
     */
    @Test
    void theDefaultScopeIsTheWholeRegister() throws Exception {
        mockMvc.perform(get(PATH).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());

        verify(queryService).search(
                isNull(), isNull(), isNull(), eq(AdminActionScope.ALL), isNull(), isNull(), any(Pageable.class));
    }

    /**
     * L'attendu est derive de l'enumeration, pas recopie : une valeur ajoutee
     * demain apparait des deux cotes ou le test echoue.
     */
    @Test
    void theActionTypesAreServedSoThePanelDoesNotKeepItsOwnCopy() throws Exception {
        String body = mockMvc.perform(get(PATH + "/types").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readValue(body, new TypeReference<List<AdminActionTypeDto>>() {}))
                .containsExactlyElementsOf(
                        Arrays.stream(AdminActionType.values()).map(AdminActionTypeDto::from).toList());
    }

    /**
     * Un filtre type refuse ce qui n'est pas du type : une action inconnue est
     * une requete mal formee, pas une erreur serveur.
     */
    @Test
    void anUnknownActionIsRejectedRatherThanCrashing() throws Exception {
        mockMvc.perform(get(PATH).param("action", "TOKENS_STOLEN").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("scope", "PARTIAL").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("from", "hier").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    /**
     * La table est en ajout seul. Ces quatre appels portent leur jeton CSRF :
     * sans lui ils seraient refuses avant meme d'atteindre le routage, et le
     * test passerait pour la mauvaise raison.
     */
    @Test
    void noWriteVerbAnswersOnThisPath() throws Exception {
        mockMvc.perform(post(PATH).with(user("admin").roles("ADMIN")).with(csrf().asHeader()))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put(PATH).with(user("admin").roles("ADMIN")).with(csrf().asHeader()))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(patch(PATH).with(user("admin").roles("ADMIN")).with(csrf().asHeader()))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete(PATH).with(user("admin").roles("ADMIN")).with(csrf().asHeader()))
                .andExpect(status().isMethodNotAllowed());
    }

    /**
     * Et rien n'est monte en ecriture nulle part sous ce prefixe - pas
     * seulement sur la racine que le test precedent essaie. La propriete
     * d'ajout seul est ainsi tenue par un test, et non par la memoire de
     * celui qui ajoutera le prochain endpoint.
     */
    @Test
    void nothingUnderThisPrefixIsMappedForWriting() {
        Set<RequestMappingInfo> mappings = handlerMapping.getHandlerMethods().keySet().stream()
                .filter(AdminActionEventControllerTest::coversTheRegister)
                .collect(java.util.stream.Collectors.toSet());

        // Sans cela, un chemin renomme ferait passer ce test sur zero mapping.
        assertThat(mappings).isNotEmpty();
        assertThat(mappings).allSatisfy(mapping ->
                assertThat(mapping.getMethodsCondition().getMethods()).containsExactly(RequestMethod.GET));
    }

    /**
     * Compare des motifs de chemin litteraux : un verbe d'ecriture monte
     * ailleurs sous un motif gabarit - par exemple
     * {@code @DeleteMapping("/api/admin/{area}")} - echapperait a ce filtre
     * sans le faire echouer. Theorique aujourd'hui, mais a garder en tete
     * avant de faire confiance a ce garde-fou.
     */
    private static boolean coversTheRegister(RequestMappingInfo mapping) {
        if (mapping.getPathPatternsCondition() == null) {
            return false;
        }
        return mapping.getPathPatternsCondition().getPatternValues().stream()
                .anyMatch(pattern -> pattern.equals(PATH) || pattern.startsWith(PATH + "/"));
    }
}
