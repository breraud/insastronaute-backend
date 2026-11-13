package fr.insastronaute.webapi.card.controller;

import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.dto.AdminCardInstanceDto;
import fr.insastronaute.webapi.card.dto.CardArchiveImportResultDto;
import fr.insastronaute.webapi.card.dto.CardDeckCardDto;
import fr.insastronaute.webapi.card.dto.CardDto;
import fr.insastronaute.webapi.card.dto.CardInstanceDto;
import fr.insastronaute.webapi.card.service.CardArchiveService;
import fr.insastronaute.webapi.card.service.CardCategoryService;
import fr.insastronaute.webapi.card.service.CardInstanceService;
import fr.insastronaute.webapi.card.service.CardService;
import fr.insastronaute.webapi.common.web.StorageUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CardService cardService;

    @MockitoBean
    private CardInstanceService cardInstanceService;

    @MockitoBean
    private CardCategoryService cardCategoryService;

    @MockitoBean
    private CardArchiveService cardArchiveService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void adminCanListCards() throws Exception {
        UUID cardId = UUID.randomUUID();
        when(cardService.findAll()).thenReturn(List.of(new CardDto(
                cardId,
                "Carte par défaut",
                UUID.randomUUID(),
                "DEFAULT",
                true,
                true,
                0,
                "https://s3.example.com/public/cards/original.jpg",
                null,
                "https://s3.example.com/public/cards/thumbnail.jpg",
                "image/jpeg",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        )));

        mockMvc.perform(get("/api/admin/cards")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(cardId.toString()))
                .andExpect(jsonPath("$[0].defaultCard").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESPO_CARTES", "SOUS_RESPO_CARTES"})
    void cardStaffCanListPhysicalCardInstances(String role) throws Exception {
        when(cardInstanceService.searchAdmin(
                eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
                any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(org.springframework.data.domain.Page.empty());

        mockMvc.perform(get("/api/admin/cards/instances")
                        .with(user("card-staff").roles(role)))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanExportCardArchive() throws Exception {
        when(cardArchiveService.exportArchive()).thenReturn(new byte[]{'P', 'K', 3, 4});

        mockMvc.perform(get("/api/admin/cards/export")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentType(MediaType.parseMediaType("application/zip")));

        verify(cardArchiveService).exportArchive();
    }

    @Test
    void adminCanImportCardArchive() throws Exception {
        when(cardArchiveService.importArchive(any(), eq(true))).thenReturn(new CardArchiveImportResultDto(
                1,
                2,
                3,
                4,
                5,
                6,
                7
        ));

        mockMvc.perform(multipart("/api/admin/cards/import")
                        .file(new MockMultipartFile("archive", "cards.zip", "application/zip", new byte[]{'P', 'K'}))
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeCount").value(1))
                .andExpect(jsonPath("$.cardCount").value(3))
                .andExpect(jsonPath("$.instanceCount").value(4))
                .andExpect(jsonPath("$.objectCount").value(7));

        verify(cardArchiveService).importArchive(any(), eq(true));
    }

    @Test
    void adminCanImportCardArchiveWithoutPhysicalData() throws Exception {
        when(cardArchiveService.importArchive(any(), eq(false))).thenReturn(new CardArchiveImportResultDto(
                1,
                2,
                3,
                0,
                5,
                0,
                7
        ));

        mockMvc.perform(multipart("/api/admin/cards/import")
                        .file(new MockMultipartFile("archive", "cards.zip", "application/zip", new byte[]{'P', 'K'}))
                        .param("importPhysical", "false")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceCount").value(0))
                .andExpect(jsonPath("$.printRunCount").value(0));

        verify(cardArchiveService).importArchive(any(), eq(false));
    }

    @Test
    void adminCanCreateCardWithImage() throws Exception {
        UUID cardId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(cardService.create(eq("Carte"), eq(categoryId), eq(null), eq(null), eq(true), eq(false), eq(2), eq(120), eq(15), any(), eq(null)))
                .thenReturn(adminCard(cardId));

        mockMvc.perform(multipart("/api/admin/cards")
                        .file(imageFile())
                        .param("name", "Carte")
                        .param("categoryId", categoryId.toString())
                        .param("enabled", "true")
                        .param("defaultCard", "false")
                        .param("displayOrder", "2")
                        .param("firstAcquisitionPoints", "120")
                        .param("duplicatePoints", "15")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(cardId.toString()));

        verify(cardService).create(eq("Carte"), eq(categoryId), eq(null), eq(null), eq(true), eq(false), eq(2), eq(120), eq(15), any(), eq(null));
    }

    @Test
    void storageFailuresReturnServiceUnavailable() throws Exception {
        when(cardService.create(eq("Carte"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), any(), eq(null)))
                .thenThrow(new StorageUnavailableException(
                        "Le stockage des images de cartes est temporairement indisponible.",
                        new RuntimeException("S3 unavailable")
                ));

        mockMvc.perform(multipart("/api/admin/cards")
                        .file(imageFile())
                        .param("name", "Carte")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Le stockage des images de cartes est temporairement indisponible."));
    }

    @Test
    void adminCanPatchCardWithImage() throws Exception {
        UUID cardId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(cardService.update(eq(cardId), eq("Carte 2"), eq(categoryId), eq(null), eq(null), eq(false), eq(true), eq(false), eq(3), eq(null), eq(0), eq(true), eq(false), any()))
                .thenReturn(adminCard(cardId));

        MockMultipartHttpServletRequestBuilder request = multipart("/api/admin/cards/{id}", cardId);
        request.with(servletRequest -> {
            servletRequest.setMethod("PATCH");
            return servletRequest;
        });

        mockMvc.perform(request
                        .file(imageFile())
                        .param("name", "Carte 2")
                        .param("categoryId", categoryId.toString())
                        .param("enabled", "true")
                        .param("defaultCard", "false")
                        .param("displayOrder", "3")
                        .param("duplicatePoints", "0")
                        .param("clearFirstAcquisitionPoints", "true")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cardId.toString()));

        verify(cardService).update(eq(cardId), eq("Carte 2"), eq(categoryId), eq(null), eq(null), eq(false), eq(true), eq(false), eq(3), eq(null), eq(0), eq(true), eq(false), any());
    }

    @Test
    void adminCanPatchCoverImageOnly() throws Exception {
        when(cardService.updateCover(any())).thenReturn(adminCard(UUID.randomUUID()));

        mockMvc.perform(multipart("/api/admin/cards/cover")
                        .file(imageFile())
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        })
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());

        verify(cardService).updateCover(any());
    }

    @Test
    void adminCanDeleteCover() throws Exception {
        mockMvc.perform(delete("/api/admin/cards/cover")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(cardService).deleteCover();
    }

    @Test
    void adminCanDeleteCard() throws Exception {
        UUID cardId = UUID.randomUUID();

        mockMvc.perform(delete("/api/admin/cards/{id}", cardId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(cardService).delete(cardId);
    }

    @Test
    void adminCanGrantCardToUser() throws Exception {
        UUID cardId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(cardService.grant(eq(cardId), eq(CardCollectionType.VIRTUAL), any(), eq("admin-sub"))).thenReturn(new CardDeckCardDto(
                cardId,
                "Carte",
                UUID.randomUUID(),
                "Rare",
                false,
                2,
                3,
                "https://s3.example.com/public/cards/original.jpg",
                "https://s3.example.com/public/cards/thumbnail.jpg"
        ));

        mockMvc.perform(post("/api/admin/cards/{id}/collections", cardId)
                        .param("collectionType", "VIRTUAL")
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s",
                                  "quantity": 2
                                }
                                """.formatted(userId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(2));

        verify(cardService).grant(eq(cardId), eq(CardCollectionType.VIRTUAL), any(), eq("admin-sub"));
    }

    @Test
    void adminCanSearchPhysicalCardInstances() throws Exception {
        UUID cardId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(cardInstanceService.searchAdmin(
                eq(null), eq(cardId), eq("ABCDE-FGHJK-MNPQR"), eq(ownerId), eq("Rare"), eq(3), eq(null), any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(adminCardInstance(instanceId, cardId, ownerId))));

        mockMvc.perform(get("/api/admin/cards/instances")
                        .param("cardId", cardId.toString())
                        .param("publicId", "ABCDE-FGHJK-MNPQR")
                        .param("ownerUserId", ownerId.toString())
                        .param("type", "Rare")
                        .param("number", "3")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(instanceId.toString()))
                .andExpect(jsonPath("$.content[0].type").value("Rare"))
                .andExpect(jsonPath("$.content[0].number").value(3))
                .andExpect(jsonPath("$.content[0].ownerUserId").value(ownerId.toString()));

        verify(cardInstanceService).searchAdmin(
                eq(null), eq(cardId), eq("ABCDE-FGHJK-MNPQR"), eq(ownerId), eq("Rare"), eq(3), eq(null), any(org.springframework.data.domain.Pageable.class)
        );
    }

    @Test
    void adminCanPatchPhysicalCardInstance() throws Exception {
        UUID instanceId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(cardInstanceService.updateAdmin(eq(instanceId), any(), eq("admin-sub")))
                .thenReturn(adminCardInstance(instanceId, cardId, ownerId));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/admin/cards/instances/{instanceId}", instanceId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cardId": "%s",
                                  "publicId": "ABCDE-FGHJK-MNPQR",
                                  "ownerUserId": "%s"
                                }
                                """.formatted(cardId, ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(instanceId.toString()))
                .andExpect(jsonPath("$.cardId").value(cardId.toString()))
                .andExpect(jsonPath("$.ownerUserId").value(ownerId.toString()));

        verify(cardInstanceService).updateAdmin(eq(instanceId), any(), eq("admin-sub"));
    }

    @Test
    void adminCanListPhysicalCardInstances() throws Exception {
        UUID cardId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        when(cardInstanceService.findByCard(cardId)).thenReturn(List.of(cardInstance(instanceId, cardId, null)));

        mockMvc.perform(get("/api/admin/cards/{id}/instances", cardId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(instanceId.toString()))
                .andExpect(jsonPath("$[0].cardId").value(cardId.toString()));

        verify(cardInstanceService).findByCard(cardId);
    }

    @Test
    void adminCanCreatePhysicalCardInstance() throws Exception {
        UUID cardId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        when(cardInstanceService.create(eq(cardId), any())).thenReturn(cardInstance(instanceId, cardId, null));

        mockMvc.perform(post("/api/admin/cards/{id}/instances", cardId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(instanceId.toString()))
                .andExpect(jsonPath("$.publicId").value("ABCDE-FGHJK-MNPQR"));

        verify(cardInstanceService).create(eq(cardId), any());
    }

    @Test
    void adminCanCreatePhysicalCardInstanceFromBody() throws Exception {
        UUID cardId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(cardInstanceService.createAdmin(any(), eq("admin-sub"))).thenReturn(adminCardInstance(instanceId, cardId, ownerId));

        mockMvc.perform(post("/api/admin/cards/instances")
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cardId": "%s",
                                  "publicId": "ABCDE-FGHJK-MNPQR",
                                  "ownerUserId": "%s"
                                }
                                """.formatted(cardId, ownerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(instanceId.toString()))
                .andExpect(jsonPath("$.cardId").value(cardId.toString()))
                .andExpect(jsonPath("$.ownerUserId").value(ownerId.toString()));

        verify(cardInstanceService).createAdmin(any(), eq("admin-sub"));
    }

    @Test
    void adminCanCreatePhysicalCardInstanceBatch() throws Exception {
        UUID cardId = UUID.randomUUID();
        when(cardInstanceService.createBatch(eq(cardId), eq(2), any())).thenReturn(List.of(
                cardInstance(UUID.randomUUID(), cardId, null),
                cardInstance(UUID.randomUUID(), cardId, null)
        ));

        mockMvc.perform(post("/api/admin/cards/{id}/instances/batch", cardId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));

        verify(cardInstanceService).createBatch(eq(cardId), eq(2), any());
    }

    @Test
    void adminCanAssignPhysicalCardInstance() throws Exception {
        UUID instanceId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(cardInstanceService.assign(instanceId, userId, "admin-sub")).thenReturn(cardInstance(instanceId, cardId, userId));

        mockMvc.perform(post("/api/admin/cards/instances/{instanceId}/assignment", instanceId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s"
                                }
                                """.formatted(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerUserId").value(userId.toString()));

        verify(cardInstanceService).assign(instanceId, userId, "admin-sub");
    }

    @Test
    void adminCanReleasePhysicalCardInstance() throws Exception {
        UUID instanceId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        when(cardInstanceService.release(instanceId, "admin-sub")).thenReturn(cardInstance(instanceId, cardId, null));

        mockMvc.perform(delete("/api/admin/cards/instances/{instanceId}/assignment", instanceId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerUserId").doesNotExist());

        verify(cardInstanceService).release(instanceId, "admin-sub");
    }

    @Test
    void adminCanDeletePhysicalCardInstance() throws Exception {
        UUID instanceId = UUID.randomUUID();

        mockMvc.perform(delete("/api/admin/cards/instances/{instanceId}", instanceId)
                        .with(oidcLogin()
                                .idToken(token -> token.subject("admin-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(cardInstanceService).delete(instanceId, "admin-sub");
    }

    private CardDto adminCard(UUID cardId) {
        return new CardDto(
                cardId,
                "Carte",
                UUID.randomUUID(),
                "Légendaire",
                true,
                false,
                2,
                "https://s3.example.com/public/cards/original.jpg",
                null, // textlessImageUrl
                "https://s3.example.com/public/cards/thumbnail.jpg",
                "image/jpeg",
                null,
                null
        );
    }

    private CardInstanceDto cardInstance(UUID instanceId, UUID cardId, UUID ownerUserId) {
        return new CardInstanceDto(
                instanceId,
                cardId,
                "Carte",
                "ABCDE-FGHJK-MNPQR",
                ownerUserId,
                ownerUserId == null ? null : "Player",
                ownerUserId == null ? null : "player@example.com",
                ownerUserId == null ? null : Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                false
        );
    }

    private AdminCardInstanceDto adminCardInstance(UUID instanceId, UUID cardId, UUID ownerUserId) {
        return new AdminCardInstanceDto(
                instanceId,
                cardId,
                "Carte",
                "Rare",
                3,
                "ABCDE-FGHJK-MNPQR",
                ownerUserId,
                ownerUserId == null ? null : "Player",
                ownerUserId == null ? null : "player@example.com",
                null,
                null,
                null,
                null,
                ownerUserId == null ? null : Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private MockMultipartFile imageFile() {
        return new MockMultipartFile(
                "image",
                "card.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );
    }
}
