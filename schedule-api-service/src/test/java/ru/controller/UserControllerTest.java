package ru.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.client.IdentityClient;
import ru.config.SecurityConfig;
import ru.dto.UserDto;
import ru.model.Role;
import ru.security.DownstreamAuthHeaderFactory;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "security.jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IdentityClient identityClient;

    @MockBean
    private DownstreamAuthHeaderFactory authHeaderFactory;

    @Test
    void instructorWithEditorRoleCanReadUsersCatalog() throws Exception {
        given(authHeaderFactory.bearerHeader(any())).willReturn("Bearer test-token");
        given(identityClient.getUsers("Bearer test-token")).willReturn(List.of(
                UserDto.builder()
                        .id(UUID.randomUUID())
                        .username("editor")
                        .fullName("Schedule Editor")
                        .role(Role.EDITOR)
                        .active(true)
                        .canTeach(true)
                        .build()
        ));

        mockMvc.perform(get("/api/users")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .tokenValue("test-token")
                                        .subject("instructor")
                                        .claim("roles", List.of("INSTRUCTOR", "EDITOR")))
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_INSTRUCTOR"),
                                        new SimpleGrantedAuthority("ROLE_EDITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("editor"))
                .andExpect(jsonPath("$[0].role").value("EDITOR"));
    }

    @Test
    void plainInstructorCannotReadUsersCatalog() throws Exception {
        mockMvc.perform(get("/api/users")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .tokenValue("test-token")
                                        .subject("instructor")
                                        .claim("roles", List.of("INSTRUCTOR")))
                                .authorities(new SimpleGrantedAuthority("ROLE_INSTRUCTOR"))))
                .andExpect(status().isForbidden());
    }
}
