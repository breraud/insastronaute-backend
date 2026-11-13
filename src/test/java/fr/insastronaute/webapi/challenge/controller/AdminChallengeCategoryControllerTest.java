package fr.insastronaute.webapi.challenge.controller;

import fr.insastronaute.webapi.challenge.dto.ChallengeCategoryDto;
import fr.insastronaute.webapi.challenge.service.ChallengeCategoryService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminChallengeCategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChallengeCategoryService categoryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void adminCanListCategories() throws Exception {
        when(categoryService.findAll()).thenReturn(List.of(
                new ChallengeCategoryDto("DEFAULT", true, null, null),
                new ChallengeCategoryDto("photo", false, null, null)
        ));

        mockMvc.perform(get("/api/admin/challenge-categories")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("DEFAULT"))
                .andExpect(jsonPath("$[0].defaultCategory").value(true))
                .andExpect(jsonPath("$[1].name").value("photo"));
    }

    @Test
    void adminCanCreateCategory() throws Exception {
        when(categoryService.create(any())).thenReturn(new ChallengeCategoryDto("photo", false, null, null));

        mockMvc.perform(post("/api/admin/challenge-categories")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "photo"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("photo"));

        verify(categoryService).create(any());
    }

    @Test
    void adminCanRenameCategory() throws Exception {
        when(categoryService.update(eq("photo"), any()))
                .thenReturn(new ChallengeCategoryDto("media", false, null, null));

        mockMvc.perform(put("/api/admin/challenge-categories/photo")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "media"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("media"));

        verify(categoryService).update(eq("photo"), any());
    }

    @Test
    void adminCanDeleteCategory() throws Exception {
        mockMvc.perform(delete("/api/admin/challenge-categories/photo")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(categoryService).delete("photo");
    }
}
