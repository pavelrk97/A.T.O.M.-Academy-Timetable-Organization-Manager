package ru.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyExceptionHandlerTest {

    private final ProxyExceptionHandler handler = new ProxyExceptionHandler(new ObjectMapper());

    @Test
    void handleFeignException_preservesStatusAndMessage() {
        Request request = Request.create(
                Request.HttpMethod.POST,
                "http://schedule-service/api/lessons",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null
        );
        Response response = Response.builder()
                .status(404)
                .reason("Not Found")
                .request(request)
                .headers(Map.of())
                .body("{\"message\":\"dayId is required\"}", StandardCharsets.UTF_8)
                .build();
        FeignException exception = FeignException.errorStatus("createLesson", response);

        ResponseEntity<Map<String, Object>> handled = handler.handleFeignException(exception);

        assertThat(handled.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handled.getBody()).containsEntry("message", "dayId is required");
        assertThat(handled.getBody()).containsEntry("error", "Not Found");
    }
}
