package ru.service;

/**
 * Бесплатная квота Gemini на сегодня исчерпана (HTTP 429 RESOURCE_EXHAUSTED).
 * Ассистент ловит это и отвечает пользователю понятным сообщением вместо 500.
 */
public class GeminiQuotaExceededException extends RuntimeException {

    public GeminiQuotaExceededException(String message) {
        super(message);
    }
}
