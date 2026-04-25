package ru.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import ru.model.Role;
import ru.model.User;

import javax.crypto.SecretKey;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    @Test
    void generateAccessToken_addsEditorRoleForInstructorWithEditorAccess() {
        SecretKey secretKey = JwtService.secretKey("test-jwt-secret-test-jwt-secret-123456");
        JwtEncoder jwtEncoder = JwtService.jwtEncoder(secretKey);
        JwtDecoder jwtDecoder = JwtService.jwtDecoder(secretKey);
        JwtService jwtService = new JwtService(jwtEncoder, "atom-identity", 60);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("instructor");
        user.setFullName("Main Instructor");
        user.setDisplayName("Расписенко");
        user.setRole(Role.INSTRUCTOR);
        user.setActive(true);
        user.setCanTeach(true);
        user.setEditorAccess(true);

        String token = jwtService.generateAccessToken(user);
        Jwt decoded = jwtDecoder.decode(token);

        assertThat(decoded.getSubject()).isEqualTo("instructor");
        assertThat(decoded.getClaimAsStringList("roles"))
                .containsExactly("INSTRUCTOR", "EDITOR");
        assertThat(decoded.getClaimAsBoolean("editorAccess")).isTrue();
        assertThat(decoded.getClaimAsString("displayName")).isEqualTo("Расписенко");
    }
}
