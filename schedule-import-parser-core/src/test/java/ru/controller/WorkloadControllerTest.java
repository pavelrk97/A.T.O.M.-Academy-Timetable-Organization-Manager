package ru.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import ru.config.SecurityConfig;
import ru.dto.WorkloadDto;
import ru.service.LessonService;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkloadController.class)
@Import(SecurityConfig.class)
class WorkloadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LessonService lessonService;

    @Test
    void workload_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/workload"))
                .andExpect(status().isUnauthorized());

        verify(lessonService, never()).getWorkload(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void workload_returnsRowsForAuthenticatedUser() throws Exception {
        UUID instructorId = UUID.randomUUID();
        given(lessonService.getWorkload(
                eq(instructorId),
                org.mockito.ArgumentMatchers.<java.util.List<UUID>>any(),
                eq(LocalDate.of(2026, 6, 1)),
                eq(LocalDate.of(2026, 6, 30)),
                any(Authentication.class)
        )).willReturn(List.of(
                WorkloadDto.builder()
                        .instructorId(instructorId)
                        .instructorName("Mentor QA")
                        .totalHours(14)
                        .build()
        ));

        mockMvc.perform(get("/api/workload")
                        .param("instructorId", instructorId.toString())
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].instructorId").value(instructorId.toString()))
                .andExpect(jsonPath("$[0].instructorName").value("Mentor QA"))
                .andExpect(jsonPath("$[0].totalHours").value(14));

        verify(lessonService).getWorkload(
                eq(instructorId),
                org.mockito.ArgumentMatchers.<java.util.List<UUID>>any(),
                eq(LocalDate.of(2026, 6, 1)),
                eq(LocalDate.of(2026, 6, 30)),
                any(Authentication.class)
        );
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void export_returnsExcelAttachmentForAdmin() throws Exception {
        UUID instructorId = UUID.randomUUID();
        given(lessonService.exportWorkloadExcel(
                eq(instructorId),
                org.mockito.ArgumentMatchers.<java.util.List<UUID>>any(),
                eq("расп"),
                eq(LocalDate.of(2026, 6, 1)),
                eq(LocalDate.of(2026, 6, 30)),
                any(Authentication.class)
        )).willReturn("csv-data".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/workload/export")
                        .param("instructorId", instructorId.toString())
                        .param("instructorQuery", "расп")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", "attachment; filename=\"workload-single-2026-06-01-2026-06-30.xlsx\""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes("csv-data".getBytes(StandardCharsets.UTF_8)));

        verify(lessonService).exportWorkloadExcel(
                eq(instructorId),
                org.mockito.ArgumentMatchers.<java.util.List<UUID>>any(),
                eq("расп"),
                eq(LocalDate.of(2026, 6, 1)),
                eq(LocalDate.of(2026, 6, 30)),
                any(Authentication.class)
        );
    }
}
