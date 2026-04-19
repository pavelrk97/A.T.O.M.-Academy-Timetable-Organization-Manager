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
import ru.dto.MyDashboardDataDto;
import ru.dto.MyNotificationDto;
import ru.dto.ScheduleGridDayCellDto;
import ru.dto.ScheduleGridDto;
import ru.dto.ScheduleGridGroupRowDto;
import ru.dto.WorkloadCalendarDayDto;
import ru.dto.WorkloadCalendarDto;
import ru.service.MyCabinetService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.model.NotificationType.LESSON_ADDED;

@WebMvcTest(MeController.class)
@Import(SecurityConfig.class)
class MeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MyCabinetService myCabinetService;

    @Test
    void fullScheduleGrid_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/me/schedule/grid"))
                .andExpect(status().isUnauthorized());

        verify(myCabinetService, never()).getFullScheduleGrid(any(), any());
    }

    @Test
    @WithMockUser(username = "mentor", roles = "INSTRUCTOR")
    void fullScheduleGrid_returnsGridForAuthenticatedUser() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID dayId = UUID.randomUUID();
        given(myCabinetService.getFullScheduleGrid(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .willReturn(ScheduleGridDto.builder()
                        .dates(List.of(LocalDate.of(2026, 1, 12)))
                        .groups(List.of(
                                ScheduleGridGroupRowDto.builder()
                                        .groupId(groupId)
                                        .groupCode("QA-42")
                                        .location("B201")
                                        .course(4)
                                        .days(List.of(
                                                ScheduleGridDayCellDto.builder()
                                                        .dayId(dayId)
                                                        .date(LocalDate.of(2026, 1, 12))
                                                        .lessons(List.of())
                                                        .build()
                                        ))
                                        .build()
                        ))
                        .build());

        mockMvc.perform(get("/api/me/schedule/grid")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dates[0]").value("2026-01-12"))
                .andExpect(jsonPath("$.groups[0].groupId").value(groupId.toString()))
                .andExpect(jsonPath("$.groups[0].groupCode").value("QA-42"))
                .andExpect(jsonPath("$.groups[0].days[0].dayId").value(dayId.toString()));

        verify(myCabinetService).getFullScheduleGrid(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
    }

    @Test
    @WithMockUser(username = "mentor", roles = "INSTRUCTOR")
    void instructorGrid_passesAuthenticationAndDateRange() throws Exception {
        given(myCabinetService.getInstructorScheduleGrid(any(Authentication.class), eq(LocalDate.of(2026, 2, 1)), eq(LocalDate.of(2026, 2, 28))))
                .willReturn(ScheduleGridDto.builder()
                        .dates(List.of(LocalDate.of(2026, 2, 10)))
                        .groups(List.of())
                        .build());

        mockMvc.perform(get("/api/me/schedule/instructor-grid")
                        .param("from", "2026-02-01")
                        .param("to", "2026-02-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dates[0]").value("2026-02-10"));

        verify(myCabinetService).getInstructorScheduleGrid(any(Authentication.class), eq(LocalDate.of(2026, 2, 1)), eq(LocalDate.of(2026, 2, 28)));
    }

    @Test
    @WithMockUser(username = "mentor", roles = "INSTRUCTOR")
    void notifications_returnsUserFeed() throws Exception {
        UUID dayId = UUID.randomUUID();
        given(myCabinetService.getMyNotifications(any(Authentication.class), eq(LocalDate.of(2026, 3, 1)), eq(LocalDate.of(2026, 3, 31))))
                .willReturn(List.of(
                        MyNotificationDto.builder()
                                .type(LESSON_ADDED)
                                .dayId(dayId)
                                .date(LocalDate.of(2026, 3, 12))
                                .message("Smoke lesson added")
                                .link("/cabinet?tab=schedule")
                                .build()
                ));

        mockMvc.perform(get("/api/me/notifications")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("LESSON_ADDED"))
                .andExpect(jsonPath("$[0].dayId").value(dayId.toString()))
                .andExpect(jsonPath("$[0].message").value("Smoke lesson added"));

        verify(myCabinetService).getMyNotifications(any(Authentication.class), eq(LocalDate.of(2026, 3, 1)), eq(LocalDate.of(2026, 3, 31)));
    }

    @Test
    @WithMockUser(username = "mentor", roles = "INSTRUCTOR")
    void dashboard_returnsAggregatedWidgets() throws Exception {
        UUID instructorId = UUID.randomUUID();
        given(myCabinetService.getDashboard(any(Authentication.class), eq(LocalDate.of(2026, 4, 1)), eq(LocalDate.of(2026, 4, 30))))
                .willReturn(MyDashboardDataDto.builder()
                        .instructorSchedule(ScheduleGridDto.builder()
                                .dates(List.of(LocalDate.of(2026, 4, 7)))
                                .groups(List.of())
                                .build())
                        .workload(WorkloadCalendarDto.builder()
                                .instructorId(instructorId)
                                .instructorName("Mentor QA")
                                .from(LocalDate.of(2026, 4, 1))
                                .to(LocalDate.of(2026, 4, 30))
                                .totalHours(6)
                                .days(List.of(
                                        WorkloadCalendarDayDto.builder()
                                                .dayId(UUID.randomUUID())
                                                .date(LocalDate.of(2026, 4, 7))
                                                .totalHours(6)
                                                .lessons(List.of())
                                                .build()
                                ))
                                .build())
                        .notifications(List.of())
                        .build());

        mockMvc.perform(get("/api/me/dashboard")
                        .param("from", "2026-04-01")
                        .param("to", "2026-04-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instructorSchedule.dates[0]").value("2026-04-07"))
                .andExpect(jsonPath("$.workload.instructorId").value(instructorId.toString()))
                .andExpect(jsonPath("$.workload.totalHours").value(6))
                .andExpect(jsonPath("$.notifications").isArray());

        verify(myCabinetService).getDashboard(any(Authentication.class), eq(LocalDate.of(2026, 4, 1)), eq(LocalDate.of(2026, 4, 30)));
    }
}
