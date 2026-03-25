package ru.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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

    private final WebClient webClient;

    public ImportController(@Value("${core.service.url}") String coreUrl) {
        this.webClient = WebClient.builder().baseUrl(coreUrl).build();
    }

    @PostMapping(value = "/json", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importJson(@RequestHeader("Authorization") String authorization,
                                          @RequestParam("file") MultipartFile file) throws IOException {
        return sendMultipart("/api/import/json", authorization, file);
    }

    @PostMapping(value = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importCsv(@RequestHeader("Authorization") String authorization,
                                         @RequestParam("file") MultipartFile file) throws IOException {
        return sendMultipart("/api/import/csv", authorization, file);
    }

    private Map<String, Object> sendMultipart(String path, String authorization, MultipartFile file) throws IOException {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", file.getBytes())
                .filename(file.getOriginalFilename())
                .contentType(MediaType.parseMediaType(file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE));

        return webClient.post()
                .uri(path)
                .header("Authorization", authorization)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }
}
