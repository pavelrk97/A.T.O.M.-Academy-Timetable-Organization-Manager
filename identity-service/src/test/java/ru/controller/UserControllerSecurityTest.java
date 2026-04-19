package ru.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.config.SecurityConfig;
import ru.dto.UserDto;
import ru.model.Role;
import ru.security.InternalApiKeyAuthenticationFilter;
import ru.service.UserService;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, InternalApiKeyAuthenticationFilter.class})
@TestPropertySource(properties = {
        "internal.security.api-key=test-internal-key",
        "security.jwt.secret=test-jwt-secret-test-jwt-secret-123456"
})
class UserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    @WithMockUser(username = "editor", roles = "EDITOR")
    void editorCanReadUsersCatalog() throws Exception {
        when(userService.getAll()).thenReturn(List.of(UserDto.builder()
                .id(UUID.randomUUID())
                .username("instructor")
                .fullName("Main Instructor")
                .displayName("Raspisenko")
                .role(Role.INSTRUCTOR)
                .active(true)
                .canTeach(true)
                .build()));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("instructor"))
                .andExpect(jsonPath("$[0].displayName").value("Raspisenko"));
    }

    @Test
    @WithMockUser(username = "instructor", roles = {"INSTRUCTOR", "EDITOR"})
    void instructorWithEditorRoleCanReadUsersCatalog() throws Exception {
        when(userService.getAll()).thenReturn(List.of(UserDto.builder()
                .id(UUID.randomUUID())
                .username("editor")
                .fullName("Schedule Editor")
                .role(Role.EDITOR)
                .active(true)
                .canTeach(true)
                .build()));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("editor"))
                .andExpect(jsonPath("$[0].role").value("EDITOR"));
    }

    @Test
    @WithMockUser(username = "mentor", roles = "INSTRUCTOR")
    void plainInstructorCannotReadUsersCatalog() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "editor", roles = "EDITOR")
    void editorCannotCreateUsers() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "new-user",
                                  "password": "pass123",
                                  "fullName": "New User",
                                  "role": "EDITOR"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanReadUsersCatalog() throws Exception {
        when(userService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk());
    }
}
