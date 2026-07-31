package ru.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.client.ScheduleClient;
import ru.dto.AssistantRequest;
import ru.dto.AssistantResponse;
import ru.dto.ScheduleEntryDto;
import ru.service.AssistantRateLimiter.Challenge;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ИИ-ассистент по расписанию. Заземление на реальные данные, ОДИН вызов модели:
 *   1) период (from/to) вычисляем в Java по ключевым словам вопроса (без обращения к модели);
 *   2) тянем занятия из публичного эндпоинта schedule-service;
 *   3) один запрос к Gemini: модель сама фильтрует по группе/предмету/преподавателю
 *      и формулирует ответ ТОЛЬКО по переданным данным.
 * Перед 4-м запросом с одного IP включается самописная капча (см. {@link AssistantRateLimiter}).
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final int MAX_LESSONS_TO_MODEL = 80;
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final String QUOTA_MESSAGE = "ноу мани ноу хани, токены на сегодня все";

    private final GeminiClient gemini;
    private final ScheduleClient scheduleClient;
    private final AssistantRateLimiter rateLimiter;

    public AssistantService(GeminiClient gemini, ScheduleClient scheduleClient, AssistantRateLimiter rateLimiter) {
        this.gemini = gemini;
        this.scheduleClient = scheduleClient;
        this.rateLimiter = rateLimiter;
    }

    public AssistantResponse ask(AssistantRequest request, String clientIp) {
        if (!gemini.isConfigured()) {
            return textResponse("Ассистент не настроен: не задан ключ Gemini (переменная GEMINI_API_KEY).");
        }

        String question = request.getQuestion() == null ? "" : request.getQuestion().trim();
        if (question.isBlank()) {
            return textResponse("Задай вопрос про расписание - например, «какие занятия у группы 87 на этой неделе».");
        }

        // Защита от ботов: после лимита требуем капчу, пока её не решат.
        if (rateLimiter.needsCaptcha(clientIp)) {
            boolean solved = rateLimiter.solve(clientIp, request.getCaptchaId(), request.getCaptchaAnswer());
            if (!solved) {
                Challenge challenge = rateLimiter.issueChallenge(clientIp);
                return AssistantResponse.builder()
                        .answer("Слишком много запросов. Подтвердите, что вы не робот.")
                        .lessons(List.of())
                        .captchaRequired(true)
                        .captchaId(challenge.id())
                        .captchaQuestion(challenge.question())
                        .build();
            }
        }
        rateLimiter.recordRequest(clientIp);

        LocalDate today = LocalDate.now();
        LocalDate[] window = resolveWindow(question, today);

        List<ScheduleEntryDto> lessons;
        try {
            lessons = scheduleClient.getPublicSchedule(null, null, window[0], window[1]);
        } catch (Exception ex) {
            log.error("Assistant schedule fetch failed", ex);
            return textResponse("Не удалось получить расписание, попробуй позже.");
        }

        List<ScheduleEntryDto> capped = lessons.stream()
                .limit(MAX_LESSONS_TO_MODEL)
                .collect(Collectors.toList());

        String compact = capped.stream().map(this::compactLine).collect(Collectors.joining("\n"));
        String prompt = """
                Ты ассистент по учебному расписанию академии. Сегодня %s.
                Ответь на вопрос кратко и на языке вопроса, опираясь ТОЛЬКО на данные ниже.
                Если в вопросе упомянута группа, предмет или преподаватель - сам отфильтруй нужные строки.
                Ничего не выдумывай; если подходящих занятий нет - так и скажи.
                Вопрос: %s
                Занятия за период %s … %s (дата | группа | предмет | преподаватели | часы):
                %s
                """.formatted(today, question, window[0], window[1],
                        compact.isBlank() ? "(нет занятий за период)" : compact);

        String answer;
        try {
            answer = gemini.generate(prompt, false);
        } catch (GeminiQuotaExceededException ex) {
            log.warn("Assistant quota exhausted for ip={}", clientIp);
            return textResponse(QUOTA_MESSAGE);
        } catch (Exception ex) {
            log.warn("Assistant answer step failed: {}", ex.getMessage());
            answer = "Нашёл занятий: " + capped.size() + " за период " + window[0] + " … " + window[1] + ".";
        }

        return AssistantResponse.builder().answer(answer).lessons(capped).build();
    }

    /** Определяет период по ключевым словам вопроса, без вызова модели. По умолчанию - ближайшие 7 дней. */
    private LocalDate[] resolveWindow(String question, LocalDate today) {
        Matcher matcher = ISO_DATE.matcher(question);
        if (matcher.find()) {
            LocalDate first = LocalDate.parse(matcher.group());
            LocalDate second = matcher.find() ? LocalDate.parse(matcher.group()) : first;
            return first.isAfter(second)
                    ? new LocalDate[]{second, first}
                    : new LocalDate[]{first, second};
        }

        String q = question.toLowerCase();
        if (q.contains("послезавтра")) {
            return new LocalDate[]{today.plusDays(2), today.plusDays(2)};
        }
        if (q.contains("завтра") || q.contains("tomorrow")) {
            return new LocalDate[]{today.plusDays(1), today.plusDays(1)};
        }
        if (q.contains("сегодня") || q.contains("today")) {
            return new LocalDate[]{today, today};
        }
        if (q.contains("месяц") || q.contains("month")) {
            return new LocalDate[]{today, today.plusDays(30)};
        }
        if (q.contains("недел") || q.contains("week")) {
            return new LocalDate[]{today, today.plusDays(7)};
        }
        return new LocalDate[]{today, today.plusDays(7)};
    }

    private AssistantResponse textResponse(String answer) {
        return AssistantResponse.builder().answer(answer).lessons(List.of()).build();
    }

    private String compactLine(ScheduleEntryDto l) {
        List<String> instructors = l.getInstructorNames() == null ? List.of() : l.getInstructorNames();
        return l.getDate() + " | " + l.getGroupCode() + " | " + l.getTitle()
                + " | " + String.join(", ", instructors) + " | " + l.getDurationHours() + "ч";
    }
}
