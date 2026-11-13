package fr.insastronaute.webapi.card.goofy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminGoofyCardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoofyCardService goofyCardService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void cardAdminCanGenerateGoofyCode() throws Exception {
        when(goofyCardService.generateUniqueCode()).thenReturn("ABCDE-FGHJK-MNPQR");

        mockMvc.perform(post("/api/admin/cards/goofy/generate-code")
                        .with(user("admin").roles("RESPO_CARTES"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ABCDE-FGHJK-MNPQR"));
    }

    @Test
    void unauthorizedUserCannotGenerateGoofyCode() throws Exception {
        mockMvc.perform(post("/api/admin/cards/goofy/generate-code")
                        .with(user("player").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
