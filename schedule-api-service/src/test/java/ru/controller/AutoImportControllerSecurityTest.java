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
    void editorCannotRunAutoImport() throws Exception {
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
    void editorCannotUpdateAutoImportSettings() throws Exception {
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
                                  "sourceUrl": "https://example.test/schedule.csv"
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(scheduleClient, never()).updateAutoImportSettings(any(), any());
    }

    @Test
    void adminCanRunAutoImport() throws Exception {
        given(authHeaderFactory.bearerHeader(any())).willReturn("Bearer admin-token");
        given(scheduleClient.runAutoImport("Bearer admin-token"))
                .willReturn(AutoImportSettingsDto.builder().enabled(true).lastStatus("OK").build());

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
