package ru.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyExceptionHandlerTest {

    private final ProxyExceptionHandler handler = new ProxyExceptionHandler(new ObjectMapper());

    @Test
    void handleWebClientResponseException_preservesStatusAndMessage() {
        WebClientResponseException ex = WebClientResponseException.create(
                400,
                "Bad Request",
                null,
                "{\"message\":\"Пустая дата в CSV\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        ResponseEntity<Map<String, Object>> response = handler.handleWebClientResponseException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "Пустая дата в CSV");
        assertThat(response.getBody()).containsEntry("error", "Bad Request");
    }
}
