package fr.insastronaute.webapi.medialibrary.web;

import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.common.web.RateLimitExceededException;
import fr.insastronaute.webapi.common.web.StorageUnavailableException;
import fr.insastronaute.webapi.medialibrary.service.MediaLibraryExportService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
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

import java.io.OutputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaLibraryExportControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MediaLibraryExportService exportService;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void aPartIsAZipAttachmentAndReleasesItsSlot() throws Exception {
        UUID exportId = UUID.randomUUID();
        MediaLibraryExportService.ExportSlot slot = mock(MediaLibraryExportService.ExportSlot.class);
        when(exportService.acquireSlot()).thenReturn(slot);
        when(exportService.preparePart(eq("staff-sub"), any(), eq(exportId), eq(1)))
                .thenReturn(new MediaLibraryExportService.PartPlan(Instant.parse("2026-10-02T08:00:00Z"), 1, 3, List.of()));
        doAnswer(call -> {
            ((OutputStream) call.getArgument(1)).write(new byte[]{'P', 'K'});
            return null;
        }).when(exportService).writePart(any(), any());

        mockMvc.perform(get("/api/media-library/exports/{id}/parts/{n}", exportId, 1)
                        .with(oidcLogin().idToken(token -> token.subject("staff-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"mediatheque-2026-10-02-partie-1-sur-3.zip\""))
                .andExpect(content().bytes(new byte[]{'P', 'K'}));

        verify(slot).close();
        InOrder order = inOrder(exportService);
        order.verify(exportService).checkPart(eq("staff-sub"), any(), eq(exportId), eq(1));
        order.verify(exportService).acquireSlot();
        order.verify(exportService).preparePart(eq("staff-sub"), any(), eq(exportId), eq(1));
    }

    /** Rien n'est encore parti : une vraie erreur JSON, sans rien qui ressemble a un zip. */
    @Test
    void aFailureBeforeTheFirstByteIsAJsonErrorWithoutTheZipHeaders() throws Exception {
        UUID exportId = UUID.randomUUID();
        when(exportService.acquireSlot()).thenReturn(mock(MediaLibraryExportService.ExportSlot.class));
        when(exportService.preparePart(eq("staff-sub"), any(), eq(exportId), eq(1)))
                .thenReturn(new MediaLibraryExportService.PartPlan(Instant.parse("2026-10-02T08:00:00Z"), 1, 3, List.of()));
        doThrow(new StorageUnavailableException("Le stockage des médias ne répond pas.", null))
                .when(exportService).writePart(any(), any());

        mockMvc.perform(get("/api/media-library/exports/{id}/parts/{n}", exportId, 1)
                        .with(oidcLogin().idToken(token -> token.subject("staff-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Le stockage des médias ne répond pas."));
    }

    /**
     * Deux exports tournent : un identifiant faux ou un refus d'acces doivent quand meme
     * repondre 404 ou 403, pas 429. La verification se fait avant de prendre une place.
     */
    @Test
    void aMissingPartIsA404EvenWhenTheServerIsBusy() throws Exception {
        UUID exportId = UUID.randomUUID();
        when(exportService.acquireSlot()).thenThrow(new RateLimitExceededException(5_000));
        doThrow(new NotFoundException("Partie d'export introuvable."))
                .when(exportService).checkPart(eq("staff-sub"), any(), eq(exportId), eq(9));

        mockMvc.perform(get("/api/media-library/exports/{id}/parts/{n}", exportId, 9)
                        .with(oidcLogin().idToken(token -> token.subject("staff-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound());
        verify(exportService, never()).acquireSlot();
    }

    @Test
    void aRefusedUserIsA403EvenWhenTheServerIsBusy() throws Exception {
        when(exportService.acquireSlot()).thenThrow(new RateLimitExceededException(5_000));
        doThrow(new ForbiddenException("Accès à la Médiathèque refusé."))
                .when(exportService).checkPart(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());

        mockMvc.perform(get("/api/media-library/exports/{id}/parts/{n}", UUID.randomUUID(), 1)
                        .with(oidcLogin().idToken(token -> token.subject("staff-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aBusyServerAnswers429() throws Exception {
        when(exportService.acquireSlot()).thenThrow(new RateLimitExceededException(5_000));

        mockMvc.perform(get("/api/media-library/exports/{id}/parts/{n}", UUID.randomUUID(), 1)
                        .with(oidcLogin().idToken(token -> token.subject("staff-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "5"));
    }
}
