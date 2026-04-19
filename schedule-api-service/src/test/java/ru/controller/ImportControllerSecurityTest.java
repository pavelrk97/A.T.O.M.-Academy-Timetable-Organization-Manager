package ru.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.config.SecurityConfig;
import ru.security.DownstreamAuthHeaderFactory;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImportController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "security.jwt.secret=test-jwt-secret-test-jwt-secret-123456",
        "import.service.url=http://localhost:8083"
})
class ImportControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DownstreamAuthHeaderFactory authHeaderFactory;

    @Test
    void editorCapableInstructorCannotImportCsv() throws Exception {
        mockMvc.perform(multipart("/api/import/csv")
                        .file("file", "col1,col2".getBytes())
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .tokenValue("test-token")
                                        .subject("instructor")
                                        .claim("roles", List.of("INSTRUCTOR", "EDITOR")))
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_INSTRUCTOR"),
                                        new SimpleGrantedAuthority("ROLE_EDITOR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void plainInstructorCannotImportCsv() throws Exception {
        mockMvc.perform(multipart("/api/import/csv")
                        .file("file", "col1,col2".getBytes())
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .tokenValue("test-token")
                                        .subject("instructor")
                                        .claim("roles", List.of("INSTRUCTOR")))
                                .authorities(new SimpleGrantedAuthority("ROLE_INSTRUCTOR"))))
                .andExpect(status().isForbidden());
    }
}
