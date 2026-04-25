package ru.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ImportControllerTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @SuppressWarnings("unchecked")
    @Test
    void importCsv_proxiesFileAndLogsSuccess(CapturedOutput output) throws Exception {
        ImportController controller = new ImportController("http://schedule-service", "schedule-internal-key");
        ReflectionTestUtils.setField(controller, "webClient", webClient);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "schedule.csv",
                "text/csv",
                "col1,col2".getBytes(StandardCharsets.UTF_8)
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken("admin", "pass");
        Map<String, Object> payload = Map.of("status", "ok", "importedGroups", 2);

        given(webClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri("/internal/import/csv")).willReturn(requestBodySpec);
        given(requestBodySpec.header("X-Performed-By", "admin")).willReturn(requestBodySpec);
        given(requestBodySpec.contentType(MediaType.MULTIPART_FORM_DATA)).willReturn(requestBodySpec);
        given(requestBodySpec.body(any(BodyInserter.class))).willReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(eq(Map.class))).willReturn(Mono.just(payload));

        Map<String, Object> result = controller.importCsv(file, authentication);

        assertThat(result).isEqualTo(payload);
        verify(requestBodyUriSpec).uri("/internal/import/csv");
        verify(requestBodySpec).header("X-Performed-By", "admin");
        verify(requestBodySpec).contentType(MediaType.MULTIPART_FORM_DATA);
        assertThat(output.getOut())
                .contains("CSV import request accepted")
                .contains("CSV import request finished")
                .contains("schedule.csv");
    }

    @SuppressWarnings("unchecked")
    @Test
    void importCsv_logsFailureAndRethrows(CapturedOutput output) {
        ImportController controller = new ImportController("http://schedule-service", "schedule-internal-key");
        ReflectionTestUtils.setField(controller, "webClient", webClient);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "broken.csv",
                "text/csv",
                "oops".getBytes(StandardCharsets.UTF_8)
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken("admin", "pass");

        given(webClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri("/internal/import/csv")).willReturn(requestBodySpec);
        given(requestBodySpec.header("X-Performed-By", "admin")).willReturn(requestBodySpec);
        given(requestBodySpec.contentType(MediaType.MULTIPART_FORM_DATA)).willReturn(requestBodySpec);
        given(requestBodySpec.body(any(BodyInserter.class))).willReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(eq(Map.class))).willReturn(Mono.error(new IllegalStateException("downstream failed")));

        assertThatThrownBy(() -> controller.importCsv(file, authentication))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("downstream failed");

        assertThat(output.getOut())
                .contains("CSV import request failed")
                .contains("broken.csv");
    }
}
