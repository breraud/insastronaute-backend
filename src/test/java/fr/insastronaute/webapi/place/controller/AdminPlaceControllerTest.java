package fr.insastronaute.webapi.place.controller;

import fr.insastronaute.webapi.place.service.PlaceCanvasDescriptor;
import fr.insastronaute.webapi.place.service.PlaceCanvasResetEvent;
import fr.insastronaute.webapi.place.service.PlaceCanvasResizer;
import fr.insastronaute.webapi.place.service.PlaceCanvasStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminPlaceControllerTest {

    private static final String CONFIG_JSON = """
            {
              "isActive": false,
              "startDate": "2026-07-22T10:00:00Z",
              "endDate": "2026-07-23T10:00:00Z",
              "canvasWidth": %d,
              "canvasHeight": %d,
              "baseCooldownMs": 28800000,
              "challengeCooldownMs": 300000,
              "challengeRatio": 50%s
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private PlaceCanvasStore canvasStore;

    @MockitoBean
    private PlaceCanvasResizer canvasResizer;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void adminCanReadDynamicPlaceConfiguration() throws Exception {
        mockMvc.perform(get("/api/admin/place/config")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canvasWidth").value(500))
                .andExpect(jsonPath("$.challengeRatio").value(50));
    }

    @Test
    void rejectsCanvasWidthAboveMaximumWithStableCode() throws Exception {
        putConfig(2001, 500, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACE_DIMENSIONS_TOO_LARGE"));
    }

    @Test
    void rejectsCanvasHeightAboveMaximumWithStableCode() throws Exception {
        putConfig(500, 2001, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACE_DIMENSIONS_TOO_LARGE"));
    }

    @Test
    void requiresResizeModeWhenDimensionsChange() throws Exception {
        putConfig(600, 500, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACE_RESIZE_MODE_REQUIRED"));
    }

    @Test
    void acceptsMissingResizeModeWhenDimensionsAreUnchanged() throws Exception {
        putConfig(500, 500, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canvasWidth").value(500))
                .andExpect(jsonPath("$.canvasHeight").value(500));
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void dimensionChangeReturnsActivatedDimensionsAndBroadcastsResetRevision() throws Exception {
        byte[] source = new byte[500 * 500 * 3];
        byte[] resized = new byte[600 * 400 * 3];
        when(canvasStore.readCanvas()).thenReturn(new PlaceCanvasStore.CanvasRead(
                source, new PlaceCanvasDescriptor("old", 20, 500, 500)));
        when(canvasResizer.resize(source, 500, 500, 600, 400,
                fr.insastronaute.webapi.place.domain.PlaceResizeMode.PRESERVE_CENTERED)).thenReturn(resized);
        var lease = new PlaceCanvasStore.ResizeLease("test-owner");
        when(canvasStore.acquireResizeLease(500, 500)).thenReturn(lease);
        when(canvasStore.activateResizedGeneration(lease, 20, 600, 400, resized))
                .thenReturn(new PlaceCanvasStore.ResizeActivation("new", 21));

        putConfig(600, 400, ",\n  \"resizeMode\": \"PRESERVE_CENTERED\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canvasWidth").value(600))
                .andExpect(jsonPath("$.canvasHeight").value(400));

        verify(messagingTemplate).convertAndSend(
                "/topic/canvas-updates", new PlaceCanvasResetEvent("CANVAS_RESET", 21));
    }

    @Test
    void bonusPixelPricesAreSavedWhenSentAndLeftAloneWhenAbsent() throws Exception {
        putConfig(500, 500, """
                , "bonusPixelPrice": 45, "bonusPixelPriceIncrement": 3""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bonusPixelPrice").value(45))
                .andExpect(jsonPath("$.bonusPixelPriceIncrement").value(3));

        // Un client qui ignore encore ces champs configure le reste sans les écraser.
        putConfig(500, 500, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bonusPixelPrice").value(45))
                .andExpect(jsonPath("$.bonusPixelPriceIncrement").value(3));
    }

    private org.springframework.test.web.servlet.ResultActions putConfig(
            int canvasWidth,
            int canvasHeight,
            String extraFields
    ) throws Exception {
        return mockMvc.perform(put("/api/admin/place/config")
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .with(csrf())
                .contentType("application/json")
                .content(CONFIG_JSON.formatted(canvasWidth, canvasHeight, extraFields)));
    }
}
