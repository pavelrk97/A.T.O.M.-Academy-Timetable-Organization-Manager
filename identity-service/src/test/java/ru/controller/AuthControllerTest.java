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
import ru.dto.LoginRequest;
import ru.dto.TokenResponse;
import ru.dto.UserDto;
import ru.model.Role;
import ru.security.InternalApiKeyAuthenticationFilter;
import ru.service.AuthService;
import ru.service.UserService;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, InternalApiKeyAuthenticationFilter.class})
@TestPropertySource(properties = {
        "internal.security.api-key=test-internal-key",
        "security.jwt.secret=test-jwt-secret-test-jwt-secret-123456"
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserService userService;

    @Test
    void login_isPublicAndReturnsToken() throws Exception {
        TokenResponse response = TokenResponse.builder()
                .tokenType("Bearer")
                .accessToken("jwt-token")
                .expiresAt(Instant.parse("2026-04-18T10:15:30Z"))
                .build();

        given(authService.login(any(LoginRequest.class), any())).willReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "admin123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").value("jwt-token"));
    }

    @Test
    void me_returnsCurrentUserForJwtAuthentication() throws Exception {
        UserDto user = UserDto.builder()
                .id(UUID.randomUUID())
                .username("mentor")
                .fullName("Mentor QA")
                .role(Role.INSTRUCTOR)
                .active(true)
                .canTeach(true)
                .build();

        given(userService.getCurrent(org.mockito.ArgumentMatchers.any())).willReturn(user);

        mockMvc.perform(get("/api/auth/me")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("mentor")
                                .claim("roles", java.util.List.of("INSTRUCTOR")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("mentor"))
                .andExpect(jsonPath("$.role").value("INSTRUCTOR"));
    }
}
