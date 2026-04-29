package ru.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.config.SecurityConfig;
import ru.dto.AutoImportSettingsDto;
import ru.dto.AutoImportSettingsUpdateRequest;
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
@TestPropertySource(properties = {
        "security.jwt.secret=test-jwt-secret-test-jwt-secret-123456",
        "internal.security.api-key=test-internal-api-key"
})
class AutoImportControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AutoImportService autoImportService;

    @Test
    @WithMockUser(username = "editor", roles = "EDITOR")
    void updateSettings_isForbiddenForEditor() throws Exception {
        mockMvc.perform(put("/api/auto-import/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "sourceUrl": "https://docs.google.com/spreadsheets/d/sheet_123-ABC/edit"
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(autoImportService, never()).updateSettings(any(AutoImportSettingsUpdateRequest.class), any());
    }

    @Test
    @WithMockUser(username = "editor", roles = "EDITOR")
    void runNow_isForbiddenForEditor() throws Exception {
        mockMvc.perform(post("/api/auto-import/run"))
                .andExpect(status().isForbidden());

        verify(autoImportService, never()).runImport(any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateSettings_isAllowedForAdmin() throws Exception {
        given(autoImportService.updateSettings(any(AutoImportSettingsUpdateRequest.class), eq("admin")))
                .willReturn(AutoImportSettingsDto.builder()
                        .enabled(true)
                        .sourceUrl("https://docs.google.com/spreadsheets/d/sheet_123-ABC/edit")
                        .updatedBy("admin")
                        .build());

        mockMvc.perform(put("/api/auto-import/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "sourceUrl": "https://docs.google.com/spreadsheets/d/sheet_123-ABC/edit"
                                }
                                """))
                .andExpect(status().isOk());

        verify(autoImportService).updateSettings(any(AutoImportSettingsUpdateRequest.class), eq("admin"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void runNow_isAllowedForAdmin() throws Exception {
        given(autoImportService.runImport("admin"))
                .willReturn(AutoImportSettingsDto.builder()
                        .enabled(true)
                        .updatedBy("admin")
                        .build());

        mockMvc.perform(post("/api/auto-import/run"))
                .andExpect(status().isOk());

        verify(autoImportService).runImport("admin");
    }
}
