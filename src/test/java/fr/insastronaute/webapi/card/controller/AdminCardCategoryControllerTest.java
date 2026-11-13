package fr.insastronaute.webapi.card.controller;

import fr.insastronaute.webapi.card.dto.CardCategoryDto;
import fr.insastronaute.webapi.card.service.CardCategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCardCategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CardCategoryService categoryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void adminCanUpdateCategoryImage() throws Exception {
        UUID categoryId = UUID.randomUUID();
        when(categoryService.updateImage(eq(categoryId), any())).thenReturn(categoryDto(categoryId));

        mockMvc.perform(multipart("/api/admin/card-categories/{id}/image", categoryId)
                        .file("image", new byte[]{1, 2, 3})
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        })
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl")
                        .value("https://s3.example.com/card-categories/rare/original.jpg"));

        verify(categoryService).updateImage(eq(categoryId), any());
    }

    @Test
    void adminCanDeleteCategoryImage() throws Exception {
        UUID categoryId = UUID.randomUUID();
        when(categoryService.deleteImage(categoryId)).thenReturn(new CardCategoryDto(
                categoryId,
                "Rare",
                1,
                null,
                null,
                null,
                false,
                false,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        ));

        mockMvc.perform(delete("/api/admin/card-categories/{id}/image", categoryId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());

        verify(categoryService).deleteImage(categoryId);
    }

    private CardCategoryDto categoryDto(UUID categoryId) {
        return new CardCategoryDto(
                categoryId,
                "Rare",
                1,
                "https://s3.example.com/card-categories/rare/original.jpg",
                "https://s3.example.com/card-categories/rare/thumbnail.jpg",
                "image/jpeg",
                false,
                false,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}
