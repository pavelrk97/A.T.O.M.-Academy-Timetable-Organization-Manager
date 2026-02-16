package ru.schedule.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    /**
     * Определяет учебный год автоматически:
     * Если сейчас сентябрь–декабрь → начало текущего года
     * Если январь–август → начало прошлого года
     */
    private static int resolveAcademicYear() {
        LocalDate now = LocalDate.now();

        if (now.getMonthValue() >= 9) {
            return now.getYear();
        } else {
            return now.getYear() - 1;
        }
    }

    public static LocalDate parse(String rawDate) {

        if (rawDate == null || rawDate.isBlank()) {
            log.warn("Пустая дата из CSV");
            return null;
        }

        try {
            rawDate = rawDate.trim().toLowerCase();

            String[] parts = rawDate.split("\\.");
            if (parts.length < 2) {
                log.error("Неверный формат даты: {}", rawDate);
                return null;
            }

            int day = Integer.parseInt(parts[0]);
            String monthKey = parts[1].replace(".", "");

            Month month = MONTHS.get(monthKey);
            if (month == null) {
                log.error("Неизвестный месяц: {}", rawDate);
                return null;
            }

            int academicStartYear = resolveAcademicYear();

            // если месяц сентябрь–декабрь → тот же год
            // если январь–август → следующий
            int year = (month.getValue() >= 9)
                    ? academicStartYear
                    : academicStartYear + 1;

            return LocalDate.of(year, month, day);

        } catch (Exception e) {
            log.error("Ошибка парсинга даты: {}", rawDate, e);
            return null;
        }
    }
}
