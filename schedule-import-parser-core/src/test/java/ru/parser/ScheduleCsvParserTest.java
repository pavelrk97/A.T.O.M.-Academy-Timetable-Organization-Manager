package ru.parser;

import org.junit.jupiter.api.Test;
import ru.model.Group;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleCsvParserTest {

    @Test
    void parse_ignoresNonEmptyCellsWithoutDateHeader() throws Exception {
        String csv = """
                h0,%s,,,
                h1,05.%s,,,
                "%s\nB201","I&C02\nTopic one (2 С‡)","","Ghost lesson (1 С‡)","Another ghost lesson (1 С‡)"
                """.formatted(
                weekdayKey(),
                januaryKey(),
                "гр. 6"
        );

        List<Group> groups = ScheduleCsvParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(groups).hasSize(1);
        Group group = groups.get(0);
        assertThat(group.getCode()).isEqualTo("гр. 6");
        assertThat(group.getDays()).hasSize(1);
        assertThat(group.getDays().get(0).getDate()).isEqualTo(LocalDate.of(2026, Month.JANUARY, 5));
    }

    @SuppressWarnings("unchecked")
    private static String januaryKey() {
        try {
            Field field = DateParser.class.getDeclaredField("MONTHS");
            field.setAccessible(true);
            Map<String, Month> months = (Map<String, Month>) field.get(null);
            return months.entrySet().stream()
                    .filter(entry -> entry.getValue() == Month.JANUARY)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to resolve January key", ex);
        }
    }

    private static String weekdayKey() {
        return "пн";
    }
}
