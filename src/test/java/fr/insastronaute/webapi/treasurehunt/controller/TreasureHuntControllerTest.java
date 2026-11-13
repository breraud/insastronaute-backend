package fr.insastronaute.webapi.treasurehunt.controller;

import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntContentType;
import fr.insastronaute.webapi.treasurehunt.dto.CheckpointValidationResponse;
import fr.insastronaute.webapi.treasurehunt.dto.PlayerPathProgressResponse;
import fr.insastronaute.webapi.treasurehunt.dto.PlayerTreasureHuntProgressResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntContentResponse;
import fr.insastronaute.webapi.treasurehunt.dto.ValidatedCheckpointResponse;
import fr.insastronaute.webapi.treasurehunt.dto.ValidatedCheckpointsResponse;
import fr.insastronaute.webapi.treasurehunt.service.TreasureHuntService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TreasureHuntControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean private TreasureHuntService service;

    @Test
    void studentLimitedCannotAccessTreasureHuntReadsOrMutations() throws Exception {
        mockMvc.perform(get("/api/treasure-hunt/progress").with(limitedOidc()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_LIMITED_ACTION_FORBIDDEN"));
        mockMvc.perform(get("/api/treasure-hunt/checkpoints/validated").with(limitedOidc()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STUDENT_LIMITED_ACTION_FORBIDDEN"));
        mockMvc.perform(post("/api/treasure-hunt/checkpoints/validate")
                        .with(limitedOidc()).with(csrf().asHeader())
                        .contentType("application/json")
                        .content("{\"tool\":\"KEYPAD\",\"secret\":\"STEP-1\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/treasure-hunt/vault/unlock")
                        .with(limitedOidc()).with(csrf().asHeader())
                        .contentType("application/json")
                        .content("{\"codes\":[\"A\",\"B\",\"C\",\"D\",\"E\",\"F\"]}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void returnsPersistedProgressForEveryPlayerPath() throws Exception {
        UUID pathId = UUID.randomUUID();
        when(service.getProgress("player-sub")).thenReturn(new PlayerTreasureHuntProgressResponse(List.of(
                new PlayerPathProgressResponse(pathId, "GM", 2, null)
        )));

        mockMvc.perform(get("/api/treasure-hunt/progress")
                        .with(SecurityMockMvcRequestPostProcessors.oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths[0].pathId").value(pathId.toString()))
                .andExpect(jsonPath("$.paths[0].department").value("GM"))
                .andExpect(jsonPath("$.paths[0].validatedStepCount").value(2))
                .andExpect(jsonPath("$.paths[0].vaultCode").doesNotExist())
                // La position de la voie est la combinaison du coffre : elle ne sort pas.
                .andExpect(jsonPath("$.paths[0].pathPosition").doesNotExist());
    }

    @Test
    void returnsOrderedCompositeContentsWhenAPlayerValidatesACheckpoint() throws Exception {
        UUID pathId = UUID.randomUUID();
        when(service.validateCheckpoint("player-sub", "KEYPAD", "STEP-1", null)).thenReturn(new CheckpointValidationResponse(pathId, 1,
                List.of(new TreasureHuntContentResponse(TreasureHuntContentType.RICH_TEXT, "Bravo", null, null)), null));

        mockMvc.perform(post("/api/treasure-hunt/checkpoints/validate")
                        .with(SecurityMockMvcRequestPostProcessors.oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf().asHeader())
                        .contentType("application/json").content("{\"tool\":\"KEYPAD\",\"secret\":\"STEP-1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contents[0].text").value("Bravo"))
                // Une etape par voie aurait sinon suffi a reconstituer la combinaison.
                .andExpect(jsonPath("$.pathPosition").doesNotExist());
    }

    // Un corps incomplet se fait refuser comme un secret faux, pas par la validation du
    // contrat : un 400 d'une autre forme designerait l'outil attendu.
    @Test
    void refusesAnIncompleteBodyLikeAnyOtherWrongSecret() throws Exception {
        when(service.validateCheckpoint("player-sub", null, null, null))
                .thenThrow(new fr.insastronaute.webapi.common.web.BadRequestException(TreasureHuntService.REFUSAL));

        mockMvc.perform(post("/api/treasure-hunt/checkpoints/validate")
                        .with(SecurityMockMvcRequestPostProcessors.oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf().asHeader())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(TreasureHuntService.REFUSAL));
    }

    // L'invariant central de l'etabli, verifie la ou le joueur le lit : sur le fil.
    //
    // Deux refus de natures differentes -- un bon secret soumis par le mauvais outil, un
    // secret que personne ne connait -- doivent rendre le meme corps, au caractere pres.
    // Le service leve bien la meme exception dans les deux cas (voir TreasureHuntServiceTest) ;
    // ce qui restait non tenu, c'est la traduction HTTP : un gestionnaire d'erreurs qui
    // ajouterait le champ fautif, un code d'erreur ou le chemin de la requete suffirait a
    // designer l'outil attendu, sans qu'aucun test ne bronche.
    @Test
    void rendersTheVerySameBodyForTwoRefusalsOfDifferentNatures() throws Exception {
        // Le bon secret d'une etape piano, soumis au clavier.
        when(service.validateCheckpoint("player-sub", "KEYPAD", "DO-RE-MI", null))
                .thenThrow(new fr.insastronaute.webapi.common.web.BadRequestException(TreasureHuntService.REFUSAL));
        // Un secret qu'aucune etape ne porte.
        when(service.validateCheckpoint("player-sub", "PIANO", "LA-LA-LA", null))
                .thenThrow(new fr.insastronaute.webapi.common.web.BadRequestException(TreasureHuntService.REFUSAL));

        Map<String, Object> wrongTool = refusal("{\"tool\":\"KEYPAD\",\"secret\":\"DO-RE-MI\"}");
        Map<String, Object> unknownSecret = refusal("{\"tool\":\"PIANO\",\"secret\":\"LA-LA-LA\"}");

        assertThat(unknownSecret).isEqualTo(wrongTool);
        // Et c'est bien le refus unique, pas deux corps vides qui se ressembleraient par hasard.
        assertThat(wrongTool).containsEntry("message", TreasureHuntService.REFUSAL);
    }

    /**
     * Le refus rendu par l'endpoint de validation pour un corps donne, horodatage retire.
     *
     * L'horodatage est la seule chose que deux appels ne peuvent pas partager, et il ne dit
     * rien de la nature du refus : tout le reste du corps doit concorder.
     */
    private Map<String, Object> refusal(String body) throws Exception {
        String json = mockMvc.perform(post("/api/treasure-hunt/checkpoints/validate")
                        .with(SecurityMockMvcRequestPostProcessors.oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf().asHeader())
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        Map<String, Object> parsed = new java.util.HashMap<>(
                new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { }));
        parsed.remove("timestamp");
        return parsed;
    }

    // Le backend se deploie a la main et le front par Cloudflare Pages : les deux ne
    // partent jamais ensemble, un ancien client qui envoie encore {pathId, code} doit
    // donc continuer de fonctionner.
    @Test
    void stillAcceptsTheOldRequestShapeUntilBothFrontsAreDeployed() throws Exception {
        UUID pathId = UUID.randomUUID();
        when(service.validateCheckpoint("player-sub", null, "STEP-1", pathId)).thenReturn(new CheckpointValidationResponse(pathId, 1,
                List.of(new TreasureHuntContentResponse(TreasureHuntContentType.RICH_TEXT, "Bravo", null, null)), null));

        mockMvc.perform(post("/api/treasure-hunt/checkpoints/validate")
                        .with(SecurityMockMvcRequestPostProcessors.oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf().asHeader())
                        .contentType("application/json").content("{\"pathId\":\"" + pathId + "\",\"code\":\"STEP-1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contents[0].text").value("Bravo"));
    }

    @Test
    void handsBackTheContentOfTheStepsThePlayerAlreadyWalked() throws Exception {
        UUID pathId = UUID.randomUUID();
        when(service.getValidatedCheckpoints("player-sub")).thenReturn(new ValidatedCheckpointsResponse(List.of(
                new ValidatedCheckpointResponse(pathId, 2,
                        List.of(new TreasureHuntContentResponse(TreasureHuntContentType.RICH_TEXT, "Bravo", null, null)))
        )));

        mockMvc.perform(get("/api/treasure-hunt/checkpoints/validated")
                        .with(SecurityMockMvcRequestPostProcessors.oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")).idToken(token -> token.subject("player-sub"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkpoints[0].pathId").value(pathId.toString()))
                .andExpect(jsonPath("$.checkpoints[0].stepNumber").value(2))
                .andExpect(jsonPath("$.checkpoints[0].contents[0].text").value("Bravo"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor limitedOidc() {
        return oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT_LIMITED"))
                .idToken(token -> token.subject("limited-sub"));
    }
}
