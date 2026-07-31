package ru.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Простая самописная защита ассистента от ботов, без внешних сервисов и ключей.
 * После {@link #FREE_REQUESTS} запросов с одного IP за окно {@link #WINDOW} следующий запрос
 * требует решить арифметическую капчу. Верный ответ обнуляет счётчик.
 */
@Service
public class AssistantRateLimiter {

    static final int FREE_REQUESTS = 3;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);

    private final ConcurrentHashMap<String, Counter> countersByIp = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Challenge> challengesById = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public boolean needsCaptcha(String ip) {
        Counter counter = countersByIp.get(ip);
        if (counter == null) {
            return false;
        }
        if (isExpired(counter.windowStart, WINDOW)) {
            countersByIp.remove(ip);
            return false;
        }
        return counter.count >= FREE_REQUESTS;
    }

    public Challenge issueChallenge(String ip) {
        int left = 1 + random.nextInt(9);
        int right = 1 + random.nextInt(9);
        Challenge challenge = new Challenge(
                UUID.randomUUID().toString(), ip, left + " + " + right, left + right, Instant.now());
        challengesById.put(challenge.id(), challenge);
        cleanupChallenges();
        return challenge;
    }

    /** Проверяет ответ на капчу; при успехе снимает блокировку (обнуляет счётчик по IP). */
    public boolean solve(String ip, String challengeId, String answer) {
        if (challengeId == null || answer == null) {
            return false;
        }
        Challenge challenge = challengesById.get(challengeId);
        if (challenge == null || !challenge.ip().equals(ip) || isExpired(challenge.createdAt(), CHALLENGE_TTL)) {
            return false;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(answer.trim());
        } catch (NumberFormatException ex) {
            return false;
        }
        if (parsed != challenge.expectedAnswer()) {
            return false;
        }
        challengesById.remove(challengeId);
        countersByIp.remove(ip);
        return true;
    }

    /** Учитывает обработанный запрос в окне по IP. */
    public void recordRequest(String ip) {
        countersByIp.compute(ip, (key, existing) -> {
            if (existing == null || isExpired(existing.windowStart, WINDOW)) {
                return new Counter(1, Instant.now());
            }
            existing.count++;
            return existing;
        });
    }

    private void cleanupChallenges() {
        challengesById.values().removeIf(challenge -> isExpired(challenge.createdAt(), CHALLENGE_TTL));
    }

    private static boolean isExpired(Instant since, Duration ttl) {
        return since.plus(ttl).isBefore(Instant.now());
    }

    private static final class Counter {
        private int count;
        private final Instant windowStart;

        private Counter(int count, Instant windowStart) {
            this.count = count;
            this.windowStart = windowStart;
        }
    }

    public record Challenge(String id, String ip, String question, int expectedAnswer, Instant createdAt) {
    }
}
