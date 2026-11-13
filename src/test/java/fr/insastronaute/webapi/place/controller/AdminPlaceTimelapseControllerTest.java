package fr.insastronaute.webapi.place.controller;

import fr.insastronaute.webapi.common.web.CodedBadRequestException;
import fr.insastronaute.webapi.place.domain.PlaceTimelapseColouring;
import fr.insastronaute.webapi.place.service.PlaceTimelapseService;
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
import org.springframework.test.web.servlet.MvcResult;

import java.io.OutputStream;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminPlaceTimelapseControllerTest {

    private static final Instant FROM = Instant.parse("2026-07-20T10:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-23T10:00:00Z");

    @Autowired MockMvc mockMvc;
    @MockitoBean PlaceTimelapseService timelapseService;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean ClientRegistrationRepository clientRegistrationRepository;

    /** L'historique sert la moderation : il ne s'ouvre pas aux joueurs par une autre porte. */
    @Test
    void aStudentCannotDownloadTheReplay() throws Exception {
        mockMvc.perform(request().with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(timelapseService);
    }

    @Test
    void anAdminDownloadsTheStreamAsAFile() throws Exception {
        PlaceTimelapseService.Replay replay =
                new PlaceTimelapseService.Replay(PlaceTimelapseColouring.PLACED, "gen", 500, 400, FROM, TO);
        when(timelapseService.prepare(eq(PlaceTimelapseColouring.PLACED), eq(FROM), eq(TO))).thenReturn(replay);
        doAnswer(invocation -> {
            ((OutputStream) invocation.getArgument(0)).write(new byte[]{'P', 'L', 'T', 'L'});
            return 4L;
        }).when(timelapseService).writeTo(any(), eq(replay));

        // Le corps s'ecrit en asynchrone : `perform` rend la main des que le flux est
        // ouvert, pas quand il est ecrit. Sans `asyncDispatch`, la lecture du corps
        // gagne parfois la course et lit vide — elle la gagnait ici, et la perdait sur
        // un runner plus lent.
        MvcResult started = mockMvc.perform(request().with(admin()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"place-timelapse-placed.pltl\""))
                .andExpect(content().bytes(new byte[]{'P', 'L', 'T', 'L'}));
    }

    /** La coloration se choisit ; les couleurs posees sont celles qu'on veut par defaut. */
    @Test
    void theColouringCanBeAskedForExplicitly() throws Exception {
        when(timelapseService.prepare(any(), any(), any())).thenReturn(
                new PlaceTimelapseService.Replay(PlaceTimelapseColouring.DEPARTMENT, "gen", 500, 400, FROM, TO));

        MvcResult started = mockMvc.perform(get("/api/admin/place/timelapse")
                        .param("from", FROM.toString())
                        .param("to", TO.toString())
                        .param("colorBy", "DEPARTMENT")
                        .with(admin()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"place-timelapse-department.pltl\""));

        verify(timelapseService).prepare(PlaceTimelapseColouring.DEPARTMENT, FROM, TO);
    }

    /**
     * Un refus doit arriver comme un statut, pas comme un fichier tronque : la
     * verification precede donc l'ouverture du flux.
     */
    @Test
    void aRefusedWindowFailsBeforeTheDownloadStarts() throws Exception {
        when(timelapseService.prepare(any(), any(), any()))
                .thenThrow(new CodedBadRequestException("PLACE_TIMELAPSE_EMPTY_WINDOW", "période vide"));

        mockMvc.perform(request().with(admin()))
                .andExpect(status().isBadRequest());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request() {
        return get("/api/admin/place/timelapse")
                .param("from", FROM.toString())
                .param("to", TO.toString());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
