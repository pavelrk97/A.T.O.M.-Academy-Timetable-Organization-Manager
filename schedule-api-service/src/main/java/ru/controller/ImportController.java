package ru.controller;

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
import ru.security.DownstreamAuthHeaderFactory;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final WebClient webClient;
    private final DownstreamAuthHeaderFactory authHeaderFactory;

    public ImportController(@Value("${import.service.url}") String importServiceUrl,
                            DownstreamAuthHeaderFactory authHeaderFactory) {
        this.webClient = WebClient.builder().baseUrl(importServiceUrl).build();
        this.authHeaderFactory = authHeaderFactory;
    }

    @PostMapping(value = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importCsv(Authentication authentication,
                                         @RequestParam("file") MultipartFile file) throws IOException {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", file.getBytes())
                .filename(file.getOriginalFilename())
                .contentType(MediaType.parseMediaType(file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE));

        return webClient.post()
                .uri("/api/import/csv")
                .header("Authorization", authHeaderFactory.bearerHeader(authentication))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }
}
