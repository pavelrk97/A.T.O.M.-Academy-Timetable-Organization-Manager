package ru.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.config.SecurityConfig;
import ru.dto.ImportedInstructorSyncRequest;
import ru.dto.InternalUserDetailsDto;
import ru.model.Role;
import ru.security.InternalApiKeyAuthenticationFilter;
import ru.service.UserService;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalUserController.class)
@Import({SecurityConfig.class, InternalApiKeyAuthenticationFilter.class})
@TestPropertySource(properties = "internal.security.api-key=test-internal-key")
class InternalUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void getByUsername_rejectsMissingInternalApiKey() throws Exception {
        mockMvc.perform(get("/internal/users/by-username/mentor"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getByUsername_rejectsWrongInternalApiKey() throws Exception {
        mockMvc.perform(get("/internal/users/by-username/mentor")
                        .header("X-Internal-Api-Key", "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getByUsername_returnsTrimmedInternalPayloadForValidInternalApiKey() throws Exception {
        when(userService.getInternalByUsername("mentor")).thenReturn(InternalUserDetailsDto.builder()
                .id(UUID.randomUUID())
                .username("mentor")
                .password("encoded-pass")
                .fullName("Mentor QA")
                .role(Role.INSTRUCTOR)
                .active(true)
                .build());

        mockMvc.perform(get("/internal/users/by-username/mentor")
                        .header("X-Internal-Api-Key", "test-internal-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("mentor"))
                .andExpect(jsonPath("$.fullName").value("Mentor QA"))
                .andExpect(jsonPath("$.role").value("INSTRUCTOR"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.position").doesNotExist())
                .andExpect(jsonPath("$.department").doesNotExist());
    }

    @Test
    void syncImportedInstructors_requiresInternalApiKey() throws Exception {
        mockMvc.perform(post("/internal/users/sync-instructors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullNames": ["Расписенко", "Петров"]
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void syncImportedInstructors_acceptsValidInternalApiKey() throws Exception {
        mockMvc.perform(post("/internal/users/sync-instructors")
                        .header("X-Internal-Api-Key", "test-internal-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullNames": ["Расписенко", "Петров"]
                                }
                                """))
                .andExpect(status().isOk());
    }
}
