package ru.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.client.ScheduleClient;
import ru.config.SecurityConfig;
import ru.dto.AutoImportSettingsDto;
import ru.dto.AutoImportSettingsUpdateRequest;
import ru.security.DownstreamAuthHeaderFactory;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AutoImportController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "security.jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class AutoImportControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScheduleClient scheduleClient;

    @MockBean
    private DownstreamAuthHeaderFactory authHeaderFactory;

    @Test
    void updateSettings_isForbiddenForEditor() throws Exception {
        mockMvc.perform(put("/api/auto-import/settings")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .tokenValue("editor-token")
                                        .subject("editor")
                                        .claim("roles", List.of("EDITOR")))
                                .authorities(new SimpleGrantedAuthority("ROLE_EDITOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "sourceUrl": "https://docs.google.com/spreadsheets/d/sheet_123-ABC/edit"
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(scheduleClient, never()).updateAutoImportSettings(any(), any(AutoImportSettingsUpdateRequest.class));
    }

    @Test
    void runNow_isForbiddenForEditor() throws Exception {
        mockMvc.perform(post("/api/auto-import/run")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .tokenValue("editor-token")
                                        .subject("editor")
                                        .claim("roles", List.of("EDITOR")))
                                .authorities(new SimpleGrantedAuthority("ROLE_EDITOR"))))
                .andExpect(status().isForbidden());

        verify(scheduleClient, never()).runAutoImport(any());
    }

    @Test
    void updateSettings_isAllowedForAdmin() throws Exception {
        given(authHeaderFactory.bearerHeader(any())).willReturn("Bearer admin-token");
        given(scheduleClient.updateAutoImportSettings(any(), any(AutoImportSettingsUpdateRequest.class)))
                .willReturn(AutoImportSettingsDto.builder()
                        .enabled(true)
                        .sourceUrl("https://docs.google.com/spreadsheets/d/sheet_123-ABC/edit")
                        .updatedBy("admin")
                        .build());

        mockMvc.perform(put("/api/auto-import/settings")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .tokenValue("admin-token")
                                        .subject("admin")
                                        .claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "sourceUrl": "https://docs.google.com/spreadsheets/d/sheet_123-ABC/edit"
                                }
                                """))
                .andExpect(status().isOk());

        verify(scheduleClient).updateAutoImportSettings(any(), any(AutoImportSettingsUpdateRequest.class));
    }

    @Test
    void runNow_isAllowedForAdmin() throws Exception {
        given(authHeaderFactory.bearerHeader(any())).willReturn("Bearer admin-token");
        given(scheduleClient.runAutoImport("Bearer admin-token"))
                .willReturn(AutoImportSettingsDto.builder()
                        .enabled(true)
                        .updatedBy("admin")
                        .build());

        mockMvc.perform(post("/api/auto-import/run")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .tokenValue("admin-token")
                                        .subject("admin")
                                        .claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());

        verify(scheduleClient).runAutoImport("Bearer admin-token");
    }
}
