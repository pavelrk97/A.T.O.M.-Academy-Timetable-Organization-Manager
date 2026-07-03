package ru.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.client.IdentityClient;
import ru.client.ScheduleClient;
import ru.dto.MyDashboardDataDto;
import ru.dto.MyNotificationDto;
import ru.dto.ScheduleGridDto;
import ru.dto.UserDto;
import ru.dto.WorkloadCalendarDto;
import ru.model.NotificationType;
import ru.model.Role;
import ru.security.DownstreamAuthHeaderFactory;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeController.class)
@AutoConfigureMockMvc(addFilters = false)
class MeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IdentityClient identityClient;

    @MockBean
    private ScheduleClient scheduleClient;

    @MockBean
    private DownstreamAuthHeaderFactory authHeaderFactory;

    @Test
    void getDashboard_usesAggregatedScheduleEndpoint() throws Exception {
        String authorization = "Bearer test-token";
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);

        UserDto profile = UserDto.builder()
                .id(UUID.randomUUID())
                .username("mentor")
                .fullName("Mentor QA")
                .role(Role.INSTRUCTOR)
                .active(true)
                .canTeach(true)
                .build();

        MyDashboardDataDto dashboardData = MyDashboardDataDto.builder()
                .instructorSchedule(ScheduleGridDto.builder().dates(List.of(LocalDate.of(2026, 1, 12))).groups(List.of()).build())
                .workload(WorkloadCalendarDto.builder()
                        .instructorId(profile.getId())
                        .instructorName("Mentor QA")
                        .from(from)
                        .to(to)
                        .totalHours(6)
                        .days(List.of())
                        .build())
                .notifications(List.of(MyNotificationDto.builder()
                        .type(NotificationType.LESSON_ADDED)
                        .date(LocalDate.of(2026, 1, 12))
                        .message("lesson day")
                        .link("/api/me/schedule/instructor-grid?from=2026-01-12&to=2026-01-12")
                        .build()))
                .build();

        given(authHeaderFactory.bearerHeader(org.mockito.ArgumentMatchers.any())).willReturn(authorization);
        given(identityClient.getMyProfile(eq(authorization))).willReturn(profile);
        given(scheduleClient.getMyDashboard(eq(authorization), eq(from), eq(to))).willReturn(dashboardData);

        mockMvc.perform(get("/api/me/dashboard")
                        .with(jwt().jwt(jwt -> jwt
                                .tokenValue("test-token")
                                .subject("mentor")
                                .claim("roles", java.util.List.of("INSTRUCTOR"))))
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.username").value("mentor"))
                .andExpect(jsonPath("$.workload.totalHours").value(6))
                .andExpect(jsonPath("$.notifications[0].message").value("lesson day"));

        verify(identityClient).getMyProfile(authorization);
        verify(scheduleClient).getMyDashboard(authorization, from, to);
        verify(scheduleClient, never()).getMyInstructorScheduleGrid(authorization, from, to);
        verify(scheduleClient, never()).getMyWorkloadCalendar(authorization, from, to);
        verify(scheduleClient, never()).getMyNotifications(authorization, from, to);
    }

    @Test
    void exportMyWorkload_proxiesCsvResponse() throws Exception {
        String authorization = "Bearer test-token";
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 31);

        given(authHeaderFactory.bearerHeader(org.mockito.ArgumentMatchers.any())).willReturn(authorization);
        given(scheduleClient.exportMyWorkload(eq(authorization), eq(from), eq(to), eq(true)))
                .willReturn("csv-data".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/me/workload/export")
                        .with(jwt().jwt(jwt -> jwt
                                .tokenValue("test-token")
                                .subject("mentor")
                                .claim("roles", java.util.List.of("INSTRUCTOR"))))
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", "attachment; filename=\"my-workload-2026-05-01-2026-05-31.xlsx\""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes("csv-data".getBytes(StandardCharsets.UTF_8)));

        verify(scheduleClient).exportMyWorkload(authorization, from, to, true);
    }
}
