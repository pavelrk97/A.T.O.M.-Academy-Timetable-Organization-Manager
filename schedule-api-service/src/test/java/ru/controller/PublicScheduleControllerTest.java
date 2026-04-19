package ru.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.client.ScheduleClient;
import ru.dto.ScheduleEntryDto;
import ru.model.LessonType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicScheduleController.class)
@AutoConfigureMockMvc(addFilters = false)
class PublicScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScheduleClient scheduleClient;

    @Test
    void getSchedule_passesIsoDatesToScheduleService() throws Exception {
        LocalDate day = LocalDate.of(2026, 1, 5);
        ScheduleEntryDto response = ScheduleEntryDto.builder()
                .lessonId(UUID.randomUUID())
                .groupCode("гр.6 ()")
                .date(day)
                .title("Lesson")
                .type(LessonType.LECTURE)
                .durationHours(3)
                .build();

        given(scheduleClient.getPublicSchedule(eq("гр.6 ()"), isNull(), eq(day), eq(day)))
                .willReturn(List.of(response));

        mockMvc.perform(get("/api/public/schedule")
                        .param("groupCode", "гр.6 ()")
                        .param("from", "2026-01-05")
                        .param("to", "2026-01-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupCode").value("гр.6 ()"))
                .andExpect(jsonPath("$[0].date").value("2026-01-05"));

        // если тут даты приехали не как ISO, снова словим тот самый 500 через gateway
        verify(scheduleClient).getPublicSchedule("гр.6 ()", null, day, day);
    }
}
