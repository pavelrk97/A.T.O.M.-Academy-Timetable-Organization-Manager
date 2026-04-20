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
import ru.dto.DayDto;
import ru.dto.GroupDto;
import ru.security.DownstreamAuthHeaderFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LessonController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "security.jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class LessonControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScheduleClient scheduleClient;

    @MockBean
    private DownstreamAuthHeaderFactory authHeaderFactory;

    @Test
    void editorCanSyncWholeDayThroughGateway() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID dayId = UUID.randomUUID();
        given(authHeaderFactory.bearerHeader(any())).willReturn("Bearer test-token");
        given(scheduleClient.syncLessonDay(eq("Bearer test-token"), any()))
                .willReturn(GroupDto.builder()
                        .id(groupId)
                        .code("QA-101")
                        .days(List.of(
                                DayDto.builder()
                                        .id(dayId)
                                        .date(LocalDate.of(2026, 4, 20))
                                        .lessons(List.of())
                                        .build()
                        ))
                        .build());

        mockMvc.perform(post("/api/lessons/day-sync")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .tokenValue("test-token")
                                        .subject("editor")
                                        .claim("roles", List.of("EDITOR")))
                                .authorities(new SimpleGrantedAuthority("ROLE_EDITOR")))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupId": "%s",
                                  "date": "2026-04-20",
                                  "ensureDay": true,
                                  "lessons": []
                                }
                                """.formatted(groupId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(groupId.toString()))
                .andExpect(jsonPath("$.days[0].id").value(dayId.toString()));

        verify(scheduleClient).syncLessonDay(eq("Bearer test-token"), any());
    }
}
