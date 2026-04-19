package ru.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import ru.config.SecurityConfig;
import ru.dto.ScheduleEntryDto;
import ru.model.LessonType;
import ru.service.LessonService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicScheduleController.class)
@Import(SecurityConfig.class)
class PublicScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LessonService lessonService;

    @Test
    void schedule_isPublicAndDelegatesAllFilters() throws Exception {
        UUID lessonId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();

        given(lessonService.getSchedule(
                "QA-42",
                instructorId,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31)
        )).willReturn(List.of(
                ScheduleEntryDto.builder()
                        .lessonId(lessonId)
                        .groupId(groupId)
                        .groupCode("QA-42")
                        .location("B201")
                        .date(LocalDate.of(2026, 5, 12))
                        .orderNumber(1)
                        .title("Signals")
                        .type(LessonType.LECTURE)
                        .durationHours(4)
                        .instructorIds(List.of(instructorId))
                        .instructorNames(List.of("Mentor QA"))
                        .build()
        ));

        mockMvc.perform(get("/api/public/schedule")
                        .param("groupCode", "QA-42")
                        .param("instructorId", instructorId.toString())
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lessonId").value(lessonId.toString()))
                .andExpect(jsonPath("$[0].groupCode").value("QA-42"))
                .andExpect(jsonPath("$[0].date").value("2026-05-12"))
                .andExpect(jsonPath("$[0].type").value("LECTURE"))
                .andExpect(jsonPath("$[0].instructorIds[0]").value(instructorId.toString()));

        verify(lessonService).getSchedule(
                eq("QA-42"),
                eq(instructorId),
                eq(LocalDate.of(2026, 5, 1)),
                eq(LocalDate.of(2026, 5, 31))
        );
    }
}
