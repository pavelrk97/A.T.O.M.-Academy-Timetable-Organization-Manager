package ru.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.client.ScheduleClient;
import ru.dto.AssistantResponse;
import ru.dto.ScheduleEntryDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * ИИ-ассистент по расписанию. Схема «RAG-lite» с заземлением на реальные данные:
 *   1) Gemini извлекает из вопроса период (from/to) и текстовый фильтр (группа/преподаватель);
 *   2) тянем занятия из существующего публичного эндпоинта schedule-service;
 *   3) фуззи-фильтруем в Java (обходит точное совпадение кодов групп);
 *   4) Gemini формулирует ответ ТОЛЬКО по этим данным — ничего не выдумывает.
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final int MAX_LESSONS_TO_MODEL = 60;

    private final GeminiClient gemini;
    private final ScheduleClient scheduleClient;
    private final ObjectMapper objectMapper;

    public AssistantService(GeminiClient gemini, ScheduleClient scheduleClient, ObjectMapper objectMapper) {
        this.gemini = gemini;
        this.scheduleClient = scheduleClient;
        this.objectMapper = objectMapper;
    }

    public AssistantResponse ask(String question) {
        if (!gemini.isConfigured()) {
            return AssistantResponse.builder()
                    .answer("Ассистент не настроен: не задан ключ Gemini (переменная GEMINI_API_KEY).")
                    .lessons(List.of())
                    .build();
        }
        if (question == null || question.isBlank()) {
            return AssistantResponse.builder()
                    .answer("Задай вопрос про расписание — например, «какие занятия у группы 87 на этой неделе».")
                    .lessons(List.of())
                    .build();
        }

        LocalDate today = LocalDate.now();
        LocalDate from = today;
        LocalDate to = today.plusDays(7);
        String filter = "";

        // 1) извлечение параметров
        try {
            String extractPrompt = """
                    Сегодня %s. Пользователь спрашивает про учебное расписание академии.
                    Верни СТРОГО JSON без пояснений и текста вокруг:
                    {"from":"YYYY-MM-DD","to":"YYYY-MM-DD","filter":"<название группы или фамилия преподавателя из вопроса; пусто, если не упомянуты>"}
                    Если период в вопросе не указан — возьми ближайшие 7 дней от сегодняшней даты.
                    Вопрос: %s
                    """.formatted(today, question);
            String json = gemini.generate(extractPrompt, true);
            JsonNode node = objectMapper.readTree(json);
            if (node.hasNonNull("from")) {
                from = LocalDate.parse(node.get("from").asText());
            }
            if (node.hasNonNull("to")) {
                to = LocalDate.parse(node.get("to").asText());
            }
            if (node.hasNonNull("filter")) {
                filter = node.get("filter").asText("").trim();
            }
        } catch (Exception ex) {
            log.warn("Assistant extract step failed, using defaults ({}..{}): {}", from, to, ex.getMessage());
        }

        // 2) данные из существующего публичного эндпоинта
        List<ScheduleEntryDto> lessons;
        try {
            lessons = scheduleClient.getPublicSchedule(null, null, from, to);
        } catch (Exception ex) {
            log.error("Assistant schedule fetch failed", ex);
            return AssistantResponse.builder()
                    .answer("Не удалось получить расписание, попробуй позже.")
                    .lessons(List.of())
                    .build();
        }

        // 3) фуззи-фильтр в Java по группе / предмету / преподавателю
        String needle = filter.toLowerCase(Locale.ROOT);
        List<ScheduleEntryDto> filtered = lessons.stream()
                .filter(l -> needle.isBlank() || matches(l, needle))
                .limit(MAX_LESSONS_TO_MODEL)
                .collect(Collectors.toList());

        // 4) естественный ответ строго по данным
        String answer;
        try {
            String compact = filtered.stream()
                    .map(this::compactLine)
                    .collect(Collectors.joining("\n"));
            String answerPrompt = """
                    Ты ассистент по учебному расписанию. Ответь на вопрос кратко и на языке вопроса,
                    опираясь ТОЛЬКО на данные ниже. Ничего не выдумывай; если подходящих занятий нет — так и скажи.
                    Вопрос: %s
                    Занятия (дата | группа | предмет | преподаватели | часы):
                    %s
                    """.formatted(question, compact.isBlank() ? "(нет занятий за период)" : compact);
            answer = gemini.generate(answerPrompt, false);
        } catch (Exception ex) {
            log.warn("Assistant answer step failed: {}", ex.getMessage());
            answer = "Нашёл занятий: " + filtered.size() + " за период " + from + " … " + to + ".";
        }

        return AssistantResponse.builder().answer(answer).lessons(filtered).build();
    }

    private String compactLine(ScheduleEntryDto l) {
        List<String> instructors = l.getInstructorNames() == null ? List.of() : l.getInstructorNames();
        return l.getDate() + " | " + l.getGroupCode() + " | " + l.getTitle()
                + " | " + String.join(", ", instructors) + " | " + l.getDurationHours() + "ч";
    }

    private boolean matches(ScheduleEntryDto l, String needle) {
        if (l.getGroupCode() != null && l.getGroupCode().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        if (l.getTitle() != null && l.getTitle().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        if (l.getInstructorNames() != null) {
            for (String name : l.getInstructorNames()) {
                if (name != null && name.toLowerCase(Locale.ROOT).contains(needle)) {
                    return true;
                }
            }
        }
        return false;
    }
}
