package ru.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import ru.config.SecurityConfig;
import ru.dto.AutoImportSettingsDto;
import ru.service.AutoImportService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AutoImportController.class)
@Import(SecurityConfig.class)
class AutoImportControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AutoImportService autoImportService;

    @Test
    @WithMockUser(username = "editor", roles = "EDITOR")
    void editorCannotRunAutoImport() throws Exception {
        mockMvc.perform(post("/api/auto-import/run"))
                .andExpect(status().isForbidden());

        verify(autoImportService, never()).runImport(any());
    }

    @Test
    @WithMockUser(username = "editor", roles = "EDITOR")
    void editorCannotUpdateAutoImportSettings() throws Exception {
        mockMvc.perform(put("/api/auto-import/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "sourceUrl": "https://example.test/schedule.csv"
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(autoImportService, never()).updateSettings(any(), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanRunAutoImport() throws Exception {
        given(autoImportService.runImport("admin"))
                .willReturn(AutoImportSettingsDto.builder().enabled(true).lastStatus("OK").build());

        mockMvc.perform(post("/api/auto-import/run"))
                .andExpect(status().isOk());

        verify(autoImportService).runImport("admin");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanUpdateAutoImportSettings() throws Exception {
        given(autoImportService.updateSettings(any(), eq("admin")))
                .willReturn(AutoImportSettingsDto.builder().enabled(true).build());

        mockMvc.perform(put("/api/auto-import/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "sourceUrl": "https://example.test/schedule.csv"
                                }
                                """))
                .andExpect(status().isOk());

        verify(autoImportService).updateSettings(any(), eq("admin"));
    }
}
