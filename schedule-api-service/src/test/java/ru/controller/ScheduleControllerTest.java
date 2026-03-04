package ru.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.client.ScheduleClient;
import ru.controller.ScheduleController;
import ru.dto.GroupDto;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScheduleController.class)
class ScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScheduleClient scheduleClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/groups - returns list of groups")
    void getAll_shouldReturnList() throws Exception {

        UUID id = UUID.randomUUID();

        GroupDto dto = new GroupDto();
        dto.setId(id);
        dto.setName("Java-21");

        Mockito.when(scheduleClient.getAllGroups())
                .thenReturn(List.of(dto));

        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].name").value("Java-21"));

        Mockito.verify(scheduleClient, times(1)).getAllGroups();
    }

    @Test
    @DisplayName("GET /api/groups/{id} - returns group by id")
    void getById_shouldReturnGroup() throws Exception {

        UUID id = UUID.randomUUID();

        GroupDto dto = new GroupDto();
        dto.setId(id);
        dto.setName("Spring-17");

        Mockito.when(scheduleClient.getGroupById(eq(id)))
                .thenReturn(dto);

        mockMvc.perform(get("/api/groups/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Spring-17"));

        Mockito.verify(scheduleClient, times(1)).getGroupById(id);
    }

    @Test
    @DisplayName("POST /api/groups - creates group")
    void create_shouldReturnCreatedGroup() throws Exception {

        UUID id = UUID.randomUUID();

        GroupDto request = new GroupDto();
        request.setName("New-Group");

        GroupDto response = new GroupDto();
        response.setId(id);
        response.setName("New-Group");

        Mockito.when(scheduleClient.createGroup(any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("New-Group"));

        Mockito.verify(scheduleClient, times(1)).createGroup(any());
    }

    @Test
    @DisplayName("DELETE /api/groups/{id} - deletes group")
    void delete_shouldReturnOk() throws Exception {

        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/groups/{id}", id))
                .andExpect(status().isOk());

        Mockito.verify(scheduleClient, times(1)).deleteGroup(id);
    }
}