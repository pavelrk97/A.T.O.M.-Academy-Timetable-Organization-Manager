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
import java.util.Set;
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

    @Test
    void parse_marksTripCellLessonsAsBusinessTrips() throws Exception {
        String csv = """
                h0,%s
                h1,05.%s
                "%s\nБ201","OE00\nTRIP\nМеняйло %s\nЗагузин %s"
                """.formatted(
                weekdayKey(),
                monthKey(Month.JUNE),
                "гр. 6",
                durationLiteral(8),
                durationLiteral(8)
        );

        List<Group> groups = ScheduleCsvParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(groups).hasSize(1);
        List<Lesson> lessons = groups.get(0).getDays().get(0).getLessons();
        assertThat(lessons).hasSize(2);
        assertThat(lessons).allSatisfy(lesson -> {
            assertThat(lesson.isBusinessTrip()).isTrue();
            assertThat(lesson.getTitle()).isEqualTo("TRIP");
            assertThat(lesson.getDurationHours()).isEqualTo(8);
        });
        assertThat(lessons.get(0).getLecturer()).isEqualTo("Меняйло");
        assertThat(lessons.get(1).getLecturer()).isEqualTo("Загузин");
    }

    @Test
    void parse_keepsRegularLessonsWithoutBusinessTripFlag() throws Exception {
        String csv = """
                h0,%s
                h1,05.%s
                "%s\nБ201","OE00\nМеняйло %s\nOrdinary topic"
                """.formatted(
                weekdayKey(),
                monthKey(Month.JUNE),
                "гр. 6",
                durationLiteral(4)
        );

        List<Group> groups = ScheduleCsvParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        List<Lesson> lessons = groups.get(0).getDays().get(0).getLessons();
        assertThat(lessons).hasSize(1);
        assertThat(lessons.get(0).isBusinessTrip()).isFalse();
        assertThat(lessons.get(0).getTitle()).isEqualTo("Ordinary topic");
    }

    @Test
    void parse_handlesInstructorFirstFormat() throws Exception {
        // Format B: "Instructor (Nч)" then title on next line (e.g. гр.174 в реальной выгрузке).
        String csv = """
                h0,%s
                h1,30.%s
                "%s\nБ202","CH03\nМеркель %s\nSystem of chemical reagents preparation (KBD-1)\nМеркель %s\nPrimary coolant treatment system (KBF)\nСП\nМеркель %s\nLiquid radioactive waste storage system (KPK)"
                """.formatted(
                weekdayKey(),
                monthKey(Month.APRIL),
                "гр. 174",
                durationLiteral(2),
                durationLiteral(3),
                durationLiteral(1)
        );

        List<Group> groups = ScheduleCsvParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(groups).hasSize(1);
        List<Lesson> lessons = groups.get(0).getDays().get(0).getLessons();
        assertThat(lessons).hasSize(3);

        assertThat(lessons.get(0).getType()).isEqualTo(LessonType.LECTURE);
        assertThat(lessons.get(0).getTitle()).isEqualTo("System of chemical reagents preparation (KBD-1)");
        assertThat(lessons.get(0).getLecturer()).isEqualTo("Меркель");
        assertThat(lessons.get(0).getDurationHours()).isEqualTo(2);

        assertThat(lessons.get(1).getType()).isEqualTo(LessonType.LECTURE);
        assertThat(lessons.get(1).getTitle()).isEqualTo("Primary coolant treatment system (KBF)");
        assertThat(lessons.get(1).getDurationHours()).isEqualTo(3);

        assertThat(lessons.get(2).getType()).isEqualTo(LessonType.SELF_STUDY);
        assertThat(lessons.get(2).getTitle()).isEqualTo("Liquid radioactive waste storage system (KPK)");
        assertThat(lessons.get(2).getDurationHours()).isEqualTo(1);
    }

    @Test
    void parse_handlesAssessmentWithSuffixAndMultipleInstructors() throws Exception {
        // OE00 / "Intermediate Examination (пересдача)" / Бращенко (3ч) / Костылев (3ч) / Климов (3ч)
        String csv = """
                h0,%s
                h1,27.%s
                "%s\nА308","OE00\n\nIntermediate Examination (пересдача)\n\nБращенко %s\nКостылев %s\nКлимов %s"
                """.formatted(
                weekdayKey(),
                monthKey(Month.APRIL),
                "гр. 87",
                durationLiteral(3),
                durationLiteral(3),
                durationLiteral(3)
        );

        List<Group> groups = ScheduleCsvParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(groups).hasSize(1);
        List<Lesson> lessons = groups.get(0).getDays().get(0).getLessons();
        assertThat(lessons).hasSize(1);

        Lesson lesson = lessons.get(0);
        assertThat(lesson.getType()).isEqualTo(LessonType.ASSESSMENT);
        assertThat(lesson.getTitle()).isEqualTo("Intermediate Examination (пересдача)");
        assertThat(lesson.getDurationHours()).isEqualTo(3);
        assertThat(lesson.getLecturers()).containsExactly("Бращенко", "Костылев", "Климов");
    }

    @Test
    void parse_handlesCourseCodeWithTrailingColon() throws Exception {
        String csv = """
                h0,%s
                h1,15.%s
                "%s\nА101","CS01:\nКорепанова\nAuxiliary boiler house (QH) %s"
                """.formatted(
                weekdayKey(),
                monthKey(Month.MARCH),
                "гр. 12",
                durationLiteral(3)
        );

        List<Group> groups = ScheduleCsvParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getDays().get(0).getMeta()).containsEntry("courseCode", "CS01");
        Lesson lesson = groups.get(0).getDays().get(0).getLessons().get(0);
        assertThat(lesson.getTitle()).isEqualTo("Auxiliary boiler house (QH)");
        assertThat(lesson.getLecturer()).isEqualTo("Корепанова");
    }

    @Test
    void parse_recognisesDynamicallyProvidedInstructorNotInDefaultList() throws Exception {
        // ФИО "Новенький" заведомо отсутствует в DEFAULT_INSTRUCTORS, но передаётся
        // в перегрузке parse(is, set) — парсер должен распознать его как инструктора.
        String csv = """
                h0,%s
                h1,12.%s
                "%s\nA101","T01\nНовенький\nIntroduction to power systems %s"
                """.formatted(
                weekdayKey(),
                monthKey(Month.MAY),
                "гр. 99",
                durationLiteral(4)
        );

        Set<String> dynamicInstructors = new java.util.LinkedHashSet<>();
        dynamicInstructors.add("Новенький");

        List<Group> groups = ScheduleCsvParser.parse(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                dynamicInstructors
        );

        assertThat(groups).hasSize(1);
        Lesson lesson = groups.get(0).getDays().get(0).getLessons().get(0);
        assertThat(lesson.getTitle()).isEqualTo("Introduction to power systems");
        assertThat(lesson.getLecturer()).isEqualTo("Новенький");
        assertThat(lesson.getDurationHours()).isEqualTo(4);
    }

    @Test
    void parse_resolvesPrefixCollisionByLongestMatch() throws Exception {
        // У "Иванов" и "Иванов С" одно префиксное вхождение —
        // в строке "Иванов С (3ч)" должен победить более длинный матч.
        String csv = """
                h0,%s
                h1,15.%s
                "%s\nA202","T05\nИванов С %s\nElectrical safety overview"
                """.formatted(
                weekdayKey(),
                monthKey(Month.MAY),
                "гр. 100",
                durationLiteral(3)
        );

        List<Group> groups = ScheduleCsvParser.parse(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))
        );

        assertThat(groups).hasSize(1);
        Lesson lesson = groups.get(0).getDays().get(0).getLessons().get(0);
        assertThat(lesson.getLecturer()).isEqualTo("Иванов С");
        assertThat(lesson.getTitle()).isEqualTo("Electrical safety overview");
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
