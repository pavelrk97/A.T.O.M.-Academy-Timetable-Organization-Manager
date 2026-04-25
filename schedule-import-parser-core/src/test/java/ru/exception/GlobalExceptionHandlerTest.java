package ru.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleGeneric_returns500BodyAndLogsStackTrace(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/groups");

        ResponseEntity<?> response = handler.handleGeneric(new IllegalStateException("boom"), request);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body)
                .containsEntry("error", "Internal error")
                .containsEntry("message", "boom");

        assertThat(output.getOut())
                .contains("Unhandled exception")
                .contains("/api/groups")
                .contains("boom");
    }

    @Test
    void handleNotFound_returns404BodyAndWarnLog(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/lessons/missing");

        ResponseEntity<?> response = handler.handleNotFound(
                new ResourceNotFoundException("Lesson not found: missing"),
                request
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body)
                .containsEntry("error", "Not found")
                .containsEntry("message", "Lesson not found: missing");

        assertThat(output.getOut())
                .contains("Request failed with 404")
                .contains("/api/lessons/missing");
    }
}
