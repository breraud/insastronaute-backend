package fr.insastronaute.webapi.event.controller;

import fr.insastronaute.webapi.event.domain.EventSource;
import fr.insastronaute.webapi.event.dto.EventDto;
import fr.insastronaute.webapi.event.service.EventService;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void adminCanUploadEventCover() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(eventService.uploadCover(eq(eventId), any())).thenReturn(new EventDto(
                eventId,
                "Tournoi",
                "Description",
                "Gymnase",
                "https://s3.example.com/insastronaute-public/events/" + eventId + "/cover/image.png",
                EventSource.TOURNAMENT,
                Instant.parse("2026-09-01T10:00:00Z"),
                null,
                null,
                false,
                null,
                false,
                null,
                List.of(),
                List.of(),
                0,
                0,
                null,
                List.of(),
                false,
                Instant.parse("2026-07-16T08:00:00Z"),
                Instant.parse("2026-07-16T08:10:00Z")
        ));

        mockMvc.perform(multipart("/api/admin/events/{eventId}/cover", eventId)
                        .file("file", new byte[]{(byte) 0x89, 'P', 'N', 'G'})
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId.toString()))
                .andExpect(jsonPath("$.coverImageUrl").value("https://s3.example.com/insastronaute-public/events/" + eventId + "/cover/image.png"));
    }

    @Test
    void studentCannotUploadEventCover() throws Exception {
        mockMvc.perform(multipart("/api/admin/events/{eventId}/cover", UUID.randomUUID())
                        .file("file", new byte[]{(byte) 0x89, 'P', 'N', 'G'})
                        .with(user("student").roles("STUDENT"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isForbidden());

        verifyNoInteractions(eventService);
    }
}
