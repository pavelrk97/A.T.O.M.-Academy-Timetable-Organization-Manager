package ru.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Тонкий клиент к Google Gemini (generateContent REST API).
 * Ключ берётся из GEMINI_API_KEY; если он пуст — {@link #isConfigured()} вернёт false,
 * и ассистент вежливо сообщит, что не настроен, вместо падения.
 */
@Service
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final WebClient webClient;
    private final String apiKey;
    private final String model;

    public GeminiClient(@Value("${gemini.base-url}") String baseUrl,
                        @Value("${gemini.api-key:}") String apiKey,
                        @Value("${gemini.model}") String model) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Отправляет промпт, возвращает текст ответа модели.
     * jsonMode=true заставляет Gemini вернуть валидный JSON (для шага извлечения параметров).
     */
    public String generate(String prompt, boolean jsonMode) {
        Map<String, Object> generationConfig = jsonMode
                ? Map.of("temperature", 0.1, "responseMimeType", "application/json")
                : Map.of("temperature", 0.3);
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", generationConfig
        );

        JsonNode response;
        try {
            response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models/{model}:generateContent")
                            .queryParam("key", apiKey)
                            .build(model))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
        } catch (WebClientResponseException ex) {
            log.warn("Gemini API {} for model {}: {}", ex.getStatusCode(), model, ex.getResponseBodyAsString());
            if (ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throw new GeminiQuotaExceededException("Gemini free-tier quota exhausted");
            }
            throw ex;
        }

        if (response == null) {
            return "";
        }
        JsonNode text = response.at("/candidates/0/content/parts/0/text");
        return text.isMissingNode() ? "" : text.asText();
    }
}
