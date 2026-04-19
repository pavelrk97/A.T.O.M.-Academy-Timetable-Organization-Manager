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
import ru.client.IdentityClient;
import ru.config.SecurityConfig;
import ru.dto.LoginRequest;
import ru.dto.TokenResponse;
import ru.dto.UserDto;
import ru.model.Role;
import ru.security.DownstreamAuthHeaderFactory;

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
@Import(SecurityConfig.class)
@TestPropertySource(properties = "security.jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IdentityClient identityClient;

    @MockBean
    private DownstreamAuthHeaderFactory authHeaderFactory;

    @Test
    void login_isPublicAndProxiesToIdentityService() throws Exception {
        TokenResponse response = TokenResponse.builder()
                .tokenType("Bearer")
                .accessToken("jwt-token")
                .expiresAt(Instant.parse("2026-04-18T10:15:30Z"))
                .build();

        given(identityClient.login(any(LoginRequest.class))).willReturn(response);

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
    void me_acceptsJwtAuthenticatedUser() throws Exception {
        UserDto user = UserDto.builder()
                .id(UUID.randomUUID())
                .username("mentor")
                .fullName("Mentor QA")
                .role(Role.INSTRUCTOR)
                .active(true)
                .canTeach(true)
                .build();

        given(authHeaderFactory.bearerHeader(any())).willReturn("Bearer test-token");
        given(identityClient.getMe("Bearer test-token")).willReturn(user);

        mockMvc.perform(get("/api/auth/me")
                        .with(jwt().jwt(jwt -> jwt
                                .tokenValue("test-token")
                                .subject("mentor")
                                .claim("roles", java.util.List.of("INSTRUCTOR")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("mentor"))
                .andExpect(jsonPath("$.role").value("INSTRUCTOR"));
    }
}
