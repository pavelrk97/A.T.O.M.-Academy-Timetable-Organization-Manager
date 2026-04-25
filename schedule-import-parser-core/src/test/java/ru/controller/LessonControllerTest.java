package ru.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import ru.config.SecurityConfig;
import ru.dto.ChangeLogDto;
import ru.dto.DayDto;
import ru.dto.GroupDto;
import ru.dto.LessonDto;
import ru.model.ChangeAction;
import ru.model.LessonType;
import ru.service.LessonService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LessonController.class)
@Import(SecurityConfig.class)
class LessonControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LessonService lessonService;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createLesson_returnsSavedLessonForAdmin() throws Exception {
        UUID lessonId = UUID.randomUUID();
        UUID dayId = UUID.randomUUID();

        given(lessonService.create(any(LessonDto.class), any(Authentication.class))).willReturn(
                LessonDto.builder()
                        .id(lessonId)
                        .version(0L)
                        .dayId(dayId)
                        .orderNumber(1)
                        .title("APCS intro")
                        .durationHours(4)
                        .type(LessonType.LECTURE)
                        .instructorIds(List.of(UUID.randomUUID()))
                        .instructorNames(List.of("Mentor QA"))
                        .build()
        );

        mockMvc.perform(post("/api/lessons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dayId": "%s",
                                  "orderNumber": 1,
                                  "title": "APCS intro",
                                  "durationHours": 4,
                                  "type": "LECTURE",
                                  "instructorIds": ["%s"]
                                }
                                """.formatted(dayId, UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(lessonId.toString()))
                .andExpect(jsonPath("$.title").value("APCS intro"))
                .andExpect(jsonPath("$.type").value("LECTURE"));

        verify(lessonService).create(
                argThat(dto -> "APCS intro".equals(dto.getTitle()) && dayId.equals(dto.getDayId())),
                any(Authentication.class)
        );
    }

    @Test
    @WithMockUser(username = "mentor", roles = "INSTRUCTOR")
    void createLesson_isForbiddenForInstructorAtHttpLevel() throws Exception {
        mockMvc.perform(post("/api/lessons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dayId": "%s",
                                  "orderNumber": 1,
                                  "title": "APCS intro",
                                  "durationHours": 4,
                                  "type": "LECTURE",
                                  "instructorIds": []
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());

        verify(lessonService, never()).create(any(LessonDto.class), any(Authentication.class));
    }

    @Test
    @WithMockUser(username = "instructor", roles = {"INSTRUCTOR", "EDITOR"})
    void createLesson_returnsSavedLessonForInstructorWithEditorRole() throws Exception {
        UUID lessonId = UUID.randomUUID();
        UUID dayId = UUID.randomUUID();

        given(lessonService.create(any(LessonDto.class), any(Authentication.class))).willReturn(
                LessonDto.builder()
                        .id(lessonId)
                        .version(0L)
                        .dayId(dayId)
                        .orderNumber(3)
                        .title("Smoke lesson")
                        .durationHours(2)
                        .type(LessonType.ASSESSMENT)
                        .build()
        );

        mockMvc.perform(post("/api/lessons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dayId": "%s",
                                  "orderNumber": 3,
                                  "title": "Smoke lesson",
                                  "durationHours": 2,
                                  "type": "ASSESSMENT",
                                  "instructorIds": []
                                }
                                """.formatted(dayId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(lessonId.toString()))
                .andExpect(jsonPath("$.title").value("Smoke lesson"))
                .andExpect(jsonPath("$.type").value("ASSESSMENT"));

        verify(lessonService).create(
                argThat(dto -> "Smoke lesson".equals(dto.getTitle()) && dayId.equals(dto.getDayId())),
                any(Authentication.class)
        );
    }

    @Test
    @WithMockUser(username = "editor", roles = "EDITOR")
    void syncDay_returnsUpdatedGroupForEditor() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID dayId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();

        given(lessonService.syncDay(any(), any(Authentication.class))).willReturn(
                GroupDto.builder()
                        .id(groupId)
                        .code("QA-101")
                        .days(List.of(
                                DayDto.builder()
                                        .id(dayId)
                                        .date(java.time.LocalDate.of(2026, 4, 20))
                                        .lessons(List.of(
                                                LessonDto.builder()
                                                        .id(lessonId)
                                                        .version(1L)
                                                        .orderNumber(1)
                                                        .title("Day sync lesson")
                                                        .durationHours(2)
                                                        .type(LessonType.LECTURE)
                                                        .build()
                                        ))
                                        .build()
                        ))
                        .build()
        );

        mockMvc.perform(post("/api/lessons/day-sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupId": "%s",
                                  "date": "2026-04-20",
                                  "ensureDay": true,
                                  "lessons": [
                                    {
                                      "orderNumber": 1,
                                      "title": "Day sync lesson",
                                      "durationHours": 2,
                                      "type": "LECTURE",
                                      "instructorIds": []
                                    }
                                  ]
                                }
                                """.formatted(groupId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(groupId.toString()))
                .andExpect(jsonPath("$.days[0].id").value(dayId.toString()))
                .andExpect(jsonPath("$.days[0].lessons[0].id").value(lessonId.toString()))
                .andExpect(jsonPath("$.days[0].lessons[0].title").value("Day sync lesson"));

        verify(lessonService).syncDay(
                argThat(dto -> groupId.equals(dto.getGroupId())
                        && java.time.LocalDate.of(2026, 4, 20).equals(dto.getDate())
                        && Boolean.TRUE.equals(dto.getEnsureDay())
                        && dto.getLessons().size() == 1),
                any(Authentication.class)
        );
    }

    @Test
    @WithMockUser(username = "editor", roles = "EDITOR")
    void updateLesson_returnsSavedLessonForEditor() throws Exception {
        UUID lessonId = UUID.randomUUID();
        UUID dayId = UUID.randomUUID();

        given(lessonService.update(eq(lessonId), any(LessonDto.class), any(Authentication.class))).willReturn(
                LessonDto.builder()
                        .id(lessonId)
                        .version(1L)
                        .dayId(dayId)
                        .orderNumber(2)
                        .title("APCS advanced")
                        .durationHours(6)
                        .type(LessonType.SELF_STUDY)
                        .build()
        );

        mockMvc.perform(put("/api/lessons/{id}", lessonId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": 0,
                                  "dayId": "%s",
                                  "orderNumber": 2,
                                  "title": "APCS advanced",
                                  "durationHours": 6,
                                  "type": "SELF_STUDY",
                                  "instructorIds": []
                                }
                                """.formatted(dayId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(lessonId.toString()))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.title").value("APCS advanced"));

        verify(lessonService).update(
                eq(lessonId),
                argThat(dto -> Long.valueOf(0L).equals(dto.getVersion()) && "APCS advanced".equals(dto.getTitle())),
                any(Authentication.class)
        );
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deleteLesson_passesVersionToService() throws Exception {
        UUID lessonId = UUID.randomUUID();

        mockMvc.perform(delete("/api/lessons/{id}", lessonId)
                        .param("version", "7"))
                .andExpect(status().isOk());

        verify(lessonService).delete(eq(lessonId), eq(7L), any(Authentication.class));
    }

    @Test
    @WithMockUser(username = "mentor", roles = "INSTRUCTOR")
    void getHistory_returnsAuditTrailForAuthenticatedUser() throws Exception {
        UUID lessonId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        given(lessonService.getHistory(lessonId)).willReturn(List.of(
                ChangeLogDto.builder()
                        .id(logId)
                        .entityType("LESSON")
                        .entityId(lessonId)
                        .action(ChangeAction.UPDATED)
                        .changedBy("editor")
                        .changedAt(LocalDateTime.of(2026, 1, 12, 10, 30))
                        .comment("Lesson updated")
                        .build()
        ));

        mockMvc.perform(get("/api/lessons/{id}/history", lessonId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(logId.toString()))
                .andExpect(jsonPath("$[0].action").value("UPDATED"))
                .andExpect(jsonPath("$[0].changedBy").value("editor"))
                .andExpect(jsonPath("$[0].comment").value("Lesson updated"));
    }
}
