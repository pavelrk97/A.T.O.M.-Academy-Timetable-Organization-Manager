package ru.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/import")
public class ImportController {

    private static final Logger log = LoggerFactory.getLogger(ImportController.class);

    private final WebClient webClient;

    public ImportController(@Value("${schedule.service.url}") String scheduleServiceUrl,
                            @Value("${schedule.service.api-key}") String scheduleServiceApiKey) {
        this.webClient = WebClient.builder()
                .baseUrl(scheduleServiceUrl)
                .defaultHeader("X-Internal-Api-Key", scheduleServiceApiKey)
                .build();
    }

    @PostMapping(value = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importCsv(@RequestParam("file") MultipartFile file,
                                         Authentication authentication) throws IOException {
        long startedAt = System.nanoTime();
        log.info("CSV import request accepted: user={}, filename={}, sizeBytes={}",
                authentication.getName(), file.getOriginalFilename(), file.getSize());

        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", file.getBytes())
                    .filename(file.getOriginalFilename())
                    .contentType(MediaType.parseMediaType(file.getContentType() != null
                            ? file.getContentType()
                            : MediaType.APPLICATION_OCTET_STREAM_VALUE));

            Map<String, Object> response = webClient.post()
                    .uri("/internal/import/csv")
                    .header("X-Performed-By", authentication.getName())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("CSV import request finished: user={}, filename={}, durationMs={}",
                    authentication.getName(), file.getOriginalFilename(), durationMs);
            return response;
        } catch (IOException | RuntimeException ex) {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.error("CSV import request failed: user={}, filename={}, durationMs={}",
                    authentication.getName(), file.getOriginalFilename(), durationMs, ex);
            throw ex;
        }
    }
}
