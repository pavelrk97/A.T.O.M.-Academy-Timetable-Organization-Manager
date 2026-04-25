package ru.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import ru.config.SecurityConfig;
import ru.dto.GroupDto;
import ru.service.GroupService;

import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GroupController.class)
@Import(SecurityConfig.class)
class GroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GroupService groupService;

    @Test
    @WithMockUser(username = "mentor", roles = "INSTRUCTOR")
    void getGroups_returnsCatalogForAuthenticatedUser() throws Exception {
        UUID groupId = UUID.randomUUID();
        given(groupService.getAll()).willReturn(List.of(
                GroupDto.builder()
                        .id(groupId)
                        .code("QA-42")
                        .location("B201")
                        .course("4A")
                        .days(List.of())
                        .build()
        ));

        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(groupId.toString()))
                .andExpect(jsonPath("$[0].code").value("QA-42"))
                .andExpect(jsonPath("$[0].location").value("B201"));
    }

    @Test
    void getGroups_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "mentor", roles = "INSTRUCTOR")
    void createGroup_isForbiddenForInstructor() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "QA-43",
                                  "location": "B202",
                                  "course": "4A",
                                  "days": []
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(groupService, never()).create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @WithMockUser(username = "mentor", roles = {"INSTRUCTOR", "EDITOR"})
    void createGroup_isAllowedForEditorCapableInstructor() throws Exception {
        UUID groupId = UUID.randomUUID();
        given(groupService.create(org.mockito.ArgumentMatchers.any(GroupDto.class))).willReturn(
                GroupDto.builder()
                        .id(groupId)
                        .code("QA-43")
                        .location("B202")
                        .course("4A")
                        .days(List.of())
                        .build()
        );

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "QA-43",
                                  "location": "B202",
                                  "course": "4A",
                                  "days": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(groupId.toString()))
                .andExpect(jsonPath("$.code").value("QA-43"));
    }
}
