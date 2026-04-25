package ru.parser;

import org.junit.jupiter.api.Test;
import ru.model.Group;
import ru.model.Lesson;
import ru.model.LessonType;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleCsvParserTest {

    @Test
    void parse_ignoresNonEmptyCellsWithoutDateHeader() throws Exception {
        String csv = """
                h0,%s,,,
                h1,05.%s,,,
                "%s\nB201","I&C02\nTopic one %s","","Ghost lesson %s","Another ghost lesson %s"
                """.formatted(
                weekdayKey(),
                monthKey(Month.JANUARY),
                "гр. 6",
                durationLiteral(2),
                durationLiteral(1),
                durationLiteral(1)
        );

        List<Group> groups = ScheduleCsvParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(groups).hasSize(1);
        Group group = groups.get(0);
        assertThat(group.getCode()).isEqualTo("гр. 6");
        assertThat(group.getDays()).hasSize(1);
        assertThat(group.getDays().get(0).getDate()).isEqualTo(LocalDate.of(2026, Month.JANUARY, 5));
    }

    @Test
    void parse_treatsEntryLevelTestAsAssessmentInsteadOfNamelessLecture() throws Exception {
        String csv = """
                h0,%s
                h1,30.%s
                "%s\nA210","ELT00\nEntry Level Test\nName %s"
                """.formatted(
                weekdayKey(),
                monthKey(Month.APRIL),
                "гр. 114",
                durationLiteral(8)
        );

        List<Group> groups = ScheduleCsvParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(groups).hasSize(1);
        Group group = groups.get(0);
        assertThat(group.getDays()).hasSize(1);
        assertThat(group.getDays().get(0).getDate()).isEqualTo(LocalDate.of(2026, Month.APRIL, 30));

        Lesson lesson = group.getDays().get(0).getLessons().get(0);
        assertThat(group.getDays().get(0).getLessons()).hasSize(1);
        assertThat(lesson.getType()).isEqualTo(LessonType.ASSESSMENT);
        assertThat(lesson.getTitle()).isEqualTo("Entry Level Test");
        assertThat(lesson.getDurationHours()).isEqualTo(8);
        assertThat(lesson.getLecturers()).containsExactly("Name");
    }

    @SuppressWarnings("unchecked")
    private static String monthKey(Month month) {
        try {
            Field field = DateParser.class.getDeclaredField("MONTHS");
            field.setAccessible(true);
            Map<String, Month> months = (Map<String, Month>) field.get(null);
            return months.entrySet().stream()
                    .filter(entry -> entry.getValue() == month)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to resolve month key for " + month, ex);
        }
    }

    private static String weekdayKey() {
        return "пн";
    }

    private static String durationLiteral(int hours) {
        try {
            Field field = ScheduleCsvParser.class.getDeclaredField("DURATION");
            field.setAccessible(true);
            Pattern pattern = (Pattern) field.get(null);
            String regex = pattern.pattern();
            int literalStart = regex.indexOf("\\s*");
            int literalEnd = regex.lastIndexOf("\\)");
            String durationSuffix = regex.substring(literalStart + 3, literalEnd);
            return "(" + hours + " " + durationSuffix + ")";
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to resolve duration literal", ex);
        }
    }
}
