package ru.schedule.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.schedule.exception.ScheduleParseException;

import java.time.LocalDate;
import java.time.Month;
import java.util.Map;

public class DateParser {

    private static final Logger log =
            LoggerFactory.getLogger(DateParser.class);

    private static final Map<String, Month> MONTHS = Map.ofEntries(
            Map.entry("янв", Month.JANUARY),
            Map.entry("февр", Month.FEBRUARY),
            Map.entry("мар", Month.MARCH),
            Map.entry("апр", Month.APRIL),
            Map.entry("мая", Month.MAY),
            Map.entry("июн", Month.JUNE),
            Map.entry("июл", Month.JULY),
            Map.entry("авг", Month.AUGUST),
            Map.entry("сент", Month.SEPTEMBER),
            Map.entry("окт", Month.OCTOBER),
            Map.entry("нояб", Month.NOVEMBER),
            Map.entry("дек", Month.DECEMBER)
    );

    private static int resolveAcademicYear() {
        LocalDate now = LocalDate.now();
        return now.getYear();
    }

    public static LocalDate parse(String rawDate) {

        if (rawDate == null || rawDate.isBlank()) {
            throw new ScheduleParseException("Пустая дата в CSV");
        }

        try {
            rawDate = rawDate.trim().toLowerCase();

            String[] parts = rawDate.split("\\.");
            if (parts.length < 2) {
                throw new ScheduleParseException(
                        "Неверный формат даты: " + rawDate
                );
            }

            int day = Integer.parseInt(parts[0]);
            String monthKey = parts[1].replace(".", "");

            Month month = MONTHS.get(monthKey);
            if (month == null) {
                throw new ScheduleParseException(
                        "Неизвестный месяц: " + rawDate
                );
            }

            int year = 2026;

            return LocalDate.of(year, month, day);

        } catch (NumberFormatException e) {
            log.error("Ошибка числа в дате: {}", rawDate, e);
            throw new ScheduleParseException(
                    "Ошибка числа в дате: " + rawDate, e
            );
        }
    }
}
