package fr.insastronaute.webapi.game.controller;

import fr.insastronaute.webapi.game.service.GameConfigService;
import fr.insastronaute.webapi.game.dto.GameConfigImportDto;
import fr.insastronaute.webapi.game.dto.GameConfigReleaseDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminGameConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GameConfigService gameConfigService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void adminCanListGameConfigs() throws Exception {
        when(gameConfigService.list()).thenReturn(List.of("cards.json", "levels/settings.json"));

        mockMvc.perform(get("/api/admin/game/configs")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("cards.json"))
                .andExpect(jsonPath("$[1]").value("levels/settings.json"));
    }

    @Test
    void adminCanListGameConfigFolders() throws Exception {
        when(gameConfigService.listFolders()).thenReturn(List.of("levels", "levels/world_1"));

        mockMvc.perform(get("/api/admin/game/configs/folders")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("levels"))
                .andExpect(jsonPath("$[1]").value("levels/world_1"));
    }

    @Test
    void adminCanReadGameConfig() throws Exception {
        when(gameConfigService.read("settings.json")).thenReturn("{\"enabled\":true}");

        mockMvc.perform(get("/api/admin/game/configs/settings.json")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"enabled\":true}"));
    }

    @Test
    void adminCanReadGameConfigInSubfolder() throws Exception {
        when(gameConfigService.read("levels/world_1/settings.json")).thenReturn("{\"enabled\":true}");

        mockMvc.perform(get("/api/admin/game/configs/levels/world_1/settings.json")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"enabled\":true}"));
    }

    @Test
    void adminCanWriteGameConfig() throws Exception {
        mockMvc.perform(put("/api/admin/game/configs/settings.json")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"enabled\":true}")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(gameConfigService).write("settings.json", "{\"enabled\":true}");
    }

    @Test
    void adminCanWriteGameConfigAsJsonContentType() throws Exception {
        mockMvc.perform(put("/api/admin/game/configs/settings.json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(gameConfigService).write("settings.json", "{\"enabled\":true}");
    }

    @Test
    void adminCanWriteGameConfigInSubfolder() throws Exception {
        mockMvc.perform(put("/api/admin/game/configs/levels/world_1/settings.json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(gameConfigService).write("levels/world_1/settings.json", "{\"enabled\":true}");
    }

    @Test
    void adminCanCreateGameConfigFolder() throws Exception {
        mockMvc.perform(post("/api/admin/game/configs/folders")
                        .param("path", "levels/world_1")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(gameConfigService).createFolder("levels/world_1");
    }

    @Test
    void nonAdminCannotReadGameConfigs() throws Exception {
        mockMvc.perform(get("/api/admin/game/configs")
                        .with(user("student").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void writeRequiresCsrfToken() throws Exception {
        mockMvc.perform(put("/api/admin/game/configs/settings.json")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createFolderRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/admin/game/configs/folders")
                        .param("path", "levels")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDeleteGameConfigFileInSubfolder() throws Exception {
        mockMvc.perform(delete("/api/admin/game/configs/levels/world_1/settings.json")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(gameConfigService).deleteFile("levels/world_1/settings.json");
    }

    @Test
    void deleteGameConfigFileRequiresCsrfToken() throws Exception {
        mockMvc.perform(delete("/api/admin/game/configs/settings.json")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDeleteGameConfigFolder() throws Exception {
        mockMvc.perform(delete("/api/admin/game/configs/folders")
                        .param("path", "levels/world_1")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(gameConfigService).deleteFolder("levels/world_1");
    }

    @Test
    void deleteGameConfigFolderRequiresCsrfToken() throws Exception {
        mockMvc.perform(delete("/api/admin/game/configs/folders")
                        .param("path", "levels")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReleaseGameConfigs() throws Exception {
        when(gameConfigService.release("2026.1.0")).thenReturn(releaseDto());

        mockMvc.perform(post("/api/admin/game/configs/releases")
                        .param("version", "2026.1.0")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value("2026.1.0"))
                .andExpect(jsonPath("$.objectPrefix").value("GameData/versions/2026.1.0/"))
                .andExpect(jsonPath("$.files[0]").value("settings.json"));

        verify(gameConfigService).release("2026.1.0");
    }

    @Test
    void adminCanUploadAndReleaseGameConfigs() throws Exception {
        when(gameConfigService.releaseUploaded(eq("2026.1.0"), anyList())).thenReturn(releaseDto());

        mockMvc.perform(multipart("/api/admin/game/configs/releases/upload")
                        .file(configFile("cards.json"))
                        .file(configFile("levels/world_1/settings.json"))
                        .param("version", "2026.1.0")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value("2026.1.0"))
                .andExpect(jsonPath("$.files[0]").value("settings.json"));

        verify(gameConfigService).releaseUploaded(eq("2026.1.0"), anyList());
    }

    @Test
    void adminCanGetLatestRelease() throws Exception {
        when(gameConfigService.latestRelease()).thenReturn(releaseDto());

        mockMvc.perform(get("/api/admin/game/configs/releases/latest")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("2026.1.0"));
    }

    @Test
    void adminCanListReleases() throws Exception {
        when(gameConfigService.listReleases()).thenReturn(List.of(
                releaseDto(),
                new GameConfigReleaseDto(
                        "2026.0.0",
                        "GameData/versions/2026.0.0/",
                        List.of("cards.json"),
                        Instant.parse("2026-07-08T10:00:00Z")
                )
        ));

        mockMvc.perform(get("/api/admin/game/configs/releases")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value("2026.1.0"))
                .andExpect(jsonPath("$[1].version").value("2026.0.0"))
                .andExpect(jsonPath("$[1].files[0]").value("cards.json"));
    }

    @Test
    void adminCanReadReleaseFile() throws Exception {
        when(gameConfigService.readReleaseFile("2026.1.0", "levels/world_1/settings.json"))
                .thenReturn("{\"enabled\":true}");

        mockMvc.perform(get("/api/admin/game/configs/releases/2026.1.0/files/levels/world_1/settings.json")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"enabled\":true}"));
    }

    @Test
    void releaseRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/admin/game/configs/releases")
                        .param("version", "2026.1.0")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadAndReleaseRequiresCsrfToken() throws Exception {
        mockMvc.perform(multipart("/api/admin/game/configs/releases/upload")
                        .file(configFile("settings.json"))
                        .param("version", "2026.1.0")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDeleteRelease() throws Exception {
        mockMvc.perform(delete("/api/admin/game/configs/releases/2026.1.0")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(gameConfigService).deleteRelease("2026.1.0");
    }

    @Test
    void deleteReleaseRequiresCsrfToken() throws Exception {
        mockMvc.perform(delete("/api/admin/game/configs/releases/2026.1.0")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    private GameConfigReleaseDto releaseDto() {
        return new GameConfigReleaseDto(
                "2026.1.0",
                "GameData/versions/2026.1.0/",
                List.of("settings.json"),
                Instant.parse("2026-07-09T10:00:00Z")
        );
    }

    private MockMultipartFile configFile(String path) {
        return new MockMultipartFile(
                "files",
                path,
                "application/json",
                "{\"enabled\":true}".getBytes()
        );
    }

    @Test
    void exportsTheEditableConfigsAsADownloadableArchive() throws Exception {
        when(gameConfigService.exportArchive()).thenReturn(new byte[] { 0x50, 0x4B, 0x03, 0x04 });

        mockMvc.perform(get("/api/admin/game/configs/archive").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"game-configs.zip\""))
                .andExpect(content().contentTypeCompatibleWith("application/zip"));
    }

    /**
     * Le remplacement efface l'existant : il ne peut pas s'activer par defaut, ni par
     * omission du parametre.
     */
    @Test
    void importDoesNotReplaceUnlessAsked() throws Exception {
        MockMultipartFile archive = new MockMultipartFile("archive", "configs.zip", "application/zip", new byte[] { 1 });
        when(gameConfigService.importArchive(any(), eq(false)))
                .thenReturn(new GameConfigImportDto(3, 1, false, 0));

        mockMvc.perform(multipart("/api/admin/game/configs/archive").file(archive)
                        .with(user("admin").roles("ADMIN")).with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileCount").value(3))
                .andExpect(jsonPath("$.replaced").value(false));

        verify(gameConfigService).importArchive(any(), eq(false));
    }

    @Test
    void importReplacesWhenExplicitlyAsked() throws Exception {
        MockMultipartFile archive = new MockMultipartFile("archive", "configs.zip", "application/zip", new byte[] { 1 });
        when(gameConfigService.importArchive(any(), eq(true)))
                .thenReturn(new GameConfigImportDto(3, 1, true, 7));

        mockMvc.perform(multipart("/api/admin/game/configs/archive").file(archive).param("replace", "true")
                        .with(user("admin").roles("ADMIN")).with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedCount").value(7));
    }
}
