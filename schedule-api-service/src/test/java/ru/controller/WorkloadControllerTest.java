package ru.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.client.ScheduleClient;
import ru.config.SecurityConfig;
import ru.dto.WorkloadDto;
import ru.security.DownstreamAuthHeaderFactory;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkloadController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "security.jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class WorkloadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScheduleClient scheduleClient;

    @MockBean
    private DownstreamAuthHeaderFactory authHeaderFactory;

    @Test
    void authenticatedEditorGetsWorkloadRows() throws Exception {
        UUID instructorId = UUID.randomUUID();
        given(authHeaderFactory.bearerHeader(any())).willReturn("Bearer test-token");
        given(scheduleClient.getWorkload("Bearer test-token", instructorId, null,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
                .willReturn(List.of(
                        WorkloadDto.builder()
                                .instructorId(instructorId)
                                .instructorName("Mentor QA")
                                .totalHours(14)
                                .build()
                ));

        mockMvc.perform(get("/api/workload")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .tokenValue("test-token")
                                        .subject("editor")
                                        .claim("roles", List.of("EDITOR")))
                                .authorities(new SimpleGrantedAuthority("ROLE_EDITOR")))
                        .param("instructorId", instructorId.toString())
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].instructorName").value("Mentor QA"))
                .andExpect(jsonPath("$[0].totalHours").value(14));

        verify(scheduleClient).getWorkload("Bearer test-token", instructorId, null,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
    }

    @Test
    void adminCanExportWorkloadCsv() throws Exception {
        UUID instructorId = UUID.randomUUID();
        given(authHeaderFactory.bearerHeader(any())).willReturn("Bearer test-token");
        given(scheduleClient.exportWorkload("Bearer test-token", instructorId, null, "расп",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), true))
                .willReturn("csv-data".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/workload/export")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .tokenValue("test-token")
                                        .subject("admin")
                                        .claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .param("instructorId", instructorId.toString())
                        .param("instructorQuery", "расп")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", "attachment; filename=\"workload-single-2026-06-01-2026-06-30.xlsx\""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes("csv-data".getBytes(StandardCharsets.UTF_8)));

        verify(scheduleClient).exportWorkload("Bearer test-token", instructorId, null, "расп",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), true);
    }

    @Test
    void editorCanExportWorkloadCsv() throws Exception {
        // EDITOR теперь имеет доступ к экспорту workload — это прокидывается далее
        // в schedule-service, где LessonService так же пропускает ADMIN/EDITOR/editorAccess.
        given(authHeaderFactory.bearerHeader(any())).willReturn("Bearer editor-token");
        given(scheduleClient.exportWorkload("Bearer editor-token", null, null, null, null, null, true))
                .willReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/workload/export")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .tokenValue("editor-token")
                                        .subject("editor")
                                        .claim("roles", List.of("EDITOR")))
                                .authorities(new SimpleGrantedAuthority("ROLE_EDITOR"))))
                .andExpect(status().isOk());
    }

    @Test
    void instructorCanExportWorkloadCsv() throws Exception {
        given(authHeaderFactory.bearerHeader(any())).willReturn("Bearer instructor-token");
        given(scheduleClient.exportWorkload("Bearer instructor-token", null, null, null, null, null, true))
                .willReturn(new byte[]{4, 5, 6});

        mockMvc.perform(get("/api/workload/export")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .tokenValue("instructor-token")
                                        .subject("instructor")
                                        .claim("roles", List.of("INSTRUCTOR")))
                                .authorities(new SimpleGrantedAuthority("ROLE_INSTRUCTOR"))))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(new byte[]{4, 5, 6}));

        verify(scheduleClient).exportWorkload("Bearer instructor-token", null, null, null, null, null, true);
    }
}
